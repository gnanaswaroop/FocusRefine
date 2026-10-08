# FocusRefine - Android Permissions Reference & Code Usage Analysis

This document provides a comprehensive technical breakdown of all Android permissions declared in [AndroidManifest.xml](file:///E:/Projects/do-i-care-app/app/src/main/AndroidManifest.xml), detailing why each permission is required, which classes use them, and how they are implemented in code.

---

## 📋 Declared Permissions Summary

| Permission | Target API | Purpose |
| :--- | :--- | :--- |
| `android.permission.BIND_NOTIFICATION_LISTENER_SERVICE` | All APIs | System listener binding to intercept and filter incoming notifications in real-time. |
| `android.permission.FOREGROUND_SERVICE` | API 28+ | Keeps the background filtering service running reliably without OS termination. |
| `android.permission.FOREGROUND_SERVICE_SPECIAL_USE` | API 34+ | Compliant foreground service declaration for continuous real-time notification filtering utility. |
| `android.permission.POST_NOTIFICATIONS` | API 33+ | Displays the persistent ongoing live service notification with suppression counts and quick toggle. |

---

## 🔍 Detailed Permission & Code Usage Analysis

### 1. `BIND_NOTIFICATION_LISTENER_SERVICE`
- **Why Required**: Android restricts access to incoming device notifications for privacy and security. To listen to, evaluate, and suppress notifications, an application must request the system permission `BIND_NOTIFICATION_LISTENER_SERVICE` and be explicitly enabled by the user in system notification listener settings (`Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`).
- **Class / Component**: [NotificationFilterService.kt](file:///E:/Projects/do-i-care-app/app/src/main/java/com/focusflow/app/NotificationFilterService.kt) extending `NotificationListenerService`.
- **Code Usage**:
  ```kotlin
  class NotificationFilterService : NotificationListenerService() {
      override fun onNotificationPosted(sbn: StatusBarNotification) {
          // Intercepts incoming notifications in real-time
          val packageName = sbn.packageName
          val title = sbn.notification.extras.getString(Notification.EXTRA_TITLE)
          val text = sbn.notification.extras.getString(Notification.EXTRA_TEXT)
          // Evaluate against AI / heuristics and cancel if non-urgent
      }
  }
  ```

---

### 2. `FOREGROUND_SERVICE` & `FOREGROUND_SERVICE_SPECIAL_USE`
- **Why Required**: Modern Android versions (API 28+ / API 34+) aggressively restrict background execution. Without foreground service permissions and the `specialUse` type, background listeners running continuous tasks are prone to being killed under memory pressure.

---

### 3. `POST_NOTIFICATIONS`
- **Why Required**: Introduced in Android 13 (API 33+), runtime permission is required to post notifications (used for the ongoing foreground status bar notification).

---

## 📱 Understanding the System Notification Access Prompt

When enabling Notification Access in Android Settings (`Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS`), the system prompt displays three bundled capabilities:
1. **"Read your notifications"**: **Used by FocusRefine.** Required to read incoming notification extras (`title`, `text`, `packageName`) to evaluate them against AI and heuristic filtering rules.
2. **"Reply to messages"**: **Not used by FocusRefine.** Included automatically by the Android OS because `NotificationListenerService` inherently has the OS-level capability to call `cancelNotification()` or interact with notification action buttons. FocusRefine **never** sends automated replies or injects remote input into messaging apps.
3. **"Change settings"**: **Not used by FocusRefine.** A generic Android OS description bundled into notification listener access for apps that modify system notification policies. FocusRefine **never** modifies system settings.

### Can we request a minimal set without these system bullets?
**No.** The Android operating system hardcodes these three capability bullet points for **every** notification listener app on the platform. No app can customize or trim the text inside this system dialog. However, FocusRefine uses the absolute minimum required system permission (`NotificationListenerService`) to perform local on-device notification filtering.

---

## 🔒 Privacy & Offline Guarantee
FocusRefine declares **zero internet permissions** (`android.permission.INTERNET`). All notification interception, evaluation (MediaPipe AI / semantic match), and database logging occur **100% locally on-device**. No notification data is ever transmitted, cached externally, or uploaded.
