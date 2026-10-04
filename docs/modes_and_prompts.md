# FocusFlow - Modes, System Prompts & AI Classification Architecture

This document defines the out-of-the-box modes, their system prompts, filtering goals, and how **MediaPipe Tasks Text Classification & Android On-Device AI** are integrated into **FocusFlow**.

---

## 1. Out-of-the-Box Modes & System Prompts

### Commute Mode
- **System Prompt / Heuristic**: `"navigation, turn left, turn right, meeting, traffic, call"`
- **Goal / Behavior**: 
  - **Show (Shown)**: Real-time navigation prompts (Google Maps, Waze), incoming phone calls, calendar meeting reminders starting soon, and traffic hazard alerts.
  - **Archive (Muted)**: Social media pings, promotional offers, news updates, chat messages.
- **Why**: Keeps the driver or commuter safe and focused on transit without screen distractions.

### Focus & Reading Mode (Merged)
- **System Prompt / Heuristic**: `"call, boss, emergency, hospital"`
- **Goal / Behavior**:
  - **Show (Shown)**: Direct phone calls, VIP contacts (e.g. "boss"), urgent emergency alerts, and critical hospital notifications.
  - **Archive (Muted)**: Email notifications, group chats, app updates, general reminders, and reading interruptions.
- **Why**: Eliminates interruptions during deep work, high-concentration tasks, or quiet reading sessions.

### Sleep Mode
- **System Prompt / Heuristic**: `"emergency, family, call"`
- **Goal / Behavior**:
  - **Show (Shown)**: Life safety emergencies, family phone calls, extreme weather alerts.
  - **Archive (Muted)**: All other notifications, social apps, emails, and general alerts.
- **Why**: Ensures absolute zero disturbance while resting or sleeping.

---

## 2. MediaPipe Text Classification & On-Device AI Integration

FocusFlow harnesses **MediaPipe Tasks Text Classification** and on-device natural language evaluation to perform intelligent, privacy-preserving notification filtering without sending user data to the cloud.

### A. Core Architecture
1. **MediaPipe Text Classifier**:
   - Evaluates incoming notifications (`title` and `text`) against on-device classification models (`text_classifier.tflite`).
2. **Semantic Heuristic Fallback Engine**:
   - Automatically falls back to robust semantic keyword token-matching (`split(",", " ")`) when model assets are not present or on unsupported emulator environments.
3. **Privacy & Performance**:
   - 100% local execution ensures zero cloud dependency and ultra-low latency.

---

## 3. Advanced Features & Data Management
- **Live-Updating Foreground Service Notification**: Shows real-time suppression counters (`"Suppressed: X notifications"`) with an ongoing `.setOngoing(true)` status bar banner and a direct **STOP/START** action button.
- **Data Retention Cleanup**: Automatically purges historical notifications older than a user-configured number of days (e.g. 7 days) via Settings.
- **Auto-Refreshing Inbox**: Real-time polling keeps the Shown and Muted tabs synchronized automatically.
- **Notification History Re-evaluation**: Re-analyzes existing notification history logs against newly selected mode prompts instantly.
