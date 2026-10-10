# FocusRefine — Interactive Feature Walkthrough & Demo Guide

This document provides a comprehensive feature walkthrough and visual demonstration guide for **FocusRefine** running on the Android Emulator. Because physical notification streams are absent on emulators, sample seeding and emulator interaction models are used to showcase the full feature set.

---

## 🎬 Feature Walkthrough & Annotations

### 1. Launch & Splash Screen (`SplashActivity`)
- **Action**: Cold launch the app from the home screen launcher icon (`FocusRefine`).
- **Annotation**: 
  - Displays the branded logo and version information.
  - Instantly honors the user's saved theme mode (Dark, Light, or System Default) on cold launch without any white/light flash.

### 2. Notification Access & Educational Dialog (`MainActivity`)
- **Action**: On first launch, if Notification Access is not yet granted, the red status banner appears: *"✕ Notification Access Disabled"*.
- **Annotation**:
  - Tapping **"Why is this required?"** opens an educational Material 3 dialog explaining clearly how FocusRefine intercepts and filters notifications locally on-device for 100% privacy.
  - Tapping **"Got it, Enable Now"** transitions directly to system notification listener settings.

### 3. Main Dashboard & Smart Inbox (`MainActivity`)
- **Action**: View the main dashboard with pre-seeded sample notifications (Google Maps, WhatsApp, Phone calls, Instagram).
- **Annotation**:
  - **Filtering Toggle**: Master switch to start/stop background filtering instantly (updates the persistent foreground notification in the status bar).
  - **Mode Selector**: Dropdown to switch between *Commute*, *Do Not Disturb (DND)*, and *Custom Modes*.
  - **Muted vs. Shown Tabs**: Switch between archived notifications and urgent notifications.
  - **Search Bar**: Real-time keyword search across inbox items.
  - **Card Interactions**: Tap any card to expand/collapse text; tap the refresh/sync button to reclassify between Muted and Shown; tap the external link button to launch source apps.

### 4. Settings & Customization (`SettingsActivity`)
- **Action**: Tap **"Settings"** in the top right of the dashboard.
- **Annotation**:
  - **AI Status Banner**: Indicates whether MediaPipe Gemini Nano or the local semantic heuristic engine is active.
  - **Theme Mode Switcher**: Toggle instantly between *System Default*, *Light*, and *Dark* modes.
  - **Data Retention**: Configure automated history cleanup in days.
  - **Send Sample Notifications**: Instantly seed 20 diverse test notifications from JSON to evaluate classification speed and accuracy.

### 5. Configure Modes (`ConfigureModesActivity`)
- **Action**: Tap **"Configure Modes"** in Settings.
- **Annotation**:
  - View and toggle out-of-the-box and custom modes.
  - Tap any mode card to edit its natural language rules and system prompt.
  - Add new custom modes via the bottom action button.

### 6. Learned Rules (`LearnedRulesActivity`)
- **Action**: Tap **"Learned Rules & Customizations"** in Settings.
- **Annotation**:
  - Review rules learned from user reclassifications and swipes.
  - Toggle rule status or delete individual rules, or clear all learned rules at once.
