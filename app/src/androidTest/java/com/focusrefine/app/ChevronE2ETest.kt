package com.focusrefine.app

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** The Settings expand/collapse chevrons (issue #11): present, stateful, and a proper touch target, in both theme settings. */
@RunWith(AndroidJUnit4::class)
class ChevronE2ETest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs get() = context.getSharedPreferences("FocusRefinePrefs", Context.MODE_PRIVATE)
    private val originalTheme = prefs.getInt("theme_mode", AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)

    @After
    fun restoreTheme() {
        prefs.edit().putInt("theme_mode", originalTheme).commit()
    }

    private fun checkSettingsChevrons() {
        ActivityScenario.launch(SettingsActivity::class.java).use {
            AccessibilityDriver.expectText("Classification Engine")
            // Settings opens with every section collapsed.
            AccessibilityDriver.expectText("Use On-Device AI Engine", present = false, why = "the section starts collapsed")
            assertTrue("collapsed sections show an Expand chevron", AccessibilityDriver.countOf("Expand") >= 1)
            assertEquals("and none starts open", 0, AccessibilityDriver.countOf("Collapse"))

            val heightPx = AccessibilityDriver.clickableHeightPx("Classification Engine")!!
            val minPx = 48 * context.resources.displayMetrics.density
            assertTrue("the header is at least 48dp tall: $heightPx px vs ${minPx.toInt()} px", heightPx >= minPx - 1)

            AccessibilityDriver.tapText("Classification Engine")
            AccessibilityDriver.expectText("Use On-Device AI Engine", why = "after expanding")
            AccessibilityDriver.expectText("Collapse", why = "an open section offers Collapse")
            AccessibilityDriver.tapText("Classification Engine")
            AccessibilityDriver.expectText("Use On-Device AI Engine", present = false, why = "after collapsing again")
        }
    }

    @Test
    fun chevronsWorkInTheDarkThemeSetting() {
        prefs.edit().putInt("theme_mode", AppCompatDelegate.MODE_NIGHT_YES).commit()
        checkSettingsChevrons()
    }

    @Test
    fun chevronsWorkInTheLightThemeSetting() {
        prefs.edit().putInt("theme_mode", AppCompatDelegate.MODE_NIGHT_NO).commit()
        checkSettingsChevrons()
    }

    @Test
    fun theHelpSectionsUseTheSameChevron() {
        ActivityScenario.launch(HelpActivity::class.java).use {
            AccessibilityDriver.expectText("What it does")
            assertTrue("open Help sections show Collapse", AccessibilityDriver.countOf("Collapse") >= 1)
        }
    }
}
