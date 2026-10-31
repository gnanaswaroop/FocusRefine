package com.focusrefine.app

import android.content.Context
import android.content.Intent

/**
 * The one place a user's "show this" / "hide this" choice is applied, so the detail screen and the inbox
 * quick actions behave the same way. Blocking: call from a background thread.
 */
object FeedbackRecorder {

    fun learnPreference(context: Context): Boolean =
        context.getSharedPreferences("FocusRefinePrefs", Context.MODE_PRIVATE).getBoolean("learn_from_choices", true)

    /**
     * Stores [notif] as the user wants it ([makeUrgent] = Shown). When [learn] ("Learn from my choices") is on, the
     * row becomes a learned rule and the choice also teaches the on-device personal model (built-in modes only).
     * The row's confidence becomes certain: the user decided. Returns the stored row.
     */
    fun apply(context: Context, notif: NotificationEntity, makeUrgent: Boolean, activeMode: String, learn: Boolean = learnPreference(context)): NotificationEntity {
        val ruleText = if (learn) {
            AiRuleSynthesizer.synthesizeRule(
                packageName = notif.packageName,
                title = notif.title,
                text = notif.text,
                isUrgent = makeUrgent,
                activeMode = activeMode
            )
        } else notif.synthesizedRule

        val updated = notif.copy(
            isUrgent = makeUrgent,
            isLearned = learn,
            synthesizedRule = ruleText,
            confidence = Confidence.RULE
        )
        AppDatabase.getDatabase(context).notificationDao().insert(updated)
        if (ReviewQueue.refresh(context)) context.sendBroadcast(Intent(NotificationFilterService.ACTION_UPDATE_COUNT))
        if (learn) PersonalLearning.record(context, notif.title, notif.text, activeMode, makeUrgent, notif.packageName)
        return updated
    }
}
