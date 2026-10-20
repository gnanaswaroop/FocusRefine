package com.focusrefine.app

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Issue #10: the timed mode session and its live notification. Uses the app's real preferences: emulator only. */
@RunWith(AndroidJUnit4::class)
class ModeSessionE2ETest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs get() = context.getSharedPreferences("FocusRefinePrefs", Context.MODE_PRIVATE)
    private val manager get() = context.getSystemService(NotificationManager::class.java)

    private fun live() = manager.activeNotifications.firstOrNull { it.id == ModeSession.NOTIFICATION_ID }
    private fun done() = manager.activeNotifications.firstOrNull { it.id == 1003 }

    @Before
    fun setUp() {
        ModeSession.end(context)
        manager.cancel(1003)
        prefs.edit().putString("current_mode", "Sleeping / Rest").commit()
    }

    @After
    fun tearDown() {
        ModeSession.end(context)
        manager.cancel(1003)
        manager.cancel(ModeSession.NOTIFICATION_ID)
    }

    @Test
    fun startingASessionSwitchesModeAndShowsALiveCountdown() {
        ModeSession.start(context, "Deep Focus", 45)
        assertEquals("Deep Focus", prefs.getString("current_mode", null))
        val n = live()
        assertNotNull("the live notification is posted", n)
        val extras = n!!.notification.extras
        assertEquals("Deep Focus session started", extras.getString(Notification.EXTRA_TITLE))
        assertTrue(extras.getCharSequence(Notification.EXTRA_TEXT).toString().contains("45 min"))
        assertEquals(100, extras.getInt(Notification.EXTRA_PROGRESS_MAX))
        assertTrue("it counts down", extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN))
        assertEquals(listOf("End session", "Open FocusRefine"), n.notification.actions.map { it.title.toString() })
        assertTrue("silent", n.notification.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        assertTrue("ongoing", n.notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
    }

    @Test
    fun anActiveSessionShowsTimeLeftAndProgress() {
        val now = System.currentTimeMillis()
        ModeSession.start(context, "Deep Focus", 60, nowMs = now - 30 * 60_000L)
        ModeSession.refresh(context, nowMs = now)
        // notify() is asynchronous: wait for the update to replace the "started" post.
        val deadline = System.currentTimeMillis() + 3_000
        while (live()?.notification?.extras?.getString(Notification.EXTRA_TITLE)?.contains("30 min left") != true &&
            System.currentTimeMillis() < deadline) Thread.sleep(50)
        val extras = live()!!.notification.extras
        assertTrue(extras.getString(Notification.EXTRA_TITLE)!!, extras.getString(Notification.EXTRA_TITLE)!!.contains("30 min left"))
        assertEquals(50, extras.getInt(Notification.EXTRA_PROGRESS))
    }

    @Test
    fun endingTheSessionRestoresTheEarlierModeAndClearsTheNotification() {
        ModeSession.start(context, "Deep Focus", 45)
        ModeSession.end(context)
        assertEquals("Sleeping / Rest", prefs.getString("current_mode", null))
        assertNull(live())
        assertFalse(ModeSession.isActive(context))
        assertNull("ending by hand is not a completion", done())
    }

    @Test
    fun aSessionThatRunsOutCompletesRestoresTheModeAndSaysSo() {
        val now = System.currentTimeMillis()
        ModeSession.start(context, "Deep Focus", 25, nowMs = now - 26 * 60_000L)
        ModeSession.refresh(context, nowMs = now)
        assertFalse(ModeSession.isActive(context))
        assertEquals("Sleeping / Rest", prefs.getString("current_mode", null))
        assertNull(live())
        val d = done()
        assertNotNull("a completion note is posted", d)
        assertEquals("Deep Focus session complete", d!!.notification.extras.getString(Notification.EXTRA_TITLE))
    }

    @Test
    fun startingASecondSessionKeepsTheOriginalModeToGoBackTo() {
        ModeSession.start(context, "Deep Focus", 25)
        ModeSession.start(context, "Commute", 45)
        assertEquals("Commute", prefs.getString("current_mode", null))
        ModeSession.end(context)
        assertEquals("Sleeping / Rest", prefs.getString("current_mode", null))
    }

    @Test
    fun homeStartsAndEndsASessionInTwoTaps() {
        androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java).use {
            assertTrue(AccessibilityDriver.waitForText("Timed session"))
            AccessibilityDriver.tapText("Timed session")
            AccessibilityDriver.tapText("25 minutes")
            assertTrue("the session card shows", AccessibilityDriver.waitForText("min left"))
            assertTrue(ModeSession.isActive(context))
            assertNotNull(live())
            AccessibilityDriver.tapText("End session")
            assertTrue(AccessibilityDriver.waitForText("Timed session"))
            assertFalse(ModeSession.isActive(context))
            assertNull(live())
        }
    }
}
