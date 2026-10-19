package com.focusrefine.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.BitmapFactory
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationFilterService : NotificationListenerService() {

    private val TAG = "NotificationFilter"
    private lateinit var database: AppDatabase
    private val CHANNEL_ID = "focusrefine_service_channel"
    private val NOTIFICATION_ID = 1001
    private var embeddingClassifier: EmbeddingClassifier? = null
    private lateinit var classifier: NotificationClassifier

    private val toggleReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val prefs = context.getSharedPreferences("FocusRefinePrefs", Context.MODE_PRIVATE)
            if (intent.action == ACTION_TOGGLE_SERVICE) {
                val current = prefs.getBoolean("service_running", true)
                val newState = !current
                prefs.edit().putBoolean("service_running", newState).apply()
                val count = prefs.getInt("suppressed_count", 0)

                val stateIntent = Intent(ACTION_SERVICE_STATE_CHANGED).apply {
                    setPackage(context.packageName)
                    putExtra("is_running", newState)
                }
                context.sendBroadcast(stateIntent)

                updateForegroundNotification(newState, count)
                try {
                    val manager = getSystemService(NotificationManager::class.java)
                    if (manager.isNotificationPolicyAccessGranted) {
                        if (newState) {
                            manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_PRIORITY)
                        } else {
                            manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                        }
                    }
                } catch (_: Exception) {}
                Log.d(TAG, "Service toggled from notification bar: running = $newState")
            } else if (intent.action == ACTION_UPDATE_COUNT) {
                val isRunning = prefs.getBoolean("service_running", true)
                val count = prefs.getInt("suppressed_count", 0)
                updateForegroundNotification(isRunning, count)
                Log.d(TAG, "Foreground notification updated with count: $count")
            }
        }
    }

    companion object {
        const val ACTION_TOGGLE_SERVICE = "com.focusrefine.app.ACTION_TOGGLE_SERVICE"
        const val ACTION_UPDATE_COUNT = "com.focusrefine.app.ACTION_UPDATE_COUNT"
        const val ACTION_SERVICE_STATE_CHANGED = "com.focusrefine.app.ACTION_SERVICE_STATE_CHANGED"
        const val ACTION_NOTIFICATION_ARRIVED = "com.focusrefine.app.ACTION_NOTIFICATION_ARRIVED"
        const val ACTION_STOP_SERVICE = "com.focusrefine.app.ACTION_STOP_SERVICE"
    }

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getDatabase(this)
        createNotificationChannel()

        val prefs = getSharedPreferences("FocusRefinePrefs", Context.MODE_PRIVATE)
        embeddingClassifier = EmbeddingClassifier.create(this)
        classifier = NotificationClassifier(this, database, embeddingClassifier)
        embeddingClassifier?.let { engine ->
            Thread {
                try { engine.warmUp() } catch (e: Throwable) { Log.w(TAG, "Embedding warm-up failed: ${e.message}") }
            }.start()
        }

        val filter = IntentFilter().apply {
            addAction(ACTION_TOGGLE_SERVICE)
            addAction(ACTION_UPDATE_COUNT)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(toggleReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(toggleReceiver, filter)
        }

        try {
            val isRunning = prefs.getBoolean("service_running", true)
            val count = prefs.getInt("suppressed_count", 0)
            startForeground(NOTIFICATION_ID, buildForegroundNotification(isRunning, count))
            Log.d(TAG, "Service Created & Started in Foreground")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground service: ${e.message}")
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val prefs = getSharedPreferences("FocusRefinePrefs", Context.MODE_PRIVATE)
        val isRunning = prefs.getBoolean("service_running", true)
        val count = prefs.getInt("suppressed_count", 0)

        try {
            startForeground(NOTIFICATION_ID, buildForegroundNotification(isRunning, count))
            Log.d(TAG, "Service onStartCommand Started in Foreground: running=$isRunning, count=$count")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground in onStartCommand: ${e.message}")
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "FocusRefine Live Update & Filtering Service",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Ongoing Live Update notification showing suppression count and quick STOP/START toggle."
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(isRunning: Boolean, suppressedCount: Int): Notification {
        val toggleIntent = Intent(this, NotificationToggleReceiver::class.java).apply {
            action = ACTION_TOGGLE_SERVICE
        }
        val flagImmutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val togglePendingIntent = PendingIntent.getBroadcast(
            this, 0, toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or flagImmutable
        )

        val actionText = if (isRunning) "STOP" else "START"
        val toReview = ReviewQueue.count(this)
        val statusText = if (isRunning) {
            "Suppressed: $suppressedCount notifications" +
                if (toReview > 0) " · $toReview close ${if (toReview == 1) "call" else "calls"} to review" else ""
        } else {
            "Filtering stopped — showing all notifications"
        }

        val openAppIntent = Intent(this, MainActivity::class.java)
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or flagImmutable
        )

        val appIconBitmap = BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(statusText)
            .setSmallIcon(R.drawable.ic_notification)
            .setLargeIcon(appIconBitmap)
            .setContentIntent(openAppPendingIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, actionText, togglePendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateForegroundNotification(isRunning: Boolean, suppressedCount: Int) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildForegroundNotification(isRunning, suppressedCount))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val notification = sbn.notification
        val extras = notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE)
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val packageName = sbn.packageName

        // Never log notification content: logcat is readable over adb and by other tools.
        Log.d(TAG, "Received notification from $packageName")

        if (packageName == this.packageName || packageName == "com.android.systemui") {
            return
        }

        val prefs = getSharedPreferences("FocusRefinePrefs", Context.MODE_PRIVATE)
        val isRunning = prefs.getBoolean("service_running", true)
        if (!isRunning) {
            return
        }

        CoroutineScope(Dispatchers.IO).launch {
            val defaultMode = PromptManager.getDefaultModeName(this@NotificationFilterService)
            val currentMode = prefs.getString("current_mode", defaultMode) ?: defaultMode
            val classification = classifier.classify(title, text, currentMode)

            if (classification.isUrgent) {
                Log.d(TAG, "Allowed (Shown) notification: ${sbn.key}")
                val entity = NotificationEntity(
                    title = title,
                    text = text,
                    packageName = packageName,
                    timestamp = System.currentTimeMillis(),
                    isUrgent = true,
                    explanation = classification.explanation,
                    confidence = classification.confidence
                )
                database.notificationDao().insert(entity)
                sendBroadcast(Intent(ACTION_NOTIFICATION_ARRIVED).setPackage(this@NotificationFilterService.packageName))
                if (ReviewQueue.refresh(this@NotificationFilterService)) updateForegroundNotification(true, prefs.getInt("suppressed_count", 0))
            } else {
                if (sbn.isClearable) {
                    cancelNotification(sbn.key)
                    Log.d(TAG, "Suppressed/Muted notification: ${sbn.key}")

                    val count = prefs.getInt("suppressed_count", 0) + 1
                    prefs.edit().putInt("suppressed_count", count).apply()
                    updateForegroundNotification(true, count)

                    val entity = NotificationEntity(
                        title = title,
                        text = text,
                        packageName = packageName,
                        timestamp = System.currentTimeMillis(),
                        isUrgent = false,
                        explanation = classification.explanation,
                    confidence = classification.confidence
                    )
                    database.notificationDao().insert(entity)
                    sendBroadcast(Intent(ACTION_NOTIFICATION_ARRIVED).setPackage(this@NotificationFilterService.packageName))
                    if (ReviewQueue.refresh(this@NotificationFilterService)) updateForegroundNotification(true, prefs.getInt("suppressed_count", 0))
                }
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        Log.d(TAG, "Notification Removed: ${sbn.key}")
    }

    override fun onDestroy() {
        try {
            unregisterReceiver(toggleReceiver)
        } catch (_: Exception) {
        }
        try {
            val manager = getSystemService(NotificationManager::class.java)
            if (manager.isNotificationPolicyAccessGranted) {
                manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            }
        } catch (_: Exception) {
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.cancel(NOTIFICATION_ID)
        } catch (_: Exception) {
        }
        embeddingClassifier?.close()
        super.onDestroy()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        try {
            val manager = getSystemService(NotificationManager::class.java)
            if (manager.isNotificationPolicyAccessGranted) {
                manager.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
            }
            manager.cancel(NOTIFICATION_ID)
            stopSelf()
        } catch (_: Exception) {}
        super.onTaskRemoved(rootIntent)
    }
}
