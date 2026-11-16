package com.focusrefine.app

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The Learned Rules screen explains itself when there are no rules, and lists rules when there are some.
 * Uses the app's real database: emulator only.
 */
@RunWith(AndroidJUnit4::class)
class LearnedRulesEmptyStateE2ETest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dao get() = AppDatabase.getDatabase(context).notificationDao()

    @Before
    fun setUp() {
        dao.deleteAll()
        PersonalLearning.clear(context)
    }

    @After
    fun tearDown() {
        dao.deleteAll()
        PersonalLearning.clear(context)
    }

    @Test
    fun showsAnEmptyStateWhenThereAreNoRules() {
        ActivityScenario.launch(LearnedRulesActivity::class.java).use {
            AccessibilityDriver.expectText("No learned rules yet.")
        }
    }

    @Test
    fun listsTheRuleInsteadOfTheEmptyStateWhenThereIsOne() {
        dao.insert(NotificationEntity(
            title = "Greenwood School", text = "Early dismissal today due to a safety incident",
            packageName = "com.android.shell", timestamp = System.currentTimeMillis(), isUrgent = false, confidence = 0.6f
        ))
        FeedbackRecorder.apply(context, dao.getAll().first(), makeUrgent = true, activeMode = "Sleeping / Rest", learn = true)
        ActivityScenario.launch(LearnedRulesActivity::class.java).use {
            AccessibilityDriver.expectText("Greenwood School")
            AccessibilityDriver.expectText("No learned rules yet.", present = false)
        }
    }
}
