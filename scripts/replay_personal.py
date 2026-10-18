#!/usr/bin/env python3
"""
Measures what the on-device personal model adds, end to end, through the real FocusRefine service.

For every mode the script:
  1. replays the golden and holdout notifications with NO learning (the baseline) and records each decision,
  2. simulates a user who teaches the app N corrections taken from the *golden* set (sampled at random, or only
     the notifications the app got wrong), by seeding the personal model exactly as the detail screen does
     (PersonalSeedTest, run with `am instrument`),
  3. replays the unseen holdout set plus the sampled golden notifications again and scores them,
and repeats this for several personas, sampling variants, sizes and random seeds.

Personas change the labels the simulated user wants:
  standard  agrees with the dataset labels
  chatty    additionally wants chat apps (WhatsApp, Messenger, Slack, Snapchat) Shown in Commute, Focus and Workout

Nothing here leaves the machine: the corrections are written into the app's private storage on the emulator.
WARNING: it wipes the emulator app's notification history (the DB, prefs and personal model are backed up and
restored afterwards). It refuses to run on anything but an emulator.

Requires the debug app and the androidTest APK installed (./gradlew assembleDebug assembleDebugAndroidTest).

Usage:
  python scripts/replay_personal.py --serial emulator-5554
  python scripts/replay_personal.py --serial emulator-5554 --sizes 10 --seeds 1 --personas standard --variants random --modes Commute
"""
import argparse
import json
import os
import random
import statistics
import sys
import time
from datetime import datetime

import replay_golden as g

CHAT_APPS = {"whatsapp", "messenger", "slack", "snapchat"}
CHAT_MODES = ("Commute", "Focus", "Workout")
SEED_TEST = "com.focusrefine.app.PersonalSeedTest"
RUNNER = "com.focusrefine.app.test/androidx.test.runner.AndroidJUnitRunner"


def relabel(items, persona):
    out = []
    for it in items:
        if "labels" in it:  # corpus items carry per-persona labels
            out.append({**it, "urgentIn": it["labels"][persona]})
            continue
        wanted = set(it["urgentIn"])
        if persona == "chatty" and it["title"].lower() in CHAT_APPS:
            wanted |= {m for m in g.ALL_MODES if any(k in m for k in CHAT_MODES)}
        out.append({**it, "urgentIn": sorted(wanted)})
    return out


def key(it):
    return (it["title"], it["text"])


def seed(adb, corrections):
    """Teach the personal model [corrections] through the real code path, then restart the listener."""
    g.push_app_file(adb, "files/corrections.json", json.dumps(corrections, ensure_ascii=False).encode("utf-8"))
    out = adb.shell(f"am instrument -w -e class {SEED_TEST} {RUNNER}", check=False)
    if "OK (1 test)" not in out:
        raise RuntimeError("seeding failed:\n" + out[-1500:])
    adb.shell(f"cmd notification disallow_listener {g.LISTENER}", check=False)
    time.sleep(1)
    adb.shell(f"am force-stop {g.PKG}", check=False)
    adb.shell(f"cmd notification allow_listener {g.LISTENER}", check=False)
    time.sleep(3)


def run_pass(adb, mode, items, delay, settle, corrections=None):
    """Fresh app state for [mode], optionally taught [corrections]; returns the stored rows for [items]."""
    g.reset_app(adb, mode, True)
    if corrections:
        seed(adb, corrections)
    g.wait_until_listening(adb)
    g.post_all(adb, items, delay)
    rows = g.wait_for_items(adb, items, settle)
    for _ in (1, 2):
        have = {(t, x) for t, x, *_ in rows}
        missing = [it for it in items if key(it) not in have]
        if not missing:
            break
        g.post_all(adb, missing, delay)
        rows = g.wait_for_items(adb, items, 30)
    return rows


def decisions(rows):
    return {(t, x): (bool(u), e) for t, x, u, e in rows}


def personalised(rows):
    return sum(1 for *_, e in rows if "past choices" in (e or ""))


def macro(results, field):
    return sum(r[field] for r in results) / len(results)


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    ap.add_argument("--serial", required=True)
    ap.add_argument("--modes", nargs="+", default=g.ALL_MODES)
    ap.add_argument("--personas", nargs="+", default=["standard", "chatty"],
                    choices=["standard", "chatty", "workaholic", "minimalist", "news_fan", "parent"])
    ap.add_argument("--variants", nargs="+", default=["random", "errors"], choices=["random", "errors"],
                    help="random = a random sample of golden notifications; errors = only ones the app got wrong")
    ap.add_argument("--sizes", nargs="+", type=int, default=[10, 30], help="corrections per mode")
    ap.add_argument("--seeds", nargs="+", type=int, default=[1, 2])
    ap.add_argument("--delay", type=float, default=0.15)
    ap.add_argument("--settle", type=int, default=90)
    ap.add_argument("--holdout-limit", type=int, help="only use the first N holdout items (quick run)")
    ap.add_argument("--corpus", action="store_true",
                    help="use testdata/corpus (590 items, 6 personas) instead of golden/holdout: half of each template "
                         "is the pool corrections are sampled from, the other half is the unseen test set")
    ap.add_argument("--personas-corpus", nargs="+", default=None, help="personas to use with --corpus")
    ap.add_argument("--out", default=os.path.join(g.ROOT, "app", "build", "golden-reports"))
    a = ap.parse_args()
    if not a.serial.startswith("emulator-"):
        sys.exit("This experiment wipes app data; it only runs on an emulator.")

    if a.corpus:
        corpus = json.load(open(os.path.join(g.ROOT, "testdata", "corpus", "corpus_notifications.json"), encoding="utf-8"))
        rng0 = random.Random(20261019)
        by_template = {}
        for it in corpus:
            by_template.setdefault(it["template"], []).append(it)
        golden, holdout = [], []  # golden = the pool corrections come from, holdout = unseen instances of the same templates
        for items_ in by_template.values():
            rng0.shuffle(items_)
            half = (len(items_) + 1) // 2
            golden += items_[:half]
            holdout += items_[half:]
    else:
        golden = json.load(open(g.DATASETS["golden"], encoding="utf-8"))
        holdout = json.load(open(g.DATASETS["holdout"], encoding="utf-8"))
    if a.holdout_limit:
        holdout = holdout[: a.holdout_limit]
    adb = g.Adb(a.serial)
    saved = g.backup_state(adb, os.path.join(a.out, "backup_personal_" + datetime.now().strftime("%Y%m%d_%H%M%S")))
    report = {"serial": a.serial, "time": datetime.now().isoformat(timespec="seconds"), "baseline": {}, "runs": []}

    try:
        # ---- 1. baseline: no learning ----
        base_dec = {}  # mode -> {(title, text): shown}
        for mode in a.modes:
            print(f"[baseline] {mode}: replaying {len(golden) + len(holdout)} notifications ...", flush=True)
            rows = run_pass(adb, mode, golden + holdout, a.delay, a.settle)
            base_dec[mode] = decisions(rows)
        for mode in a.modes:
            wrong = [(k, v) for k, v in base_dec[mode].items()]
            by = {}
            for it in relabel(golden, "standard"):
                if key(it) in base_dec[mode] and base_dec[mode][key(it)][0] != (mode in it["urgentIn"]):
                    who = g.decided_by(base_dec[mode][key(it)][1])
                    by[who] = by.get(who, 0) + 1
            print(f"[baseline] {mode}: golden mistakes by decider {by}", flush=True)
            report["baseline"].setdefault("mistakes_by_decider", {})[mode] = by
        for persona in a.personas:
            res = {"golden": [], "holdout": []}
            for mode in a.modes:
                for name, items in (("golden", golden), ("holdout", holdout)):
                    rows = [(t, x, u, e) for (t, x), (u, e) in base_dec[mode].items()]
                    res[name].append(g.score(relabel(items, persona), rows, mode))
            report["baseline"][persona] = {n: macro(r, "f1") for n, r in res.items()}
            print(f"[baseline] persona={persona}: golden F1={report['baseline'][persona]['golden']:.3f} "
                  f"holdout F1={report['baseline'][persona]['holdout']:.3f}", flush=True)

        # ---- 2/3. teach corrections, replay unseen + sampled ----
        for persona in a.personas:
            gold_p = relabel(golden, persona)
            hold_p = relabel(holdout, persona)
            for variant in a.variants:
                for n in a.sizes:
                    for seed_ in a.seeds:
                        per_mode, sampled_before, sampled_after, personalised_rows = [], [], [], 0
                        for mode in a.modes:
                            rng = random.Random(f"{persona}-{variant}-{n}-{seed_}-{mode}")
                            pool = gold_p
                            if variant == "errors":
                                # Only mistakes the embedding model made: exceptions and rules decide before it,
                                # so the personal model cannot change those.
                                pool = [it for it in gold_p if key(it) in base_dec[mode]
                                        and base_dec[mode][key(it)][0] != (mode in it["urgentIn"])
                                        and "AI Pipeline" in (base_dec[mode][key(it)][1] or "")]
                            sample = rng.sample(pool, min(n, len(pool)))
                            corrections = [{"title": it["title"], "text": it["text"], "mode": mode,
                                            "urgent": mode in it["urgentIn"]} for it in sample]
                            tag = f"{persona}/{variant}/N={n}/seed={seed_}"
                            print(f"[{tag}] {mode}: {len(corrections)} corrections, replaying {len(hold_p) + len(sample)} ...", flush=True)
                            rows = run_pass(adb, mode, hold_p + sample, a.delay, a.settle, corrections or None)
                            per_mode.append(g.score(hold_p, rows, mode))
                            if sample:
                                after = g.score(sample, rows, mode)
                                before_rows = [(t, x, u, e) for (t, x), (u, e) in base_dec[mode].items()]
                                before = g.score(sample, before_rows, mode)
                                sampled_before.append(before["accuracy"]); sampled_after.append(after["accuracy"])
                            personalised_rows += personalised(rows)
                        run = {"persona": persona, "variant": variant, "size": n, "seed": seed_,
                               "holdout_f1": macro(per_mode, "f1"), "holdout_precision": macro(per_mode, "precision"),
                               "holdout_recall": macro(per_mode, "recall"), "missing": sum(r["missing"] for r in per_mode),
                               "per_mode_f1": {r["mode"]: round(r["f1"], 3) for r in per_mode},
                               "sampled_accuracy_before": statistics.mean(sampled_before) if sampled_before else None,
                               "sampled_accuracy_after": statistics.mean(sampled_after) if sampled_after else None,
                               "decisions_explained_by_personal_model": personalised_rows}
                        report["runs"].append(run)
                        base = report["baseline"][persona]["holdout"]
                        print(f"[{persona}/{variant}/N={n}/seed={seed_}] holdout F1 {run['holdout_f1']:.3f} "
                              f"(baseline {base:.3f}, {run['holdout_f1'] - base:+.3f}); sampled accuracy "
                              f"{run['sampled_accuracy_before']} -> {run['sampled_accuracy_after']}; "
                              f"missing={run['missing']}", flush=True)
    finally:
        g.restore_state(adb, saved)

    print("\n=== SUMMARY: holdout macro F1, mean over seeds (min..max), vs the no-learning baseline ===")
    groups = {}
    for r in report["runs"]:
        groups.setdefault((r["persona"], r["variant"], r["size"]), []).append(r)
    for (persona, variant, n), runs in sorted(groups.items()):
        f1s = [r["holdout_f1"] for r in runs]
        base = report["baseline"][persona]["holdout"]
        sb = [r["sampled_accuracy_before"] for r in runs if r["sampled_accuracy_before"] is not None]
        sa = [r["sampled_accuracy_after"] for r in runs if r["sampled_accuracy_after"] is not None]
        print(f"{persona:8s} {variant:7s} N={n:<3d} F1 {statistics.mean(f1s):.3f} ({min(f1s):.3f}..{max(f1s):.3f}) "
              f"baseline {base:.3f}  delta {statistics.mean(f1s) - base:+.3f}  "
              f"sampled accuracy {statistics.mean(sb) if sb else float('nan'):.2f} -> {statistics.mean(sa) if sa else float('nan'):.2f}")
    os.makedirs(a.out, exist_ok=True)
    path = os.path.join(a.out, datetime.now().strftime("personal_%Y%m%d_%H%M%S.json"))
    json.dump(report, open(path, "w", encoding="utf-8"), indent=2, ensure_ascii=False)
    print("report:", path)


if __name__ == "__main__":
    main()
