package com.focusrefine.app

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Checks the embedding model itself (asset present, loads on this ABI, sensible verdicts).
 * Accuracy over the whole datasets is measured by EmbedderBenchmarkTest and scripts/replay_golden.py.
 * Needs a device or emulator; it only reads the app preferences (and rewrites `embedding_engine_loaded`).
 */
class EmbeddingClassifierTest {

    private var engine: EmbeddingClassifier? = null

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("FocusRefinePrefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putBoolean("use_ai_model", true).commit() // other tests switch it off
        engine = EmbeddingClassifier.create(context)
    }

    @After
    fun tearDown() {
        engine?.close()
    }

    @Test
    fun modelLoadsOnThisDevice() {
        assertNotNull("text_embedder.tflite should load on every supported ABI", engine)
    }

    @Test
    fun urgentMessageIsShownAndRoutineIsMuted() {
        val e = engine!!
        val urgent = e.classify("doctor: urgent medical notification, please call", "Sleeping / Rest", 1)!!
        val routine = e.classify("special offer: 30% discount on your next purchase", "Sleeping / Rest", 1)!!
        assertTrue("urgent score ${urgent.score} vs ${urgent.threshold}", urgent.isUrgent)
        assertFalse("routine score ${routine.score} vs ${routine.threshold}", routine.isUrgent)
        assertTrue(urgent.score > routine.score)
    }

    @Test
    fun everyBuiltInModeHasAnchors() {
        val e = engine!!
        listOf("Commute", "Deep Work / Focus", "Do Not Disturb (DND)", "Sleeping / Rest", "Workout / Gym").forEach { mode ->
            assertNotNull("no anchors for $mode", e.classify("hello", mode, 1))
        }
    }

    @Test
    fun customModeAbstains() {
        assertNull(engine!!.classify("urgent: server is down", "My Custom Mode", 1))
    }

    @Test
    fun filteringStrengthShiftsTheThreshold() {
        val e = engine!!
        val gentle = e.classify("heart rate zone reached", "Workout / Gym", 0)!!
        val balanced = e.classify("heart rate zone reached", "Workout / Gym", 1)!!
        val strict = e.classify("heart rate zone reached", "Workout / Gym", 2)!!
        assertEquals(balanced.score, gentle.score, 1e-9)
        assertEquals(balanced.score, strict.score, 1e-9)
        assertTrue(gentle.threshold < balanced.threshold)
        assertTrue(balanced.threshold < strict.threshold)
    }

    @Test
    fun usingAClosedClassifierFailsFast() {
        val e = engine!!
        engine = null // tearDown must not close it twice
        e.close()
        try {
            e.classify("anything", "Commute", 1)
            org.junit.Assert.fail("expected the closed classifier to refuse work")
        } catch (expected: IllegalStateException) {
            // classify() must not touch freed native memory
        }
    }
}
