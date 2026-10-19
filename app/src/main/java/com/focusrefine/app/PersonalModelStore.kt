package com.focusrefine.app

import android.content.Context
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

/**
 * Where the user's corrections and the model trained from them live: one file in the app's private
 * storage (`personal_model.bin`). It holds notification embeddings (numbers), not message text, is
 * excluded from backup (`allowBackup=false`), and is never read by anything but this app.
 */
class PersonalModelStore(context: Context) {

    private val file = File(context.filesDir, FILE_NAME)

    class Snapshot(val dim: Int, val examples: List<PersonalModel.Example>, val model: PersonalModel)

    @Synchronized
    fun load(): Snapshot? {
        if (!file.exists()) return null
        return try {
            DataInputStream(file.inputStream().buffered()).use { read(it) }
        } catch (e: Exception) {
            null // unreadable or from an incompatible version: behave as if nothing was learned
        }
    }

    /** Adds or replaces the example with the same key, keeps the newest [MAX_EXAMPLES], retrains, saves. */
    @Synchronized
    fun addAndTrain(example: PersonalModel.Example, margin: (FloatArray, String) -> Double): Int {
        val current = load()
        val examples = (current?.examples.orEmpty().filterNot { it.key == example.key && it.mode == example.mode } + example)
            .takeLast(MAX_EXAMPLES)
        return trainAndSave(example.vector.size, examples, margin)
    }

    @Synchronized
    fun remove(key: String, margin: (FloatArray, String) -> Double): Boolean {
        val current = load() ?: return false
        val kept = current.examples.filterNot { it.key == key }
        if (kept.size == current.examples.size) return false
        if (kept.isEmpty()) clear() else trainAndSave(current.dim, kept, margin)
        return true
    }

    @Synchronized
    fun clear() {
        file.delete()
        version.incrementAndGet()
    }

    fun exampleCount(): Int = load()?.examples?.size ?: 0

    private fun trainAndSave(dim: Int, examples: List<PersonalModel.Example>, margin: (FloatArray, String) -> Double): Int {
        val model = PersonalModel(dim)
        model.train(examples, DoubleArray(examples.size) { margin(examples[it].vector, examples[it].mode) })
        val tmp = File(file.parentFile, "$FILE_NAME.tmp")
        DataOutputStream(tmp.outputStream().buffered()).use { write(it, Snapshot(dim, examples, model)) }
        if (!tmp.renameTo(file)) { file.delete(); check(tmp.renameTo(file)) { "could not save the personal model" } }
        version.incrementAndGet()
        return examples.size
    }

    private fun write(out: DataOutputStream, s: Snapshot) {
        out.writeInt(MAGIC); out.writeInt(FORMAT); out.writeInt(s.dim)
        out.writeInt(s.examples.size)
        for (e in s.examples) {
            out.writeUTF(e.key); out.writeUTF(e.mode); out.writeBoolean(e.urgent)
            for (x in e.vector) out.writeFloat(x)
        }
        for (alpha in s.model.savedAlphas()) out.writeFloat(alpha.toFloat())
    }

    private fun read(inp: DataInputStream): Snapshot {
        require(inp.readInt() == MAGIC && inp.readInt() == FORMAT)
        val dim = inp.readInt()
        val examples = List(inp.readInt()) {
            val key = inp.readUTF(); val mode = inp.readUTF(); val urgent = inp.readBoolean()
            PersonalModel.Example(key, mode, FloatArray(dim) { inp.readFloat() }, urgent)
        }
        val alphas = DoubleArray(examples.size) { inp.readFloat().toDouble() }
        val model = PersonalModel(dim)
        model.restore(examples, alphas)
        return Snapshot(dim, examples, model)
    }

    companion object {
        private const val FILE_NAME = "personal_model.bin"
        private const val MAGIC = 0x464F4350 // "FOCP"
        private const val FORMAT = 2
        const val MAX_EXAMPLES = 300

        /** Bumped on every save or clear so running classifiers know to reload (same process). */
        val version = AtomicInteger(0)

        /** Stable key for "the same notification": normalised title + text. */
        fun keyFor(title: String?, text: String?): String =
            ("${title.orEmpty().trim()}|${text.orEmpty().trim()}").lowercase().hashCode().toString(16)
    }
}
