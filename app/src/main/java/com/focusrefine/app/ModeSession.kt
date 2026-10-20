package com.focusrefine.app

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat

/**
 * A timed context-mode session: "Deep Focus for 45 minutes", or open-ended ("until I turn it off"). While it runs, the
 * chosen mode is active and ONE live notification shows it: a countdown with a progress bar (a count-up for an
 * open-ended session), the number of notifications muted so far, and End session / Open FocusRefine actions. That
 * notification is the filtering service's own ongoing notification (see [applyTo]); only when the service is not
 * running is a separate one posted. When the time is up (or the user ends it) the previous mode is restored and a
 * completion notification summarises the session.
 *
 * State lives in the preferences (`session_*` keys), so it survives the process being killed; a once-a-minute alarm
 * ([ModeSessionReceiver]) refreshes the notification and finishes the session on time. Nothing here uses the network.
 */
object ModeSession {
    const val ACTION_TICK = "com.focusrefine.app.ACTION_SESSION_TICK"
    const val ACTION_END = "com.focusrefine.app.ACTION_SESSION_END"
    const val NOTIFICATION_ID = 1002
    private const val DONE_NOTIFICATION_ID = 1003
    private const val CHANNEL_ACTIVE = "focusrefine_session"
    private const val CHANNEL_DONE = "focusrefine_session_done"
    private const val TICK_MS = 60_000L

    private const val K_END = "session_end"
    private const val K_START = "session_start"
    private const val K_MODE = "session_mode"
    private const val K_PREVIOUS = "session_previous_mode"
    private const val K_BASE_COUNT = "session_base_count"

    /** Lengths offered on Home; the picker adds an open-ended "until I turn it off" choice after these. */
    val DURATIONS_MIN = listOf(5, 15, 30, 45)

    /** Stored as the end time of a session that runs until the user ends it. */
    const val OPEN_ENDED = Long.MAX_VALUE

    /** Where a session is: nothing running, just started (first minute), running, or past its end time. */
    enum class Phase { NONE, STARTED, ACTIVE, COMPLETE }

    data class State(val mode: String, val startMs: Long, val endMs: Long, val mutedSoFar: Int) {
        val openEnded: Boolean get() = endMs == OPEN_ENDED
    }

    private fun prefs(context: Context) = context.getSharedPreferences("FocusRefinePrefs", Context.MODE_PRIVATE)

    /** Share of the session that has passed, 0 to 100. Pure, so it is unit tested. */
    fun percent(startMs: Long, endMs: Long, nowMs: Long): Int {
        if (endMs <= startMs) return 100
        return (((nowMs - startMs).coerceIn(0, endMs - startMs)) * 100 / (endMs - startMs)).toInt()
    }

    /** Whole minutes left, rounded up, never negative. Pure. */
    fun minutesLeft(endMs: Long, nowMs: Long): Int = (((endMs - nowMs).coerceAtLeast(0) + 59_999) / 60_000).toInt()

    fun phase(startMs: Long, endMs: Long, nowMs: Long): Phase = when {
        endMs <= 0 -> Phase.NONE
        nowMs >= endMs -> Phase.COMPLETE
        nowMs - startMs < TICK_MS -> Phase.STARTED
        else -> Phase.ACTIVE
    }

    fun current(context: Context): State? {
        val p = prefs(context)
        val end = p.getLong(K_END, 0L)
        if (end <= 0L) return null
        val base = p.getInt(K_BASE_COUNT, 0)
        return State(
            mode = p.getString(K_MODE, "") ?: "",
            startMs = p.getLong(K_START, 0L),
            endMs = end,
            mutedSoFar = (p.getInt("suppressed_count", 0) - base).coerceAtLeast(0)
        )
    }

    fun isActive(context: Context) = current(context) != null

    /** Starts a session in [mode] for [minutes] (null = until the user ends it), remembering the mode to go back to. Replaces a running session. */
    fun start(context: Context, mode: String, minutes: Int?, nowMs: Long = System.currentTimeMillis()) {
        val p = prefs(context)
        val previous = if (isActive(context)) p.getString(K_PREVIOUS, null) else p.getString("current_mode", null)
        p.edit()
            .putString(K_PREVIOUS, previous ?: mode)
            .putString(K_MODE, mode)
            .putLong(K_START, nowMs)
            .putLong(K_END, if (minutes == null) OPEN_ENDED else nowMs + minutes * 60_000L)
            .putInt(K_BASE_COUNT, p.getInt("suppressed_count", 0))
            .putString("current_mode", mode)
            .commit()
        post(context, nowMs)
        scheduleTick(context, nowMs + TICK_MS)
    }

    /** Ends the session now (the user pressed End session) and restores the earlier mode. */
    fun end(context: Context, completed: Boolean = false, nowMs: Long = System.currentTimeMillis()) {
        val state = current(context) ?: return
        val p = prefs(context)
        val previous = p.getString(K_PREVIOUS, null)
        p.edit().remove(K_END).remove(K_START).remove(K_MODE).remove(K_PREVIOUS).remove(K_BASE_COUNT)
            .apply { if (previous != null) putString("current_mode", previous) }
            .commit()
        cancelTick(context)
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.cancel(NOTIFICATION_ID)
        if (completed) postComplete(context, state)
        context.sendBroadcast(Intent("com.focusrefine.app.ACTION_LIVE_REFRESH"))
        refreshServiceNotification(context) // the service's notification goes back to its normal look
    }

    /** Re-posts the live notification with the current numbers; finishes the session if its time is up. */
    fun refresh(context: Context, nowMs: Long = System.currentTimeMillis()) {
        val state = current(context) ?: return
        if (nowMs >= state.endMs) {
            end(context, completed = true, nowMs = nowMs)
        } else {
            post(context, nowMs)
        }
    }

    fun tick(context: Context, nowMs: Long = System.currentTimeMillis()) {
        val state = current(context) ?: return
        refresh(context, nowMs)
        if (nowMs < state.endMs) scheduleTick(context, minOf(nowMs + TICK_MS, state.endMs))
    }

    private fun ensureChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ACTIVE, "Focus sessions", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Live countdown while a timed focus session is running."
                setSound(null, null); enableVibration(false); setShowBadge(false)
            }
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_DONE, "Focus session finished", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "A single note when a timed focus session ends."
            }
        )
    }

    private fun immutable() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) PendingIntent.FLAG_IMMUTABLE else 0

    private fun receiverIntent(context: Context, action: String, request: Int) = PendingIntent.getBroadcast(
        context, request, Intent(context, ModeSessionReceiver::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or immutable()
    )

    private fun openApp(context: Context) = PendingIntent.getActivity(
        context, 0, Intent(context, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or immutable()
    )

    private fun scheduleTick(context: Context, atMs: Long) {
        val alarms = context.getSystemService(AlarmManager::class.java)
        // Inexact on purpose: no exact-alarm permission is needed, and a minute of slack is fine for a countdown.
        alarms.setAndAllowWhileIdle(AlarmManager.RTC, atMs, receiverIntent(context, ACTION_TICK, 11))
    }

    private fun cancelTick(context: Context) {
        context.getSystemService(AlarmManager::class.java).cancel(receiverIntent(context, ACTION_TICK, 11))
    }

    /** Asks the filtering service to rebuild its ongoing notification (it reads the session through [applyTo]). */
    private fun refreshServiceNotification(context: Context) {
        context.sendBroadcast(Intent(NotificationFilterService.ACTION_UPDATE_COUNT).setPackage(context.packageName))
    }

    /**
     * Shows the session. There is only ever one live notification: while the filtering service runs, its own ongoing
     * notification carries the session (so nothing is posted here and any separate one is removed); when it is not
     * running, a standalone notification is posted instead.
     */
    private fun post(context: Context, nowMs: Long) {
        if (current(context) == null) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (NotificationFilterService.isCreated) {
            manager.cancel(NOTIFICATION_ID)
            refreshServiceNotification(context)
            return
        }
        ensureChannels(context)
        val builder = NotificationCompat.Builder(context, CHANNEL_ACTIVE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(openApp(context))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        applyTo(context, builder, nowMs)
        manager.notify(NOTIFICATION_ID, builder.build())
    }

    /**
     * Gives [builder] the session's look (title, text, countdown or count-up, progress, End session and Open
     * buttons, Live Update request) when a session is running, and returns true; returns false and changes nothing
     * when there is none. Used by the filtering service for its own notification, and by [post] for the standalone one.
     */
    fun applyTo(context: Context, builder: NotificationCompat.Builder, nowMs: Long = System.currentTimeMillis()): Boolean {
        val state = current(context) ?: return false
        val open = state.openEnded
        val phase = phase(state.startMs, state.endMs, nowMs)
        val left = minutesLeft(state.endMs, nowMs)
        val title = when {
            phase == Phase.STARTED -> "${state.mode} session started"
            open -> "${state.mode} · until you turn it off"
            else -> "${state.mode} · $left min left"
        }
        val muted = "${state.mutedSoFar} muted so far"
        val review = ReviewQueue.count(context)
        val first = when {
            phase != Phase.STARTED -> muted
            open -> "no end time · $muted"
            else -> "${(state.endMs - state.startMs) / 60_000} min · $muted"
        }
        val text = first + if (review > 0) " · $review close ${if (review == 1) "call" else "calls"} to review" else ""

        builder.setContentTitle(title)
            .setContentText(text)
            .setContentIntent(openApp(context))
            .setShowWhen(true)
            .clearActions()
        if (open) {
            builder.setProgress(0, 0, false).setUsesChronometer(true).setChronometerCountDown(false).setWhen(state.startMs)
        } else {
            builder.setProgress(100, percent(state.startMs, state.endMs, nowMs), false)
                .setUsesChronometer(true).setChronometerCountDown(true).setWhen(state.endMs)
        }
        builder.addAction(0, "End session", receiverIntent(context, ACTION_END, 12))
            .addAction(0, "Open FocusRefine", openApp(context))
        // Android 16 "Live Updates": ask to be promoted to the status bar chip. Older versions ignore these extras.
        builder.addExtras(android.os.Bundle().apply {
            putBoolean("android.requestPromotedOngoing", true)
            putString("android.shortCriticalText", if (open) "On" else "${left}m")
        })
        return true
    }

    private fun postComplete(context: Context, state: State) {
        ensureChannels(context)
        val minutes = (state.endMs - state.startMs) / 60_000
        val n = NotificationCompat.Builder(context, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("${state.mode} session complete")
            .setContentText("${state.mutedSoFar} muted in $minutes min. Open FocusRefine to see what you missed.")
            .setContentIntent(openApp(context))
            .setAutoCancel(true)
            .setProgress(100, 100, false)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(DONE_NOTIFICATION_ID, n)
    }
}

/** Receives the once-a-minute tick and the End session button of [ModeSession]'s notification. */
class ModeSessionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ModeSession.ACTION_TICK -> ModeSession.tick(context)
            ModeSession.ACTION_END -> ModeSession.end(context)
        }
    }
}
