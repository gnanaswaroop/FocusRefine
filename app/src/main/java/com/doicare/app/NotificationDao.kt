package com.doicare.app

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface NotificationDao {
    @Insert
    fun insert(notification: NotificationEntity)

    @Query("SELECT * FROM notifications ORDER BY timestamp DESC")
    fun getAll(): List<NotificationEntity>

    @Query("SELECT * FROM notifications WHERE isUrgent = :isUrgent ORDER BY timestamp DESC")
    fun getByUrgency(isUrgent: Boolean): List<NotificationEntity>

    @Query("DELETE FROM notifications")
    fun deleteAll()
}
