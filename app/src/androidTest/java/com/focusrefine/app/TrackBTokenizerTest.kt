package com.focusrefine.app

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONArray
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * The app's tokenizer against SentencePiece's own ids for the held-out texts (tokenizer_check.json, pushed with the
 * fixture). Logs to tag TRACKB_BENCH.
 */
@RunWith(AndroidJUnit4::class)
class TrackBTokenizerTest {
    private val check = File("/data/local/tmp/focusrefine/trackb/tokenizer_check.json")

    @Test
    fun theOnDeviceTokenizerMatchesSentencePiece() {
        assumeTrue("push tokenizer_check.json to ${check.parent} first", check.exists())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val tokenizer = TrackBTokenizer.fromJson(context.assets.open("track_b_vocab.json").bufferedReader().use { it.readText() })
        val rows = JSONArray(check.readText())
        var same = 0
        for (r in 0 until rows.length()) {
            val row = rows.getJSONObject(r)
            val expected = row.getJSONArray("ids").let { ids -> IntArray(ids.length()) { ids.getInt(it) } }
            if (tokenizer.encode(row.getString("text")).contentEquals(expected)) same++
        }
        Log.i("TRACKB_BENCH", "engine=tokenizer identical=$same of ${rows.length()}")
        assertTrue("only $same of ${rows.length()} texts tokenise like SentencePiece", same >= rows.length() * 0.99)
    }
}
