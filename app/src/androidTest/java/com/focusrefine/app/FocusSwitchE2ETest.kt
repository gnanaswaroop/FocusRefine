package com.focusrefine.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
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
 * The Home switch changes the running state once per tap. Before the fix the app saved the new state and the service
 * flipped it again, so one tap changed it twice and the Do Not Disturb change followed the wrong state. The service
 * reports every state change in ACTION_SERVICE_STATE_CHANGED, so these tests count those reports.
 *
 * The app must never change Android's Do Not Disturb setting; the last two tests check that. They can only fail on a
 * build that declares ACCESS_NOTIFICATION_POLICY and has been granted it, because the app cannot change the setting
 * without that access. Emulator only: uses the real preferences and the real service.
 */
@RunWith(AndroidJUnit4::class)
class FocusSwitchE2ETest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs get() = context.getSharedPreferences("FocusRefinePrefs", Context.MODE_PRIVATE)

    /** The running states the service reported, in order. */
    private val reported = mutableListOf<Boolean>()

    private val reporter = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            reported.add(intent.getBooleanExtra("is_running", false))
        }
    }

    @Before
    fun setUp() {
        ServiceHarness.start("Sleeping / Rest", serviceRunning = false)
        ContextCompat.registerReceiver(
            context, reporter, IntentFilter(NotificationFilterService.ACTION_SERVICE_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    @After
    fun tearDown() {
        context.unregisterReceiver(reporter)
        ServiceHarness.shell("cmd notification set_dnd off") // leave the emulator as it was
        ModeSession.end(context)
        ServiceHarness.stop()
    }

    /** Waits for the service's report, then a little longer, so a second (wrong) report would show up too. */
    private fun awaitReports(expected: Int) {
        val deadline = System.currentTimeMillis() + 5_000
        while (reported.size < expected && System.currentTimeMillis() < deadline) Thread.sleep(50)
        Thread.sleep(1_500)
    }

    @Test
    fun turningTheSwitchOnReportsOneChangeToRunning() {
        ActivityScenario.launch(MainActivity::class.java).use {
            assertTrue(AccessibilityDriver.waitForText("Filtering Paused"))
            AccessibilityDriver.tapSwitch()
            AccessibilityDriver.tapText("Until I turn it off")
            assertTrue(AccessibilityDriver.waitForText("AI Filtering Active"))
            awaitReports(expected = 1)
            assertEquals("one report, and it says running", listOf(true), reported)
            assertTrue("the saved state matches the switch", prefs.getBoolean("service_running", false))
        }
    }

    @Test
    fun turningTheSwitchOffReportsOneChangeToPaused() {
        ServiceHarness.start("Sleeping / Rest", serviceRunning = true)
        ActivityScenario.launch(MainActivity::class.java).use {
            assertTrue(AccessibilityDriver.waitForText("AI Filtering Active"))
            AccessibilityDriver.tapSwitch()
            assertTrue(AccessibilityDriver.waitForText("Filtering Paused"))
            awaitReports(expected = 1)
            assertEquals("one report, and it says paused", listOf(false), reported)
            assertFalse("the saved state matches the switch", prefs.getBoolean("service_running", true))
        }
    }

    @Test
    fun aSwitchOnThenOffReportsExactlyTwoChanges() {
        ActivityScenario.launch(MainActivity::class.java).use {
            assertTrue(AccessibilityDriver.waitForText("Filtering Paused"))
            AccessibilityDriver.tapSwitch()
            AccessibilityDriver.tapText("Until I turn it off")
            assertTrue(AccessibilityDriver.waitForText("AI Filtering Active"))
            // The duration picker must be gone before the switch is tapped again, or the tap lands on the dialog.
            assertTrue(AccessibilityDriver.waitForText("How long should FocusRefine work?", present = false))
            awaitReports(expected = 1)
            AccessibilityDriver.tapSwitch()
            assertTrue(
                "switched off; reported $reported, saved ${prefs.getBoolean("service_running", true)}, screen ${AccessibilityDriver.visibleTexts()}",
                AccessibilityDriver.waitForText("Filtering Paused")
            )
            awaitReports(expected = 2)
            assertEquals("on, then off, one report each", listOf(true, false), reported)
        }
    }

    private fun zenMode(): Int = android.provider.Settings.Global.getInt(context.contentResolver, "zen_mode", 0)

    /** Puts Do Not Disturb in [mode], turns filtering on and off, and checks the setting never moved. */
    private fun switchOnThenOffLeavesDoNotDisturb(mode: String) {
        ServiceHarness.shell("cmd notification set_dnd $mode")
        Thread.sleep(500)
        val before = zenMode()
        ActivityScenario.launch(MainActivity::class.java).use {
            assertTrue(AccessibilityDriver.waitForText("Filtering Paused"))
            AccessibilityDriver.tapSwitch()
            AccessibilityDriver.tapText("Until I turn it off")
            assertTrue(AccessibilityDriver.waitForText("AI Filtering Active"))
            assertTrue(AccessibilityDriver.waitForText("How long should FocusRefine work?", present = false))
            awaitReports(expected = 1)
            assertEquals("Do Not Disturb while filtering is on", before, zenMode())
            AccessibilityDriver.tapSwitch()
            assertTrue(AccessibilityDriver.waitForText("Filtering Paused"))
            awaitReports(expected = 2)
            assertEquals("Do Not Disturb after filtering is turned off", before, zenMode())
        }
    }

    @Test
    fun theSwitchLeavesDoNotDisturbOnWhenItWasOn() = switchOnThenOffLeavesDoNotDisturb("priority")

    @Test
    fun theSwitchLeavesDoNotDisturbOffWhenItWasOff() = switchOnThenOffLeavesDoNotDisturb("off")
}
