package com.focusrefine.app

import android.content.Context
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The production engine on the held-out Track B items (synthetic, never used for training), with the app context taken
 * from each item's package, as the filter service sees it. Reads /data/local/tmp/focusrefine/trackb/fixture.json
 * (see ml/export/README.md on the training branch); skipped when it is not there. Logs to tag TRACKB_BENCH.
 * Clears the personal model first, so the scores come from the built-in model alone.
 */
@RunWith(AndroidJUnit4::class)
class TrackBEngineTest {
    private val fixture = File("/data/local/tmp/focusrefine/trackb/fixture.json")

    @Test
    fun theProductionEngineScoresTheHeldOutItems() {
        assumeTrue("push the Track B fixture to ${fixture.parent} first", fixture.exists())
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("FocusRefinePrefs", Context.MODE_PRIVATE).edit()
            .putBoolean("use_ai_model", true).putInt("filter_strength", 1).commit()
        PersonalLearning.clear(context)
        val engine = EmbeddingClassifier.shared(context)
        assumeTrue("the engine did not load on this device", engine != null)

        val json = JSONObject(fixture.readText())
        val modes = json.getJSONArray("modes")
        val items = json.getJSONArray("items")
        val score = TrackBScore(modes.length())
        var unscored = 0
        for (i in 0 until items.length()) {
            val item = items.getJSONObject(i)
            val truth = item.getJSONArray("urgentIn")
            for (k in 0 until modes.length()) {
                val verdict = engine!!.classify(item.getString("title"), item.getString("text"), item.getString("package"), modes.getString(k), 1)
                if (verdict == null) unscored++
                score.add(item.getString("lang"), k, verdict?.isUrgent ?: false, truth.getBoolean(k))
            }
        }
        val all = TrackBScore.ALL
        Log.i("TRACKB_BENCH", "engine=production lang=$all macroF1=${"%.3f".format(score.macro(all))} " +
            "fp=${score.falsePositives(all)} fn=${score.falseNegatives(all)} unscoredModes=$unscored")
        for (lang in score.languages()) {
            Log.i("TRACKB_BENCH", "engine=production lang=$lang macroF1=${"%.3f".format(score.macro(lang))} " +
                "fp=${score.falsePositives(lang)} fn=${score.falseNegatives(lang)}")
        }
        assertTrue("holdout macro F1 ${score.macro(all)} is below the host's 0.70 (tolerance 0.02)", score.macro(all) >= 0.68)
    }
}
