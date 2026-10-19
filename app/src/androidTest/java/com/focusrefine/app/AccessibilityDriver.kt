package com.focusrefine.app

import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.platform.app.InstrumentationRegistry

/**
 * Drives the screen through the accessibility tree. The Compose test rule needs Espresso, which does not run on
 * the newest emulator images, so end-to-end tests use this instead. Compose's accessibility provider does not
 * implement text search, so the tree is walked by hand.
 */
object AccessibilityDriver {
    private val automation get() = InstrumentationRegistry.getInstrumentation().uiAutomation

    private fun matches(node: AccessibilityNodeInfo, text: String) =
        node.text?.contains(text, ignoreCase = true) == true || node.contentDescription?.contains(text, ignoreCase = true) == true

    private fun find(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        if (matches(node, text)) return node
        for (i in 0 until node.childCount) node.getChild(i)?.let { child -> find(child, text)?.let { return it } }
        return null
    }

    /** Every node containing [text], in tree order (a title and a button can both contain the same word). */
    private fun findAll(node: AccessibilityNodeInfo, text: String, out: MutableList<AccessibilityNodeInfo> = mutableListOf()): List<AccessibilityNodeInfo> {
        if (matches(node, text)) out += node
        for (i in 0 until node.childCount) node.getChild(i)?.let { findAll(it, text, out) }
        return out
    }

    private fun scrollable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) node.getChild(i)?.let { child -> scrollable(child)?.let { return it } }
        return null
    }

    fun hasText(text: String): Boolean = automation.rootInActiveWindow?.let { find(it, text) != null } ?: false

    /**
     * Waits until [text] is on screen (true) or gone (false when [present] is false). With [scroll], scrolls down while
     * waiting for it to appear, for content that starts below the visible part of the screen.
     */
    fun waitForText(text: String, present: Boolean = true, timeoutMs: Long = 20_000, scroll: Boolean = false): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (hasText(text) == present) return true
            if (scroll && present) automation.rootInActiveWindow?.let { scrollable(it)?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) }
            Thread.sleep(300)
        }
        return false
    }

    /** Taps the clickable element containing [text], scrolling the screen while it is not yet visible. */
    fun tapText(text: String, timeoutMs: Long = 20_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        var last = "no window yet"
        while (System.currentTimeMillis() < deadline) {
            val root = automation.rootInActiveWindow
            if (root != null) {
                val all = findAll(root, text)
                last = "${all.size} node(s) with the text"
                for (match in all) {
                    var node: AccessibilityNodeInfo? = match
                    while (node != null && !node.isClickable) node = node.parent
                    if (node != null && node.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return
                }
                scrollable(root)?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            }
            Thread.sleep(500)
        }
        throw AssertionError("could not find and tap '$text' ($last)")
    }
}
