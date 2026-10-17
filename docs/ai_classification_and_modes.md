# FocusRefine — AI Classification Architecture, Modes, Exceptions & Filtering Strength

This document defines out-of-the-box filtering modes, system prompts, filtering strength thresholds, JSON-driven pre-configured exceptions, and the integration of **Google AI Edge MediaPipe Tasks Text Classification** in **FocusRefine**.

---

## 1. Out-of-the-Box Modes & System Prompts

### Commute Mode
- **System Prompt / Heuristic**: `"navigation, maps, turn left, turn right, traffic, call, vip, boss, route"`
- **Behavior**:
  - **Shown**: Real-time navigation prompts (Google Maps, Waze), incoming phone calls, calendar meeting reminders, and traffic hazard alerts.
  - **Muted**: Social media pings, promotional offers, news updates, casual chat messages.

### Deep Focus Mode
- **System Prompt / Heuristic**: `"urgent, call, boss, meeting, build, outage, deadline"`
- **Behavior**:
  - **Shown**: Incoming phone calls, VIP contacts (e.g. "boss"), manager escalations, critical build/deploy failures.
  - **Muted**: Email newsletters, social updates, casual chat messages.

### Sleep Mode
- **System Prompt / Heuristic**: `"emergency, family, hospital, call, alarm"`
- **Behavior**:
  - **Shown**: Emergency alerts, family calls, hospital updates, critical alarms.
  - **Muted**: All routine alerts, social apps, emails, non-urgent pings.

### Gaming Mode
- **System Prompt / Heuristic**: `"call, priority, emergency"`
- **Behavior**:
  - **Shown**: Priority calls and emergency alerts.
  - **Muted**: Routine emails, social chats, non-urgent alerts.

---

## 2. Filtering Strength Control (`SettingsActivity.kt`)

FocusRefine allows users to tune filtering strictness via a tri-state segmented control:

| Filtering Strength | Score Threshold | Behavior |
| :--- | :--- | :--- |
| **Gentle (`0`)** | `score >= 0.4f` | Lenient filtering. Allows lower confidence AI predictions and loose keyword matches to pass through as Shown. |
| **Balanced (`1`)** | `score >= 0.6f` | Default balanced filtering. Standard AI confidence and mode prompt matching. |
| **Strict (`2`)** | `score >= 0.8f` | High suppression. Requires high AI confidence or exact VIP/emergency rules to pass as Shown. Aggressively mutes routine pings. |

---

## 3. JSON-Driven Pre-Configured Exceptions (`assets/exceptions.json`)

Pre-configured exceptions are externalized into `assets/exceptions.json` and parsed by `ExceptionManager.kt`:

```json
[
  {
    "id": "nav_prompts",
    "title": "Navigation prompts",
    "description": "Directions while you're on the move",
    "icon": "🚀",
    "prefKey": "exception_nav_prompts",
    "defaultEnabled": true,
    "keywords": ["navigation", "maps", "turn left", "turn right", "traffic", "route", "200 feet", "merge"]
  },
  {
    "id": "calls_priority",
    "title": "Calls & priority contacts",
    "description": "Let important people reach you",
    "icon": "📞",
    "prefKey": "exception_calls",
    "defaultEnabled": true,
    "keywords": ["call", "incoming call", "missed call", "mom", "dad", "wife", "husband", "boss", "emergency"]
  },
  {
    "id": "security_otps",
    "title": "Security & OTP verifications",
    "description": "Two-factor auth codes and bank security pings",
    "icon": "🔐",
    "prefKey": "exception_security_otps",
    "defaultEnabled": true,
    "keywords": ["otp", "verification code", "security", "bank", "authentication", "login code", "passcode"]
  }
]
```

When `NotificationFilterService.onNotificationPosted()` runs, enabled exceptions are evaluated first. If notification text matches any user-enabled exception keyword, the notification is immediately allowed as Shown.

---

## 4. MediaPipe Text Classification & On-Device AI Integration

FocusRefine harnesses **Google AI Edge MediaPipe Tasks Text Classification** (`com.google.mediapipe:tasks-text`) and on-device natural language evaluation:

1. **MediaPipe Text Classifier**: Evaluates incoming notifications against on-device classification models (`text_classifier.tflite`).
2. **Semantic Heuristic Fallback Engine**: Automatically falls back to token matching when model assets are not present or on emulator environments.
3. **100% Offline Execution**: All classification and exception checking happen locally on-device with zero network traffic.
