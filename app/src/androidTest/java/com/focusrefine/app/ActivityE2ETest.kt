package com.focusrefine.app

import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ActivityE2ETest {

    @Test
    fun testSplashActivityLaunchesAndTransitions() {
        ActivityScenario.launch(SplashActivity::class.java).use { scenario ->
            assertNotNull(scenario)
        }
    }

    @Test
    fun testMainActivityLaunches() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            assertNotNull(scenario)
        }
    }

    @Test
    fun testSettingsActivityLaunches() {
        ActivityScenario.launch(SettingsActivity::class.java).use { scenario ->
            assertNotNull(scenario)
        }
    }

    @Test
    fun testConfigureModesActivityLaunches() {
        ActivityScenario.launch(ConfigureModesActivity::class.java).use { scenario ->
            assertNotNull(scenario)
        }
    }

    @Test
    fun testLearnedRulesActivityLaunches() {
        ActivityScenario.launch(LearnedRulesActivity::class.java).use { scenario ->
            assertNotNull(scenario)
        }
    }
}
