"""General round for Track B: python -m ml.data.general_round <generate|assemble> [lang ...]

Round 7 adds training items for every rubric use case in the languages the round-6 model scores lowest, weighted towards
routine look-alikes (hard negatives and near misses), which drive the Commute and Focus false positives. Training split
only: the golden holdout and the safety holdout are unchanged, so round 7 is measured on the same items as round 6.
Writes testdata/golden_v2/round7/{raw,assembled,agreed}/<lang>.jsonl. Same privacy rules as ml/data/AUTHORING.md.
"""
import json
import os
import random
import sys
from concurrent.futures import ThreadPoolExecutor

from ml.data import assemble, schema
from ml.data.agy_scale import NO_TOOLS, agy, json_lines
from ml.data.safety_round import STYLE

ROOT = os.path.join("testdata", "golden_v2", "round7")
TAG = "r7"
PER_LANG = 240
BATCH = 25
WORKERS = 4
START = 80000
PROVENANCE = "authored-r7-general-agy-v1"
DEFAULT_LANGS = ["ar", "ta", "ru", "gu", "id", "es"]


def slots_for(lang, need, rubric, catalogue, rng):
    homes = set(schema.LANGS[lang][2])
    usecases = rubric["usecases"]
    slots = []
    for _ in range(need):
        uc = rng.choice(usecases)
        urgent = bool(schema.expected_label(uc["id"], rubric))
        pool = [a for a in catalogue.values() if a["category"] in uc["apps"]]
        local = [a for a in pool if homes & set(a["regions"])]
        app = rng.choice(local or pool)
        # routine use cases are kept for hard negatives and near misses; urgent ones are clear positives
        difficulty = "easy" if urgent else rng.choice(["hard_negative", "near_miss"])
        slots.append({"usecase": uc["id"], "package": app["package"], "app_name": app["name"],
                      "app_category": app["category"], "sender_type": rng.choice(uc["senders"]),
                      "difficulty": difficulty, "region": rng.choice(sorted(homes)), "use_case_desc": uc["desc"]})
    return slots


def prompt_for(lang, slots):
    name = schema.LANGS[lang][0]
    lines = [json.dumps({"n": i + 1, "app": s["app_name"], "package": s["package"], "category": s["app_category"],
                         "sender_type": s["sender_type"], "use_case": s["usecase"], "meaning": s["use_case_desc"]},
                        ensure_ascii=False) for i, s in enumerate(slots)]
    return (NO_TOOLS + f"Write {len(slots)} synthetic smartphone notification samples in {name} for a training dataset. "
            "All content is invented: fictional names, phone numbers only in +1 555-01xx, links only on example.com, "
            "no OTP or 2FA codes, no real people or organisations.\n"
            "Write natural text as a native speaker would for that app (not a translation). " + STYLE +
            "One slot per line below. Output ONLY one JSON object per slot, in the same order, one per line, no markdown:\n"
            '{"title": "...", "text": "...", "gloss_title": "...", "gloss_text": "..."}\n'
            "gloss_* is a short faithful English gloss. The notification must match its use_case meaning; for hard_negative "
            "and near_miss slots it must look like the use case but be routine or from the wrong sender.\nSlots:\n"
            + "\n".join(lines))


def generate(lang, rubric, catalogue):
    rng = random.Random(f"{TAG}-{lang}")
    jobs = [slots_for(lang, min(BATCH, PER_LANG - s), rubric, catalogue, rng) for s in range(0, PER_LANG, BATCH)]
    os.makedirs(os.path.join(ROOT, "raw"), exist_ok=True)
    kept = []

    def run(slots):
        return slots, json_lines(agy(prompt_for(lang, slots)))

    with ThreadPoolExecutor(WORKERS) as pool:
        for slots, rows in pool.map(run, jobs):
            for k, slot in enumerate(slots):
                if k >= len(rows) or not rows[k].get("title") or not rows[k].get("text"):
                    continue
                kept.append({"split": "train", "region": slot["region"], "package": slot["package"],
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
    good, bad = assemble.assemble(schema.load_jsonl(raw_path), lang, PROVENANCE, rubric, catalogue, start=START)
    for folder in ("assembled", "agreed"):
        os.makedirs(os.path.join(ROOT, folder), exist_ok=True)
    schema.dump_jsonl(good, os.path.join(ROOT, "assembled", f"{lang}.jsonl"))
    schema.dump_jsonl(good, os.path.join(ROOT, "agreed", f"{lang}.jsonl"))
    print(f"{lang}: {len(good)} accepted, {len(bad)} rejected", flush=True)


def main():
    cmd, langs = sys.argv[1], sys.argv[2:] or DEFAULT_LANGS
    rubric, catalogue = schema.load_rubric(), schema.load_catalogue()
    for lang in langs:
        if cmd == "generate":
            generate(lang, rubric, catalogue)
        elif cmd == "assemble":
            assemble_lang(lang, rubric, catalogue)


if __name__ == "__main__":
    main()
