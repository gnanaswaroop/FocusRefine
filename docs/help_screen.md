# The Help screen

**Help & how it works** is an in-app screen that explains the whole app in plain language and proves its privacy claims on the
device itself. Open it from the **?** button on the home screen or from the **⋮ menu** at the top right of Settings (**Help & how it works**). It is
not exported, so other apps cannot open it. Internally it can open with just one section expanded (the `section` intent extra:
`what`, `decides`, `privacy`, `teach`, `settings`, `limits`, `data`, `troubleshoot`), which the tests use and which lets other
screens link straight to, say, the privacy section.

![The Help screen](../assets/HelpActivity.png)

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

## The Settings menu and the change log
![What's new](../assets/ChangelogActivity.png)

The **⋮ menu** in the Settings top bar has two items: **Help & how it works** and **What's new**. **What's new** opens the change
log (`ChangelogActivity`): release cards, newest first, with the version, the date, a title and the entries grouped under New,
Improved, Fixed and Privacy, plus "You have version X" and an "Installed" tag on the release you are running.

The text is data, not code: `app/src/main/assets/changelog.json`, newest release first. **Add an entry whenever a user-visible
change ships.** `ChangelogTest` fails the build if the newest entry's version differs from `versionName` in
`app/build.gradle.kts` (so a version bump without release notes cannot slip through), or if the file is malformed (an empty
section, a blank entry, a bad date, a repeated version).

## Tests
`ChangelogTest` (JVM): parsing, the bundled file is well formed, the newest entry matches the app version. `ChangelogE2ETest`
(device): the bundled change log loads and matches the installed version; the Settings menu opens What's new and Help; Back closes
the menu without opening anything. `PrivacyFactsTest` (JVM): which permissions count as network access. `HelpScreenE2ETest` (device): the facts match the installed app, the live check shows the real counts with correct plurals, sections open and close, the screen opens from Settings and from the home screen, and the buttons it tells users to press (Clear Inbox, Clear local learning data) exist.
