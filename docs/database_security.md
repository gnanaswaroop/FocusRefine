# FocusRefine Local Database Storage & Security Architecture

This document details how notifications are persisted locally in FocusRefine, the security guarantees of the storage mechanism, and how Android's application sandbox isolates the data from unauthorized access by other applications.

---

## 1. Local Database Schema & Storage Mechanism

FocusRefine utilizes **Android Room** (an SQLite-based object-mapping persistence library) to store notification metadata and triage history locally on-device.

### Database File Details
- **Database Name:** `focusrefine_database`
- **Database Version:** `6` (version 6 adds the `confidence` column; `MIGRATION_5_6` keeps existing rows)
- **Storage Path:** Private application internal storage (`/data/data/com.focusrefine.app/databases/focusrefine_database`), encrypted (see section 1A)
- **Core Entity (`NotificationEntity`):**
  - `id`: Auto-generated primary key (`INTEGER`)
  - `title`: Notification title string (nullable)
  - `text`: Notification body content text (nullable)
  - `packageName`: Originating app package identifier (`TEXT`, e.g., `com.whatsapp`)
  - `timestamp`: Epoch millisecond timestamp of receipt (`LONG`)
  - `isUrgent`: Recommended Action flag (`true` = Shown/Urgent feed, `false` = Muted/Archived feed)
  - `isLearned`: Boolean flag indicating explicit user-defined correction or rule (`BOOLEAN`)
  - `synthesizedRule`: AI-synthesized rule tag string (`TEXT`, e.g., `"[MUTE] Whatsapp: grab coffee later"`)
  - `explanation`: Detailed classification explanation string (`TEXT`, e.g., `"🤖 [AI Pipeline] On-device embedding model scored this 4.76 for Commute, at or above the 0.50 urgency threshold; shown."`)

### Data access (`NotificationDao`)
All DAO methods are blocking (non-suspend) and must be called off the main thread. Alongside the existing inbox, search, learned-rule, and retention queries, the history re-classification feature uses:
- `getHistory()`: every row where `isLearned = 0` (the notification history, excluding the user's learned-rule rows).
- `updateClassification(id, isUrgent, explanation)`: rewrites only the decision and its explanation for one row.

No schema change was needed for these. `AppDatabase` still uses `fallbackToDestructiveMigration()` as a safety net, so a version bump without a `Migration` erases history and learned rules; add a `Migration` for every bump.

### 1A. Encryption at rest

- **Library:** SQLCipher for Android 4.19.1 (`net.zetetic:sqlcipher-android`), loaded with `System.loadLibrary("sqlcipher")` before the first open. Room opens the database through `SupportOpenHelperFactory`.
- **Key:** a 32-byte random passphrase, generated on first use and stored with `EncryptedSharedPreferences` under an Android Keystore `MasterKey`. The passphrase never appears in the app's files in plain text.
- **New installs:** the database is created encrypted from the start.
- **Existing plaintext database (upgrade from an older build):** `EncryptionMigration` writes an encrypted copy beside the original, copies every table with SQLCipher's `sqlcipher_export`, carries the schema version over, and checks that every table has the same row count. Only after those checks is the encrypted copy put in place. The plaintext original is **kept** as `focusrefine_database_plaintext_backup` in the same private folder, so no history is lost. Nothing is deleted by this step.
- **Known gap:** that plaintext backup is still unencrypted on the phone, and nothing removes it yet. It is protected only by the app sandbox. A follow-up should delete it once the migrated database has been checked on the device, or encrypt it.
- **Unreadable database** (neither plaintext nor opened by the key, for example after a Keystore reset): kept aside as `focusrefine_database_legacy`, the app starts with an empty history, and a one-time notice explains this. Migration of that history is not supported yet (see the open issue on legacy databases).
- **What is not encrypted:** the in-app preferences (`FocusRefinePrefs`) and the personal model file (`personal_model.bin`, numbers only, no message text).

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

1. **100% On-Device Processing:** All notification inspection, classification (via the on-device model or local heuristics), and persistence occur entirely on-device. No notification text or metadata ever leaves the physical device.
2. **Data Retention:** History is kept until the user clears it (**Settings > Clear Inbox**); there is no automatic purge. (`NotificationDao.deleteOlderThan` exists but nothing calls it yet.)
3. **Manual Clearing:** Users have full control to clear all inbox items or reset learned rules instantly via the in-app settings interface.
4. **Re-running Classification:** Settings → Learning → *Re-run classification on history* re-evaluates stored notifications entirely on-device and updates only `isUrgent` and `explanation`. Learned-rule rows (`isLearned = 1`) are never modified, and no notification text is sent anywhere.

---

## 4. Development & Testing Access (Debug Builds Only)

The end-to-end replay script (`scripts/replay_golden.py`) and the instrumented tests read and wipe the app's database through `adb shell run-as com.focusrefine.app`. `run-as` only works for **debuggable** builds, so release builds keep the sandbox guarantees described above: no other app, and no adb shell, can read the database. The script backs up and restores the database and preferences around a run, and refuses to run on a non-emulator unless `--allow-device` is passed, because it clears the device's notification history. See the [Technical Implementation Guide](internals.md#3-testing--evaluation).
