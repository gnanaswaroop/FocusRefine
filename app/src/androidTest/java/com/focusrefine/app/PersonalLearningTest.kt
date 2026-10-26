package com.focusrefine.app

import android.content.Context
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/** The real feedback path: a correction is stored on the device and changes later decisions. */
class PersonalLearningTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val mode = "Sleeping / Rest"
    private val title = "Greenwood School"
    private val text = "Early dismissal today due to a safety incident"

    @Before
    fun setUp() {
        context.getSharedPreferences("FocusRefinePrefs", Context.MODE_PRIVATE).edit().putBoolean("use_ai_model", true).commit()
        PersonalLearning.clear(context)
    }

    @After
    fun tearDown() = PersonalLearning.clear(context)

    private fun verdict(engine: EmbeddingClassifier, t: String = title, x: String = text) = engine.classify(t, x, null, mode, 1)!!

    @Test
    fun aCorrectionChangesTheDecisionAndForgettingRestoresIt() {
        val engine = EmbeddingClassifier.shared(context)
        assertNotNull(engine); engine!!
        try {
            val before = verdict(engine)
            assertFalse("test premise: the built-in model mutes this (score ${before.score} vs ${before.threshold})", before.isUrgent)

            assertEquals(1, PersonalLearning.record(context, title, text, mode, urgent = true))

            val after = verdict(engine) // same running engine: it must notice the new model
            assertTrue("adjustment ${after.adjustment}", after.isUrgent && after.adjustment > 0)
            assertEquals("the built-in score is untouched", before.score, after.score, 1e-9)

            val unrelated = verdict(engine, "Lightning deal", "amazon lightning deal: 60% off smart watches")
            assertFalse("an unrelated promo must stay muted", unrelated.isUrgent)

            PersonalLearning.forget(context, title, text)
            val forgotten = verdict(engine)
            assertFalse(forgotten.isUrgent)
            assertEquals(0.0, forgotten.adjustment, 1e-9)
            assertEquals(0, PersonalLearning.correctionCount(context))
        } finally {
            // the shared engine stays open
        }
    }

    @Test
    fun aShownNotificationCanBeLearnedAsMutedAndBack() {
        val shownTitle = "Apple Watch"
        val shownText = "High heart rate notification: 140 BPM while inactive"
        val engine = EmbeddingClassifier.shared(context)!!
        try {
            val before = verdict(engine, shownTitle, shownText)
            assertTrue("test premise: the built-in model shows this (score ${before.score} vs ${before.threshold})", before.isUrgent)

            assertEquals(1, PersonalLearning.record(context, shownTitle, shownText, mode, urgent = false))

            val after = verdict(engine, shownTitle, shownText)
            assertFalse("adjustment ${after.adjustment}", after.isUrgent)
            assertTrue(after.adjustment < 0)
            val similar = verdict(engine, "apple watch high heart rate notification: 150 bpm while inactive")
            assertFalse("a near-identical notification follows the user's choice", similar.isUrgent)
            val unrelated = verdict(engine, "doctor: urgent medical notification, please call")
            assertTrue("a different urgent message stays shown", unrelated.isUrgent)

            PersonalLearning.forget(context, shownTitle, shownText)
            assertTrue(verdict(engine, shownTitle, shownText).isUrgent)
        } finally {
            // the shared engine stays open
        }
    }

    @Test
    fun customModesAreNotLearned() {
        assertEquals(null, PersonalLearning.record(context, title, text, "My Custom Mode", urgent = true))
        assertEquals(0, PersonalLearning.correctionCount(context))
    }

    @Test
    fun clearDeletesEverything() {
        PersonalLearning.record(context, title, text, mode, urgent = true)
        assertEquals(1, PersonalLearning.correctionCount(context))
        PersonalLearning.clear(context)
        assertEquals(0, PersonalLearning.correctionCount(context))
        assertFalse(File(context.filesDir, "personal_model.bin").exists())
    }

    @Test
    fun theStoredFileHoldsNumbersNotMessageText() {
        PersonalLearning.record(context, title, text, mode, urgent = true)
        val file = File(context.filesDir, "personal_model.bin")
        assertTrue(file.exists())
        val bytes = String(file.readBytes(), Charsets.ISO_8859_1).lowercase()
        listOf("greenwood", "dismissal", "safety incident").forEach {
            assertFalse("'$it' must not be stored", bytes.contains(it))
        }
    }

    @Test
    fun theFileSurvivesARestartOfTheClassifier() {
        PersonalLearning.record(context, title, text, mode, urgent = true)
        val fresh = EmbeddingClassifier.shared(context)!!
        try {
            assertTrue(verdict(fresh).isUrgent)
        } finally {
            // the shared engine stays open
        }
    }
}
