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
  - **Manifest Declaration**:
    ```xml
    <service
        android:name=".NotificationFilterService"
        android:label="FocusRefine Notification Filter"
        android:permission="android.permission.BIND_NOTIFICATION_LISTENER_SERVICE"
        android:foregroundServiceType="specialUse"
        android:exported="true">
        <intent-filter>
            <action android:name="android.service.notification.NotificationListenerService" />
        </intent-filter>
    </service>
    ```

---

### 2. `FOREGROUND_SERVICE`
- **Why Required**: Modern Android versions (API 28+) aggressively restrict background execution. Without a foreground service permission, background listeners running continuous tasks are prone to being killed under memory pressure. `FOREGROUND_SERVICE` allows FocusRefine to promote its filtering service to foreground status.
- **Class / Component**: [NotificationFilterService.kt](file:///E:/Projects/do-i-care-app/app/src/main/java/com/focusflow/app/NotificationFilterService.kt) (`onStartCommand` / `startForeground`).
- **Code Usage**:
  ```kotlin
  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
      val notification = buildForegroundNotification(isRunning, suppressedCount)
      startForeground(NOTIFICATION_ID, notification)
      return START_STICKY
  }
  ```

---

### 3. `FOREGROUND_SERVICE_SPECIAL_USE`
- **Why Required**: Introduced in Android 14 (API 34+), Google Play requires foreground services to declare a specific compliant `foregroundServiceType`. Because notification filtering is a continuous utility task that doesn't fit standard types (like media playback, location, or camera), `specialUse` is required and declared with proper justification.
- **Class / Component**: [AndroidManifest.xml](file:///E:/Projects/do-i-care-app/app/src/main/AndroidManifest.xml) (`<service android:foregroundServiceType="specialUse" />`).
- **Code Usage**:
  Paired with `FOREGROUND_SERVICE` in the service manifest block to satisfy Android 14+ runtime enforcement policies.

---

### 4. `POST_NOTIFICATIONS`
- **Why Required**: Introduced in Android 13 (API 33+), runtime permission is required to post notifications. FocusRefine requires this permission to display its mandatory ongoing foreground notification (`NotificationManager.notify()`), which provides the user with visibility into the filtering engine's status, live suppression count, and a one-tap STOP/START action button.
- **Class / Component**: [MainActivity.kt](file:///E:/Projects/do-i-care-app/app/src/main/java/com/focusflow/app/MainActivity.kt) (`onCreate` runtime permission check).
- **Code Usage**:
  ```kotlin
  if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
      if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
          requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 101)
      }
  }
  ```
  - And posting the persistent notification in [NotificationFilterService.kt](file:///E:/Projects/do-i-care-app/app/src/main/java/com/focusflow/app/NotificationFilterService.kt):
    ```kotlin
    private fun updateForegroundNotification(isRunning: Boolean, suppressedCount: Int) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildForegroundNotification(isRunning, suppressedCount))
    }
    ```

---

## 🔒 Privacy & Offline Guarantee
FocusRefine declares **zero internet permissions** (`android.permission.INTERNET`). All notification interception, evaluation (MediaPipe AI / semantic match), and database logging occur **100% locally on-device**. No notification data is ever transmitted, cached externally, or uploaded.
