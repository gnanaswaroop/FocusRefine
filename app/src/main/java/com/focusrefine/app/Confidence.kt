package com.focusrefine.app

import kotlin.math.abs

/**
 * How sure the app was about a Shown/Muted decision.
 *
 * For the embedding model the decision value `z` is positive for Shown and negative for Muted, and `|z|` is the
 * distance from the line between them (`z = baseScale * (score - threshold) + personal adjustment`, the
 * built-in score being a logit). The confidence is
 * `sigmoid(|z|)`: 0.5 right on the line, 1.0 far from it. A user-made or exception decision is certain. It is a
 * distance, not a calibrated probability, so the UI shows words ("Not sure") and not percentages.
 */
object Confidence {
    /** Rules, exceptions and the user's own choices. */
    const val RULE = 1.0f

    /** Below this the inbox flags the decision for review: about one filtering-strength step (0.02) from the threshold. */
    const val UNCERTAIN_BELOW = 0.75f
    const val VERY_SURE_FROM = 0.90f

    fun fromDecision(decision: Double): Float = PersonalModel.sigmoid(abs(decision)).toFloat()

    fun isUncertain(confidence: Float?): Boolean = confidence != null && confidence < UNCERTAIN_BELOW

    fun label(confidence: Float?): String? = when {
        confidence == null -> null
        confidence >= VERY_SURE_FROM -> "Very sure"
        confidence >= UNCERTAIN_BELOW -> "Fairly sure"
        else -> "Not sure"
    }
}

/** True for decisions worth a second look: low confidence and not already corrected by the user. */
fun NotificationEntity.isUncertain(): Boolean = !isLearned && Confidence.isUncertain(confidence)

/**
 * How many decisions are waiting for the user's review, kept in a preference so the ongoing notification can show it
 * without touching the database on the main thread. Recounted whenever decisions are stored or corrected.
 */
object ReviewQueue {
    private const val PREFS = "FocusRefinePrefs"
    private const val KEY = "uncertain_count"

    fun count(context: android.content.Context): Int =
        context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE).getInt(KEY, 0)

    /** Recounts from the database (blocking, off the main thread). Returns true when the number changed. */
    fun refresh(context: android.content.Context): Boolean {
        val now = AppDatabase.getDatabase(context).notificationDao().countUncertain(Confidence.UNCERTAIN_BELOW)
        val prefs = context.getSharedPreferences(PREFS, android.content.Context.MODE_PRIVATE)
        val changed = prefs.getInt(KEY, 0) != now
        if (changed) prefs.edit().putInt(KEY, now).apply()
        return changed
    }
}
