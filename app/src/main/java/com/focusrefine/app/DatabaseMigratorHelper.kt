package com.focusrefine.app

import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicBoolean

object DatabaseMigratorHelper {

    /**
     * Runs the migration and if it fails, prompts the user to reset the database.
     * Since this is called from getDatabase() (often on a background thread),
     * we must show the dialog on the main thread and block until a decision is made.
     */
    fun migrateWithFallback(context: Context, dbName: String, passphrase: ByteArray) {
        val success = DatabaseMigrator.migrateToEncryptedIfNecessary(context, dbName, passphrase)

        if (!success) {
            val latch = CountDownLatch(1)
            val shouldDelete = AtomicBoolean(false)

            Handler(Looper.getMainLooper()).post {
                try {
                    AlertDialog.Builder(context)
                        .setTitle("Database Migration Failed")
                        .setMessage("We encountered an error upgrading your secure storage. To continue using the app, we need to reset the database (this will clear your notification history).")
                        .setPositiveButton("Reset and Continue") { _, _ ->
                            shouldDelete.set(true)
                            latch.countDown()
                        }
                        .setNegativeButton("Exit") { _, _ ->
                            shouldDelete.set(false)
                            latch.countDown()
                        }
                        .setCancelable(false)
                        .show()
                } catch (e: Exception) {
                    // If we can't show a dialog (e.g. invalid context token for service), fail gracefully by resetting anyway
                    Log.e("DatabaseMigratorHelper", "Could not show dialog, defaulting to delete", e)
                    shouldDelete.set(true)
                    latch.countDown()
                }
            }

            try {
                latch.await()
            } catch (e: InterruptedException) {
                // Ignore
            }

            if (shouldDelete.get()) {
                val dbFile = context.getDatabasePath(dbName)
                if (dbFile.exists()) dbFile.delete()
                File(dbFile.parent, "$dbName-wal").delete()
                File(dbFile.parent, "$dbName-shm").delete()
            } else {
                // If they chose exit, we can just throw an exception to crash or halt
                throw RuntimeException("User declined database reset after migration failure.")
            }
        }
    }
}
