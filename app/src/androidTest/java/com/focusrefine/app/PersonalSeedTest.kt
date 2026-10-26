package com.focusrefine.app

import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Not a test: a seeding hook for scripts/replay_personal.py. When `files/corrections.json` exists in the app's
 * private storage ([{title, text, mode, urgent}]), it teaches the personal model each correction exactly as the
 * detail screen would, then deletes the file. Without that file it is skipped, so normal runs ignore it.
 */
class PersonalSeedTest {

    @Test
    fun seedCorrectionsFromFile() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val file = File(context.filesDir, "corrections.json")
        assumeTrue("no corrections.json to seed", file.exists())

        context.getSharedPreferences("FocusRefinePrefs", android.content.Context.MODE_PRIVATE).edit().putBoolean("use_ai_model", true).commit()
        val list = JSONArray(file.readText())
        file.delete()
        val engine = EmbeddingClassifier.shared(context)
        assertTrue("embedding model did not load", engine != null)
        engine!!
        try {
            var stored = 0
            for (i in 0 until list.length()) {
                val c = list.getJSONObject(i)
                val n = engine.learn(c.getString("title"), c.getString("text"), null, c.getString("mode"), c.getBoolean("urgent"))
                if (n != null) stored = n
            }
            android.util.Log.i("PERS_SEED", "stored $stored correction(s) from ${list.length()}")
            assertTrue("nothing was stored", stored > 0 || list.length() == 0)
        } finally {
        }
    }
}
