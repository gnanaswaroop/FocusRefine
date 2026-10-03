package com.focusflow.app

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationFilterService : NotificationListenerService() {

    private val TAG = "NotificationFilter"
    private lateinit var database: AppDatabase

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getDatabase(this)
        Log.d(TAG, "Service Created")
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val notification = sbn.notification
        val extras = notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE)
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        val packageName = sbn.packageName

        Log.d(TAG, "Received Notification: Title=$title, Text=$text")

        // Don't intercept own app or system UI to prevent weird loops
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
