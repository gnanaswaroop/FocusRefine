# FocusRefine Local Database Storage & Security Architecture

This document details how notifications are persisted locally in FocusRefine, the security guarantees of the storage mechanism, and how Android's application sandbox isolates the data from unauthorized access by other applications.

---

## 1. Local Database Schema & Storage Mechanism

FocusRefine utilizes **Android Room** (an SQLite-based object-mapping persistence library) to store notification metadata and triage history locally on-device.

### Database File Details
- **Database Name:** `focusrefine_database`
- **Storage Path:** Private application internal storage (`/data/data/com.focusrefine.app/databases/focusrefine_database`)
- **Core Entity (`NotificationEntity`):**
  - `id`: Auto-generated primary key (`INTEGER`)
  - `title`: Notification title string (nullable)
  - `text`: Notification body content text (nullable)
  - `packageName`: Originating app package identifier (`TEXT`, e.g., `com.whatsapp`)
  - `timestamp`: Epoch millisecond timestamp of receipt (`LONG`)
  - `isUrgent`: Boolean flag determining classification (`true` = Shown/Urgent feed, `false` = Muted/Archived feed)
  - `isLearned`: Boolean flag indicating explicit user-defined correction or rule (`BOOLEAN`)

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

1. **100% On-Device Processing:** All notification inspection, classification (via MediaPipe or local heuristics), and persistence occur entirely on-device. No notification text or metadata ever leaves the physical device.
2. **Data Retention Controls:** Users can configure automatic data purging in `SettingsActivity` to delete database records older than a specified threshold (e.g., 1, 3, or 7 days), limiting data retention.
3. **Manual Clearing:** Users have full control to clear all inbox items or reset learned rules instantly via the in-app settings interface.
