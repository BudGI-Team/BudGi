package com.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "goals",
    indices = [Index(value = ["serverId"], unique = true)]
)
data class GoalEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverId: String? = null,
    val clientId: String? = null,
    val title: String,
    val targetAmount: Double,
    val currentAmount: Double = 0.0,
    val deadline: String? = null,
    val category: String = "savings",
    val icon: String = "🎯",
    val color: String = "#7C6AF5",
    val isCompleted: Boolean = false,
    val completedAt: String? = null,
    val syncStatus: String = "synced",
    val updatedAt: String? = null,
    val createdAt: String? = null
)


@Entity(tableName = "pending_operations")
data class PendingOperationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val entityType: String,    // "transaction", "budget", "goal", "debt"
    val operationType: String, // "create", "update", "delete"
    val entityId: String,      // local ID or server ID
    val payload: String,       // JSON payload
    val createdAt: Long = System.currentTimeMillis(),
    val retryCount: Int = 0
)
