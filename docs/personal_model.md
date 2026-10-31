# Personal Model: learning one user's preferences on the device

> **Note.** The simulation numbers below were measured with the earlier MediaPipe embedding model, which this release removes. A re-run on the Track B model is pending; until then, treat them as a record of the earlier model.

FocusRefine ships a built-in embedding classifier ([AI Classification Architecture](ai_classification_and_modes.md)). The **personal model** is a very small model that sits on top of it and learns from one user's own Shown/Muted corrections. It exists so the app can adapt to a person without any message ever leaving the phone.

## 1. Privacy guarantees

| Guarantee | How it is enforced |
| :--- | :--- |
| No network access | The manifest removes `INTERNET` and `ACCESS_NETWORK_STATE` (a dependency such as the former MediaPipe library could add them); `PrivacyGuardsTest` fails if they return or if a socket can connect |
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
- It affects step 4 of the pipeline (the Track B model). Learned and synthesized rules, and exceptions, still run first and are not changed by it.
- If the Track B model is off or cannot load, the personal model is unused (and deleting one correction clears all of them, because it cannot retrain).

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
The 1,209-item corpus (section 3.3), six simulated users, 12 random seeds per cell, 2 sampling variants, 6 sizes, 3 ways of
splitting pool from test: 3,000+ trained models. It runs on the host JVM in about 45 seconds using the real model's
embeddings (dumped from the emulator by `CorpusEmbeddingDumpTest`) and the real `PersonalModel` class. It scores the
**embedding model alone** (no rules or exceptions), macro F1 over the five modes, **mean change over all six users**:

| Split | Corrections chosen | N=10 | N=25 | N=50 | N=100 | N=200 | N=300 |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| same kind of notification (new instances of kinds already corrected) | random sample | +0.007 | +0.017 | +0.033 | +0.064 | +0.112 | +0.151 |
| same kind | the model's mistakes | +0.038 | +0.079 | +0.126 | +0.180 | +0.209 | +0.215 |
| new kind (templates never corrected) | random sample | +0.006 | +0.016 | +0.022 | +0.042 | +0.065 | +0.081 |
| new kind | the model's mistakes | +0.023 | +0.051 | +0.079 | +0.103 | +0.113 | +0.116 |
| separate hand-written holdout set | random sample | +0.011 | +0.013 | +0.030 | +0.042 | +0.078 | +0.094 |
| separate holdout set | the model's mistakes | +0.024 | +0.064 | +0.062 | +0.086 | +0.069 | +0.068 |

(N is the total number of corrections across all modes; the app keeps at most 300.) The full per-user tables, including
the range over seeds, the share of decisions fixed or broken and a score at realistic traffic mix, are in
[`personal_model_simulation.md`](personal_model_simulation.md).

What this says:
- **It helps, and the effect grows with the number of corrections.** The mean change is positive in every row.
- **Corrections that fix the model's mistakes are worth more than random ones**, about 2 to 3 times at 100 corrections.
  That matches how the app collects them: a user only flips a notification when the app got it wrong.
- **It helps most for a user whose tastes differ from the built-in ones.** A "minimalist" who wants only genuine safety
  alerts goes from F1 0.37 to 0.66 with 300 of the model's mistakes corrected (same-kind split), against 0.50 to 0.69 for the
  "standard" user.
- **It generalises mostly to the same kinds of notification** (the kernel is deliberately local). For kinds of message the
  user has never corrected the gain is real but roughly 60% as large.
- **It is not free of harm.** With 100 or more corrections (same-kind and new-kind splits), 1% to 14% of previously correct
  decisions were broken while 4% to 19% of wrong ones were fixed. Broken decisions grow with the number of corrections and
  are highest for users whose tastes conflict with the model's clusters (workaholic, chatty). The test asserts that the
  mean change never drops below -0.02 in any cell.
- **At realistic traffic the gain is smaller in absolute terms.** The corpus is about half urgent, while real traffic is
  mostly noise. Re-weighting the negatives so 10% of decisions are urgent, the built-in model's F1 is about 0.39 and with
  100 of its own mistakes corrected it reaches about 0.51 (same-kind). In the new-kind split the benefit levels off and
  drifts back down after about 100 corrections of mistakes (0.451 at 100, 0.438 at 300) because extra corrections add false
  positives.
- **Few corrections do almost nothing, by design.** With 10 random corrections the mean change is under +0.01.

Caveats: the labels are rules applied to synthetic templates, so notifications within a template are near paraphrases of
each other, which flatters the same-kind split. The new-kind split and the separate holdout are the conservative figures.
The users are simulated, not people; real corrections will be noisier and may contradict each other. Parameters were
tuned on the golden set only (cross-validation); the corpus and the holdout set are independent confirmation.

### 3.3 The corpus (`testdata/corpus/corpus_notifications.json`)
1,209 synthetic notifications built by `scripts/generate_corpus.py` from 185 hand-written templates in 48 categories
(deterministic, disjoint from the golden and holdout sets, no duplicates). Each item records its `category`, `template`,
surface-form `variant` and `labels` for six simulated users:

| User | What they want Shown |
| :--- | :--- |
| standard | the same rules as the golden set |
| chatty | also casual chat in Commute, Focus and Workout |
| workaholic | work alerts (incidents, build failures, meetings, deadlines) in Commute, Focus and Workout as well |
| minimalist | only genuine safety alerts (family distress, public emergencies, home alarms, critical health, door security), in every mode |
| news fan | also headlines and weather in Commute and Workout |
| parent | school alerts in every mode, and parcels while commuting |

**Version 1 (590 items, 94 templates)** covered everyday English traffic. **Version 2 (619 more items, 91 more
templates)** was added after measuring where the built-in model fails (see
[`model_coverage_gaps.md`](model_coverage_gaps.md)). The v1 items are unchanged, so earlier results (sections 3.1 and 3.4) still
apply to them. v2 adds:

| Addition | Why |
| :--- | :--- |
| More phrasings of safety alerts (public emergencies, home alarms, critical health, school, family distress, door security) | the model was weakest here and these must reach the user in every mode |
| Typos and abbreviations ("bus 42 arrivng in 2 mins", "pls cll me asap") | real people write like this |
| ALL CAPS and emoji ("URGENT: ... IS DOWN", "🔥🔥 MEGA SALE 🔥🔥") | common in alerts and promotions |
| Hinglish and Devanagari Hindi | the app's users include Hindi speakers and the encoder is English-only |
| Long, multi-sentence messages | alerts are not always one short line |
| Group-chat format ("Family group", "Office group") and sender prefixes | urgent news often arrives in a group |
| Very short messages ("Ok", "Delivered", "Sale!") | little context to go on |
| Missed calls, flight delays and cancellations, train status, ongoing/system notifications, sports scores, stock alerts, payments, kids' location, school notices, ordinary email and subscription traffic | new categories and more everyday noise |

About 52% of items are urgent in some mode, far above real traffic; the simulation therefore also reports a score
re-weighted to 10% urgent. 41 items are deliberate near-misses ("Build #4413 succeeded", "Fire sale: 50% off").
To grow the corpus, add templates at the end of `scripts/generate_corpus.py` (older templates must keep their order so
existing items stay stable) and re-run it; prefer adding real, anonymised notifications of your own over inflating the
synthetic ones.

### 3.4 Real service on the v1 corpus (`replay_personal.py --corpus`)
The same real-service experiment as 3.1, on the 590-item v1 corpus, one random seed per row. Half of each template's instances are the
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
### 3.5 Real service on the expanded corpus (`replay_personal.py --corpus --test-sample 300`)
The same experiment on the full 1,209-item corpus (section 3.3), one random seed per row. The pool the corrections come
from is 626 items (half of each template); the app is then scored on a fixed random sample of 300 of the other half,
never corrected. About 18,000 notifications went through the real service. Macro F1 over the five modes:

| Simulated user | Corrections chosen | N per mode | F1 before | F1 after | Change | Corrected items, accuracy before -> after |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| standard | random sample | 20 | 0.530 | 0.519 | -0.012 | 0.79 -> 0.89 |
| standard | random sample | 60 | 0.530 | 0.639 | +0.109 | 0.81 -> 0.96 |
| standard | the model's own mistakes | 20 | 0.530 | 0.684 | +0.153 | 0.00 -> 1.00 |
| standard | the model's own mistakes | 60 | 0.530 | 0.639 | +0.109 | 0.00 -> 1.00 |
| workaholic | random sample | 20 | 0.503 | 0.574 | +0.071 | 0.79 -> 0.99 |
| workaholic | random sample | 60 | 0.503 | 0.607 | +0.105 | 0.78 -> 0.97 |
| workaholic | the model's own mistakes | 20 | 0.503 | 0.650 | +0.147 | 0.00 -> 1.00 |
| workaholic | the model's own mistakes | 60 | 0.503 | 0.631 | +0.128 | 0.00 -> 1.00 |
| minimalist | random sample | 20 | 0.410 | 0.455 | +0.044 | 0.72 -> 0.96 |
| minimalist | random sample | 60 | 0.410 | 0.527 | +0.116 | 0.76 -> 0.97 |
| minimalist | the model's own mistakes | 20 | 0.410 | 0.577 | +0.166 | 0.00 -> 1.00 |
| minimalist | the model's own mistakes | 60 | 0.410 | 0.581 | +0.170 | 0.00 -> 1.00 |

- **Eleven of twelve configurations improved on never-seen notifications; one got slightly worse** (a standard user
  teaching 20 random corrections per mode: -0.012). That matches the simulation: a small random sample moves almost
  nothing and can nudge a few decisions the wrong way.
- **Fixing the model's own mistakes beats random sampling** in every comparison (+0.109 to +0.170, against +0.044 to
  +0.116 for random at the same size), and the corrected notifications themselves always came out right.
- **More is not always better.** For the standard and workaholic users, 60 corrections of mistakes did slightly worse
  than 20 (0.639 against 0.684, and 0.631 against 0.650), the same plateau and drift the simulation shows once the useful
  corrections have been made. The minimalist, whose tastes differ most from the defaults, kept gaining.
- **Almost all the app's mistakes here were the model's, so the personal model could reach them.** In the pool the
  baseline made 548 mistakes decided by the embedding model and 92 decided by exceptions (86% against 14%), because the
  corpus avoids exception keywords. On the golden set the split was the other way for Sleeping and Workout.
- **Baselines.** The built-in app scored F1 0.530 (standard), 0.503 (workaholic) and 0.410 (minimalist) on the unseen
  sample.
- **Harness note.** 5 of about 18,000 posted notifications never reached the app and were left out of their score
  (the script re-posts misses twice; these still failed). Hindi and emoji notifications are posted correctly only when
  Python runs in UTF-8 mode or after the fix to `replay_golden.py` that writes the post script as UTF-8.

Total real-service runs behind sections 3.1, 3.4 and 3.5: 32 configurations, each across all five modes.

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
