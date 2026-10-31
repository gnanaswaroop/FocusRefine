# FocusRefine Performance Findings

The figures below describe the current on-device model (Track B, `track_b.tflite` on LiteRT 2.2.0) and the keyword engine that runs alongside it. Each figure comes from this repository's own tests and data; the reproduction commands are at the end. The labels are synthetic and written by the same author, so the accuracy numbers are optimistic relative to real traffic.

## 1. Accuracy (model alone)

Model alone means no learned rules, no exceptions, and no personal corrections. Golden holdout: 1,600 held-out items, macro F1 over the five modes.

| Measure | Result |
| :--- | :--- |
| Golden holdout, macro F1 | 0.748 (host and device agree) |
| Safety holdout (312 items never trained on), macro F1 | 0.939 |
| 44 device-measured safety notices, shown per mode (Commute / Focus / DND / Sleep / Workout) | 30 / 16 / 27 / 27 / 15 |

Per-language and per-mode detail: [`track_b_vs_old_engine.md`](track_b_vs_old_engine.md). Safety detail: [`safety_recall.md`](safety_recall.md).

## 2. Latency and memory

**Not measured yet on the phone for Track B.** The figures for model load, per-notification inference and anchor warm-up need a phone run (`scripts/replay_golden.py` plus the timing logs). Until then, do not quote numbers for the current model here.

What is known:
- The model file is 11 MB and is loaded once per process from a page-aligned buffer.
- One warm-up inference runs on a background thread when the filter service starts, so the first real notification does not pay the setup cost.
- The host and the device produce the same decisions on the held-out items.

## 3. Keyword engine

The keyword engine (`--engine keyword`) is the fallback when the model is off or cannot load:

| Set | F1 | Precision | Recall | Accuracy |
| :--- | :--- | :--- | :--- | :--- |
| Golden (96) | 0.396 | 0.292 | 0.617 | 0.710 |
| Holdout (76) | 0.360 | 0.288 | 0.489 | 0.687 |

It is identical on the emulator and on a Pixel.

## 4. Earlier embedder

The app used to use a different on-device embedder. It was removed before this release, and its measurements are no longer relevant to the current build. The only lesson still in use is that the model must be loaded from a page-aligned buffer, not read straight out of the APK, which crashed on a Pixel.

## 5. Reproducing

```
python scripts/replay_golden.py --serial emulator-5554                  # end to end, model in the service
python scripts/replay_golden.py --serial emulator-5554 --engine keyword  # keyword fallback
adb shell am instrument -w -e class com.focusrefine.app.TrackBEngineTest com.focusrefine.app.test/androidx.test.runner.AndroidJUnitRunner   # held-out items on the device (logs to TRACKB_BENCH)
```

Model-only figures come from the Track B training and export scripts; see [`track_b_decisions.md`](track_b_decisions.md) and `ml/export/README.md`.
