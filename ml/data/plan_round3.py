"""Round 3 quotas for the coverage gaps: python -m ml.data.plan_round3 <items-dir>

Writes testdata/golden_v2/round3/quotas.json and prints the totals per gap. Gaps covered:
  shortcut  apps with 5+ items that all carry one label get 2 counter-examples each, of the opposite label
  missing   use cases with no items in a language get 6 items there (the five thin use cases)
  long      300 longer texts (group-chat or multi-sentence) spread over the 18 languages
  regional  apps the dataset never uses get 2 items in their first home-region language
"""
import glob
import json
import os
import sys
from collections import Counter, defaultdict

from ml.data import schema

OUT = os.path.join("testdata", "golden_v2", "round3", "quotas.json")
THIN_USECASES = ["visitor_gate", "voicemail_routine", "investment_alert", "caption_status", "system_routine"]
PER_MISSING_PAIR = 6
LONG_TOTAL = 300
COUNTER_PER_APP = 2
REGIONAL_PER_APP = 2


def home_lang(regions):
    for r in regions:
        for lang, (_, _, homes) in schema.LANGS.items():
            if lang != "en" and r in homes:
                return lang
    return "en"


def load_items(items_dir):
    items = []
    for path in sorted(glob.glob(os.path.join(items_dir, "*.jsonl"))):
        items += schema.load_jsonl(path)
    return items


def plan(items, rubric, catalogue):
    langs = list(schema.LANGS)
    quotas = {lang: {"shortcut": [], "missing": [], "long": 0, "regional": []} for lang in langs}
    by_app = defaultdict(list)
    for i in items:
        by_app[i["package"]].append(tuple(i["urgentIn"]))
    usecases = rubric["usecases"]
    urgent_ucs = [u for u in usecases if schema.expected_label(u["id"], rubric)]
    muted_ucs = [u for u in usecases if not schema.expected_label(u["id"], rubric)]

    # shortcut: 2 counter-examples per single-label app, in the app's home language
    shortcut_apps = sorted(p for p, labels in by_app.items() if len(set(labels)) == 1 and len(labels) >= 5)
    for p in shortcut_apps:
        muted_only = next(iter(set(by_app[p]))) == ()
        pool = urgent_ucs if muted_only else muted_ucs
        in_cat = [u for u in pool if catalogue[p]["category"] in u["apps"]]
        uc = (in_cat or pool)[0]
        for _ in range(COUNTER_PER_APP):
            # spread evenly over languages so no single language carries the app-label signal
            lang = min(langs, key=lambda l: (len(quotas[l]["shortcut"]), langs.index(l)))
            quotas[lang]["shortcut"].append({"package": p, "name": catalogue[p]["name"],
                                             "polarity": "urgent" if muted_only else "muted", "usecase": uc["id"]})

    # missing: 6 items per (thin use case, language) with none
    have = Counter((i["lang"], i["usecase"]) for i in items)
    for uc in THIN_USECASES:
        for lang in langs:
            if have[(lang, uc)] == 0:
                quotas[lang]["missing"].append({"usecase": uc, "count": PER_MISSING_PAIR})

    # long: community/group use cases, spread evenly
    group_ucs = [u["id"] for u in usecases if u["family"] == "community_group"]
    per_lang, extra = divmod(LONG_TOTAL, len(langs))
    for n, lang in enumerate(langs):
        quotas[lang]["long"] = per_lang + (1 if n < extra else 0)
        quotas[lang]["long_usecases"] = group_ucs

    # regional: apps never used
    used = set(by_app)
    for p, app in sorted(catalogue.items()):
        if p in used:
            continue
        lang = home_lang(app["regions"])
        quotas[lang]["regional"].append({"package": p, "name": app["name"], "category": app["category"],
                                         "count": REGIONAL_PER_APP})
    return {"shortcut_apps": len(shortcut_apps), "quotas": quotas}


def totals(quotas):
    s = sum(len(q["shortcut"]) for q in quotas.values())
    m = sum(c["count"] for q in quotas.values() for c in q["missing"])
    l = sum(q["long"] for q in quotas.values())
    r = sum(a["count"] for q in quotas.values() for a in q["regional"])
    return s, m, l, r


def main(items_dir):
    rubric, catalogue = schema.load_rubric(), schema.load_catalogue()
    result = plan(load_items(items_dir), rubric, catalogue)
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8", newline="\n") as f:
        json.dump(result, f, ensure_ascii=False, indent=1)
    s, m, l, r = totals(result["quotas"])
    print(f"shortcut {s} items for {result['shortcut_apps']} apps; missing {m}; long {l}; regional {r}; "
          f"total {s + m + l + r}")
    print(f"wrote {OUT}")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else os.path.join("ml", "review", "combined_items"))
