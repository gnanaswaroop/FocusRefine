package com.focusrefine.app

import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sqrt

/**
 * A small kernel-logistic-regression model that learns one user's Shown/Muted preferences from their own
 * corrections, on top of the built-in embedding score. No Android dependencies, so it can be unit tested.
 *
 *     z = baseScale * (score - threshold) + sum_i alpha_i * k(x, x_i)        Shown  <=>  z >= 0
 *     k(x, x_i) = exp((cos(x, x_i) - 1) / tau) * (1 if same mode else crossMode)
 *
 * `x` is the L2-normalised sentence embedding of the notification. The kernel is local: a correction only
 * moves notifications that are close to the corrected one (the same kind of message), and leaves every other
 * notification exactly as the built-in model decided. With no corrections the adjustment is exactly zero, so the
 * decision equals the built-in one. Training fits the alphas by gradient descent on the logistic loss with an L2
 * penalty, starting from that prior. Everything is local: nothing here does I/O.
 */
class PersonalModel(val dim: Int, val params: Params = Params()) {

    data class Params(
        /** Strength of the built-in score as a prior (score margins are about +-0.1). */
        val baseScale: Double = 60.0,
        /** Kernel width on cosine similarity: smaller means a correction affects fewer neighbours. */
        val tau: Double = 0.06,
        /** How much a correction made in another mode counts (0 = modes are independent). */
        val crossMode: Double = 0.0,
        /** L2 penalty on the alphas. */
        val lambda: Double = 0.01,
        val iterations: Int = 300
    )

    class Example(val key: String, val mode: String, val vector: FloatArray, val urgent: Boolean)

    private var examples: List<Example> = emptyList()
    private var alphas = DoubleArray(0)

    /** Added to the scaled built-in margin; exactly 0.0 when nothing has been learned. */
    fun adjustment(vector: FloatArray, mode: String): Double {
        var z = 0.0
        for (i in examples.indices) z += alphas[i] * kernel(vector, mode, examples[i])
        return z
    }

    /** Full decision value; Shown when `>= 0`. [margin] is `score - threshold` from the built-in scorer. */
    fun decision(vector: FloatArray, mode: String, margin: Double): Double =
        params.baseScale * margin + adjustment(vector, mode)

    /** Retrains from scratch on [examples]; [margins] holds each example's built-in `score - threshold`. */
    fun train(examples: List<Example>, margins: DoubleArray) {
        require(examples.size == margins.size)
        this.examples = examples.toList()
        val n = examples.size
        alphas = DoubleArray(n)
        if (n == 0) return

        val k = Array(n) { i -> DoubleArray(n) { j -> kernel(examples[i].vector, examples[i].mode, examples[j]) } }
        val prior = DoubleArray(n) { params.baseScale * margins[it] }
        val target = DoubleArray(n) { if (examples[it].urgent) 1.0 else 0.0 }
        // Step size from a Gershgorin bound on the Hessian, so the descent is stable for any n.
        val rowSum = k.maxOf { row -> row.sum() }
        val step = 1.0 / (0.25 * rowSum * rowSum + params.lambda)

        val err = DoubleArray(n)
        repeat(params.iterations) {
            for (j in 0 until n) {
                var z = prior[j]
                for (i in 0 until n) z += alphas[i] * k[j][i]
                err[j] = sigmoid(z) - target[j]
            }
            for (i in 0 until n) {
                var g = params.lambda * alphas[i]
                for (j in 0 until n) g += err[j] * k[j][i]
                alphas[i] -= step * g
            }
        }
    }

    fun savedExamples(): List<Example> = examples
    fun savedAlphas(): DoubleArray = alphas.copyOf()

    fun restore(examples: List<Example>, alphas: DoubleArray) {
        require(examples.size == alphas.size)
        this.examples = examples.toList()
        this.alphas = alphas.copyOf()
    }

    private fun kernel(x: FloatArray, mode: String, e: Example): Double {
        if (mode != e.mode && params.crossMode == 0.0) return 0.0 // modes are independent: skip the dot product
        var cos = 0.0
        for (d in x.indices) cos += x[d].toDouble() * e.vector[d]
        val sameMode = if (mode == e.mode) 1.0 else params.crossMode
        return exp((max(-1.0, cos) - 1.0) / params.tau) * sameMode
    }

    companion object {
        fun sigmoid(z: Double): Double = 1.0 / (1.0 + exp(-z.coerceIn(-30.0, 30.0)))

        /** Unit-length copy; the zero vector is returned unchanged. */
        fun normalize(v: FloatArray): FloatArray {
            var s = 0.0
            for (x in v) s += x.toDouble() * x
            val norm = sqrt(s)
            if (norm == 0.0) return v.copyOf()
            return FloatArray(v.size) { (v[it] / norm).toFloat() }
        }
    }
}
