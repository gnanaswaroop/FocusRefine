package com.focusrefine.app

import android.content.Context
import android.util.Log
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.io.File
import net.zetetic.database.sqlcipher.SQLiteDatabase as CipherDatabase

/**
 * A database that this version cannot open (for example, one written by a build whose storage format it does not read).
 * Migration from it is not supported yet, so it is kept on the phone, untouched, under another name, and the app starts
 * with an empty history and tells the user. Nothing is deleted. Tracked in the issue on migrating legacy databases.
 */
object LegacyDatabase {
    private const val TAG = "LegacyDatabase"
    const val KEPT_NAME = "focusrefine_database_legacy"
    const val PREF_KEPT = "legacy_db_kept"
    const val PREF_NOTICE_SEEN = "legacy_db_notice_seen"
    private const val PREFS_NAME = "FocusRefinePrefs"

    /**
     * Moves the database aside when it exists but cannot be opened with [passphrase]. Returns true when it was moved.
     * Only a file the native library rejects as "not a database" counts; other failures leave the file alone.
     */
    fun keepIfUnreadable(context: Context, dbName: String, passphrase: ByteArray): Boolean {
        val db = context.getDatabasePath(dbName)
        if (!db.exists() || canOpen(db, passphrase)) return false
        val kept = context.getDatabasePath(KEPT_NAME)
        for (suffix in listOf("", "-wal", "-shm")) {
            val from = File(db.path + suffix)
            if (from.exists() && !from.renameTo(File(kept.path + suffix))) {
                throw IllegalStateException("Could not keep the unreadable database aside")
            }
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
            .putBoolean(PREF_KEPT, true).putBoolean(PREF_NOTICE_SEEN, false).apply()
        Log.w(TAG, "Existing database could not be opened; kept aside and starting empty")
        return true
    }

    private fun canOpen(file: File, passphrase: ByteArray): Boolean {
        return try {
            val db = CipherDatabase.openDatabase(file.absolutePath, passphrase, null, CipherDatabase.OPEN_READONLY, null, null)
            try {
                db.rawQuery("SELECT count(*) FROM sqlite_master", null).close()
            } finally {
                db.close()
            }
            true
        } catch (e: Exception) {
            val notReadable = e.message?.contains("not a database", ignoreCase = true) == true
            if (!notReadable) Log.w(TAG, "Database check failed (${e.javaClass.simpleName}); leaving it in place")
            !notReadable
        }
    }

    fun noticeNeeded(context: Context): Boolean {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getBoolean(PREF_KEPT, false) && !prefs.getBoolean(PREF_NOTICE_SEEN, false)
    }

    fun markNoticeSeen(context: Context) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().putBoolean(PREF_NOTICE_SEEN, true).apply()
    }
}

/** One-time alert after an unreadable database was kept aside. */
@Composable
fun LegacyDataNotice(context: Context) {
    var showing by remember { mutableStateOf(LegacyDatabase.noticeNeeded(context)) }
    if (!showing) return
    AlertDialog(
        onDismissRequest = {
            LegacyDatabase.markNoticeSeen(context)
            showing = false
        },
        title = { Text("Earlier notification history") },
        text = {
            Text(
                "This phone has notification history from an earlier version of FocusRefine. This version cannot " +
                    "read it yet, and migration is not supported yet, so the history is kept on this phone and is not " +
                    "shown. New notifications start a new history."
            )
        },
        confirmButton = {
            TextButton(onClick = {
                LegacyDatabase.markNoticeSeen(context)
                showing = false
            }) { Text("OK") }
        }
    )
}
