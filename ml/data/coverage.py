"""Coverage and weakness report (stage S4b): python -m ml.data.coverage [items_dir] [out.md]

Counts what the dataset covers, flags thin or missing areas, and ranks the gaps with a proposed fix and size, so the
user can choose what to fix before any training. Pure counting: no model is involved.
"""
import glob
import os
import sys
from collections import Counter, defaultdict

from ml.data import schema, validate

ROOT = os.path.join("testdata", "golden_v2")
PLAN = {  # full-corpus plan per language (from the approved plan): test 300, dev 150, training about 3,800
    "test": 300, "dev": 150, "train": 3800,
}
SAFETY = ["weather_severe", "earthquake_alert", "tsunami_flood_wildfire", "public_safety_alert",
          "community_safety_notice", "home_alarm_critical", "sound_alert_critical"]
CALLS = ["call_missed_family", "call_missed_contact", "call_unknown", "call_spam_likely", "call_scam"]
PLANNED_SHARE = {  # family -> planned share of items, from the plan's use-case breakdown (percent)
    "personal_life": 13, "community_group": 9, "phone_calls": 8, "spam_scam": 13, "emergency_broadcast": 6,
    "banking_payments": 7, "delivery_ride": 8, "subscriptions": 4, "media_recommendations": 5, "social": 5,
    "work": 6, "smart_home_security": 3, "live_captions_accessibility": 2, "news_weather_sports": 4,
    "travel": 2, "health_fitness": 1.5, "games_education": 2, "system": 1.5,
}


def load(items_dir):
    items = []
    for p in sorted(glob.glob(os.path.join(items_dir, "*.jsonl"))):
        if not p.endswith(".rejected.jsonl"):
            items.extend(schema.load_jsonl(p))
    return items


def table(header, rows):
    out = ["| " + " | ".join(header) + " |", "|" + "|".join("---" for _ in header) + "|"]
    out += ["| " + " | ".join(str(c) for c in r) + " |" for r in rows]
    return "\n".join(out)


def main(items_dir, out_path, contested_path):
    rubric, catalogue = schema.load_rubric(), schema.load_catalogue()
    items = load(items_dir)
    langs = sorted({i["lang"] for i in items})
    by_lang = defaultdict(list)
    for i in items:
        by_lang[i["lang"]].append(i)
    fam = {u["id"]: u["family"] for u in rubric["usecases"]}
    lines = [f"# Coverage and weakness report\n", f"Generated from `{items_dir}`: **{len(items)} items**, {len(langs)} languages. "
             "This is a count-based check of what the data covers; it does not involve any model.\n"]

    # 1. progress against the plan
    split_counts = {l: Counter(i["split"] for i in by_lang[l]) for l in langs}
    rows = [(l, schema.LANGS[l][0], len(by_lang[l]), split_counts[l]["test"], split_counts[l]["dev"],
             f"{len(by_lang[l]) / (PLAN['test'] + PLAN['dev']):.0%}") for l in langs]
    lines += ["## 1. Size against the plan\n",
              f"Plan per language: {PLAN['test']} test + {PLAN['dev']} dev + about {PLAN['train']} training (the training set and "
              "challenge sets are not authored yet).\n",
              table(["lang", "language", "items now", "test", "dev", "of test+dev plan"], rows), ""]

    # 2. use-case coverage
    uc_lang = defaultdict(Counter)
    for i in items:
        uc_lang[i["usecase"]][i["lang"]] += 1
    missing_rows, thin = [], []
    for u in rubric["usecases"]:
        have = [l for l in langs if uc_lang[u["id"]][l] > 0]
        miss = [l for l in langs if uc_lang[u["id"]][l] == 0]
        total = sum(uc_lang[u["id"]].values())
        lab = ",".join(u["urgentIn"]) or "-"
        missing_rows.append((u["id"], u["family"], lab, total, len(have), ",".join(miss) if len(miss) <= 6 else f"{len(miss)} languages"))
        if len(have) < len(langs) * 0.5:
            thin.append((u["id"], len(have), total))
    lines += ["## 2. Use-case coverage across languages\n",
              "`urgent in` uses C F D S W for Commute, Focus, DND, Sleeping, Workout. Use cases that appear in fewer than half the "
              "languages are flagged below.\n",
              table(["use case", "family", "urgent in", "items", "languages with it", "missing in"], missing_rows), ""]

    # 3. family shares vs plan
    fam_count = Counter(fam[i["usecase"]] for i in items)
    rows = [(f, f"{100 * fam_count[f] / len(items):.1f}%", f"{p}%", f"{100 * fam_count[f] / len(items) - p:+.1f}")
            for f, p in PLANNED_SHARE.items()]
    other = {f: c for f, c in fam_count.items() if f not in PLANNED_SHARE}
    lines += ["## 3. Use-case family share against the plan\n", table(["family", "now", "plan", "difference (points)"], rows)]
    if other:
        lines.append("\nFamilies not in the plan: " + ", ".join(f"{k} ({v})" for k, v in other.items()))
    lines.append("")

    # 4. label balance
    mode_names = list(rubric["modes"].values())
    rows = []
    for l in langs:
        n = len(by_lang[l])
        urgent = sum(1 for i in by_lang[l] if i["urgentIn"])
        rows.append([l, f"{urgent / n:.0%}"] + [f"{sum(1 for i in by_lang[l] if m in i['urgentIn']) / n:.0%}" for m in mode_names])
    lines += ["## 4. Label balance (share of items shown)\n",
              table(["lang", "urgent in any mode"] + [m.split(" (")[0] for m in mode_names], rows), ""]

    # 5. difficulty and sender mix
    diff = Counter(i["difficulty"] for i in items)
    sender = Counter(i["sender_type"] for i in items)
    lines += ["## 5. Difficulty and sender mix\n",
              "Difficulty: " + ", ".join(f"{k} {v} ({v / len(items):.0%})" for k, v in diff.most_common()) + ".\n",
              "Sender type: " + ", ".join(f"{k} {v}" for k, v in sender.most_common()) + ".\n"]

    # 6. safety-critical coverage
    rows = [[l] + [uc_lang[u][l] for u in SAFETY] for l in langs]
    lines += ["## 6. Safety-critical coverage per language (items per use case)\n",
              table(["lang"] + SAFETY, rows), ""]
    rows = [[l] + [uc_lang[u][l] for u in CALLS] for l in langs]
    lines += ["## 7. Phone-call types per language\n", table(["lang"] + CALLS, rows), ""]

    # 8. app diversity and shortcuts
    apps_by_lang = {l: len({i["package"] for i in by_lang[l]}) for l in langs}
    cat = Counter(i["app_category"] for i in items)
    all_cats = sorted({a["category"] for a in catalogue.values()})
    unused = [c for c in all_cats if cat[c] == 0]
    pack_label = defaultdict(Counter)
    for i in items:
        pack_label[i["package"]][tuple(i["urgentIn"])] += 1
    shortcuts = sorted(((p, sum(c.values()), "muted" if not next(iter(c)) else "urgent")
                        for p, c in pack_label.items() if len(c) == 1 and sum(c.values()) >= 5), key=lambda x: -x[1])
    used_apps = len({i["package"] for i in items})
    lines += ["## 8. App coverage and shortcut risk\n",
              f"Distinct apps used: **{used_apps} of {len(catalogue)}** in the catalogue. Per language: "
              + ", ".join(f"{l} {n}" for l, n in apps_by_lang.items()) + ".\n",
              "App categories with no items: " + (", ".join(unused) or "none") + ".\n",
              f"Apps whose items all carry one label (a model could learn the app instead of the text): {len(shortcuts)}. Largest: "
              + ", ".join(f"{catalogue[p]['name']} ({n}, {lab})" for p, n, lab in shortcuts[:10]) + ".\n"]

    # 9. length
    lengths = sorted(len(i["text"]) for i in items)
    lines += ["## 9. Text length (characters)\n",
              f"median {lengths[len(lengths) // 2]}, 90th percentile {lengths[int(len(lengths) * 0.9)]}, "
              f"longest {lengths[-1]}; under 40 characters: {sum(1 for x in lengths if x < 40)} items, over 200: "
              f"{sum(1 for x in lengths if x > 200)} items.\n"]

    # 10. ranked gaps
    gaps = []
    for u in SAFETY:
        miss = [l for l in langs if uc_lang[u][l] == 0]
        if miss:
            gaps.append((1, f"Safety use case `{u}` has no items in {len(miss)} languages: {', '.join(miss)}",
                         f"add about 20 per language across 4+ apps ({20 * len(miss)} items)", "safety-critical"))
    for u in ("call_unknown", "call_spam_likely", "call_scam", "call_missed_family"):
        few = [l for l in langs if uc_lang[u][l] < 2]
        if few:
            gaps.append((2, f"`{u}` has fewer than 2 items in {len(few)} languages: {', '.join(few)}",
                         f"add about 15 per language ({15 * len(few)} items)", "calls"))
    for u, n_have, total in thin:
        gaps.append((2, f"Use case `{u}` appears in only {n_have} of {len(langs)} languages ({total} items)",
                     "add about 12 per language in the missing languages", "coverage"))
    for c in unused:
        gaps.append((3, f"App category `{c}` has no items", "add items from 3+ apps of this category across languages", "coverage"))
    if shortcuts:
        names = ", ".join(f"{catalogue[p]['name']} ({lab})" for p, n, lab in shortcuts[:8])
        gaps.append((1, f"{len(shortcuts)} apps always carry the same label ({names}...), so a model could learn the app instead of the text",
                     "add counter-examples per app (a muted Maps or GitHub notification, a Spotify item that is not a recommendation) and hold some apps out", "shortcut"))
    long_share = sum(1 for x in lengths if x > 120) / len(lengths)
    gaps.append((2, f"Texts are short: median {lengths[len(lengths) // 2]} characters, {long_share:.0%} over 120, none over 200",
                 "add longer, group-chat and multi-sentence items (the plan's 300-item long and group challenge set)", "diversity"))
    gaps.append((2, f"Only {used_apps} of {len(catalogue)} catalogue apps are used; about 95 regional apps were added after authoring started",
                 "author round 2 around the regional apps for each language (Japan, Russia, Brazil, Indonesia, Germany, France, Gulf, China, India)", "diversity"))
    gaps.append((1, "No training set yet (about 72,000 items planned)", "author about 24,000 base items, then deterministic augmentation", "structural"))
    gaps.append((1, f"Test and dev are at {len(items)} items against about 8,100 planned", "author about 450 per language (300 test, 150 dev) in rounds with blind relabeling", "structural"))
    gaps.append((1, "No held-out apps (about 15% of apps never used in training) and no challenge sets yet", "split the catalogue now, then author the 2,100-item challenge sets", "structural"))
    gaps.append((2, "Near-miss items are often filed under the urgent use case (found in the blind relabel)", "add a validator warning for near_miss rows whose use case is urgent, and brief authors again", "quality"))
    gaps.append((2, "Rubric conflicts: vip_ping (Commute+Focus) vs 'workplace urgency is Focus only'; booking disruption; clinic 'urgent consult' wording", "decide the label for these boundary cases (see contested file) and update rubric.json", "rubric"))
    gaps.append((2, "Model baselines not run on golden_v2 yet (keyword and current embedding model)", "port the keyword engine and run both on dev/test to find areas where both fail", "diagnosis"))
    gaps.sort(key=lambda g: g[0])
    sev = {1: "high", 2: "medium", 3: "low"}
    lines += ["## 10. Ranked gaps and proposed fixes\n",
              table(["severity", "kind", "gap", "proposed fix"], [(sev[g[0]], g[3], g[1], g[2]) for g in gaps]), ""]

    if os.path.exists(contested_path):
        c = schema.load_jsonl(contested_path)
        lines += [f"## 11. Contested items\n", f"{len(c)} items are kept out of scoring until you decide their label (file `{contested_path}`).\n"]

    with open(out_path, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(lines) + "\n")
    print(f"wrote {out_path}: {len(items)} items, {len(gaps)} ranked gaps")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else os.path.join(ROOT, "pilot_agreed"),
         sys.argv[2] if len(sys.argv) > 2 else os.path.join(ROOT, "COVERAGE_REPORT.md"),
         sys.argv[3] if len(sys.argv) > 3 else os.path.join(ROOT, "contested_pilot.jsonl"))
