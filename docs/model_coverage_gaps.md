# Where the built-in model fails: coverage gaps found with the expanded corpus

The 1,209-item corpus ([`personal_model.md`](personal_model.md#33-the-corpus-testdatacorpuscorpus_notificationsjson)) was
expanded after measuring where the built-in embedding model goes wrong. These numbers are for the **embedding model alone**
(macro over the five modes, Balanced strength, no rules or exceptions; the real app adds exceptions for a few keywords such
as "emergency", "call" and "maps"). They come from the emulator and are reproducible with `CorpusEmbeddingDumpTest` and the
Python snippet in the "Reproducing" section. Labels are synthetic and written by the same author as the anchors, so read
them as a map of weak spots, not as a forecast.

## 1. Safety alerts are the biggest gap
A (notification, mode) pair counts as wrong when the model Shows what should be Muted or Mutes what should be Shown. Each
safety notification should be Shown in every mode (or in the modes listed), so a miss means a real alert is muted.

| Category | Pairs | Wrong | Missed (should be shown) | Wrongly shown |
| :--- | :--- | :--- | :--- | :--- |
| public emergency (floods, evacuations, gas leaks) | 280 | 65% | 183 | 0 |
| door security (unlock or intruder at night) | 40 | 63% | 16 | 9 |
| school alert | 130 | 61% | 60 | 19 |
| home alarm (smoke, CO, leak, intrusion) | 170 | 58% | 98 | 0 |
| critical health (low glucose, high heart rate, fall) | 120 | 58% | 48 | 21 |
| family distress | 370 | 45% | 120 | 48 |

Of all pairs that should be Shown, the model gets about half (recall 0.51 for plain English). In the first, smaller corpus
the same categories were already the worst (school 56%, home alarm 50%, critical health 48%, public emergency 40%); the
added phrasings made it clearer. Likely causes: each mode has only 6 to 7 example phrases, the shared "real emergencies in
every mode" anchors are missing, and alerts that name no familiar subject ("Gas leak reported near Sector 12") sit far
from the example phrases.

## 2. Non-English text is mostly muted
Recall on urgent notifications by surface form (higher is better):

| Form | Urgent pairs | Recall |
| :--- | :--- | :--- |
| plain English | 767 | 0.51 |
| SMS-style (typos, abbreviations) | 118 | 0.45 |
| emoji and ALL CAPS | 64 | 0.47 |
| group-chat format | 72 | 0.42 |
| Hinglish (Hindi in Latin script) | 103 | 0.36 |
| long, multi-sentence | 74 | 0.31 |
| Devanagari Hindi | 62 | 0.18 |

The encoder (Universal Sentence Encoder) is English-only, so Hindi urgency almost never reaches the threshold: urgent Hindi
family-distress, flood and train messages are muted unless an exception keyword happens to match. Error rate over all pairs
is 39.5% for Devanagari, 25% for Hinglish and long text, 20% for SMS-style, 18% for plain English, 9% for very short messages.

## 3. Other weak spots
Transit (31% wrong, mostly false alarms), road incidents (30%, mostly false alarms), rideshare (26%), and missed calls from
unknown numbers (40%, shown by mistake).

## 4. What to do about it (not done yet)
1. Add **shared "genuine emergency" anchors** to every mode in `assets/embedding_anchors.json` and re-tune the thresholds
   against the corpus (not only the golden set).
2. Add a **multilingual encoder** (or an on-device translation step) so Hindi and other languages are understood; until then
   consider a small keyword list for emergencies in major languages in `exceptions.json`.
3. Add more example phrases per mode, including indirect wording and group-chat prefixes.
4. Treat a muted-but-possibly-urgent alert more cautiously: for example, never mute a notification whose score is within a
   margin of the threshold when it matches a safety category.
The personal model does not solve these on its own: it only corrects what a user notices and flips.

## Reproducing
```
adb -s emulator-5554 shell am instrument -w -e class com.focusrefine.app.CorpusEmbeddingDumpTest com.focusrefine.app.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5554 exec-out run-as com.focusrefine.app cat files/corpus_embeddings.bin > app/build/corpus/corpus_embeddings.bin
```
Then group the margins by `category` or `variant` (the per-mode margin is `score - threshold`; Shown when it is >= 0).
