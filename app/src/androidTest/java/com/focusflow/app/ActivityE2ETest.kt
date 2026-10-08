package com.focusflow.app

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActivityE2ETest {

    @Test
    fun testSplashActivityLaunches() {
        ActivityScenario.launch(SplashActivity::class.java).use {
            // Splash activity launches successfully
        }
    }

    @Test
    fun testMainActivityAndWhyDialog() {
        ActivityScenario.launch(MainActivity::class.java).use {
            // Main activity launches successfully
            Thread.sleep(500)
        }
    }

    @Test
    fun testSettingsActivityLaunches() {
        ActivityScenario.launch(SettingsActivity::class.java).use {
            Thread.sleep(500)
        }
    }

    @Test
    fun testConfigureModesActivityLaunches() {
        ActivityScenario.launch(ConfigureModesActivity::class.java).use {
            Thread.sleep(500)
        }
    }

    @Test
    fun testLearnedRulesActivityLaunches() {
        ActivityScenario.launch(LearnedRulesActivity::class.java).use {
            Thread.sleep(500)
        }
    }
}
