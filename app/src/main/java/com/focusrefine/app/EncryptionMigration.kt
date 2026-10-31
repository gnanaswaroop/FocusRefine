package com.focusrefine.app

import android.content.Context
import android.util.Log
import java.io.File
import net.zetetic.database.sqlcipher.SQLiteDatabase as CipherDatabase

/**
 * Encrypts an existing plaintext database in place, without deleting anything.
 *
 * The encrypted copy is written beside the original and checked (same tables, same row counts, same schema version)
 * before anything is renamed. The plaintext original is kept as a backup file, so the user's history is never lost by
 * this step. If any check fails, the original stays where it is and the copy is removed; [LegacyDatabase] then keeps it
 * aside as before. Uses the SQLCipher library's own API, not the framework's SQLite (which cannot attach encrypted
 * databases).
 */
object EncryptionMigration {
    private const val TAG = "EncryptionMigration"
    const val PLAINTEXT_BACKUP = "focusrefine_database_plaintext_backup"

    enum class Result { NOT_NEEDED, MIGRATED, FAILED }

    /** [passphrase] is the database key. The SQLCipher native library must already be loaded. */
    fun migrateIfPlaintext(context: Context, dbName: String, passphrase: ByteArray): Result {
        val db = context.getDatabasePath(dbName)
        if (!db.exists() || !isPlaintext(db)) return Result.NOT_NEEDED
        val encrypted = File(db.parentFile, "$dbName.encrypting")
        encrypted.delete()
        return try {
            export(db, encrypted, passphrase)
            if (!sameContents(db, encrypted, passphrase)) {
                encrypted.delete()
                Log.w(TAG, "Encrypted copy did not match the original; leaving the original in place")
                return Result.FAILED
            }
            val backup = context.getDatabasePath(PLAINTEXT_BACKUP)
            moveWithSidecars(db, backup)
            if (!encrypted.renameTo(db)) throw IllegalStateException("Could not put the encrypted database in place")
            Log.i(TAG, "Database encrypted in place; the plaintext copy is kept as a backup")
            Result.MIGRATED
        } catch (e: Exception) {
            Log.w(TAG, "Encryption migration failed (${e.javaClass.simpleName}: ${e.message}); leaving the original in place", e)
            encrypted.delete()
            Result.FAILED
        }
    }

    private fun isPlaintext(file: File): Boolean {
        val db = try {
            CipherDatabase.openDatabase(file.absolutePath, null as ByteArray?, null, CipherDatabase.OPEN_READONLY, null, null)
        } catch (e: Exception) {
            return false
        }
        return try {
            db.rawQuery("SELECT count(*) FROM sqlite_master", null).use { it.moveToFirst() }
            true
        } catch (e: Exception) {
            false
        } finally {
            db.close()
        }
    }

    /**
     * Creates the encrypted copy with the key, then copies the plaintext tables into it (SQLCipher's documented way to
     * encrypt an existing database), and carries the schema version over.
     */
    private fun export(plain: File, encrypted: File, passphrase: ByteArray) {
        val version = CipherDatabase.openDatabase(plain.absolutePath, null as ByteArray?, null, CipherDatabase.OPEN_READONLY, null, null).let { db ->
            try {
                db.rawQuery("PRAGMA user_version", null).use { it.moveToFirst(); it.getLong(0) }
            } finally {
                db.close()
            }
        }
        val copy = CipherDatabase.openOrCreateDatabase(encrypted.absolutePath, passphrase, null, null)
        try {
            copy.execSQL("ATTACH DATABASE '${plain.absolutePath}' AS plaintext KEY ''")
            copy.rawQuery("SELECT sqlcipher_export('main', 'plaintext')", null).use { it.moveToFirst() }
            copy.execSQL("DETACH DATABASE plaintext")
            copy.execSQL("PRAGMA user_version = $version")
        } finally {
            copy.close()
        }
    }

    /** True when both files hold the same user tables, with the same row counts and schema version. */
    private fun sameContents(plain: File, encrypted: File, passphrase: ByteArray): Boolean {
        val before = countsAndVersion(CipherDatabase.openDatabase(plain.absolutePath, null as ByteArray?, null, CipherDatabase.OPEN_READONLY, null, null))
        val after = countsAndVersion(CipherDatabase.openDatabase(encrypted.absolutePath, passphrase, null, CipherDatabase.OPEN_READONLY, null, null))
        return before.isNotEmpty() && before == after
    }

    /** Closes [db] when done. */
    private fun countsAndVersion(db: CipherDatabase): Map<String, Long> {
        try {
            val tables = db.rawQuery(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name NOT LIKE 'sqlite_%' ORDER BY name", null
            ).use { c -> generateSequence { if (c.moveToNext()) c.getString(0) else null }.toList() }
            val out = LinkedHashMap<String, Long>()
            for (table in tables) {
                out[table] = db.rawQuery("SELECT count(*) FROM \"$table\"", null).use { c -> c.moveToFirst(); c.getLong(0) }
            }
            out["user_version"] = db.rawQuery("PRAGMA user_version", null).use { c -> c.moveToFirst(); c.getLong(0) }
            return out
        } finally {
            db.close()
        }
    }

    private fun moveWithSidecars(from: File, to: File) {
        for (suffix in listOf("", "-wal", "-shm")) {
            val source = File(from.path + suffix)
            if (source.exists() && !source.renameTo(File(to.path + suffix))) {
                throw IllegalStateException("Could not move ${source.name} aside")
            }
        }
    }
}
