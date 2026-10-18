package com.focusrefine.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class PersonalModelTest {

    private val dim = 16

    /** Unit vector pointing mostly along axis [axis], with [mix] of axis [other]. */
    private fun vec(axis: Int, other: Int = (axis + 1) % dim, mix: Double = 0.0): FloatArray {
        val v = FloatArray(dim)
        v[axis] = sqrt(1 - mix * mix).toFloat()
        v[other] += mix.toFloat()
        return PersonalModel.normalize(v)
    }

    private fun ex(key: String, v: FloatArray, urgent: Boolean, mode: String = "Focus") = PersonalModel.Example(key, mode, v, urgent)

    @Test
    fun untrainedModelChangesNothing() {
        val m = PersonalModel(dim)
        assertEquals(0.0, m.adjustment(vec(0), "Focus"), 0.0)
        assertEquals(m.params.baseScale * 0.03, m.decision(vec(0), "Focus", 0.03), 1e-9)
    }

    @Test
    fun aCorrectionFlipsTheBuiltInDecisionForThatNotification() {
        val m = PersonalModel(dim)
        val v = vec(0)
        val margin = -0.02 // the built-in scorer would Mute this
        assertTrue(m.decision(v, "Focus", margin) < 0)
        m.train(listOf(ex("a", v, urgent = true)), doubleArrayOf(margin))
        assertTrue("decision ${m.decision(v, "Focus", margin)}", m.decision(v, "Focus", margin) >= 0)
    }

    @Test
    fun theCorrectionGeneralisesToSimilarNotificationsButNotToUnrelatedOnes() {
        val m = PersonalModel(dim)
        m.train(listOf(ex("a", vec(0), urgent = true)), doubleArrayOf(-0.02))
        val similar = vec(0, other = 1, mix = 0.3)
        val unrelated = vec(5)
        assertTrue(m.decision(similar, "Focus", -0.02) >= 0)
        assertTrue("unrelated moved: ${m.decision(unrelated, "Focus", -0.02)}", m.decision(unrelated, "Focus", -0.02) < 0)
    }

    @Test
    fun anExampleThatAgreesWithTheBuiltInScoreDoesNotMoveClearCases() {
        val m = PersonalModel(dim)
        m.train(listOf(ex("a", vec(0), urgent = true), ex("b", vec(1), urgent = false)), doubleArrayOf(0.06, -0.06))
        assertTrue(m.decision(vec(2), "Focus", 0.08) > 0)
        assertTrue(m.decision(vec(3), "Focus", -0.08) < 0)
        assertEquals(0.0, m.adjustment(vec(9), "Focus"), 0.05)
    }

    @Test
    fun aModeOnlyLearnsFromItsOwnCorrectionsPlusTheSharedPart() {
        val m = PersonalModel(dim)
        m.train(listOf(ex("a", vec(0), urgent = true, mode = "Focus")), doubleArrayOf(-0.02))
        // A different mode only feels the correction through the (weaker) cross-mode kernel.
        val sameMode = m.adjustment(vec(0), "Focus")
        val otherMode = m.adjustment(vec(0), "Sleep")
        assertTrue("same $sameMode other $otherMode", otherMode in 0.0..(sameMode * 0.5))
        assertEquals(0.0, m.adjustment(vec(7), "Sleep"), 0.05)
    }

    @Test
    fun retrainingFromNothingRestoresTheBuiltInBehaviour() {
        val m = PersonalModel(dim)
        m.train(listOf(ex("a", vec(0), urgent = true)), doubleArrayOf(-0.02))
        m.train(emptyList(), DoubleArray(0))
        assertEquals(0.0, m.adjustment(vec(0), "Focus"), 0.0)
    }

    @Test
    fun savedWeightsRestoreToTheSameDecisions() {
        val m = PersonalModel(dim)
        m.train(listOf(ex("a", vec(0), urgent = true), ex("b", vec(4), urgent = false)), doubleArrayOf(-0.02, 0.03))
        val copy = PersonalModel(dim)
        copy.restore(m.savedExamples(), m.savedAlphas())
        listOf(vec(0), vec(4), vec(9)).forEach {
            assertEquals(m.adjustment(it, "Focus"), copy.adjustment(it, "Focus"), 1e-12)
        }
    }
}
