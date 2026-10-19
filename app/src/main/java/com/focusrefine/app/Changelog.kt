package com.focusrefine.app

import android.content.Context
import org.json.JSONObject

/** One group of entries in a release: New, Improved, Fixed, Privacy. */
data class ChangeSection(val heading: String, val items: List<String>)

data class Release(val version: String, val date: String, val title: String, val sections: List<ChangeSection>)

/**
 * The change log shown in the "What's new" screen. The text lives in `assets/changelog.json`, newest release first;
 * add an entry there whenever a user-visible change ships (a unit test checks that the newest entry matches the app's
 * versionName, so a version bump cannot be forgotten).
 */
object Changelog {

    fun parse(json: String): List<Release> {
        val releases = JSONObject(json).getJSONArray("releases")
        return (0 until releases.length()).map { i ->
            val r = releases.getJSONObject(i)
            val sections = r.getJSONArray("sections")
            Release(
                version = r.getString("version"),
                date = r.getString("date"),
                title = r.optString("title"),
                sections = (0 until sections.length()).map { j ->
                    val s = sections.getJSONObject(j)
                    val items = s.getJSONArray("items")
                    ChangeSection(s.getString("heading"), (0 until items.length()).map { items.getString(it) })
                }
            )
        }
    }

    /** Reads the bundled change log; empty if the file is missing or malformed (the screen then says so). */
    fun load(context: Context): List<Release> = try {
        parse(context.assets.open("changelog.json").bufferedReader().use { it.readText() })
    } catch (e: Exception) {
        emptyList()
    }
}
