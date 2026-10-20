package com.focusrefine.app

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Home keeps its controls compact: the search bar is behind a magnifier, close calls are one chip (the banner only
 * shows while reviewing), and the timed-session chip shares the mode row. Uses the app's real database: emulator only.
 */
@RunWith(AndroidJUnit4::class)
class HomeDeclutterE2ETest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dao get() = AppDatabase.getDatabase(context).notificationDao()

    @Before
    fun setUp() {
        context.getSharedPreferences("FocusRefinePrefs", Context.MODE_PRIVATE).edit().putString("current_mode", "Sleeping / Rest").commit()
        ModeSession.end(context)
        dao.deleteAll()
        dao.insert(NotificationEntity(title = "Greenwood School", text = "Early dismissal today due to a safety incident",
            packageName = "com.android.shell", timestamp = System.currentTimeMillis(), isUrgent = false, confidence = 0.55f))
    }

    @After
    fun tearDown() {
        dao.deleteAll()
    }

    @Test
    fun theSearchBarIsHiddenUntilTheMagnifierIsTapped() {
        ActivityScenario.launch(MainActivity::class.java).use {
            assertTrue(AccessibilityDriver.waitForText("Smart Inbox"))
            assertTrue("no search bar at first", AccessibilityDriver.waitForText("Search notifications...", present = false))
            AccessibilityDriver.tapText("Search notifications")
            assertTrue("the bar opens", AccessibilityDriver.waitForText("Search notifications..."))
            AccessibilityDriver.tapText("Close search")
            assertTrue("and closes again", AccessibilityDriver.waitForText("Search notifications...", present = false))
        }
    }

    @Test
    fun closeCallsAreOneChipAndTheBannerOnlyShowsWhileReviewing() {
        ActivityScenario.launch(MainActivity::class.java).use {
            assertTrue("the chip counts them", AccessibilityDriver.waitForText("Close calls (1)"))
            assertTrue("no banner above the list", AccessibilityDriver.hasText("close call to review").not())
            AccessibilityDriver.tapText("Close calls (1)")
            assertTrue("the banner says so while reviewing", AccessibilityDriver.waitForText("Reviewing close calls"))
            AccessibilityDriver.tapText("Done")
            assertTrue("and goes away", AccessibilityDriver.waitForText("Reviewing close calls", present = false))
        }
    }

    @Test
    fun theTimedSessionChipSitsOnTheModeRowAndGivesWayToTheCardWhileRunning() {
        ActivityScenario.launch(MainActivity::class.java).use {
            assertTrue(AccessibilityDriver.waitForText("Mode (long press to manage)"))
            assertTrue("the chip is on Home", AccessibilityDriver.hasText("Timed session"))
            assertTrue("no duplicate 'Active' text next to the label", AccessibilityDriver.hasText("· Active").not())
            AccessibilityDriver.tapText("Timed session")
            AccessibilityDriver.tapText("15 minutes")
            assertTrue("the running card replaces the chip", AccessibilityDriver.waitForText("min left"))
            assertTrue(AccessibilityDriver.waitForText("Timed session", present = false))
            AccessibilityDriver.tapText("End session")
            assertTrue(AccessibilityDriver.waitForText("Timed session"))
        }
    }
}
