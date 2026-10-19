package com.focusrefine.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ConfidenceTest {

    @Test
    fun rightOnTheLineIsAsUnsureAsPossible() {
        assertEquals(0.5f, Confidence.fromDecision(0.0), 1e-6f)
    }

    @Test
    fun theSignOfTheDecisionDoesNotMatter() {
        assertEquals(Confidence.fromDecision(2.0), Confidence.fromDecision(-2.0), 1e-6f)
    }

    @Test
    fun confidenceGrowsWithDistanceFromTheLine() {
        val values = listOf(0.0, 0.5, 1.0, 2.0, 4.0, 8.0).map { Confidence.fromDecision(it) }
        assertEquals(values, values.sorted())
        assertTrue(values.last() > 0.99f)
    }

    @Test
    fun closeCallsAreFlaggedAndClearOnesAreNot() {
        // |decision| = 60 * |score - threshold|, so 0.01 away from the threshold is a close call and 0.05 away is not.
        assertTrue(Confidence.isUncertain(Confidence.fromDecision(60 * 0.01)))
        assertFalse(Confidence.isUncertain(Confidence.fromDecision(60 * 0.05)))
    }

    @Test
    fun theFlagStartsAboutOneStrengthStepFromTheThreshold() {
        // One filtering-strength step is 0.02 of score, i.e. 1.2 of decision value: that is already "fairly sure".
        assertFalse(Confidence.isUncertain(Confidence.fromDecision(60 * 0.02)))
        assertTrue(Confidence.isUncertain(Confidence.fromDecision(60 * 0.015)))
    }

    @Test
    fun noConfidenceMeansNothingToFlag() {
        assertFalse(Confidence.isUncertain(null))
        assertNull(Confidence.label(null))
    }

    @Test
    fun wordsMatchTheLevels() {
        assertEquals("Not sure", Confidence.label(0.6f))
        assertEquals("Fairly sure", Confidence.label(0.8f))
        assertEquals("Very sure", Confidence.label(0.95f))
        assertEquals("Very sure", Confidence.label(Confidence.RULE))
    }

    @Test
    fun onlyTheAppsOwnUndecidedRowsNeedReview() {
        fun row(learned: Boolean, c: Float?) = NotificationEntity(
            title = "t", text = "x", packageName = "p", timestamp = 0, isLearned = learned, confidence = c
        )
        assertTrue(row(learned = false, c = 0.6f).isUncertain())
        assertFalse(row(learned = true, c = 0.6f).isUncertain()) // the user already decided
        assertFalse(row(learned = false, c = 0.95f).isUncertain())
        assertFalse(row(learned = false, c = null).isUncertain())
    }
}
