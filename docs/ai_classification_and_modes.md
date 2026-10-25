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

## 4. AI Pipeline vs. Semantic Search Inputs

The decision logic lives in `NotificationClassifier` (shared by the live listener service and the history re-run). It distinguishes the **On-Device AI Pipeline** (an embedding model) from **Semantic Search / Prompt Matcher** inputs. The order below is the real precedence: the first step that matches decides.

```mermaid
graph TD
    N["Notification: title, text and active mode"] --> L{"1. User learned rule matches?"}
    L -->|yes| R1["Use the rule action: Shown or Muted"]
    L -->|no| S{"2. AI-synthesized rule matches?"}
    S -->|yes| R2["Use the rule action: Shown or Muted"]
    S -->|no| E{"3. Enabled exception keyword in the text?"}
    E -->|yes| R3["Shown"]
    E -->|no| M{"4. Embedding model loaded, enabled, built-in mode?"}
    M -->|yes| R4["AI Pipeline: Shown if score reaches the mode threshold, else Muted"]
    M -->|no| K{"5. Any token of the mode prompt in the text?"}
    K -->|yes| R5["Shown"]
    K -->|no| R6["Muted"]
    R1 --> D["Live service: save the row and cancel the notification if Muted. History re-run: update isUrgent and explanation"]
    R2 --> D
    R3 --> D
    R4 --> D
    R5 --> D
    R6 --> D
```

The first step that matches decides, so a learned rule always beats an exception, and an exception always beats the model. The model is only consulted at step 4 (and its inference is skipped entirely when an earlier step already decided). Step 5 never falls through: it always returns Shown or Muted. Step 4's per-mode thresholds are shifted by the filtering-strength setting (`Gentle` -0.02, `Balanced` 0, `Strict` +0.02).

1. **`🤖 [AI Pipeline]`**: an on-device sentence-embedding model (MediaPipe `TextEmbedder`, Universal Sentence Encoder, `assets/text_embedder.tflite`). It scores a notification for the active **built-in** mode as the best similarity to that mode's example phrases minus the best similarity to routine phrases, and compares it with the mode's threshold (`assets/embedding_anchors.json`). Custom modes have no anchors, so they use the keyword engine with your own prompt. Explanations look like:
   - Shown: `🤖 [AI Pipeline] On-device embedding model scored this 0.071 for Commute, at or above the 0.041 urgency threshold; shown.`
   - Muted: `🤖 [AI Pipeline] On-device embedding model scored this 0.012 for Commute, below the 0.041 urgency threshold; muted as routine.`
   - **Availability:** loads on emulators and phones (`tasks-text` 1.1.1). If it cannot load, or `use_ai_model` is off, the keyword engine decides.
2. **`🔍 [Semantic Search]`**: Token-based keyword matching, user exception rules, and dynamic AI-synthesized preference rules. It decides whenever the embedding model is off, failed to load, or the mode is custom.

Keep the explanation prefixes (`🤖 [AI Pipeline]`, `🔍 [Semantic Search]`) stable: they are persisted, shown in the detail screen, and referenced by tests and docs.

---

## 5. AI-Driven Dynamic Prompt Synthesis (`AiRuleSynthesizer.kt`)

When a user reclassifies a notification (e.g., clicking *"Show notifications like these in the future"* or *"Hide notifications like these in the future"*):

1. **Dynamic Token Extraction**: `AiRuleSynthesizer` extracts significant semantic tokens from title and body text without hardcoded keyword or app rules.
2. **Rule Synthesis**: Generates a dynamic, human-readable rule string (e.g., `"[MUTE] Whatsapp: grab coffee later"` or `"[ALLOW] Jenkins: build failed main"`).
3. **Dynamic Token Overlap Matching (`matchesRule`)**: Future incoming notifications are matched against synthesized rules using dynamic token overlap ratios.
4. **Opposite Classification Adaptation**: When a user reclassifies a notification that was originally classified as Muted, the synthesized rule flips subsequent similar notifications to **Shown** (and vice-versa).

---

## 6. Re-running Classification on History

Settings → **Learning & Feedback** → **Re-run classification on history** re-evaluates every stored notification (`HistoryReclassifier`). It is useful after changing the mode, filtering strength, exceptions, or learned rules.

- Uses the **current** mode for all history, not the mode that was active when each notification arrived.
- Rows may move between *Shown* and *Muted* and get a fresh explanation. Learned-rule rows are never changed.
- Only the in-app inbox changes. Muted notifications were already cancelled from the system shade when they arrived and are not restored; nothing is added to or removed from the shade.
- A confirmation dialog is shown first, a spinner while it runs, and a toast reports the result (for example `Re-classified 25: 6 now Shown, 1 now Muted`).

---

## 7. Measured Quality

A labeled dataset (`testdata/golden/`) and an adb end-to-end replay (`scripts/replay_golden.py`) measure the real service on an emulator or device. See [Technical Implementation Guide](internals.md#3-testing--evaluation) for how to run them.

**Measured with the real service** (macro average over the five OOTB modes):

| Engine | Golden F1 | Held-out F1 |
| :--- | :--- | :--- |
| Keyword only (`--engine keyword`) | 0.396 | 0.360 |
| Embedding model (default, Balanced) | 0.570 | 0.527 |

The keyword engine's main weaknesses, all measured:
- **Exceptions fire on the whole text, including app names.** "Calls & priority contacts" Shows `Mom | Good morning!` and `Boss | Thanks, noted`; "Navigation prompts" Shows `Google Maps | Rate your visit`; "Security & OTP" Shows a bank statement. Some of this is intended (these exceptions apply in every mode), but it disagrees with mode-specific expectations, and it caps the end-to-end score of the embedding engine too, because exceptions decide before the model does.
- **Substring tokens.** Prompt tokens such as `or` and `and` match inside unrelated words.
- **`Workout / Gym` has no dedicated branch** in the keyword lookup, so it falls back to a generic default prompt.
- **Paraphrases are missed**: every keyword false negative is "no keyword matched".

**Embedding model alone** (`EmbedderBenchmarkTest`, no rules or exceptions; thresholds tuned on the golden set only):

| | Golden F1 | Held-out F1 |
| :--- | :--- | :--- |
| Keyword engine (benchmark re-implementation) | 0.42 | 0.35 |
| Embedding, similarity to mode prompt only | 0.61 | 0.44 |
| Embedding, examples minus routine anchors (used) | 0.77 | 0.60 |

Cost on an Android phone: about 40 ms per notification (p95 about 90 ms), about 6 MB of native heap, a one-time anchor embedding of about 1.7 s, and a 6.1 MB model. The weakest mode is Deep Work / Focus (short technical alerts), and real emergencies need shared anchors in every mode. The dataset and anchor phrases are synthetic and written by the same author, so treat these figures as indicative. Model source and license status: [`THIRD_PARTY_MODELS.md`](../THIRD_PARTY_MODELS.md).
