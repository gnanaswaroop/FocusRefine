package com.focusrefine.app

import android.app.Notification
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End to end through the real listener service: notifications are posted from the shell, the service classifies and
 * stores them, and the tests read back the decision, its confidence, the shade and the ongoing notification.
 * Uses the app's real database and preferences: emulator only.
 *
 * The notifications were chosen from the measured scores so each is far from (or, where stated, right next to) the
 * decision line, and none contains an exception keyword unless the test is about exceptions.
 */
@RunWith(AndroidJUnit4::class)
class PipelineE2ETest {

    private val h = ServiceHarness
    private val focus = "Deep Work / Focus"
    private val sleep = "Sleeping / Rest"
    private val commute = "Commute"
    private val workout = "Workout / Gym"

    // Shown in Commute (+2.21) and Muted in Workout (-6.28), the production engine with no app context
    private val standup = "Calendar" to "Standup in 5 minutes"
    // Commute, a hair above the threshold (+0.09): Shown at Balanced, Muted at Strict; a close call in Sleeping (+0.89)
    private val borderline = "Office" to "Server down: production alert, respond now"
    // Sleeping, clearly urgent (+5.04) and clearly routine (-3.34)
    private val clearlyUrgent = "Doctor" to "Please call me back, lab results are ready"
    private val clearlyRoutine = "Myntra" to "Lightning deal: 50% off sneakers"

    @After
    fun tearDown() {
        h.context.getSharedPreferences("FocusRefinePrefs", Context.MODE_PRIVATE).edit().putBoolean("service_running", true).putInt("filter_strength", 1).commit()
        h.stop()
    }

    private fun postAndRead(n: Pair<String, String>): NotificationEntity {
        h.post(n.first, n.second)
        return h.awaitRow(n.first, n.second)
    }

    private fun setPref(f: android.content.SharedPreferences.Editor.() -> Unit) {
        h.context.getSharedPreferences("FocusRefinePrefs", Context.MODE_PRIVATE).edit().apply(f).commit()
    }

    @Test
    fun theSameNotificationIsDecidedByTheActiveMode() {
        h.start(commute)
        val inCommute = postAndRead(standup)
        assertTrue("shown while commuting (${inCommute.explanation})", inCommute.isUrgent)
        assertTrue(inCommute.explanation!!.contains("AI Pipeline") && inCommute.explanation!!.contains(commute))

        h.dao.deleteAll()
        setPref { putString("current_mode", workout) }
        val inWorkout = postAndRead(standup)
        assertFalse("muted while working out (${inWorkout.explanation})", inWorkout.isUrgent)
        assertTrue(inWorkout.explanation!!.contains(workout))
    }

    @Test
    fun filteringStrengthMovesABorderlineDecision() {
        h.start(commute)
        val balanced = postAndRead(borderline)
        assertTrue("shown at Balanced (${balanced.explanation})", balanced.isUrgent)

        h.dao.deleteAll(); setPref { putInt("filter_strength", 2) }
        val strict = postAndRead(borderline)
        assertFalse("muted at Strict (${strict.explanation})", strict.isUrgent)

        h.dao.deleteAll(); setPref { putInt("filter_strength", 0) }
        val gentle = postAndRead(borderline)
        assertTrue("shown at Gentle (${gentle.explanation})", gentle.isUrgent)
        assertTrue("and the app is more sure about it than at Balanced", gentle.confidence!! > balanced.confidence!!)
    }

    @Test
    fun clearCasesAreConfidentAndCloseCallsAreFlagged() {
        h.start(sleep)
        val urgent = postAndRead(clearlyUrgent)
        val routine = postAndRead(clearlyRoutine)
        val close = postAndRead(borderline)
        assertTrue(urgent.isUrgent); assertTrue("very sure: ${urgent.confidence}", urgent.confidence!! >= Confidence.VERY_SURE_FROM)
        assertFalse(routine.isUrgent); assertTrue("very sure: ${routine.confidence}", routine.confidence!! >= Confidence.VERY_SURE_FROM)
        assertTrue("a close call: ${close.confidence}", close.isUncertain())
        assertFalse(urgent.isUncertain()); assertFalse(routine.isUncertain())
        assertEquals(1, h.dao.countUncertain(Confidence.UNCERTAIN_BELOW))
    }

    @Test
    fun theOngoingNotificationCountsCloseCallsToReview() {
        h.start(sleep)
        postAndRead(borderline)
        val manager = h.context.getSystemService(NotificationManager::class.java)
        val deadline = System.currentTimeMillis() + 20_000
        var text = ""
        while (System.currentTimeMillis() < deadline && !text.contains("close call")) {
            text = manager.activeNotifications.firstOrNull { it.id == 1001 }?.notification?.extras?.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
            Thread.sleep(300)
        }
        assertTrue("ongoing notification says: '$text'", text.contains("1 close call to review"))
    }

    @Test
    fun mutedNotificationsLeaveTheShadeAndShownOnesStay() {
        h.start(sleep)
        val routineTag = h.post(clearlyRoutine.first, clearlyRoutine.second)
        h.awaitRow(clearlyRoutine.first, clearlyRoutine.second)
        val urgentTag = h.post(clearlyUrgent.first, clearlyUrgent.second)
        h.awaitRow(clearlyUrgent.first, clearlyUrgent.second)
        Thread.sleep(1500)
        assertFalse("a muted notification is cancelled", h.isInShade(routineTag))
        assertTrue("a shown notification stays", h.isInShade(urgentTag))
    }

    @Test
    fun aStoppedServiceLeavesNotificationsAlone() {
        h.start(sleep, serviceRunning = false)
        val tag = h.post(clearlyRoutine.first, clearlyRoutine.second)
        assertTrue("nothing is stored while filtering is off", h.noRowFor(clearlyRoutine.first, clearlyRoutine.second))
        assertTrue("and the notification is not cancelled", h.isInShade(tag))

        setPref { putBoolean("service_running", true) }
        h.post(clearlyRoutine.first, clearlyRoutine.second + "!")
        assertFalse(h.awaitRow(clearlyRoutine.first, clearlyRoutine.second + "!").isUrgent)
    }

    @Test
    fun theCallsExceptionCanBeSwitchedOff() {
        h.start(sleep)
        val on = postAndRead("Wife" to "Missed call (2)")
        assertTrue(on.isUrgent)
        assertTrue("decided by the exception: ${on.explanation}", on.explanation!!.contains("user exception"))
        assertEquals(Confidence.RULE, on.confidence)

        setPref { putBoolean("exception_calls", false) }
        val off = postAndRead("Wife" to "Missed call (3)")
        assertFalse("no exception any more: ${off.explanation}", off.explanation!!.contains("user exception"))
    }

    @Test
    fun aCustomModeUsesTheUsersOwnKeywords() {
        h.start("Gaming Night", mapOf("custom_mode_prompt_Gaming Night" to "fortnite, tournament"))
        val hit = postAndRead("Epic Games" to "Fortnite tournament starts in 10 minutes")
        assertTrue(hit.isUrgent)
        assertTrue(hit.explanation!!, hit.explanation!!.contains("Semantic Search") && hit.explanation!!.contains("'fortnite'"))
        val miss = postAndRead("Substack" to "Weekly digest of cooking tips")
        assertFalse(miss.isUrgent)
        assertTrue(miss.explanation!!.contains("Did not contain urgent prompt tokens"))
        assertNull("keyword matching reports no confidence", miss.confidence)
        assertEquals(0, h.dao.countUncertain(Confidence.UNCERTAIN_BELOW))
    }

    @Test
    fun aLearnedRuleBeatsTheModelAndIsCertain() {
        h.start(sleep)
        val first = postAndRead(borderline)
        assertTrue(first.isUrgent)
        FeedbackRecorder.apply(h.context, first, makeUrgent = false, activeMode = sleep, learn = true)

        h.post(borderline.first, borderline.second)
        val second = h.awaitRow(borderline.first, borderline.second)
        assertFalse("muted by the rule the user just taught", second.isUrgent)
        assertTrue(second.explanation!!, second.explanation!!.contains("learned"))
        assertEquals(Confidence.RULE, second.confidence)
    }

    @Test
    fun theStopButtonInTheOngoingNotificationTogglesFiltering() {
        h.start(sleep)
        val receiver = ComponentName(h.context, NotificationToggleReceiver::class.java)
        val prefs = h.context.getSharedPreferences("FocusRefinePrefs", Context.MODE_PRIVATE)
        fun toggle() = h.context.sendBroadcast(Intent(NotificationFilterService.ACTION_TOGGLE_SERVICE).setComponent(receiver))
        fun waitFor(expected: Boolean): Boolean {
            val deadline = System.currentTimeMillis() + 10_000
            while (System.currentTimeMillis() < deadline) {
                if (prefs.getBoolean("service_running", true) == expected) return true
                Thread.sleep(200)
            }
            return false
        }
        toggle(); assertTrue("stopped", waitFor(false))
        toggle(); assertTrue("started again", waitFor(true))
        assertNotNull(prefs)
        h.shell("cmd notification set_dnd off")
    }

    @Test
    fun theOngoingNotificationIsSilentAndDoesNotBuzzOnEveryMutedOne() {
        h.start(sleep)
        postAndRead(clearlyRoutine)
        val manager = h.context.getSystemService(NotificationManager::class.java)
        val deadline = System.currentTimeMillis() + 20_000
        while (System.currentTimeMillis() < deadline && manager.activeNotifications.none { it.id == 1001 }) Thread.sleep(300)
        val ongoing = manager.activeNotifications.first { it.id == 1001 }.notification
        val channel = manager.getNotificationChannel(ongoing.channelId)
        assertTrue("low importance, so no sound or vibration: ${channel.importance}", channel.importance <= NotificationManager.IMPORTANCE_LOW)
        assertFalse("channel does not vibrate", channel.shouldVibrate())
        assertNull("channel has no sound", channel.sound)
        assertTrue("alerts at most once", ongoing.flags and Notification.FLAG_ONLY_ALERT_ONCE != 0)
        assertNull("the old default-importance channel is gone", manager.getNotificationChannel("focusrefine_service_channel"))
    }

    @Test
    fun aMutedNotificationIsCancelledQuickly() {
        h.start(sleep)
        postAndRead(clearlyUrgent) // warms the model and anchors, as the first real notification would
        val tag = h.post(clearlyRoutine.first, clearlyRoutine.second)
        val start = System.currentTimeMillis()
        h.awaitRow(clearlyRoutine.first, clearlyRoutine.second)
        val took = System.currentTimeMillis() - start
        android.util.Log.i("VIBE_BENCH", "post to stored row: $took ms")
        assertTrue("decision took $took ms", took < 5_000)
    }

    @Test
    fun comingBackToTheAppDoesNotRepostTheOngoingNotification() {
        h.start(sleep)
        val manager = h.context.getSystemService(NotificationManager::class.java)
        val deadline = System.currentTimeMillis() + 20_000
        while (System.currentTimeMillis() < deadline && manager.activeNotifications.none { it.id == 1001 }) Thread.sleep(300)
        val before = manager.activeNotifications.first { it.id == 1001 }.postTime
        // Opening the app, leaving it and coming back is what used to restart the service and buzz each time.
        repeat(3) {
            androidx.test.core.app.ActivityScenario.launch(MainActivity::class.java).use { Thread.sleep(1500) }
            Thread.sleep(500)
        }
        val after = manager.activeNotifications.first { it.id == 1001 }.postTime
        assertEquals("the ongoing notification was not re-posted", before, after)
    }
}
