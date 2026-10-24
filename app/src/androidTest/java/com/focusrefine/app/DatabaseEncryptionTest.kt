package com.focusrefine.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseEncryptionTest {
    private lateinit var db: AppDatabase
    private lateinit var dao: NotificationDao

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = AppDatabase.getDatabase(context)
        dao = db.notificationDao()
        dao.deleteAll()
    }

    @After
    fun closeDb() {
        if (::db.isInitialized) {
            db.close()
        }
    }

    @Test
    fun testInsertAndReadEncryptedDb() {
        val entity = NotificationEntity(
            title = "Test",
            text = "Secret text",
            packageName = "com.test",
            timestamp = 1000L,
            isUrgent = true
        )
        dao.insert(entity)
        val all = dao.getAll()
        assertEquals(1, all.size)
        assertEquals("Secret text", all[0].text)
    }
}
