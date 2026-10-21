package com.focusrefine.app

import org.junit.Assert.assertEquals
import org.junit.Test

class ModeSessionTest {
    private val min = 60_000L

    @Test
    fun percentRunsFromZeroToOneHundredAndStaysInRange() {
        assertEquals(0, ModeSession.percent(0, 45 * min, 0))
        assertEquals(50, ModeSession.percent(0, 60 * min, 30 * min))
        assertEquals(100, ModeSession.percent(0, 45 * min, 45 * min))
        assertEquals(100, ModeSession.percent(0, 45 * min, 99 * min))
        assertEquals(0, ModeSession.percent(10 * min, 45 * min, 5 * min))
        assertEquals(100, ModeSession.percent(5, 5, 5))
    }

    @Test
    fun minutesLeftRoundsUpAndNeverGoesNegative() {
        assertEquals(45, ModeSession.minutesLeft(45 * min, 0))
        assertEquals(1, ModeSession.minutesLeft(45 * min, 45 * min - 1))
        assertEquals(0, ModeSession.minutesLeft(45 * min, 45 * min))
        assertEquals(0, ModeSession.minutesLeft(45 * min, 50 * min))
    }

    @Test
    fun phaseIsStartedThenActiveThenComplete() {
        assertEquals(ModeSession.Phase.NONE, ModeSession.phase(0, 0, 10))
        assertEquals(ModeSession.Phase.STARTED, ModeSession.phase(0, 45 * min, 10_000))
        assertEquals(ModeSession.Phase.ACTIVE, ModeSession.phase(0, 45 * min, 5 * min))
        assertEquals(ModeSession.Phase.COMPLETE, ModeSession.phase(0, 45 * min, 45 * min))
    }

    @Test
    fun anOpenEndedSessionNeverCompletesOrShowsProgress() {
        val start = 1_000 * min
        assertEquals(ModeSession.Phase.STARTED, ModeSession.phase(start, ModeSession.OPEN_ENDED, start + 10_000))
        assertEquals(ModeSession.Phase.ACTIVE, ModeSession.phase(start, ModeSession.OPEN_ENDED, start + 600 * min))
        assertEquals(ModeSession.Phase.ACTIVE, ModeSession.phase(start, ModeSession.OPEN_ENDED, start + 365 * 24 * 60 * min))
        assertEquals(0, ModeSession.percent(start, ModeSession.OPEN_ENDED, start + 600 * min))
        assertEquals(true, ModeSession.State("Deep Focus", start, ModeSession.OPEN_ENDED, 0).openEnded)
        assertEquals(false, ModeSession.State("Deep Focus", start, start + 45 * min, 0).openEnded)
    }

    @Test
    fun theOfferedLengthsAreFiveFifteenThirtyAndFortyFiveMinutes() {
        assertEquals(listOf(5, 15, 30, 45), ModeSession.DURATIONS_MIN)
    }

    @Test
    fun countdownShowsMinutesAndSecondsThenHours() {
        assertEquals("4:59", ModeSession.countdown(300_000, 1_000))
        assertEquals("0:01", ModeSession.countdown(1_000, 0))
        assertEquals("0:00", ModeSession.countdown(1_000, 5_000))
        assertEquals("1:05:00", ModeSession.countdown(3_900_000, 0))
    }
}
