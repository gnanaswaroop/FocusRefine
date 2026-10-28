# Track B (round 6) vs the old engine (1.1.2)

Model only, both engines, the same 1,956 items: 1,600 golden_v2 held-out items, 312 safety holdout items, and the 44
device-measured safety notices. Shown = the model's decision at Balanced strength. No personal model, no exceptions,
no learned rules, no keyword fallback.

How each side was run:
- **Track B round 6:** PyTorch weights on the host (the exported TFLite matches them on all 1,600 items, and the device
  matched the host on the golden holdout, 0.748).
- **Old engine (1.1.2):** the MediaPipe text embedder and its anchors, on the emulator (`EmbeddingClassifier.classify`,
  content = title + " " + text, lowercased, as the old classifier does). The old engine gets no app context; Track B
  gets the app name and category from the package, as it was trained with.

## Golden holdout (1,600 items, 5 modes)

| Mode | Old F1 | Old prec / recall | Track B F1 | Track B prec / recall |
| --- | --- | --- | --- | --- |
| Commute | 0.145 | 0.40 / 0.09 | 0.741 | 0.69 / 0.80 |
| Deep Work / Focus | 0.250 | 0.15 / 0.91 | 0.726 | 0.68 / 0.79 |
| DND | 0.136 | 0.39 / 0.08 | 0.752 | 0.74 / 0.76 |
| Sleeping / Rest | 0.067 | 0.44 / 0.04 | 0.752 | 0.74 / 0.76 |
| Workout / Gym | 0.116 | 0.44 / 0.07 | 0.771 | 0.74 / 0.81 |
| **Macro F1** | **0.143** | | **0.748** | |

Per language (macro F1, old | Track B): ar 0.04 | 0.58, bn 0.07 | 0.72, de 0.17 | 0.81, en 0.45 | 0.82, es 0.23 | 0.72,
fr 0.14 | 0.74, gu 0.05 | 0.67, hi 0.05 | 0.71, hi-Latn 0.31 | 0.83, id 0.09 | 0.68, ja 0.03 | 0.86, kn 0.05 | 0.84,
mr 0.05 | 0.72, pt-BR 0.06 | 0.74, ru 0.05 | 0.66, ta 0.04 | 0.62, te 0.06 | 0.84, zh-Hans 0.05 | 0.74.

## Safety holdout (312 items, never trained on)

| Mode | Old F1 | Track B F1 |
| --- | --- | --- |
| Commute | 0.295 | 0.937 |
| Deep Work / Focus | 0.596 | 0.929 |
| DND | 0.440 | 0.948 |
| Sleeping / Rest | 0.208 | 0.948 |
| Workout / Gym | 0.116 | 0.934 |
| **Macro F1** | **0.331** | **0.939** |

## 44 device-measured safety notices (shown per mode)

| | Commute | Focus | DND | Sleep | Workout | Total |
| --- | --- | --- | --- | --- | --- | --- |
| Old | 19 | 31 | 26 | 18 | 13 | 107 |
| Track B round 6 | 30 | 16 | 27 | 27 | 15 | 115 |

## Gaps that remain in Track B

- **Commute precision (0.69 on golden):** 131 items muted-as-routine are shown. Most of the Commute false positives are
  "routine but worded like an alert" items.
- **Focus false positives (81 on golden):** Focus is the mode the model is least sure about.
- **Low-resource and non-Latin languages:** ta (0.62), ar (0.58), ru (0.66), gu (0.67), id (0.68) trail the rest.
- **Focus on the 44 notices (16 vs 31):** partly the test's own labels (the rubric does not treat medical, school and
  home notices as urgent in Focus), not only the model.

## Caveats

- The old engine was tuned on English-centric data and the golden set is multilingual synthetic data, so the old engine's
  low golden scores are partly a data mismatch, not only a model failure. Its own docs report end-to-end F1 0.570 on its
  original golden set (different items, full pipeline).
- The golden holdout is synthetic and the same generator wrote the training data, so the golden numbers flatter both
  engines in similar ways but do not prove real-world accuracy.
- DND and Sleeping / Rest have identical golden errors for Track B. The rubric gives them the same labels for most use
  cases, so this is expected rather than a copy error.
