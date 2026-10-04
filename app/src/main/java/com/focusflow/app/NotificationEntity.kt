package com.focusflow.app

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String?,
    val text: String?,
    val packageName: String,
    val timestamp: Long,
    val isUrgent: Boolean = false // false = Archived, true = Shown (Urgent)
)
