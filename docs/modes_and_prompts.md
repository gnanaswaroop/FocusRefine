# FocusFlow - Modes, System Prompts & Filtering Goals

This document defines the out-of-the-box modes, their system prompts, filtering goals, and how custom local model prompts operate in **FocusFlow**.

---

## 1. Out-of-the-Box Modes & System Prompts

### Commute Mode
- **System Prompt / Heuristic**: `"Focus on navigation, turn left/right, meetings, traffic, and calls."`
- **Goal / Behavior**: 
  - **Show (Urgent / Read Aloud)**: Real-time navigation prompts (Google Maps, Waze), incoming phone calls, calendar meeting reminders starting soon, and traffic hazard alerts.
  - **Archive (Muted / Filtered)**: Social media pings, promotional offers, news updates, chat messages.
- **Why**: Keeps the driver or commuter safe and focused on transit without screen distractions.

### Focus Mode
- **System Prompt / Heuristic**: `"Suppress almost everything except explicit calls, boss, or emergencies."`
- **Goal / Behavior**:
  - **Show (Urgent / Read Aloud)**: Direct phone calls, VIP contacts (e.g. "boss"), urgent emergency alerts.
  - **Archive (Muted / Filtered)**: Email notifications, group chats, app updates, general reminders.
- **Why**: Eliminates interruptions during deep work or high-concentration tasks.

### Reading Mode
- **System Prompt / Heuristic**: `"Extremely strict, only allow emergencies or hospital alerts."`
- **Goal / Behavior**:
  - **Show (Urgent / Read Aloud)**: Life safety, hospital alerts, extreme weather emergencies.
  - **Archive (Muted / Filtered)**: Literally all other notifications (calls, texts, app notifications).
- **Why**: Ensures absolute zero interruptions while reading or resting.

---

## 2. Custom Local Model Prompt Integration

Users can configure a **Custom Local Model Prompt** via the settings screen (`SettingsActivity`). 
- When running on-device (via ML Kit GenAI Prompt API / Gemini Nano on Android 14+ or heuristic fallbacks on Android 9+), FocusFlow appends the custom prompt instructions to evaluate incoming `StatusBarNotification` extras (`title` and `text`).
- This allows users to fine-tune classification criteria on the fly without leaking private notification data to the cloud.
