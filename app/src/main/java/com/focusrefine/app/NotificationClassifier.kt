package com.focusrefine.app

import android.content.Context
import android.content.SharedPreferences
import android.util.Log

data class ClassificationResult(
    val isUrgent: Boolean,
    val explanation: String
)

/**
 * Decides whether a notification is Shown (urgent) or Muted. Shared by the live listener
 * service and the "re-run classification on history" action, so both always agree.
 *
 * Explanations are persisted and shown in the UI; keep their wording and prefixes stable.
 */
class NotificationClassifier(
    private val context: Context,
    private val database: AppDatabase,
    private val embeddingClassifier: EmbeddingClassifier?
) {

    /**
     * Precedence, first match wins:
     * 1. a user learned rule
     * 2. an AI-synthesized rule
     * 3. an enabled exception (navigation, calls, OTP, ...)
     * 4. the on-device embedding model, when it is loaded and the mode is a built-in one
     * 5. keywords from the active mode's prompt (always produces a result)
     */
    fun classify(title: String?, text: String?, mode: String): ClassificationResult {
        val content = "${title ?: ""} ${text ?: ""}".lowercase()
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val synthesizedRules by lazy { database.notificationDao().getSynthesizedRules() }

        return matchLearnedRule(title, text)
            ?: matchSynthesizedRule(synthesizedRules, title, text)
            ?: matchException(content, prefs)
            ?: classifyWithModel(content, mode, prefs)
            ?: classifyByModePrompt(content, mode, prefs, synthesizedRules)
    }

    // ---- 1. Learned rules -------------------------------------------------------------------

    private fun matchLearnedRule(title: String?, text: String?): ClassificationResult? {
        val rule = database.notificationDao().findMatchingLearnedRule(title ?: "", "%${text ?: ""}%")
            ?: return null
        val ruleTag = rule.synthesizedRule ?: "User preference override"
        Log.d(TAG, "$SEMANTIC Learned rule override matched: isUrgent = ${rule.isUrgent}")
        return ClassificationResult(
            isUrgent = rule.isUrgent,
            explanation = "$SEMANTIC Matched user learned preference rule '$ruleTag'. Action: ${actionLabel(rule.isUrgent)}."
        )
    }

    // ---- 2. AI-synthesized rules ------------------------------------------------------------

    private fun matchSynthesizedRule(
        rules: List<NotificationEntity>,
        title: String?,
        text: String?
    ): ClassificationResult? {
        val match = rules.firstOrNull { rule ->
            val ruleText = rule.synthesizedRule
            ruleText != null && AiRuleSynthesizer.matchesRule(ruleText, title, text)
        } ?: return null
        val ruleText = match.synthesizedRule ?: return null
        Log.d(TAG, "$SEMANTIC Matched AI synthesized rule: isUrgent = ${match.isUrgent}")
        return ClassificationResult(
            isUrgent = match.isUrgent,
            explanation = "$SEMANTIC Matched AI synthesized preference rule '$ruleText'. Action: ${actionLabel(match.isUrgent)}."
        )
    }

    // ---- 3. JSON-driven exceptions ----------------------------------------------------------

    private fun matchException(content: String, prefs: SharedPreferences): ClassificationResult? {
        val exception = ExceptionManager.getAllExceptions(context).firstOrNull { candidate ->
            prefs.getBoolean(candidate.prefKey, candidate.defaultEnabled) &&
                candidate.keywords.any { keyword -> content.contains(keyword.lowercase()) }
        } ?: return null
        Log.d(TAG, "$SEMANTIC Matched user-enabled exception '${exception.title}': showing notification")
        return ClassificationResult(
            isUrgent = true,
            explanation = "$SEMANTIC Matched user exception '${exception.title}'. Priority override allowed notification."
        )
    }

    // ---- 4. On-device embedding model -------------------------------------------------------

    /** Returns null when the model is off, unavailable, failed, or the mode has no anchors (custom modes). */
    private fun classifyWithModel(content: String, mode: String, prefs: SharedPreferences): ClassificationResult? {
        val engine = embeddingClassifier ?: return null
        if (!prefs.getBoolean(PREF_USE_AI, true)) return null

        val verdict = try {
            engine.classify(content, mode, prefs.getInt(PREF_FILTER_STRENGTH, 1))
        } catch (e: Exception) {
            Log.e(TAG, "Embedding classification error: ${e.message}")
            null
        } ?: return null

        val score = "%.3f".format(verdict.score)
        val threshold = "%.3f".format(verdict.threshold)
        return ClassificationResult(
            isUrgent = verdict.isUrgent,
            explanation = if (verdict.isUrgent) {
                "$AI_PIPELINE On-device embedding model scored this $score for $mode, at or above the $threshold urgency threshold; shown."
            } else {
                "$AI_PIPELINE On-device embedding model scored this $score for $mode, below the $threshold urgency threshold; muted as routine."
            }
        )
    }

    // ---- 5. Mode prompt keywords ------------------------------------------------------------

    private fun classifyByModePrompt(
        content: String,
        mode: String,
        prefs: SharedPreferences,
        synthesizedRules: List<NotificationEntity>
    ): ClassificationResult {
        val prompt = PromptManager.getEnrichedModePrompt(
            resolveModePrompt(mode, prefs),
            synthesizedRules.mapNotNull { it.synthesizedRule }
        )
        val matchedToken = promptTokens(prompt).firstOrNull { content.contains(it) }

        if (matchedToken == null) {
            Log.d(TAG, "$SEMANTIC Notification muted under $mode mode")
            return ClassificationResult(
                isUrgent = false,
                explanation = "$SEMANTIC Did not contain urgent prompt tokens for $mode mode; muted as casual communication."
            )
        }
        Log.d(TAG, "$SEMANTIC Matched $mode prompt token: $matchedToken")
        return ClassificationResult(
            isUrgent = true,
            explanation = "$SEMANTIC Matched active $mode system prompt token '$matchedToken'."
        )
    }

    /** The user-edited prompt if set, else the out-of-the-box prompt, else a hard-coded default. */
    private fun resolveModePrompt(mode: String, prefs: SharedPreferences): String {
        val builtIn = BUILT_IN_MODES.firstOrNull { mode.contains(it.matchText, ignoreCase = true) }
            ?: return prefs.getString("custom_mode_prompt_$mode", DEFAULT_CUSTOM_PROMPT) ?: DEFAULT_CUSTOM_PROMPT

        val default = PromptManager.getAllOotbPrompts(context)[builtIn.name] ?: builtIn.fallbackPrompt
        return prefs.getString("prompt_${builtIn.name}", default) ?: ""
    }

    private fun promptTokens(prompt: String): List<String> =
        prompt.lowercase().split(",", " ", ";", "[", "]", ":").map { it.trim() }.filter { it.isNotEmpty() }

    private fun actionLabel(isUrgent: Boolean) = if (isUrgent) "Shown" else "Muted"

    /** An out-of-the-box mode: [matchText] is looked for in the active mode name (so "Focus" matches "Deep Work / Focus"). */
    private class BuiltInMode(val matchText: String, val name: String, val fallbackPrompt: String)

    companion object {
        private const val TAG = "NotificationFilter"
        private const val PREFS_NAME = "FocusRefinePrefs"
        private const val PREF_USE_AI = "use_ai_model"
        private const val PREF_FILTER_STRENGTH = "filter_strength"

        private const val SEMANTIC = "🔍 [Semantic Search]"
        private const val AI_PIPELINE = "🤖 [AI Pipeline]"

        private const val DEFAULT_CUSTOM_PROMPT = "urgent, call, emergency, navigation, meeting"

        /** Checked in order; the first whose [BuiltInMode.matchText] is in the mode name wins. */
        private val BUILT_IN_MODES = listOf(
            BuiltInMode("Commute", "Commute", "navigation, maps, turn left, turn right, traffic, call, vip, boss, route"),
            BuiltInMode("Focus", "Deep Work / Focus", "urgent, call, boss, meeting, build, outage, deadline"),
            BuiltInMode("Sleep", "Sleeping / Rest", "emergency, family, hospital, call, alarm"),
            BuiltInMode("DND", "Do Not Disturb (DND)", "emergency, hospital, 911, critical")
        )
    }
}
