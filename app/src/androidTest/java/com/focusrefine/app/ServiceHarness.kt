package com.focusrefine.app

import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry

/**
 * Helpers for end-to-end tests that need the real listener service: they post notifications from the shell (as
 * the adb shell would), wait for the service to store its decision, and inspect the notification shade.
 * Uses the app's real database and preferences, so these tests are for an emulator only.
 */
object ServiceHarness {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    val context: Context get() = instrumentation.targetContext
    val dao get() = AppDatabase.getDatabase(context).notificationDao()
    private const val LISTENER = "com.focusrefine.app/com.focusrefine.app.NotificationFilterService"
    private var counter = 0

    /** Runs [cmd] in a real shell (quotes work; UiAutomation.executeShellCommand does not parse them). */
    fun shell(cmd: String): String {
        val (stdout, stdin) = instrumentation.uiAutomation.executeShellCommandRw("sh")
        ParcelFileDescriptor.AutoCloseOutputStream(stdin).use { it.write((cmd + "\n").toByteArray()) }
        return ParcelFileDescriptor.AutoCloseInputStream(stdout).use { it.readBytes().toString(Charsets.UTF_8) }
    }

    /** Posts a notification the way adb does; returns the tag it was posted under. */
    fun post(title: String, text: String): String {
        val tag = "h${counter++ % 20}"
        shell("cmd notification post -t '$title' $tag '$text'")
        return tag
    }

    /** Waits for the service to store a (not learned) row for this notification. */
    fun awaitRow(title: String, text: String, timeoutMs: Long = 30_000): NotificationEntity {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            dao.getHistory().firstOrNull { it.title == title && it.text == text }?.let { return it }
            Thread.sleep(300)
        }
        throw AssertionError("the service never stored '$title | $text'")
    }

    /** Asserts nothing is stored for this notification within [waitMs]. */
    fun noRowFor(title: String, text: String, waitMs: Long = 6_000): Boolean {
        Thread.sleep(waitMs)
        return dao.getAll().none { it.title == title && it.text == text }
    }

    /** True while the posted notification (by tag) is still in the shade. */
    fun isInShade(tag: String): Boolean =
        shell("dumpsys notification --noredact").lineSequence().any { it.contains("NotificationRecord(") && it.contains("pkg=com.android.shell") && Regex("tag=$tag\\b").containsMatchIn(it) }

    /**
     * Fresh state: the given preferences on top of the defaults (AI on, Balanced, exceptions on, learning on), an empty
     * history, no personal model, and a listener that has been restarted so it re-reads the preferences.
     */
    fun start(mode: String, extra: Map<String, Any> = emptyMap(), serviceRunning: Boolean = true) {
        val editor = context.getSharedPreferences("FocusRefinePrefs", Context.MODE_PRIVATE).edit()
            .putString("current_mode", mode).putBoolean("use_ai_model", true).putBoolean("service_running", true) // on for the readiness probe
            .putInt("filter_strength", 1).putBoolean("learn_from_choices", true)
            .putBoolean("exception_nav_prompts", true).putBoolean("exception_calls", true).putBoolean("exception_security_otps", true)
        for ((k, v) in extra) when (v) {
            is Boolean -> editor.putBoolean(k, v)
            is Int -> editor.putInt(k, v)
            is String -> editor.putString(k, v)
            else -> error("unsupported preference type for $k")
        }
        editor.commit()
        dao.deleteAll()
        PersonalLearning.clear(context)
        ReviewQueue.refresh(context)
        shell("cmd notification disallow_listener $LISTENER"); Thread.sleep(1500)
        shell("cmd notification allow_listener $LISTENER")
        val deadline = System.currentTimeMillis() + 60_000
        while (dao.getHistory().none { it.title == "zz-probe" } && System.currentTimeMillis() < deadline) {
            post("zz-probe", "ready check"); Thread.sleep(3000)
        }
        check(dao.getHistory().any { it.title == "zz-probe" }) { "the listener service never started" }
        dao.deleteAll()
        ReviewQueue.refresh(context)
        if (!serviceRunning) context.getSharedPreferences("FocusRefinePrefs", Context.MODE_PRIVATE).edit().putBoolean("service_running", false).commit()
    }

    fun stop() {
        dao.deleteAll()
        dao.deleteLearnedRules()
        PersonalLearning.clear(context)
        ReviewQueue.refresh(context)
        shell("am force-stop com.android.shell")
    }
}
