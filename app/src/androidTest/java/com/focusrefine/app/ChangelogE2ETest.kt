package com.focusrefine.app

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The Settings menu opens the change log and the Help screen. Read-only: safe on any device. */
@RunWith(AndroidJUnit4::class)
class ChangelogE2ETest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun theBundledChangelogLoadsOnTheDevice() {
        val releases = Changelog.load(context)
        assertTrue(releases.isNotEmpty())
        val version = context.packageManager.getPackageInfo(context.packageName, 0).versionName
        assertEquals("the newest release is the installed version", version, releases.first().version)
    }

    @Test
    fun theSettingsMenuOpensTheChangelog() {
        ActivityScenario.launch(SettingsActivity::class.java).use {
            AccessibilityDriver.tapText("More options")
            AccessibilityDriver.expectText("What's new", why = "the menu offers both screens")
            AccessibilityDriver.expectText("Help & how it works", why = "the menu offers Help too")
            AccessibilityDriver.tapText("What's new")
            AccessibilityDriver.expectText("You have version", why = "the change log opens")
            AccessibilityDriver.expectText("Version ${Changelog.load(context).first().version}", why = "shows the release")
            AccessibilityDriver.expectText("Close calls", scroll = true, why = "and what changed")
        }
    }

    @Test
    fun theSettingsMenuOpensHelp() {
        ActivityScenario.launch(SettingsActivity::class.java).use {
            AccessibilityDriver.tapText("More options")
            AccessibilityDriver.tapText("Help & how it works")
            AccessibilityDriver.expectText("Keep the noise out")
        }
    }

    @Test
    fun theMenuClosesWithoutOpeningAnything() {
        ActivityScenario.launch(SettingsActivity::class.java).use {
            AccessibilityDriver.tapText("More options")
            AccessibilityDriver.expectText("What's new")
            InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            AccessibilityDriver.expectText("What's new", present = false, why = "back closes the menu and stays on Settings")
            AccessibilityDriver.expectText("Settings", why = "still on Settings")
        }
    }
}
