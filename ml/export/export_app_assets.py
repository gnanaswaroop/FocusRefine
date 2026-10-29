"""Writes the app-side assets for Track B (run in the training venv).

python -m ml.export.export_app_assets [--artifacts ml/artifacts/track_b] [--out ml/artifacts/track_b/export]

- vocab.json: SentencePiece unigram pieces and scores, for the app's tokenizer.
- app_catalogue.json: package -> display name and category, from ml/data/apps.tsv (the same labels the model was trained with).
- tokenizer_check.json: token ids from this Python port for held-out texts, next to SentencePiece's ids, for the on-device check.
"""
import argparse
import json
import os
import unicodedata

import sentencepiece as spm

from ml.train import train_b as t

UNK_PENALTY = 10.0


def normalize(text):
    # SentencePiece treats zero-width non-joiners as word boundaries
    text = unicodedata.normalize("NFKC", text).replace("‌", " ")
    words = text.split()
    return "\u2581" + "\u2581".join(words) if words else ""


def encode(pieces, scores, text, unk_id=1):
    """Viterbi segmentation over the unigram model (the same algorithm the app ports)."""
    s = normalize(text)
    n = len(s)
    best = [float("-inf")] * (n + 1)
    back = [None] * (n + 1)
    best[0] = 0.0
    min_score = min(scores)
    for i in range(n):
        if best[i] == float("-inf"):
            continue
        matched = False
        for j in range(i + 1, min(n, i + MAX_LEN) + 1):
            piece = s[i:j]
            if piece in pieces:
                matched = True
                cand = best[i] + scores[pieces[piece]]
                if cand > best[j]:
                    best[j] = cand
                    back[j] = (i, pieces[piece])
        if not matched:
            cand = best[i] + min_score - UNK_PENALTY
            if cand > best[i + 1]:
                best[i + 1] = cand
                back[i + 1] = (i, unk_id)
    out = []
    j = n
    while j > 0:
        i, pid = back[j]
        # SentencePiece emits one unknown token for a run of unknown characters
        if not (pid == unk_id and out and out[-1] == unk_id):
            out.append(pid)
        j = i
    return list(reversed(out))


MAX_LEN = 1


def main():
    global MAX_LEN
    ap = argparse.ArgumentParser()
    ap.add_argument("--artifacts", default=os.path.join("ml", "artifacts", "track_b"))
    ap.add_argument("--out", default=os.path.join("ml", "artifacts", "track_b", "export"))
    args = ap.parse_args()
    os.makedirs(args.out, exist_ok=True)

    sp = spm.SentencePieceProcessor(model_file=os.path.join(args.artifacts, "spm.model"))
    pieces, scores = [], []
    for i in range(sp.get_piece_size()):
        pieces.append(sp.id_to_piece(i))
        scores.append(float(sp.get_score(i)))
    MAX_LEN = max(len(p) for p in pieces)
    index = {p: i for i, p in enumerate(pieces)}
    with open(os.path.join(args.out, "vocab.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump({"pieces": pieces, "scores": scores, "unk_id": 1, "pad_id": 0}, f, ensure_ascii=False)

    import numpy as np
    weights = np.load(os.path.join(args.out, "weights.npz"))
    head = {"modes": t.MODES, "weight": weights["head.weight"].tolist(), "bias": weights["head.bias"].tolist()}
    with open(os.path.join(args.out, "head.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(head, f)

    # The mean pooled feature over the training items. The app subtracts it before comparing notifications, so the
    # personal model's cosine similarities are not all close to 1 (pooled features of any two texts are).
    import torch
    model = t.Model(sp.get_piece_size())
    model.load_state_dict(torch.load(os.path.join(args.artifacts, "model_b.pt"), map_location="cpu", weights_only=False))
    model.eval()
    pooled = []
    handle = model.head.register_forward_pre_hook(lambda m, inp: pooled.append(inp[0].detach()))
    train_items, _ = t.load_split()
    x_train, _ = t.encode(sp, train_items)
    with torch.no_grad():
        for i in range(0, len(x_train), 256):
            model(x_train[i:i + 256])
    handle.remove()
    centre = torch.cat(pooled).mean(0).numpy().tolist()
    with open(os.path.join(args.out, "centre.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump({"centre": centre}, f)

    catalogue = []
    with open(os.path.join("ml", "data", "apps.tsv"), encoding="utf-8") as f:
        for line in f:
            if not line.strip() or line.startswith("#"):
                continue
            row = line.rstrip("\n").split("\t")
            if len(row) >= 3:
                catalogue.append({"package": row[0], "name": row[1], "category": row[2]})
    with open(os.path.join(args.out, "app_catalogue.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(catalogue, f, ensure_ascii=False)

    _, evalset = t.load_split()
    same = 0
    total_tokens = 0
    mismatches = []
    checks = []
    for it in evalset:
        text = t.text_of(it)
        ours = encode(index, scores, text)
        theirs = sp.encode(text)
        total_tokens += len(theirs)
        if ours == theirs:
            same += 1
        else:
            mismatches.append({"text": text, "ours": ours, "theirs": theirs})
        checks.append({"text": text, "ids": theirs})
    with open(os.path.join(args.out, "tokenizer_check.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(checks, f, ensure_ascii=False)
    print(f"tokenizer: {same}/{len(evalset)} items identical to SentencePiece")
    for m in mismatches[:3]:
        print("  mismatch:", m["text"][:80])


if __name__ == "__main__":
    main()
