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
   - Context Mode selector chips (🚗 `Commute` and 🎧 `Deep Focus` enabled by default; long press opens `ContextModesActivity`).
   - Smart Inbox with `🔕 Muted` and `🔔 Shown` tabs, horizontal **swipe left/right gesture to switch tabs**, `🔍` search field, Full-Screen Inbox option (`⛶`), and native app icon cards (`AppIconImage`).
   - Auto-refreshing Notification Access permission warning card on return from settings.
2. **Settings & Studio (`SettingsActivity.kt`)**:
   - Collapsible studio cards (open/expanded by default).
   - On-Device AI Engine vs Keyword Matcher switch; the status banner shows the engine that actually loaded.
   - Tri-state filtering strength selector (`Gentle` | `Balanced` | `Strict` shifting the AI urgency thresholds).
   - JSON-driven urgent notification exception switches loaded from `exceptions.json`.
   - Direct navigation links to **Learned Rules** (`Learnings`).
   - **Re-run classification on history** (under *Learning & Feedback*): re-checks every stored notification with the current mode, filtering strength, exceptions and learned rules (confirmation dialog first; learned rules are never changed, and the notification shade is untouched).
3. **Notification Details (`NotificationDetailActivity.kt`)**:
   - Deep inspection card with native app icon, content body, and status tag.
   - AI explanation card (*"Why it was muted/shown"*) explicitly highlighting **`🤖 [AI Pipeline]`** vs **`🔍 [Semantic Search]`** evaluation reasons.
   - Dynamic action buttons (*"🔔 Show notifications like these in the future"* / *"🔕 Hide notifications like these in the future"*).
   - Feedback controls (*"Was this helpful?"* -> `Yes` | `Not quite`).
4. **App Rules (`ConfigureModesActivity.kt`)**:
   - Searchable app rules list (`Google Maps`, `WhatsApp`, `Gmail`, `Instagram`).
   - Expandable rule tuner drawer with segmented control (`Use AI`, `Allow all`, `Mute all`). Purely focused on per-app rules.
5. **Context Modes (`ContextModesActivity.kt`)**:
   - Active mode banner card (`Commute · Active`).
   - Detailed cards for `Commute`, `Deep Focus`, `Sleep`, `Gaming`, and custom modes with Allow/Mute rule breakdowns.
   - *"Show on Home Page"* toggles for controlling home screen selector bubbles.
   - Custom mode creation dialog (*"➕ Add Custom Context Mode"*).
6. **Privacy & Insights (`PrivacyInsightsActivity.kt`)**:
   - On-Device protection status badge.
   - Today's filtering stat card with dynamic `0% Noise Filtered` reset when inbox data is cleared.
   - "Private by design" guarantee (*No cloud notification processing*).
   - Clear local learning data reset button.

---

## 🧠 Dual-Engine On-Device Intelligence & AI Prompt Synthesis

FocusRefine employs a **Dual-Engine On-Device Intelligence Architecture** to evaluate incoming notifications locally:

### 1. Local AI Engine (Google AI Edge MediaPipe)
- **Library**: `com.google.mediapipe:tasks-text` 1.1.1 (16 KB-aligned natives for all four ABIs, so it also runs on x86_64 emulators; see the [Technical Implementation Guide](internals.md#f-16-kb-page-size-alignment-android-15-compatibility)).
- **Model**: `text_embedder.tflite` (Universal Sentence Encoder, 6.1 MB, bundled in `assets/`). Each built-in mode is scored by similarity to example phrases minus similarity to routine phrases, using `assets/embedding_anchors.json`. About 40 ms per notification on a Pixel 9 Pro XL. Source and license status: [`THIRD_PARTY_MODELS.md`](../THIRD_PARTY_MODELS.md).
- **Score Thresholds**: per-mode, shifted by Filtering Strength (`Gentle` -0.02, `Balanced` 0, `Strict` +0.02).

### 2. Semantic Search & Heuristic Matcher
- Compares message content against active user-configured modes (*Commute*, *Deep Focus*, *Sleep*, *Gaming*, *DND*, or *Custom Modes*) using natural language rules and keyword matching. This engine decides when the AI model is off, fails to load, or the mode is custom.
- Quality measurements for both engines: see [AI Classification Architecture & Modes](ai_classification_and_modes.md#7-measured-quality).

### 3. AI-Driven Prompt Synthesis (`AiRuleSynthesizer.kt`)
- Generates dynamic, token-based semantic rules (e.g. `"[MUTE] Whatsapp: grab coffee later"`) when users reclassify notifications.
- Dynamically matches future variations via token overlap ratios without hardcoded app or keyword rules.

---

## 🔒 Privacy & Offline Guarantee

FocusRefine declares **zero internet permissions** (`android.permission.INTERNET`). All notification interception, AI classification, semantic matching, and database logging occur **100% locally on-device**. No notification data is ever transmitted, cached externally, or uploaded.

---

## 📚 Table of Contents & Documentation Navigation

| Document | Description |
| :--- | :--- |
| **[Permissions Reference](permissions_reference.md)** | Detailed breakdown of required system permissions (Notification Listener, Foreground Service, Post Notifications) and least-privilege justifications. |
| **[Google Play Store Submission Guide](play_store_submission.md)** | Store listing metadata, short/full descriptions, data safety disclosures, and pre-publication compliance checklists. |
| **[AI Classification Architecture & Modes](ai_classification_and_modes.md)** | Dual-engine architecture overview detailing the real decision precedence, MediaPipe availability, filtering strength thresholds, AI prompt synthesis, JSON exceptions, history re-classification, and measured accuracy with embedding-classifier findings. |
| **[Technical Implementation Guide](internals.md)** | In-depth technical breakdown of `NotificationFilterService`, `NotificationClassifier`, `HistoryReclassifier`, 6 activity screens, Room persistence, `ExceptionManager`, `PromptManager`, 16 KB page size alignment, and the testing setup (golden datasets, adb replay, instrumented tests). |
| **[Local Database & Security Architecture](database_security.md)** | Detailed documentation on SQLite/Room local storage (version 5 schema), Android app sandbox isolation (`chmod 700`), and zero-internet privacy guarantees. |
| **[Interactive Feature Walkthrough & Demo Guide](demo.md)** | Step-by-step walkthrough covering the 6 Dark Studio screens, inbox gestures, settings, app rules, context modes, and privacy insights. |
