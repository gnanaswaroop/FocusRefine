package com.doicare.app

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.speech.tts.TextToSpeech
import android.util.Log
import java.util.Locale

class NotificationFilterService : NotificationListenerService(), TextToSpeech.OnInitListener {

    private val TAG = "NotificationFilter"
    private var tts: TextToSpeech? = null

    override fun onCreate() {
        super.onCreate()
        tts = TextToSpeech(this, this)
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
        val tickerText = notification.tickerText?.toString()
        val extras = notification.extras
        val title = extras.getString(Notification.EXTRA_TITLE)
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()

        Log.d(TAG, "Received Notification: Title=$title, Text=$text")

        // Simplified MVP Logic: If it's a specific app or contains certain words, we might keep it.
        // For the MVP, we just demonstrate interception and TTS.

        val isUrgent = analyzeContent(title, text)

        if (isUrgent) {
            // Read it out loud
            val speechText = "Urgent notification from $title: $text"
            tts?.speak(speechText, TextToSpeech.QUEUE_ADD, null, null)
        } else {
            // "Archive" it (in MVP, we cancel it to remove from status bar)
            // For a real inbox, we would save to a local database here before cancelling.
            if (sbn.isClearable) {
                cancelNotification(sbn.key)
                Log.d(TAG, "Archived notification: ${sbn.key}")
            }
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        Log.d(TAG, "Notification Removed: ${sbn.key}")
    }

    override fun onDestroy() {
        tts?.stop()
        tts?.shutdown()
        super.onDestroy()
    }

    private fun analyzeContent(title: String?, text: String?): Boolean {
        // Placeholder for AI/Prompt based filtering logic
        // For MVP, just a simple string match mock
        val content = "${title ?: ""} ${text ?: ""}".lowercase()
        return content.contains("urgent") || content.contains("call") || content.contains("meeting")
    }
}
