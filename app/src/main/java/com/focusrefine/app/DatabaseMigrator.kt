package com.focusrefine.app

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileInputStream

object DatabaseMigrator {
    private const val TAG = "DatabaseMigrator"

    fun migrateToEncryptedIfNecessary(context: Context, dbName: String, passphrase: ByteArray) {
        val originalDbFile = context.getDatabasePath(dbName)
        if (!originalDbFile.exists()) {
            return
        }

        // Check the SQLite header to see if it's plaintext
        var isPlaintext = false
        try {
            val fileInputStream = FileInputStream(originalDbFile)
            val header = ByteArray(16)
            val bytesRead = fileInputStream.read(header)
            fileInputStream.close()

            // Standard SQLite3 header is "SQLite format 3\0"
            if (bytesRead == 16) {
                val headerStr = String(header, Charsets.US_ASCII)
                if (headerStr == "SQLite format 3\u0000") {
                    isPlaintext = true
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking database header", e)
        }

        if (!isPlaintext) {
            Log.d(TAG, "Database is not plaintext (likely already encrypted).")
            return
        }

        Log.d(TAG, "Plaintext database detected. Attempting migration to encrypted SQLCipher database.")

        try {
            // First, let's open the unencrypted database to ensure it's a valid SQLite DB.
            val unencryptedDb = android.database.sqlite.SQLiteDatabase.openDatabase(
                originalDbFile.absolutePath,
                null,
                android.database.sqlite.SQLiteDatabase.OPEN_READWRITE
            )

            val tempDbFile = File(context.cacheDir, "temp_encrypted.db")
            if (tempDbFile.exists()) tempDbFile.delete()

            // Escape single quotes in the passphrase if necessary (it's base64 decoded bytes though, so we need to be careful with String conversion for ATTACH)
            // SQLCipher's ATTACH syntax can take the key directly.
            // Better to use parameter binding if possible, but ATTACH KEY usually requires a string.
            // SQLCipher 4+ uses raw key pragmas or specific string formats.
            // The safest way for raw bytes is to format it as "x'...'".
            val hexKey = passphrase.joinToString("") { "%02x".format(it) }

            unencryptedDb.rawQuery("ATTACH DATABASE '${tempDbFile.absolutePath}' AS encrypted KEY \"x'$hexKey'\"", null).close()
            unencryptedDb.rawQuery("SELECT sqlcipher_export('encrypted')", null).close()
            unencryptedDb.rawQuery("DETACH DATABASE encrypted", null).close()
            unencryptedDb.close()

            // Swap files
            val success = tempDbFile.renameTo(originalDbFile)
            if (!success) {
                Log.e(TAG, "Failed to rename temp encrypted DB to original DB.")
            } else {
                Log.d(TAG, "Successfully migrated plaintext database to encrypted.")
                // Also clean up WAL and SHM files of the plaintext DB
                File(originalDbFile.parent, "$dbName-wal").delete()
                File(originalDbFile.parent, "$dbName-shm").delete()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Migration to encrypted database failed", e)
        }
    }
}
