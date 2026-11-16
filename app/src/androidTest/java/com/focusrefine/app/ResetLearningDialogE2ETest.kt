package com.focusrefine.app

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The "Reset local learning data" row in Settings asks for confirmation before it resets the counter.
 * Uses the app's real preferences: emulator only.
 */
@RunWith(AndroidJUnit4::class)
class ResetLearningDialogE2ETest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs get() = context.getSharedPreferences("FocusRefinePrefs", Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        prefs.edit().putInt("suppressed_count", 5).commit()
    }

    @After
    fun tearDown() {
        prefs.edit().putInt("suppressed_count", 0).commit()
    }

    private fun openResetDialog() {
        AccessibilityDriver.tapText("Learning")
        AccessibilityDriver.tapText("Reset local learning data")
        AccessibilityDriver.expectText("Reset Learning Data?")
    }

    @Test
    fun cancelingTheDialogKeepsTheLearningData() {
        ActivityScenario.launch(SettingsActivity::class.java).use {
            openResetDialog()
            AccessibilityDriver.tapText("Cancel")
            AccessibilityDriver.expectText("Reset Learning Data?", present = false)
            assertEquals("nothing was reset", 5, prefs.getInt("suppressed_count", -1))
        }
    }

    @Test
    fun confirmingTheDialogResetsTheLearningData() {
        ActivityScenario.launch(SettingsActivity::class.java).use {
            openResetDialog()
            AccessibilityDriver.tapText("Reset")
            AccessibilityDriver.expectText("Reset Learning Data?", present = false)
            val deadline = System.currentTimeMillis() + 20_000
            while (prefs.getInt("suppressed_count", -1) != 0 && System.currentTimeMillis() < deadline) Thread.sleep(300)
            assertEquals("the counter was reset", 0, prefs.getInt("suppressed_count", -1))
        }
    }
}
