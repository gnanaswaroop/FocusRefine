# FocusFlow - Technical Implementation Guide

This document provides a comprehensive technical breakdown of the architecture, data flows, core services, and persistence layers in **FocusFlow**.

---

## 1. Architecture & Component Interaction

```mermaid
graph TD
    A[System Notifications] -->|onNotificationPosted| B[NotificationFilterService]
    B -->|Read State & Prompts| C[SharedPreferences]
    B -->|Check Urgency| D[MediaPipe / Semantic Heuristic Engine]
    D -->|Urgent| E[Room DB: isUrgent = true]
    D -->|Non-Urgent| F[Cancel System Notification & Increment Counter]
    D -->|Non-Urgent| G[Room DB: isUrgent = false]
    H[MainActivity] -->|Auto-refresh Polling| I[RecyclerView Adapter]
    H -->|Query by Urgency| J[NotificationDao]
    K[SettingsActivity] -->|Retention Cleanup & Modes| C
```

---

## 2. Core Technical Components

### A. Background Notification Listening (`NotificationFilterService`)
- **Lifecycle**: Runs persistently in the background as a Foreground Service (`startForeground`) implementing `NotificationListenerService`.
- **Processing Flow**:
  1. Intercepts `StatusBarNotification` via `onNotificationPosted(sbn)`.
  2. Ignores self-notifications and system UI notifications.
  3. Checks `service_running` in `SharedPreferences`.
  4. Evaluates notification `title` and `text` against **MediaPipe Text Classification** and **Semantic Heuristic Prompt Rules**.
  5. **Urgent Route**: Keeps system notification visible and persists to Room DB (`isUrgent = true`).
  6. **Muted Route**: Cancels the system notification (`cancelNotification`), increments the live suppression counter, and persists to Room DB (`isUrgent = false`).
- **Live-Updating Status Bar Notification**: Provides an ongoing (`.setOngoing(true)`) foreground notification with a live suppression count and a **STOP/START** action button for quick control.

### B. Local Persistence Layer (`Room Database`)
- **`NotificationEntity`**:
  ```kotlin
  @Entity(tableName = "notifications")
  data class NotificationEntity(
      @PrimaryKey(autoGenerate = true) val id: Int = 0,
      val title: String?,
      val text: String?,
      val packageName: String,
      val timestamp: Long,
      val isUrgent: Boolean = false
  )
  ```
- **`NotificationDao`**:
  - `getByUrgency(isUrgent: Boolean): List<NotificationEntity>` powers the Muted vs. Shown tab filtering.
  - `deleteOlderThan(cutoffTimestamp: Long)` powers the automated data retention cleanup.

### C. Dynamic Mode Management & Settings (`SettingsActivity`)
- **OOTB Modes**: Commute, Focus & Reading (merged), and Sleep modes.
- **Data Retention**: Configurable history retention in days (clears older records on save).
- **Theme Switcher**: In-app DayNight Material 3 theme switching (System Default, Light, Dark).

### D. Hyper-Minimalist UI Design & Auto-Refresh
- **Inbox Auto-Refresh**: Coroutine polling loop in `onResume` automatically refreshes Shown and Muted tabs in real-time.
- **Expandable Cards & Open App**: Tap to expand/collapse notification cards and launch target apps directly.
