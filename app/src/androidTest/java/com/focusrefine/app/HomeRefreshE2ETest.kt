package com.focusrefine.app

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Issue #14: rows added while the Home screen is behind Settings (as "Add 20 Samples" does) show up as soon as the
 * user comes back, without a manual refresh. Uses the app's real database: emulator only.
 */
@RunWith(AndroidJUnit4::class)
class HomeRefreshE2ETest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val dao get() = AppDatabase.getDatabase(context).notificationDao()

    @Before
    fun setUp() {
        dao.deleteAll()
    }

    @After
    fun tearDown() {
        dao.deleteAll()
    }

    @Test
    fun rowsAddedBehindSettingsAppearWhenTheUserComesBack() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertTrue(AccessibilityDriver.waitForText("Smart Inbox"))
            scenario.onActivity { it.startActivity(Intent(it, SettingsActivity::class.java)) }
            assertTrue("Settings is on top", AccessibilityDriver.waitForText("Smart Inbox", present = false))

            // What the sample button does: write rows, then broadcast.
            dao.insert(NotificationEntity(title = "Refresh Probe", text = "Added while Settings was open",
                packageName = "com.android.shell", timestamp = System.currentTimeMillis(), isUrgent = false, confidence = 1.0f))
            context.sendBroadcast(Intent(NotificationFilterService.ACTION_NOTIFICATION_ARRIVED).setPackage(context.packageName))

            instrumentation.uiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)
            assertTrue("the new row is listed after returning", AccessibilityDriver.waitForText("Refresh Probe"))
        }
    }
}
