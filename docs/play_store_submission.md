# FocusRefine: Google Play submission

What to enter in the Play Console, checked against the code on 2026-10-21 (`versionName` 1.0, `versionCode` 1). Items marked **BLOCKER** must be done before the first upload is published.

## 1. Blockers before publishing

1. **Embedding model licence.** `THIRD_PARTY_MODELS.md` does not confirm the licence of the bundled text model. Confirm the terms (or swap the model) before publishing. See `CLAUDE.md`.
2. **Target API level.** `targetSdk` is 34 (`app/build.gradle.kts`). Google Play requires new apps and updates to target Android 15 (API 35) or higher. Raising it changes runtime behaviour (edge-to-edge, background limits), so it needs a full device test run first.
3. **Privacy policy URL.** Play needs a public web address for the policy. The in-app Privacy Policy screen (`LegalActivity`) does not count. The text is now in `docs/privacy.md`, published at `https://gnanaswaroop.github.io/FocusRefine/privacy/` once GitHub Pages is enabled (Settings, Pages, source: GitHub Actions). Until then, the URL does not resolve.
4. **Store graphics.** `assets/feature_graphic.png` is a 1080x2400 phone screenshot. Play needs a 1024x500 feature graphic. A 512x512 high-resolution icon is also required; only the in-app launcher icon exists.
5. **Upload.** The signed bundle is `app/build/outputs/bundle/release/app-release.aab` (about 24 MB, ARM only, signed with the upload key `1A:3F:83:22:…:08:DF`). Play App Signing must be accepted when the first release is created.

## 2. Store listing

- **App name (30 max):** FocusRefine: Distraction Free
- **Short description (80 max):** FocusRefine: Privacy first notification blocker with on device AI
- **Full description:** FocusRefine filters your notifications by the mode you are in (Commute, Deep Focus, Do Not Disturb, Sleep, Workout, or your own). Urgent notifications are shown. The rest are muted and kept in an inbox you can check. A timed session keeps filtering on for 5, 15, 30 or 45 minutes, or until you turn it off. Everything runs on your phone. The app has no internet permission, so nothing you receive is sent anywhere.
- **Category:** Productivity. **Contact:** the repository issues page (`github.com/gnanaswaroop/FocusRefine`) until a support address exists.
- **Screenshots:** the phone screenshots in `assets/` (1080x2400), taken from the release build. Retake them after UI changes.

## 3. Permissions and what they are for

| Permission | Why |
| --- | --- |
| `BIND_NOTIFICATION_LISTENER_SERVICE` | Reads incoming notifications to decide Shown or Muted, and moves muted ones out of the shade. Declare this in the Play Console's notification access declaration. |
| `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_SPECIAL_USE` | Keeps the filter running while the phone is in use. Special use is declared as "keeps the notification filter running". |
| `POST_NOTIFICATIONS` | The ongoing status notification and the timed-session notification. |
| `POST_PROMOTED_NOTIFICATIONS` | Lets a timed session show as a status bar chip on Android 16. Normal permission, no data access. |

The app declares no `INTERNET` or network state permission. `PrivacyGuardsTest` fails the build if one is added back.

## 4. Data safety form

- **Does the app collect or share user data?** No.
- **Is data processed on the device?** Yes. Notification text is read to filter it and stored only on the device, in the app's private storage. Nothing is sent off the device.
- **Is data encrypted in transit?** Not applicable: nothing is transmitted.
- **Can users request deletion?** Yes: clear the inbox, reset learned feedback in Privacy Insights, or uninstall the app. Automatic deletion is not implemented.
- **Backup:** cloud backup is off (`allowBackup="false"`).

## 5. Content and policy questionnaires

- **Target audience:** 18 and over, or general audience with no child-directed features. The app collects no personal data from anyone.
- **Ads:** none. **In-app purchases:** none.
- **Export compliance:** the app uses on-device machine learning (MediaPipe). Declare it, and that it complies with export law.
- **Content rating:** complete the IARC questionnaire. The app contains no user-generated content and no violence or sexual content.

## 6. Release checklist

- [ ] Blockers in section 1 resolved
- [ ] `./gradlew bundleRelease` succeeds, and the AAB is signed with the upload key (`keytool -printcert -jarfile …aab` shows `1A:3F:83:…`)
- [ ] Release build tested on the Pixel and the emulator (filter on, session on, extend, end, permission revoke and re-grant)
- [ ] `ChangelogTest` passes (newest changelog entry equals `versionName`)
- [ ] `PrivacyGuardsTest` passes (no INTERNET or network state in the merged manifest)
- [ ] `docs/` and the in-app Privacy Policy and Terms text still match the behaviour
