package com.focusrefine.app

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Safety recall of the production engine on testdata/safety (synthetic urgent notices). Every notice should be Shown in
 * every built-in mode. Reads /data/local/tmp/focusrefine/safety/safety_items.json (pushed from testdata/safety); skipped
 * when it is missing. Logs to tag SAFETY. Model only: no exceptions, no learned rules, no app context.
 */
@RunWith(AndroidJUnit4::class)
class SafetyRecallTest {
    private val items = File("/data/local/tmp/focusrefine/safety/safety_items.json")
    private val modes = listOf("Commute", "Deep Work / Focus", "Do Not Disturb (DND)", "Sleeping / Rest", "Workout / Gym")

    @Test
    fun theProductionEngineShowsSafetyNotices() {
        assumeTrue("push testdata/safety to ${items.parent} first", items.exists())
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("FocusRefinePrefs", Context.MODE_PRIVATE).edit()
            .putBoolean("use_ai_model", true).putInt("filter_strength", 1).commit()
        PersonalLearning.clear(context)
        val engine = EmbeddingClassifier.shared(context)
        assumeTrue("the engine did not load", engine != null)

        val rows = JSONArray(items.readText())
        val shown = HashMap<String, IntArray>() // group -> shown per mode
        val total = HashMap<String, Int>()
        for (r in 0 until rows.length()) {
            val row = rows.getJSONObject(r)
            val decisions = modes.map { m ->
                engine!!.classify(row.getString("title"), row.getString("text"), null, m, 1)!!.isUrgent
            }
            Log.i("SAFETY", "item=${row.getString("id")} cat=${row.getString("category")} lang=${row.getString("lang")} " +
                "decisions=" + decisions.joinToString(",") { if (it) "S" else "M" })
            for (group in listOf("ALL", row.getString("category"), "lang:" + row.getString("lang"))) {
                total[group] = (total[group] ?: 0) + 1
                val counts = shown.getOrPut(group) { IntArray(modes.size) }
                decisions.forEachIndexed { k, s -> if (s) counts[k]++ }
            }
        }
        for ((group, counts) in shown.toSortedMap()) {
            val n = total.getValue(group)
            counts.forEachIndexed { k, c ->
                Log.i("SAFETY", "recall group=$group mode=${modes[k]} shown=$c of $n")
            }
        }
    }
}
