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
}
