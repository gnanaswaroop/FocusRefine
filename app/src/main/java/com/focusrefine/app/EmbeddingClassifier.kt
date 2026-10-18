package com.focusrefine.app

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.components.containers.Embedding
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import org.json.JSONObject

/**
 * Semantic urgency scoring with an on-device sentence embedder (MediaPipe TextEmbedder).
 *
 * A notification's score for a mode is its best cosine similarity to that mode's example phrases
 * minus its best similarity to "routine" phrases (promos, social, newsletters). Anchors and
 * per-mode thresholds live in `assets/embedding_anchors.json`.
 *
 * Built-in modes only: [classify] returns null for any other mode (e.g. custom modes), so the
 * caller falls back to the keyword engine, which honors the user's own prompt.
 */
class EmbeddingClassifier private constructor(
    private val embedder: TextEmbedder,
    private val config: Config
) {
    data class Verdict(val isUrgent: Boolean, val score: Double, val threshold: Double)

    class ModeAnchors(val match: String, val threshold: Double, val examples: List<String>)
    class Config(val routine: List<String>, val modes: List<ModeAnchors>, val strengthOffsets: DoubleArray)

    private val lock = Any()
    private var closed = false

    private class Embedded(val examples: Map<String, List<Embedding>>, val routine: List<Embedding>)

    /** Embedded once per process (~1.7 s on a Pixel 9 Pro XL); embedding calls are serialized by MediaPipe. */
    private val embedded: Embedded by lazy {
        Embedded(
            examples = config.modes.associate { m -> m.match to m.examples.map(::embed) },
            routine = config.routine.map(::embed)
        )
    }

    /** Pays the one-time anchor cost up front so the first notification is not delayed. */
    fun warmUp() {
        embedded
    }

    /** [filterStrength]: 0 = Gentle, 1 = Balanced, 2 = Strict. Returns null if [mode] has no anchors. */
    fun classify(content: String, mode: String, filterStrength: Int): Verdict? {
        val anchors = config.modes.firstOrNull { mode.contains(it.match, ignoreCase = true) } ?: return null
        val vector = embed(content)
        val best = embedded.examples.getValue(anchors.match).maxOf { similarity(vector, it) }
        val routine = embedded.routine.maxOf { similarity(vector, it) }
        val threshold = anchors.threshold + config.strengthOffsets[filterStrength.coerceIn(0, 2)]
        val score = best - routine
        return Verdict(score >= threshold, score, threshold)
    }

    fun close() = synchronized(lock) {
        closed = true
        embedder.close()
    }

    // Closing while a background warm-up is embedding would free native memory in use.
    private fun embed(text: String): Embedding = synchronized(lock) {
        check(!closed) { "Embedding classifier is closed" }
        embedder.embed(text).embeddingResult().embeddings()[0]
    }

    private fun similarity(a: Embedding, b: Embedding): Double = TextEmbedder.cosineSimilarity(a, b)

    companion object {
        private const val TAG = "EmbeddingClassifier"
        private const val MODEL_ASSET = "text_embedder.tflite"
        private const val ANCHORS_ASSET = "embedding_anchors.json"
        private const val PREFS_NAME = "FocusRefinePrefs"
        private const val PREF_USE_AI = "use_ai_model"

        /** Written on every attempt so the UI can show the engine that is really running. */
        const val PREF_ENGINE_LOADED = "embedding_engine_loaded"

        /** Returns the embedder, or null when disabled by the user or unavailable on this device. */
        fun create(context: Context): EmbeddingClassifier? {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (!prefs.getBoolean(PREF_USE_AI, true)) {
                Log.d(TAG, "Embedding model disabled by user settings")
                prefs.edit().putBoolean(PREF_ENGINE_LOADED, false).apply()
                return null
            }
            val engine = try {
                val base = BaseOptions.builder().setModelAssetPath(MODEL_ASSET).build()
                val options = TextEmbedder.TextEmbedderOptions.builder().setBaseOptions(base).build()
                EmbeddingClassifier(TextEmbedder.createFromOptions(context, options), loadConfig(context))
            } catch (e: Throwable) {
                Log.w(TAG, "Embedding model unavailable; using keyword engine: ${e.message}")
                null
            }
            prefs.edit().putBoolean(PREF_ENGINE_LOADED, engine != null).apply()
            return engine
        }

        private fun loadConfig(context: Context): Config {
            val json = JSONObject(context.assets.open(ANCHORS_ASSET).bufferedReader().use { it.readText() })
            val offsets = json.getJSONObject("strengthOffsets")
            fun strings(array: org.json.JSONArray) = (0 until array.length()).map { array.getString(it) }
            return Config(
                routine = strings(json.getJSONArray("routine")),
                modes = json.getJSONArray("modes").let { modes ->
                    (0 until modes.length()).map { i ->
                        val m = modes.getJSONObject(i)
                        ModeAnchors(m.getString("match"), m.getDouble("threshold"), strings(m.getJSONArray("examples")))
                    }
                },
                strengthOffsets = doubleArrayOf(offsets.getDouble("gentle"), offsets.getDouble("balanced"), offsets.getDouble("strict"))
            )
        }
    }
}
