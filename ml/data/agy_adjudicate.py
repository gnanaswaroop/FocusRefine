"""Adjudicate contested items with Antigravity: python -m ml.data.agy_adjudicate <contested.jsonl> <agreed-dir> <out-dir>

Each contested item has an author use case and a blind use case. agy picks one of the two against the rubric
description, or answers "neither". Picked items are appended to agreed-dir/<lang>.jsonl with the chosen use case and its
label; the rest are written to out-dir/unresolved.jsonl for a human decision.
"""
import json
import os
import sys
from concurrent.futures import ThreadPoolExecutor

from ml.data import agy_scale, schema

BATCH = 10


def prompt(batch, rubric):
    use = "\n".join(f'{u["id"]}: {u["desc"]}' for u in rubric["usecases"])
    rows = "\n".join(json.dumps({"id": d["item"]["id"], "app_name": d["item"]["app_name"],
                                 "sender_type": d["item"]["sender_type"], "title": d["item"]["title"],
                                 "text": d["item"]["text"], "option_a": d["item"]["usecase"],
                                 "option_b": d["blind_usecase"]}, ensure_ascii=False) for d in batch)
    return (agy_scale.NO_TOOLS + "For each notification, two use cases were proposed (option_a and option_b). "
            "Choose the one that matches the notification better, using these use case descriptions:\n" + use +
            "\n\nNotifications:\n" + rows +
            '\nOutput ONLY one JSON object per notification, one per line, no markdown: '
            '{"id": "<same id>", "choice": "option_a" | "option_b" | "neither", "reason": "<under 12 words>"}')


def main(contested_path, agreed_dir, out_dir):
    rubric = schema.load_rubric()
    rows = schema.load_jsonl(contested_path)
    batches = [rows[i:i + BATCH] for i in range(0, len(rows), BATCH)]
    with ThreadPoolExecutor(agy_scale.WORKERS) as pool:
        answers = list(pool.map(lambda b: agy_scale.json_lines(agy_scale.agy(prompt(b, rubric))), batches))
    by_id = {}
    for a in answers:
        for r in a:
            by_id[r.get("id")] = r
    accepted, unresolved = [], []
    for d in rows:
        item = dict(d["item"])
        r = by_id.get(item["id"], {})
        if r.get("choice") == "option_a":
            accepted.append(item)
        elif r.get("choice") == "option_b":
            item["usecase"] = d["blind_usecase"]
            item["urgentIn"] = schema.expected_label(d["blind_usecase"], rubric)
            accepted.append(item)
        else:
            unresolved.append({"item": d["item"], "blind_usecase": d["blind_usecase"], "agy": r})
    os.makedirs(out_dir, exist_ok=True)
    for lang in sorted({i["lang"] for i in accepted}):
        path = os.path.join(agreed_dir, f"{lang}.jsonl")
        rows_out = schema.load_jsonl(path) if os.path.exists(path) else []
        rows_out += [i for i in accepted if i["lang"] == lang]
        schema.dump_jsonl(rows_out, path)
    schema.dump_jsonl(unresolved, os.path.join(out_dir, "unresolved.jsonl"))
    print(f"accepted {len(accepted)} (author {sum(1 for r in by_id.values() if r.get('choice') == 'option_a')}, "
          f"blind {sum(1 for r in by_id.values() if r.get('choice') == 'option_b')}), unresolved {len(unresolved)}")


if __name__ == "__main__":
    main(*sys.argv[1:4])
