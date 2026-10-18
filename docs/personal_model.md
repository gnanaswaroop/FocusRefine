# Personal Model: learning one user's preferences on the device

FocusRefine ships a built-in embedding classifier ([AI Classification Architecture](ai_classification_and_modes.md)). The **personal model** is a very small model that sits on top of it and learns from one user's own Shown/Muted corrections. It exists so the app can adapt to a person without any message ever leaving the phone.

## 1. Privacy guarantees

| Guarantee | How it is enforced |
| :--- | :--- |
| No network access | The manifest removes `INTERNET` and `ACCESS_NETWORK_STATE` (a MediaPipe dependency adds them); `PrivacyGuardsTest` fails if they return or if a socket can connect |
| No cloud backup | `allowBackup="false"`; checked by `PrivacyGuardsTest` |
| Stored data is not message text | The file holds sentence-embedding vectors (arrays of numbers), a label and a one-way key; `PersonalLearningTest` asserts that title and body words do not appear in the file |
| Stays in app-private storage | `filesDir/personal_model.bin`, readable only by this app's UID |
| Training happens on the device | Plain Kotlin gradient descent, about 4 ms for 80 corrections |
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

## 3. Measured benefit (simulation)

`PersonalModelBenchmarkTest` simulates a user who corrects the built-in model's mistakes, in random order, using the golden set, then scores the **unseen holdout set** (embedding model alone, macro F1 over the five modes, mean of three random orders):

| Corrections | "Standard" user (agrees with the dataset labels) | "Chatty" user (also wants chat apps Shown in Commute, Focus and Workout) |
| :--- | :--- | :--- |
| 0 (built-in) | 0.599 | 0.556 |
| 5 | 0.613 | 0.559 |
| 10 | 0.632 | 0.567 |
| 20 | 0.648 | 0.584 |
| 40 | 0.666 | 0.638 |
| 80 | 0.664 | 0.650 |

- It never fell below the built-in score at any size. (A first design, a linear model over the embedding, did: with 5 corrections the holdout F1 dropped from 0.60 to 0.22, because the only training examples were the built-in model's mistakes and the global weights leaked onto unrelated notifications. That is why the kernel is local.)
- The gain is modest but real after a few dozen corrections: about +0.07 for the standard user and about +0.08 to +0.09 for the chatty user at 40 to 80 corrections. Few corrections give almost no change by design.
- The chatty user shows real personalisation, preferences the built-in model cannot know.
- Limits: the simulated users are synthetic, corrections are simulated from labels, and the figures are for the model alone (rules and exceptions excluded). Real users will be noisier and may contradict themselves.

Cost: training 80 corrections takes about 4 ms (emulator); each stored correction is about 2 KB, so the file is at most about 600 KB. Classification adds one pass over the stored corrections (at most 300 dot products of 512 numbers).

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
