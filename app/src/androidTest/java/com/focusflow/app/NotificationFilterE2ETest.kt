package com.focusflow.app

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class NotificationFilterE2ETest {

    private lateinit var db: AppDatabase
    private lateinit var dao: NotificationDao
    private lateinit var context: Context

    @Before
    fun createDb() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.notificationDao()
    }

    @After
    @Throws(IOException::class)
    fun closeDb() {
        db.close()
    }

    @Test
    fun testDummyNotificationPersistenceFlow() = runBlocking {
        val urgentEntity = NotificationEntity(
            title = "Google Maps",
            text = "Turn right in 200 feet",
            packageName = "com.google.android.apps.maps",
            timestamp = System.currentTimeMillis(),
            isUrgent = true
        )
        dao.insert(urgentEntity)

        val mutedEntity = NotificationEntity(
            title = "WhatsApp",
            text = "Hey! What's up?",
            packageName = "com.whatsapp",
            timestamp = System.currentTimeMillis() + 1000,
            isUrgent = false
        )
        dao.insert(mutedEntity)

        val shownList = dao.getByUrgency(true)
        assertEquals(1, shownList.size)
        assertEquals("Google Maps", shownList[0].title)
        assertEquals(true, shownList[0].isUrgent)

        val mutedList = dao.getByUrgency(false)
        assertEquals(1, mutedList.size)
        assertEquals("WhatsApp", mutedList[0].title)
        assertEquals(false, mutedList[0].isUrgent)
    }
}
