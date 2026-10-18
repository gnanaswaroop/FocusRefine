# FocusRefine — AI Classification Architecture, Modes, Exceptions & AI Prompt Synthesis

This document defines out-of-the-box filtering modes, system prompts, filtering strength thresholds, JSON-driven pre-configured exceptions, AI prompt synthesis learning, and the integration of **Google AI Edge MediaPipe Tasks Text Classification** in **FocusRefine**.

---

## 1. Out-of-the-Box Modes & System Prompts

FocusRefine includes dynamic context modes. By default, **Commute** and **Deep Focus** are enabled on the Home screen, while other modes can be toggled on/off in Context Modes settings:

### Commute Mode (Enabled by Default)
- **System Prompt / Heuristic**: `"navigation, maps, turn left, turn right, traffic, call, vip, boss, route"`
- **Behavior**:
  - **Shown**: Real-time navigation prompts (Google Maps, Waze), incoming phone calls, calendar meeting reminders, and traffic hazard alerts.
  - **Muted**: Social media pings, promotional offers, news updates, casual chat messages.

### Deep Focus Mode (Enabled by Default)
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

---

## 4. MediaPipe AI Pipeline vs. Semantic Search Inputs

FocusRefine evaluates incoming notifications through an organized pipeline, clearly distinguishing between the **MediaPipe On-Device AI Pipeline** and **Semantic Search / Prompt Matcher** inputs:

```
Incoming Notification
         │
         ├─► 🤖 [AI Pipeline] On-Device MediaPipe TextClassifier (Primary Deep Intent Classifier)
         │
         ├─► 🔍 [Semantic Search] User Learned Rules & AI Synthesized Rules (AiRuleSynthesizer)
         │
         ├─► 🔍 [Semantic Search] JSON Exceptions (exceptions.json)
         │
         └─► 🔍 [Semantic Search] Mode System Prompts & Keyword Tokens (prompts.json)
```

1. **`🤖 [AI Pipeline]`**: On-device MediaPipe deep intent classification model (`text_classifier.tflite`). Evaluates notification text semantics and generates explicit AI explanations for both Urgent (*Shown*) and Routine (*Muted*) classifications:
   - Urgent AI Explanation: `🤖 [AI Pipeline] MediaPipe model classified notification as urgent with 88% confidence (exceeded Deep Focus threshold 60%).`
   - Routine AI Explanation: `🤖 [AI Pipeline] MediaPipe model confidence 35% was below Deep Focus urgency threshold (60%); muted as routine.`
   - On emulator environments where native JNI is unavailable, the AI pipeline automatically disables and falls back to local semantic search.
2. **`🔍 [Semantic Search]`**: Token-based keyword matching, user exception rules, and dynamic AI-synthesized preference rules.

---

## 5. AI-Driven Dynamic Prompt Synthesis (`AiRuleSynthesizer.kt`)

When a user reclassifies a notification (e.g., clicking *"Show notifications like these in the future"* or *"Hide notifications like these in the future"*):

1. **Dynamic Token Extraction**: `AiRuleSynthesizer` extracts significant semantic tokens from title and body text without hardcoded keyword or app rules.
2. **Rule Synthesis**: Generates a dynamic, human-readable rule string (e.g., `"[MUTE] Whatsapp: grab coffee later"` or `"[ALLOW] Jenkins: build failed main"`).
3. **Dynamic Token Overlap Matching (`matchesRule`)**: Future incoming notifications are matched against synthesized rules using dynamic token overlap ratios.
4. **Opposite Classification Adaptation**: When a user reclassifies a notification that was originally classified as Muted, the synthesized rule flips subsequent similar notifications to **Shown** (and vice-versa).
