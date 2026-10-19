# FocusRefine Performance Findings

Measurements for the on-device embedding classifier (`EmbeddingClassifier`) and the keyword engine it complements. All figures come from this repository's own tests; how to reproduce each is listed at the end. Labels and anchor phrases are synthetic and written by the same author, so accuracy numbers are indicative, not a forecast of real traffic.

**Devices:** a Pixel 9 Pro XL (arm64, real hardware) and the Android emulator `emulator-5554` (x86_64, API 37, running on a desktop CPU, so its speed is not representative of phones).

---

## 1. Summary

| Question | Result |
| :--- | :--- |
| Per-notification cost on a phone | About 40 ms (p95 about 90 ms) |
| One-time cost per process | Model load 82-125 ms, anchor embedding 1.6-1.9 s (background) |
| Memory | About 6 MB native heap for the model |
| Accuracy of the model alone (holdout) | F1 0.60 / AUC 0.89, versus 0.35 for keywords |
| Accuracy end to end (holdout) | F1 0.527, versus 0.360 for keywords |
| Same accuracy on emulator and phone? | Yes (the model is deterministic; measured identical) |
| Thread safety | No errors or wrong results under 4 threads, but no speed-up |
| Cost to app size | Native libraries grow; APK size not yet optimised |

---

## 2. Latency (Pixel 9 Pro XL, `EmbedderBenchmarkTest`)

| Measure | Value |
| :--- | :--- |
| Model load | 82-125 ms |
| Embed one notification, p50 | about 38-44 ms |
| Embed one notification, p95 | about 83-95 ms |
| Scoring against all five modes on top of the embed | about 0.7 ms (emulator: 6.60 ms mean decision vs 5.95 ms embed alone) |
| Embed the 47 anchor phrases (once) | 1.6-1.9 s |
| Emulator host CPU, for reference | load 190 ms, embed p50 5.9 ms / p95 6.6 ms, 47 anchors 280 ms |

What this means in the app:
- **Per notification** the model adds one embedding, about 40 ms, on the `Dispatchers.IO` path of the listener service. This is small next to the time Android takes to deliver the notification and is not user-visible.
- **First notification after process start:** the service starts a background warm-up thread (`EmbeddingClassifier.warmUp`) in `onCreate`, so the 1.7 s anchor cost normally finishes before the first notification arrives. If a notification arrives sooner, it waits for the warm-up (the lazy anchor initialisation is synchronised).
- **Anchors are not cached on disk.** Every process start pays the 1.7 s again. Caching the vectors (keyed on model and `embedding_anchors.json` contents) would remove it.
- **History re-run:** `HistoryReclassifier` loads its own embedder and pays model load plus anchors once, then about 40 ms per stored notification on a phone. For 500 rows expect roughly 20 s on a Pixel (estimated from the per-item figure, not measured).

## 2b. A crash found only on a real phone (and its fix)
Testing the new build on a Pixel 9 Pro XL (Android 17) found a native crash that never happens on the emulator: a
`SIGBUS` (memory fault) on one of MediaPipe's own worker threads (`drishti/...`), inside `libmediapipe_tasks_jni.so`,
while reading the model's weights. It was intermittent at first (2 of 3 runs right after a fresh install, then
reproducible), and it hit the model tests, the personal-learning tests and the benchmark.

| Experiment on the Pixel | Result |
| :--- | :--- |
| Load the model with `setModelAssetPath` (MediaPipe reads the weights straight out of the APK), 600 embeddings per run | crashed in **6 of 6** runs |
| Load the same model into ordinary memory (`setModelAssetBuffer`), same 600 embeddings | **0 of 6** crashed |
| Create and close a model 40 times in various patterns, with a warm-up thread racing a close | no crash (so create/close churn was not the cause) |

The crashing reads are file-backed pages of the installed APK (the kernel's fs-verity worker threads were active in the
log). The fix, in `EmbeddingClassifier.loadModelBuffer`: copy the model from the assets into a **page-aligned direct
buffer** and pass it with `setModelAssetBuffer`, keeping a reference to the buffer for as long as the model lives.
Two traps found on the way: an ordinary (unaligned) allocation made inference fail on x86_64 (`interpreter_->Invoke()`
failed, and the app silently fell back to the keyword engine), and a buffer that is not referenced can be garbage collected
while native code still reads it. Cost: the model's roughly 6 MB now sits in memory instead of being mapped from the APK.

A second, related change: the app used to create and close a MediaPipe model for every correction and every history
re-run (about 2 s each, mostly embedding the anchors). It now uses **one process-wide model**
(`EmbeddingClassifier.shared`) that the service, corrections and history re-runs share and **nothing ever closes**.
Releasing it when the AI engine was switched off was tried and removed: the service holds the same instance, and every
later decision failed with "closed". After both changes: 12 of 12 test-class runs on the Pixel passed with no native crash
(model tests, personal-learning tests and the benchmark, three rounds), and all 66 device tests pass on the emulator.

Also verified on the Pixel: embeddings from the phone and the emulator differ by at most 1.2e-6 and **none of the 6,905
notification-and-mode decisions differ**, so model accuracy is identical across devices (model alone: macro F1 0.774 on
the golden set, 0.599 on the holdout set, as on the emulator). Latency on the phone while it was also in use: embed p50
46 to 48 ms, p95 80 to 98 ms, the 47 anchors 2.0 to 3.2 s (earlier idle-phone runs: 38 to 44 ms, 83 to 95 ms, 1.6 to 1.9 s).
Four threads sharing one model still produced no errors or wrong results.

## 3. Memory

About **6 MB** of native heap allocated after loading the model (`Debug.getNativeHeapAllocatedSize` delta). Anchor embeddings are 47 float vectors plus the routine set, a few hundred KB at most. The classifier is created once per service lifetime and closed in `onDestroy`.

## 4. Concurrency

Four threads sharing one embedder, 100 embeddings each: **0 errors, 0 mismatches**, but no speed-up, because MediaPipe serialises calls. `EmbeddingClassifier` also holds a lock around every embed and around `close()`, so closing the service while the warm-up thread is running cannot free the model mid-call. Notifications that arrive together are therefore processed one embedding at a time (about 40 ms each on a phone).

## 5. App size

- `tasks-text:1.1.1` is needed for 16 KB-aligned natives and an `x86_64` build, but it also ships `libmediapipe_tasks_textgenai_jni.so`, which this app does not use.
- Native libraries per ABI in the built debug APK:

| ABI | `libmediapipe_tasks_jni.so` | `libmediapipe_tasks_textgenai_jni.so` (unused) |
| :--- | :--- | :--- |
| arm64-v8a | 14.4 MB | 15.5 MB |
| armeabi-v7a | 10.1 MB | 10.5 MB |
| x86 | 20.7 MB | 21.9 MB |
| x86_64 | 17.8 MB | 19.3 MB |

- The model asset adds **6.1 MB**.
- The debug APK, which contains all four ABIs uncompressed, is about **183 MB**. A Play App Bundle serves one ABI per device, so the per-device cost is far lower, but **release APK/AAB size has not been measured**. Removing the unused GenAI library (for example with a per-ABI split or an exclusion rule that is checked not to break loading) is the obvious first optimisation.
- With `0.10.14` the arm64 natives were about 8 MB; the model path costs roughly 20 MB more per arm64 device before any trimming.

## 6. Accuracy

### 6.1 The model alone (`EmbedderBenchmarkTest`)
Macro average over the five built-in modes, thresholds tuned on the golden set only, then applied unchanged to the holdout set.

| Method | Golden F1 / AUC | Holdout F1 / AUC |
| :--- | :--- | :--- |
| Keyword engine (re-implemented in the benchmark) | 0.42 / n.a. | 0.35 / n.a. |
| Similarity to mode prompt only | 0.61 / 0.82 | 0.44 / 0.79 |
| Examples minus routine anchors (**used**) | 0.77 / 0.93 | 0.60 / 0.89 |

Per mode, golden set, tuned threshold: Commute 0.84 (0.041), Deep Work / Focus 0.50 (0.006), DND 0.83 (0.047), Sleeping / Rest 0.93 (0.043), Workout / Gym 0.76 (0.063). Deep Work / Focus is weakest: its urgent items are short technical alerts that are close to routine tool chatter. Real emergencies such as weather and smoke alarms need shared anchors in every mode.

### 6.2 The real app, end to end (`scripts/replay_golden.py`, emulator)
Every notification is posted from the adb shell, classified by the real service (rules, exceptions, model, keywords) and read back from the database.

| Engine | Golden F1 | Golden precision / recall | Holdout F1 | Holdout precision / recall |
| :--- | :--- | :--- | :--- | :--- |
| Keyword only (`--engine keyword`) | 0.396 | 0.292 / 0.617 | 0.360 | 0.288 / 0.489 |
| Embedding model (default, Balanced) | 0.570 | 0.443 / 0.855 | 0.527 | 0.439 / 0.708 |

Per mode F1, embedding engine: golden Commute 0.81, Focus 0.45, DND 0.57, Sleeping 0.65, Workout 0.37; holdout 0.74 / 0.47 / 0.53 / 0.62 / 0.27. Measured at filtering strength Balanced (the replay script pins it).

### 6.3 Why end to end is lower than the model alone
- **Exceptions decide first and apply in every mode.** 21 of 96 golden and 15 of 76 holdout items were decided by an exception, not the model. Calls, navigation and "bank" or "maps" substrings are Shown in all modes, which creates false positives, especially for Workout / Gym.
- **Strength offsets (+-0.02)** are a first guess and were not calibrated.
- **Custom modes** have no anchors and use keywords only.

Quick wins that do not touch the model: match exceptions on the title only or on word boundaries, and make exceptions mode-aware. Both change user-visible behaviour, so they need a decision first.

### 6.4 Overfitting check
Thresholds were chosen on the golden set. The holdout set (disjoint, never tuned on) is the honest figure: the isolated model dropped from 0.77 to 0.60 F1, and the keyword engine stayed at 0.35. The model remains well ahead, but expect real-world numbers to land nearer the holdout figures or lower.

## 7. Emulator versus phone
- **Speed:** the emulator uses the desktop CPU (about 6 ms per embedding), so it understates phone latency by roughly 7x. Do not use emulator timings for budgeting.
- **Accuracy:** identical on both. The keyword baseline was also identical across the two devices.
- **Loading:** with `tasks-text` 0.10.14 only the phone could load MediaPipe (no `x86_64` native). With 1.1.1 both can. The full end-to-end replay of the embedding engine was run on the emulator only. On the Pixel it was checked with the **release APK** (installed over the existing app, data kept): seven notifications posted in Sleeping / Rest mode at Balanced strength were Shown for Dexcom (low glucose), Apple Watch (high heart rate) and Hospital (exception), and removed from the shade for Greenwood School, Amazon, TikTok and Spotify, which is the same split the emulator produced (scores 0.047 and 0.095 against a 0.043 threshold for the first two). A release build is not debuggable, so the database and explanations could not be read there; the Shown/removed outcome was read from the system notification list.

## 8. Test harness findings that affect measurements
- Android silently drops posts beyond **50 active notifications per package**, which looked like classification misses at first. The replay script reuses 24 tags and force-stops `com.android.shell` between modes to clear them.
- The listener must be rebound after each reset; notifications posted before `onListenerConnected` are never delivered. The script posts a probe and waits until it is recorded.
- `connectedDebugAndroidTest` uninstalls the app and wipes history; use `am instrument` on devices that matter.

## 9. Reproducing
```
./gradlew assembleDebug assembleDebugAndroidTest
adb -s <serial> install -r app/build/outputs/apk/debug/app-debug.apk
adb -s <serial> install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk

# model in isolation: latency, memory, concurrency, accuracy (results in logcat tag EMB_BENCH)
adb -s <serial> shell am instrument -w -e class com.focusrefine.app.EmbedderBenchmarkTest com.focusrefine.app.test/androidx.test.runner.AndroidJUnitRunner
adb -s <serial> logcat -d -s EMB_BENCH

# real app, end to end
python scripts/replay_golden.py --serial emulator-5554                    # embedding model
python scripts/replay_golden.py --serial emulator-5554 --engine keyword   # baseline
```
Native library sizes: `unzip -l app/build/outputs/apk/debug/app-debug.apk | grep "\.so$"`.

## 10. Not measured yet
- Release AAB size and per-ABI download size. The universal release APK (all four ABIs, minification off) is about 165 MB; the debug APK is about 183 MB.
- Battery impact over a day of real notifications.
- Full golden/holdout replay on a physical device (it needs a debuggable build and clears that device's notification history). Only the 7-notification release probe above was run there.
- Behaviour on low-memory or older (API 28) devices.
- Anchor-vector caching.
