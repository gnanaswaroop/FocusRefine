# FocusFlow - Modes, System Prompts & Filtering Goals

This document defines the out-of-the-box modes, their system prompts, filtering goals, and how custom local model prompts operate in **FocusFlow**.

---

## 1. Out-of-the-Box Modes & System Prompts

### Commute Mode
- **System Prompt / Heuristic**: `"navigation, turn left, turn right, meeting, traffic, call"`
- **Goal / Behavior**: 
  - **Show (Urgent / Read Aloud)**: Real-time navigation prompts (Google Maps, Waze), incoming phone calls, calendar meeting reminders starting soon, and traffic hazard alerts.
  - **Archive (Muted / Suppressed)**: Social media pings, promotional offers, news updates, chat messages.
- **Why**: Keeps the driver or commuter safe and focused on transit without screen distractions.

### Focus Mode
- **System Prompt / Heuristic**: `"call, boss, emergency"`
- **Goal / Behavior**:
  - **Show (Urgent / Read Aloud)**: Direct phone calls, VIP contacts (e.g. "boss"), urgent emergency alerts.
  - **Archive (Muted / Suppressed)**: Email notifications, group chats, app updates, general reminders.
- **Why**: Eliminates interruptions during deep work or high-concentration tasks.

### Reading Mode
- **System Prompt / Heuristic**: `"emergency, hospital"`
- **Goal / Behavior**:
  - **Show (Urgent / Read Aloud)**: Life safety, hospital alerts, extreme weather emergencies.
  - **Archive (Muted / Suppressed)**: Literally all other notifications (calls, texts, app notifications).
- **Why**: Ensures absolute zero interruptions while reading or resting.

---

## 2. Custom Local Model Prompt Integration & Suppression Mechanics

Users can configure **Custom Prompts** and **Custom Modes** via the settings screen (`SettingsActivity`). 
- **Filtering Mechanism**: Incoming notifications (`StatusBarNotification`) are intercepted by `NotificationFilterService`.
- **Keyword Evaluation**: The service extracts the notification `title` and `text`, converts them to lowercase, and evaluates them against the active mode's prompt keywords.
- **Urgent vs Muted**: If a keyword matches, the notification is read aloud via TextToSpeech and saved as **Shown**. If no keyword matches, the system notification is immediately cancelled/suppressed and saved in the **Muted** inbox for later review.
