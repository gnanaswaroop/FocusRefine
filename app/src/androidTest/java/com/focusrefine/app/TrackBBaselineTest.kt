package com.focusrefine.app

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The current embedding engine on the same held-out items the Track B model is scored on, so the two can be compared
 * on identical data. Reads /data/local/tmp/focusrefine/trackb/fixture.json (see ml/export/README.md); skipped when it is
 * not there. Logs to tag TRACKB_BENCH. Read-only: it does not touch the notification database.
 */
@RunWith(AndroidJUnit4::class)
class TrackBBaselineTest {
    private val fixture = File("/data/local/tmp/focusrefine/trackb/fixture.json")

    @Test
    fun theCurrentEngineScoresTheSameHoldoutItems() {
        assumeTrue("push the Track B fixture to ${fixture.parent} first", fixture.exists())
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("FocusRefinePrefs", Context.MODE_PRIVATE).edit()
            .putBoolean("use_ai_model", true).putInt("filter_strength", 1).commit()
        val engine = EmbeddingClassifier.shared(context)
        assumeTrue("the embedding engine did not load on this device", engine != null)

        val json = JSONObject(fixture.readText())
        val modes = json.getJSONArray("modes")
        val items = json.getJSONArray("items")
        val score = TrackBScore(modes.length())
        var unsupported = 0
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            val content = "${item.getString("title")} ${item.getString("text")}".lowercase()
            val truth = item.getJSONArray("urgentIn")
            for (k in 0 until modes.length()) {
                val verdict = engine!!.classify(content, modes.getString(k), 1)
                if (verdict == null) unsupported++
                score.add(item.getString("lang"), k, verdict?.isUrgent ?: false, truth.getBoolean(k))
            }
        }
        report("embedding", modes, score)
        Log.i("TRACKB_BENCH", "engine=embedding unsupportedModes=$unsupported")
    }
}

/** One log line for the overall score, one per mode, and one per language, under the given engine name. */
fun report(engine: String, modes: JSONArray, score: TrackBScore) {
    val all = TrackBScore.ALL
    Log.i("TRACKB_BENCH", "engine=$engine lang=$all macroF1=${"%.3f".format(score.macro(all))} " +
        "fp=${score.falsePositives(all)} fn=${score.falseNegatives(all)}")
    for (k in 0 until modes.length()) {
        Log.i("TRACKB_BENCH", "engine=$engine mode=${modes.getString(k)} f1=${"%.3f".format(score.perMode(all)[k])}")
    }
    for (lang in score.languages()) {
        Log.i("TRACKB_BENCH", "engine=$engine lang=$lang macroF1=${"%.3f".format(score.macro(lang))} " +
            "fp=${score.falsePositives(lang)} fn=${score.falseNegatives(lang)}")
    }
}
