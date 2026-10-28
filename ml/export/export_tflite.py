"""Step 2 of the Track B export (run in a TensorFlow venv, not the training venv).

python ml/export/export_tflite.py [--export ml/artifacts/track_b/export]

Rebuilds the Track B transformer with TensorFlow ops, loads the weights dumped by dump_track_b.py, checks the
logits against PyTorch's reference outputs, converts to float32 TFLite, and checks the TFLite file the same way.
Input: int32 token ids, shape [1, 96], pad id 0. Output: 5 logits, one per mode in train_b.MODES (sigmoid >= 0.5 means Shown).
"""
import argparse
import os

import numpy as np
import tensorflow as tf

MAXLEN = 96
LAYERS = 4
HEADS = 4
DIM = 192
EPS = 1e-5


class TrackB(tf.Module):
    def __init__(self, w):
        super().__init__()
        self.emb = tf.Variable(w["emb.weight"], name="emb")
        self.pos = tf.constant(w["pos.weight"][:MAXLEN], name="pos")
        self.layers = []
        for i in range(LAYERS):
            p = f"enc.layers.{i}."
            self.layers.append({
                "w_in": tf.Variable(w[p + "self_attn.in_proj_weight"], name=f"w_in{i}"),
                "b_in": tf.Variable(w[p + "self_attn.in_proj_bias"], name=f"b_in{i}"),
                "w_out": tf.Variable(w[p + "self_attn.out_proj.weight"], name=f"w_out{i}"),
                "b_out": tf.Variable(w[p + "self_attn.out_proj.bias"], name=f"b_out{i}"),
                "w1": tf.Variable(w[p + "linear1.weight"], name=f"w1_{i}"),
                "b1": tf.Variable(w[p + "linear1.bias"], name=f"b1_{i}"),
                "w2": tf.Variable(w[p + "linear2.weight"], name=f"w2_{i}"),
                "b2": tf.Variable(w[p + "linear2.bias"], name=f"b2_{i}"),
                "g1": tf.Variable(w[p + "norm1.weight"], name=f"g1_{i}"),
                "be1": tf.Variable(w[p + "norm1.bias"], name=f"be1_{i}"),
                "g2": tf.Variable(w[p + "norm2.weight"], name=f"g2_{i}"),
                "be2": tf.Variable(w["enc.layers.%d.norm2.bias" % i], name=f"be2_{i}"),
            })
        self.w_head = tf.Variable(w["head.weight"], name="w_head")
        self.b_head = tf.Variable(w["head.bias"], name="b_head")

    @staticmethod
    def _norm(x, g, b):
        mean, var = tf.nn.moments(x, axes=[-1], keepdims=True)
        return (x - mean) * tf.math.rsqrt(var + EPS) * g + b

    def _attention(self, x, layer, bias):
        qkv = tf.matmul(x, layer["w_in"], transpose_b=True) + layer["b_in"]
        q, k, v = tf.split(qkv, 3, axis=-1)
        head = DIM // HEADS
        def heads(t):
            return tf.transpose(tf.reshape(t, [1, MAXLEN, HEADS, head]), [0, 2, 1, 3])
        q, k, v = heads(q), heads(k), heads(v)
        scores = tf.matmul(q, k, transpose_b=True) / np.sqrt(head).astype(np.float32) + bias
        out = tf.matmul(tf.nn.softmax(scores, axis=-1), v)
        out = tf.reshape(tf.transpose(out, [0, 2, 1, 3]), [1, MAXLEN, DIM])
        return tf.matmul(out, layer["w_out"], transpose_b=True) + layer["b_out"]

    @tf.function
    def __call__(self, ids):
        pad = tf.equal(ids, 0)
        bias = tf.reshape(tf.where(pad, -1e9, 0.0), [1, 1, 1, MAXLEN])
        h = tf.gather(self.emb, ids) + self.pos[None]
        for layer in self.layers:
            h = self._norm(h + self._attention(h, layer, bias), layer["g1"], layer["be1"])
            ff = tf.matmul(tf.nn.relu(tf.matmul(h, layer["w1"], transpose_b=True) + layer["b1"]),
                           layer["w2"], transpose_b=True) + layer["b2"]
            h = self._norm(h + ff, layer["g2"], layer["be2"])
        keep = tf.cast(tf.not_equal(ids, 0), tf.float32)[..., None]
        pooled = tf.reduce_sum(h * keep, axis=1) / tf.maximum(tf.reduce_sum(keep, axis=1), 1.0)
        logits = tf.matmul(pooled, self.w_head, transpose_b=True) + self.b_head
        return logits, pooled


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--export", default=os.path.join("ml", "artifacts", "track_b", "export"))
    args = ap.parse_args()

    w = dict(np.load(os.path.join(args.export, "weights.npz")))
    ref = np.load(os.path.join(args.export, "reference.npz"))
    module = TrackB(w)
    concrete = module.__call__.get_concrete_function(tf.TensorSpec([1, MAXLEN], tf.int32))

    worst = 0.0
    for n in range(len(ref["ids"])):
        out = module(tf.constant(ref["ids"][n:n + 1]))[0].numpy()[0]
        worst = max(worst, float(np.max(np.abs(out - ref["logits"][n]))))
    print(f"keras vs pytorch: max abs logit difference {worst:.2e} over {len(ref['ids'])} items")
    assert worst < 1e-3, "rebuilt network does not match the PyTorch model"

    converter = tf.lite.TFLiteConverter.from_concrete_functions([concrete], module)
    tflite_model = converter.convert()
    path = os.path.join(args.export, "model.tflite")
    with open(path, "wb") as f:
        f.write(tflite_model)
    print(f"wrote {path} ({len(tflite_model) / 1e6:.1f} MB)")

    interp = tf.lite.Interpreter(model_content=tflite_model)
    interp.allocate_tensors()
    inp = interp.get_input_details()[0]
    # two outputs: logits [1, 5] and pooled features [1, 192]; pick the logits by shape
    outp = next(d for d in interp.get_output_details() if d["shape"][-1] == len(ref["logits"][0]))
    tflite_worst = 0.0
    agree = 0
    total = len(ref["ids"])
    for n in range(total):
        interp.set_tensor(inp["index"], ref["ids"][n:n + 1])
        interp.invoke()
        out = interp.get_tensor(outp["index"])[0]
        tflite_worst = max(tflite_worst, float(np.max(np.abs(out - ref["logits"][n]))))
        agree += int(np.all((out >= 0) == (ref["logits"][n] >= 0)))
    print(f"tflite vs pytorch: max abs logit difference {tflite_worst:.2e}; "
          f"items with identical Shown/Muted per mode: {agree}/{total}")


if __name__ == "__main__":
    main()
