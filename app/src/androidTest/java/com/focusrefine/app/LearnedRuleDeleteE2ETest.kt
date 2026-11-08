package com.focusrefine.app

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Deleting a learned rule asks first. Cancelling keeps the rule; confirming deletes it. Uses the app's real database:
 * emulator only.
 */
@RunWith(AndroidJUnit4::class)
class LearnedRuleDeleteE2ETest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dao get() = AppDatabase.getDatabase(context).notificationDao()
    private val ruleText = "[MUTE] Chat: lunch later"

    @Before
    fun setUp() {
        dao.deleteAll()
        dao.insert(
            NotificationEntity(
                title = "E2E Chat", text = "Lunch at 1?", packageName = "com.example.chat",
                timestamp = System.currentTimeMillis(), isUrgent = false, isLearned = true, synthesizedRule = ruleText
            )
        )
    }

    @After
    fun tearDown() {
        dao.deleteAll()
    }

    private fun learnedRules() = dao.getLearnedRules().filter { it.synthesizedRule == ruleText }

    private fun tapDelete() {
        assertTrue("the rule is listed", AccessibilityDriver.waitForText("AI Rule: $ruleText"))
        AccessibilityDriver.tapText("Delete")
        assertTrue("it asks first", AccessibilityDriver.waitForText("Delete Rule?"))
    }

    @Test
    fun tappingDeleteAsksFirstAndCancelKeepsTheRule() {
        ActivityScenario.launch(LearnedRulesActivity::class.java).use {
            tapDelete()
            assertEquals("nothing is deleted while the dialog is open", 1, learnedRules().size)
            AccessibilityDriver.tapText("Cancel")
            assertTrue("the dialog closes", AccessibilityDriver.waitForText("Delete Rule?", present = false))
            assertEquals("cancelling keeps the rule", 1, learnedRules().size)
            assertTrue("and it is still listed", AccessibilityDriver.hasText("AI Rule: $ruleText"))
        }
    }

    @Test
    fun confirmingDeleteRemovesTheRule() {
        ActivityScenario.launch(LearnedRulesActivity::class.java).use {
            tapDelete()
            AccessibilityDriver.tapText("Delete")
            assertTrue("the dialog closes", AccessibilityDriver.waitForText("Delete Rule?", present = false))
            val deadline = System.currentTimeMillis() + 5_000
            while (learnedRules().isNotEmpty() && System.currentTimeMillis() < deadline) Thread.sleep(100)
            assertEquals("confirming deletes the rule", 0, learnedRules().size)
            assertTrue("and it leaves the list", AccessibilityDriver.waitForText("AI Rule: $ruleText", present = false))
        }
    }
}
