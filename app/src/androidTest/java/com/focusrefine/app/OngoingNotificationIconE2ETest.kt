package com.focusrefine.app

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The ongoing FocusRefine notification must carry the app icon as its large icon. The Always-On Display draws that
 * icon in its circle, and an empty large icon shows as an empty circle. Emulator only: runs the real service.
 */
@RunWith(AndroidJUnit4::class)
class OngoingNotificationIconE2ETest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val manager get() = context.getSystemService(NotificationManager::class.java)

    @Before
    fun setUp() {
        ServiceHarness.grantNotificationAccess()
        ServiceHarness.shell("pm grant com.focusrefine.app android.permission.POST_NOTIFICATIONS")
    }

    @After
    fun tearDown() {
        context.stopService(Intent(context, NotificationFilterService::class.java))
    }

    @Test
    fun theOngoingNotificationHasTheAppIconAsItsLargeIcon() {
        ContextCompat.startForegroundService(context, Intent(context, NotificationFilterService::class.java))

        val deadline = System.currentTimeMillis() + 15_000
        var ongoing = manager.activeNotifications.firstOrNull { it.id == 1001 }
        while (ongoing == null && System.currentTimeMillis() < deadline) {
            Thread.sleep(300)
            ongoing = manager.activeNotifications.firstOrNull { it.id == 1001 }
        }
        assertNotNull("the service posted its ongoing notification", ongoing)
        assertNotNull("the ongoing notification has a large icon (the app icon)", ongoing!!.notification.getLargeIcon())
        assertTrue("it has a small icon too", ongoing.notification.smallIcon != null)
    }
}
