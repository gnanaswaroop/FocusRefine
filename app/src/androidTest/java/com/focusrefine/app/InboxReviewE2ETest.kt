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
 * The inbox surfaces decisions the app was not sure about and lets the user settle them with one tap.
 * Uses the app's real database and preferences: emulator only.
 */
@RunWith(AndroidJUnit4::class)
class InboxReviewE2ETest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dao get() = AppDatabase.getDatabase(context).notificationDao()

    @Before
    fun setUp() {
        context.getSharedPreferences("FocusRefinePrefs", Context.MODE_PRIVATE).edit()
            .putBoolean("use_ai_model", true).putBoolean("learn_from_choices", true).putString("current_mode", "Sleeping / Rest").commit()
        dao.deleteAll()
        PersonalLearning.clear(context)
        val now = System.currentTimeMillis()
        dao.insert(NotificationEntity(title = "Greenwood School", text = "Early dismissal today due to a safety incident",
            packageName = "com.android.shell", timestamp = now, isUrgent = false, confidence = 0.55f))
        dao.insert(NotificationEntity(title = "Amazon", text = "Lightning deal: 60% off smart watches",
            packageName = "com.android.shell", timestamp = now - 1000, isUrgent = false, confidence = 0.98f))
    }

    @After
    fun tearDown() {
        dao.deleteAll()
        PersonalLearning.clear(context)
    }

    @Test
    fun closeCallsAreFlaggedAndOneTapSettlesThem() {
        ActivityScenario.launch(MainActivity::class.java).use {
            assertTrue("the banner should count the close call", AccessibilityDriver.waitForText("1 close call to review"))
            assertTrue("the uncertain card carries a 'Not sure' pill", AccessibilityDriver.waitForText("Not sure"))
            assertTrue("and the one-tap choices", AccessibilityDriver.hasText("Show it"))

            AccessibilityDriver.tapText("Review")
            assertTrue(AccessibilityDriver.waitForText("Reviewing close calls"))
            assertTrue("the confident decision is not in the review list", AccessibilityDriver.waitForText("Lightning deal", present = false))

            AccessibilityDriver.tapText("Show it")

            val deadline = System.currentTimeMillis() + 60_000 // learning embeds the anchors first
            while (System.currentTimeMillis() < deadline &&
                (dao.getAll().firstOrNull { it.title == "Greenwood School" }?.isUrgent != true || PersonalLearning.correctionCount(context) == 0)) {
                Thread.sleep(300)
            }
            val row = dao.getAll().first { it.title == "Greenwood School" }
            assertTrue("moved to Shown", row.isUrgent)
            assertTrue("remembered as the user's choice", row.isLearned)
            assertEquals(Confidence.RULE, row.confidence)
            assertEquals("the choice also taught the personal model", 1, PersonalLearning.correctionCount(context))
            assertTrue("nothing is left to review, so the banner goes away", AccessibilityDriver.waitForText("close call", present = false))
        }
    }

    @Test
    fun keepingTheDecisionAlsoSettlesItAndTeachesTheApp() {
        ActivityScenario.launch(MainActivity::class.java).use {
            assertTrue(AccessibilityDriver.waitForText("1 close call to review"))
            AccessibilityDriver.tapText("Keep muted")

            val deadline = System.currentTimeMillis() + 60_000
            while (System.currentTimeMillis() < deadline && PersonalLearning.correctionCount(context) == 0) Thread.sleep(300)
            val row = dao.getAll().first { it.title == "Greenwood School" }
            assertFalse("still muted", row.isUrgent)
            assertTrue(row.isLearned)
            assertEquals(Confidence.RULE, row.confidence)
            assertEquals(1, PersonalLearning.correctionCount(context))
        }
    }

    @Test
    fun quickFilterChipListsOnlyCloseCallsAndTogglesOff() {
        ActivityScenario.launch(MainActivity::class.java).use {
            assertTrue("the chip counts the close calls", AccessibilityDriver.waitForText("Close calls (1)"))
            AccessibilityDriver.tapText("Close calls (1)")
            assertTrue(AccessibilityDriver.waitForText("Reviewing close calls"))
            assertTrue("only the close call is listed", AccessibilityDriver.waitForText("Lightning deal", present = false))
            AccessibilityDriver.tapText("Muted (2)")
            assertTrue("a tab leaves the filter", AccessibilityDriver.waitForText("Amazon"))
        }
    }
}
