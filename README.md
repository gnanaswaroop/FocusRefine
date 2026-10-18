# FocusRefine

**FocusRefine** is a modern, lightweight, privacy-first Android application designed to intelligently filter and triage incoming notifications based on your current activity mode (**Commute** or **Do Not Disturb (DND)**, plus customizable modes). By combining **MediaPipe Tasks Text Classification** with local semantic heuristics, FocusRefine ensures that urgent alerts (such as navigation, emergency calls, and VIP messages) get through while suppressing digital distractions entirely on-device.

---

## Application Overview & Screens

FocusRefine consists of two primary screens designed with a **dark-mode first** aesthetic, featuring a shared unified header bar with the custom brand app icon and title:

1. **Main Dashboard (`MainActivity`)**:
   - Features the master filtering toggle switch, live notification access status indicator, and mode selection dropdown (**Commute**, **DND**, and **Custom Modes**).
   - Houses a dual-tab inbox (**Shown** vs. **Muted**) powered by modern notification cards. Each card displays the corresponding **application icon**, timestamps, expandable/collapsible text views, package identifiers, and a compact **external link icon button** to launch target applications instantly (with robust fallback handling for Telegram variants like `org.telegram.messenger` and `org.telegram.plus`).

2. **Settings & Configuration Screen (`SettingsActivity`)**:
   - Displays a live **AI Support Status Banner** showing which on-device engine is really running: the embedding model or the keyword matcher.
   - Features an **AI Engine Toggle Switch** to switch between the on-device embedding model and the keyword matcher.
   - Provides an in-app **Theme Mode Switcher** (System Default, Light, Dark — **Dark-Mode First** by default).
   - Includes **Data Retention** configuration to automatically clear history older than X days.
   - Allows enabling/disabling OOTB modes (**Commute** and **DND**) with inline prompt editing, plus creation and management of **Custom Modes**.
   - Contains the **"Send Sample Notifications"** action to test AI/heuristic classification and update live statistics.
   - Offers **"Re-run classification on history"** (under *Learning & Feedback*) to re-check every stored notification with your current mode, filtering strength, exceptions and learned rules.

---

## Key Architecture & Recent Upgrades

1. **Live Update Ongoing Notification Service (`NotificationFilterService`)**:
   - Upgraded to comply with Android **Live Update / Ongoing Notification** specifications (`IMPORTANCE_DEFAULT`, `CATEGORY_SERVICE`, ongoing status).
   - Displays a live-updating suppression counter (`"Suppressed: X notifications"`) that updates instantly when notifications are filtered or when sample notifications are sent from Settings (via `ACTION_UPDATE_COUNT` broadcast).
   - Includes a quick **STOP/START** action button in the notification shade. Stopping the service explicitly dismisses the live notification and removes foreground status.

2. **Externalized OOTB Prompts (`PromptManager` & `prompts.json`)**:
   - Out-of-the-box prompts are externalized into `assets/prompts.json` and loaded via `PromptManager`, enabling prompt updates without modifying Kotlin source code.
   - *TODO*: Planned enhancement to support comma-separated free-form text prompting when AI is enabled for more precise keyword matching.

3. **Permission Minimization & Security**:
   - Minimized application permissions in `AndroidManifest.xml` to include strictly notification listening, foreground service, and post notifications.
   - **Zero internet permissions**: All classification and storage occur 100% locally on-device.

---

## Testing & Evaluation

Labeled golden and holdout notification datasets live in `testdata/golden/` (see `testdata/README.md`). `scripts/replay_golden.py` posts them from the adb shell through the real service and checks the Shown/Muted decisions in the app database, and the instrumented test `HistoryReclassifierTest` in `app/src/androidTest/` covers history re-classification. Details, results and caveats are in [`docs/internals.md`](docs/internals.md#3-testing--evaluation).
