# FocusRefine — Feature Walkthrough & Demo Guide

This document provides a feature walkthrough and visual demonstration guide for **FocusRefine** across its 6 Dark Studio screens.

---

## 🎬 Screen-by-Screen Feature Walkthrough

### 1. Main Dashboard & Smart Inbox (`MainActivity.kt`)
![MainActivity](../assets/MainActivity.png)

- **Top Bar**: Displays the app logo, `FocusRefine` header, `● On Device AI` green badge, and Settings gear icon button in a single row.
- **Noise Filter Stat Banner Card**: Displays dynamic noise filtered percentage (e.g., `84% Noise Filtered`) and live counters (`42 Muted • 8 Urgent Shown Today`).
- **Context Mode Row**: Interactive chips for enabled context modes (🚗 `Commute` and 🎧 `Deep Focus` by default). **Long-pressing** any mode bubble opens [`ContextModesActivity`](file:///E:/Projects/do-i-care-app/app/src/main/java/com/focusrefine/app/ContextModesActivity.kt) directly.
- **Smart Inbox Tabs, Swipe Gesture & Animations**: Toggle between `🔕 Muted` and `🔔 Shown` pill tabs or **swipe left / right** across the notification list to switch tabs with smooth Compose spring slide animations.
- **Native App Icon Cards**: Displays authentic native app icons (`AppIconImage`) for installed applications.
- **Full-Screen Smart Inbox (`⛶`)**: Tapping the full-screen icon expands the Smart Inbox into a full-height dialog view.

---

### 2. Settings Screen (`SettingsActivity.kt`)
![SettingsActivity](../assets/SettingsActivity.png)

- **Collapsible Studio Cards**: All cards are collapsible (open/expanded by default) with clickable headers and expand/collapse chevrons (`˅` / `›`).
- **Classification Engine Toggle & Emulator Detection**: Switch between `On-Device AI Engine Active` (MediaPipe AI model) and `On-Device Semantic Matcher Active`. On emulators, the switch is safely disabled with an emulator status note.
- **Filtering Strength**: Tri-state segmented control (`Gentle` | `Balanced` | `Strict`) adjusting AI score thresholds (`0.4f`–`0.8f`).
- **Urgent Notification Exceptions**: JSON-driven switches for `Navigation prompts`, `Calls & priority contacts`, and `Security & OTP verifications` loaded from `assets/exceptions.json`.
- **Advanced Settings**: Includes an **"Inbox Swipe Animations"** toggle to enable or disable horizontal slide transitions on Smart Inbox tab switches.
- **Learned Rules Direct Access**: Dedicated navigation link to view and manage AI-synthesized learned rules in `LearnedRulesActivity`.
- **Sample Seeding & Clear Inbox**: Tap **"✨ Add 20 Samples"** to seed 20 pre-configured test notifications or **"🗑 Clear Inbox"** to purge history.

---

### 3. Notification Details (`NotificationDetailActivity.kt`)
![NotificationDetailActivity](../assets/NotificationDetailActivity.png)

- **Inspection Card**: Displays native app icon, content text, timestamp, and status badge (`Muted: Routine Communication` / `High Urgency: Priority Prompt`).
- **AI Explanation Card**: Displays why the notification was muted or shown, clearly highlighting **`🤖 [AI Pipeline]`** (MediaPipe model confidence score vs threshold) vs **`🔍 [Semantic Search]`** evaluation reasons.
- **Dynamic Preference Action Buttons**:
  - When notification is hidden: `"🔔 Show notifications like these in the future"` (Secondary: `"🔕 Keep hiding these"`).
  - When notification is shown: `"🔕 Hide notifications like these in the future"` (Secondary: `"🔔 Keep showing these"`).
- **Feedback Controls**: Record user feedback (*"Was this helpful?"* -> `Yes` | `Not quite`).

---

### 4. App Rules (`ConfigureModesActivity.kt`)
![ConfigureModesActivity](../assets/ConfigureModesActivity.png)

- **App Search Bar**: Real-time search filter across installed and preset apps.
- **Expandable Rule Drawer**: Tapping an app expands an embedded rule tuner with segmented control (`Use AI` | `Allow all` | `Mute all`), `Mute casual chat` toggle, and `Allow urgent exceptions` toggle. Purely focused on per-app rules.

---

### 5. Context Modes (`ContextModesActivity.kt`)
![ContextModesActivity](../assets/ContextModesActivity.png)

- **Active Mode Banner**: Displays `Commute · Active` status.
- **Show-on-Home Toggles**: Each context mode card features a *"Show on Home Page"* toggle controlling which mode bubbles appear on the Home screen.
- **Mode Cards Breakdown**: Displays Allow/Mute rule summaries for `Commute`, `Deep Focus`, `Sleep`, `Gaming`, and custom modes.
- **Add Custom Context Mode**: Create and manage custom mode definitions and natural language prompt rules.

---

### 6. Privacy & Insights (`PrivacyInsightsActivity.kt`)
![PrivacyInsightsActivity](../assets/PrivacyInsightsActivity.png)

- **Protection Badge**: Displays `● On-Device Protection Active`.
- **Today's Filtering Stats**: Displays `84% Noise Filtered` metrics.
- **Local Learning & Data**: Toggle local feedback learning or reset learned feedback rules.
- **Private by Design Card**: Located at the bottom of the screen, guaranteeing no notification data is ever uploaded or processed in the cloud.
