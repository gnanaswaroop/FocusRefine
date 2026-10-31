package com.focusrefine.app

import org.json.JSONArray

/**
 * Package -> display name and category, the app context the Track B model was trained with
 * (`assets/track_b_apps.json`, from ml/data/apps.tsv). A package that is not listed gets empty fields, which the
 * model reads as "no app context" and scores noticeably less accurately.
 */
class AppCatalogue private constructor(private val byPackage: Map<String, Entry>) {

    class Entry(val name: String, val category: String)

    /** Category and name for [packageName], or empty strings when it is unknown. */
    fun lookup(packageName: String?): Entry = packageName?.let { byPackage[it] } ?: EMPTY

    companion object {
        private val EMPTY = Entry("", "")

        fun fromJson(json: String): AppCatalogue {
            val array = JSONArray(json)
            val map = HashMap<String, Entry>(array.length() * 2)
            for (i in 0 until array.length()) {
                val row = array.getJSONObject(i)
                map[row.getString("package")] = Entry(row.getString("name"), row.getString("category"))
            }
            return AppCatalogue(map)
        }
    }
}
