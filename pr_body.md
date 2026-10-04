# FocusFlow - Production-Ready Android Notification Filtering App

This pull request introduces **FocusFlow**, a high-performance, privacy-first Android notification filtering application that runs locally on-device using **MediaPipe On-Device AI Tasks Text Classification** and a robust semantic heuristic engine.

---

## 🌟 Key Features & Architecture

### 1. Dual-Engine On-Device Intelligence
- **MediaPipe Text Classification**: Integrates Google AI Edge `com.google.mediapipe:tasks-text` to classify incoming notification intents (`text_classifier.tflite`) entirely on-device with zero cloud dependency and complete privacy.
- **Semantic Heuristic Fallback Engine**: Instantly falls back to local tokenization and keyword scoring when model assets are absent or running on unsupported emulator environments.
- **AI Status Indicator**: Dynamically detects device/emulator AI support in Settings (`SettingsActivity`) and indicates whether Gemini Nano / MediaPipe models are active or running on the local semantic engine.

### 2. Live-Updating Background Foreground Service
- **Persistent Ongoing Notification**: Runs as an Android Foreground Service (`NotificationListenerService`) with `.setOngoing(true)` so it cannot be dismissed while active.
- **Live-Updating Counter**: Dynamically updates in real-time as notifications are suppressed (`"Suppressed: X notifications"`).
- **Quick STOP/START Action Button**: Allows users to toggle background filtering directly from the status bar without opening the app UI.

### 3. Streamlined Out-of-the-Box Modes
- Reduced OOTB modes to **Commute** (navigation, calls, traffic, VIPs) and **Do Not Disturb (DND)** (emergencies, hospital, critical alerts), plus fully customizable **Custom Modes**.

### 4. Auto-Saving Settings & Data Retention
- **Instant Auto-Save**: Settings (theme mode, AI engine toggle, retention days, mode prompts) auto-save immediately on change.
- **Automated Data Retention**: Configurable history retention in days automatically purges old notification records from the Room database.

### 5. Interactive FoxyNotes-Inspired Minimalist UI
- **Expandable Cards**: Tap any notification card in the Shown or Muted inbox to expand/collapse full text and timestamps.
- **Open App Action**: Direct button on each card to launch source apps instantly (with app-not-installed fallback).
- **Auto-Refreshing Inbox**: Real-time coroutine polling keeps Shown and Muted tabs synchronized automatically.
- **Material 3 DayNight Theme Switcher**: Full Light, Dark, and System Default theme support persisted locally and initialized via `App.kt`.

### 6. Rigorous Testing & Benchmark Suites
- **Unit Tests**: `PromptEvaluationTest`, `TextClassificationTest`, and `NotificationBenchmarkTest` (100-notification accuracy benchmark achieving ~99% accuracy).
- **Instrumented E2E Tests**: `NotificationFilterE2ETest` and `ThemeToggleTest` (`10 passed, 0 failed`).

---

## 🛡️ Android Development Best Practices Followed
1. **Lifecycle Management & Background Services**: Implemented `NotificationListenerService` as a compliant Foreground Service with proper notification channels, foreground service permissions (`FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE`), and action broadcast receivers.
2. **Local Persistence (Room DB)**: Thread-safe SQLite persistence with Coroutines and Flows on `Dispatchers.IO` to prevent main thread ANRs.
3. **Declarative & Modular Architecture**: Clean package structure under `com.focusflow.app`, view binding, Material 3 components, and robust exception handling.
4. **Offline-First Privacy**: Zero telemetry, zero cloud calls, ensuring 100% data confidentiality for sensitive notifications.
