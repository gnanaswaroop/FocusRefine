package com.focusrefine.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/** Confidence is stored with each decision, existing history survives the schema change, and review queries work. */
class ConfidenceStorageTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val dbName = "confidence_storage_test.db"

    @Before
    fun setUp() {
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
        PersonalLearning.clear(context)
    }

    private fun row(title: String, urgent: Boolean, confidence: Float?, learned: Boolean = false, ts: Long = 1) = NotificationEntity(
        title = title, text = "body $title", packageName = "pkg", timestamp = ts, isUrgent = urgent, isLearned = learned,
        confidence = confidence
    )

    @Test
    fun upgradingFromVersion5KeepsHistoryAndLearnedRules() {
        // A database exactly as version 5 of the app created it.
        val file = context.getDatabasePath(dbName).also { it.parentFile?.mkdirs() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL(
                "CREATE TABLE notifications (id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, title TEXT, text TEXT, " +
                    "packageName TEXT NOT NULL, timestamp INTEGER NOT NULL, isUrgent INTEGER NOT NULL, isLearned INTEGER NOT NULL, " +
                    "synthesizedRule TEXT, explanation TEXT)"
            )
            db.execSQL("CREATE TABLE room_master_table (id INTEGER PRIMARY KEY, identity_hash TEXT)")
            db.execSQL("INSERT INTO room_master_table (id, identity_hash) VALUES (42, 'old')")
            val values = ContentValues().apply {
                put("title", "Old"); put("text", "from before the upgrade"); put("packageName", "pkg")
                put("timestamp", 5L); put("isUrgent", 1); put("isLearned", 1); put("synthesizedRule", "[ALLOW] Old"); put("explanation", "why")
            }
            db.insert("notifications", null, values)
            db.version = 5
        }
        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(AppDatabase.MIGRATION_5_6).allowMainThreadQueries().build()
        try {
            val rows = db.notificationDao().getAll()
            assertEquals(1, rows.size)
            assertEquals("Old", rows[0].title)
            assertTrue(rows[0].isLearned)
            assertEquals("[ALLOW] Old", rows[0].synthesizedRule)
            assertNull("no confidence was recorded before the upgrade", rows[0].confidence)
            assertEquals(0, db.notificationDao().countUncertain(Confidence.UNCERTAIN_BELOW))
        } finally {
            db.close()
        }
    }

    @Test
    fun uncertainDecisionsAreListedLeastSureFirstAndLearnedRowsAreLeftOut() {
        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName).allowMainThreadQueries().build()
        try {
            val dao = db.notificationDao()
            dao.insert(row("clear", urgent = false, confidence = 0.97f))
            dao.insert(row("close", urgent = true, confidence = 0.62f, ts = 2))
            dao.insert(row("closest", urgent = false, confidence = 0.51f, ts = 3))
            dao.insert(row("unknown", urgent = false, confidence = null))
            dao.insert(row("decided", urgent = true, confidence = 0.55f, learned = true))

            assertEquals(2, dao.countUncertain(Confidence.UNCERTAIN_BELOW))
            assertEquals(listOf("closest", "close"), dao.getUncertain(Confidence.UNCERTAIN_BELOW).map { it.title })
        } finally {
            db.close()
        }
    }

    @Test
    fun reclassifyingUpdatesTheConfidenceToo() {
        val db = Room.databaseBuilder(context, AppDatabase::class.java, dbName).allowMainThreadQueries().build()
        try {
            val dao = db.notificationDao()
            dao.insert(row("r", urgent = false, confidence = 0.52f))
            val id = dao.getAll().single().id
            dao.updateClassification(id, true, "new explanation", 0.93f)
            val after = dao.getAll().single()
            assertTrue(after.isUrgent)
            assertEquals(0.93f, after.confidence!!, 1e-6f)
            assertFalse(after.isUncertain())
        } finally {
            db.close()
        }
    }

    @Test
    fun theUsersChoiceMakesTheDecisionCertainAndTeachesThePersonalModel() {
        context.getSharedPreferences("FocusRefinePrefs", Context.MODE_PRIVATE).edit().putBoolean("use_ai_model", true).commit()
        PersonalLearning.clear(context)
        val real = AppDatabase.getDatabase(context).notificationDao()
        real.getAll().filter { it.title == "Greenwood School" }.forEach { real.delete(it) } // leftovers from an aborted run
        val original = row("Greenwood School", urgent = false, confidence = 0.55f).copy(text = "Early dismissal today due to a safety incident")
        real.insert(original)
        val stored = real.getAll().first { it.title == "Greenwood School" }
        try {
            val updated = FeedbackRecorder.apply(context, stored, makeUrgent = true, activeMode = "Sleeping / Rest", learn = true)
            assertTrue(updated.isUrgent)
            assertTrue(updated.isLearned)
            assertEquals(Confidence.RULE, updated.confidence)
            assertFalse(updated.isUncertain())
            assertEquals(1, PersonalLearning.correctionCount(context))
            assertEquals(1, real.getAll().count { it.title == "Greenwood School" })
        } finally {
            real.delete(real.getAll().first { it.title == "Greenwood School" })
        }
    }

    @Test
    fun withLearningOffTheChoiceIsStoredButNothingIsTaught() {
        PersonalLearning.clear(context)
        val real = AppDatabase.getDatabase(context).notificationDao()
        real.getAll().filter { it.title == "Spotify" }.forEach { real.delete(it) }
        real.insert(row("Spotify", urgent = true, confidence = 0.6f).copy(text = "Your Daily Mix is ready"))
        val stored = real.getAll().first { it.title == "Spotify" }
        try {
            val updated = FeedbackRecorder.apply(context, stored, makeUrgent = false, activeMode = "Sleeping / Rest", learn = false)
            assertFalse(updated.isUrgent)
            assertFalse(updated.isLearned)
            assertEquals(0, PersonalLearning.correctionCount(context))
        } finally {
            real.delete(real.getAll().first { it.title == "Spotify" })
        }
    }
}
