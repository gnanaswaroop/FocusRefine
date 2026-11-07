package com.focusrefine.app

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * "Reset local learning data" in Settings asks before it does anything. Cancelling keeps the data; confirming clears
 * it. Uses the app's real preferences: emulator only.
 */
@RunWith(AndroidJUnit4::class)
class ResetLearningDialogE2ETest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs get() = context.getSharedPreferences("FocusRefinePrefs", Context.MODE_PRIVATE)

    @Before
    fun setUp() {
        prefs.edit().putInt("suppressed_count", 7).commit()
    }

    @After
    fun tearDown() {
        prefs.edit().putInt("suppressed_count", 0).commit()
    }

    private fun tapReset() {
        AccessibilityDriver.tapText("Learning") // its own card; sections start collapsed
        AccessibilityDriver.tapText("Reset local learning data")
        assertTrue("it asks first", AccessibilityDriver.waitForText("Reset Local Learning Data?"))
    }

    @Test
    fun tappingResetAsksFirstAndCancelKeepsTheData() {
        ActivityScenario.launch(SettingsActivity::class.java).use {
            tapReset()
            assertEquals("nothing is cleared while the dialog is open", 7, prefs.getInt("suppressed_count", -1))
            AccessibilityDriver.tapText("Cancel")
            assertTrue("the dialog closes", AccessibilityDriver.waitForText("Reset Local Learning Data?", present = false))
            assertEquals("cancelling keeps the data", 7, prefs.getInt("suppressed_count", -1))
        }
    }

    @Test
    fun confirmingResetClearsTheData() {
        ActivityScenario.launch(SettingsActivity::class.java).use {
            tapReset()
            AccessibilityDriver.tapText("Reset Data")
            assertTrue("the dialog closes", AccessibilityDriver.waitForText("Reset Local Learning Data?", present = false))
            assertEquals("confirming clears the data", 0, prefs.getInt("suppressed_count", -1))
        }
    }
}
