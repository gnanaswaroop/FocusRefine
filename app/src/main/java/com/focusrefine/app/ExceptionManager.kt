package com.focusrefine.app

import android.content.Context
import android.util.Log
import org.json.JSONArray

object ExceptionManager {
    private const val TAG = "ExceptionManager"

    data class ExceptionConfig(
        val id: String,
        val title: String,
        val description: String,
        val icon: String,
        val prefKey: String,
        val defaultEnabled: Boolean,
        val keywords: List<String>
    )

    fun getAllExceptions(context: Context): List<ExceptionConfig> {
        val list = mutableListOf<ExceptionConfig>()
        try {
            val inputStream = context.assets.open("exceptions.json")
            val size = inputStream.available()
            val buffer = ByteArray(size)
            inputStream.read(buffer)
            inputStream.close()
            val jsonStr = String(buffer, Charsets.UTF_8)
            val jsonArray = JSONArray(jsonStr)

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                val keywordsArray = obj.optJSONArray("keywords")
                val keywordsList = mutableListOf<String>()
                if (keywordsArray != null) {
                    for (j in 0 until keywordsArray.length()) {
                        keywordsList.add(keywordsArray.getString(j))
                    }
                }

                list.add(
                    ExceptionConfig(
                        id = obj.optString("id"),
                        title = obj.optString("title"),
                        description = obj.optString("description"),
                        icon = obj.optString("icon", "⚡"),
                        prefKey = obj.optString("prefKey"),
                        defaultEnabled = obj.optBoolean("defaultEnabled", true),
                        keywords = keywordsList
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load exceptions.json: ${e.message}")
        }
        return list
    }
}
