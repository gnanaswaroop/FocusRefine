# The Help screen

**Help & how it works** is an in-app screen that explains the whole app in plain language and proves its privacy claims on the
device itself. Open it from the **?** button on the home screen or from **Settings > Help & how it works**. It is not exported,
so other apps cannot open it.

## What it covers
| Section | What it says |
| :--- | :--- |
| What it does | The app reads notifications through Android's notification access and decides Shown or Muted for the current mode; Muted ones move to the Smart Inbox and nothing is deleted |
| How it decides | The four steps in order, first match wins: your own rules, safety exceptions (calls, navigation, security codes), the on-device AI model, then your own keywords (custom modes, or AI off) |
| Offline-first and private | Everything runs on the phone; a live privacy check (below); what it never does; what stays outside its control |
| Teach it what you want | Close calls, Show it / Mute it / Keep, the "Learn from this choice" switch, where to see and delete what it learned |
| Settings that matter | Mode, filtering strength (with the warning that Strict can hide urgent alerts), exceptions, the AI switch, re-running classification |
| What it cannot do well | It can mute urgent notifications, especially unusual wording or non-English text; confident mistakes are not flagged; keep Android's own emergency alerts on |
| Your data | What is stored, where, that history is kept until you clear it, and the exact buttons that clear it |
| Not working? | Paused filtering, notification access, battery settings, the keyword-matcher label |

## The live privacy check
Instead of just claiming privacy, the screen reads the facts from the installed app each time it opens (`PrivacyFacts`):
- **Network access**: the network permissions in the installed app's merged manifest (`INTERNET`, `ACCESS_NETWORK_STATE`, `ACCESS_WIFI_STATE`, `CHANGE_NETWORK_STATE`, `CHANGE_WIFI_STATE`). It shows "None. No internet permission." or lists what is requested, in amber.
- **Cloud backup**: whether the app is allowed to be backed up (it is not).
- **AI model**: switched off, running on this phone, or not loaded yet.
- **Stored here**: how many notifications, rules you taught it and lessons learned are on the phone, counted from the local database and personal-model file.

If a future change adds a network permission or turns backup on, the screen says so, and `PrivacyGuardsTest` fails the build.

## Honest about limits
The screen states what the app cannot do well (see [`model_coverage_gaps.md`](model_coverage_gaps.md)), and only describes features that exist: there is no automatic history deletion, so it says history is kept until you clear it.

## Tests
`PrivacyFactsTest` (JVM): which permissions count as network access. `HelpScreenE2ETest` (device): the facts match the installed app, the live check shows the real counts with correct plurals, sections open and close, the screen opens from Settings and from the home screen, and the buttons it tells users to press (Clear Inbox, Clear local learning data) exist.
