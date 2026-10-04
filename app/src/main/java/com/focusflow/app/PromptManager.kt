package com.focusflow.app

import android.content.Context
import android.util.Log
import org.json.JSONObject

object PromptManager {
    private const val TAG = "PromptManager"

    fun getAllOotbPrompts(context: Context): Map<String, String> {
        val map = mutableMapOf<String, String>()
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
                map[key] = json.getString(key)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load OOTB prompts from assets: ${e.message}")
            map["Commute"] = "navigation, maps, turn left, turn right, traffic, call, vip, boss"
            map["Do Not Disturb (DND)"] = "emergency, hospital, 911, critical"
        }
        return map
    }

    fun getOotbPrompt(context: Context, modeKey: String, defaultVal: String): String {
        return getAllOotbPrompts(context)[modeKey] ?: defaultVal
    }
}
