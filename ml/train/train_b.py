"""Track B diagnostic: a small transformer trained from scratch on the golden_v2 items.

python -m ml.train.train_b [--epochs N] [--out ml/artifacts/track_b]

Data: pilot, round 2, round 3 and round 4 agreed items. There is no separate train split yet, so items are split by
template_family: 80% of families train, 20% are held out and never used for training or threshold choice.
Output: metrics.json and run.json (data hash, seed, settings). Weights stay in the git-ignored artifacts folder.
This is a diagnostic run, not a release model: its job is to find label noise and hard cases.
"""
import argparse
import glob
import hashlib
import json
import os
import random

import numpy as np
import sentencepiece as spm
import torch
import torch.nn as nn
from sklearn.metrics import f1_score

from ml.data import schema

MODES = ["Commute", "Deep Work / Focus", "Do Not Disturb (DND)", "Sleeping / Rest", "Workout / Gym"]
SOURCES = ["testdata/golden_v2/pilot_agreed", "testdata/golden_v2/r2_agreed",
           "testdata/golden_v2/round3/agreed", "testdata/golden_v2/round4/agreed"]
# Round 5: safety notices added to training. Their families are never held out, so the holdout below stays the same.
EXTRA_SOURCES = ["testdata/golden_v2/round5/agreed", "testdata/golden_v2/round6/agreed"]
# Round 7 (general items for the weak languages) did not beat round 6, so it is not in the trained model:
# testdata/golden_v2/round7/agreed
# Fresh safety notices (round 5, `test` split), never trained on; scored separately.
SAFETY_HOLDOUT = "testdata/golden_v2/round5/safety_holdout"
# The 44 notices measured on the device (docs/safety_recall.md); scored separately, never trained on.
SAFETY_ITEMS = "testdata/safety/safety_items.json"
MAXLEN = 96
SEED = 1


def load_items(dirs):
    items = []
    for d in dirs:
        for path in sorted(glob.glob(os.path.join(d, "*.jsonl"))):
            items += schema.load_jsonl(path)
    return items


def text_of(item):
    return f"{item['app_category']} | {item['app_name']} | {item['title']} | {item['text']}"


def load_split():
    """(train, holdout) exactly as main() trains and scores them: the same held-out families, with the extra rounds."""
    base = load_items(SOURCES)
    return split(base + load_items(EXTRA_SOURCES), held_families(base))


def held_families(base):
    """The 20% of template families held out. Chosen from the original sources only, so adding data never moves them."""
    families = sorted({i["template_family"] for i in base})
    random.Random(SEED).shuffle(families)
    return set(families[: int(0.2 * len(families))])


def split(items, held):
    train = [i for i in items if i["template_family"] not in held]
    evalset = [i for i in items if i["template_family"] in held]
    return train, evalset


def data_hash(items):
    h = hashlib.sha256()
    for i in sorted(items, key=lambda x: x["id"]):
        h.update(json.dumps(i, sort_keys=True, ensure_ascii=False).encode("utf-8"))
    return h.hexdigest()


class Model(nn.Module):
    def __init__(self, vocab):
        super().__init__()
        self.emb = nn.Embedding(vocab, 192, padding_idx=0)
        self.pos = nn.Embedding(MAXLEN, 192)
        layer = nn.TransformerEncoderLayer(192, 4, 384, dropout=0.1, batch_first=True)
        self.enc = nn.TransformerEncoder(layer, 4)
        self.head = nn.Linear(192, len(MODES))

    def forward(self, x):
        pad = x.eq(0)
        h = self.emb(x) + self.pos(torch.arange(x.size(1), device=x.device))[None]
        h = self.enc(h, src_key_padding_mask=pad)
        keep = (~pad).unsqueeze(-1).float()
        pooled = (h * keep).sum(1) / keep.sum(1).clamp(min=1)
        return self.head(pooled)


def encode(sp, items):
    x = np.zeros((len(items), MAXLEN), dtype=np.int64)
    y = np.zeros((len(items), len(MODES)), dtype=np.float32)
    for n, item in enumerate(items):
        ids = sp.encode(text_of(item))[:MAXLEN]
        x[n, : len(ids)] = ids
        y[n] = [1.0 if m in item["urgentIn"] else 0.0 for m in MODES]
    return torch.tensor(x), torch.tensor(y)


def evaluate(model, x, y, device):
    model.eval()
    with torch.no_grad():
        logits = torch.cat([model(x[i:i + 256].to(device)) for i in range(0, len(x), 256)]).cpu()
    pred = (torch.sigmoid(logits) >= 0.5).int().numpy()
    truth = y.int().numpy()
    per_mode = {m: float(f1_score(truth[:, k], pred[:, k], zero_division=0)) for k, m in enumerate(MODES)}
    return per_mode, float(np.mean(list(per_mode.values()))), pred, truth


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--epochs", type=int, default=12)
    ap.add_argument("--out", default=os.path.join("ml", "artifacts", "track_b"))
    ap.add_argument("--no-extra", action="store_true", help="train on the original sources only (the round-4 baseline)")
    args = ap.parse_args()
    torch.manual_seed(SEED)
    os.makedirs(args.out, exist_ok=True)

    base = load_items(SOURCES)
    extra = [] if args.no_extra else load_items(EXTRA_SOURCES)
    items = base + extra
    train, evalset = split(items, held_families(base))
    sp_text = os.path.join(args.out, "spm_train.txt")
    with open(sp_text, "w", encoding="utf-8", newline="\n") as f:
        f.write("\n".join(text_of(i) for i in train) + "\n")
    spm.SentencePieceTrainer.train(input=sp_text, model_prefix=os.path.join(args.out, "spm"), vocab_size=8000,
                                   model_type="unigram", character_coverage=1.0, pad_id=0, unk_id=1,
                                   bos_id=-1, eos_id=-1, minloglevel=2)
    sp = spm.SentencePieceProcessor(model_file=os.path.join(args.out, "spm.model"))

    x_tr, y_tr = encode(sp, train)
    x_ev, y_ev = encode(sp, evalset)
    device = "cuda" if torch.cuda.is_available() else "cpu"
    pos = y_tr.sum(0).clamp(min=1)
    pos_weight = ((len(y_tr) - pos) / pos).to(device)
    model = Model(sp.get_piece_size()).to(device)
    opt = torch.optim.AdamW(model.parameters(), lr=3e-4, weight_decay=0.01)
    loss_fn = nn.BCEWithLogitsLoss(pos_weight=pos_weight)
    history = []
    for epoch in range(args.epochs):
        model.train()
        order = torch.randperm(len(x_tr))
        total = 0.0
        for i in range(0, len(order), 64):
            idx = order[i:i + 64]
            opt.zero_grad()
            loss = loss_fn(model(x_tr[idx].to(device)), y_tr[idx].to(device))
            loss.backward()
            opt.step()
            total += float(loss) * len(idx)
        history.append(total / len(order))
        print(f"epoch {epoch + 1}: train loss {history[-1]:.4f}", flush=True)

    per_mode, macro, pred, truth = evaluate(model, x_ev, y_ev, device)
    langs = sorted({i["lang"] for i in evalset})
    per_lang = {}
    for lang in langs:
        rows = [n for n, i in enumerate(evalset) if i["lang"] == lang]
        per_lang[lang] = float(np.mean([f1_score(truth[rows, k], pred[rows, k], zero_division=0)
                                        for k in range(len(MODES))]))
    safety_holdout = load_items([SAFETY_HOLDOUT])
    safety_macro, safety_per_mode, safety_recall = safety_scores(model, sp, safety_holdout, device)
    items_44 = json.load(open(SAFETY_ITEMS, encoding="utf-8"))
    shown_44 = shown_counts(model, sp, [f" |  | {i['title']} | {i['text']}" for i in items_44], device)

    metrics = {"macro_f1_holdout": macro, "per_mode_f1_holdout": per_mode, "per_language_macro_f1_holdout": per_lang,
               "train_items": len(train), "holdout_items": len(evalset), "train_loss": history,
               "safety_holdout": {"items": len(safety_holdout), "macro_f1": safety_macro,
                                  "per_mode_f1": safety_per_mode, "per_mode_recall": safety_recall},
               "safety_items_44_shown": dict(zip(MODES, shown_44)), "safety_items_44_total": len(items_44)}
    with open(os.path.join(args.out, "metrics.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump(metrics, f, ensure_ascii=False, indent=1)
    with open(os.path.join(args.out, "run.json"), "w", encoding="utf-8", newline="\n") as f:
        json.dump({"track": "B", "seed": SEED, "epochs": args.epochs, "device": device,
                   "data_hash_all": data_hash(items), "data_hash_train": data_hash(train),
                   "data_hash_holdout": data_hash(evalset), "sources": SOURCES + extra_sources(args),
                   "maxlen": MAXLEN, "torch": torch.__version__}, f, indent=1)
    torch.save(model.state_dict(), os.path.join(args.out, "model_b.pt"))
    print(f"holdout macro F1 {macro:.3f} on {len(evalset)} items; per mode {per_mode}")
    print(f"safety holdout macro F1 {safety_macro:.3f} on {len(safety_holdout)} items; recall {safety_recall}")
    print(f"safety 44 shown per mode {metrics['safety_items_44_shown']}")


def extra_sources(args):
    return [] if args.no_extra else EXTRA_SOURCES


def probabilities(model, sp, texts, device):
    """Shown probability per mode for each text (no app context beyond what the text carries)."""
    x = np.zeros((len(texts), MAXLEN), dtype=np.int64)
    for n, text in enumerate(texts):
        ids = sp.encode(text)[:MAXLEN]
        x[n, : len(ids)] = ids
    model.eval()
    with torch.no_grad():
        logits = torch.cat([model(torch.tensor(x[i:i + 256]).to(device)) for i in range(0, len(x), 256)]).cpu()
    return torch.sigmoid(logits).numpy()


def shown_counts(model, sp, texts, device):
    """How many texts each mode shows (probability >= 0.5), the same rule the app applies at Balanced."""
    return (probabilities(model, sp, texts, device) >= 0.5).sum(0).tolist()


def safety_scores(model, sp, items, device):
    """Macro F1 and per-mode recall on the safety holdout, using the rubric labels (urgentIn)."""
    texts = [text_of(i) for i in items]
    pred = probabilities(model, sp, texts, device) >= 0.5
    truth = np.array([[m in i["urgentIn"] for m in MODES] for i in items])
    per_mode_f1 = {m: float(f1_score(truth[:, k], pred[:, k], zero_division=0)) for k, m in enumerate(MODES)}
    recall = {m: float(pred[truth[:, k], k].mean()) if truth[:, k].any() else 0.0 for k, m in enumerate(MODES)}
    return float(np.mean(list(per_mode_f1.values()))), per_mode_f1, recall


if __name__ == "__main__":
    main()
