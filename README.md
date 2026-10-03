# Do I Care

**Do I Care** is a modern, lightweight, privacy-focused Android application designed to intelligently filter and triage incoming notifications based on your current activity mode (Commute, Focus, Reading). 

---

## Why This Was Built

In an era of relentless notification spam (promotional alerts, social media pings, group chats), staying focused during deep work, driving, or reading is difficult. Traditional Do Not Disturb modes are often all-or-nothing and cause you to miss critical alerts. 

**Do I Care** solves this by:
1. **Analyzing content locally on-device** using context-aware heuristics and on-device models.
2. **Reading urgent notifications aloud** via Text-To-Speech (TTS) if they match your active mode criteria (e.g. navigation updates, work meetings, emergencies).
3. **Silently archiving non-urgent notifications** into a local Room database inbox so they are out of sight, yet accessible whenever you want.

---

## Key Features

- **Local-First Privacy**: No cloud transmission; all notification content and filtering happen 100% on-device.
- **START / STOP Master Control**: A dedicated master toggle to enable or pause notification filtering instantly.
- **Context Modes**: Tailor filtering behavior to your current activity:
  - **Commute**: Focuses on navigation, transit updates, and meetings.
  - **Focus**: Suppresses all notifications except calls, boss messages, or emergencies.
  - **Reading**: Ultra-strict mode allowing only critical emergencies.
- **Smart Inbox**: Archived notifications are stored locally and presented in a clean, modern UI.
- **Lightweight & Compatible**: Built with high-performance Kotlin coroutines and Room, supporting Android devices from `minSdk = 28` (Android 9.0+) upwards.
