package com.focusrefine.app

import android.os.Build
import android.os.Debug
import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import com.google.mediapipe.tasks.components.containers.Embedding
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import org.json.JSONArray
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.max

/**
 * Spike benchmark for MediaPipe TextEmbedder as the notification classifier.
 * Results are written to logcat under the tag EMB_BENCH.
 */
class EmbedderBenchmarkTest {

    private val tag = "EMB_BENCH"
    private lateinit var modelBuffer: java.nio.ByteBuffer

    private data class Item(val title: String, val text: String, val urgentIn: Set<String>) {
        val content get() = "$title $text".lowercase()
    }

    private val examples = mapOf(
        "Commute" to listOf(
            "Turn right in 300 meters", "Heavy traffic on your route, take alternate road",
            "Your bus arrives in 4 minutes at stop 12", "Your ride is arriving now",
            "Boarding has started for your flight", "Urgent call from family, please pick up",
            "Gate has changed for your flight"
        ),
        "Deep Work / Focus" to listOf(
            "Production server is down", "Build failed, deployment blocked",
            "Your meeting starts in 10 minutes", "Deadline is today, need status update",
            "Critical bug reported by customer", "Manager needs you urgently",
            "Calendar reminder for upcoming meeting"
        ),
        "Do Not Disturb (DND)" to listOf(
            "Emergency alert issued for your area", "Fire or smoke alarm triggered",
            "Family member in hospital, come immediately", "Call the emergency services now",
            "Critical alarm: dangerous gas detected", "Urgent: family emergency, please call"
        ),
        "Sleeping / Rest" to listOf(
            "Family emergency please call immediately", "School alert about your child",
            "Doctor: urgent medical notification", "Your relative is in the hospital",
            "Health alert: abnormal heart rate", "Message from your parent, it is important"
        ),
        "Workout / Gym" to listOf(
            "Your workout timer has finished", "Next interval begins now",
            "Heart rate zone reached", "Workout summary: distance and pace",
            "Rest period over, start next set", "Emergency alert nearby"
        )
    )

    private val routine = listOf(
        "Special offer: 30% discount on your next purchase", "Someone liked your photo",
        "Weekly newsletter with tips and stories", "Friend sending a casual message about weekend plans",
        "App update is available to install", "Your food order is being prepared",
        "New episode available to watch", "Rate your recent experience",
        "Daily news digest and trending topics", "Game reminder: come back and play"
    )

    private fun log(s: String) { Log.i(tag, s) }

    private fun loadItems(file: String): List<Item> {
        val json = InstrumentationRegistry.getInstrumentation().context.assets
            .open(file).bufferedReader().readText()
        val arr = JSONArray(json)
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            val modes = o.getJSONArray("urgentIn")
            Item(o.getString("title"), o.getString("text"), (0 until modes.length()).map { i -> modes.getString(i) }.toSet())
        }
    }

    private fun pct(sorted: List<Double>, p: Double): Double = sorted[((sorted.size - 1) * p).toInt()]

    private fun auc(scores: List<Double>, labels: List<Boolean>): Double {
        val pos = scores.filterIndexed { i, _ -> labels[i] }
        val neg = scores.filterIndexed { i, _ -> !labels[i] }
        if (pos.isEmpty() || neg.isEmpty()) return Double.NaN
        var wins = 0.0
        for (p in pos) for (n in neg) wins += if (p > n) 1.0 else if (p == n) 0.5 else 0.0
        return wins / (pos.size * neg.size)
    }

    private data class Cut(val th: Double, val p: Double, val r: Double, val f1: Double, val acc: Double, val fp: Int, val fn: Int)

    private fun cutAt(th: Double, scores: List<Double>, labels: List<Boolean>): Cut {
        var tp = 0; var fp = 0; var fn = 0; var tn = 0
        for (i in scores.indices) {
            val pred = scores[i] >= th
            when {
                pred && labels[i] -> tp++
                pred && !labels[i] -> fp++
                !pred && labels[i] -> fn++
                else -> tn++
            }
        }
        val p = if (tp + fp == 0) 0.0 else tp.toDouble() / (tp + fp)
        val r = if (tp + fn == 0) 0.0 else tp.toDouble() / (tp + fn)
        val f1 = if (p + r == 0.0) 0.0 else 2 * p * r / (p + r)
        return Cut(th, p, r, f1, (tp + tn).toDouble() / scores.size, fp, fn)
    }

    private fun bestF1(scores: List<Double>, labels: List<Boolean>): Cut =
        scores.distinct().map { cutAt(it, scores, labels) }.maxByOrNull { it.f1 }!!

    private fun recall90(scores: List<Double>, labels: List<Boolean>): Cut? =
        scores.distinct().sortedDescending().map { cutAt(it, scores, labels) }.firstOrNull { it.r >= 0.9 }

    private fun fmt(c: Cut) = "th=%.3f P=%.2f R=%.2f F1=%.2f acc=%.2f FP=%d FN=%d".format(c.th, c.p, c.r, c.f1, c.acc, c.fp, c.fn)

    @Test
    fun benchmarkTextEmbedder() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val items = loadItems("golden_notifications.json")
        val holdout = loadItems("holdout_notifications.json")
        val prompts = PromptManager.getAllOotbPrompts(ctx)
        val modes = examples.keys.toList()

        log("=== DEVICE ${Build.MANUFACTURER} ${Build.MODEL} sdk=${Build.VERSION.SDK_INT} abi=${Build.SUPPORTED_ABIS.joinToString()} fp=${Build.FINGERPRINT.contains("generic")} ===")
        log("golden=${items.size} holdout=${holdout.size} modes=${modes.size}")

        // ---- model load ----
        val heapBefore = Debug.getNativeHeapAllocatedSize()
        lateinit var embedder: TextEmbedder
        val loadMs = measureMs {
            modelBuffer = EmbeddingClassifier.loadModelBuffer(ctx) // keep a reference: native code reads the weights in place
            val base = BaseOptions.builder().setModelAssetBuffer(modelBuffer).build()
            val opts = TextEmbedder.TextEmbedderOptions.builder().setBaseOptions(base).build()
            embedder = TextEmbedder.createFromOptions(ctx, opts)
        }
        val heapMb = (Debug.getNativeHeapAllocatedSize() - heapBefore) / 1024.0 / 1024.0
        log("MODEL_LOAD ms=%.1f nativeHeapDeltaMB=%.1f".format(loadMs, heapMb))

        fun embed(s: String): Embedding = embedder.embed(s).embeddingResult().embeddings()[0]
        fun sim(a: Embedding, b: Embedding): Double = TextEmbedder.cosineSimilarity(a, b)

        // ---- warmup + anchor embedding ----
        repeat(5) { embed("warmup text for the model") }
        val promptEmb = modes.associateWith { embed(prompts[it] ?: "") }
        val exampleEmb = modes.associateWith { m -> examples[m]!!.map { embed(it) } }
        val routineEmb = routine.map { embed(it) }
        val anchorTotal = measureMs {
            modes.forEach { embed(prompts[it] ?: "") }
            examples.values.flatten().forEach { embed(it) }
            routine.forEach { embed(it) }
        }
        log("ANCHORS_EMBED_TOTAL ms=%.1f count=%d (done once at startup or mode change)".format(anchorTotal, modes.size + examples.values.sumOf { it.size } + routine.size))

        // ---- latency over all notifications (3 rounds, per-call) ----
        val lat = ArrayList<Double>()
        val itemEmb = ArrayList<Embedding>()
        repeat(3) { round ->
            items.forEach {
                var e: Embedding? = null
                val ms = measureMs { e = embed(it.content) }
                lat += ms
                if (round == 0) itemEmb += e!!
            }
        }
        val holdoutEmb = holdout.map { embed(it.content) }
        val sorted = lat.sorted()
        log("LATENCY_EMBED n=%d mean=%.2f p50=%.2f p95=%.2f p99=%.2f max=%.2f (ms)".format(
            lat.size, lat.average(), pct(sorted, 0.5), pct(sorted, 0.95), pct(sorted, 0.99), sorted.last()))

        // end-to-end decision latency: embed + score every mode variant
        val e2e = ArrayList<Double>()
        items.forEach { itm ->
            e2e += measureMs {
                val e = embed(itm.content)
                modes.forEach { m ->
                    val ex = exampleEmb[m]!!.maxOf { sim(e, it) }
                    val rt = routineEmb.maxOf { sim(e, it) }
                    ex - rt
                }
            }
        }
        val es = e2e.sorted()
        log("LATENCY_DECISION n=%d mean=%.2f p50=%.2f p95=%.2f max=%.2f (ms, embed + all-mode scoring)".format(
            es.size, e2e.average(), pct(es, 0.5), pct(es, 0.95), es.last()))

        // ---- accuracy ----
        data class Variant(val name: String, val score: (Embedding, String) -> Double)
        val variants = listOf(
            Variant("A prompt-only") { e, m -> sim(e, promptEmb[m]!!) },
            Variant("B examples-max") { e, m -> exampleEmb[m]!!.maxOf { sim(e, it) } },
            Variant("C prompt+examples-max") { e, m -> max(sim(e, promptEmb[m]!!), exampleEmb[m]!!.maxOf { sim(e, it) }) },
            Variant("D examples-minus-routine") { e, m -> exampleEmb[m]!!.maxOf { sim(e, it) } - routineEmb.maxOf { sim(e, it) } },
            Variant("E prompt+examples-minus-routine") { e, m ->
                max(sim(e, promptEmb[m]!!), exampleEmb[m]!!.maxOf { sim(e, it) }) - routineEmb.maxOf { sim(e, it) }
            }
        )

        val summary = HashMap<String, MutableList<Double>>()
        for (m in modes) {
            val labels = items.map { m in it.urgentIn }
            log("--- MODE $m positives=${labels.count { it }} negatives=${labels.count { !it }} ---")

            // baseline: current keyword engine (re-implemented exactly as in the service)
            val tokens = (prompts[m] ?: "").lowercase().split(",", " ", ";", "[", "]", ":").map { it.trim() }.filter { it.isNotEmpty() }
            val kwPred = items.map { itm -> tokens.any { itm.content.contains(it) } }
            val kw = cutOf(kwPred, labels)
            log("BASELINE keyword-engine P=%.2f R=%.2f F1=%.2f acc=%.2f FP=%d FN=%d".format(kw[0], kw[1], kw[2], kw[3], kw[4].toInt(), kw[5].toInt()))
            val kwHold = cutOf(holdout.map { h -> tokens.any { h.content.contains(it) } }, holdout.map { m in it.urgentIn })
            log("BASELINE on holdout P=%.2f R=%.2f F1=%.2f acc=%.2f FP=%d FN=%d".format(kwHold[0], kwHold[1], kwHold[2], kwHold[3], kwHold[4].toInt(), kwHold[5].toInt()))
            summary.getOrPut("baseline HOLDOUT F1") { ArrayList() }.add(kwHold[2])
            summary.getOrPut("baseline F1") { ArrayList() }.add(kw[2])
            summary.getOrPut("baseline acc") { ArrayList() }.add(kw[3])

            for (v in variants) {
                val scores = itemEmb.map { v.score(it, m) }
                val a = auc(scores, labels)
                val best = bestF1(scores, labels)
                val r90 = recall90(scores, labels)
                log("${v.name}: AUC=%.3f | bestF1 %s | recall>=0.9 %s".format(a, fmt(best), r90?.let { fmt(it) } ?: "n/a"))
                // held-out check: threshold chosen on the golden set only, applied to unseen notifications
                val hScores = holdoutEmb.map { v.score(it, m) }
                val hLabels = holdout.map { m in it.urgentIn }
                log("   HOLDOUT@golden-th AUC=%.3f %s".format(auc(hScores, hLabels), fmt(cutAt(best.th, hScores, hLabels))))
                summary.getOrPut("${v.name} HOLDOUT F1") { ArrayList() }.add(cutAt(best.th, hScores, hLabels).f1)
                summary.getOrPut("${v.name} HOLDOUT AUC") { ArrayList() }.add(auc(hScores, hLabels))
                summary.getOrPut("${v.name} AUC") { ArrayList() }.add(a)
                summary.getOrPut("${v.name} F1") { ArrayList() }.add(best.f1)
                if (v.name.startsWith("E")) {
                    scores.indices.filter { (scores[it] >= best.th) != labels[it] }.forEach { i ->
                        log("  ${if (labels[i]) "FN" else "FP"} score=%.3f %s | %s".format(scores[i], items[i].title, items[i].text))
                    }
                }
            }
        }
        log("=== MACRO AVERAGE over modes ===")
        summary.toSortedMap().forEach { (k, v) -> log("%s = %.3f".format(k, v.average())) }

        // ---- concurrency: same embedder from 4 threads ----
        val ref = items.take(20).map { embed(it.content).floatEmbedding().sum() }
        val pool = Executors.newFixedThreadPool(4)
        var errors = 0; var mismatches = 0
        val lock = Any()
        val t0 = System.nanoTime()
        val futures = (0 until 4).map {
            pool.submit {
                repeat(5) {
                    items.take(20).forEachIndexed { i, itm ->
                        try {
                            val s = embed(itm.content).floatEmbedding().sum()
                            if (kotlin.math.abs(s - ref[i]) > 1e-3f) synchronized(lock) { mismatches++ }
                        } catch (t: Throwable) { synchronized(lock) { errors++ } }
                    }
                }
            }
        }
        futures.forEach { it.get(120, TimeUnit.SECONDS) }
        pool.shutdown()
        log("CONCURRENCY 4 threads x 100 embeds: errors=$errors mismatches=$mismatches wall=%.0fms".format((System.nanoTime() - t0) / 1e6))

        embedder.close()
        assertTrue("benchmark completed", true)
    }

    private fun cutOf(pred: List<Boolean>, labels: List<Boolean>): DoubleArray {
        var tp = 0; var fp = 0; var fn = 0; var tn = 0
        for (i in pred.indices) when {
            pred[i] && labels[i] -> tp++
            pred[i] && !labels[i] -> fp++
            !pred[i] && labels[i] -> fn++
            else -> tn++
        }
        val p = if (tp + fp == 0) 0.0 else tp.toDouble() / (tp + fp)
        val r = if (tp + fn == 0) 0.0 else tp.toDouble() / (tp + fn)
        val f1 = if (p + r == 0.0) 0.0 else 2 * p * r / (p + r)
        return doubleArrayOf(p, r, f1, (tp + tn).toDouble() / pred.size, fp.toDouble(), fn.toDouble())
    }

    private inline fun measureMs(block: () -> Unit): Double {
        val t = System.nanoTime()
        block()
        return (System.nanoTime() - t) / 1e6
    }
}
