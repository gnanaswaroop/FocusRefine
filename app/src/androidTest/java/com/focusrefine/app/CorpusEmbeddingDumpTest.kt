package com.focusrefine.app

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.DataOutputStream
import java.io.File

/**
 * Not a check: writes the sentence embeddings and built-in margins of the golden, holdout and corpus datasets to
 * `files/corpus_embeddings.bin` so the personal-model simulation (a fast JVM test) can run thousands of experiments
 * with the real model's numbers. Pull it with:
 *   adb exec-out run-as com.focusrefine.app cat files/corpus_embeddings.bin > app/build/corpus/corpus_embeddings.bin
 * Layout: int magic, int dim, int files; per file: int count, then per item: dim floats, 5 double margins
 * (Commute, Focus, DND, Sleep, Workout at Balanced strength). Files in the order golden, holdout, corpus.
 */
class CorpusEmbeddingDumpTest {

    @Test
    fun dumpEmbeddings() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        context.getSharedPreferences("FocusRefinePrefs", Context.MODE_PRIVATE).edit().putBoolean("use_ai_model", true).commit()
        val engine = EmbeddingClassifier.shared(context)
        assertNotNull(engine)
        engine!!
        engine.warmUp()
        val modes = listOf("Commute", "Focus", "DND", "Sleep", "Workout")
        val out = File(context.filesDir, "corpus_embeddings.bin")
        DataOutputStream(out.outputStream().buffered()).use { o ->
            val names = listOf("golden_notifications.json", "holdout_notifications.json", "corpus_notifications.json")
            o.writeInt(0x434F5250); o.writeInt(engine.embed("dimension probe").size); o.writeInt(names.size)
            for (name in names) {
                val json = instrumentation.context.assets.open(name).bufferedReader().readText()
                val items = JSONArray(json)
                o.writeInt(items.length())
                for (i in 0 until items.length()) {
                    val it = items.getJSONObject(i)
                    val v = engine.embed("${it.getString("title")} ${it.getString("text")}".lowercase())
                    for (x in v) o.writeFloat(x)
                    for (m in modes) o.writeDouble(engine.baseMargin(v, m))
                }
            }
        }
    }
}
