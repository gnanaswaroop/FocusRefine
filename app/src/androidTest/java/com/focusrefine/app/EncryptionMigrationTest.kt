package com.focusrefine.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.zetetic.database.sqlcipher.SQLiteDatabase as CipherDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** A plaintext database is encrypted in place, the rows and schema version survive, and the plaintext copy is kept. */
@RunWith(AndroidJUnit4::class)
class EncryptionMigrationTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val name = "focusrefine_migration_test"
    private val passphrase = ByteArray(32) { (it * 7 + 3).toByte() }

    @Before
    fun setUp() {
        System.loadLibrary("sqlcipher")
        cleanUp()
        val db = CipherDatabase.openOrCreateDatabase(context.getDatabasePath(name).absolutePath, null as ByteArray?, null, null)
        db.execSQL("CREATE TABLE notifications (id INTEGER PRIMARY KEY, title TEXT, isUrgent INTEGER)")
        for (i in 1..25) db.execSQL("INSERT INTO notifications (title, isUrgent) VALUES ('row $i', ${i % 2})")
        db.execSQL("PRAGMA user_version = 6")
        db.close()
    }

    @After
    fun tearDown() = cleanUp()

    private fun cleanUp() {
        for (file in listOf(name, name + "-wal", name + "-shm", name + ".encrypting",
            EncryptionMigration.PLAINTEXT_BACKUP, EncryptionMigration.PLAINTEXT_BACKUP + "-wal", EncryptionMigration.PLAINTEXT_BACKUP + "-shm")) {
            context.getDatabasePath(file).delete()
        }
    }

    @Test
    fun aPlaintextDatabaseIsEncryptedAndKeepsItsRows() {
        assertEquals(EncryptionMigration.Result.MIGRATED, EncryptionMigration.migrateIfPlaintext(context, name, passphrase))

        val encrypted = CipherDatabase.openDatabase(context.getDatabasePath(name).absolutePath, passphrase, null, CipherDatabase.OPEN_READONLY, null, null)
        try {
            encrypted.rawQuery("SELECT count(*) FROM notifications", null).use { it.moveToFirst(); assertEquals(25L, it.getLong(0)) }
            encrypted.rawQuery("PRAGMA user_version", null).use { it.moveToFirst(); assertEquals(6L, it.getLong(0)) }
        } finally {
            encrypted.close()
        }
        assertTrue("the plaintext copy is kept as a backup", context.getDatabasePath(EncryptionMigration.PLAINTEXT_BACKUP).exists())
    }

    @Test
    fun theEncryptedFileCannotBeOpenedWithoutTheKey() {
        EncryptionMigration.migrateIfPlaintext(context, name, passphrase)
        val opened = try {
            val plain = CipherDatabase.openDatabase(context.getDatabasePath(name).absolutePath, null as ByteArray?, null, CipherDatabase.OPEN_READONLY, null, null)
            try {
                plain.rawQuery("SELECT count(*) FROM sqlite_master", null).use { it.moveToFirst() } // reading is what fails without the key
                true
            } finally {
                plain.close()
            }
        } catch (e: Exception) {
            false
        }
        assertFalse("the database must not open as plaintext", opened)
    }

    @Test
    fun runningItAgainDoesNothing() {
        EncryptionMigration.migrateIfPlaintext(context, name, passphrase)
        assertEquals(EncryptionMigration.Result.NOT_NEEDED, EncryptionMigration.migrateIfPlaintext(context, name, passphrase))
    }
}
