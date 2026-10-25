package com.focusrefine.app

import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Issue #12: the Home title row. A bigger, aligned logo, the full app name, comfortable buttons, and a layout that holds
 * on a narrow phone and with large text. Changes the emulator's display size and font scale while it runs (restored
 * afterwards), so it is for an emulator only.
 */
@RunWith(AndroidJUnit4::class)
class HomeHeaderE2ETest {

    @After
    fun restoreDisplay() {
        ServiceHarness.shell("wm size reset; wm density reset; settings put system font_scale 1.0")
        Thread.sleep(1500)
    }

    private fun narrowPhone() { // 720x1520 at 360dpi is 320dp wide
        ServiceHarness.shell("wm size 720x1520; wm density 360")
        Thread.sleep(2000)
    }

    // Geometry helpers kept here (not in AccessibilityDriver) so this change stays independent of the other issue branches.
    private fun root() = InstrumentationRegistry.getInstrumentation().uiAutomation.rootInActiveWindow

    private fun findNode(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        if (node.text?.contains(text, ignoreCase = true) == true || node.contentDescription?.contains(text, ignoreCase = true) == true) return node
        for (i in 0 until node.childCount) node.getChild(i)?.let { child -> findNode(child, text)?.let { return it } }
        return null
    }

    /** Screen bounds of the first node containing [text], or null. */
    private fun boundsOf(text: String): Rect? {
        val node = root()?.let { findNode(it, text) } ?: return null
        return Rect().also { node.getBoundsInScreen(it) }
    }

    /** Height in pixels of the clickable element that contains [text] (its touch target), or null. */
    private fun clickableHeightPx(text: String): Int? {
        var node: AccessibilityNodeInfo? = root()?.let { findNode(it, text) }
        while (node != null && !node.isClickable) node = node.parent
        return node?.let { Rect().also { r -> it.getBoundsInScreen(r) }.height() }
    }

    private fun check(minLogoDp: Int, buttonMinDp: Int, expectHelp: Boolean) {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            var density = 1f
            var widthPx = 0
            var fontScale = 1f
            scenario.onActivity {
                density = it.resources.displayMetrics.density
                widthPx = it.resources.displayMetrics.widthPixels
                fontScale = it.resources.configuration.fontScale
            }
            AccessibilityDriver.expectText("FocusRefine") // the header itself; other parts of Home may be off screen at 320dp
            AccessibilityDriver.expectText("App Logo")
            val logo = boundsOf("App Logo")!!
            val title = boundsOf("FocusRefine")!!
            val badge = boundsOf("On Device AI")!!
            val settings = boundsOf("Settings")!!

            assertTrue(
                "logo is at least $minLogoDp dp: ${logo.width() / density}",
                logo.width() >= minLogoDp * density - 1 && logo.height() >= minLogoDp * density - 1
            )

            // The logo is vertically centred on the title block (name + badge).
            val blockCenter = (title.top + badge.bottom) / 2f
            assertTrue("logo centre ${logo.centerY()} vs title block centre $blockCenter", Math.abs(logo.centerY() - blockCenter) <= 6 * density)
            assertTrue("a gap between logo and title: ${(title.left - logo.right) / density} dp", title.left - logo.right >= 6 * density)

            // The app name is not cut off: its box is as wide as the text needs.
            val paint = Paint().apply {
                typeface = Typeface.create("sans-serif", Typeface.BOLD)
                textSize = (if (widthPx / density < 360) 20f else 22f) * fontScale * density
            }
            val needed = paint.measureText("FocusRefine")
            assertTrue("name is not truncated: box ${title.width()} px, needs about ${needed.toInt()} px", title.width() >= needed * 0.92f)

            // Nothing runs off the screen, and the buttons are a proper touch target.
            listOf(logo, title, badge, settings).forEach {
                assertTrue("inside the screen: $it of $widthPx", it.left >= 0 && it.right <= widthPx)
            }
            val settingsTarget = clickableHeightPx("Settings")!!
            assertTrue("Settings button is at least $buttonMinDp dp: ${settingsTarget / density}", settingsTarget >= buttonMinDp * density - 1)
            assertEquals("Help button shown: $expectHelp", expectHelp, AccessibilityDriver.hasText("Help"))
            if (expectHelp) {
                val helpTarget = clickableHeightPx("Help")!!
                assertTrue("Help button is at least $buttonMinDp dp: ${helpTarget / density}", helpTarget >= buttonMinDp * density - 1)
                val help = boundsOf("Help")!!
                assertTrue("title and Help do not overlap", title.right <= help.left + 2)
            }
        }
    }

    @Test
    fun theHeaderIsLargeAlignedAndComfortableAtNormalWidth() = check(minLogoDp = 48, buttonMinDp = 48, expectHelp = true)

    @Test
    fun theHeaderHoldsOnA320dpPhone() {
        narrowPhone()
        check(minLogoDp = 40, buttonMinDp = 44, expectHelp = true)
    }

    @Test
    fun theHeaderHoldsOnA320dpPhoneWithLargeText() {
        narrowPhone()
        ServiceHarness.shell("settings put system font_scale 1.3")
        Thread.sleep(2000)
        check(minLogoDp = 40, buttonMinDp = 44, expectHelp = false) // the app name wins over the "?" button here
    }

    /** True when the "On Device AI" badge sits to the right of the app name on the same line, not under it. */
    private fun badgeBesideName(): Boolean {
        AccessibilityDriver.expectText("FocusRefine")
        val title = boundsOf("FocusRefine")!!
        val badge = boundsOf("On Device AI")!!
        return badge.left >= title.right - 2 && badge.centerY() in title.top..title.bottom
    }

    @Test
    fun theBadgeSitsBesideTheNameAtTheDefaultWidth() {
        ActivityScenario.launch(MainActivity::class.java).use {
            assertTrue("one line: badge beside the name", badgeBesideName())
        }
    }

    @Test
    fun theBadgeSitsBesideTheNameOnAPhoneSizedScreenWithItsDisplaySizeSetting() {
        // The user's Android phone: 1008x2244 at 408dpi is 395dp wide, which the first version of this layout missed.
        ServiceHarness.shell("wm size 1008x2244; wm density 408")
        Thread.sleep(2000)
        ActivityScenario.launch(MainActivity::class.java).use {
            assertTrue("one line at 395dp: badge beside the name", badgeBesideName())
            check(minLogoDp = 48, buttonMinDp = 48, expectHelp = true)
        }
    }

    @Test
    fun theBadgeGoesUnderTheNameWhenThereIsNoRoomBesideIt() {
        narrowPhone()
        ActivityScenario.launch(MainActivity::class.java).use {
            assertTrue("stacked on a 320dp phone", !badgeBesideName())
        }
    }

    @Test
    fun theHelpAndSettingsButtonsStillOpenTheirScreens() {
        ActivityScenario.launch(MainActivity::class.java).use {
            AccessibilityDriver.tapText("Help")
            AccessibilityDriver.expectText("Keep the noise out", why = "Help opens from the header")
        }
        ActivityScenario.launch(MainActivity::class.java).use {
            AccessibilityDriver.tapText("Settings")
            AccessibilityDriver.expectText("Classification Engine", why = "Settings opens from the header")
        }
    }
}
