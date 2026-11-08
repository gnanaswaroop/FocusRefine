package com.focusrefine.app

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A fresh install has no `service_running` preference. Before the fix, granting notification access read that missing
 * key as "on" and turned filtering on without the user asking. These tests start from a fresh install (the key and
 * the paused-for-access flag removed) and check that:
 *  - the Home screen shows the access warning until access is granted, then the warning goes away;
 *  - filtering stays paused after access is granted, and nothing is switched on by itself.
 * Emulator only: changes the listener grant and the real preferences.
 */
@RunWith(AndroidJUnit4::class)
class FreshInstallAccessE2ETest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs get() = context.getSharedPreferences("FocusRefinePrefs", Context.MODE_PRIVATE)

    /** Back to a fresh install: no saved running state, no paused-for-access flag, empty history. */
    private fun freshInstall() {
        prefs.edit().remove("service_running").remove("paused_for_access").commit()
        ServiceHarness.dao.deleteAll()
        ServiceHarness.shell("cmd notification disallow_listener com.focusrefine.app/com.focusrefine.app.NotificationFilterService")
        Thread.sleep(1500)
    }

    @Before
    fun setUp() = freshInstall()

    @After
    fun tearDown() {
        // Leaves the listener granted, as the other E2E tests expect; ServiceHarness.start re-reads the grant anyway.
        ServiceHarness.stop()
    }

    @Test
    fun aFreshInstallStartsPausedWithoutAccess() {
        ActivityScenario.launch(MainActivity::class.java).use {
            assertTrue("the access warning shows before access is granted",
                AccessibilityDriver.waitForText("Notification Access Disabled"))
            assertTrue("filtering is paused on a fresh install", AccessibilityDriver.waitForText("Filtering Paused"))
            assertFalse("nothing saved as running", prefs.getBoolean("service_running", false))
        }
    }

    @Test
    fun grantingAccessClearsTheWarningAndKeepsFilteringPaused() {
        ActivityScenario.launch(MainActivity::class.java).use {
            assertTrue(AccessibilityDriver.waitForText("Notification Access Disabled"))
            // Same as the user approving FocusRefine in Settings.
            ServiceHarness.grantNotificationAccess()
            assertTrue("the access warning clears once access is granted",
                AccessibilityDriver.waitForText("Notification Access Disabled", present = false))
            assertTrue("filtering stays paused after access is granted",
                AccessibilityDriver.waitForText("Filtering Paused"))
            Thread.sleep(1_500) // give a wrong auto-start time to show up
            assertFalse("the app did not turn filtering on by itself", prefs.getBoolean("service_running", false))
            assertTrue("the switch still reads paused", AccessibilityDriver.waitForText("Filtering Paused"))
        }
    }
}
