package com.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local Room entity for caching AI chat messages.
 * Allows offline viewing of chat history.
 */
@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverId: String? = null,
    val role: String,            // "user" or "assistant"
    val content: String,
    val createdAt: Long = System.currentTimeMillis()
)
