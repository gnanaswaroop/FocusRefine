# FocusRefine — Intelligent On-Device Notification Filtering

Welcome to the official documentation and technical guide for **FocusRefine**, a high-performance, privacy-first Android application designed to intelligently filter, categorize, and suppress distraction-heavy notifications locally on-device.

---

## 📱 Supported Android Versions & Architecture

- **Minimum SDK**: **API 28 (Android 9.0 Pie)**
- **Target SDK**: **API 34 (Android 14)** / fully compatible with Android 15+ (including 16 KB memory page size support with aligned native JNI packaging).
- **Recommended Android Version**: Android 10 (API 29) or higher for optimal background service performance.

---

## 🎨 Dark Studio UI Design System & 6 Screen Flows

FocusRefine features a sleek **Dark Studio UI** (`#0F0E17` background, `#181625` / `#201D33` elevated surfaces, `#8B5CF6` electric violet accents, `#22C55E` green active badges, and `#EF4444` muted tags) across 6 primary screen flows:

1. **Main Dashboard / Smart Inbox (`MainActivity.kt`)**:
   - Single top header with app logo, `FocusRefine` title, `● On Device AI` green badge, and settings gear button.
   - Stat Summary Card (`84% Noise Filtered`, master toggle, `42 Muted • 8 Urgent`).
   - Context Mode selector chips (🚗 `Commute`, 🎧 `Deep Focus`, 🌙 `Sleep`, 🎮 `Gaming`).
   - Smart Inbox with `🔕 Muted` and `🔔 Shown` tabs, `🔍` search field, Full-Screen Inbox option (`⛶`), and native app icon cards (`AppIconImage`).
2. **Settings & Studio (`SettingsActivity.kt`)**:
   - `Gemini Nano AI Active (AICore)` banner.
   - Tri-state filtering strength selector (`Gentle` | `Balanced` | `Strict` with dynamic AI score thresholds `0.4f`–`0.8f`).
   - JSON-driven urgent notification exception switches loaded from `exceptions.json`.
   - Context mode chip switcher and navigation hub links.
3. **Notification Details (`NotificationDetailActivity.kt`)**:
   - Deep inspection card with native app icon, content body, and status tag.
   - AI explanation card (*"Why it was muted"*) with context mode badge (`🚗 Commute · Active`).
   - Direct action buttons (`Show notification`, `Keep muted`, `Open app`).
   - Feedback controls (*"Was this helpful?"* -> `Yes` | `Not quite`).
4. **App Rules (`ConfigureModesActivity.kt`)**:
   - Searchable app rules list (`Google Maps`, `WhatsApp`, `Gmail`, `Instagram`).
   - Active mode context banner.
   - Expandable WhatsApp rule tuner drawer with segmented control (`Use AI`, `Allow all`, `Mute all`).
5. **Context Modes (`ContextModesActivity.kt`)**:
   - Active mode banner card (`Commute · Active`).
   - Detailed cards for `Commute`, `Deep Focus`, `Sleep`, and `Gaming` with Allow & Mute rules breakdown.
   - Manual mode activation toggle.
6. **Privacy & Insights (`PrivacyInsightsActivity.kt`)**:
   - On-Device protection status badge.
   - Today's filtering stat card with dynamic `0% Noise Filtered` reset when inbox data is cleared.
   - "Private by design" guarantee (*No cloud notification processing*).
   - Clear local learning data reset button.

---

## 🧠 Dual-Engine On-Device Intelligence & Exception Architecture

FocusRefine employs a **Dual-Engine On-Device Intelligence Architecture** to evaluate incoming notifications locally:

### 1. Local AI Engine (Google AI Edge MediaPipe)
- **Library**: `com.google.mediapipe:tasks-text`
- **Model**: `text_classifier.tflite` bundled locally in app assets.
- **Score Thresholds**: Controlled via Filtering Strength (`Gentle` = `0.4f`, `Balanced` = `0.6f`, `Strict` = `0.8f`).

### 2. Semantic Match Heuristic Fallback Engine
- Compares message content against active user-configured modes (*Commute*, *Deep Focus*, *Sleep*, *Gaming*, *DND*, or *Custom Modes*) using natural language rules and keyword matching.

### 3. JSON-Driven Pre-Configured Exceptions (`ExceptionManager`)
- Exceptions (`Navigation prompts`, `Calls & priority contacts`, `Security & OTP verifications`) are externalized in `assets/exceptions.json`.
- Evaluated against notification content prior to general mode filtering.

---

## 🔒 Privacy & Offline Guarantee

FocusRefine declares **zero internet permissions** (`android.permission.INTERNET`). All notification interception, AI classification, semantic matching, and database logging occur **100% locally on-device**. No notification data is ever transmitted, cached externally, or uploaded.

---

## 📚 Table of Contents & Documentation Navigation

| Document | Description |
| :--- | :--- |
| **[Permissions Reference](permissions_reference.md)** | Detailed breakdown of required system permissions (Notification Listener, Foreground Service, Post Notifications) and least-privilege justifications. |
| **[Google Play Store Submission Guide](play_store_submission.md)** | Store listing metadata, short/full descriptions, data safety disclosures, and pre-publication compliance checklists. |
| **[AI Classification Architecture & Modes](ai_classification_and_modes.md)** | Dual-engine architecture overview detailing MediaPipe on-device AI classification, filtering strength thresholds, and JSON-driven exceptions. |
| **[Technical Implementation Guide](internals.md)** | In-depth technical breakdown of `NotificationFilterService`, 6 activity screens, Room persistence, `ExceptionManager`, `PromptManager`, and 16 KB page size alignment. |
| **[Local Database & Security Architecture](database_security.md)** | Detailed documentation on SQLite/Room local storage, Android app sandbox isolation (`chmod 700`), and zero-internet privacy guarantees. |
| **[Interactive Feature Walkthrough & Demo Guide](demo.md)** | Step-by-step walkthrough covering the 6 Dark Studio screens, splash flow, inbox triage, settings, app rules, context modes, and privacy insights. |
