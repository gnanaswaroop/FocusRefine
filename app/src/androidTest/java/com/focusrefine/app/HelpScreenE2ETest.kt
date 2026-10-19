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

    private val prefs get() = context.getSharedPreferences("FocusRefinePrefs", Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        // The listener service must not record the emulator's own notifications while counts are being checked.
        prefs.edit().putBoolean("service_running", false).commit()
        dao.deleteAll()
        PersonalLearning.clear(context)
    }

    @After
    fun tearDown() {
        dao.deleteAll()
        PersonalLearning.clear(context)
        prefs.edit().putBoolean("service_running", true).commit()
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
        // Opened with only the privacy section expanded, so the check is near the top whatever the window size.
        val intent = android.content.Intent(context, HelpActivity::class.java).putExtra(HelpActivity.EXTRA_SECTION, "privacy")
        ActivityScenario.launch<HelpActivity>(intent).use {
            AccessibilityDriver.expectText("Help & how it works")
            AccessibilityDriver.expectText("Offline-first and private", scroll = true)
            AccessibilityDriver.expectText("None. No internet permission.", scroll = true, why = "states that there is no internet permission")
            AccessibilityDriver.expectText("Cloud backup", scroll = true, why = "and that cloud backup is off")
            AccessibilityDriver.expectText("3 notifications, 1 rule you taught it, 0 lessons learned", scroll = true, why = "and counts what is stored, with correct plurals (stored: ${PrivacyFacts.load(context)})")
        }
    }

    @Test
    fun sectionsOpenAndCloseWhenTapped() {
        // Only "What it does" starts open, so the next header is right below it and no scrolling is needed.
        val intent = android.content.Intent(context, HelpActivity::class.java).putExtra(HelpActivity.EXTRA_SECTION, "what")
        ActivityScenario.launch<HelpActivity>(intent).use {
            AccessibilityDriver.expectText("How it decides")
            assertFalse("a closed section hides its text", AccessibilityDriver.hasText("The first one that applies decides"))
            AccessibilityDriver.tapText("How it decides")
            AccessibilityDriver.expectText("The first one that applies decides", why = "after opening")
            AccessibilityDriver.tapText("How it decides")
            AccessibilityDriver.expectText("The first one that applies decides", present = false, why = "after closing")
        }
    }

    @Test
    fun aRequestedSectionOpensAloneAndTheDefaultOpensTheFirstThree() {
        val only = android.content.Intent(context, HelpActivity::class.java).putExtra(HelpActivity.EXTRA_SECTION, "how-it-decides-does-not-exist")
        ActivityScenario.launch<HelpActivity>(only).use {
            AccessibilityDriver.expectText("What it does")
            assertFalse("an unknown section name opens nothing", AccessibilityDriver.hasText("Android lets an app you approve read incoming notifications"))
        }
        ActivityScenario.launch(HelpActivity::class.java).use {
            AccessibilityDriver.expectText("Android lets an app you approve read incoming notifications", why = "the default opens the first sections")
        }
    }

    @Test
    fun helpIsReachableFromSettingsAndFromTheHomeScreen() {
        ActivityScenario.launch(SettingsActivity::class.java).use {
            AccessibilityDriver.tapText("More options")
            AccessibilityDriver.tapText("Help & how it works")
            AccessibilityDriver.expectText("Keep the noise out", why = "opens from the Settings menu")
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            AccessibilityDriver.expectText("Smart Inbox")
            AccessibilityDriver.tapText("Help")
            AccessibilityDriver.expectText("Keep the noise out", why = "opens from the home screen")
        }
    }

    @Test
    fun whatTheHelpScreenSaysAboutDeletingMatchesTheButtonsThatExist() {
        // The screen tells users where to clear their data; those labels must exist in the app.
        ActivityScenario.launch(SettingsActivity::class.java).use {
            AccessibilityDriver.expectText("Clear Inbox", scroll = true, why = "'Clear Inbox' is in Settings")
        }
        ActivityScenario.launch(PrivacyInsightsActivity::class.java).use {
            AccessibilityDriver.expectText("Clear local learning data", scroll = true, why = "'Clear local learning data' is in Privacy & Insights")
        }
        assertEquals(0, dao.getLearnedRules().size)
    }

}
