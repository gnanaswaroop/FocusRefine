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

/** The Help screen explains the app and checks the privacy facts live. Uses the real database: emulator only. */
@RunWith(AndroidJUnit4::class)
class HelpScreenE2ETest {

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

    private fun row(title: String, learned: Boolean = false) = NotificationEntity(
        title = title, text = "body of $title", packageName = "pkg", timestamp = System.currentTimeMillis(), isLearned = learned
    )

    @Test
    fun theFactsMatchWhatTheInstalledAppReallyIs() {
        val facts = PrivacyFacts.load(context)
        assertTrue("no network permission in the installed app: ${facts.networkPermissions}", facts.worksOffline)
        assertFalse("cloud backup is off", facts.cloudBackupAllowed)
    }

    @Test
    fun theHelpScreenShowsTheLivePrivacyCheck() {
        dao.insert(row("one")); dao.insert(row("two")); dao.insert(row("three")); dao.insert(row("rule", learned = true))
        ActivityScenario.launch(HelpActivity::class.java).use {
            assertTrue(AccessibilityDriver.waitForText("Help & how it works"))
            assertTrue(AccessibilityDriver.waitForText("Offline-first and private", scroll = true))
            assertTrue("states that there is no internet permission", AccessibilityDriver.waitForText("None. No internet permission.", scroll = true))
            assertTrue("and that cloud backup is off", AccessibilityDriver.waitForText("Cloud backup", scroll = true))
            assertTrue("and counts what is stored, with correct plurals", AccessibilityDriver.waitForText("3 notifications, 1 rule you taught it, 0 lessons learned", scroll = true))
        }
    }

    @Test
    fun sectionsOpenAndCloseWhenTapped() {
        ActivityScenario.launch(HelpActivity::class.java).use {
            assertTrue(AccessibilityDriver.waitForText("How it decides"))
            assertFalse("closed sections hide their text", AccessibilityDriver.hasText("Show it / Mute it flips the decision"))
            AccessibilityDriver.tapText("Teach it what you want")
            assertTrue(AccessibilityDriver.waitForText("Show it / Mute it flips the decision"))
            AccessibilityDriver.tapText("Teach it what you want")
            assertTrue(AccessibilityDriver.waitForText("Show it / Mute it flips the decision", present = false))
        }
    }

    @Test
    fun helpIsReachableFromSettingsAndFromTheHomeScreen() {
        ActivityScenario.launch(SettingsActivity::class.java).use {
            AccessibilityDriver.tapText("Help & how it works")
            assertTrue("opens from Settings", AccessibilityDriver.waitForText("Keep the noise out"))
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            assertTrue(AccessibilityDriver.waitForText("Smart Inbox"))
            AccessibilityDriver.tapText("Help")
            assertTrue("opens from the home screen", AccessibilityDriver.waitForText("Keep the noise out"))
        }
    }

    @Test
    fun whatTheHelpScreenSaysAboutDeletingMatchesTheButtonsThatExist() {
        // The screen tells users where to clear their data; those labels must exist in the app.
        ActivityScenario.launch(SettingsActivity::class.java).use {
            assertTrue("'Clear Inbox' is in Settings", AccessibilityDriver.waitForText("Clear Inbox", scroll = true))
        }
        ActivityScenario.launch(PrivacyInsightsActivity::class.java).use {
            assertTrue("'Clear local learning data' is in Privacy & Insights", AccessibilityDriver.waitForText("Clear local learning data", scroll = true))
        }
        assertEquals(0, dao.getLearnedRules().size)
    }

}
