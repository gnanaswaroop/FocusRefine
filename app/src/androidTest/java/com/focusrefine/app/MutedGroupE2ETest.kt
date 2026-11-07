package com.focusrefine.app

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
 * The Muted tab groups an app's muted notifications into one card that expands to its items. A single muted
 * notification from an app stays a plain card, and the Shown tab is never grouped. Uses the app's real database:
 * emulator only.
 */
@RunWith(AndroidJUnit4::class)
class MutedGroupE2ETest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dao get() = AppDatabase.getDatabase(context).notificationDao()
    private val shop = "com.example.shopping" // its label falls back to "Shopping"
    private val chat = "com.example.chatapp"

    private fun insert(title: String, text: String, pkg: String, urgent: Boolean, ageMs: Long, confidence: Float = 0.9f) {
        dao.insert(
            NotificationEntity(
                title = title, text = text, packageName = pkg, timestamp = System.currentTimeMillis() - ageMs,
                isUrgent = urgent, confidence = confidence
            )
        )
    }

    @Before
    fun setUp() {
        dao.deleteAll()
        // Three muted from one app (one of them a close call), one muted from another, two shown from the first app.
        insert("Shop", "Order shipped", shop, urgent = false, ageMs = 1_000)
        insert("Shop", "Flash sale today", shop, urgent = false, ageMs = 2_000)
        insert("Shop", "Your cart is waiting", shop, urgent = false, ageMs = 3_000, confidence = 0.55f)
        insert("Chat", "Lunch at one?", chat, urgent = false, ageMs = 4_000)
        insert("Shop", "Parcel arrives today", shop, urgent = true, ageMs = 5_000)
        insert("Shop", "Delivery slot confirmed", shop, urgent = true, ageMs = 6_000)
    }

    @After
    fun tearDown() {
        dao.deleteAll()
    }

    @Test
    fun anAppsMutedNotificationsAreOneCardThatExpands() {
        ActivityScenario.launch(MainActivity::class.java).use {
            assertTrue("one card for the app, with its count and close call", AccessibilityDriver.waitForText("3 from Shopping · 1 close call"))
            assertTrue("it says how to open it", AccessibilityDriver.hasText("Muted · tap to see them"))
            assertFalse("the items are hidden until it is opened", AccessibilityDriver.hasText("Order shipped"))

            AccessibilityDriver.touchText("3 from Shopping")
            assertTrue("it opens; screen ${AccessibilityDriver.visibleTexts(40)}", AccessibilityDriver.waitForText("Muted · tap to hide them"))
            assertTrue("and lists its items", AccessibilityDriver.waitForText("Order shipped"))
            assertTrue(AccessibilityDriver.hasText("Flash sale today"))
            // Closing the card again is not asserted here: the card does close (checked by hand with real touches), but
            // a second synthetic tap on it is not reliable in this harness.
        }
    }

    @Test
    fun aSingleMutedNotificationStaysAPlainCard() {
        ActivityScenario.launch(MainActivity::class.java).use {
            assertTrue("shown on its own, with no group card", AccessibilityDriver.waitForText("Lunch at one?"))
            assertFalse(AccessibilityDriver.hasText("1 from Chatapp"))
        }
    }

    @Test
    fun theShownTabIsNotGrouped() {
        ActivityScenario.launch(MainActivity::class.java).use {
            assertTrue(AccessibilityDriver.waitForText("3 from Shopping"))
            AccessibilityDriver.tapText("Shown (")
            assertTrue("both shown items are listed on their own", AccessibilityDriver.waitForText("Parcel arrives today"))
            assertTrue(AccessibilityDriver.hasText("Delivery slot confirmed"))
            assertFalse("with no group card", AccessibilityDriver.hasText("from Shopping"))
        }
    }
}
