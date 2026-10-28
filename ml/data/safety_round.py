"""Safety-focused round for Track B: python -m ml.data.safety_round <generate|assemble> [lang ...]

generate  asks agy for synthetic safety notices (weather, earthquake, flood, community alerts, alarms, health, school and
          medical emergencies), plus routine look-alikes (a storm sale, a health tip) as hard negatives. Writes
          testdata/golden_v2/round5/raw/<lang>.jsonl. Items in the `train` split feed training; items in the `test` split
          are a fresh safety holdout that training never sees.
assemble  runs the same validation gate as the earlier rounds and writes round5/assembled/<lang>.jsonl, then the
          `train` items to round5/agreed/<lang>.jsonl and the `test` items to round5/safety_holdout/<lang>.jsonl.

All content is invented, with the same privacy rules as the other rounds (see ml/data/AUTHORING.md).
"""
import json
import os
import random
import sys
from concurrent.futures import ThreadPoolExecutor

from ml.data import assemble, schema
from ml.data.agy_scale import NO_TOOLS, agy, json_lines

# round5 (the first safety round, with a fresh holdout) or round6 (more training items only; the holdout stays round5)
ROUND = os.environ.get("SAFETY_ROUND", "round5")
TAG = {"round5": "r5", "round6": "r6"}[ROUND]
ROOT = os.path.join("testdata", "golden_v2", ROUND)
SAFETY_USECASES = ["weather_severe", "earthquake_alert", "tsunami_flood_wildfire", "community_safety_notice",
                   "sound_alert_critical", "health_critical", "medical_urgent", "school_urgent", "home_security",
                   "home_alarm_critical", "family_distress"]
ROUTINE_USECASES = ["weather_routine", "health_routine", "medical_routine", "school_info", "home_routine"]
# how many items per language and split; hard negatives are about one in four
PLAN = {"train": {"safe": 120, "routine": 40}, "test": {"safe": 40, "routine": 12}}
if ROUND == "round6":  # about twice the safety training items of round 5, training split only
    PLAN["train"] = {"safe": 240, "routine": 80}
BATCH = 25
WORKERS = 4
PROVENANCE = f"authored-{TAG}-safety-agy-v1"
START = {"round5": {"train": 50000, "test": 60000}, "round6": {"train": 70000}}[ROUND]
SPLITS = tuple(START)
STYLE = ("Vary the wording a lot: some alerts are one short imperative sentence (for example a shelter or evacuation "
         "instruction), some are longer and read like a real official or app message, some use no capital letters "
         "or emoji. Do not repeat the same phrases across items.\n")


def slots_for(lang, split, need, rubric, catalogue, rng):
    homes = set(schema.LANGS[lang][2])
    slots = []
    for _ in range(need):
        pick_safe = rng.random() < PLAN[split]["safe"] / (PLAN[split]["safe"] + PLAN[split]["routine"])
        names = SAFETY_USECASES if pick_safe else ROUTINE_USECASES
        uc = rubric["by_id"][rng.choice(names)]
        pool = [a for a in catalogue.values() if a["category"] in uc["apps"]]
        local = [a for a in pool if homes & set(a["regions"])]
        app = rng.choice(local or pool)
        slots.append({"split": split, "usecase": uc["id"], "package": app["package"], "app_name": app["name"],
                      "app_category": app["category"], "sender_type": rng.choice(uc["senders"]),
                      "difficulty": "easy" if pick_safe else "hard_negative",
                      "region": rng.choice(sorted(homes)), "use_case_desc": uc["desc"]})
    return slots


def prompt_for(lang, slots):
    name = schema.LANGS[lang][0]
    lines = [json.dumps({"n": i + 1, "app": s["app_name"], "package": s["package"], "category": s["app_category"],
                         "sender_type": s["sender_type"], "use_case": s["usecase"], "meaning": s["use_case_desc"]},
                        ensure_ascii=False) for i, s in enumerate(slots)]
    return (NO_TOOLS + f"Write {len(slots)} synthetic smartphone notification samples in {name} for a test dataset. "
            "All content is invented: fictional names, phone numbers only in +1 555-01xx, links only on example.com, "
            "no OTP or 2FA codes, no real people or organisations, no real places named as the source of an emergency.\n"
            "Write natural text as a native speaker would for that app (not a translation). " + STYLE +
            "One slot per line below. Output ONLY one JSON object per slot, in the same order, one per line, no markdown:\n"
            '{"title": "...", "text": "...", "gloss_title": "...", "gloss_text": "..."}\n'
            "gloss_* is a short faithful English gloss. Each slot's notification must match its use_case meaning.\n"
            "Slots:\n" + "\n".join(lines))


def generate(lang, rubric, catalogue):
    rng = random.Random(f"{TAG}-{lang}")
    jobs = []
    for split in SPLITS:
        need = PLAN[split]["safe"] + PLAN[split]["routine"]
        for start in range(0, need, BATCH):
            jobs.append(slots_for(lang, split, min(BATCH, need - start), rubric, catalogue, rng))
    os.makedirs(os.path.join(ROOT, "raw"), exist_ok=True)
    kept = []

    def run(slots):
        return slots, json_lines(agy(prompt_for(lang, slots)))

    with ThreadPoolExecutor(WORKERS) as pool:
        for slots, rows in pool.map(run, jobs):
            for k, slot in enumerate(slots):
                if k >= len(rows) or not rows[k].get("title") or not rows[k].get("text"):
                    continue
                kept.append({"split": slot["split"], "region": slot["region"], "package": slot["package"],
                             "usecase": slot["usecase"], "sender_type": slot["sender_type"],
                             "difficulty": slot["difficulty"], "title": rows[k]["title"], "text": rows[k]["text"],
                             "template_family": f"{TAG}-{lang}-{slot['usecase']}-{len(kept)}",
                             "gloss_title": rows[k].get("gloss_title", ""), "gloss_text": rows[k].get("gloss_text", "")})
    schema.dump_jsonl(kept, os.path.join(ROOT, "raw", f"{lang}.jsonl"))
    print(f"{lang}: {len(kept)} raw items from {len(jobs)} batches", flush=True)


def assemble_lang(lang, rubric, catalogue):
    raw_path = os.path.join(ROOT, "raw", f"{lang}.jsonl")
    if not os.path.exists(raw_path):
        return
    good = []
    for split, start in START.items():
        authored = [r for r in schema.load_jsonl(raw_path) if r["split"] == split]
        accepted, rejected = assemble.assemble(authored, lang, PROVENANCE, rubric, catalogue, start=start)
        good += accepted
        print(f"{lang} {split}: {len(accepted)} accepted, {len(rejected)} rejected", flush=True)
    for folder in ("assembled", "agreed", "safety_holdout"):
        os.makedirs(os.path.join(ROOT, folder), exist_ok=True)
    schema.dump_jsonl(good, os.path.join(ROOT, "assembled", f"{lang}.jsonl"))
    schema.dump_jsonl([i for i in good if i["split"] == "train"],
                      os.path.join(ROOT, "agreed", f"{lang}.jsonl"))
    if ROUND == "round5":  # the fixed safety holdout is only written for round 5
        schema.dump_jsonl([i for i in good if i["split"] == "test"],
                          os.path.join(ROOT, "safety_holdout", f"{lang}.jsonl"))


def main():
    cmd, langs = sys.argv[1], sys.argv[2:] or ["en", "es", "de", "fr", "hi", "ja"]
    rubric, catalogue = schema.load_rubric(), schema.load_catalogue()
    for lang in langs:
        if cmd == "generate":
            generate(lang, rubric, catalogue)
        elif cmd == "assemble":
            assemble_lang(lang, rubric, catalogue)


if __name__ == "__main__":
    main()
