"""Scale the dev and test sets with Antigravity (agy --print): python -m ml.data.agy_scale <generate|review|assemble> [lang ...]

generate  asks agy for batches of synthetic items (one batch = BATCH slots: a use case, an app and a sender each),
          parses the JSON lines, and writes testdata/golden_v2/round4/raw/<lang>.jsonl
assemble  runs the same assemble gate as round 2 and round 3 on the raw items, writing round4/assembled/<lang>.jsonl
review    the automated blind relabel: agy picks a use case per item without seeing the author's label; the output goes
          to round4/relabel_out/<lang>.jsonl and is compared with `python -m ml.relabel compare` (see the README)

Targets: 300 test and 150 dev items per language (ml/data/plan_round2.py has the plan).
"""
import json
import os
import random
import subprocess
import sys
from concurrent.futures import ThreadPoolExecutor

from ml.data import assemble, schema

ROOT = os.path.join("testdata", "golden_v2", "round4")
BATCH = 25
TARGET = {"test": 300, "dev": 150}
AGY_TIMEOUT = 240
REVIEW_BATCH = 10
WORKERS = 4
# headless agy auto-denies tool use, and a prompt that invites a tool call returns no text at all
NO_TOOLS = "Answer in plain text only. Do not run commands and do not use any tools.\n"


def existing_counts():
    import glob
    counts = {}
    for path in glob.glob(os.path.join("ml", "review", "combined_items", "*.jsonl")):
        for item in schema.load_jsonl(path):
            key = (item["lang"], item["split"])
            counts[key] = counts.get(key, 0) + 1
    return counts


def agy(prompt):
    for _ in range(2):
        try:
            out = subprocess.run(["agy", "--print", prompt, "--output-format", "text"],
                                 capture_output=True, text=True, encoding="utf-8", timeout=AGY_TIMEOUT)
            if out.returncode == 0 and out.stdout.strip():
                return out.stdout
        except subprocess.TimeoutExpired:
            pass
    return ""


def json_lines(text):
    rows = []
    for line in text.splitlines():
        line = line.strip().rstrip(",")
        if line.startswith("{") and line.endswith("}"):
            try:
                rows.append(json.loads(line))
            except json.JSONDecodeError:
                pass
    return rows


def slots_for(lang, need, rubric, catalogue, rng):
    homes = set(schema.LANGS[lang][2])
    usecases = rubric["usecases"]
    slots = []
    for k in range(need):
        uc = usecases[(k + rng.randrange(len(usecases))) % len(usecases)]
        pool = [a for a in catalogue.values() if uc["id"] and a["category"] in uc["apps"]]
        local = [a for a in pool if homes & set(a["regions"])]
        app = rng.choice(local or pool)
        urgent = bool(schema.expected_label(uc["id"], rubric))
        slots.append({"usecase": uc["id"], "package": app["package"], "app_name": app["name"],
                      "app_category": app["category"], "sender_type": rng.choice(uc["senders"]),
                      "difficulty": "easy" if urgent else rng.choice(["hard_negative", "near_miss"]),
                      "region": rng.choice(sorted(homes)), "use_case_desc": uc["desc"]})
    return slots


def generation_prompt(lang, name, slots):
    lines = [json.dumps({"n": i + 1, "app": s["app_name"], "package": s["package"], "category": s["app_category"],
                         "sender_type": s["sender_type"], "use_case": s["usecase"], "meaning": s["use_case_desc"]},
                        ensure_ascii=False) for i, s in enumerate(slots)]
    return (NO_TOOLS + f"Write {len(slots)} synthetic smartphone notification samples in {name} for a test dataset. "
            "All content is invented: fictional names, phone numbers only in +1 555-01xx, links only on example.com, "
            "no OTP or 2FA codes, no real people or organisations.\n"
            "Write natural text as a native speaker would for that app (not a translation). Vary length and style.\n"
            "One slot per line below. Output ONLY one JSON object per slot, in the same order, one per line, no markdown:\n"
            '{"title": "...", "text": "...", "gloss_title": "...", "gloss_text": "..."}\n'
            "gloss_* is a short faithful English gloss.\nSlots:\n" + "\n".join(lines))


def generate(lang, rubric, catalogue, have):
    name = schema.LANGS[lang][0]
    rng = random.Random(f"r4-{lang}")
    jobs = []
    for split in ("test", "dev"):
        need = max(0, TARGET[split] - have.get((lang, split), 0))
        for start in range(0, need, BATCH):
            size = min(BATCH, need - start)
            jobs.append((split, slots_for(lang, size, rubric, catalogue, rng)))
    os.makedirs(os.path.join(ROOT, "raw"), exist_ok=True)
    raw_path = os.path.join(ROOT, "raw", f"{lang}.jsonl")
    kept = []

    def run(job):
        split, slots = job
        return split, slots, json_lines(agy(generation_prompt(lang, name, slots)))

    with ThreadPoolExecutor(WORKERS) as pool:
        for split, slots, rows in pool.map(run, jobs):
            for k, slot in enumerate(slots):
                if k >= len(rows) or not rows[k].get("title") or not rows[k].get("text"):
                    continue
                kept.append({"split": split, "region": slot["region"], "package": slot["package"],
                             "usecase": slot["usecase"], "sender_type": slot["sender_type"],
                             "difficulty": slot["difficulty"], "title": rows[k]["title"], "text": rows[k]["text"],
                             "template_family": f"r4-{lang}-{slot['usecase']}-{len(kept)}",
                             "gloss_title": rows[k].get("gloss_title", ""), "gloss_text": rows[k].get("gloss_text", "")})
    schema.dump_jsonl(kept, raw_path)
    print(f"{lang}: {len(kept)} raw items from {len(jobs)} batches")


def assemble_lang(lang, rubric, catalogue):
    raw_path = os.path.join(ROOT, "raw", f"{lang}.jsonl")
    if not os.path.exists(raw_path):
        return
    authored = schema.load_jsonl(raw_path)
    good, bad = assemble.assemble(authored, lang, "authored-r4-agy-v1", rubric, catalogue, start=10000)
    os.makedirs(os.path.join(ROOT, "assembled"), exist_ok=True)
    schema.dump_jsonl(good, os.path.join(ROOT, "assembled", f"{lang}.jsonl"))
    gloss = {}
    for n, raw in enumerate(authored, 10000):
        gloss[f"{lang}-{raw['split']}-{n:05d}"] = raw
    os.makedirs(os.path.join(ROOT, "gloss"), exist_ok=True)
    gloss_rows = [{"id": i["id"], "gloss_title": gloss[i["id"]]["gloss_title"], "gloss_text": gloss[i["id"]]["gloss_text"]}
                  for i in good]
    schema.dump_jsonl(gloss_rows, os.path.join("testdata", "golden_v2", "gloss", f"{lang}.r4.jsonl"))
    print(f"{lang}: {len(good)} accepted, {len(bad)} rejected")


def review(lang, rubric):
    src = os.path.join(ROOT, "assembled", f"{lang}.jsonl")
    if not os.path.exists(src):
        return
    items = schema.load_jsonl(src)
    use_list = "\n".join(f'{u["id"]}: {u["desc"]}' for u in rubric["usecases"])
    batches = [items[i:i + REVIEW_BATCH] for i in range(0, len(items), REVIEW_BATCH)]

    def run(batch):
        slots = "\n".join(json.dumps({"id": it["id"], "app_name": it["app_name"], "sender_type": it["sender_type"],
                                      "title": it["title"], "text": it["text"]}, ensure_ascii=False) for it in batch)
        prompt = (NO_TOOLS + "You are a blind labeler. For each notification below, choose the single use case id from this list "
                  "that best describes it. Use only ids from the list.\n" + use_list + "\n\nNotifications:\n" + slots +
                  '\nOutput ONLY one JSON object per notification, one per line, no markdown: '
                  '{"id": "<same id>", "usecase": "<use case id>", "note": "<under 8 words>"}')
        return json_lines(agy(prompt))

    with ThreadPoolExecutor(WORKERS) as pool:
        results = list(pool.map(run, batches))
    out = []
    valid = {u["id"] for u in rubric["usecases"]}
    for batch, rows in zip(batches, results):
        by_id = {r.get("id"): r for r in rows}
        for it in batch:
            r = by_id.get(it["id"])
            if r and r.get("usecase") in valid:
                out.append({"id": it["id"], "usecase": r["usecase"], "note": r.get("note", "")})
    os.makedirs(os.path.join(ROOT, "relabel_out"), exist_ok=True)
    schema.dump_jsonl(out, os.path.join(ROOT, "relabel_out", f"{lang}.jsonl"))
    print(f"{lang}: {len(out)} of {len(items)} blind labels")


def main():
    cmd, langs = sys.argv[1], sys.argv[2:] or list(schema.LANGS)
    rubric, catalogue = schema.load_rubric(), schema.load_catalogue()
    have = existing_counts()
    for lang in langs:
        if cmd == "generate":
            generate(lang, rubric, catalogue, have)
        elif cmd == "assemble":
            assemble_lang(lang, rubric, catalogue)
        elif cmd == "review":
            review(lang, rubric)


if __name__ == "__main__":
    main()
