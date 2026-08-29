package com.doicare.app

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface NotificationDao {
    @Insert
    fun insert(notification: NotificationEntity)

    @Query("SELECT * FROM archived_notifications ORDER BY timestamp DESC")
    fun getAll(): List<NotificationEntity>

    @Query("DELETE FROM archived_notifications")
    fun deleteAll()
}
