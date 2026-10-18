package com.focusrefine.app

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface NotificationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(notification: NotificationEntity)

    @Query("SELECT * FROM notifications ORDER BY timestamp DESC")
    fun getAll(): List<NotificationEntity>

    @Query("SELECT * FROM notifications WHERE isLearned = 0 ORDER BY timestamp DESC")
    fun getHistory(): List<NotificationEntity>

    @Query("UPDATE notifications SET isUrgent = :isUrgent, explanation = :explanation WHERE id = :id")
    fun updateClassification(id: Int, isUrgent: Boolean, explanation: String?)

    @Query("SELECT * FROM notifications WHERE isUrgent = :isUrgent ORDER BY timestamp DESC")
    fun getByUrgency(isUrgent: Boolean): List<NotificationEntity>

    @Query("SELECT * FROM notifications WHERE isUrgent = :isUrgent AND (title LIKE :query OR text LIKE :query) ORDER BY timestamp DESC")
    fun searchByUrgency(isUrgent: Boolean, query: String): List<NotificationEntity>

    @Query("SELECT * FROM notifications WHERE isLearned = 1 ORDER BY timestamp DESC")
    fun getLearnedRules(): List<NotificationEntity>

    @Query("SELECT * FROM notifications WHERE isLearned = 1 AND synthesizedRule IS NOT NULL ORDER BY timestamp DESC")
    fun getSynthesizedRules(): List<NotificationEntity>

    @Query("SELECT * FROM notifications WHERE isLearned = 1 AND (title = :title OR text LIKE :textQuery) ORDER BY timestamp DESC LIMIT 1")
    fun findMatchingLearnedRule(title: String, textQuery: String): NotificationEntity?

    @Query("DELETE FROM notifications WHERE timestamp < :cutoffTimestamp")
    fun deleteOlderThan(cutoffTimestamp: Long)

    @Query("DELETE FROM notifications")
    fun deleteAll()

    @Query("DELETE FROM notifications WHERE isLearned = 1")
    fun deleteLearnedRules()

    @androidx.room.Delete
    fun delete(notification: NotificationEntity)
}
