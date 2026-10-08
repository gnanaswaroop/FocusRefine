# FocusRefine — Intelligent On-Device Notification Filtering

Welcome to the official documentation and technical guide for **FocusRefine**, a high-performance, privacy-first Android application designed to intelligently filter, categorize, and suppress distraction-heavy notifications locally on-device.

---

## 📱 Supported Android Versions

- **Minimum SDK**: **API 28 (Android 9.0 Pie)**
- **Target SDK**: **API 34 (Android 14)** / fully compatible with Android 15+ (including 16 KB memory page size support).
- **Recommended Android Version**: Android 10 (API 29) or higher for optimal background foreground service performance.

---

## 🧠 How Notifications Are Filtered (Dual-Engine Architecture)

FocusRefine employs a sophisticated **Dual-Engine On-Device Intelligence Architecture** to evaluate incoming notifications in real-time without ever connecting to the cloud:

### 1. Local AI Engine (Google AI Edge MediaPipe)
- **Library**: `com.google.mediapipe:tasks-text`
- **Model**: `text_classifier.tflite` bundled locally in app assets.
- **How it works**: When a notification arrives via `NotificationListenerService.onNotificationPosted()`, the text classifier evaluates the notification title and body against natural language classification intents locally on-device, categorizing the intent score into urgent (Shown) or non-urgent (Muted).

### 2. Semantic Match Heuristic Fallback Engine
- **How it works**: If MediaPipe model assets are missing or the app is running on an unsupported emulator environment, FocusRefine instantly falls back to a robust local semantic tokenization and keyword scoring engine.
- **Modes & Prompts**: Compares message content against active user-configured modes (*Commute*, *Do Not Disturb*, or *Custom Modes*) using natural language rules and keyword matching.

---

## 🛠️ Key APIs & Technical Stack

- **Notification Interception**: `android.service.notification.NotificationListenerService`
- **On-Device AI Classification**: Google AI Edge MediaPipe Tasks Text (`com.google.mediapipe:tasks-text`)
- **UI Framework**: Jetpack Compose Material 3 (100% declarative UI with DayNight theme switching)
- **Local Persistence**: AndroidX Room (SQLite database `focusrefine_database`)
- **Background Processing**: Kotlin Coroutines & Flows on `Dispatchers.IO`
- **Foreground Service**: Compliant Android Foreground Service (`specialUse` foreground service type)

---

## 🔒 Privacy & Offline Guarantee

FocusRefine declares **zero internet permissions** (`android.permission.INTERNET`). All notification interception, AI classification, semantic matching, and database logging occur **100% locally on-device**. No notification data is ever transmitted, cached externally, or uploaded.

---

## 📂 Documentation Links
- [Permissions Reference](permissions_reference.md)
- [Google Play Store Submission Guide](play_store_submission.md)
- [AI Classification Architecture & Modes](ai_classification_and_modes.md)
- [Technical Implementation Guide](internals.md)
