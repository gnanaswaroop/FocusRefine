# Personal Model: learning one user's preferences on the device

FocusRefine ships a built-in embedding classifier ([AI Classification Architecture](ai_classification_and_modes.md)). The **personal model** is a very small model that sits on top of it and learns from one user's own Shown/Muted corrections. It exists so the app can adapt to a person without any message ever leaving the phone.

## 1. Privacy guarantees

| Guarantee | How it is enforced |
| :--- | :--- |
| No network access | The manifest removes `INTERNET` and `ACCESS_NETWORK_STATE` (a MediaPipe dependency adds them); `PrivacyGuardsTest` fails if they return or if a socket can connect |
| No cloud backup | `allowBackup="false"`; checked by `PrivacyGuardsTest` |
| Stored data is not message text | The file holds sentence-embedding vectors (arrays of 100 numbers), a label and a one-way key; `PersonalLearningTest` asserts that title and body words do not appear in the file |
| Stays in app-private storage | `filesDir/personal_model.bin`, readable only by this app's UID |
| Training happens on the device | Plain Kotlin gradient descent, a few milliseconds for 80 corrections |
| User can erase it | "Reset learning data" (Privacy screen) and "Clear all learned rules" delete the file; deleting one learned rule removes that correction |
| No content in logs | The service no longer logs notification title or text |

A vector is not a perfect one-way function: an embedding can leak topic information if someone has the file and the model. That is why the file is private storage, excluded from backup, and deleted by the reset actions. The file is not encrypted at rest beyond Android's own file-based encryption of app storage.

## 2. How it works

For a built-in mode, the embedding classifier produces `margin = score - threshold` (Shown when it is `>= 0`). The personal model adds a correction:

```
z = baseScale * margin + sum_i alpha_i * k(x, x_i)        Shown when z >= 0
k(x, x_i) = exp((cos(x, x_i) - 1) / tau)  (corrections in another mode count crossMode times as much)
```

- `x` is the normalised embedding of the notification; `x_i` are the embeddings of the notifications the user corrected.
- The kernel `k` is **local**: a correction only moves notifications that are very similar to the corrected one. Everything else keeps the built-in decision. With no corrections the adjustment is exactly 0.
- The `alpha_i` are fitted by gradient descent on the logistic loss with an L2 penalty, starting from the built-in score as the prior, so a few corrections cannot wreck the built-in behaviour.
- Defaults: `baseScale 60`, `tau 0.06`, `crossMode 0.0` (modes are independent), `lambda 0.01`, 300 iterations. They were chosen by cross-validation inside the golden set only.
- At most 300 corrections are kept (newest win). Correcting the same notification again replaces the earlier correction.

The decision explanation says so when it matters: *"... Your past choices pushed this toward shown."*

### Where the labels come from
Only explicit feedback: when the user flips a notification to Shown or Muted in the detail screen and "Learn from my choices" is on, the correction is recorded for the mode that was active. Nothing is inferred from what the user ignores or dismisses.

### Scope
- Built-in modes only. Custom modes have no anchors, so they keep using the keyword engine.
- It affects step 4 of the pipeline (the embedding model). Learned and synthesized rules, and exceptions, still run first and are not changed by it.
- If the embedding model is off or cannot load, the personal model is unused (and deleting one correction clears all of them, because it cannot retrain).

## 3. Measured benefit

Three layers of evidence, from the real app down to large simulations. The small hand-written golden and holdout sets
(96 and 76 items) are the same ones used elsewhere; the **corpus** is a new, larger set described in section 3.3.

### 3.1 The real service, end to end (`scripts/replay_personal.py`)
Emulator, real `NotificationFilterService` (rules, exceptions and model all active). For each mode the script wipes the
app, teaches the personal model N corrections sampled from the **golden** set through the same code path as the detail
screen, then posts the **unseen holdout** set from the adb shell and scores the stored decisions. Macro F1 over the five
modes, 2 random seeds per row:

| Simulated user | Corrections chosen | N per mode | Holdout F1 before | Holdout F1 after | Change | The corrected items themselves, accuracy before -> after |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| standard | random sample | 10 | 0.527 | 0.541 | +0.014 | 0.82 -> 0.89 |
| standard | random sample | 30 | 0.527 | 0.552 | +0.025 | 0.79 -> 0.86 |
| standard | the model's own mistakes | 10 | 0.527 | 0.582 | +0.055 | 0.00 -> 1.00 |
| standard | the model's own mistakes | 30 | 0.527 | 0.583 | +0.056 | 0.00 -> 1.00 |
| chatty | random sample | 10 | 0.498 | 0.522 | +0.023 | 0.76 -> 0.86 |
| chatty | random sample | 30 | 0.498 | 0.546 | +0.048 | 0.75 -> 0.87 |
| chatty | the model's own mistakes | 10 | 0.498 | 0.585 | +0.086 | 0.00 -> 1.00 |
| chatty | the model's own mistakes | 30 | 0.498 | 0.593 | +0.095 | 0.00 -> 1.00 |

Every configuration improved on notifications the app had never seen, and re-running the corrected notifications gave
the corrected answer. "The model's own mistakes" means the mistakes the embedding model made; mistakes made by
exceptions or learned rules cannot be fixed by the personal model because those steps run first (in the golden set,
for example, 12 of 14 Sleeping-mode mistakes and 19 of 24 Workout-mode mistakes are exception decisions). Only 33
golden mistakes (across the five modes) were the model's, so the two sizes of "errors" are nearly the same set.

### 3.2 Large-scale simulation (`PersonalCorpusSimulationTest`)
The 590-item corpus, six simulated users, 12 random seeds per cell, 2 sampling variants, 6 sizes, 3 ways of splitting
pool from test: 3,000+ trained models. It runs on the host JVM in about 40 seconds using the real model's embeddings
(dumped from the emulator by `CorpusEmbeddingDumpTest`) and the real `PersonalModel` class. It scores the **embedding
model alone** (no rules or exceptions), macro F1 over the five modes, **mean change over all six users**:

| Split | Corrections chosen | N=10 | N=25 | N=50 | N=100 | N=200 | N=300 |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| same kind of notification (new instances of kinds already corrected) | random sample | -0.001 | +0.009 | +0.021 | +0.047 | +0.084 | +0.129 |
| same kind | the model's mistakes | +0.025 | +0.062 | +0.107 | +0.163 | +0.225 | +0.244 |
| new kind (templates never corrected) | random sample | -0.001 | +0.003 | +0.008 | +0.015 | +0.024 | +0.036 |
| new kind | the model's mistakes | +0.012 | +0.026 | +0.035 | +0.053 | +0.063 | +0.066 |
| separate hand-written holdout set | random sample | +0.008 | +0.015 | +0.016 | +0.035 | +0.048 | +0.079 |
| separate holdout set | the model's mistakes | +0.019 | +0.064 | +0.084 | +0.099 | +0.123 | +0.125 |

(N is the total number of corrections across all modes; the app keeps at most 300.) The full per-user tables, including
the range over seeds and the share of decisions fixed or broken, are in
[`personal_model_simulation.md`](personal_model_simulation.md).

What this says:
- **It helps, and the effect grows with the number of corrections.** The mean change is positive in every row once
  there are about 25 corrections.
- **Corrections that fix the model's mistakes are worth much more than random ones**, about 3x at 100 corrections. That
  matches how the app collects them: a user only flips a notification when the app got it wrong.
- **It helps most for a user whose tastes differ from the built-in ones.** A "minimalist" who wants only genuine safety
  alerts goes from F1 0.38 to 0.78 with 300 corrections of the model's mistakes (same-kind split); the user whose tastes
  already match the defaults ("standard") goes from 0.60 to 0.82.
- **It generalises mostly to the same kinds of notification** (the kernel is deliberately local). For kinds of message the
  user has never corrected the gain is real but about a third as large.
- **It is not free of harm.** With 100 or more corrections, 0.6% to 8% of previously correct decisions were broken while
  4% to 18% of wrong ones were fixed. Broken decisions are highest for users whose tastes conflict with the model's clusters (workaholic, chatty) and when
  all corrections target mistakes. In the new-kind split, single seeds reached -0.08 (the average stayed positive).
  The test asserts that the mean change never drops below -0.02 in any cell.
- **Few corrections do almost nothing, by design.** With 10 random corrections the mean change is about zero.

Caveats: the labels are rules applied to synthetic templates, so notifications within a template are near paraphrases of
each other, which flatters the same-kind split. The new-kind split and the separate holdout are the conservative figures.
The users are simulated, not people; real corrections will be noisier and may contradict each other. Parameters were
tuned on the golden set only (cross-validation); the corpus and the holdout set are independent confirmation.

### 3.3 The corpus (`testdata/corpus/corpus_notifications.json`)
590 synthetic notifications built by `scripts/generate_corpus.py` from 94 hand-written templates in 37 categories (about
8 instances per template, deterministic, disjoint from the golden and holdout sets). Each item records its `category`,
its `template` and `labels` for six simulated users:

| User | What they want Shown |
| :--- | :--- |
| standard | the same rules as the golden set |
| chatty | also casual chat in Commute, Focus and Workout |
| workaholic | work alerts (incidents, build failures, meetings, deadlines) in Commute, Focus and Workout as well |
| minimalist | only genuine safety alerts (family distress, public emergencies, home alarms, critical health), in every mode |
| news fan | also headlines and weather in Commute and Workout |
| parent | school alerts in every mode, and parcels while commuting |

About 40% of items are urgent in some mode; 41 are deliberate near-misses ("Build #4413 succeeded", "Fire sale: 50% off",
"Heart rate summary for last week"). To grow the corpus, add templates to `scripts/generate_corpus.py` and re-run it;
prefer adding real, anonymised notifications of your own over inflating the synthetic ones.

### 3.4 Real service on the corpus (`replay_personal.py --corpus`)
The same real-service experiment as 3.1, on the corpus, one random seed per row. Half of each template's instances are the
pool the corrections come from; the other half (295 notifications of the same kinds, never corrected) is posted to the app
and scored. Macro F1 over the five modes. Here the baseline's mistakes are almost all the embedding model's (the corpus
avoids exception keywords), so the personal model can reach them:

| Simulated user | Corrections chosen | N per mode | F1 before | F1 after | Change | Corrected items, accuracy before -> after |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| standard | random sample | 20 | 0.585 | 0.640 | +0.055 | 0.88 -> 0.99 |
| standard | random sample | 60 | 0.585 | 0.685 | +0.100 | 0.84 -> 0.98 |
| standard | the model's own mistakes | 20 | 0.585 | 0.755 | +0.169 | 0.00 -> 1.00 |
| standard | the model's own mistakes | 60 | 0.585 | 0.749 | +0.164 | 0.00 -> 1.00 |
| workaholic | random sample | 20 | 0.519 | 0.578 | +0.058 | 0.76 -> 1.00 |
| workaholic | random sample | 60 | 0.519 | 0.635 | +0.115 | 0.80 -> 0.99 |
| workaholic | the model's own mistakes | 20 | 0.519 | 0.690 | +0.171 | 0.00 -> 1.00 |
| workaholic | the model's own mistakes | 60 | 0.519 | 0.726 | +0.207 | 0.00 -> 1.00 |
| minimalist | random sample | 20 | 0.358 | 0.355 | -0.003 | 0.74 -> 0.95 |
| minimalist | random sample | 60 | 0.358 | 0.471 | +0.112 | 0.80 -> 0.99 |
| minimalist | the model's own mistakes | 20 | 0.358 | 0.541 | +0.183 | 0.00 -> 1.00 |
| minimalist | the model's own mistakes | 60 | 0.358 | 0.650 | +0.291 | 0.00 -> 1.00 |

Eleven of twelve configurations improved on never-seen notifications of the same kinds; the twelfth (20 random
corrections for a user whose tastes differ most from the defaults) did not change. This agrees with the simulation in 3.2
and shows the effect survives the rest of the pipeline: rules, exceptions, the real service and the real database.
These are the favourable "same kind" conditions; the new-kind and external-holdout figures in 3.2 are the conservative ones.
Total real-service runs behind sections 3.1 and 3.4: 20 configurations, each across all five modes.

## 4. Why not a larger or federated model?
- A network-trained or shared model would need data to leave the device, which is ruled out.
- Training a neural network on a few dozen labels overfits; the measured linear variant already got worse. A local kernel on a strong pretrained embedding is the smallest thing that adapts safely.
- With hundreds of real corrections, a learned layer on top of the embedding could do better; the stored corrections would be its training data.

## 5. Known limitations and next steps
- **Cold start:** with fewer than about 10 corrections the effect is small.
- **Stale mode:** the correction is recorded for the mode active when the user flips it, which may differ from the mode when the notification arrived.
- **Toggling a learned rule** in the Learned Rules screen does not update the personal model (deleting one does).
- **Anchors cost:** every correction loads a short-lived embedder and embeds the anchors (about 2 s on a phone, in the background). The service could share its embedder.
- **No UI** shows how many corrections exist or lets the user inspect them; adding a count and a "what the app has learned" screen would help trust.
- **Evaluation on real data** is not possible without real labels; the next step is an on-device-only "accuracy over my corrections" readout.

## 6. Code map
| File | Role |
| :--- | :--- |
| `PersonalModel.kt` | The kernel model (pure Kotlin, unit tested in `PersonalModelTest`) |
| `PersonalModelStore.kt` | `personal_model.bin`: load, save, remove, clear |
| `EmbeddingClassifier.kt` | Applies the model in `classify`; `learn` / `forget` |
| `PersonalLearning.kt` | Entry points used by the UI (record, forget, clear) |
| `NotificationDetailActivity.kt`, `LearnedRulesActivity.kt`, `PrivacyInsightsActivity.kt` | Record, forget and clear hooks |
| `PersonalLearningTest`, `PersonalModelBenchmarkTest`, `PrivacyGuardsTest` | Device tests and the simulation |
