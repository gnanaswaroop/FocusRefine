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
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.text.textclassifier.TextClassifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationFilterService : NotificationListenerService() {

    private val TAG = "NotificationFilter"
    private lateinit var database: AppDatabase
    private val CHANNEL_ID = "focusrefine_service_channel"
    private val NOTIFICATION_ID = 1001
    private var textClassifier: TextClassifier? = null

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
        val useAi = prefs.getBoolean("use_ai_model", true)

        if (useAi) {
            try {
                val baseOptions = BaseOptions.builder().setModelAssetPath("text_classifier.tflite").build()
                val options = TextClassifier.TextClassifierOptions.builder().setBaseOptions(baseOptions).build()
                textClassifier = TextClassifier.createFromOptions(this, options)
                Log.d(TAG, "MediaPipe TextClassifier initialized successfully")
            } catch (e: Throwable) {
                Log.d(TAG, "MediaPipe model asset or JNI native library not found (x86_64/emulator); using semantic prompt heuristic engine: ${e.message}")
            }
        } else {
            Log.d(TAG, "MediaPipe AI Model disabled by user settings")
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

        val appIconBitmap = BitmapFactory.decodeResource(resources, R.mipmap.ic_launcher)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Focus Refine Active")
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

        Log.d(TAG, "Received Notification: Title=$title, Text=$text")

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
            val classification = analyzeContentWithDetails(title, text, currentMode)

            if (classification.isUrgent) {
                Log.d(TAG, "Allowed (Shown) notification: ${sbn.key}")
                val entity = NotificationEntity(
                    title = title,
                    text = text,
                    packageName = packageName,
                    timestamp = System.currentTimeMillis(),
                    isUrgent = true,
                    explanation = classification.explanation
                )
                database.notificationDao().insert(entity)
                sendBroadcast(Intent(ACTION_NOTIFICATION_ARRIVED).setPackage(this@NotificationFilterService.packageName))
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
                        explanation = classification.explanation
                    )
                    database.notificationDao().insert(entity)
                    sendBroadcast(Intent(ACTION_NOTIFICATION_ARRIVED).setPackage(this@NotificationFilterService.packageName))
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
        textClassifier?.close()
        super.onDestroy()
    }

    data class ClassificationResult(
        val isUrgent: Boolean,
        val explanation: String
    )

    private fun analyzeContentWithDetails(title: String?, text: String?, mode: String): ClassificationResult {
        val content = "${title ?: ""} ${text ?: ""}".lowercase()
        val prefs = getSharedPreferences("FocusRefinePrefs", Context.MODE_PRIVATE)

        // 1. MediaPipe On-Device AI Pipeline Evaluation FIRST (if enabled and classifier available)
        val useAi = prefs.getBoolean("use_ai_model", true)
        val filterStrength = prefs.getInt("filter_strength", 1) // 0=Gentle, 1=Balanced, 2=Strict
        val scoreThreshold = when (filterStrength) {
            0 -> 0.4f // Gentle
            2 -> 0.8f // Strict
            else -> 0.6f // Balanced
        }

        var aiExplanation: String? = null
        var aiClassifiedUrgent = false

        if (useAi && textClassifier != null) {
            try {
                val result = textClassifier!!.classify(content)
                for (classification in result.classificationResult().classifications()) {
                    for (category in classification.categories()) {
                        if (category.categoryName().equals("urgent", ignoreCase = true)) {
                            val score = category.score()
                            val scorePercent = (score * 100).toInt()
                            val thresholdPercent = (scoreThreshold * 100).toInt()
                            if (score >= scoreThreshold) {
                                aiClassifiedUrgent = true
                                aiExplanation = "🤖 [AI Pipeline] MediaPipe model classified notification as urgent with $scorePercent% confidence (exceeded $mode threshold $thresholdPercent%)."
                            } else {
                                aiExplanation = "🤖 [AI Pipeline] MediaPipe model confidence $scorePercent% was below $mode urgency threshold ($thresholdPercent%); muted as routine."
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "MediaPipe AI Pipeline classification error: ${e.message}")
            }
        }

        // 2. Check for matching user learned rule overrides
        val learnedRule = database.notificationDao().findMatchingLearnedRule(title ?: "", "%${text ?: ""}%")
        if (learnedRule != null) {
            val ruleTag = learnedRule.synthesizedRule ?: "User preference override"
            Log.d(TAG, "🔍 [Semantic Search] Learned rule override matched: isUrgent = ${learnedRule.isUrgent}")
            return ClassificationResult(
                isUrgent = learnedRule.isUrgent,
                explanation = "🔍 [Semantic Search] Matched user learned preference rule '$ruleTag'. Action: ${if (learnedRule.isUrgent) "Shown" else "Muted"}."
            )
        }

        // 2b. Check all AI Synthesized semantic rules
        val synthesizedRules = database.notificationDao().getSynthesizedRules()
        for (ruleNotif in synthesizedRules) {
            val ruleText = ruleNotif.synthesizedRule ?: continue
            if (AiRuleSynthesizer.matchesRule(ruleText, title, text)) {
                Log.d(TAG, "🔍 [Semantic Search] Matched AI Synthesized rule '$ruleText': isUrgent = ${ruleNotif.isUrgent}")
                return ClassificationResult(
                    isUrgent = ruleNotif.isUrgent,
                    explanation = "🔍 [Semantic Search] Matched AI synthesized preference rule '$ruleText'. Action: ${if (ruleNotif.isUrgent) "Shown" else "Muted"}."
                )
            }
        }

        // 3. Check JSON-driven exceptions from exceptions.json
        val exceptions = ExceptionManager.getAllExceptions(this)
        for (exc in exceptions) {
            val isEnabled = prefs.getBoolean(exc.prefKey, exc.defaultEnabled)
            if (isEnabled) {
                if (exc.keywords.any { keyword -> content.contains(keyword.lowercase()) }) {
                    Log.d(TAG, "🔍 [Semantic Search] Matched user-enabled exception '${exc.title}': showing notification")
                    return ClassificationResult(
                        isUrgent = true,
                        explanation = "🔍 [Semantic Search] Matched user exception '${exc.title}'. Priority override allowed notification."
                    )
                }
            }
        }

        // 4. Return MediaPipe AI Pipeline Result if available
        if (aiExplanation != null) {
            return ClassificationResult(
                isUrgent = aiClassifiedUrgent,
                explanation = aiExplanation
            )
        }

        // 5. Semantic Prompt Search & Keyword Matching Fallback
        val ootbPrompts = PromptManager.getAllOotbPrompts(this)
        val basePrompt = when {
            mode.contains("Commute", ignoreCase = true) ->
                prefs.getString("prompt_Commute", ootbPrompts["Commute"] ?: "navigation, maps, turn left, turn right, traffic, call, vip, boss, route") ?: ""
            mode.contains("Focus", ignoreCase = true) ->
                prefs.getString("prompt_Deep Work / Focus", ootbPrompts["Deep Work / Focus"] ?: "urgent, call, boss, meeting, build, outage, deadline") ?: ""
            mode.contains("Sleep", ignoreCase = true) ->
                prefs.getString("prompt_Sleeping / Rest", ootbPrompts["Sleeping / Rest"] ?: "emergency, family, hospital, call, alarm") ?: ""
            mode.contains("DND", ignoreCase = true) ->
                prefs.getString("prompt_Do Not Disturb (DND)", ootbPrompts["Do Not Disturb (DND)"] ?: "emergency, hospital, 911, critical") ?: ""
            else ->
                prefs.getString("custom_mode_prompt_$mode", "urgent, call, emergency, navigation, meeting") ?: "urgent, call, emergency, navigation, meeting"
        }

        val enrichedPrompt = PromptManager.getEnrichedModePrompt(basePrompt, synthesizedRules.mapNotNull { it.synthesizedRule })
        val keywords = enrichedPrompt.lowercase().split(",", " ", ";", "[", "]", ":").map { it.trim() }.filter { it.isNotEmpty() }
        val matchedKeyword = keywords.firstOrNull { keyword -> content.contains(keyword) }

        if (matchedKeyword != null) {
            Log.d(TAG, "🔍 [Semantic Search] Matched $mode prompt token: $matchedKeyword")
            return ClassificationResult(
                isUrgent = true,
                explanation = "🔍 [Semantic Search] Matched active $mode system prompt token '$matchedKeyword'."
            )
        }

        Log.d(TAG, "🔍 [Semantic Search] Notification muted under $mode mode")
        return ClassificationResult(
            isUrgent = false,
            explanation = "🔍 [Semantic Search] Did not contain urgent prompt tokens for $mode mode; muted as casual communication."
        )
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
