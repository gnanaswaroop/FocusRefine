# Timed mode sessions

A session keeps one context mode on for a set time ("Deep Focus for 45 minutes"), or until you turn it off, and then puts the previous mode back.

## Using it

| Pick a length | Running on Home | Live notification |
| --- | --- | --- |
| ![Session length picker](assets/session_picker.png) | ![Session card on Home](assets/session_active.png) | ![Live notification in the shade](assets/session_notification.png) |

- On Home, turn the filtering switch **on**. FocusRefine asks **how long it should work** and you pick **5, 15, 30 or 45 minutes**, or **Until I turn it off** (Cancel leaves it off). The session uses the mode that is selected, and when it ends filtering pauses again.
- While it runs, a live countdown (m:ss, or h:mm:ss from an hour) shows under the switch in the top card; an open-ended session says "until you turn it off". Turn the switch off, or tap **End session** in the notification, to stop early (that also pauses filtering).
- **One live notification, not two.** The filtering service's own ongoing notification (the one that always says how many were muted) takes over while a session runs: title "Deep Focus session started" for the first minute, then "Deep Focus · 32 min left" (or "Deep Focus · until you turn it off"), a progress bar, a system countdown (a count-up for an open-ended session), "N muted so far" plus any close calls waiting, and buttons: **End session**, and **+15 min** for a timed session (an open-ended one has no end to extend). Tapping the notification opens FocusRefine. When the session ends it goes back to its normal look. Only if the service is not running is a separate session notification (id 1002) posted. On Android 16 it asks to be promoted to a Live Update (status bar chip) through `android.requestPromotedOngoing` and the `POST_PROMOTED_NOTIFICATIONS` permission; earlier versions show a normal ongoing notification.
- When time is up, or when you press End session, the previous mode returns. A finished session also posts one "session complete" note with the muted count.

## How it works

`ModeSession` keeps the state in the preferences (`session_mode`, `session_start`, `session_end`, `session_previous_mode`, `session_base_count`), so it survives the app process being killed. A once-a-minute inexact alarm (`ModeSessionReceiver`) refreshes the notification and finishes a timed session on time (`ModeSession.applyTo` gives the service's notification the session's look; the service rebuilds it when it receives `ACTION_UPDATE_COUNT`); no exact-alarm permission is needed, and a minute of slack is fine for a countdown. The listener service also refreshes the notification when it mutes something, so the count stays current. Starting a second session keeps the original mode to go back to. Nothing uses the network.

## Tests

`ModeSessionTest` (JVM: progress, minutes left, phase, open-ended, the offered lengths), `ModeSessionE2ETest` (device: start, active, end, complete, second session, one live notification, open-ended, the picker, and the Home buttons).

## Not done

A mockup-style Android 16 `ProgressStyle` with segments and points needs compileSdk 36; the app is on 34, so it uses the standard progress bar and chronometer.
