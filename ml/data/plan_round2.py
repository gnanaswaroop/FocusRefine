"""Tell an author exactly what to write in round 2 for one language: python -m ml.data.plan_round2 --lang hi

Counts what that language already has (testdata/golden_v2/pilot_agreed) and prints the quotas: missing safety use
cases, phone-call types, counter-examples for apps that only ever carry one label, and items built around regional
apps that have not been used yet, with some long and group-chat items.
"""
import argparse
import glob
import os
from collections import Counter, defaultdict

from ml.data import schema
from ml.data.coverage import SAFETY
from ml.data.options import apps_for_language

CALL_TYPES = ["call_unknown", "call_spam_likely", "call_scam", "call_missed_family"]
ROOT = os.path.join("testdata", "golden_v2")


def existing():
    items = []
    for p in glob.glob(os.path.join(ROOT, "pilot_agreed", "*.jsonl")):
        items.extend(schema.load_jsonl(p))
    return items


def main(lang):
    catalogue = schema.load_catalogue()
    items = existing()
    mine = [i for i in items if i["lang"] == lang]
    counts = Counter(i["usecase"] for i in mine)
    labels = defaultdict(set)
    for i in items:
        labels[i["package"]].add(bool(i["urgentIn"]))
    urgent_only = sorted({p for p, v in labels.items() if v == {True}})
    used = {i["package"] for i in mine}
    by_cat = apps_for_language(lang, catalogue)
    regional = [a for apps in by_cat.values() for a in apps
                if "GLOBAL" not in a["regions"] and a["package"] not in used]
    name = schema.LANGS[lang][0]
    print(f"ROUND 2 PLAN for {lang} ({name}). Regions allowed: {', '.join(schema.LANGS[lang][2])}.")
    print("Split rule: about 65% of your items `test`, the rest `dev`. Every item needs its own template_family.\n")
    todo = 0
    print("A. SAFETY (high priority). For each use case below that this language lacks, write 20 items:")
    print("   14 genuine ones of that use case (vary hazard, app, region, wording, length) plus 6 hard negatives or near misses")
    print("   that use the same hazard words in a routine context (a 'storm sale', a game 'flood event'), each filed under its")
    print("   correct ROUTINE use case, difficulty hard_negative or near_miss.")
    any_a = False
    for u in SAFETY:
        if counts[u] == 0:
            any_a = True
            todo += 20
            print(f"   - {u}: have 0, write 20")
    if not any_a:
        print("   (none missing in this language)")
    print("\nB. PHONE CALLS. Write this many of each use case (apps for calls: see options; include variety of wording, number masks, caller-id labels):")
    for u in CALL_TYPES:
        need = max(0, 15 - counts[u])
        todo += need
        print(f"   - {u}: have {counts[u]}, write {need}")
    print("\nC. COUNTER-EXAMPLES for apps that so far only appear with urgent labels. Write 12 ROUTINE items: 4 `maps_routine`")
    print("   (map or navigation apps), 4 `work_tool_routine`, 4 more routine items (work_chat_routine, health_routine, travel_routine,")
    print("   email_routine...) from these apps: " + ", ".join(catalogue[p]["name"] for p in urgent_only[:14]) + ".")
    todo += 12
    print("\nD. REGIONAL APPS AND LONGER TEXTS. Write 30 items across many use cases built around REGIONAL apps for this language")
    print("   (not used yet): " + "; ".join(f"{a['package']}={a['name']}" for a in regional[:30]) + (" ..." if len(regional) > 30 else "") + ".")
    print("   Of these 30: at least 10 LONG items (150 to 380 characters, two or three sentences, like a real bank, delivery or")
    print("   airline notice) and at least 5 group-chat style items ('Group name - Sender' titles, several lines).")
    todo += 30
    print(f"\nTOTAL to write: about {todo} items. Write them in two files so each Write call stays manageable:")
    print(f"   testdata/golden_v2/raw/{lang}.r2a.jsonl (parts A and B) and testdata/golden_v2/raw/{lang}.r2b.jsonl (parts C and D).")
    print("   Assemble each with:  python -m ml.data.assemble <raw file> testdata/golden_v2/pilot/{lang}.r2a.jsonl --lang " + lang + " --provenance authored-r2-v1 --start 1000")
    print("   (use --start 2000 and r2b names for the second file).")


if __name__ == "__main__":
    ap = argparse.ArgumentParser()
    ap.add_argument("--lang", required=True, choices=sorted(schema.LANGS))
    main(ap.parse_args().lang)
