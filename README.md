# FocusFlow

**FocusFlow** is a modern, lightweight, privacy-focused Android application designed to intelligently filter and triage incoming notifications based on your current activity mode (Commute, Focus, Reading).

---

## Why This Was Built

In an era of relentless notification spam (promotional alerts, social media pings, group chats), staying focused during deep work, driving, or reading is difficult. Traditional Do Not Disturb modes are often all-or-nothing and cause you to miss critical alerts. 

**FocusFlow** solves this by:
1. **Analyzing content locally on-device** using context-aware heuristics and on-device models.
2. **Reading urgent notifications aloud** via Text-To-Speech (TTS) if they match your active mode criteria (e.g. navigation updates, work meetings, emergencies).
3. **Silently archiving non-urgent notifications** into a local Room database inbox so they are out of sight, yet accessible whenever you want.

---

## Key Functionality & UI Walkthrough

### 1. Main Dashboard & Master Control
- **START / STOP Master Switch**: Instantly toggle notification filtering on or off. When stopped, notifications pass through normally.
- **Context Modes**: Switch between **Commute**, **Focus**, and **Reading** modes to adjust filtering strictness.
- **Dynamic Notification Access**: Automatically detects notification access permissions, hiding the setup button and displaying a green checkmark once granted.
- **Archived vs. Shown Tabs**: Switch between **Archived (Muted)** notifications and **Shown (Urgent)** notifications.

<p align="center">
  <img src="assets/main_screen.png" width="300" alt="FocusFlow Main Dashboard">
</p>

---

### 2. Configuration & Custom Prompt Settings
- **System Prompts**: View and customize out-of-the-box system instructions for Commute, Focus, and Reading modes.
- **Custom Local Model Prompt**: Provide custom natural language instructions to tailor the local AI filtering model to your personal workflow.

<p align="center">
  <img src="assets/settings_screen.png" width="300" alt="FocusFlow Settings Screen">
</p>

---

## Architecture & Privacy
- **Local-First**: 100% on-device processing; no data is ever transmitted to the cloud.
- **Lightweight**: Built with Kotlin coroutines and Room database, fully optimized for older and modern Android devices (`minSdk = 28`+).
