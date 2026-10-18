package com.focusrefine.app

import android.content.Context
import android.content.Intent
import android.os.ParcelFileDescriptor
import android.view.accessibility.AccessibilityNodeInfo
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Full flow with the real listener service: a notification is posted from the shell, classified and stored by
 * the service, the user flips it in the real detail screen, and a similar notification is then decided
 * differently. Both directions. Uses the app's real database and preferences: emulator only.
 */
@RunWith(AndroidJUnit4::class)
class PersonalLearningFlowE2ETest {

    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context: Context = instrumentation.targetContext
    private val dao get() = AppDatabase.getDatabase(context).notificationDao()
    private val listener = "com.focusrefine.app/com.focusrefine.app.NotificationFilterService"
    private val mode = "Sleeping / Rest"
    private var counter = 0

    /** Runs [cmd] in a real shell (so quotes work): UiAutomation.executeShellCommand does not parse them. */
    private fun shell(cmd: String): String {
        val (stdout, stdin) = instrumentation.uiAutomation.executeShellCommandRw("sh")
        ParcelFileDescriptor.AutoCloseOutputStream(stdin).use { it.write((cmd + "\n").toByteArray()) }
        return ParcelFileDescriptor.AutoCloseInputStream(stdout).use { it.readBytes().toString(Charsets.UTF_8) }
    }

    private fun post(title: String, text: String) {
        shell("cmd notification post -t '$title' pl${counter++ % 20} '$text'")
    }

    /** Waits for the service to store a (non-learned) row for this notification. */
    private fun awaitRow(title: String, text: String, timeoutMs: Long = 30_000): NotificationEntity {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            dao.getHistory().firstOrNull { it.title == title && it.text == text }?.let { return it }
            Thread.sleep(300)
        }
        throw AssertionError("the service never stored '$title | $text'")
    }

    /**
     * Taps the on-screen element containing [text] through the accessibility tree (the Compose test rule needs
     * Espresso, which does not run on the newest emulator images). Scrolls down while it is not yet visible.
     */
    private fun tapText(text: String, timeoutMs: Long = 20_000) {
        val deadline = System.currentTimeMillis() + timeoutMs
        var last = "no window yet"
        while (System.currentTimeMillis() < deadline) {
            val root = instrumentation.uiAutomation.rootInActiveWindow
            if (root != null) {
                // Compose's accessibility provider does not implement text search, so walk the tree.
                val match = findByText(root, text)
                var node: AccessibilityNodeInfo? = match
                while (node != null && !node.isClickable) node = node.parent
                last = "window ${root.packageName}, text found=${match != null}, clickable ancestor=${node != null}"
                if (node != null) {
                    val clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    last += ", click=$clicked"
                    if (clicked) return
                }
                scrollable(root)?.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
            }
            Thread.sleep(500)
        }
        throw AssertionError("could not find and tap '$text' ($last)")
    }

    private fun findByText(node: AccessibilityNodeInfo, text: String): AccessibilityNodeInfo? {
        if (node.text?.contains(text, ignoreCase = true) == true) return node
        for (i in 0 until node.childCount) node.getChild(i)?.let { child -> findByText(child, text)?.let { return it } }
        return null
    }

    private fun scrollable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) node.getChild(i)?.let { child -> scrollable(child)?.let { return it } }
        return null
    }

    @Before
    fun setUp() {
        context.getSharedPreferences("FocusRefinePrefs", Context.MODE_PRIVATE).edit()
            .putString("current_mode", mode).putBoolean("use_ai_model", true).putBoolean("service_running", true)
            .putInt("filter_strength", 1).putBoolean("learn_from_choices", true)
            .putBoolean("exception_nav_prompts", true).putBoolean("exception_calls", true).putBoolean("exception_security_otps", true)
            .commit()
        dao.deleteAll()
        PersonalLearning.clear(context)
        // Restart the listener so the service re-reads use_ai_model and loads the model.
        shell("cmd notification disallow_listener $listener"); Thread.sleep(1500)
        shell("cmd notification allow_listener $listener")
        // The listener needs a moment to rebind: post probes until one is recorded.
        val deadline = System.currentTimeMillis() + 60_000
        while (dao.getHistory().none { it.title == "zz-probe" } && System.currentTimeMillis() < deadline) {
            post("zz-probe", "ready check"); Thread.sleep(3000)
        }
        assertTrue("the listener service never started", dao.getHistory().any { it.title == "zz-probe" })
        assertTrue("the embedding model did not load in the service",
            context.getSharedPreferences("FocusRefinePrefs", Context.MODE_PRIVATE).getBoolean(EmbeddingClassifier.PREF_ENGINE_LOADED, false))
    }

    @After
    fun tearDown() {
        dao.deleteLearnedRules()
        PersonalLearning.clear(context)
        shell("am force-stop com.android.shell")
    }

    private fun openDetailAndTap(row: NotificationEntity, buttonText: String) {
        val intent = Intent(context, NotificationDetailActivity::class.java)
            .putExtra("notif_id", row.id).putExtra("title", row.title).putExtra("text", row.text)
            .putExtra("package_name", row.packageName).putExtra("timestamp", row.timestamp)
            .putExtra("is_urgent", row.isUrgent).putExtra("explanation", row.explanation)
        ActivityScenario.launch<NotificationDetailActivity>(intent).use {
            tapText(buttonText)
            val deadline = System.currentTimeMillis() + 60_000 // learning embeds the anchors first
            while (PersonalLearning.correctionCount(context) == 0 && System.currentTimeMillis() < deadline) Thread.sleep(300)
        }
        assertEquals("the correction was not stored", 1, PersonalLearning.correctionCount(context))
        // Keep only the personal model: the learned *rule* would otherwise decide the next, similar, notification.
        dao.deleteLearnedRules()
    }

    @Test
    fun mutedNotificationBecomesShownAfterTheUserOverridesIt() {
        val title = "Greenwood School"
        post(title, "Early dismissal today due to a safety incident")
        val first = awaitRow(title, "Early dismissal today due to a safety incident")
        assertFalse("premise: muted before learning (${first.explanation})", first.isUrgent)
        assertTrue(first.explanation!!.contains("AI Pipeline"))
        assertFalse(first.explanation!!.contains("past choices"))

        openDetailAndTap(first, "Show notifications like these in the future")

        post(title, "Early dismissal today due to a safety issue")
        val second = awaitRow(title, "Early dismissal today due to a safety issue")
        assertTrue("shown after learning (${second.explanation})", second.isUrgent)
        assertTrue(second.explanation!!, second.explanation!!.contains("pushed this toward shown"))

        // An unrelated promo is untouched.
        post("Amazon", "Lightning deal: 60% off smart watches")
        assertFalse(awaitRow("Amazon", "Lightning deal: 60% off smart watches").isUrgent)
    }

    @Test
    fun shownNotificationBecomesMutedAfterTheUserOverridesIt_andResetRestoresIt() {
        val title = "Apple Watch"
        post(title, "High heart rate notification: 140 BPM while inactive")
        val first = awaitRow(title, "High heart rate notification: 140 BPM while inactive")
        assertTrue("premise: shown before learning (${first.explanation})", first.isUrgent)
        assertTrue(first.explanation!!.contains("AI Pipeline"))

        openDetailAndTap(first, "Hide notifications like these in the future")

        post(title, "High heart rate notification: 150 BPM while inactive")
        val second = awaitRow(title, "High heart rate notification: 150 BPM while inactive")
        assertFalse("muted after learning (${second.explanation})", second.isUrgent)
        assertTrue(second.explanation!!, second.explanation!!.contains("pushed this toward muted"))

        // "Reset learning data" returns to the built-in decision.
        PersonalLearning.clear(context)
        post(title, "High heart rate notification: 160 BPM while inactive")
        val third = awaitRow(title, "High heart rate notification: 160 BPM while inactive")
        assertTrue("shown again after reset (${third.explanation})", third.isUrgent)
        assertFalse(third.explanation!!.contains("past choices"))
        assertNotNull(third.explanation)
    }
}
