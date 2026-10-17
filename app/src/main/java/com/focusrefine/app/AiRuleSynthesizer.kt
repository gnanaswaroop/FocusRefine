package com.focusrefine.app

object AiRuleSynthesizer {

    private val STOP_WORDS = setOf(
        "a", "an", "the", "is", "are", "was", "were", "be", "been", "being",
        "in", "on", "at", "to", "for", "of", "with", "by", "from", "up", "about",
        "into", "over", "after", "and", "or", "but", "so", "if", "not", "no",
        "can", "will", "just", "should", "now", "it", "this", "that", "my", "your",
        "we", "you", "he", "she", "they", "me", "him", "her", "them", "us", "i", "want"
    )

    /**
     * Synthesizes a generalized, human-readable dynamic AI rule from notification details and user action.
     * Extracts significant semantic tokens dynamically without relying on hardcoded app names or keyword examples.
     *
     * @param packageName Package name of the app (e.g. "com.whatsapp")
     * @param title Title of the notification
     * @param text Body text of the notification
     * @param isUrgent true if user set to Shown (Allowed), false if set to Muted (Suppressed)
     * @param activeMode Currently active mode
     * @return Dynamic synthesized rule string, e.g. "[MUTE] Whatsapp: grab coffee later"
     */
    fun synthesizeRule(
        packageName: String,
        title: String?,
        text: String?,
        isUrgent: Boolean,
        activeMode: String
    ): String {
        val appName = extractAppName(packageName)
        val actionTag = if (isUrgent) "[ALLOW]" else "[MUTE]"

        val titleTokens = extractSignificantTokens(title ?: "")
        val textTokens = extractSignificantTokens(text ?: "")

        val keyTokens = (titleTokens + textTokens).distinct().take(4)

        return if (keyTokens.isNotEmpty()) {
            val tokenPhrase = keyTokens.joinToString(" ")
            "$actionTag $appName: $tokenPhrase"
        } else {
            val cleanTitle = title?.takeIf { it.isNotBlank() } ?: "alerts"
            "$actionTag $appName: $cleanTitle"
        }
    }

    /**
     * Checks if a notification semantically matches a synthesized rule using dynamic token overlap.
     */
    fun matchesRule(rule: String, title: String?, text: String?): Boolean {
        val content = "${title ?: ""} ${text ?: ""}".lowercase()
        val ruleBody = rule.substringAfter(":").ifEmpty { rule }
        val ruleTokens = extractSignificantTokens(ruleBody)

        if (ruleTokens.isEmpty()) return false

        val matchedCount = ruleTokens.count { token -> content.contains(token) }
        val matchRatio = matchedCount.toDouble() / ruleTokens.size.toDouble()

        return matchedCount >= 2 || (ruleTokens.size == 1 && matchedCount == 1) || matchRatio >= 0.5
    }

    private fun extractAppName(packageName: String): String {
        val segments = packageName.split(".").filter { it.length > 2 && it !in setOf("com", "org", "net", "android", "apps", "app") }
        val rawName = segments.lastOrNull() ?: packageName.substringAfterLast(".")
        return rawName.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
    }

    private fun extractSignificantTokens(input: String): List<String> {
        return input.lowercase()
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split("\\s+".toRegex())
            .map { it.trim() }
            .filter { it.length >= 3 && it !in STOP_WORDS }
    }
}
