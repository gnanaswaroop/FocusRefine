# FocusFlow - Modes, System Prompts & Android AI API Integration

This document defines the out-of-the-box modes, their system prompts, filtering goals, and how **Android On-Device AI APIs (ML Kit GenAI Prompt API & Gemini Nano)** are integrated into **FocusFlow**.

---

## 1. Out-of-the-Box Modes & System Prompts

### Commute Mode
- **System Prompt / Heuristic**: `"Focus on navigation, turn left/right, meetings, traffic, and calls."`
- **Goal / Behavior**: 
  - **Show (Urgent / Read Aloud)**: Real-time navigation prompts (Google Maps, Waze), incoming phone calls, calendar meeting reminders starting soon, and traffic hazard alerts.
  - **Archive (Muted / Suppressed)**: Social media pings, promotional offers, news updates, chat messages.
- **Why**: Keeps the driver or commuter safe and focused on transit without screen distractions.

### Focus Mode
- **System Prompt / Heuristic**: `"Suppress almost everything except explicit calls, boss, or emergencies."`
- **Goal / Behavior**:
  - **Show (Urgent / Read Aloud)**: Direct phone calls, VIP contacts (e.g. "boss"), urgent emergency alerts.
  - **Archive (Muted / Suppressed)**: Email notifications, group chats, app updates, general reminders.
- **Why**: Eliminates interruptions during deep work or high-concentration tasks.

### Reading Mode
- **System Prompt / Heuristic**: `"Extremely strict, only allow emergencies or hospital alerts."`
- **Goal / Behavior**:
  - **Show (Urgent / Read Aloud)**: Life safety, hospital alerts, extreme weather emergencies.
  - **Archive (Muted / Suppressed)**: Literally all other notifications (calls, texts, app notifications).
- **Why**: Ensures absolute zero interruptions while reading or resting.

---

## 2. Android On-Device AI API Integration (ML Kit GenAI Prompt API & Gemini Nano)

FocusFlow is architected to harness cutting-edge **Android On-Device AI APIs** to perform intelligent, privacy-preserving natural language classification of notifications without sending user data to the cloud.

### A. Core AI Architecture
1. **Model Execution (Gemini Nano / AICore)**:
   - On supported devices (Android 14+ with AICore and Gemini Nano), FocusFlow utilizes the **ML Kit GenAI Prompt API** to send natural language evaluation requests directly to the on-device foundation model.
2. **Prompt Construction**:
   - The user's active system prompt (e.g., Commute Mode instructions) is combined with the incoming notification payload (`title` and `text`) into a structured prompt:
     ```text
     [System Instruction]: {Active Mode Prompt}
     [Incoming Notification]: Title: {title}, Body: {text}
     [Task]: Is this notification urgent according to the system instruction? Answer strictly TRUE or FALSE.
     ```
3. **Structured Output**:
   - The on-device LLM processes the input locally and returns a classification result (`TRUE` for urgent/shown, `FALSE` for suppressed/muted).
4. **Privacy & Performance**:
   - Because execution happens 100% on-device via AICore, notification contents (which may contain sensitive personal data, messages, or emails) never leave the device, ensuring zero cloud dependency and ultra-low latency.

### B. Graceful Heuristic & Keyword Fallback Engine
- For devices running earlier Android versions (e.g., Android 9.0 Pie / minSdk 28 upwards), or when on-device LLM models are downloading/initializing, FocusFlow automatically falls back to its robust **Keyword Token-Matching Engine** (`NotificationFilterService#analyzeContent`):
  - Tokenizes prompt strings into keyword lists (`split(",", " ")`).
  - Evaluates whether notification `title` or `text` contains relevant keywords.
  - Routes notifications to either **Urgent (TTS Read Aloud + Shown Inbox)** or **Muted (System Suppression + Muted Inbox)**.
