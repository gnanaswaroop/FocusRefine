# CLAUDE.md

Guidance for working in this repository. Facts marked *(verified)* were checked on a real device or against the code; anything else is from reading the code.

## What this is

**FocusRefine** (GitHub: `gnanaswaroop/FocusRefine`) is a privacy-first, fully on-device Android app that filters incoming notifications by the user's current *context mode* (Commute, Deep Work / Focus, DND, Sleeping, Workout, plus custom modes). Urgent notifications are "Shown"; the rest are cancelled from the shade and stored as "Muted" in an in-app inbox. There is no network access: the manifest declares **no INTERNET permission**, and this must stay true.

- Package / applicationId: `com.focusrefine.app` (all code in one package, `app/src/main/java/com/focusrefine/app/`)
- Single Gradle module `:app`. Kotlin, Jetpack Compose (Material3) UI, Room, **MediaPipe Tasks Text 1.1.1** (`TextEmbedder`; 16 KB-aligned natives for all four ABIs, including `x86_64`).
- `minSdk 28`, `compileSdk/targetSdk 34`, versionName `1.0`, `versionCode 1`. Java/Kotlin target 1.8. AGP 9.4.1, Kotlin 2.2.10, KSP 2.3.6 (root `build.gradle.kts`); `composeOptions.kotlinCompilerExtensionVersion = 1.5.8`.

## Repo state

- Default branch `main`. History uses **merge commits** for PRs (`Merge pull request #N from ...`) and conventional commit subjects (`feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `chore:`).
- The TextEmbedder classifier lands via PR from `feat/embedding-classifier` (see "Embedding classifier"). The older `spike/text-embedder` branch holds only the first commit of that work and is superseded; it can be deleted once the PR is merged.
- The embedding model's license is **unconfirmed** (`THIRD_PARTY_MODELS.md`): do not publish a release build until that is resolved.
- `CLAUDE.md` is tracked; `.claude/` (local Claude Code state, including worktrees) is git-ignored.

## Commands

Run from the repo root (Windows: `gradlew.bat`; Git Bash: `./gradlew`).

```
./gradlew assembleDebug            # build debug APK
./gradlew testDebugUnitTest        # JVM unit tests (app/src/test)
./gradlew connectedDebugAndroidTest # instrumented tests - needs device/emulator (see warning below)
./gradlew bundleRelease            # signed AAB, as used by CI
```

**Do not use `connectedDebugAndroidTest` on a device whose data matters:** Gradle uninstalls the app afterwards, wiping history and learned rules. Use `am instrument` instead:

```
./gradlew assembleDebug assembleDebugAndroidTest
adb -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk
adb -s <serial> install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
# whole suite (41 tests, all passing on the emulator with the embedding classifier)
adb -s <serial> shell am instrument -w com.focusrefine.app.test/androidx.test.runner.AndroidJUnitRunner
# one class
adb -s <serial> shell am instrument -w -e class com.focusrefine.app.HistoryReclassifierTest com.focusrefine.app.test/androidx.test.runner.AndroidJUnitRunner
python scripts/replay_golden.py --serial emulator-5554     # adb end-to-end replay (see Tests)
```

CI uses JDK 17. Lint is non-fatal (`abortOnError = false`). The `release` build signs with the **debug** signing config and has minification off. `testInstrumentationRunner` is `androidx.test.runner.AndroidJUnitRunner`.

## Architecture

### Classification pipeline - the heart of the app
`NotificationFilterService` (a `NotificationListenerService` that is also a `specialUse` foreground service) handles `onNotificationPosted`. It ignores its own package and `com.android.systemui`, returns early if the `service_running` pref is false, then on `Dispatchers.IO` calls `NotificationClassifier.classify(title, text, mode)` and persists/cancels based on the result. The same class is used by `HistoryReclassifier`, so **keep all decision logic in `NotificationClassifier` and nowhere else**. `ClassificationResult(isUrgent, explanation)` is a top-level data class.

`classify()` is a flat precedence chain, first match wins (a Mermaid flowchart is in `docs/ai_classification_and_modes.md` section 4):

1. **Learned rule**: `NotificationDao.findMatchingLearnedRule` (same title, or text LIKE).
2. **AI-synthesized rule**: `AiRuleSynthesizer.matchesRule` token overlap over `getSynthesizedRules()` (fetched lazily).
3. **Exception** from `assets/exceptions.json` (navigation, calls/priority contacts, OTP/security), each gated by a SharedPreferences key (`prefKey`, `defaultEnabled`). A keyword hit in title+text means Shown.
4. **Embedding model** (`EmbeddingClassifier`, MediaPipe `TextEmbedder` + `assets/text_embedder.tflite`), consulted only here, only if it loaded and `use_ai_model` is on, and only for built-in modes (custom modes abstain). Score = best cosine similarity to the mode's example phrases minus best similarity to routine phrases; Shown if score >= the mode's threshold. Examples, routine phrases and thresholds are data in `assets/embedding_anchors.json` (tuned on the golden set only). `filter_strength` shifts the threshold: Gentle 0 = -0.02, Balanced 1 = 0 (default), Strict 2 = +0.02. A per-user **personal model** (`PersonalModel`, a local kernel model trained from the user's own Shown/Muted corrections) adds an adjustment to the decision; with no corrections it is exactly zero. See `docs/personal_model.md`. Evidence: `scripts/replay_personal.py` (real service) and `PersonalCorpusSimulationTest` (3,000+ models on the 1,209-item `testdata/corpus`, regenerate with `scripts/generate_corpus.py`).
5. **Mode-prompt keyword fallback** (always returns): the active mode's prompt (user-edited pref `prompt_<name>` or default from `assets/prompts.json`; custom modes use `custom_mode_prompt_<mode>`), enriched with synthesized rules via `PromptManager.getEnrichedModePrompt`, is split on `, ; : [ ]` and whitespace into tokens; any token contained (as a substring) in the notification means Shown, otherwise Muted. Built-in modes are matched by `mode.contains(...)` against the `BUILT_IN_MODES` table (`Commute`, `Focus`, `Sleep`, `DND`).

Every result carries a human-readable `explanation` prefixed `🤖 [AI Pipeline]` or `🔍 [Semantic Search]`. These are persisted, shown in `NotificationDetailActivity` ("why it was muted/shown"), and matched by tests, docs and the replay script, so **keep the wording and prefixes stable**.

**Engine reality:** `EmbeddingClassifier.create` loads the model in `NotificationFilterService.onCreate` (and in `HistoryReclassifier`), writes the pref `embedding_engine_loaded` on every attempt, and returns null on failure or when `use_ai_model` is off; then steps 1, 2, 3 and 5 decide (keyword engine). The service warms the anchors up on a background thread (about 1.7 s on a Pixel, once per process). `EmbeddingClassifier` serializes access with a lock so `close()` cannot free the model during a warm-up. The Settings/Privacy "AI Engine Active" labels read `embedding_engine_loaded` (refreshed only when the service starts). Verified on the emulator (`emulator-5554`): the model decides non-exception items (`decided by {'exception': 21, 'ai model': 75}`). Release build checked on the Pixel 9 Pro XL with a 7-notification probe (matches the emulator; a release build is not debuggable, so no `run-as`/replay there). A full replay on the Pixel has not been run. The old `text_classifier.tflite` / `TextClassifier` path was removed (public MediaPipe classifiers are sentiment-only).

Shown notifications are left in the shade and saved with `isUrgent=true`. Muted ones are only cancelled/counted/saved if `sbn.isClearable`; they increment `suppressed_count`, refresh the foreground notification, and are saved with `isUrgent=false`. Both send the package-scoped `ACTION_NOTIFICATION_ARRIVED` broadcast so `MainActivity` refreshes.

On `onDestroy`/`onTaskRemoved` the service must restore `INTERRUPTION_FILTER_ALL` (if policy access is granted) so the device is never stuck in system DND. Preserve this.

### Learning from user feedback
In `NotificationDetailActivity` the user can flip a notification to Shown/Muted. That marks the row `isLearned=true` and sets `synthesizedRule` from `AiRuleSynthesizer.synthesizeRule(...)` (e.g. `[MUTE] Whatsapp: grab coffee later`: action tag, derived app name, up to 4 significant tokens, stop-words removed, length >= 3). Matching is `>=2` token hits, or the single-token case, or >=50% overlap. `LearnedRulesActivity` lists/removes them; `PrivacyInsightsActivity` can wipe them (`deleteLearnedRules`). Each flip also records a correction in the personal model (`PersonalLearning.record`, for built-in modes only), stored as embeddings in `filesDir/personal_model.bin` (no message text); deleting a rule calls `PersonalLearning.forget`, and every reset path calls `PersonalLearning.clear`. Learned rules live in the same `notifications` table, so `deleteAll()` and retention cleanup (`deleteOlderThan`) also affect them - check this before changing either.

### History re-classification
`HistoryReclassifier.run(context)` (Settings -> Learning & Feedback -> "Re-run classification on history", with a confirmation dialog) re-classifies all rows with `isLearned = 0` (`NotificationDao.getHistory()`) using the **current** mode and settings, writes changes with `updateClassification(id, isUrgent, explanation)`, never touches learned-rule rows, and adjusts `suppressed_count` by the net Shown/Muted movement. Only the inbox changes; nothing is re-posted to or cancelled from the shade. It is blocking: call it on `Dispatchers.IO`.

### Data & state
- **Room**: `AppDatabase` (`focusrefine_database`, version 5, `exportSchema=false`, `fallbackToDestructiveMigration()`, so bumping the version **wipes user data and learned rules**). One entity `NotificationEntity` (`title, text, packageName, timestamp, isUrgent, isLearned, synthesizedRule, explanation`) and `NotificationDao`. DAO methods are blocking (non-suspend); call them off the main thread.
- **SharedPreferences** `"FocusRefinePrefs"` is the single settings store, accessed ad hoc from every Activity and the service (no wrapper). Known keys: `service_running`, `suppressed_count`, `current_mode`, `use_ai_model`, `filter_strength`, `theme_mode`, `learn_from_choices`, `enable_swipe_animations`, `prompt_<mode>`, `custom_mode_prompt_<mode>`, `mode_enabled_<mode>`, `app_rule_type_whatsapp`, exception `prefKey`s (`exception_nav_prompts`, `exception_calls`, `exception_security_otps`). Search the codebase for the rest before adding new keys.
- **Assets** (`app/src/main/assets/`): `prompts.json` (OOTB modes `Commute`, `Deep Work / Focus`, `Do Not Disturb (DND)`, `Sleeping / Rest`, `Workout / Gym` -> `{prompt, enabled}`; loaded by `PromptManager`; first enabled entry is the default mode), `exceptions.json` (`ExceptionManager`), `samples.json` (test notifications for "Send Sample Notifications"). Mode names in `prompts.json` are used as pref-key suffixes, so renaming one orphans saved user prompts.

### UI
All screens are Compose with a shared "Dark Studio" design system in `FocusRefineTheme.kt` (`StudioColors`, `StudioCard`, `PillBadge`, `StudioSegmentedControl`, `StudioFooter`, `AppIconImage`); dark-first, palette base `#0F0E17` with violet `#8B5CF6` accent. Reuse these components rather than raw Material widgets. Settings sections are collapsible cards.

| Activity | Role |
| --- | --- |
| `SplashActivity` | Launcher entry |
| `MainActivity` (~1250 lines) | Smart Inbox: Shown/Muted tabs, search, mode bubbles, swipe-between-tabs animation, full-screen inbox |
| `SettingsActivity` | AI toggle/status, theme, filtering strength, exceptions, retention, Learning & Feedback (incl. re-run classification on history), advanced settings, sample notifications |
| `NotificationDetailActivity` | Inspect one notification, AI rationale, reclassify |
| `ContextModesActivity` | Add/edit modes and prompts, "show on home" toggles |
| `ConfigureModesActivity` | Per-app rules (`Use AI` / `Allow all` / `Mute all`) - only a WhatsApp pref exists; not consulted by the classifier |
| `LearnedRulesActivity` | View/remove learned rules |
| `PrivacyInsightsActivity` | Privacy status, noise-filtered %, reset learning data |

Other components: `App` (applies saved night mode at startup), `NotificationToggleReceiver` (STOP/START action in the ongoing notification, action `com.focusrefine.app.ACTION_TOGGLE_SERVICE`), `AutoActivationReceiver` (Bluetooth ACL connect/disconnect to set `current_mode`), `AndroidAutoDetector`.

Note: `AutoActivationReceiver` writes mode names `"Commute"` and `"Focus"`, while `prompts.json` names the mode `"Deep Work / Focus"`. The `mode.contains("Focus")` lookup tolerates this, but be careful when changing mode names.

## Tests

- `app/src/test/` (JVM): `PersonalModelTest` (the personal model's math), `TextClassificationTest`, `PromptEvaluationTest`, `NotificationBenchmarkTest`. These mostly re-implement the keyword logic locally rather than calling the classifier, so they don't catch regressions in `NotificationClassifier`.
- `app/src/androidTest/` (device): `NotificationFilterE2ETest` (in-memory Room, toggle sync, learned-rule overrides, strength/exception prefs, synthesizer flows; it does **not** run the service), `ActivityE2ETest`, `PromptManagerTest`, `ThemeToggleTest`, `HistoryReclassifierTest`, `PersonalLearningTest` (real feedback path; asserts the stored file holds no message text), `PrivacyGuardsTest`, `PersonalModelBenchmarkTest` (learning-curve simulation, logs `PERS_BENCH`), `EmbeddingClassifierTest` (asserts: model loads, urgent Shown / routine Muted, custom modes abstain, strength shifts the threshold, closed classifier fails fast), `EmbedderBenchmarkTest` (needs the golden assets: `sourceSets["androidTest"].assets.srcDir("testdata/golden")`). Because Room DAO calls are blocking, tests use `runBlocking` and an in-memory DB.
- `HistoryReclassifierTest` uses the app's **real** database and wipes it: emulator only.
- **Golden datasets** in `testdata/golden/` (schema and labeling rules in `testdata/README.md`): `golden_notifications.json` (96 items, free to tune on) and `holdout_notifications.json` (76 unseen items, **never tune on it**; keep the two disjoint). Items are `{title, text, urgentIn: [modes in which it must be Shown]}`; labels are synthetic and hand-written. OTP/security is deliberately absent (handled by exceptions).
- **`scripts/replay_golden.py`** is the real end-to-end test: it posts every notification from the adb shell (`cmd notification post`), lets the real service classify it, reads the Room DB back via `run-as` (debug builds only), and scores Shown/Muted per mode. It wipes history and pins `current_mode`, `use_ai_model`, `filter_strength` (Balanced) and the three exception toggles, and drops edited prompts (the instrumented tests leave those prefs changed, which once moved the scores from 0.570/0.527 to 0.629/0.546), so it backs up the DB and prefs (to `app/build/golden-reports/backup_*`) and afterwards restores prefs and learned rules (history stays cleared); it refuses non-emulators without `--allow-device`; on a device it sets priority-only DND during the run and restores it. Options: `--engine ai|keyword` (default `ai`; `keyword` sets `use_ai_model` off), `--dataset`, `--modes`, `--limit`, `--min-f1` (regression gate, exit 1), `--delay`, `--settle`. Harness gotchas it handles: wait for the listener to rebind (readiness probe), Android silently drops posts beyond **50 active notifications per package** (it reuses 24 tags and `am force-stop com.android.shell` clears them), and it re-posts anything that never arrives.
- **Baseline for regression checks.** Embedding engine (default, emulator, `scripts/replay_golden.py`): measured at Balanced (`filter_strength` 1; `replay_golden.py` now pins it, an earlier run accidentally used Strict): golden macro F1 **0.570** (Commute 0.81, Focus 0.45, DND 0.57, Sleeping 0.65, Workout 0.37), holdout **0.527** (0.74 / 0.47 / 0.53 / 0.62 / 0.27). Keyword engine (`--engine keyword`; identical on emulator and Pixel; F1 / FP / FN per mode): golden Commute 0.50 / 33 / 5, Focus 0.46 / 21 / 5, DND 0.42 / 19 / 3, Sleeping 0.49 / 17 / 4, Workout 0.11 / 22 / 10 (macro F1 **0.396**); holdout Commute 0.45, Focus 0.32, DND 0.45, Sleeping 0.44, Workout 0.14 (macro F1 **0.360**). A behavior-preserving change must reproduce the relevant set exactly; an improvement should raise holdout, not just golden. Exceptions decide first in every mode and are the main cap on the embedding engine's end-to-end score.
- **Proving a refactor is behavior-preserving:** freeze the old implementation as a temporary `LegacyNotificationClassifier` in `androidTest`, run old vs new over the datasets x modes x preference states x database states (the extraction was checked on 22,176 decisions) and assert equal `ClassificationResult`s; delete the temporary files before committing. The embedding path is exercised by the replay script and `EmbedderBenchmarkTest` (a benchmark that only logs to tag `EMB_BENCH`), not by an assertion-based test.

## Docs, CI, and tooling

- `docs/` is a GitHub Pages site (Jekyll, `_config.yml`). Key pages: `internals.md` (architecture, testing, measured results), `ai_classification_and_modes.md` (pipeline flowchart, modes, exceptions, results), `database_security.md`, `permissions_reference.md`, `play_store_submission.md`, `demo.md`, `index.md`. Update them when you change behavior they describe; `testdata/README.md` documents the datasets.
- **CI state (verified 2026-10-18):** `deploy_github_pages.yml` is active but fails at `Setup Pages` because GitHub Pages is **not enabled** for the repo (Settings -> Pages -> Source: GitHub Actions); it also still lists the stale branch `feature/dark-studio-redesign`. `play_store_deploy.yml` is **disabled** in GitHub; if re-enabled it passes the invalid input `service_account_json_raw_key` to `r0adkll/upload-google-play@v1` (valid: `serviceAccountJsonPlainText`) and `versionCode` is `1`. Merges to `main` currently deploy nothing.
- `assets/` (repo root) holds README/docs screenshots; `tmp/` holds a demo video. Neither is app code.
- `tools/telegram_bridge.py` is a local-only helper that relays status/messages to Telegram. `tools/`, `.tg.env`, `tmp/`, `.artifacts/` are git-ignored. **Never commit credentials** (`tools/config.json`, bot tokens, keystores).

## Conventions and gotchas

- Everything stays on-device: no INTERNET permission, no analytics, no cloud backup. `tasks-core` (MediaPipe) drags in Google's `datatransport` library, which declares INTERNET and ACCESS_NETWORK_STATE; `AndroidManifest.xml` strips both with `tools:node="remove"` and sets `allowBackup="false"`. `PrivacyGuardsTest` fails the build if either regresses, so after adding any dependency check the merged manifest (`app/build/intermediates/merged_manifests/debug/processDebugManifest/AndroidManifest.xml`). Never log notification text or titles. Keep manifest permissions minimal (listener binding, foreground service + special use, post notifications; the Bluetooth receiver relies on ACL broadcasts).
- 16 KB page-size compatibility: keep `android:extractNativeLibs="false"` and `jniLibs.useLegacyPackaging = false`. `tasks-text:1.1.1` is 16 KB aligned on all four ABIs (checked via ELF `LOAD` alignment), but bundles an unused GenAI native lib (arm64 natives ~14 MB + 15 MB vs ~8 MB with 0.10.14); APK size has not been optimised.
- `gradle.properties` contains several `android.*` opt-out flags (e.g. `android.builtInKotlin=false`, `android.newDsl=false`) needed for this AGP/Kotlin combo; don't remove them casually.
- Several Activities are `exported="true"` without intent filters; leave as-is unless deliberately hardening.
- Prefer editing the JSON assets for prompt/exception changes rather than hard-coding in Kotlin; the Kotlin fallbacks in `NotificationClassifier` (`BUILT_IN_MODES`, `DEFAULT_CUSTOM_PROMPT`) are only defaults.
- Line endings: `.gitattributes` stores and checks out text files with LF on every platform (`*.bat` stays CRLF). Python's text-mode `open(..., "w")` on Windows writes CRLF; pass `newline="\n"` (or write bytes). If `git status` lists many files with no real diff after a bulk line-ending change, the index cache is stale (`core.fsmonitor` is on): `git diff --quiet -- <file>` confirms they are identical, and `git add` on just those files refreshes them without staging a change.

## Known issues (verified in code or on device; none fixed yet)

Classifier and rules:
- **Exceptions match the whole title+text as substrings**, so app names trigger them: `Mom | Good morning!`, `Boss | Thanks, noted`, `Google Maps | Rate your visit`, `Bank | statement` are all Shown (`exceptions.json` keywords `mom`, `boss`, `maps`, `bank`, `call`). Some are intended (they apply in every mode); decide the intent before changing.
- **Prompt tokens match as raw substrings** with no stop-word filtering: `or` and `and` match inside unrelated words ("R**and**om", "w**ai**ting" via `ai`).
- **Rule text leaks into the prompt:** `PromptManager.getEnrichedModePrompt` appends `[USER AI PREFERENCES: <rules>]`, whose tokens (`mute`, `allow`, app names, `ai`, `user`, `preferences`) become urgency keywords, so even MUTE rules widen Shown.
- **`findMatchingLearnedRule` with null/empty text** builds `LIKE '%%'`, which matches every learned row (observed on device: a notification with null title and text matched a learned rule). LIKE wildcards (`%`, `_`) in notification text are unescaped, and the title match ignores the package.
- **`AiRuleSynthesizer`:** `matchesRule` strips the app name so rules apply to every app; `activeMode` is accepted but unused (rules are not mode-scoped); digits get baked into rules; the >=50% ratio is loose; conflicting ALLOW/MUTE rules are decided by recency only.
- **Learned/synthesized MUTE rules run before the safety exceptions**, so muting one verification-code message can suppress later OTP/security alerts.
- **`Workout / Gym` has no branch** in the mode lookup, so it uses the generic default prompt (F1 0.11).
- Muted notifications that are not clearable are dropped entirely (not stored).
- **The embedding model misses most safety alerts that name no keyword** and almost all Hindi text (measured on the corpus, model alone): public emergencies 65% wrong, home alarms 58%, critical health 58%, school alerts 61%; recall on urgent Hinglish 0.36, Devanagari 0.18. See `docs/model_coverage_gaps.md` before relying on it for safety.

UI/privacy/quality:
- The engine label reads `embedding_engine_loaded`, which is only written when the service starts, so it can be stale (it defaults to false before the service has ever run).
- Doc drift: `docs/ai_classification_and_modes.md` section 1 still describes "Deep Focus" and "Gaming" modes and keyword prompts that do not match `prompts.json`; `README.md` says "two primary screens"; `SettingsActivity` has an unused `AppCompatDelegate` import.

## Embedding classifier (`EmbeddingClassifier`)

Findings to reuse, not re-derive:
- Scoring that works: per mode, best cosine similarity to a few example phrases **minus** best similarity to "routine" anchors (promos, social, newsletters). Similarity to the mode prompt alone is weak.
- Isolated model quality (`EmbedderBenchmarkTest`, thresholds tuned on golden): macro F1 0.77 / AUC 0.93 in-sample; **holdout F1 0.60 / AUC 0.89**, versus 0.35 for keywords. Tuned thresholds: Commute 0.041, Focus 0.006, DND 0.047, Sleeping 0.043, Workout 0.063. Weakest mode: Deep Work / Focus (short technical alerts); real emergencies (weather, smoke/CO) need shared anchors in every mode.
- Cost on a Pixel 9 Pro XL: load 82-125 ms; embed p50 ~38-44 ms, p95 ~83-95 ms (5.8 ms on the emulator's host CPU); ~6 MB native heap; embedding the 47 anchors takes 1.6-1.9 s (done once per process, not cached on disk). The embedder serializes concurrent calls. Accuracy is identical across devices.
- Open items: custom modes still use keywords only (no anchors for user-defined prompts); thresholds are synthetic-data tuned and the strength offsets (+-0.02) are a first guess; anchors could be cached on disk; the **model's license is unconfirmed** (`THIRD_PARTY_MODELS.md`); the exceptions (substring matching, every mode) cap the end-to-end F1.

## Dead ends already researched

- **Public MediaPipe text classifiers** (`bert_classifier`, `average_word_classifier`): sentiment labels only, no `urgent`. (The remaining 'urgent' gap was closed with embeddings instead.) Training a custom one needs MediaPipe Model Maker (deprecated, TensorFlow 2, no suitable Python/GPU setup locally: Python 3.13, no WSL distro) and a labeled dataset that does not exist; a closed-label model also cannot serve custom modes or user-edited prompts.
- **Gemini Nano via ML Kit GenAI Prompt API / AICore**: minSdk 26 and Pixel 9+ class devices, but inference is allowed only while the app is the top foreground app (`BACKGROUND_USE_BLOCKED`, including from a foreground service), plus per-app quotas and beta status, so it cannot drive live filtering from a listener service.
- **MediaPipe LLM Inference** (Gemma etc.): maintenance-only, models too large to bundle (no INTERNET permission to download), high-end devices only.

## Working environment (this machine)

- Windows 11 with **Git Bash** and PowerShell; Python 3.13; `gh` is authenticated as `gnanaswaroop`. No WSL distribution installed (RTX 3060 Ti present).
- **Temp files:** Git Bash's `/tmp` is not Windows Python's `/tmp`; convert with `cygpath -m` and pass the path explicitly to Python. Run scripts that read untrusted files with `python -I`.
- **Shell gotchas:** `cmd1 | tee | tail` hides `cmd1`'s exit code (use a subshell and echo `$?`); `adb shell` re-splits arguments, so quote for the device shell (`replay_golden.py` has `sh_quote`); `pidof` exits non-zero when the process is absent.
- **Devices:** emulator `emulator-5554` (x86_64, Google API, API 37; safe to wipe). **The user's Pixel 9 Pro XL** is on wireless debugging (serial like `adb-45301FDAS009GF-9AXCxl._adb-tls-connect._tcp`; the mDNS name can change) and holds real notification history and **4 learned rules**; its `current_mode` is `Sleep`. Never run `connectedDebugAndroidTest`, `pm clear`, or a script that deletes the database on it without a backup and explicit approval; `replay_golden.py --allow-device` backs up and restores (and clears history, which the user explicitly approved once). After a device run, check DND is back to off and no `com.android.shell` test notifications remain in the shade.
- **Restarting the listener** to pick up edited prefs: `adb shell cmd notification disallow_listener com.focusrefine.app/com.focusrefine.app.NotificationFilterService`, force-stop, edit, then `allow_listener`. Edit prefs/DB only via `run-as` on debug builds, with the app force-stopped.
- **Git/PR workflow:** **never add `Co-Authored-By` / `Claude-Session` / "Generated with Claude Code" lines to commit messages or PR descriptions** (the repo is going public; history was rewritten once to remove them); push feature branches normally (no force); merge PRs with merge commits; get explicit confirmation before merging, deleting remote branches, or anything that publishes (a push to `main` could deploy if the Play workflow is re-enabled).
