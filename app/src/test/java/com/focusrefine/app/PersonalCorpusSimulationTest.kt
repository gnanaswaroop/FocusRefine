package com.focusrefine.app

import org.json.JSONArray
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.DataInputStream
import java.io.File
import java.util.Random

/**
 * Large-scale simulation of "does the personal model help?", using the real model's embeddings (dumped from a device
 * by CorpusEmbeddingDumpTest) and the real PersonalModel code. Skipped when the dump is absent.
 *
 * Simulated users correct the built-in model, in random order, on notifications from a *pool*; the model is then scored
 * on *test* notifications it has never seen. Splits of the corpus:
 *   same-kind         half of each template's instances are the pool, the other half the test (a user who keeps getting
 *                     the same kinds of notification, which is the realistic case)
 *   new-kind          whole templates are held out (harder: a kind of notification the user never corrected)
 *   external-holdout  the pool is the corpus, the test is the separate hand-written holdout set (standard user only)
 * and two ways of choosing what the user corrects: a random sample, or only the built-in model's mistakes.
 * Scores are for the embedding model alone (no rules, no exceptions): macro F1 over the five modes.
 */
class PersonalCorpusSimulationTest {

    private val modeNames = listOf("Commute", "Focus", "DND", "Sleep", "Workout")
    private val fullNames = listOf("Commute", "Deep Work / Focus", "Do Not Disturb (DND)", "Sleeping / Rest", "Workout / Gym")
    private val personas = listOf("standard", "chatty", "workaholic", "minimalist", "news_fan", "parent")
    private val kinds = listOf("same-kind", "new-kind", "external-holdout")
    private val variants = listOf("random", "errors")
    private val sizes = listOf(10, 25, 50, 100, 200, 300)

    private class Item(
        val template: String, val labels: Map<String, Set<String>>,
        val vector: FloatArray, val margins: DoubleArray
    )

    private fun load(): Triple<List<Item>, List<Item>, List<Item>>? {
        val dump = listOf(File("build/corpus/corpus_embeddings.bin"), File("app/build/corpus/corpus_embeddings.bin"))
            .firstOrNull { it.exists() } ?: return null
        val root = listOf(File("../testdata"), File("testdata")).first { it.exists() }
        val input = DataInputStream(dump.inputStream().buffered())
        require(input.readInt() == 0x434F5250)
        val dim = input.readInt()
        require(input.readInt() == 3) { "expected golden, holdout and corpus" }
        val files = listOf("golden/golden_notifications.json", "golden/holdout_notifications.json", "corpus/corpus_notifications.json")
        val sets = files.map { f ->
            val arr = JSONArray(File(root, f).readText())
            require(input.readInt() == arr.length()) { "the dump and $f are out of sync: regenerate the dump" }
            List(arr.length()) { i ->
                val o = arr.getJSONObject(i)
                val vector = FloatArray(dim) { input.readFloat() }
                val margins = DoubleArray(5) { input.readDouble() }
                fun modes(a: JSONArray) = (0 until a.length()).map { a.getString(it) }.toSet()
                val labels = if (o.has("labels")) {
                    val l = o.getJSONObject("labels")
                    l.keys().asSequence().associateWith { modes(l.getJSONArray(it)) }
                } else mapOf("standard" to modes(o.getJSONArray("urgentIn")))
                Item(o.optString("template", "-"), labels, vector, margins)
            }
        }
        return Triple(sets[0], sets[1], sets[2])
    }

    private class Score(val f1: Double, val correct: BooleanArray)

    /** Macro F1 over modes; [decide] gives Shown/Muted for (item, mode). */
    private fun score(test: List<Item>, persona: String, decide: (Item, Int) -> Boolean): Score {
        val correct = BooleanArray(test.size * 5)
        var sum = 0.0
        for (m in 0 until 5) {
            var tp = 0; var fp = 0; var fn = 0
            for ((i, item) in test.withIndex()) {
                val actual = fullNames[m] in item.labels.getValue(persona)
                val predicted = decide(item, m)
                correct[i * 5 + m] = predicted == actual
                if (predicted && actual) tp++ else if (predicted && !actual) fp++ else if (!predicted && actual) fn++
            }
            val p = if (tp + fp == 0) 0.0 else tp.toDouble() / (tp + fp)
            val r = if (tp + fn == 0) 0.0 else tp.toDouble() / (tp + fn)
            sum += if (p + r == 0.0) 0.0 else 2 * p * r / (p + r)
        }
        return Score(sum / 5, correct)
    }

    private class Cell {
        val deltas = mutableListOf<Double>()
        val after = mutableListOf<Double>()
        val base = mutableListOf<Double>()
        var fixed = 0
        var broken = 0
        var pairs = 0
    }

    @Test
    fun personalModelHelpsAcrossPersonasSplitsAndSizes() {
        val loaded = load()
        assumeTrue("no corpus_embeddings.bin: run CorpusEmbeddingDumpTest on a device first", loaded != null)
        val (_, holdout, corpus) = loaded!!
        val seeds = 12
        val params = PersonalModel.Params()
        val cells = LinkedHashMap<String, Cell>()
        val dim = corpus.first().vector.size

        fun splitOf(kind: String, seed: Long): Pair<List<Item>, List<Item>> {
            if (kind == "external-holdout") return corpus to holdout
            val rng = Random(seed)
            if (kind == "same-kind") {
                val pool = ArrayList<Item>()
                val test = ArrayList<Item>()
                corpus.groupBy { it.template }.values.forEach { group ->
                    val shuffled = group.shuffled(rng)
                    val half = (shuffled.size + 1) / 2
                    pool += shuffled.take(half)
                    test += shuffled.drop(half)
                }
                return pool to test
            }
            val templates = corpus.map { it.template }.distinct().shuffled(rng)
            val poolTemplates = templates.take(templates.size / 2).toSet()
            return corpus.filter { it.template in poolTemplates } to corpus.filter { it.template !in poolTemplates }
        }

        for (persona in personas) for (kind in kinds) {
            if (kind == "external-holdout" && persona != "standard") continue
            for (variant in variants) for (n in sizes) for (seed in 1..seeds) {
                val (pool, test) = splitOf(kind, seed.toLong() * 7919)
                val rng = Random(seed.toLong() * 104729 + n)
                val candidates = ArrayList<Pair<Item, Int>>()
                for (item in pool) for (m in 0 until 5) {
                    val wanted = fullNames[m] in item.labels.getValue(persona)
                    if (variant == "random" || (item.margins[m] >= 0) != wanted) candidates += item to m
                }
                candidates.shuffle(rng)
                val chosen = candidates.take(n)
                val examples = chosen.mapIndexed { i, (item, m) ->
                    PersonalModel.Example("k$i", modeNames[m], item.vector, fullNames[m] in item.labels.getValue(persona))
                }
                val model = PersonalModel(dim, params)
                model.train(examples, DoubleArray(chosen.size) { chosen[it].first.margins[chosen[it].second] })

                val base = score(test, persona) { item, m -> item.margins[m] >= 0 }
                val after = score(test, persona) { item, m -> model.decision(item.vector, modeNames[m], item.margins[m]) >= 0 }
                val cell = cells.getOrPut("$persona|$kind|$variant|$n") { Cell() }
                cell.deltas += after.f1 - base.f1
                cell.after += after.f1
                cell.base += base.f1
                for (i in base.correct.indices) {
                    cell.pairs++
                    if (!base.correct[i] && after.correct[i]) cell.fixed++
                    if (base.correct[i] && !after.correct[i]) cell.broken++
                }
            }
        }

        val sb = StringBuilder()
        sb.appendLine("# Personal model simulation (embedding model alone, macro F1 over 5 modes)")
        sb.appendLine("corpus=${corpus.size} items in ${corpus.map { it.template }.distinct().size} templates, holdout=${holdout.size}, seeds=$seeds, params=$params")
        fun row(label: String, c: Cell) = "| $label | %.3f | %.3f | %+.3f | %+.3f..%+.3f | %.1f%% | %.1f%% |".format(
            c.base.average(), c.after.average(), c.deltas.average(), c.deltas.min(), c.deltas.max(),
            100.0 * c.fixed / c.pairs, 100.0 * c.broken / c.pairs
        )
        for (kind in kinds) for (variant in variants) {
            sb.appendLine("\n## split=$kind, corrections chosen: $variant\n")
            sb.appendLine("| persona / corrections | built-in F1 | with learning F1 | mean delta | delta min..max | decisions fixed | decisions broken |")
            sb.appendLine("| :--- | :--- | :--- | :--- | :--- | :--- | :--- |")
            for (persona in personas) for (n in sizes) cells["$persona|$kind|$variant|$n"]?.let { sb.appendLine(row("$persona / $n", it)) }
            sb.appendLine("\nAll personas, mean delta by number of corrections: " + sizes.joinToString("  ") { n ->
                val all = personas.mapNotNull { cells["$it|$kind|$variant|$n"] }.flatMap { it.deltas }
                if (all.isEmpty()) "" else "N=$n: %+.3f".format(all.average())
            })
        }
        val report = sb.toString()
        println(report)
        File("build/reports").mkdirs()
        File("build/reports/personal_corpus_simulation.md").writeText(report)

        // Guard rail: on average, learning must not make things worse, for any split, variant or size.
        for (kind in kinds) for (variant in variants) for (n in sizes) {
            val deltas = personas.mapNotNull { cells["$it|$kind|$variant|$n"] }.flatMap { it.deltas }
            if (deltas.isNotEmpty()) assertTrue("mean delta for $kind/$variant/N=$n is %+.3f".format(deltas.average()), deltas.average() > -0.02)
        }
    }
}
