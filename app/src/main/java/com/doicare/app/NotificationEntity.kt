package com.doicare.app

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "archived_notifications")
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String?,
    val text: String?,
    val packageName: String,
    val timestamp: Long
)
