package com.focusrefine.app

import android.database.sqlite.SQLiteDatabase as PlainDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import net.zetetic.database.sqlcipher.SQLiteDatabase as CipherDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** Uses its own database name, so the app's real database is never touched. */
@RunWith(AndroidJUnit4::class)
class DatabaseMigratorTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dbName = "migrator_test_db"
    private val key = "c3ludGhldGljLWtleS1mb3ItdGVzdHM=\n".toByteArray(Charsets.US_ASCII)
    private val file: File get() = context.getDatabasePath(dbName)

    @Before
    fun clean() = removeFiles()

    @After
    fun tearDown() = removeFiles()

    private fun removeFiles() {
        listOf("", "-wal", "-shm", "-journal", ".encrypting", ".plaintext-backup").forEach {
            context.getDatabasePath(dbName + it).delete()
        }
    }

    @Test
    fun plaintextDatabaseIsConvertedInPlaceAndKeepsRowsAndVersion() {
        file.parentFile?.mkdirs()
        PlainDatabase.openOrCreateDatabase(file, null).let { db ->
            try {
                db.execSQL("CREATE TABLE notifications (id INTEGER PRIMARY KEY, title TEXT)")
                db.execSQL("INSERT INTO notifications (title) VALUES ('synthetic row')")
                db.version = 6
            } finally {
                db.close()
            }
        }

        DatabaseMigrator.migrateToEncryptedIfNecessary(context, dbName, key)

        val header = file.readBytes().copyOf(16)
        assertFalse("file still has a plaintext SQLite header", String(header, Charsets.US_ASCII) == "SQLite format 3\u0000")

        val db = CipherDatabase.openDatabase(file.absolutePath, key, null, CipherDatabase.OPEN_READONLY, null, null)
        try {
            db.rawQuery("SELECT title FROM notifications", null).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals("synthetic row", c.getString(0))
            }
            db.rawQuery("PRAGMA user_version", null).use { c ->
                assertTrue(c.moveToFirst())
                assertEquals(6, c.getInt(0))
            }
        } finally {
            db.close()
        }
    }

    @Test
    fun runningAgainOnAnEncryptedDatabaseIsANoOp() {
        file.parentFile?.mkdirs()
        PlainDatabase.openOrCreateDatabase(file, null).close()
        DatabaseMigrator.migrateToEncryptedIfNecessary(context, dbName, key)
        val before = file.readBytes()

        DatabaseMigrator.migrateToEncryptedIfNecessary(context, dbName, key)

        assertTrue("second run changed the encrypted file", before.contentEquals(file.readBytes()))
    }
}
