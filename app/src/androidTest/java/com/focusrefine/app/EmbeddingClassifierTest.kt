package com.focusrefine.app

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Checks the Track B model itself (asset present, loads on this ABI, sensible verdicts, app context used).
 * Accuracy over the held-out set is measured by TrackBEngineTest and scripts/replay_golden.py.
 * Needs a device or emulator; it only reads the app preferences (and rewrites `embedding_engine_loaded`).
 */
class EmbeddingClassifierTest {

    private var engine: EmbeddingClassifier? = null

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("FocusRefinePrefs", android.content.Context.MODE_PRIVATE)
        prefs.edit().putBoolean("use_ai_model", true).commit() // other tests switch it off
        engine = EmbeddingClassifier.shared(context)
    }

    @Test
    fun modelLoadsOnThisDevice() {
        assertNotNull("track_b.tflite should load on every supported ABI", engine)
    }

    @Test
    fun urgentMessageIsShownAndRoutineIsMuted() {
        val e = engine!!
        val urgent = e.classify("Doctor", "Urgent medical notification, please call the clinic now", "com.whatsapp", "Sleeping / Rest", 1)!!
        val routine = e.classify("Mega sale", "Special offer: 30% discount on your next purchase", "com.amazon.mShop.android.shopping", "Sleeping / Rest", 1)!!
        assertTrue("urgent score ${urgent.score} vs ${urgent.threshold}", urgent.isUrgent)
        assertFalse("routine score ${routine.score} vs ${routine.threshold}", routine.isUrgent)
        assertTrue(urgent.score > routine.score)
    }

    @Test
    fun everyBuiltInModeHasAVerdict() {
        val e = engine!!
        listOf("Commute", "Deep Work / Focus", "Do Not Disturb (DND)", "Sleeping / Rest", "Workout / Gym").forEach { mode ->
            assertNotNull("no verdict for $mode", e.classify("Hello", "How are you", null, mode, 1))
        }
    }

    @Test
    fun unknownAppStillGetsAVerdict() {
        val verdict = engine!!.classify("Hello", "How are you", "com.example.not.in.catalogue", "Commute", 1)
        assertNotNull(verdict)
    }

    @Test
    fun customModeAbstains() {
        assertNull(engine!!.classify("Server", "urgent: server is down", null, "My Custom Mode", 1))
    }

    @Test
    fun filteringStrengthShiftsTheThreshold() {
        val e = engine!!
        val gentle = e.classify("Heart rate", "Heart rate zone reached", "com.google.android.apps.fitness", "Workout / Gym", 0)!!
        val balanced = e.classify("Heart rate", "Heart rate zone reached", "com.google.android.apps.fitness", "Workout / Gym", 1)!!
        val strict = e.classify("Heart rate", "Heart rate zone reached", "com.google.android.apps.fitness", "Workout / Gym", 2)!!
        assertEquals(balanced.score, gentle.score, 1e-9)
        assertEquals(balanced.score, strict.score, 1e-9)
        assertTrue(gentle.threshold < balanced.threshold)
        assertTrue(balanced.threshold < strict.threshold)
    }

    @Test
    fun usingAClosedClassifierFailsFast() {
        val e = EmbeddingClassifier.create(InstrumentationRegistry.getInstrumentation().targetContext)!! // a separate engine: the shared one is never closed
        e.close()
        try {
            e.classify("anything", "", null, "Commute", 1)
            org.junit.Assert.fail("expected the closed classifier to refuse work")
        } catch (expected: IllegalStateException) {
            // classify() must not touch freed native memory
        }
    }
}
