# FocusRefine — Feature Walkthrough & Demo Guide

This document provides a feature walkthrough and visual demonstration guide for **FocusRefine** across its 6 Dark Studio screens.

---

## 🎬 Screen-by-Screen Feature Walkthrough

### 1. Main Dashboard & Smart Inbox (`MainActivity.kt`)
![MainActivity](../assets/MainActivity.png)

- **Top Bar**: Displays the app logo, `FocusRefine` header, `● On Device AI` green badge, and Settings gear icon button in a single row.
- **Noise Filter Stat Banner Card**: Displays dynamic noise filtered percentage (e.g., `84% Noise Filtered`) and live counters (`42 Muted • 8 Urgent Shown Today`). Automatically resets to `0% Noise Filtered` when inbox data is cleared.
- **Context Mode Row**: Interactive chips for 🚗 `Commute`, 🎧 `Deep Focus`, 🌙 `Sleep`, and 🎮 `Gaming`. Tapping any chip updates the active mode instantly.
- **Smart Inbox Tabs & Search**: Toggle between `🔕 Muted` and `🔔 Shown` pill tabs; filter notifications in real time using the search field.
- **Native App Icon Cards**: Displays authentic native app icons (`AppIconImage`) for installed applications (Google Maps, WhatsApp, Gmail, Instagram, Slack, Phone, etc.).
- **Full-Screen Smart Inbox (`⛶`)**: Tapping the full-screen icon expands the Smart Inbox into a full-height dialog view.
- **Scrollable Footer**: The footer (*"🔒 Your notifications stay on this device."*) is embedded at the end of the list and appears only when the user scrolls to the bottom.

---

### 2. Settings Screen (`SettingsActivity.kt`)
![SettingsActivity](../assets/SettingsActivity.png)

- **Classification Engine Toggle**: Switch between `On-Device AI Engine Active` (Gemini Nano / AICore) and `On-Device Semantic Matcher Active`.
- **Filtering Strength**: Tri-state segmented control (`Gentle` | `Balanced` | `Strict`) adjusting AI score thresholds (`0.4f`–`0.8f`).
- **Urgent Notification Exceptions**: JSON-driven switches for `Navigation prompts`, `Calls & priority contacts`, and `Security & OTP verifications` loaded from `assets/exceptions.json`.
- **Unified Context Modes Card**: Select active mode, view allow/mute summaries, and manage rules in a single unified card.
- **Sample Seeding**: Tap **"✨ Add 20 Samples"** to seed 20 pre-configured notifications from `samples.json`.
- **Clear Inbox**: Tap **"🗑 Clear Inbox"** to purge inbox notifications and reset counters to `0% Noise Filtered`.

---

### 3. Notification Details (`NotificationDetailActivity.kt`)
![NotificationDetailActivity](../assets/NotificationDetailActivity.png)

- **Inspection Card**: Displays native app icon, content text, timestamp, and status badge (`Muted: Routine Alert` / `High Urgency: Priority Prompt`).
- **AI Explanation Card**: Displays why the notification was muted or shown (e.g. *"This looks like a casual conversation... Commute quiets non-essential chat"*).
- **Actions**: Direct buttons for `🔔 Show notification`, `🔕 Keep muted`, and `↗ Open app`.
- **Feedback Controls**: Record user feedback (*"Was this helpful?"* -> `Yes` | `Not quite`).

---

### 4. App Rules & Custom Modes (`ConfigureModesActivity.kt`)
![ConfigureModesActivity](../assets/ConfigureModesActivity.png)

- **App Search Bar**: Real-time search filter across installed and preset apps.
- **Expandable Rule Drawer**: Tapping WhatsApp expands an embedded rule tuner with segmented control (`Use AI` | `Allow all` | `Mute all`), `Mute casual chat` toggle, and `Allow urgent exceptions` toggle.
- **Add Custom Mode**: Tap **"➕ Add Custom Mode"** to define custom mode names and natural language prompt rules.

---

### 5. Context Modes (`ContextModesActivity.kt`)
![ContextModesActivity](../assets/ContextModesActivity.png)

- **Active Mode Banner**: Displays `Commute · Active` status.
- **Mode Cards Breakdown**: Displays Allow/Mute rule summaries for `Commute`, `Deep Focus`, `Sleep`, `Gaming`, and custom modes.
- **Add Custom Context Mode**: Create and manage custom mode definitions.
- **Manual Mode Switch**: Switch modes manually with a single tap.

---

### 6. Privacy & Insights (`PrivacyInsightsActivity.kt`)
![PrivacyInsightsActivity](../assets/PrivacyInsightsActivity.png)

- **Protection Badge**: Displays `● On-Device Protection Active`.
- **Today's Filtering Stats**: Displays `84% Noise Filtered` metrics.
- **Local Learning & Data**: Toggle local feedback learning or reset learned feedback rules.
- **Private by Design Card**: Located at the bottom of the screen, guaranteeing no notification data is ever uploaded or processed in the cloud.
