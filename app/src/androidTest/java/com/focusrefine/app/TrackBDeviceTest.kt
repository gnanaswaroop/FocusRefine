package com.focusrefine.app

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.tensorflow.lite.Interpreter
import java.io.File
import kotlin.math.abs

/**
 * Track B on the device: the exported TFLite model over the held-out items, scored the same way as on the host.
 * Reads the model and fixture from /data/local/tmp/focusrefine/trackb (see ml/export/README.md); skipped when they
 * are not there. Logs to tag TRACKB_BENCH. Not part of the app: the production classifier does not use this model.
 */
@RunWith(AndroidJUnit4::class)
class TrackBDeviceTest {
    private val dir = File("/data/local/tmp/focusrefine/trackb")

    @Test
    fun theExportedModelReproducesTheHostScoresOnTheHoldout() {
        val model = File(dir, "model.tflite")
        val fixture = File(dir, "fixture.json")
        assumeTrue("push the Track B artifacts to ${dir.path} first", model.exists() && fixture.exists())

        val json = JSONObject(fixture.readText())
        val modes = json.getJSONArray("modes")
        val modeCount = modes.length()
        val maxLen = json.getInt("maxlen")
        val items = json.getJSONArray("items")

        val tp = IntArray(modeCount)
        val fp = IntArray(modeCount)
        val fn = IntArray(modeCount)
        var maxDiff = 0f
        var inferenceNs = 0L
        Interpreter(model).use { interpreter ->
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                val ids = item.getJSONArray("ids")
                val input = arrayOf(IntArray(maxLen) { ids.getInt(it) })
                val output = arrayOf(FloatArray(modeCount))
                val start = System.nanoTime()
                interpreter.run(input, output)
                inferenceNs += System.nanoTime() - start

                val expected = item.getJSONArray("logits")
                val truth = item.getJSONArray("urgentIn")
                for (k in 0 until modeCount) {
                    val logit = output[0][k]
                    maxDiff = maxOf(maxDiff, abs(logit - expected.getDouble(k).toFloat()))
                    val predicted = logit >= 0f // sigmoid >= 0.5, as on the host
                    val actual = truth.getBoolean(k)
                    when {
                        predicted && actual -> tp[k]++
                        predicted && !actual -> fp[k]++
                        !predicted && actual -> fn[k]++
                    }
                }
            }
        }

        val f1 = DoubleArray(modeCount) { k ->
            val denominator = 2 * tp[k] + fp[k] + fn[k]
            if (denominator == 0) 0.0 else 2.0 * tp[k] / denominator
        }
        val macro = f1.average()
        Log.i("TRACKB_BENCH", "items=${items.length()} macroF1=${"%.3f".format(macro)}")
        for (k in 0 until modeCount) {
            Log.i("TRACKB_BENCH", "${modes.getString(k)} f1=${"%.3f".format(f1[k])} fp=${fp[k]} fn=${fn[k]}")
        }
        Log.i("TRACKB_BENCH", "maxLogitDiffVsHost=${"%.2e".format(maxDiff)} " +
            "avgInferenceMs=${"%.2f".format(inferenceNs / 1e6 / items.length())}")

        assertTrue("device logits drift from the host: $maxDiff", maxDiff < 1e-3f)
        assertTrue("holdout macro F1 $macro is below the host's 0.70", macro >= 0.70)
    }
}
