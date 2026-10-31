package com.focusrefine.app

/** Per-mode and per-language confusion counts for the Track B comparison. Macro F1 uses 0 when a mode has no positives. */
class TrackBScore(private val modeCount: Int) {
    private val counts = HashMap<String, Array<IntArray>>()

    fun add(lang: String, mode: Int, predicted: Boolean, actual: Boolean) {
        for (key in listOf(ALL, lang)) {
            val c = counts.getOrPut(key) { Array(3) { IntArray(modeCount) } }
            when {
                predicted && actual -> c[0][mode]++
                predicted && !actual -> c[1][mode]++
                !predicted && actual -> c[2][mode]++
            }
        }
    }

    fun perMode(key: String): DoubleArray {
        val c = counts[key] ?: return DoubleArray(modeCount)
        return DoubleArray(modeCount) { k ->
            val denominator = 2 * c[0][k] + c[1][k] + c[2][k]
            if (denominator == 0) 0.0 else 2.0 * c[0][k] / denominator
        }
    }

    fun macro(key: String): Double = perMode(key).average()

    fun falsePositives(key: String): Int = counts[key]?.get(1)?.sum() ?: 0

    fun falseNegatives(key: String): Int = counts[key]?.get(2)?.sum() ?: 0

    fun languages(): List<String> = counts.keys.filter { it != ALL }.sorted()

    companion object {
        const val ALL = "ALL"
    }
}
