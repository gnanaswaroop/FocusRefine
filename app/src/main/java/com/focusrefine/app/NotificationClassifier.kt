package com.focusrefine.app

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.text.textclassifier.TextClassifier

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
    private val textClassifier: TextClassifier?
) {

    /**
     * Precedence, first match wins:
     * 1. a user learned rule
     * 2. an AI-synthesized rule
     * 3. an enabled exception (navigation, calls, OTP, ...)
     * 4. the MediaPipe model, when it is available and reports an "urgent" category
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
        Log.d(TAG, "$SEMANTIC Matched AI Synthesized rule '$ruleText': isUrgent = ${match.isUrgent}")
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

    // ---- 4. MediaPipe model -----------------------------------------------------------------

    /** Returns null when the model is off, unavailable, failed, or has no "urgent" category. */
    private fun classifyWithModel(content: String, mode: String, prefs: SharedPreferences): ClassificationResult? {
        val classifier = textClassifier ?: return null
        if (!prefs.getBoolean(PREF_USE_AI, true)) return null

        val threshold = urgencyThreshold(prefs.getInt(PREF_FILTER_STRENGTH, 1))
        val scores = try {
            urgentScores(classifier, content)
        } catch (e: Exception) {
            Log.e(TAG, "MediaPipe AI Pipeline classification error: ${e.message}")
            return null
        }
        val lastScore = scores.lastOrNull() ?: return null

        return ClassificationResult(
            isUrgent = scores.any { it >= threshold },
            explanation = modelExplanation(lastScore, threshold, mode)
        )
    }

    private fun urgentScores(classifier: TextClassifier, content: String): List<Float> =
        classifier.classify(content).classificationResult().classifications()
            .flatMap { it.categories() }
            .filter { it.categoryName().equals("urgent", ignoreCase = true) }
            .map { it.score() }

    /** Filtering strength: 0 = Gentle, 1 = Balanced (default), 2 = Strict. */
    private fun urgencyThreshold(filterStrength: Int): Float = when (filterStrength) {
        0 -> 0.4f
        2 -> 0.8f
        else -> 0.6f
    }

    private fun modelExplanation(score: Float, threshold: Float, mode: String): String {
        val scorePercent = (score * 100).toInt()
        val thresholdPercent = (threshold * 100).toInt()
        return if (score >= threshold) {
            "$AI_PIPELINE MediaPipe model classified notification as urgent with $scorePercent% confidence (exceeded $mode threshold $thresholdPercent%)."
        } else {
            "$AI_PIPELINE MediaPipe model confidence $scorePercent% was below $mode urgency threshold ($thresholdPercent%); muted as routine."
        }
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

        /** Returns the MediaPipe classifier, or null when disabled by the user or unavailable on this device. */
        fun createTextClassifier(context: Context): TextClassifier? {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (!prefs.getBoolean(PREF_USE_AI, true)) {
                Log.d(TAG, "MediaPipe AI Model disabled by user settings")
                return null
            }
            return try {
                val baseOptions = BaseOptions.builder().setModelAssetPath("text_classifier.tflite").build()
                val options = TextClassifier.TextClassifierOptions.builder().setBaseOptions(baseOptions).build()
                TextClassifier.createFromOptions(context, options).also {
                    Log.d(TAG, "MediaPipe TextClassifier initialized successfully")
                }
            } catch (e: Throwable) {
                Log.d(TAG, "MediaPipe model asset or JNI native library not found (x86_64/emulator); using semantic prompt heuristic engine: ${e.message}")
                null
            }
        }
    }
}
