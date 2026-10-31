# FocusRefine — Technical Implementation Guide & Architecture

This document provides a comprehensive technical breakdown of the architecture, data flows, core services, persistence layers, and 16 KB memory page size alignment in **FocusRefine**.

---

## 1. Architecture & Component Interaction

```mermaid
graph TD
    A["System notifications"] -->|onNotificationPosted| B["NotificationFilterService"]
    B -->|classify| X["NotificationClassifier"]
    X -->|1. learned and synthesized rules| D["Room DB and AiRuleSynthesizer"]
    X -->|2. exceptions| E["ExceptionManager, exceptions.json"]
    X -->|3. embedding model| C["EmbeddingClassifier, track_b.tflite, track_b_head.json, track_b_vocab.json"]
    X -->|4. prompt keywords| F["PromptManager and enriched prompts"]
    X -->|Shown or Muted plus explanation| G["Room DB, NotificationEntity and explanation"]
    I["MainActivity"] -->|Smart Inbox| G
    J["SettingsActivity"] -->|settings| B
    J -->|re-run classification on history| R["HistoryReclassifier"]
    R -->|classify every stored row| X
    R -->|update isUrgent and explanation| G
    K["NotificationDetailActivity"] -->|inspect rationale, reclassify| D
    L["ConfigureModesActivity"] -->|per-app rules| B
    M["ContextModesActivity"] -->|manage modes| B
    N["PrivacyInsightsActivity"] -->|reset learning data| D
```

---

## 2. Core Technical Components

### A. Background Notification Filtering (`NotificationFilterService`)
- **Lifecycle**: Persistent Android Foreground Service (`specialUse`) implementing `NotificationListenerService`.
- **Interruption Filter Safety**: Ensures system Do Not Disturb mode is **always restored to normal (`INTERRUPTION_FILTER_ALL`)** whenever service filtering is paused, stopped, or destroyed (`onDestroy`), preventing devices from being stuck in DND mode.
- **Delegation**: The service no longer contains the decision logic. On `onCreate` it loads the (optional) Track B model via `EmbeddingClassifier.create(this)`, starts a background warm-up inference, and builds a `NotificationClassifier`; `onNotificationPosted` calls `classifier.classify(title, text, currentMode)`, then persists the result and cancels Muted notifications.

### B. Classification Engine (`NotificationClassifier.kt`)
`NotificationClassifier(context, database, embeddingClassifier?)` owns the Shown/Muted decision and is shared by the live service and the history re-run (section C), so both always agree. `classify()` returns a top-level `ClassificationResult(isUrgent, explanation)`.

**Decision precedence (first match wins; see the [flowchart](ai_classification_and_modes.md#4-ai-pipeline-vs-semantic-search-inputs)):**
1. **User learned rules** (`NotificationDao.findMatchingLearnedRule`) and **AI-synthesized rules** (`AiRuleSynthesizer.matchesRule()`).
2. **JSON-driven exceptions** from `assets/exceptions.json` (`ExceptionManager`), each gated by its SharedPreferences key.
3. **On-device model** (`EmbeddingClassifier`, Track B), if it loaded, `use_ai_model` is on, and the active mode is a built-in one. It is only consulted at this step (skipped when a rule or exception already decided). It reads `category | app name | title | text` (the app context comes from the package name, see `track_b_apps.json`), outputs one score per built-in mode, and Shows the notification when the score reaches that mode's threshold. Filtering strength shifts the threshold (Gentle, Balanced, Strict). A personal model, trained only from the user's own corrections, can adjust the decision; with no corrections it has no effect. Custom modes have no model output, so they abstain and fall to step 4.
4. **Keyword fallback (`🔍 [Semantic Search]`)**: the active mode prompt, enriched with synthesized rules (`PromptManager.getEnrichedModePrompt`), is split into tokens and matched as substrings.

**Engine availability:**
- The model is FocusRefine's own Track B transformer, trained on synthetic data (`assets/track_b.tflite`, 11 MB), run with LiteRT 2.2.0 (`com.google.ai.edge.litert:litert`). Its vocabulary, output head, training centre and app catalogue are the `track_b_*` assets. The Kotlin tokenizer (`TrackBTokenizer`) matches SentencePiece on 1,598 of 1,600 held-out texts; the rest differ only in unknown-character handling.
- If the model cannot load, `EmbeddingClassifier.create` catches the failure, records it, and **the keyword engine decides**.
- `EmbeddingClassifier.create` writes the pref `embedding_engine_loaded` on every attempt. The Settings and Privacy screens read it, so "AI Engine Active" means the model actually loaded (they show the keyword matcher when the model is off or failed). It is only refreshed when the filter service starts.

**Known limitations of the keyword engine / exceptions (measured, see section K):** prompt tokens are matched as raw substrings (for example `or` and `and` match inside unrelated words), exceptions are matched against title *and* text so app names such as "Maps" or "Bank" trigger them, and the `Workout / Gym` mode has no dedicated branch so it falls back to a generic default prompt.

### C. History Re-classification (`HistoryReclassifier.kt`)
Settings → **Learning** → **Re-run classification on history** re-checks every stored notification with the current pipeline.

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
2. **`SettingsActivity.kt`**: Collapsible studio cards (open/expanded by default), an engine status banner driven by whether the model really loaded, filtering strength selector (`Gentle` | `Balanced` | `Strict`), JSON-driven exception toggles, a **Learning** card (learn-from-choices switch, Learned Rules link, **Re-run classification on history**, reset learning data), Advanced Settings with **Inbox Swipe Animations** switch, and direct navigation links to **Learned Rules** (`Learnings`).
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
- **Library versions.** The native libraries the app ships are LiteRT 2.2.0 (`liblitert_jni.so`) and SQLCipher for Android 4.19.1 (`libsqlcipher.so`). Both are **16 KB aligned** on arm64 and armeabi-v7a (checked from the ELF `LOAD` segment alignment of the built release APK). LiteRT's transitive `androidx.lifecycle`, Guava, Kotlin and Play groups are excluded in `app/build.gradle.kts`, because they upgraded Compose and crashed the UI. LiteRT 2.3.0 does not build with this Kotlin version.

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

**Track B against the old engine**, model only, the same 1,956 items (the full write-up is in [`track_b_vs_old_engine.md`](track_b_vs_old_engine.md); safety measurements in [`safety_recall.md`](safety_recall.md)):

| Set | Old engine (1.1.2) | Track B (this release) |
| :--- | :--- | :--- |
| Golden holdout, 1,600 held-out items, macro F1 | 0.143 | 0.748 |
| Safety holdout, 312 items never trained on, macro F1 | 0.331 | 0.939 |
| 44 device-measured safety notices shown (Commute / Focus / DND / Sleep / Workout) | 19 / 31 / 26 / 18 / 13 | 30 / 16 / 27 / 27 / 15 |

Track B's training is documented in [`track_b_decisions.md`](track_b_decisions.md). Its golden holdout score was reproduced on the device (0.748, the same as the host).

**Not measured yet for Track B:** end-to-end replay (`scripts/replay_golden.py`) with exceptions and rules in place, and phone timings for model load and per-notification inference. Earlier timings for the removed MediaPipe embedder do not apply to Track B. Add these figures here when they are measured.
