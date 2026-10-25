# FocusRefine Local Database Storage & Security Architecture

This document details how notifications are persisted locally in FocusRefine, the security guarantees of the storage mechanism, and how Android's application sandbox isolates the data from unauthorized access by other applications.

---

## 1. Local Database Schema & Storage Mechanism

FocusRefine utilizes **Android Room** (an SQLite-based object-mapping persistence library) to store notification metadata and triage history locally on-device.

### Database File Details
- **Database Name:** `focusrefine_database`
- **Database Version:** `5`
- **Storage Path:** Private application internal storage (`/data/data/com.focusrefine.app/databases/focusrefine_database`)
- **Core Entity (`NotificationEntity`):**
  - `id`: Auto-generated primary key (`INTEGER`)
  - `title`: Notification title string (nullable)
  - `text`: Notification body content text (nullable)
  - `packageName`: Originating app package identifier (`TEXT`, e.g., `com.whatsapp`)
  - `timestamp`: Epoch millisecond timestamp of receipt (`LONG`)
  - `isUrgent`: Recommended Action flag (`true` = Shown/Urgent feed, `false` = Muted/Archived feed)
  - `isLearned`: Boolean flag indicating explicit user-defined correction or rule (`BOOLEAN`)
  - `synthesizedRule`: AI-synthesized rule tag string (`TEXT`, e.g., `"[MUTE] Whatsapp: grab coffee later"`)
  - `explanation`: Detailed classification explanation string (`TEXT`, e.g., `"🤖 [AI Pipeline] MediaPipe model score 88% exceeded threshold"`)

### Data access (`NotificationDao`)
All DAO methods are blocking (non-suspend) and must be called off the main thread. Alongside the existing inbox, search, learned-rule, and retention queries, the history re-classification feature uses:
- `getHistory()`: every row where `isLearned = 0` (the notification history, excluding the user's learned-rule rows).
- `updateClassification(id, isUrgent, explanation)`: rewrites only the decision and its explanation for one row.

No schema change was needed for these: the database stays at **version 5**. Remember that `AppDatabase` uses `fallbackToDestructiveMigration()`, so bumping the version erases history and learned rules.

---

## 2. Security & Sandbox Isolation (Preventing Inter-App Access)

FocusRefine relies on Android's foundational security model to ensure that notification data remains strictly confidential and inaccessible to other apps.

### A. Android Application Sandbox
- **Isolated UID/Process:** Every Android application runs within its own secure Linux sandbox with a unique User ID (UID).
- **Private Data Directory Permissions:** SQLite database files created in `context.getDatabasePath()` are stored within `/data/data/com.focusrefine.app/`. The Android operating system enforces strict file permissions (`rw-------` / `chmod 700`), meaning **only the FocusRefine app UID** has operating-system-level permission to read or write to these files.

### B. Inter-App Communication Restrictions
- **No Content Providers for DB:** FocusRefine does not expose a `ContentProvider` with `android:exported="true"` for its database. Therefore, external applications cannot query or query-inject into the database via IPC (Inter-Process Communication) intents or URIs.
- **Zero Internet Permissions:** FocusRefine declares **zero internet permissions** in its `AndroidManifest.xml`. Even if malicious code existed within the app, it is structurally incapable of exfiltrating local database records to remote servers.

---

## 3. Privacy & Data Lifecycle Management

1. **100% On-Device Processing:** All notification inspection, classification (via MediaPipe AI or local heuristics), and persistence occur entirely on-device. No notification text or metadata ever leaves the physical device.
2. **Data Retention:** History is kept until the user clears it (**Settings > Clear Inbox**); there is no automatic purge. (`NotificationDao.deleteOlderThan` exists but nothing calls it yet.)
3. **Manual Clearing:** Users have full control to clear all inbox items or reset learned rules instantly via the in-app settings interface.
4. **Re-running Classification:** Settings → Learning → *Re-run classification on history* re-evaluates stored notifications entirely on-device and updates only `isUrgent` and `explanation`. Learned-rule rows (`isLearned = 1`) are never modified, and no notification text is sent anywhere.

---

## 4. Development & Testing Access (Debug Builds Only)

The end-to-end replay script (`scripts/replay_golden.py`) and the instrumented tests read and wipe the app's database through `adb shell run-as com.focusrefine.app`. `run-as` only works for **debuggable** builds, so release builds keep the sandbox guarantees described above: no other app, and no adb shell, can read the database. The script backs up and restores the database and preferences around a run, and refuses to run on a non-emulator unless `--allow-device` is passed, because it clears the device's notification history. See the [Technical Implementation Guide](internals.md#3-testing--evaluation).
