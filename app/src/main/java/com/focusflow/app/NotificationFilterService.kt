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
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.text.textclassifier.TextClassifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationFilterService : NotificationListenerService() {

    private val TAG = "NotificationFilter"
    private lateinit var database: AppDatabase
    private val CHANNEL_ID = "focusflow_service_channel"
    private val NOTIFICATION_ID = 1001
    private var textClassifier: TextClassifier? = null

    private val toggleReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == ACTION_TOGGLE_SERVICE) {
                val prefs = context.getSharedPreferences("FocusFlowPrefs", Context.MODE_PRIVATE)
                val current = prefs.getBoolean("service_running", true)
                val newState = !current
                prefs.edit().putBoolean("service_running", newState).apply()
                val count = prefs.getInt("suppressed_count", 0)
                updateForegroundNotification(newState, count)
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

        val prefs = getSharedPreferences("FocusFlowPrefs", Context.MODE_PRIVATE)
        val useAi = prefs.getBoolean("use_ai_model", true)

        if (useAi) {
            try {
                val baseOptions = BaseOptions.builder().setModelAssetPath("text_classifier.tflite").build()
                val options = TextClassifier.TextClassifierOptions.builder().setBaseOptions(baseOptions).build()
                textClassifier = TextClassifier.createFromOptions(this, options)
                Log.d(TAG, "MediaPipe TextClassifier initialized successfully")
            } catch (e: Exception) {
                Log.d(TAG, "MediaPipe model asset not found; using semantic prompt heuristic engine: ${e.message}")
            }
        } else {
            Log.d(TAG, "MediaPipe AI Model disabled by user settings")
        }

        val filter = IntentFilter(ACTION_TOGGLE_SERVICE)
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
        val prefs = getSharedPreferences("FocusFlowPrefs", Context.MODE_PRIVATE)
        val isRunning = prefs.getBoolean("service_running", true)
        val count = prefs.getInt("suppressed_count", 0)
        try {
            startForeground(NOTIFICATION_ID, buildForegroundNotification(isRunning, count))
            Log.d(TAG, "Service onStartCommand Started in Foreground: running=$isRunning")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start foreground in onStartCommand: ${e.message}")
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "FocusFlow Background Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent notification showing suppression count and quick STOP/START toggle."
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildForegroundNotification(isRunning: Boolean, suppressedCount: Int): Notification {
        val toggleIntent = Intent(ACTION_TOGGLE_SERVICE)
        val flagImmutable = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0
        val togglePendingIntent = PendingIntent.getBroadcast(
            this, 0, toggleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or flagImmutable
        )

        val actionText = if (isRunning) "STOP" else "START"
        val statusText = if (isRunning) {
            "Suppressed: $suppressedCount notifications"
        } else {
            "Filtering stopped — showing all notifications"
        }

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
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, actionText, togglePendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
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

        Log.d(TAG, "Received Notification: Title=$title, Text=$text")

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
            if (sbn.isClearable) {
                cancelNotification(sbn.key)
                Log.d(TAG, "Suppressed/Muted notification: ${sbn.key}")

                val count = prefs.getInt("suppressed_count", 0) + 1
                prefs.edit().putInt("suppressed_count", count).apply()
                updateForegroundNotification(true, count)

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
        textClassifier?.close()
        super.onDestroy()
    }

    private fun analyzeContent(title: String?, text: String?, mode: String): Boolean {
        val content = "${title ?: ""} ${text ?: ""}".lowercase()
        val prefs = getSharedPreferences("FocusFlowPrefs", Context.MODE_PRIVATE)
        val useAi = prefs.getBoolean("use_ai_model", true)

        if (useAi && textClassifier != null) {
            try {
                val result = textClassifier!!.classify(content)
                for (classification in result.classificationResult().classifications()) {
                    for (category in classification.categories()) {
                        if (category.categoryName().equals("urgent", ignoreCase = true) && category.score() > 0.6f) {
                            return true
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "MediaPipe classification error: ${e.message}")
            }
        }

        val prompt = when (mode) {
            "Commute" -> prefs.getString("prompt_commute", "navigation, maps, turn left, turn right, traffic, call, vip, boss") ?: ""
            "Do Not Disturb (DND)" -> prefs.getString("prompt_dnd", "emergency, hospital, 911, critical") ?: ""
            else -> prefs.getString("custom_mode_prompt_$mode", "urgent") ?: ""
        }

        val keywords = prompt.lowercase().split(",", " ").map { it.trim() }.filter { it.isNotEmpty() }
        if (keywords.isEmpty()) return content.contains("urgent")

        return keywords.any { keyword -> content.contains(keyword) }
    }
}
