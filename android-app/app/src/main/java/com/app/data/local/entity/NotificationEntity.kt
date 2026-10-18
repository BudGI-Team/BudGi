package com.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notifications")
data class NotificationEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val icon: String, // String representation of icon or category
    val title: String,
    val body: String,
    val type: String,
    val timestamp: Long,
    val isRead: Boolean = false
)
