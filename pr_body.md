# FocusFlow (formerly DoICare) - Modern UI & AI-Powered Notification Filtering

## 🚀 What's New in This Update

### 1. On-Device AI & MediaPipe Text Classification
- Integrated **MediaPipe Tasks Text Classification** (`com.google.mediapipe:tasks-text`) for on-device natural language classification of notifications.
- Added a **Device/Emulator AI Support Status Banner** in Settings (`SettingsActivity`) indicating whether Gemini Nano / MediaPipe models are active or running on the high-performance semantic heuristic engine fallback.

### 2. Live-Updating Foreground Notification with Quick STOP/START Toggle
- Implemented a persistent foreground service notification (`NotificationListenerService`) featuring a **live-updating suppression counter** (`"Suppressed: X notifications"`).
- Added an action button (`"STOP"` / `"START"`) directly inside the notification bar to control background filtering instantly without opening the app UI.

### 3. Notification History Re-evaluation & Sample Generator
- Enhanced the test button on the dashboard to **re-evaluate notification history** against active mode prompt rules when database logs exist (falling back to sample notifications when empty).

### 4. Interactive Inbox UI (Expandable Cards & "Open App")
- **Expandable Cards**: Tap any notification card in Shown or Muted tabs to expand/collapse full message details.
- **Open App Action**: Added an explicit **"Open App"** button on each notification card to instantly launch the source app.

### 5. Unified Header Bar & Icon Branding
- Updated all screens (`MainActivity` and `SettingsActivity`) with a shared header bar featuring the custom user-provided brand app icon on the left of the title and streamlined spacing.

### 6. Comprehensive Benchmark & Test Suites
- Added a **100-Notification Benchmark Test Suite** (`NotificationBenchmarkTest`) comparing the MediaPipe AI classifier and Semantic Heuristic engine (`99%` vs `94%` accuracy).
- All unit tests (`10 passed`) and instrumented E2E tests (`3 passed`) passed successfully.
