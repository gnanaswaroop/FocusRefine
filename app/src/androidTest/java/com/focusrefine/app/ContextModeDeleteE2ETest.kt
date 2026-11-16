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

/**
 * Deleting a custom context mode asks for confirmation first. Uses the app's real preferences: emulator only.
 */
@RunWith(AndroidJUnit4::class)
class ContextModeDeleteE2ETest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs get() = context.getSharedPreferences("FocusRefinePrefs", Context.MODE_PRIVATE)
    private val modeName = "E2E Delete Test"

    @Before
    fun setUp() {
        prefs.edit()
            .putString("custom_modes_list", modeName)
            .putString("custom_mode_prompt_$modeName", "urgent only")
            .commit()
    }

    @After
    fun tearDown() {
        prefs.edit().remove("custom_modes_list").remove("custom_mode_prompt_$modeName").commit()
    }

    @Test
    fun cancelingTheDialogKeepsTheCustomMode() {
        ActivityScenario.launch(ContextModesActivity::class.java).use {
            AccessibilityDriver.expectText(modeName, scroll = true)
            AccessibilityDriver.tapText("Delete custom mode")
            AccessibilityDriver.expectText("Delete Custom Mode?")
            AccessibilityDriver.tapText("Cancel")
            AccessibilityDriver.expectText("Delete Custom Mode?", present = false)
            assertTrue("the mode is still there", prefs.getString("custom_modes_list", "")!!.contains(modeName))
            assertEquals("urgent only", prefs.getString("custom_mode_prompt_$modeName", null))
        }
    }

    @Test
    fun confirmingTheDialogDeletesTheCustomModeAndItsPrompt() {
        ActivityScenario.launch(ContextModesActivity::class.java).use {
            AccessibilityDriver.expectText(modeName, scroll = true)
            AccessibilityDriver.tapText("Delete custom mode")
            AccessibilityDriver.expectText("Delete Custom Mode?")
            AccessibilityDriver.tapText("Delete")
            AccessibilityDriver.expectText("Delete Custom Mode?", present = false)
            assertFalse("the mode is gone", prefs.getString("custom_modes_list", "")!!.contains(modeName))
            assertEquals(null, prefs.getString("custom_mode_prompt_$modeName", null))
        }
    }
}
