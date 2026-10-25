"""Writes the held-out Track B items as a device fixture (run in the training venv).

python -m ml.export.make_device_fixture [--artifacts ml/artifacts/track_b] [--out ml/artifacts/track_b/device]

The fixture holds token ids, the labels (urgentIn per mode) and the PyTorch logits for each held-out item. The
on-device test feeds the ids to the TFLite file and compares against those logits and labels. Nothing here is
committed: the fixture is built from the git-ignored artifacts.
"""
import argparse
import json
import os

import numpy as np
import sentencepiece as spm

from ml.train import train_b as t


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--artifacts", default=os.path.join("ml", "artifacts", "track_b"))
    ap.add_argument("--out", default=os.path.join("ml", "artifacts", "track_b", "device"))
    args = ap.parse_args()
    os.makedirs(args.out, exist_ok=True)

    sp = spm.SentencePieceProcessor(model_file=os.path.join(args.artifacts, "spm.model"))
    _, evalset = t.split(t.load_items())
    ref = np.load(os.path.join(args.artifacts, "export", "reference.npz"))
    x, _ = t.encode(sp, evalset)
    assert np.array_equal(x.numpy().astype(np.int32), ref["ids"]), "fixture order does not match the export reference"

    items = []
    for n, item in enumerate(evalset):
        items.append({
            "ids": ref["ids"][n].tolist(),
            "urgentIn": [m in item["urgentIn"] for m in t.MODES],
            "logits": ref["logits"][n].tolist(),
            "lang": item["lang"],
            "title": item["title"],
            "text": item["text"],
        })
    with open(os.path.join(args.out, "fixture.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump({"modes": t.MODES, "maxlen": t.MAXLEN, "items": items}, f, ensure_ascii=False)
    print(f"wrote {len(items)} items to {os.path.join(args.out, 'fixture.json')}")


if __name__ == "__main__":
    main()
