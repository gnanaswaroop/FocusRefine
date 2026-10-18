#!/usr/bin/env python3
"""
Replay the golden / holdout notification datasets through the real FocusRefine
NotificationFilterService on a device or emulator, then compare the app's
Shown/Muted decisions with the labels in testdata/golden/*.json.

For every mode the script:
  1. unbinds the listener and force-stops the app,
  2. wipes the app database and sets current_mode / use_ai_model in SharedPreferences,
  3. rebinds the listener and posts every notification with `cmd notification post`,
  4. reads the app's Room database back (via run-as) and scores the decisions.

WARNING: this wipes the app's notification history on the target device. The database and
preferences are backed up first; afterwards the original preferences and the learned rules
are restored (history stays cleared). It refuses to run on anything but an emulator unless --allow-device is passed.

Requires: adb on PATH, a debuggable build of com.focusrefine.app installed, and
notification-listener access already granted to the app.

Usage:
  python scripts/replay_golden.py --serial emulator-5554
  python scripts/replay_golden.py --serial emulator-5554 --dataset holdout --modes Commute "Sleeping / Rest"
"""
import argparse
import json
import os
import sqlite3
import subprocess
import sys
import tempfile
import time
import xml.etree.ElementTree as ET
from datetime import datetime

PKG = "com.focusrefine.app"
LISTENER = f"{PKG}/{PKG}.NotificationFilterService"
POSTER_PKG = "com.android.shell"  # package that `cmd notification post` posts as
PREFS_PATH = "shared_prefs/FocusRefinePrefs.xml"
ALL_MODES = ["Commute", "Deep Work / Focus", "Do Not Disturb (DND)", "Sleeping / Rest", "Workout / Gym"]
ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
DATASETS = {
    "golden": os.path.join(ROOT, "testdata", "golden", "golden_notifications.json"),
    "holdout": os.path.join(ROOT, "testdata", "golden", "holdout_notifications.json"),
}


class Adb:
    def __init__(self, serial):
        self.base = ["adb", "-s", serial]

    def run(self, *args, input=None, check=True):
        r = subprocess.run(self.base + list(args), input=input, capture_output=True)
        if check and r.returncode != 0:
            raise RuntimeError(f"adb {' '.join(args)} failed: {r.stderr.decode(errors='replace')}")
        return r.stdout

    def shell(self, cmd, **kw):
        return self.run("shell", cmd, **kw).decode(errors="replace")


def sh_quote(s):
    return "'" + s.replace("'", "'\\''") + "'"


def set_prefs(adb, values, drop_prefixes=()):
    xml = adb.run("shell", f"run-as {PKG} cat {PREFS_PATH}", check=False).decode("utf-8", errors="replace").strip()
    try:
        root = ET.fromstring(xml)
    except ET.ParseError:
        root = ET.Element("map")
    for el in list(root):
        if any(el.get("name", "").startswith(prefix) for prefix in drop_prefixes):
            root.remove(el)
    for name, (tag, val) in values.items():
        for el in root.findall(f".//*[@name='{name}']"):
            root.remove(el)
        el = ET.SubElement(root, tag, {"name": name})
        if tag == "string":
            el.text = val
        else:
            el.set("value", str(val).lower() if tag == "boolean" else str(val))
    data = b"<?xml version='1.0' encoding='utf-8' standalone='yes' ?>\n" + ET.tostring(root, encoding="utf-8")
    adb.run("shell", f"run-as {PKG} sh -c 'cat > {PREFS_PATH}'", input=data)


def clear_test_notifications(adb):
    """Force-stopping the shell package cancels every notification it posted (Android silently drops posts
    beyond 50 active notifications per package, so stale test notifications would cause missing results)."""
    adb.shell("am force-stop com.android.shell", check=False)
    time.sleep(1)


def reset_app(adb, mode, use_ai):
    clear_test_notifications(adb)
    adb.shell(f"cmd notification disallow_listener {LISTENER}")
    time.sleep(1)
    adb.shell(f"am force-stop {PKG}")
    adb.shell(f"run-as {PKG} rm -f databases/focusrefine_database databases/focusrefine_database-wal databases/focusrefine_database-shm")
    adb.shell(f"run-as {PKG} rm -f files/personal_model.bin")  # corrections from an earlier run must not leak into this one
    set_prefs(adb, {
        "current_mode": ("string", mode),
        "use_ai_model": ("boolean", use_ai),
        "filter_strength": ("int", 1),  # Balanced; a leftover Gentle/Strict would silently shift every threshold
        # The instrumented tests leave exception toggles and edited prompts behind; replay with the shipped defaults.
        "exception_nav_prompts": ("boolean", True),
        "exception_calls": ("boolean", True),
        "exception_security_otps": ("boolean", True),
        "service_running": ("boolean", True),
        "suppressed_count": ("int", 0),
    }, drop_prefixes=("prompt_", "custom_mode_prompt_"))
    adb.shell(f"cmd notification allow_listener {LISTENER}")
    for _ in range(20):  # wait for the listener process to come up
        if adb.shell(f"pidof {PKG}", check=False).strip():
            break
        time.sleep(0.5)
    time.sleep(3)


def wait_until_listening(adb, timeout=90):
    """Posts a probe until the service has recorded it, so no real test notification is posted before the
    listener has (re)bound. Notifications posted before onListenerConnected are never delivered."""
    deadline = time.time() + timeout
    n = 0
    while time.time() < deadline:
        adb.shell(f"cmd notification post -t 'zz-probe' probe 'ready check {n}'", check=False)
        n += 1
        time.sleep(2)
        if any(t == "zz-probe" for t, *_ in read_rows(adb)):
            return True
        if n % 8 == 0:  # still nothing after ~16s: kick the listener binding
            adb.shell(f"cmd notification disallow_listener {LISTENER}", check=False)
            time.sleep(1)
            adb.shell(f"cmd notification allow_listener {LISTENER}", check=False)
            time.sleep(3)
    print("    WARNING: service did not confirm it was listening; results may have missing items", flush=True)
    return False


def post_all(adb, items, delay, tag_prefix="g"):
    lines = ["#!/system/bin/sh"]
    for i, it in enumerate(items):
        lines.append(f"cmd notification post -t {sh_quote(it['title'])} {sh_quote('%s%02d' % (tag_prefix, i % TAG_POOL))} {sh_quote(it['text'])} >/dev/null 2>&1")
        lines.append(f"sleep {delay}")
    with tempfile.NamedTemporaryFile("w", suffix=".sh", delete=False, newline="\n") as f:
        f.write("\n".join(lines) + "\n")
        local = f.name
    try:
        adb.run("push", local, "/data/local/tmp/replay.sh")
        adb.shell("sh /data/local/tmp/replay.sh")
    finally:
        os.unlink(local)


def read_rows(adb):
    tmp = tempfile.mkdtemp()
    for name in ("focusrefine_database", "focusrefine_database-wal", "focusrefine_database-shm"):
        data = adb.run("exec-out", f"run-as {PKG} cat databases/{name}", check=False)
        if data:
            with open(os.path.join(tmp, name), "wb") as f:
                f.write(data)
    try:
        con = sqlite3.connect(os.path.join(tmp, "focusrefine_database"))
        rows = con.execute(
            "SELECT title, text, isUrgent, explanation FROM notifications WHERE packageName = ? AND isLearned = 0",
            (POSTER_PKG,),
        ).fetchall()
        con.close()
        return rows
    except sqlite3.Error:
        return []


TAG_POOL = 24  # Android drops posts beyond 50 active notifications per package; reuse tags so they replace each other
DB_FILES = ("focusrefine_database", "focusrefine_database-wal", "focusrefine_database-shm")


def push_app_file(adb, rel_path, data):
    adb.run("shell", f"run-as {PKG} sh -c 'cat > {rel_path}'", input=data)


def backup_state(adb, out_dir):
    """Save the app's DB + prefs so they can be restored (learned rules and settings survive the run)."""
    os.makedirs(out_dir, exist_ok=True)
    for name in DB_FILES:
        data = adb.run("exec-out", f"run-as {PKG} cat databases/{name}", check=False)
        if data:
            with open(os.path.join(out_dir, name), "wb") as f:
                f.write(data)
    prefs = adb.run("exec-out", f"run-as {PKG} cat {PREFS_PATH}", check=False)
    with open(os.path.join(out_dir, "FocusRefinePrefs.xml"), "wb") as f:
        f.write(prefs)
    personal = adb.run("exec-out", f"run-as {PKG} cat files/personal_model.bin", check=False)
    if personal:
        with open(os.path.join(out_dir, "personal_model.bin"), "wb") as f:
            f.write(personal)
    zen = adb.shell("settings get global zen_mode", check=False).strip()
    return {"zen_mode": zen, "dir": out_dir}


def restore_state(adb, saved):
    """Restore original prefs and a DB containing ONLY the user's learned rules (history stays clean)."""
    out_dir = saved["dir"]
    adb.shell(f"cmd notification disallow_listener {LISTENER}", check=False)
    time.sleep(1)
    adb.shell(f"am force-stop {PKG}", check=False)
    adb.shell(f"run-as {PKG} rm -f " + " ".join(f"databases/{n}" for n in DB_FILES), check=False)

    work = tempfile.mkdtemp()
    have_db = os.path.exists(os.path.join(out_dir, "focusrefine_database"))
    if have_db:
        for n in DB_FILES:
            src = os.path.join(out_dir, n)
            if os.path.exists(src):
                with open(src, "rb") as fi, open(os.path.join(work, n), "wb") as fo:
                    fo.write(fi.read())
        con = sqlite3.connect(os.path.join(work, "focusrefine_database"))
        con.execute("DELETE FROM notifications WHERE isLearned = 0")
        con.commit()
        con.execute("PRAGMA wal_checkpoint(TRUNCATE)")
        con.execute("PRAGMA journal_mode=DELETE")
        kept = con.execute("SELECT COUNT(*) FROM notifications").fetchone()[0]
        con.close()
        with open(os.path.join(work, "focusrefine_database"), "rb") as f:
            push_app_file(adb, "databases/focusrefine_database", f.read())
        print(f"restored DB with {kept} learned rule(s), history cleared")
    with open(os.path.join(out_dir, "FocusRefinePrefs.xml"), "rb") as f:
        prefs = f.read()
    if prefs.strip():
        push_app_file(adb, PREFS_PATH, prefs)
        print("restored original preferences")

    adb.shell(f"run-as {PKG} rm -f files/personal_model.bin files/corrections.json", check=False)
    saved_model = os.path.join(out_dir, "personal_model.bin")
    if os.path.exists(saved_model):
        with open(saved_model, "rb") as f:
            push_app_file(adb, "files/personal_model.bin", f.read())
        print("restored the personal model")
    adb.shell(f"cmd notification allow_listener {LISTENER}", check=False)
    clear_test_notifications(adb)
    print("cleared leftover test notifications from the notification shade")
    zen = saved.get("zen_mode", "0")
    adb.shell("cmd notification set_dnd " + ("off" if zen in ("", "0", "null") else "priority"), check=False)
    print(f"restored do-not-disturb state (zen_mode={zen or '0'})")


def decided_by(explanation):
    e = explanation or ""
    if "user exception" in e:
        return "exception"
    if "learned" in e:
        return "learned rule"
    if "AI Pipeline" in e:
        return "ai model"
    if "Matched active" in e:
        return "prompt keyword"
    if "Did not contain" in e:
        return "no keyword (muted)"
    return "other"


def score(items, rows, mode):
    got = {}
    for title, text, urgent, expl in rows:
        got[(title, text)] = (bool(urgent), expl)
    tp = fp = fn = tn = missing = 0
    misses, by, missing_items = [], {}, []
    for it in items:
        exp = mode in it["urgentIn"]
        res = got.get((it["title"], it["text"]))
        if res is None:
            missing += 1
            missing_items.append(f"{it['title']} | {it['text']}")
            continue
        pred, expl = res
        by[decided_by(expl)] = by.get(decided_by(expl), 0) + 1
        if pred and exp: tp += 1
        elif pred and not exp: fp += 1; misses.append(("FP", it, expl))
        elif not pred and exp: fn += 1; misses.append(("FN", it, expl))
        else: tn += 1
    p = tp / (tp + fp) if tp + fp else 0.0
    r = tp / (tp + fn) if tp + fn else 0.0
    f1 = 2 * p * r / (p + r) if p + r else 0.0
    n = tp + fp + fn + tn
    return dict(mode=mode, n=n, missing=missing, tp=tp, fp=fp, fn=fn, tn=tn, precision=p, recall=r, f1=f1,
                accuracy=(tp + tn) / n if n else 0.0, decided_by=by, missing_items=missing_items,
                misses=[dict(kind=k, title=i["title"], text=i["text"], why=w) for k, i, w in misses])


def matched_count(items, rows):
    keys = {(t, x) for t, x, *_ in rows}
    return sum(1 for it in items if (it["title"], it["text"]) in keys)


def wait_for_items(adb, items, settle):
    """Poll the DB until every dataset item is recorded, or nothing new arrives for 6s, or timeout."""
    rows, last, stable_since = [], -1, time.time()
    deadline = time.time() + settle
    while time.time() < deadline:
        rows = read_rows(adb)
        got = matched_count(items, rows)
        if got != last:
            last, stable_since = got, time.time()
        if got >= len(items) or time.time() - stable_since > 6:
            break
        time.sleep(1.5)
    return rows


def run_dataset(adb, name, items, modes, delay, settle, use_ai):
    results = []
    for mode in modes:
        print(f"[{name}] mode={mode}: resetting app and posting {len(items)} notifications ...", flush=True)
        reset_app(adb, mode, use_ai)
        wait_until_listening(adb)
        post_all(adb, items, delay)
        rows = wait_for_items(adb, items, settle)
        for attempt in (1, 2):  # re-post anything that never arrived
            have = {(t, x) for t, x, *_ in rows}
            missing = [it for it in items if (it["title"], it["text"]) not in have]
            if not missing:
                break
            print(f"    {len(missing)} not received, re-posting (attempt {attempt}) ...", flush=True)
            post_all(adb, missing, delay)
            rows = wait_for_items(adb, items, 30)
        res = score(items, rows, mode)
        results.append(res)
        print(f"    received {res['n']}/{len(items)} (missing {res['missing']})  "
              f"P={res['precision']:.2f} R={res['recall']:.2f} F1={res['f1']:.2f} acc={res['accuracy']:.2f} "
              f"FP={res['fp']} FN={res['fn']}  decided by {res['decided_by']}", flush=True)
    return results


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--serial", required=True)
    ap.add_argument("--dataset", nargs="+", default=["golden", "holdout"], choices=list(DATASETS))
    ap.add_argument("--modes", nargs="+", default=ALL_MODES)
    ap.add_argument("--engine", choices=["ai", "keyword"], default="ai",
                    help="ai = embedding model enabled (app default); keyword = use_ai_model off (baseline)")
    ap.add_argument("--delay", type=float, default=0.25, help="seconds between posts (system rate limit)")
    ap.add_argument("--settle", type=int, default=90, help="max seconds to wait for the app to process")
    ap.add_argument("--limit", type=int, help="only replay the first N notifications (quick run)")
    ap.add_argument("--min-f1", type=float, default=0.0,
                    help="exit non-zero if any mode's F1 on any dataset falls below this (regression gate)")
    ap.add_argument("--allow-device", action="store_true", help="allow running on a non-emulator (wipes app data!)")
    ap.add_argument("--out", default=os.path.join(ROOT, "app", "build", "golden-reports"))
    a = ap.parse_args()

    if not a.serial.startswith("emulator-") and not a.allow_device:
        sys.exit("Refusing to wipe app data on a non-emulator. Pass --allow-device if you really mean it.")
    adb = Adb(a.serial)
    report = {"serial": a.serial, "time": datetime.now().isoformat(timespec="seconds"), "datasets": {}}
    saved = backup_state(adb, os.path.join(a.out, "backup_" + datetime.now().strftime("%Y%m%d_%H%M%S")))
    print("backup saved to", saved["dir"])
    if not a.serial.startswith("emulator-"):
        adb.shell("cmd notification set_dnd priority", check=False)  # keep test notifications silent
    try:
        for name in a.dataset:
            items = json.load(open(DATASETS[name], encoding="utf-8"))
            if a.limit:
                items = items[: a.limit]
            report["datasets"][name] = run_dataset(adb, name, items, a.modes, a.delay, a.settle, a.engine == "ai")
    finally:
        restore_state(adb, saved)

    print("\n=== SUMMARY (macro average over modes) ===")
    for name, res in report["datasets"].items():
        avg = lambda k: sum(r[k] for r in res) / len(res)
        print(f"{name:8s} F1={avg('f1'):.3f} precision={avg('precision'):.3f} recall={avg('recall'):.3f} "
              f"accuracy={avg('accuracy'):.3f} missing={sum(r['missing'] for r in res)}")
    os.makedirs(a.out, exist_ok=True)
    path = os.path.join(a.out, datetime.now().strftime("replay_%Y%m%d_%H%M%S.json"))
    json.dump(report, open(path, "w", encoding="utf-8"), indent=2, ensure_ascii=False)
    print("report:", path)

    failing = [(n, r["mode"], r["f1"], r["missing"]) for n, res in report["datasets"].items() for r in res
               if r["f1"] < a.min_f1 or r["missing"]]
    if failing:
        print(f"\nFAIL: {len(failing)} mode run(s) below --min-f1 {a.min_f1} or with notifications that never arrived:")
        for n, m, f, miss in failing:
            reasons = ([f"F1 {f:.2f} < {a.min_f1}"] if f < a.min_f1 else []) + ([f"{miss} never arrived"] if miss else [])
            print(f"  {n} / {m}: " + ", ".join(reasons))
        sys.exit(1)


if __name__ == "__main__":
    main()
