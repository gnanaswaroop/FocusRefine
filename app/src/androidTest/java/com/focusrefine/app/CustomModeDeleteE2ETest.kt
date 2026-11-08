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
 * Deleting a custom mode asks first. Cancelling keeps the mode; confirming deletes it. Uses the app's real
 * preferences: emulator only.
 */
@RunWith(AndroidJUnit4::class)
class CustomModeDeleteE2ETest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs get() = context.getSharedPreferences("FocusRefinePrefs", Context.MODE_PRIVATE)
    private val mode = "E2E Mode"

    @Before
    fun setUp() {
        prefs.edit().putString("custom_modes_list", mode).putString("custom_mode_prompt_$mode", "e2e rules").commit()
    }

    @After
    fun tearDown() {
        prefs.edit().remove("custom_modes_list").remove("custom_mode_prompt_$mode").remove("custom_mode_enabled_$mode").commit()
    }

    private fun modeIsSaved() = (prefs.getString("custom_modes_list", "") ?: "").split(",").contains(mode)

    private fun tapDelete() {
        // The list loads after the screen appears; wait for its footer before scrolling down to the mode.
        assertTrue("the screen has loaded", AccessibilityDriver.waitForText("Add Custom Context Mode", scroll = true))
        assertTrue("the custom mode is listed (it is at the end of a long list)", AccessibilityDriver.waitForText(mode, scroll = true))
        AccessibilityDriver.tapText("Delete custom mode")
        assertTrue("it asks first", AccessibilityDriver.waitForText("Delete Mode?"))
    }

    @Test
    fun tappingDeleteAsksFirstAndCancelKeepsTheMode() {
        ActivityScenario.launch(ContextModesActivity::class.java).use {
            tapDelete()
            assertTrue("nothing is deleted while the dialog is open", modeIsSaved())
            AccessibilityDriver.tapText("Cancel")
            assertTrue("the dialog closes", AccessibilityDriver.waitForText("Delete Mode?", present = false))
            assertTrue("cancelling keeps the mode", modeIsSaved())
            assertTrue("and it is still listed", AccessibilityDriver.hasText(mode))
        }
    }

    @Test
    fun confirmingDeleteRemovesTheMode() {
        ActivityScenario.launch(ContextModesActivity::class.java).use {
            tapDelete()
            AccessibilityDriver.tapText("Delete")
            assertTrue("the dialog closes", AccessibilityDriver.waitForText("Delete Mode?", present = false))
            assertFalse("confirming deletes the mode", modeIsSaved())
            assertTrue("and it leaves the list", AccessibilityDriver.waitForText(mode, present = false))
        }
    }
}
