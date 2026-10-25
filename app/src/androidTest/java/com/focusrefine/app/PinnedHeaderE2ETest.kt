package com.focusrefine.app

import android.app.Activity
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Issue #13: the title bar and Back button stay in view while only the content scrolls. */
@RunWith(AndroidJUnit4::class)
class PinnedHeaderE2ETest {

    private fun <A : Activity> checkPinned(activity: Class<A>, title: String, firstContent: String, expand: List<String> = emptyList()) {
        ActivityScenario.launch(activity).use { scenario ->
            AccessibilityDriver.expectText(firstContent, why = "the content starts at the top")
            expand.forEach { AccessibilityDriver.tapText(it) } // sections start collapsed; a longer page gives the scroll room
            AccessibilityDriver.scrollDown(4)

            assertFalse("the content scrolled away from the top ('$firstContent' should be gone)", AccessibilityDriver.hasText(firstContent))
            AccessibilityDriver.expectText("Back", why = "the Back button is still pinned after scrolling")
            AccessibilityDriver.expectText(title, why = "the title is still pinned after scrolling")

            AccessibilityDriver.tapText("Back")
            val deadline = System.currentTimeMillis() + 10_000
            while (scenario.state != Lifecycle.State.DESTROYED && System.currentTimeMillis() < deadline) Thread.sleep(200)
            assertTrue("Back works while scrolled", scenario.state == Lifecycle.State.DESTROYED)
        }
    }

    @Test
    fun settingsKeepsItsTitleBarWhileScrolling() = checkPinned(SettingsActivity::class.java, "Settings", "Classification Engine", expand = listOf("Learning & Feedback"))

    @Test
    fun helpKeepsItsTitleBarWhileScrolling() = checkPinned(HelpActivity::class.java, "Help & how it works", "Keep the noise out")

    @Test
    fun theChangelogKeepsItsTitleBarWhileScrolling() = checkPinned(ChangelogActivity::class.java, "What's new", "You have version")
}
