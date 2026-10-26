package com.focusrefine.app

import org.json.JSONObject
import java.text.Normalizer

/**
 * SentencePiece unigram tokenizer for the Track B model: a port of `encode` in ml/export/export_app_assets.py.
 *
 * Text is NFKC-normalised, zero-width non-joiners count as word boundaries, words are joined with the "▁" marker,
 * and the best segmentation (Viterbi, by summed piece scores) is returned. Characters outside the vocabulary become
 * one `<unk>` per run. Positions are code points, not UTF-16 units, so emoji and other supplementary characters
 * segment the same way as in SentencePiece.
 */
class TrackBTokenizer private constructor(
    private val index: Map<String, Int>,
    private val scores: DoubleArray,
    private val maxPieceCodePoints: Int,
    private val unkId: Int,
) {
    /** Token ids for [text], without padding or truncation. */
    fun encode(text: String): IntArray {
        val s = normalize(text)
        val cps = s.codePoints().toArray()
        val n = cps.size
        val best = DoubleArray(n + 1) { Double.NEGATIVE_INFINITY }
        val backStart = IntArray(n + 1)
        val backId = IntArray(n + 1)
        best[0] = 0.0
        val minScore = scores.minOrNull() ?: 0.0
        for (i in 0 until n) {
            if (best[i] == Double.NEGATIVE_INFINITY) continue
            var matched = false
            for (j in i + 1..minOf(n, i + maxPieceCodePoints)) {
                val id = index[String(cps, i, j - i)] ?: continue
                matched = true
                val candidate = best[i] + scores[id]
                if (candidate > best[j]) {
                    best[j] = candidate
                    backStart[j] = i
                    backId[j] = id
                }
            }
            if (!matched) {
                val candidate = best[i] + minScore - UNK_PENALTY
                if (candidate > best[i + 1]) {
                    best[i + 1] = candidate
                    backStart[i + 1] = i
                    backId[i + 1] = unkId
                }
            }
        }
        val reversed = ArrayList<Int>()
        var j = n
        while (j > 0) {
            val id = backId[j]
            // one <unk> for a run of unknown characters, as SentencePiece emits
            if (!(id == unkId && reversed.isNotEmpty() && reversed.last() == unkId)) reversed.add(id)
            j = backStart[j]
        }
        return reversed.asReversed().toIntArray()
    }

    companion object {
        private const val UNK_PENALTY = 10.0

        /** Reads the pieces and scores exported by export_app_assets.py. */
        fun fromJson(json: String): TrackBTokenizer {
            val root = JSONObject(json)
            val pieces = root.getJSONArray("pieces")
            val scoreArray = root.getJSONArray("scores")
            val index = HashMap<String, Int>(pieces.length() * 2)
            var longest = 1
            val scores = DoubleArray(pieces.length()) { scoreArray.getDouble(it) }
            for (i in 0 until pieces.length()) {
                val piece = pieces.getString(i)
                index[piece] = i
                longest = maxOf(longest, piece.codePointCount(0, piece.length))
            }
            return TrackBTokenizer(index, scores, longest, root.getInt("unk_id"))
        }

        /** NFKC, zero-width non-joiner as a space, then words joined by "▁" with a leading "▁". */
        internal fun normalize(text: String): String {
            val normalized = Normalizer.normalize(text, Normalizer.Form.NFKC).replace('‌', ' ')
            val words = ArrayList<String>()
            val current = StringBuilder()
            normalized.codePoints().forEach { cp ->
                if (Character.isWhitespace(cp) || Character.isSpaceChar(cp)) {
                    if (current.isNotEmpty()) {
                        words.add(current.toString())
                        current.setLength(0)
                    }
                } else {
                    current.appendCodePoint(cp)
                }
            }
            if (current.isNotEmpty()) words.add(current.toString())
            return if (words.isEmpty()) "" else "▁" + words.joinToString("▁")
        }
    }
}
