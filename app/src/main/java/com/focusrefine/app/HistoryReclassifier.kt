package com.focusrefine.app

import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * Re-runs the current classification pipeline over every stored notification, so the inbox
 * reflects the active mode, filter strength, exceptions and learned rules.
 *
 * Learned-rule rows ([NotificationEntity.isLearned]) are the user's own feedback, so they are
 * left untouched. Only the in-app inbox changes: nothing is re-posted to or cancelled from
 * the system notification shade.
 */
object HistoryReclassifier {

    private const val TAG = "HistoryReclassifier"

    data class Summary(
        val total: Int,
        val toShown: Int,
        val toMuted: Int,
        val failed: Int
    ) {
        val changed get() = toShown + toMuted
    }

    /** Blocking; call off the main thread. */
    fun run(context: Context): Summary {
        val database = AppDatabase.getDatabase(context)
        val dao = database.notificationDao()
        val prefs = context.getSharedPreferences("FocusRefinePrefs", Context.MODE_PRIVATE)
        val defaultMode = PromptManager.getDefaultModeName(context)
        val mode = prefs.getString("current_mode", defaultMode) ?: defaultMode

        val history = dao.getHistory()
        val textClassifier = NotificationClassifier.createTextClassifier(context)
        val classifier = NotificationClassifier(context, database, textClassifier)

        var toShown = 0
        var toMuted = 0
        var failed = 0
        try {
            history.forEach { row ->
                try {
                    val result = classifier.classify(row.title, row.text, mode)
                    if (result.isUrgent != row.isUrgent || result.explanation != row.explanation) {
                        dao.updateClassification(row.id, result.isUrgent, result.explanation)
                    }
                    if (result.isUrgent && !row.isUrgent) toShown++
                    if (!result.isUrgent && row.isUrgent) toMuted++
                } catch (e: Exception) {
                    failed++
                    Log.e(TAG, "Failed to reclassify notification ${row.id}: ${e.message}")
                }
            }
        } finally {
            textClassifier?.close()
        }

        // The muted counter in the ongoing notification tracks muted items; keep it in step.
        val delta = toMuted - toShown
        if (delta != 0) {
            val count = (prefs.getInt("suppressed_count", 0) + delta).coerceAtLeast(0)
            prefs.edit().putInt("suppressed_count", count).apply()
        }
        context.sendBroadcast(Intent(NotificationFilterService.ACTION_UPDATE_COUNT))
        context.sendBroadcast(
            Intent(NotificationFilterService.ACTION_NOTIFICATION_ARRIVED).setPackage(context.packageName)
        )

        return Summary(history.size, toShown, toMuted, failed)
    }
}
