package com.focusrefine.app

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer

/**
 * Semantic urgency scoring with Track B, the app's own multilingual model (a small transformer trained on synthetic,
 * app-grounded notifications; see ml/export/README.md). It runs on the TFLite interpreter.
 *
 * The model reads "category | app name | title | text", the app context it was trained with ([AppCatalogue] supplies
 * it from the package name), and tokenises it with [TrackBTokenizer]. It outputs one logit per built-in mode (Shown
 * when >= 0) and the pooled features that feed the user's [PersonalModel].
 *
 * On top of the logit sits the user's [PersonalModel], trained only from their own corrections and stored only on this
 * device. With no corrections it changes nothing.
 *
 * Built-in modes only: [classify] returns null for any other mode (e.g. custom modes), so the caller falls back to the
 * keyword engine, which honors the user's own prompt.
 */
class EmbeddingClassifier private constructor(
    private val interpreter: Interpreter,
    private val tokenizer: TrackBTokenizer,
    private val apps: AppCatalogue,
    private val head: Head,
    private val centre: FloatArray,
    private val store: PersonalModelStore,
    @Suppress("unused") private val modelBuffer: ByteBuffer // keeps the model's memory alive while native code reads it
) {
    /** [adjustment] is the personal model's contribution to the decision value (0.0 when untrained). */
    data class Verdict(val isUrgent: Boolean, val score: Double, val threshold: Double, val adjustment: Double = 0.0, val decision: Double = 0.0) {
        /** 0.5 (right on the line) .. 1.0 (far from it). */
        val confidence: Float get() = Confidence.fromDecision(decision)
    }

    /** The linear output layer: one row of weights and one bias per mode, applied to the pooled features. */
    class Head(val weight: Array<FloatArray>, val bias: DoubleArray) {
        fun margin(pooled: FloatArray, mode: Int): Double {
            var s = bias[mode]
            val row = weight[mode]
            for (i in pooled.indices) s += row[i].toDouble() * pooled[i]
            return s
        }
    }

    private class Output(val logits: DoubleArray, val pooled: FloatArray)

    private val lock = Any()
    private var closed = false

    @Volatile private var personal: PersonalModel? = null
    private var personalVersion = -1

    /** Runs one inference so the first notification does not pay the interpreter's start-up cost. */
    fun warmUp() {
        infer(null, null, null)
    }

    /** [filterStrength]: 0 = Gentle, 1 = Balanced, 2 = Strict. Returns null if [mode] is not a built-in mode. */
    fun classify(title: String?, text: String?, packageName: String?, mode: String, filterStrength: Int): Verdict? {
        val canonical = canonicalMode(mode) ?: return null
        val output = infer(title, text, packageName)
        val score = output.logits[MODE_MATCHES.indexOf(canonical)]
        val threshold = STRENGTH_OFFSETS[filterStrength.coerceIn(0, 2)]
        val model = currentPersonalModel()
        val adjustment = model?.adjustment(centred(output.pooled), canonical) ?: 0.0
        val decision = (model?.params?.baseScale ?: DEFAULT_BASE_SCALE) * (score - threshold) + adjustment
        return Verdict(decision >= 0.0, score, threshold, adjustment, decision)
    }

    /** Canonical built-in mode name for [mode] ("Sleeping / Rest" -> "Sleep"), or null for custom modes. */
    fun canonicalMode(mode: String): String? = MODE_MATCHES.firstOrNull { mode.contains(it, ignoreCase = true) }

    /** The built-in logit for [canonicalMode] from a stored, centred feature vector (see [centred]). */
    fun baseMargin(vector: FloatArray, canonicalMode: String): Double {
        val raw = FloatArray(vector.size) { vector[it] + centre[it] }
        return head.margin(raw, MODE_MATCHES.indexOf(canonicalMode))
    }

    /** Records one correction ("Shown"/"Muted") for a built-in mode and retrains. Returns the number of stored corrections. */
    fun learn(title: String?, text: String?, packageName: String?, mode: String, urgent: Boolean): Int? {
        val canonical = canonicalMode(mode) ?: return null
        val vector = centred(infer(title, text, packageName).pooled)
        val example = PersonalModel.Example(PersonalModelStore.keyFor(title, text), canonical, vector, urgent)
        return store.addAndTrain(example) { v, m -> baseMargin(v, m) }
    }

    /** Forgets the correction made for this notification, if any. */
    fun forget(title: String?, text: String?): Boolean =
        store.remove(PersonalModelStore.keyFor(title, text)) { v, m -> baseMargin(v, m) }

    fun close() = synchronized(lock) {
        closed = true
        interpreter.close()
    }

    /** The pooled features minus the training mean: what the personal model compares (see [centre]). */
    private fun centred(pooled: FloatArray): FloatArray = FloatArray(pooled.size) { pooled[it] - centre[it] }

    private fun infer(title: String?, text: String?, packageName: String?): Output = synchronized(lock) {
        check(!closed) { "Embedding classifier is closed" }
        val app = apps.lookup(packageName)
        val ids = tokenizer.encode("${app.category} | ${app.name} | ${title.orEmpty()} | ${text.orEmpty()}")
        val input = IntArray(MAX_LEN) { if (it < ids.size) ids[it] else PAD_ID }

        val outputs = HashMap<Int, Any>()
        var logitsIndex = -1
        var pooledIndex = -1
        for (i in 0 until interpreter.outputTensorCount) {
            val width = interpreter.getOutputTensor(i).shape().last()
            if (width == MODE_MATCHES.size) logitsIndex = i else pooledIndex = i
            outputs[i] = Array(1) { FloatArray(width) }
        }
        interpreter.runForMultipleInputsOutputs(arrayOf<Any>(arrayOf(input)), outputs)
        val logits = (outputs.getValue(logitsIndex) as Array<*>)[0] as FloatArray
        val pooled = (outputs.getValue(pooledIndex) as Array<*>)[0] as FloatArray
        Output(DoubleArray(logits.size) { logits[it].toDouble() }, pooled)
    }

    /** Reloads the personal model when a correction was saved or cleared since the last look. */
    private fun currentPersonalModel(): PersonalModel? {
        val v = PersonalModelStore.version.get()
        if (v != personalVersion) {
            personal = store.load()?.model?.takeIf { it.dim == EMBED_DIM }
            personalVersion = v
        }
        return personal
    }

    companion object {
        private const val TAG = "EmbeddingClassifier"
        private const val MODEL_ASSET = "track_b.tflite"
        private const val VOCAB_ASSET = "track_b_vocab.json"
        private const val HEAD_ASSET = "track_b_head.json"
        private const val CENTRE_ASSET = "track_b_centre.json"
        private const val APPS_ASSET = "track_b_apps.json"
        private const val PAGE = 4096
        private const val PREFS_NAME = "FocusRefinePrefs"
        private const val PREF_USE_AI = "use_ai_model"
        private val DEFAULT_BASE_SCALE = PersonalModel.Params().baseScale

        /** The model's input length (tokens) and the width of its pooled features. */
        private const val MAX_LEN = 96
        private const val EMBED_DIM = 192
        private const val PAD_ID = 0

        /** The model's modes, in output order; each is matched inside the mode name the app uses. */
        private val MODE_MATCHES = listOf("Commute", "Focus", "DND", "Sleep", "Workout")

        /**
         * Threshold offsets for Gentle, Balanced and Strict, in logit units. Provisional: they are not tuned yet, and
         * the held-out set must not be used to tune them.
         */
        private val STRENGTH_OFFSETS = doubleArrayOf(-0.5, 0.0, 0.5)

        /** Written on every attempt so the UI can show the engine that is really running. */
        const val PREF_ENGINE_LOADED = "embedding_engine_loaded"

        @Volatile private var sharedEngine: EmbeddingClassifier? = null
        private val sharedLock = Any()

        /**
         * The process-wide engine, created on first use and then shared by the filter service, the user's corrections
         * and history re-runs. **Nothing ever closes it** (it lives as long as the process): the filter service holds
         * the same instance, and closing it under the service made every later decision fail ("closed").
         * Returns null when disabled by the user or unavailable.
         */
        fun shared(context: Context): EmbeddingClassifier? = synchronized(sharedLock) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (!prefs.getBoolean(PREF_USE_AI, true)) {
                // The user switched the AI engine off. The model is deliberately NOT released (see above).
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
         * The model as a page-aligned direct buffer in ordinary memory, not read straight out of the APK: reading the
         * weights from the APK mapping faulted on a Pixel Pro XL, and the alignment matters for the native code that reads
         * the weights in place.
         */
        fun loadModelBuffer(context: Context): ByteBuffer {
            val bytes = context.assets.open(MODEL_ASSET).use { it.readBytes() }
            val buffer = ByteBuffer.allocateDirect(bytes.size + 2 * PAGE).alignedSlice(PAGE)
            buffer.put(bytes)
            buffer.flip()
            return buffer
        }

        /** Returns a new, separate engine (tests; production code uses [shared]). Null when disabled or unavailable. */
        fun create(context: Context): EmbeddingClassifier? {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            if (!prefs.getBoolean(PREF_USE_AI, true)) {
                Log.d(TAG, "Embedding model disabled by user settings")
                prefs.edit().putBoolean(PREF_ENGINE_LOADED, false).apply()
                return null
            }
            val engine = try {
                val buffer = loadModelBuffer(context)
                EmbeddingClassifier(
                    Interpreter(buffer),
                    TrackBTokenizer.fromJson(readAsset(context, VOCAB_ASSET)),
                    AppCatalogue.fromJson(readAsset(context, APPS_ASSET)),
                    loadHead(context),
                    loadCentre(context),
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

        private fun readAsset(context: Context, name: String): String =
            context.assets.open(name).bufferedReader().use { it.readText() }

        /** The mean pooled feature over the training items (`track_b_centre.json`); see ml/export/export_app_assets.py. */
        private fun loadCentre(context: Context): FloatArray {
            val values = JSONObject(readAsset(context, CENTRE_ASSET)).getJSONArray("centre")
            check(values.length() == EMBED_DIM) { "centre does not match the model width" }
            return FloatArray(EMBED_DIM) { values.getDouble(it).toFloat() }
        }

        private fun loadHead(context: Context): Head {
            val json = JSONObject(readAsset(context, HEAD_ASSET))
            val rows = json.getJSONArray("weight")
            val weight = Array(rows.length()) { r ->
                val row = rows.getJSONArray(r)
                FloatArray(row.length()) { c -> row.getDouble(c).toFloat() }
            }
            val bias = json.getJSONArray("bias").let { b -> DoubleArray(b.length()) { b.getDouble(it) } }
            check(weight.size == MODE_MATCHES.size && bias.size == MODE_MATCHES.size) { "head does not match the modes" }
            return Head(weight, bias)
        }
    }
}
