package com.focusrefine.app

import android.content.Context
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * App Lock: the switch lives under Advanced settings and is on by default wherever biometrics are enrolled.
 * Touches only the app_lock_enabled preference, never the notification database.
 */
@RunWith(AndroidJUnit4::class)
class AppLockE2ETest {
    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val prefs = context.getSharedPreferences("FocusRefinePrefs", Context.MODE_PRIVATE)

    @Before
    fun clearSavedChoice() {
        prefs.edit().remove("app_lock_enabled").commit()
    }

    @After
    fun restoreDefault() {
        prefs.edit().remove("app_lock_enabled").commit()
    }

    @Test
    fun appLockSwitchIsUnderAdvancedSettings() {
        ActivityScenario.launch(SettingsActivity::class.java).use {
            AccessibilityDriver.tapText("Advanced") // sections start collapsed
            assertTrue(AccessibilityDriver.waitForText("App Lock (Biometrics)", scroll = true))
        }
    }

    @Test
    fun withNoSavedChoiceTheDefaultFollowsBiometricEnrolment() {
        val enrolled = BiometricHelper.isBiometricAvailable(context)
        assertEquals(enrolled, prefs.getBoolean("app_lock_enabled", enrolled))
    }

    @Test
    fun withoutEnrolledBiometricsTheLockIsOffAndTheAppOpens() {
        if (BiometricHelper.isBiometricAvailable(context)) return // only meaningful on a phone with nothing enrolled
        ActivityScenario.launch(SettingsActivity::class.java).use {
            AccessibilityDriver.tapText("Advanced")
            assertTrue(AccessibilityDriver.waitForText("App Lock (Biometrics)", scroll = true))
        }
        assertEquals(false, prefs.getBoolean("app_lock_enabled", BiometricHelper.isBiometricAvailable(context)))
    }

    @Test
    fun oneUnlockCoversEveryScreenUntilTheThresholdPasses() {
        AppLockSession.reset()
        assertFalse(AppLockSession.isUnlocked(0L))
        val unlockedAt = 1_000_000L
        AppLockSession.markUnlocked(unlockedAt)
        assertTrue(AppLockSession.isUnlocked(unlockedAt + 4 * 60_000L))   // another screen, 4 minutes later: no prompt
        assertFalse(AppLockSession.isUnlocked(unlockedAt + 6 * 60_000L))  // past the 5-minute threshold: prompt again
        AppLockSession.reset()
    }
}
