"""Step 1 of the Track B export (run in the training venv): dump weights and PyTorch reference outputs.

python -m ml.export.dump_track_b [--artifacts ml/artifacts/track_b] [--out ml/artifacts/track_b/export]

Writes weights.npz (every parameter, as float32) and reference.npz (token ids and PyTorch logits for held-out
items), which export_tflite.py uses to rebuild the network in TensorFlow and check it matches.
"""
import argparse
import os

import numpy as np
import sentencepiece as spm
import torch

from ml.train import train_b as t


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--artifacts", default=os.path.join("ml", "artifacts", "track_b"))
    ap.add_argument("--out", default=os.path.join("ml", "artifacts", "track_b", "export"))
    args = ap.parse_args()
    os.makedirs(args.out, exist_ok=True)

    sp = spm.SentencePieceProcessor(model_file=os.path.join(args.artifacts, "spm.model"))
    model = t.Model(sp.get_piece_size())
    loaded = torch.load(os.path.join(args.artifacts, "model_b.pt"), map_location="cpu", weights_only=False)
    state = loaded.state_dict() if isinstance(loaded, torch.nn.Module) else loaded
    model.load_state_dict(state)
    model.eval()

    weights = {k: v.detach().cpu().numpy().astype(np.float32) for k, v in model.state_dict().items()}
    np.savez(os.path.join(args.out, "weights.npz"), **weights)

    _, evalset = t.load_split()
    x, _ = t.encode(sp, evalset)
    with torch.no_grad():
        logits = model(x).numpy()
    np.savez(os.path.join(args.out, "reference.npz"), ids=x.numpy().astype(np.int32), logits=logits)
    print(f"weights: {len(weights)} tensors; reference: {len(x)} held-out items, logits shape {logits.shape}")


if __name__ == "__main__":
    main()
