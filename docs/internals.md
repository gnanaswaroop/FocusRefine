# FocusRefine — Technical Implementation Guide & Architecture

This document provides a comprehensive technical breakdown of the architecture, data flows, core services, persistence layers, and 16 KB memory page size alignment in **FocusRefine**.

---

## 1. Architecture & Component Interaction

```mermaid
graph TD
    A[System Notifications] -->|onNotificationPosted| B[NotificationFilterService]
    B -->|classify| X[NotificationClassifier]
    X -->|1. Learned & Synthesized Rules| D[Room DB / AiRuleSynthesizer]
    X -->|2. Check Exceptions| E[ExceptionManager: exceptions.json]
    X -->|3. Optional MediaPipe TextClassifier| C[text_classifier.tflite if bundled]
    X -->|4. Semantic Prompt Search| F[PromptManager & Enriched Prompts]
    X -->|Shown/Muted + explanation| G[Room DB: NotificationEntity + Explanation]
    I[MainActivity] -->|Smart Inbox, Swipe Animations & Gestures| G
    J[SettingsActivity] -->|Collapsible Cards, Advanced Swipe Toggle, Learnings Link| B
    J -->|Re-run classification on history| R[HistoryReclassifier]
    R -->|classify every stored row| X
    R -->|update isUrgent + explanation| G
    K[NotificationDetailActivity] -->|Inspect AI Rationale & Reclassify| D
    L[ConfigureModesActivity] -->|Per-App Rules Tuning| B
    M[ContextModesActivity] -->|Manage Modes & Show-on-Home Toggles| B
    N[PrivacyInsightsActivity] -->|Reset Local Learning Data| D
```

---

## 2. Core Technical Components

### A. Background Notification Filtering (`NotificationFilterService`)
- **Lifecycle**: Persistent Android Foreground Service (`specialUse`) implementing `NotificationListenerService`.
- **Interruption Filter Safety**: Ensures system Do Not Disturb mode is **always restored to normal (`INTERRUPTION_FILTER_ALL`)** whenever service filtering is paused, stopped, or destroyed (`onDestroy`), preventing devices from being stuck in DND mode.
- **Delegation**: The service no longer contains the decision logic. On `onCreate` it obtains the (optional) MediaPipe classifier via `NotificationClassifier.createTextClassifier(this)` and builds a `NotificationClassifier`; `onNotificationPosted` calls `classifier.classify(title, text, currentMode)`, then persists the result and cancels Muted notifications.

### B. Classification Engine (`NotificationClassifier.kt`)
`NotificationClassifier(context, database, textClassifier?)` owns the Shown/Muted decision and is shared by the live service and the history re-run (section C), so both always agree. `classify()` returns a top-level `ClassificationResult(isUrgent, explanation)`.

**Decision precedence (first match wins; see the [flowchart](ai_classification_and_modes.md#4-mediapipe-ai-pipeline-vs-semantic-search-inputs)):**
1. **User learned rules** (`NotificationDao.findMatchingLearnedRule`) and **AI-synthesized rules** (`AiRuleSynthesizer.matchesRule()`).
2. **JSON-driven exceptions** from `assets/exceptions.json` (`ExceptionManager`), each gated by its SharedPreferences key.
3. **MediaPipe `TextClassifier` result**, if the model loaded and produced an `urgent` category. The model is only consulted at this step (inference is skipped when a rule or exception already decided), so rules and exceptions always override it. Thresholds follow filtering strength (`Gentle` = `0.4f`, `Balanced` = `0.6f`, `Strict` = `0.8f`).
4. **Keyword fallback (`🔍 [Semantic Search]`)**: the active mode prompt, enriched with synthesized rules (`PromptManager.getEnrichedModePrompt`), is split into tokens and matched as substrings.

**Engine availability (verified on a Pixel 9 Pro XL and the emulator):**
- `text_classifier.tflite` is **not in the repository**. Without it `TextClassifier.createFromOptions` fails (MediaPipe reports a `RET_CHECK failure ... doesn't have a slash in it` message for a missing asset), the failure is caught in `createTextClassifier`, and **every notification is decided by the keyword fallback**, on real devices as well as emulators. The AI Pipeline path only runs if a model that outputs an `urgent` category is bundled.
- MediaPipe's public classifiers (`average_word_classifier`, `bert_classifier`) are **sentiment** models (`positive` / `negative`). They load and run, but never produce `urgent`, so they never decide anything. A custom-trained model would be required.
- The Settings and Privacy screens currently derive "AI Engine Active" from an emulator fingerprint check, not from whether the model actually loaded.

**Known limitations of the keyword engine / exceptions (measured, see section K):** prompt tokens are matched as raw substrings (for example `or` and `and` match inside unrelated words), exceptions are matched against title *and* text so app names such as "Maps" or "Bank" trigger them, and the `Workout / Gym` mode has no dedicated branch so it falls back to a generic default prompt.

### C. History Re-classification (`HistoryReclassifier.kt`)
Settings → **Learning & Feedback** → **Re-run classification on history** re-checks every stored notification with the current pipeline.

- Runs `NotificationClassifier.classify` over `NotificationDao.getHistory()` (all rows where `isLearned = 0`) using the **current** mode, filtering strength, exceptions, and learned rules. It creates and closes its own `TextClassifier` for the run.
- Rows whose decision or explanation changed are written back with `NotificationDao.updateClassification(id, isUrgent, explanation)`. Learned-rule rows (`isLearned = 1`) are the user's own feedback and are **never modified**.
- The muted counter (`suppressed_count`) is adjusted by the net number of rows that moved between Shown and Muted, then `ACTION_UPDATE_COUNT` and `ACTION_NOTIFICATION_ARRIVED` are broadcast so the foreground notification and inbox refresh.
- Only the in-app inbox changes. Nothing is re-posted to, or removed from, the system notification shade.
- Returns `HistoryReclassifier.Summary(total, toShown, toMuted, failed)`; per-row failures are counted and logged without aborting the run. The call is blocking and runs on `Dispatchers.IO` from `SettingsActivity`, which shows a confirmation dialog first and a progress indicator while it runs.

### D. Dark Studio Design System & Reusable UI Components
- **`FocusRefineTheme`**: Defines `#0F0E17` dark background, `#181625` / `#201D33` elevated surfaces, `#8B5CF6` electric violet primary accents, `#22C55E` green active badges, and `#EF4444` muted tags.
- **`StudioCard`**: Rounded container (`20.dp` corners) defaulting to `Modifier.fillMaxWidth()` with `#352F52` borders.
- **`AppIconImage`**: Renders native application icons directly from installed package manifests via `PackageManager`.
- **`StudioSegmentedControl`**: Horizontal tri-state and multi-option pill selector.
- **`StudioFooter`**: Scroll-triggered footer embedded inside containers.

### E. 6 Primary Screen Activities
1. **`MainActivity.kt`**: Smart Inbox dashboard with stat card, home context mode bubbles (enabled modes only, long press opens `ContextModesActivity`), search field, horizontal swipe gesture with smooth Compose `AnimatedContent` slide transitions (configurable via Advanced Settings), and full-screen inbox dialog (`⛶`).
2. **`SettingsActivity.kt`**: Collapsible studio cards (open/expanded by default), emulator detection fallback, filtering strength selector (`Gentle` | `Balanced` | `Strict`), JSON-driven exception toggles, a **Learning & Feedback** card (learn-from-choices switch, Learned Rules link, **Re-run classification on history**, reset learning data), Advanced Settings with **Inbox Swipe Animations** switch, and direct navigation links to **Learned Rules** (`Learnings`).
3. **`NotificationDetailActivity.kt`**: Notification inspection card, AI explanation card (*"Why it was muted/shown"*), updated preference action buttons (*"🔔 Show notifications like these in the future"* / *"🔕 Hide notifications like these in the future"*), and feedback controls.
4. **`ConfigureModesActivity.kt`**: Per-app rules list with expandable rule tuner drawers (`Use AI` | `Allow all` | `Mute all`). Purely focused on App Rules.
5. **`ContextModesActivity.kt`**: Context modes management hub. Allows adding custom context modes, editing natural language prompt rules, and setting *"Show on Home Page"* toggles for each mode.
6. **`PrivacyInsightsActivity.kt`**: On-Device protection status badge, daily filtering metrics card with dynamic `0% Noise Filtered` reset, and clear local learning data reset dialog.

### F. 16 KB Page Size Alignment (Android 15 Compatibility)
- Configured in `app/build.gradle.kts`:
  ```kotlin
  packaging {
      jniLibs {
          useLegacyPackaging = false
      }
  }
  ```
- Unsets `android:extractNativeLibs="true"` in `AndroidManifest.xml`, so uncompressed native libraries are mapped directly from the APK.
- **Library version matters (known gap).** The build uses `com.google.mediapipe:tasks-text:0.10.14`, whose native library has **4 KB** segment alignment (checked from the ELF `LOAD` segments) and no `x86_64` build, which is why x86_64 emulators cannot load it. So the 16 KB claim above does not hold for this dependency today.
- A newer release fixes both: **`tasks-text:1.1.1`** ships `libmediapipe_tasks_jni.so` **16 KB aligned on all four ABIs** (`arm64-v8a`, `armeabi-v7a`, `x86`, `x86_64`; verified on a built APK), with `minSdk` 24. Upgrading is a recommended follow-up, not applied here: it also bundles an unused GenAI native library, so per-ABI native code grows (arm64: about 14 MB + 15 MB uncompressed, versus about 8 MB), and it needs a size/regression check before a Play release.

### G. Externalized Configuration Assets
- `assets/prompts.json`: Managed via `PromptManager.kt`.
- `assets/exceptions.json`: Managed via `ExceptionManager.kt`.
- `assets/samples.json`: 20 pre-configured test notifications for instant offline benchmarking and UI evaluation.

---

## 3. Testing & Evaluation

### H. Labeled datasets (`testdata/`)
Two disjoint labeled sets in `testdata/golden/`, documented in `testdata/README.md`:
- `golden_notifications.json` (96): regression set.
- `holdout_notifications.json` (76): unseen set. Never tune thresholds, prompts, or anchors on it.

Each item is `{ "title", "text", "urgentIn": [mode names] }`: the modes in which the notification must be **Shown**, and Muted in all others. The labels are hand-written (synthetic), a judgment call rather than real traffic.

### I. ADB end-to-end replay (`scripts/replay_golden.py`)
Posts every notification from the adb shell (`cmd notification post`) so it passes through the real `NotificationFilterService`, then reads the app's Room database back with `run-as` and scores Shown/Muted per mode (precision, recall, F1, accuracy, and what decided each result: exception, prompt keyword, learned rule, AI model, or no keyword).

```
python scripts/replay_golden.py --serial emulator-5554
python scripts/replay_golden.py --serial <device> --allow-device --min-f1 0.6
```
- Needs a **debug** build (uses `run-as`) and notification-listener access.
- Wipes the app's notification history and sets `current_mode` / `use_ai_model`. It backs up the database and preferences first, then restores the original preferences and the learned rules (history stays cleared) and restores Do Not Disturb. It refuses non-emulators without `--allow-device`.
- Harness details that matter: it waits for a readiness probe before posting; Android silently drops posts beyond **50 active notifications per package**, so it reuses a pool of 24 tags and force-stops `com.android.shell` between modes (which cancels the test notifications); and it re-posts anything that never arrives.
- Reports go to `app/build/golden-reports/` (git-ignored).

### J. Instrumented tests (`app/src/androidTest/`)
- `HistoryReclassifierTest`: learned rules flip history in both directions, learned rows are untouched, the muted counter stays consistent, a second run changes nothing, and empty history is a no-op. It uses the app's real database, so run it on an emulator only.
- Run instrumentation with `adb shell am instrument -w -e class <test> com.focusrefine.app.test/androidx.test.runner.AndroidJUnitRunner`, not `connectedDebugAndroidTest`: Gradle uninstalls the app afterwards, which erases the device's history and learned rules.

### K. Measured results
**Production engine, end to end** (real service, all 5 modes, macro average; identical on the emulator and a Pixel 9 Pro XL since both run the keyword engine):

| Set | F1 | Precision | Recall | Accuracy |
| :--- | :--- | :--- | :--- | :--- |
| Golden (96) | 0.396 | 0.292 | 0.617 | 0.710 |
| Holdout (76) | 0.360 | 0.288 | 0.489 | 0.687 |

Per mode F1 (golden): Commute 0.50, Deep Work / Focus 0.46, DND 0.42, Sleeping / Rest 0.49, Workout / Gym 0.11.

**TextEmbedder prototype** (findings from a throwaway spike; **not part of this codebase**). Each mode is scored as the best similarity to a few example phrases for that mode minus the best similarity to "routine" anchors (promos, social, newsletters). Thresholds are tuned on the golden set and applied unchanged to the holdout set:

| Method | Golden F1 / AUC | Holdout F1 / AUC |
| :--- | :--- | :--- |
| Similarity to mode prompt only | 0.61 / 0.82 | 0.44 / 0.79 |
| Examples minus routine (best) | 0.77 / 0.93 | 0.60 / 0.89 |
| Keyword engine (in-process baseline) | 0.42 / n.a. | 0.35 / n.a. |

The held-out figures are the honest ones; the golden figures are in-sample. The labels and anchor phrases were written by the same author, so both are optimistic relative to real traffic.

Device measurements for the embedder (Universal Sentence Encoder, `tasks-text:1.1.1`): model load 82–125 ms on the Pixel; per-notification embedding p50 about 38–44 ms and p95 about 83–95 ms on the Pixel (5.8 ms on the emulator's host CPU); about 6 MB native heap; embedding the 47 anchor phrases takes 1.6–1.9 s on the Pixel, so anchors should be precomputed or cached. Four threads sharing one embedder produced no errors or wrong results but no speedup. Accuracy is identical across devices.
