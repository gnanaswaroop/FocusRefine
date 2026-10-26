package com.focusrefine.app

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Settings, Privacy and Learned Rules driven through the real screens. Uses the app's real database and
 * preferences: emulator only.
 */
@RunWith(AndroidJUnit4::class)
class ScreensE2ETest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dao get() = AppDatabase.getDatabase(context).notificationDao()
    private val sleep = "Sleeping / Rest"

    @Before
    fun setUp() {
        context.getSharedPreferences("FocusRefinePrefs", Context.MODE_PRIVATE).edit()
            .putString("current_mode", sleep).putBoolean("use_ai_model", true).putBoolean("learn_from_choices", true)
            .putInt("filter_strength", 1).putBoolean("exception_calls", true).putBoolean("exception_nav_prompts", true)
            .putBoolean("exception_security_otps", true).commit()
        dao.deleteAll()
        PersonalLearning.clear(context)
    }

    @After
    fun tearDown() {
        dao.deleteAll()
        PersonalLearning.clear(context)
    }

    private fun row(title: String, text: String, urgent: Boolean, ts: Long) = NotificationEntity(
        title = title, text = text, packageName = "com.android.shell", timestamp = ts, isUrgent = urgent, confidence = 0.6f
    )

    private fun waitUntil(timeoutMs: Long = 60_000, condition: () -> Boolean): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (condition()) return true
            Thread.sleep(300)
        }
        return false
    }

    @Test
    fun reRunningClassificationFromSettingsMovesHistoryToTheCurrentDecision() {
        val now = System.currentTimeMillis()
        dao.insert(row("Airline", "Your flight is boarding now, gate B4", urgent = false, ts = now))   // should be Shown while sleeping
        dao.insert(row("Myntra", "Lightning deal: 50% off sneakers", urgent = true, ts = now - 1000))     // should be Muted
        ActivityScenario.launch(SettingsActivity::class.java).use {
            AccessibilityDriver.tapText("Learning") // its own card, outside Advanced Settings
            AccessibilityDriver.tapText("Re-run classification on history")
            assertTrue("the confirmation dialog appears", AccessibilityDriver.waitForText("Re-run classification?"))
            AccessibilityDriver.tapText("Re-run")
            assertTrue("history was re-checked", waitUntil {
                dao.getAll().first { it.title == "Airline" }.isUrgent && !dao.getAll().first { it.title == "Myntra" }.isUrgent
            })
            val shown = dao.getAll().first { it.title == "Airline" }
            assertTrue("explained by the model: ${shown.explanation}", shown.explanation!!.contains("AI Pipeline"))
            assertTrue("and the confidence now reflects the new decision: ${shown.confidence}", shown.confidence!! >= Confidence.VERY_SURE_FROM)
        }
    }

    @Test
    fun clearingLearningDataFromThePrivacyScreenForgetsRulesAndThePersonalModel() {
        val now = System.currentTimeMillis()
        dao.insert(row("Greenwood School", "Early dismissal today due to a safety incident", urgent = false, ts = now))
        FeedbackRecorder.apply(context, dao.getAll().first(), makeUrgent = true, activeMode = sleep, learn = true)
        assertEquals(1, dao.getLearnedRules().size)
        assertEquals(1, PersonalLearning.correctionCount(context))

        ActivityScenario.launch(PrivacyInsightsActivity::class.java).use {
            AccessibilityDriver.tapText("Clear local learning data")
            assertTrue(AccessibilityDriver.waitForText("Clear Local Learning Data?"))
            AccessibilityDriver.tapText("Clear Data")
            assertTrue("rules and the personal model are gone", waitUntil(20_000) {
                dao.getLearnedRules().isEmpty() && PersonalLearning.correctionCount(context) == 0
            })
        }
    }

    @Test
    fun deletingALearnedRuleForgetsWhatWasLearnedFromIt() {
        val now = System.currentTimeMillis()
        dao.insert(row("Greenwood School", "Early dismissal today due to a safety incident", urgent = false, ts = now))
        dao.insert(row("Dexcom", "Urgent low glucose alert: 55 mg/dL", urgent = true, ts = now - 5000))
        dao.getAll().forEach { FeedbackRecorder.apply(context, it, makeUrgent = !it.isUrgent, activeMode = sleep, learn = true) }
        assertEquals(2, dao.getLearnedRules().size)
        assertEquals(2, PersonalLearning.correctionCount(context))

        ActivityScenario.launch(LearnedRulesActivity::class.java).use {
            AccessibilityDriver.tapText("Delete")
            assertTrue("one rule and one lesson are gone", waitUntil(60_000) {
                dao.getLearnedRules().size == 1 && PersonalLearning.correctionCount(context) == 1
            })
        }
        assertFalse(dao.getLearnedRules().isEmpty())
    }
}
