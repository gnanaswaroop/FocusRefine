package com.focusrefine.app

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer

/**
 * Semantic urgency scoring with an on-device sentence embedder (MediaPipe TextEmbedder).
 *
 * A notification's score for a mode is its best cosine similarity to that mode's example phrases
 * minus its best similarity to "routine" phrases (promos, social, newsletters). Anchors and
 * per-mode thresholds live in `assets/embedding_anchors.json`.
 *
 * On top of that sits the user's [PersonalModel], trained only from their own corrections and stored
 * only on this device. With no corrections it changes nothing.
 *
 * Built-in modes only: [classify] returns null for any other mode (e.g. custom modes), so the
 * caller falls back to the keyword engine, which honors the user's own prompt.
 */
class EmbeddingClassifier private constructor(
    private val embedder: TextEmbedder,
    private val config: Config,
    private val store: PersonalModelStore,
    @Suppress("unused") private val modelBuffer: ByteBuffer // keeps the model's memory alive while native code reads it
) {
    /** [adjustment] is the personal model's contribution to the decision value (0.0 when untrained). */
    data class Verdict(val isUrgent: Boolean, val score: Double, val threshold: Double, val adjustment: Double = 0.0, val decision: Double = 0.0) {
        /** 0.5 (right on the line) .. 1.0 (far from it). */
        val confidence: Float get() = Confidence.fromDecision(decision)
    }

    class ModeAnchors(val match: String, val threshold: Double, val examples: List<String>)
    class Config(val routine: List<String>, val modes: List<ModeAnchors>, val strengthOffsets: DoubleArray)

    private class Embedded(val examples: Map<String, List<FloatArray>>, val routine: List<FloatArray>)

    private val lock = Any()
    private var closed = false

    /** Embedded once per process (~1.7 s on a Pixel Pro XL); embedding calls are serialized by MediaPipe. */
    private val embedded: Embedded by lazy {
        Embedded(
            examples = config.modes.associate { m -> m.match to m.examples.map(::embed) },
            routine = config.routine.map(::embed)
        )
    }

    @Volatile private var personal: PersonalModel? = null
    private var personalVersion = -1

    /** Pays the one-time anchor cost up front so the first notification is not delayed. */
    fun warmUp() {
        embedded
    }

    /** [filterStrength]: 0 = Gentle, 1 = Balanced, 2 = Strict. Returns null if [mode] has no anchors. */
    fun classify(content: String, mode: String, filterStrength: Int): Verdict? {
        val anchors = anchorsFor(mode) ?: return null
        val vector = embed(content)
        val score = score(vector, anchors)
        val threshold = anchors.threshold + config.strengthOffsets[filterStrength.coerceIn(0, 2)]
        val model = currentPersonalModel()
        val adjustment = model?.adjustment(vector, anchors.match) ?: 0.0
        val decision = (model?.params?.baseScale ?: DEFAULT_BASE_SCALE) * (score - threshold) + adjustment
        return Verdict(decision >= 0.0, score, threshold, adjustment, decision)
    }

    /** Canonical built-in mode name for [mode] ("Sleeping / Rest" -> "Sleep"), or null for custom modes. */
    fun canonicalMode(mode: String): String? = anchorsFor(mode)?.match

    /** L2-normalised embedding of [text]. */
    fun embed(text: String): FloatArray = synchronized(lock) {
        check(!closed) { "Embedding classifier is closed" }
        PersonalModel.normalize(embedder.embed(text).embeddingResult().embeddings()[0].floatEmbedding())
    }

    /** Built-in `score - threshold` (Balanced strength) for an already-embedded notification. */
    fun baseMargin(vector: FloatArray, canonicalMode: String): Double {
        val anchors = config.modes.first { it.match == canonicalMode }
        return score(vector, anchors) - anchors.threshold
    }

    /** Records one correction ("Shown"/"Muted") for a built-in mode and retrains. Returns the number of stored corrections. */
    fun learn(title: String?, text: String?, mode: String, urgent: Boolean): Int? {
        val canonical = canonicalMode(mode) ?: return null
        val vector = embed("${title.orEmpty()} ${text.orEmpty()}".lowercase())
        val example = PersonalModel.Example(PersonalModelStore.keyFor(title, text), canonical, vector, urgent)
        return store.addAndTrain(example) { v, m -> baseMargin(v, m) }
    }

    /** Forgets the correction made for this notification, if any. */
    fun forget(title: String?, text: String?): Boolean =
        store.remove(PersonalModelStore.keyFor(title, text)) { v, m -> baseMargin(v, m) }

    fun close() = synchronized(lock) {
        closed = true
        embedder.close()
    }

    private fun anchorsFor(mode: String): ModeAnchors? =
        config.modes.firstOrNull { mode.contains(it.match, ignoreCase = true) }

    private fun score(vector: FloatArray, anchors: ModeAnchors): Double {
        val best = embedded.examples.getValue(anchors.match).maxOf { dot(vector, it) }
        val routine = embedded.routine.maxOf { dot(vector, it) }
        return best - routine
    }

    private fun dot(a: FloatArray, b: FloatArray): Double {
        var s = 0.0
        for (i in a.indices) s += a[i].toDouble() * b[i]
        return s
    }

    /** Reloads the personal model when a correction was saved or cleared since the last look. */
    private fun currentPersonalModel(): PersonalModel? {
        val v = PersonalModelStore.version.get()
        if (v != personalVersion) {
            personal = store.load()?.model
            personalVersion = v
        }
        return personal
    }

    companion object {
        private const val TAG = "EmbeddingClassifier"
        private const val MODEL_ASSET = "text_embedder.tflite"
        private const val PAGE = 4096
        private const val ANCHORS_ASSET = "embedding_anchors.json"
        private const val PREFS_NAME = "FocusRefinePrefs"
        private const val PREF_USE_AI = "use_ai_model"
        private val DEFAULT_BASE_SCALE = PersonalModel.Params().baseScale

        /** Written on every attempt so the UI can show the engine that is really running. */
        const val PREF_ENGINE_LOADED = "embedding_engine_loaded"

        @Volatile private var sharedEngine: EmbeddingClassifier? = null
        private val sharedLock = Any()

        /**
         * The process-wide engine, created on first use and then shared by the filter service, the user's corrections
         * and history re-runs. **Nothing ever closes it** (it lives as long as the process). Creating and closing a MediaPipe model for every
         * correction was fragile: on a Pixel Pro XL the model's worker threads sometimes faulted (SIGBUS) while a
         * model was being closed right after an install. A single long-lived model also saves loading it and embedding
         * its anchors (about 2 s) on each correction. Returns null when disabled by the user or unavailable.
         */
        fun shared(context: Context): EmbeddingClassifier? = synchronized(sharedLock) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (!prefs.getBoolean(PREF_USE_AI, true)) {
                // The user switched the AI engine off. The model is deliberately NOT released: the filter service holds
                // the same instance, and closing it under the service made every later decision fail ("closed").
                prefs.edit().putBoolean(PREF_ENGINE_LOADED, false).apply()
                return null
            }
            sharedEngine?.let { existing ->
                // Re-enabled after being off: the status the Settings screen reads must say the model is loaded again.
                if (!prefs.getBoolean(PREF_ENGINE_LOADED, false)) prefs.edit().putBoolean(PREF_ENGINE_LOADED, true).apply()
                return existing
            }
            create(context.applicationContext).also { sharedEngine = it }
        }

        /**
         * The model as a page-aligned direct buffer in ordinary memory. It is not read straight out of the APK
         * (setModelAssetPath): on a Pixel Pro XL MediaPipe's worker threads faulted (SIGBUS) reading the weights from
         * the APK mapping in 6 of 6 runs, and from memory they did not in 6 of 6. The alignment matters too: TFLite and
         * XNNPACK read the weights in place, and an ordinary allocation made inference fail on x86_64.
         */
        fun loadModelBuffer(context: Context): ByteBuffer {
            val bytes = context.assets.open(MODEL_ASSET).use { it.readBytes() }
            val buffer = ByteBuffer.allocateDirect(bytes.size + 2 * PAGE).alignedSlice(PAGE)
            buffer.put(bytes)
            buffer.flip()
            return buffer
        }

        /** Returns a new, separate embedder (tests; production code uses [shared]). Null when disabled or unavailable. */
        fun create(context: Context): EmbeddingClassifier? {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (!prefs.getBoolean(PREF_USE_AI, true)) {
                Log.d(TAG, "Embedding model disabled by user settings")
                prefs.edit().putBoolean(PREF_ENGINE_LOADED, false).apply()
                return null
            }
            val engine = try {
                val buffer = loadModelBuffer(context)
                val base = BaseOptions.builder().setModelAssetBuffer(buffer).build()
                val options = TextEmbedder.TextEmbedderOptions.builder().setBaseOptions(base).build()
                EmbeddingClassifier(
                    TextEmbedder.createFromOptions(context, options),
                    loadConfig(context),
                    PersonalModelStore(context.applicationContext),
                    buffer
                )
            } catch (e: Throwable) {
                Log.w(TAG, "Embedding model unavailable; using keyword engine: ${e.javaClass.name}: ${e.message}", e)
                null
            }
            prefs.edit().putBoolean(PREF_ENGINE_LOADED, engine != null).apply()
            return engine
        }

        private fun loadConfig(context: Context): Config {
            val json = JSONObject(context.assets.open(ANCHORS_ASSET).bufferedReader().use { it.readText() })
            val offsets = json.getJSONObject("strengthOffsets")
            fun strings(array: JSONArray) = (0 until array.length()).map { array.getString(it) }
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
