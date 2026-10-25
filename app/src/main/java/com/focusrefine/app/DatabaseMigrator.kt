package com.focusrefine.app

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileInputStream
import net.zetetic.database.sqlcipher.SQLiteDatabase

/**
 * Converts an existing plaintext Room database into a SQLCipher database in place.
 *
 * Uses the SQLCipher SQLiteDatabase (not the framework one): `sqlcipher_export` only exists in SQLCipher.
 * The key text must match what SupportOpenHelperFactory receives, so both derive the same key.
 */
object DatabaseMigrator {
    private const val TAG = "DatabaseMigrator"
    private const val SQLITE_HEADER = "SQLite format 3\u0000"

    fun migrateToEncryptedIfNecessary(context: Context, dbName: String, passphrase: ByteArray) {
        val originalDbFile = context.getDatabasePath(dbName)
        if (!originalDbFile.exists() || !isPlaintextSqlite(originalDbFile)) {
            return
        }
        Log.d(TAG, "Plaintext database detected. Migrating to SQLCipher.")

        val parent = originalDbFile.parentFile ?: return
        val encryptedFile = File(context.cacheDir, "$dbName.encrypting.db")
        val backupFile = File(parent, "$dbName.plaintext-backup")
        encryptedFile.delete()

        val keyText = String(passphrase, Charsets.US_ASCII).replace("'", "''")
        val targetPath = encryptedFile.absolutePath.replace("'", "''")

        try {
            System.loadLibrary("sqlcipher")
            val plain = SQLiteDatabase.openDatabase(
                originalDbFile.absolutePath, ByteArray(0), null,
                SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY, null, null
            )
            try {
                val userVersion = plain.rawQuery("PRAGMA user_version", null).use { c ->
                    if (c.moveToFirst()) c.getInt(0) else 0
                }
                plain.rawExecSQL("ATTACH DATABASE '$targetPath' AS encrypted KEY '$keyText'")
                plain.rawQuery("SELECT sqlcipher_export('encrypted')", null).use { it.moveToFirst() }
                plain.rawExecSQL("PRAGMA encrypted.user_version = $userVersion")
                plain.rawExecSQL("DETACH DATABASE encrypted")
            } finally {
                plain.close()
            }

            // Swap the files; keep the plaintext copy until the encrypted one is in place.
            backupFile.delete()
            if (!originalDbFile.renameTo(backupFile)) throw IllegalStateException("cannot move plaintext db aside")
            if (!encryptedFile.renameTo(originalDbFile)) {
                backupFile.renameTo(originalDbFile)
                throw IllegalStateException("cannot move encrypted db into place")
            }
            backupFile.delete()
            File(parent, "$dbName-wal").delete()
            File(parent, "$dbName-shm").delete()
            File(parent, "$dbName-journal").delete()
            Log.d(TAG, "Migrated plaintext database to SQLCipher.")
        } catch (e: Exception) {
            encryptedFile.delete()
            Log.e(TAG, "Migration to encrypted database failed: ${e.javaClass.simpleName}", e)
        }
    }

    private fun isPlaintextSqlite(file: File): Boolean = try {
        FileInputStream(file).use { input ->
            val header = ByteArray(16)
            input.read(header) == 16 && String(header, Charsets.US_ASCII) == SQLITE_HEADER
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error checking database header", e)
        false
    }
}
