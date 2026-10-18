# Notification test datasets

Labeled notifications used to measure and guard classification quality. One source of
truth for every consumer:

| Consumer | How it uses the data |
| --- | --- |
| `scripts/replay_golden.py` | Posts every notification from the adb shell (`cmd notification post`) so it goes through the real `NotificationFilterService`, then reads the app's Room database back and compares Shown/Muted with the labels. |
| Future unit/E2E tests or classifier experiments | Load the same JSON; do not copy the examples elsewhere. |

## Files

- `golden/golden_notifications.json`: the regression set (96 items). Free to tune against.
- `golden/holdout_notifications.json`: 76 *unseen* items. **Never tune thresholds, prompts or anchors on this set**; use it only to check that a change generalizes. If you do tune on it, write a fresh holdout.

The two files must stay disjoint (same `title` + `text` must not appear in both).

## Schema

```json
{ "title": "Google Maps", "text": "Turn left onto Main St in 200 m", "urgentIn": ["Commute"] }
```

`urgentIn` lists the modes (names exactly as in `app/src/main/assets/prompts.json`) in which the
notification should be **Shown**. It must be Muted in every other mode. An empty list means
"Muted everywhere".

Labeling rules used so far:

- Mode prompts decide: navigation, transit and ride arrivals, boarding, urgent family/VIP calls are urgent in Commute; incidents, build/deploy failures, meetings, deadlines and manager escalations in Deep Work / Focus; and so on.
- Real emergencies (weather/evacuation alerts, smoke/CO alarms) are urgent in **every** mode.
- Family-distress messages are urgent in Commute, DND and Sleeping.
- Hard negatives are deliberate: promos, social, newsletters, rating prompts, receipts, casual chat, and near-misses like "annual checkup reminder".
- OTP/security codes are intentionally absent; they are handled by the exceptions layer, not by mode prompts.

The labels were written by hand (and by an AI assistant); they are a judgment call, not ground
truth from real traffic. Prefer adding real, anonymized notifications over inflating the synthetic ones.

## Running the adb replay

```
python scripts/replay_golden.py --serial emulator-5554                  # both sets, all modes
python scripts/replay_golden.py --serial emulator-5554 --dataset holdout --modes Commute
python scripts/replay_golden.py --serial emulator-5554 --min-f1 0.6     # exit 1 on regression
```

- Needs a **debug** build installed (uses `run-as`) and notification-listener access granted.
- **Wipes the app database and edits `current_mode` / `use_ai_model` prefs.** It refuses to run on a
  non-emulator unless you pass `--allow-device`; do not do that on a phone with real history.
- Reports are written to `app/build/golden-reports/` (git-ignored).
- Notifications are posted as `com.android.shell`, so per-app behavior (package-based rules) is not exercised.
- Calls/priority-contact and other exceptions legitimately override mode prompts, so some "misses" are expected; the report breaks results down by what decided each one (`exception`, `prompt keyword`, `learned rule`, `ai model`, `no keyword (muted)`).
