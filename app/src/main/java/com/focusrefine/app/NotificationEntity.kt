package com.focusrefine.app

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String?,
    val text: String?,
    val packageName: String,
    val timestamp: Long,
    val isUrgent: Boolean = false, // false = Archived, true = Shown (Urgent)
    val isLearned: Boolean = false, // true when explicitly customized/actioned by user
    val synthesizedRule: String? = null, // AI-synthesized rule tag e.g. "[MUTE] Casual chat invitations"
    val explanation: String? = null, // AI-generated or heuristic explanation of why the action was taken
    val confidence: Float? = null // 0.5..1.0 = how far the decision was from the line between Shown and Muted; null when not applicable
)
