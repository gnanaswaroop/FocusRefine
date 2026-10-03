package com.doicare.app

import android.app.Notification
import android.content.Context
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale

class NotificationFilterService : NotificationListenerService(), TextToSpeech.OnInitListener {

    private val TAG = "NotificationFilter"
    private var tts: TextToSpeech? = null
    private lateinit var database: AppDatabase

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this, this)
        database = AppDatabase.getDatabase(this)
        Log.d(TAG, "Service Created")
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts?.language = Locale.US
            Log.d(TAG, "TTS Initialized")
        } else {
            Log.e(TAG, "TTS Initialization Failed")
        }
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

        val prefs = getSharedPreferences("DoICarePrefs", Context.MODE_PRIVATE)
        val isRunning = prefs.getBoolean("service_running", true)
        if (!isRunning) {
            return
        }

        val currentMode = prefs.getString("current_mode", "Commute") ?: "Commute"

        val isUrgent = analyzeContent(title, text, currentMode)

        if (isUrgent) {
            // Read it out loud
            val speechText = "Urgent notification from $title: $text"
            tts?.speak(speechText, TextToSpeech.QUEUE_ADD, null, null)
        } else {
            // Archive it
            if (sbn.isClearable) {
                cancelNotification(sbn.key)
                Log.d(TAG, "Archived notification: ${sbn.key}")

                // Save to Room DB (Inbox)
                CoroutineScope(Dispatchers.IO).launch {
                    val entity = NotificationEntity(
                        title = title,
                        text = text,
                        packageName = packageName,
                        timestamp = System.currentTimeMillis()
                    )
                    database.notificationDao().insert(entity)
                }
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        Log.d(TAG, "Notification Removed: \${sbn.key}")
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    private fun analyzeContent(title: String?, text: String?, mode: String): Boolean {
        // Mocking AI Content Analysis via basic keywords based on selected mode.
        val content = "${title ?: ""} ${text ?: ""}".lowercase()

        return when (mode) {
            "Commute" -> {
                // Focus on navigation, transit, meetings
                content.contains("navigation") || content.contains("turn left") ||
                content.contains("turn right") || content.contains("meeting") ||
                content.contains("traffic") || content.contains("call")
            }
            "Focus" -> {
                // Suppress almost everything except explicit calls or VIPs (mocked as 'boss')
                content.contains("call") || content.contains("boss") || content.contains("emergency")
            }
            "Reading" -> {
                // Extremely strict, only emergencies
                content.contains("emergency") || content.contains("hospital")
            }
            else -> {
                content.contains("urgent")
            }
        }
    }
}
