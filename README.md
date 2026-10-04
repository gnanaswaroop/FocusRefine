# FocusFlow

**FocusFlow** is a modern, lightweight, privacy-first Android application designed to intelligently filter and triage incoming notifications based on your current activity mode (**Commute** or **Do Not Disturb (DND)**, plus customizable modes). By combining **MediaPipe Tasks Text Classification** with local semantic heuristics, FocusFlow ensures that urgent alerts (such as navigation, emergency calls, and VIP messages) get through while suppressing digital distractions entirely on-device.

---

## Application Overview & Screens

FocusFlow consists of two primary screens designed with a clean, FoxyNotes-inspired minimalist aesthetic, featuring a shared unified header bar with the custom brand app icon and title:

1. **Main Dashboard (`MainActivity`)**:
   - Features the master filtering toggle switch, live notification access status indicator, and mode selection dropdown (**Commute**, **DND**, and **Custom Modes**).
   - Includes a **"Send Sample Notifications / Analyze"** action button that tests notification classification, runs AI accuracy analysis reports, and re-evaluates notification history against current mode prompt rules.
   - Houses a dual-tab inbox (**Shown** vs. **Muted**) powered by compact, narrow notification cards. Each card features timestamps, expandable/collapsible text views, package identifiers, and a direct **"Open App"** action button to launch target applications instantly.

2. **Settings & Configuration Screen (`SettingsActivity`)**:
   - Displays a live **AI Support Status Banner** indicating whether MediaPipe Gemini Nano is supported or running on the high-performance local semantic heuristic engine.
   - Features an **AI Engine Toggle Switch** to switch between MediaPipe On-Device AI and semantic heuristics.
   - Provides an in-app Material 3 **Theme Mode Switcher** (System Default, Light, Dark).
   - Includes **Data Retention** configuration to automatically clear history older than X days.
   - Allows enabling/disabling OOTB modes (**Commute** and **DND**) with inline prompt editing, plus creation and management of **Custom Modes**.

---

## Key Functionality & Architecture

1. **Dual-Engine On-Device AI (`NotificationFilterService`)**:
   - Integrates **MediaPipe Tasks Text Classification** (`text_classifier.tflite`) for intelligent, offline intent classification.
   - Automatically falls back to a high-performance **Semantic Heuristic Engine** when model assets or NPU hardware are unavailable.
   - Operates persistently in the background as a compliant **Foreground Service** (`startForeground`) with an ongoing `.setOngoing(true)` status bar notification displaying a **live-updating suppression counter** (`"Suppressed: X notifications"`) and a **STOP/START** quick-control action button.

2. **Local Persistence & Data Retention (`Room Database`)**:
   - `NotificationEntity` stores notification metadata (`title`, `text`, `packageName`, `timestamp`, `isUrgent`).
   - Automated data retention cleanup purges records older than user-configured days on save.

3. **Privacy & Security Guarantee**:
   - FocusFlow declares **zero internet permissions**. All processing, AI classification, and storage occur 100% locally on-device.
