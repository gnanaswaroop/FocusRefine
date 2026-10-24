# Authoring brief for golden_v2 samples

You are writing **purely synthetic** notification samples in one language for a notification-classification dataset. Work from the repo root. Nothing here may come from a real phone or a real notification.

## What to write

One JSON object per line (UTF-8 JSONL), with exactly these fields:

```
{"split": "test" | "dev", "region": "<one allowed region>", "package": "<package id>", "usecase": "<usecase id>",
 "sender_type": "<allowed sender type>", "difficulty": "easy" | "hard_negative" | "near_miss",
 "title": "...", "text": "...", "template_family": "<short-kebab-case scenario slug>"}
```

You do **not** write the label, app name, id or category: the tool fills those in from `ml/data/rubric.json` and `ml/data/apps.tsv`. The label is decided by the **use case** you pick, so pick the use case that is truly right for what you write.

## How to pick things

1. Run `python -m ml.data.options --lang <code>` to see every use case, its label (the modes where it is urgent), the allowed sender types and the apps that fit the language's home regions. Read `ml/data/rubric.json` (`rules`, `mode_meaning`) once.
2. Choose a use case, then one of ITS listed apps (the `package` must be one of them) and one of ITS sender types.
3. Write the notification the way that app would really show it, in natural, native-sounding language (not a translation from English):
   - chat apps: `title` = a fictional sender or "Group name - sender" style, `text` = the message;
   - bank, shopping, ride, system, news apps: `title` = a short heading as that app would show, `text` = the body;
   - calls: title such as "Missed call", text with a fictional name or a masked or unknown number.
4. `region` must be one of the language's allowed regions printed by `options`.

## Difficulty (be deliberate)

- `easy`: a clear example of the use case.
- `hard_negative`: looks urgent but is routine or promotional (a storm SALE, "urgent offer", a fake emergency, a game "alert"). Its use case is the routine one, so its label is muted.
- `near_miss`: close to an urgent use case but missing the key cue or from the wrong sender (a friend's missed call, a doorbell sound, a casual "call me" from a colleague).

## Hard rules (the tool rejects violations)

- Names are fictional. Phone numbers only as `+1 555-01xx`, UK `07700 900xxx`, or X-masked such as `+91 9XXXXXXXXX`. Email addresses and links only on `example.com`, `example.org` or `.invalid` (for example `http://prize-claim.example.invalid/x`). No card or account numbers.
- No OTP, verification-code, password or security-code messages, ever.
- Do not copy real notifications, real news, or real people's details. Brand names may appear only as the app that sends the notification.
- Text length under 400 characters. Vary length: some one-liners, some 100 to 250 characters; a few with emoji; code-mixing or romanized text where it is natural for the language (see the language note in your task).
- Every item needs its own `template_family`, and no two items may share a scenario or near-identical wording.

## Coverage for a pilot file (60 items: 40 `test`, 20 `dev`)

- At least one item for each of these use cases: family_distress, call_missed_family, call_unknown, call_spam_likely, call_scam, scam_phishing, scam_prize_loan, weather_severe, earthquake_alert, community_safety_notice, group_chatter, group_forward, sound_alert_critical, sound_alert_routine, ride_arriving, navigation_prompt, work_incident, meeting_soon, workout_prompt, health_critical, sub_renewal, sub_billing_issue, rec_music, rec_video, promo_sale, travel_urgent, home_security, medical_urgent, school_urgent, delivery_update, payment_routine.
- The other items: your choice, spread across families and app categories; use different apps and regions.
- At least 35% of items must be urgent in at least one mode.
- Difficulty mix: about half `easy`, at least 12 `hard_negative`, at least 10 `near_miss`.

## Process

1. Write your items to `testdata/golden_v2/raw/<lang>.pilot.jsonl` (use the Write tool).
2. Run `python -m ml.data.assemble testdata/golden_v2/raw/<lang>.pilot.jsonl testdata/golden_v2/pilot/<lang>.jsonl --lang <lang>`.
3. Read the rejections in `testdata/golden_v2/pilot/<lang>.jsonl.rejected.jsonl`, fix those lines in your raw file, and re-run until at least 58 items are accepted (three attempts at most).
4. Touch no other files. No git commands.

## Final report (short)

Counts per split, share urgent, difficulty counts, how many rejections you fixed, and any **rubric ambiguity**: use cases where you were unsure which label was right or could not find a plausible app or sender, with the use-case id and the reason. Say honestly if you are unsure about the naturalness of your phrasing in this language.
