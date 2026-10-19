package com.focusrefine.app

import android.util.Log
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONArray
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.util.Random

/**
 * Simulates a user who corrects the built-in model's mistakes and measures how much the personal
 * model helps on notifications it has not seen. Logs to tag PERS_BENCH. Hyperparameters are chosen by
 * cross-validation inside the golden set; the holdout set is only scored with the chosen ones.
 *
 * Two simulated users:
 *  - "standard": agrees with the dataset labels, so corrections fix the built-in model's errors.
 *  - "chatty": additionally wants messages from chat apps Shown in Commute, Focus and Workout, which
 *    the built-in model can never know, so this shows real personalisation.
 * Scores are for the embedding model alone (no rules or exceptions).
 */
class PersonalModelBenchmarkTest {

    private val tag = "PERS_BENCH"
    private val modes = listOf("Commute", "Deep Work / Focus", "Do Not Disturb (DND)", "Sleeping / Rest", "Workout / Gym")
    private val chatApps = setOf("whatsapp", "messenger", "slack", "snapchat")
    private val chatModes = setOf("Commute", "Focus", "Workout")

    private class Item(val title: String, val text: String, val urgentIn: Set<String>) {
        val content get() = "$title $text".lowercase()
    }

    private class Row(val item: Item, val vector: FloatArray, val margins: Map<String, Double>)

    private fun load(file: String): List<Item> {
        val json = InstrumentationRegistry.getInstrumentation().context.assets.open(file).bufferedReader().readText()
        val arr = JSONArray(json)
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            val m = o.getJSONArray("urgentIn")
            Item(o.getString("title"), o.getString("text"), (0 until m.length()).map { i -> m.getString(i) }.toSet())
        }
    }

    private fun label(persona: String, item: Item, mode: String): Boolean {
        val base = mode in item.urgentIn
        if (persona == "chatty" && item.title.lowercase() in chatApps && chatModes.any { mode.contains(it) }) return true
        return base
    }

    private fun f1(tp: Int, fp: Int, fn: Int): Double {
        val p = if (tp + fp == 0) 0.0 else tp.toDouble() / (tp + fp)
        val r = if (tp + fn == 0) 0.0 else tp.toDouble() / (tp + fn)
        return if (p + r == 0.0) 0.0 else 2 * p * r / (p + r)
    }

    /** Macro F1 over modes. [model] null = the built-in decision. */
    private fun evaluate(rows: List<Row>, persona: String, model: PersonalModel?): Double {
        val perMode = modes.map { mode ->
            var tp = 0; var fp = 0; var fn = 0
            for (r in rows) {
                val key = canon(mode)
                val margin = r.margins.getValue(key)
                val z = model?.decision(r.vector, key, margin) ?: (PersonalModel.Params().baseScale * margin)
                val predicted = z >= 0
                val actual = label(persona, r.item, mode)
                if (predicted && actual) tp++ else if (predicted && !actual) fp++ else if (!predicted && actual) fn++
            }
            f1(tp, fp, fn)
        }
        return perMode.average()
    }

    private fun canon(mode: String) = when {
        mode.contains("Commute") -> "Commute"
        mode.contains("Focus") -> "Focus"
        mode.contains("DND") -> "DND"
        mode.contains("Sleep") -> "Sleep"
        else -> "Workout"
    }

    /** The user corrects built-in mistakes, in random order, until [k] corrections exist. */
    private fun corrections(rows: List<Row>, persona: String, k: Int, seed: Long): Pair<List<PersonalModel.Example>, DoubleArray> {
        val candidates = ArrayList<Triple<Row, String, Boolean>>()
        for (r in rows) for (mode in modes) {
            val actual = label(persona, r.item, mode)
            val predicted = r.margins.getValue(canon(mode)) >= 0
            if (predicted != actual) candidates += Triple(r, mode, actual)
        }
        candidates.shuffle(Random(seed))
        val chosen = candidates.take(k)
        val examples = chosen.mapIndexed { i, (r, mode, actual) -> PersonalModel.Example("k$i", canon(mode), r.vector, actual) }
        return examples to DoubleArray(chosen.size) { chosen[it].first.margins.getValue(canon(chosen[it].second)) }
    }

    private fun fit(rows: List<Row>, persona: String, k: Int, params: PersonalModel.Params, seed: Long): PersonalModel {
        val (examples, margins) = corrections(rows, persona, k, seed)
        return PersonalModel(rows.first().vector.size, params).also { it.train(examples, margins) }
    }

    @Test
    fun personalisationLearningCurve() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        ctx.getSharedPreferences("FocusRefinePrefs", android.content.Context.MODE_PRIVATE).edit().putBoolean("use_ai_model", true).commit()
        val engine = EmbeddingClassifier.shared(ctx)
        assertNotNull("embedding model must load", engine)
        engine!!
        engine.warmUp()

        fun rowsOf(items: List<Item>) = items.map { item ->
            val v = engine.embed(item.content)
            Row(item, v, listOf("Commute", "Focus", "DND", "Sleep", "Workout").associateWith { engine.baseMargin(v, it) })
        }
        val golden = rowsOf(load("golden_notifications.json"))
        val holdout = rowsOf(load("holdout_notifications.json"))
        val goldenTrain = golden.filterIndexed { i, _ -> i % 2 == 0 }
        val goldenTest = golden.filterIndexed { i, _ -> i % 2 == 1 }
        val personas = listOf("standard", "chatty")
        val seeds = listOf(1L, 2L, 3L)

        // ---- choose hyperparameters on golden only (train on even rows, test on odd rows, 20 corrections) ----
        data class Candidate(val params: PersonalModel.Params, val score: Double)
        val grid = ArrayList<Candidate>()
        for (tau in listOf(0.03, 0.06, 0.1, 0.2)) for (cross in listOf(0.0, 0.3, 0.6)) for (lambda in listOf(0.01, 0.1)) {
            val p = PersonalModel.Params(tau = tau, crossMode = cross, lambda = lambda)
            val score = personas.flatMap { persona -> seeds.map { s -> evaluate(goldenTest, persona, fit(goldenTrain, persona, 20, p, s)) } }.average()
            grid += Candidate(p, score)
            Log.i(tag, "GRID tau=$tau crossMode=$cross lambda=$lambda goldenCV_F1=%.3f".format(score))
        }
        val best = grid.maxByOrNull { it.score }!!
        Log.i(tag, "CHOSEN ${best.params} goldenCV_F1=%.3f".format(best.score))

        // ---- built-in baseline ----
        for (persona in personas) {
            Log.i(tag, "BASELINE persona=$persona golden=%.3f holdout=%.3f".format(
                evaluate(golden, persona, null), evaluate(holdout, persona, null)))
        }

        // ---- learning curve on the holdout set, with the chosen hyperparameters ----
        for (persona in personas) {
            for (k in listOf(0, 5, 10, 20, 40, 80)) {
                val scores = seeds.map { s -> evaluate(holdout, persona, if (k == 0) null else fit(golden, persona, k, best.params, s)) }
                val trainScores = seeds.map { s -> evaluate(golden, persona, if (k == 0) null else fit(golden, persona, k, best.params, s)) }
                Log.i(tag, "CURVE persona=$persona corrections=$k holdoutF1=%.3f (min %.3f max %.3f) goldenF1(train)=%.3f".format(
                    scores.average(), scores.min(), scores.max(), trainScores.average()))
            }
        }

        // ---- cost ----
        val (examples, margins) = corrections(golden, "chatty", 80, 1)
        val t0 = System.nanoTime()
        PersonalModel(examples.first().vector.size, best.params).train(examples, margins)
        Log.i(tag, "TRAIN_TIME 80 examples ms=%.1f".format((System.nanoTime() - t0) / 1e6))
    }
}
