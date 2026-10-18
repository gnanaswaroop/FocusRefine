package com.focusrefine.app

import android.content.Context
import android.util.Log
import org.json.JSONObject

object PromptManager {
    private const val TAG = "PromptManager"

    data class PromptConfig(val prompt: String, val enabled: Boolean)

    fun getAllOotbConfigs(context: Context): Map<String, PromptConfig> {
        val map = mutableMapOf<String, PromptConfig>()
        try {
            val inputStream = context.assets.open("prompts.json")
            val size = inputStream.available()
            val buffer = ByteArray(size)
            inputStream.read(buffer)
            inputStream.close()
            val jsonStr = String(buffer, Charsets.UTF_8)
            val json = JSONObject(jsonStr)
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = json.get(key)
                if (value is JSONObject) {
                    val prompt = value.optString("prompt", "")
                    val enabled = value.optBoolean("enabled", false)
                    map[key] = PromptConfig(prompt, enabled)
                } else if (value is String) {
                    map[key] = PromptConfig(value, false)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load OOTB prompts from assets: ${e.message}")
        }
        return map
    }

    fun getAllOotbPrompts(context: Context): Map<String, String> {
        val configs = getAllOotbConfigs(context)
        val map = mutableMapOf<String, String>()
        for ((k, v) in configs) {
            map[k] = v.prompt
        }
        return map
    }

    fun getOotbPrompt(context: Context, modeKey: String, defaultVal: String): String {
        return getAllOotbPrompts(context)[modeKey] ?: defaultVal
    }

    fun isOotbModeEnabled(context: Context, modeKey: String): Boolean {
        val configs = getAllOotbConfigs(context)
        return configs[modeKey]?.enabled ?: false
    }

    fun getDefaultModeName(context: Context): String {
        val configs = getAllOotbConfigs(context)
        return configs.entries.firstOrNull { it.value.enabled }?.key
            ?: configs.keys.firstOrNull()
            ?: "Default"
    }

    fun getEnrichedModePrompt(basePrompt: String, synthesizedRules: List<String>): String {
        if (synthesizedRules.isEmpty()) return basePrompt
        val rulesFormatted = synthesizedRules.joinToString("; ")
        return "$basePrompt. [USER AI PREFERENCES: $rulesFormatted]"
    }
}
