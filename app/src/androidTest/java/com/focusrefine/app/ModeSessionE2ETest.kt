package com.focusrefine.app

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.service.notification.StatusBarNotification
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

    /** FocusRefine's own live notifications: the service's ongoing one (1001) and the standalone session one (1002). */
    private fun ours() = manager.activeNotifications.filter {
        it.packageName == context.packageName && (it.id == ModeSession.NOTIFICATION_ID || it.id == 1001)
    }

    /** The notification that shows the running session (it has the End session button). */
    private fun live() = ours().firstOrNull { n -> n.notification.actions?.any { it.title == "End session" } == true }

    /** The service rebuilds its notification from a broadcast, so wait for it instead of reading it at once. */
    private fun awaitLive(timeoutMs: Long = 5_000, ok: (StatusBarNotification) -> Boolean = { true }): StatusBarNotification? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            live()?.takeIf(ok)?.let { return it }
            Thread.sleep(50)
        }
        return live()?.takeIf(ok)
    }

    private fun awaitNoSessionNotification(timeoutMs: Long = 5_000): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (live() == null) return true
            Thread.sleep(50)
        }
        return live() == null
    }

    private fun title(n: StatusBarNotification) = n.notification.extras.getString(Notification.EXTRA_TITLE) ?: ""
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
        val n = awaitLive()
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
        // The update arrives asynchronously and replaces the "started" post.
        val extras = awaitLive { title(it).contains("30 min left") }!!.notification.extras
        assertTrue(extras.getString(Notification.EXTRA_TITLE)!!, extras.getString(Notification.EXTRA_TITLE)!!.contains("30 min left"))
        assertEquals(50, extras.getInt(Notification.EXTRA_PROGRESS))
    }

    @Test
    fun endingTheSessionRestoresTheEarlierModeAndClearsTheNotification() {
        ModeSession.start(context, "Deep Focus", 45)
        ModeSession.end(context)
        assertEquals("Sleeping / Rest", prefs.getString("current_mode", null))
        assertTrue("the session notification is gone", awaitNoSessionNotification())
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
        assertTrue("the session notification is gone", awaitNoSessionNotification())
        val deadline = System.currentTimeMillis() + 5_000
        while (done() == null && System.currentTimeMillis() < deadline) Thread.sleep(50)
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
            AccessibilityDriver.tapText("15 minutes")
            assertTrue("the session card shows", AccessibilityDriver.waitForText("min left"))
            assertTrue(ModeSession.isActive(context))
            assertNotNull(awaitLive())
            AccessibilityDriver.tapText("End session")
            assertTrue(AccessibilityDriver.waitForText("Timed session"))
            assertFalse(ModeSession.isActive(context))
            assertTrue(awaitNoSessionNotification())
        }
    }

    @Test
    fun aRunningSessionUsesOneLiveNotificationNotTwo() {
        ModeSession.start(context, "Deep Focus", 45)
        assertNotNull(awaitLive())
        Thread.sleep(1_500) // let any second post show up
        assertEquals("one live notification while a session runs: ${ours().map { it.id }}", 1, ours().size)
        ModeSession.end(context)
        assertTrue(awaitNoSessionNotification())
        // Back to the service's normal notification when the service runs (or none if it is not running).
        assertEquals("no session buttons are left behind", null, live())
    }

    @Test
    fun anOpenEndedSessionCountsUpHasNoProgressAndNeverRunsOut() {
        val now = System.currentTimeMillis()
        ModeSession.start(context, "Deep Focus", null, nowMs = now - 10 * 60_000L)
        assertTrue(ModeSession.current(context)!!.openEnded)
        ModeSession.refresh(context, nowMs = now)
        val n = awaitLive { title(it).contains("until you turn it off") }
        assertNotNull("the title says it runs until turned off", n)
        val extras = n!!.notification.extras
        assertFalse("it counts up, not down", extras.getBoolean(Notification.EXTRA_CHRONOMETER_COUNT_DOWN))
        assertEquals("no progress bar", 0, extras.getInt(Notification.EXTRA_PROGRESS_MAX))
        // Days later it is still running.
        ModeSession.refresh(context, nowMs = now + 5L * 24 * 60 * 60_000L)
        assertTrue(ModeSession.isActive(context))
        assertNull("no completion note", done())
        ModeSession.end(context)
        assertEquals("Sleeping / Rest", prefs.getString("current_mode", null))
    }

    @Test
    fun thePickerOffersFiveFifteenThirtyFortyFiveAndUntilTurnedOff() {
        androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java).use {
            assertTrue(AccessibilityDriver.waitForText("Timed session"))
            AccessibilityDriver.tapText("Timed session")
            for (label in listOf("5 minutes", "15 minutes", "30 minutes", "45 minutes", "Until I turn it off")) {
                assertTrue("the picker offers '$label'", AccessibilityDriver.waitForText(label))
            }
            AccessibilityDriver.tapText("Until I turn it off")
            assertTrue("the card says it runs until turned off", AccessibilityDriver.waitForText("until you turn it off"))
            assertTrue(ModeSession.current(context)!!.openEnded)
            AccessibilityDriver.tapText("End session")
            assertTrue(AccessibilityDriver.waitForText("Timed session"))
        }
    }
}
