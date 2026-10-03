package com.focusflow.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
    private val CHANNEL_ID = "focusflow_service_channel"
    private val NOTIFICATION_ID = 1001

    private val toggleReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_TOGGLE_SERVICE) {
                val prefs = context.getSharedPreferences("FocusFlowPrefs", Context.MODE_PRIVATE)
                val current = prefs.getBoolean("service_running", true)
                val newState = !current
                prefs.edit().putBoolean("service_running", newState).apply()
                updateForegroundNotification(newState)
                Log.d(TAG, "Service toggled from notification bar: running = $newState")
            }
        }
    }

    companion object {
        const val ACTION_TOGGLE_SERVICE = "com.focusflow.app.ACTION_TOGGLE_SERVICE"
    }

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getDatabase(this)
        createNotificationChannel()

        val filter = IntentFilter(ACTION_TOGGLE_SERVICE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(toggleReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(toggleReceiver, filter)
        }

        val prefs = getSharedPreferences("FocusFlowPrefs", Context.MODE_PRIVATE)
        val isRunning = prefs.getBoolean("service_running", true)
        startForeground(NOTIFICATION_ID, buildForegroundNotification(isRunning))
        Log.d(TAG, "Service Created & Started in Foreground")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "FocusFlow Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent notification for FocusFlow background filtering and quick toggle."
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(isRunning: Boolean): Notification {
        val toggleIntent = Intent(ACTION_TOGGLE_SERVICE)
        val flagImmutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val togglePendingIntent = PendingIntent.getBroadcast(
            this, 0, toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or flagImmutable
        )

        val actionText = if (isRunning) "TURN OFF" else "TURN ON"
        val statusText = if (isRunning) "Filtering active in background" else "Filtering paused"

        val openAppIntent = Intent(this, MainActivity::class.java)
        val openAppPendingIntent = PendingIntent.getActivity(
            this, 0, openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or flagImmutable
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("FocusFlow Active")
            .setContentText(statusText)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentIntent(openAppPendingIntent)
            .addAction(android.R.drawable.ic_menu_set_as, actionText, togglePendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun updateForegroundNotification(isRunning: Boolean) {
        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIFICATION_ID, buildForegroundNotification(isRunning))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val notification = sbn.notification
        val extras = notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE)
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val packageName = sbn.packageName

        Log.d(TAG, "Received Notification: Title=$title, Text=$text")

        // Don't intercept own app or system UI to prevent loops
        if (packageName == this.packageName || packageName == "com.android.systemui") {
            return
        }

        val prefs = getSharedPreferences("FocusFlowPrefs", Context.MODE_PRIVATE)
        val isRunning = prefs.getBoolean("service_running", true)
        if (!isRunning) {
            return
        }

        val currentMode = prefs.getString("current_mode", "Commute") ?: "Commute"
        val isUrgent = analyzeContent(title, text, currentMode)

        if (isUrgent) {
            // Allowed / Not suppressed: leave notification visible in system shade, record as Shown
            Log.d(TAG, "Allowed (Shown) notification: ${sbn.key}")

            CoroutineScope(Dispatchers.IO).launch {
                val entity = NotificationEntity(
                    title = title,
                    text = text,
                    packageName = packageName,
                    timestamp = System.currentTimeMillis(),
                    isUrgent = true
                )
                database.notificationDao().insert(entity)
            }
        } else {
            // Suppressed / Muted: cancel system notification, record in Muted inbox
            if (sbn.isClearable) {
                cancelNotification(sbn.key)
                Log.d(TAG, "Suppressed/Muted notification: ${sbn.key}")

                CoroutineScope(Dispatchers.IO).launch {
                    val entity = NotificationEntity(
                        title = title,
                        text = text,
                        packageName = packageName,
                        timestamp = System.currentTimeMillis(),
                        isUrgent = false
                    )
                    database.notificationDao().insert(entity)
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
        super.onDestroy()
    }

    private fun analyzeContent(title: String?, text: String?, mode: String): Boolean {
        val content = "${title ?: ""} ${text ?: ""}".lowercase()
        val prefs = getSharedPreferences("FocusFlowPrefs", Context.MODE_PRIVATE)

        val prompt = when (mode) {
            "Commute" -> prefs.getString("prompt_commute", "navigation, turn left, turn right, meeting, traffic, call") ?: ""
            "Focus" -> prefs.getString("prompt_focus", "call, boss, emergency") ?: ""
            "Reading" -> prefs.getString("prompt_reading", "emergency, hospital") ?: ""
            else -> prefs.getString("custom_mode_prompt_$mode", "urgent") ?: ""
        }

        val keywords = prompt.lowercase().split(",", " ").map { it.trim() }.filter { it.isNotEmpty() }
        if (keywords.isEmpty()) return content.contains("urgent")

        return keywords.any { keyword -> content.contains(keyword) }
    }
}
