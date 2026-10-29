"""Blind second labeling (stage S3).

python -m ml.data.relabel prepare <assembled_dir> <blind_dir>
    Writes one file per language with only what a labeler should see: id, app, sender, title, text.
python -m ml.data.relabel compare <assembled_dir> <blind_out_dir> <agreed_dir> <contested_file>
    Compares the second labeler's use case (and so its label) with the author's. Items whose LABEL agrees are
    copied to <agreed_dir>; the rest go to <contested_file> with both answers. Prints agreement and Cohen's kappa.
"""
import glob
import json
import os
import sys
from collections import defaultdict

from ml.data import schema

BLIND_FIELDS = ("id", "app_name", "package", "app_category", "sender_type", "title", "text")


def prepare(src, dst):
    os.makedirs(dst, exist_ok=True)
    for path in sorted(glob.glob(os.path.join(src, "*.jsonl"))):
        if path.endswith(".rejected.jsonl"):
            continue
        items = schema.load_jsonl(path)
        out = [{k: i[k] for k in BLIND_FIELDS} for i in items]
        schema.dump_jsonl(out, os.path.join(dst, os.path.basename(path)))
        print(f"{os.path.basename(path)}: {len(out)} items for blind labeling")


def kappa(pairs):
    """Cohen's kappa for binary labels given as (a, b) booleans."""
    n = len(pairs)
    if n == 0:
        return float("nan")
    po = sum(1 for a, b in pairs if a == b) / n
    pa = sum(1 for a, _ in pairs if a) / n
    pb = sum(1 for _, b in pairs if b) / n
    pe = pa * pb + (1 - pa) * (1 - pb)
    return 1.0 if pe == 1 else (po - pe) / (1 - pe)


def compare(src, blind_out, agreed_dir, contested_file):
    rubric = schema.load_rubric()
    os.makedirs(agreed_dir, exist_ok=True)
    contested = []
    modes = list(rubric["modes"].values())
    totals = defaultdict(lambda: [0, 0])
    mode_pairs = defaultdict(list)
    for path in sorted(glob.glob(os.path.join(src, "*.jsonl"))):
        if path.endswith(".rejected.jsonl"):
            continue
        name = os.path.basename(path)
        lang = name[:-6]
        blind_path = os.path.join(blind_out, name)
        if not os.path.exists(blind_path):
            print(f"{lang}: no blind labels yet, skipped")
            continue
        blind = {b["id"]: b for b in schema.load_jsonl(blind_path)}
        agreed = []
        for item in schema.load_jsonl(path):
            b = blind.get(item["id"])
            if b is None or b.get("usecase") not in rubric["by_id"]:
                contested.append({"item": item, "blind_usecase": (b or {}).get("usecase"), "reason": "no valid blind label"})
                totals[lang][1] += 1
                continue
            blind_label = schema.expected_label(b["usecase"], rubric)
            for m in modes:
                mode_pairs[m].append((m in item["urgentIn"], m in blind_label))
            totals[lang][1] += 1
            if blind_label == item["urgentIn"]:
                totals[lang][0] += 1
                agreed.append(item)
            else:
                contested.append({"item": item, "blind_usecase": b["usecase"], "blind_label": blind_label,
                                  "blind_note": b.get("note", ""), "reason": "label disagreement"})
        schema.dump_jsonl(agreed, os.path.join(agreed_dir, name))
    with open(contested_file, "w", encoding="utf-8", newline="\n") as f:
        for c in contested:
            f.write(json.dumps(c, ensure_ascii=False) + "\n")
    print("\nlabel agreement per language (author vs blind second labeler)")
    for lang, (ok, n) in sorted(totals.items()):
        print(f"  {lang:<8} {ok:>3}/{n:<3} {ok / n:6.1%}")
    ok_all = sum(v[0] for v in totals.values())
    n_all = sum(v[1] for v in totals.values())
    print(f"  overall  {ok_all}/{n_all} {ok_all / max(n_all, 1):.1%}   contested: {len(contested)}")
    print("\nCohen's kappa per mode")
    for m in modes:
        print(f"  {m:<24} {kappa(mode_pairs[m]):.3f}")


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else ""
    if cmd == "prepare" and len(sys.argv) == 4:
        prepare(sys.argv[2], sys.argv[3])
    elif cmd == "compare" and len(sys.argv) == 6:
        compare(*sys.argv[2:6])
    else:
        sys.exit(__doc__)
