package com.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "budgets",
    indices = [
        Index(value = ["serverId"], unique = true),
        Index(value = ["category"])
    ]
)
data class BudgetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverId: String? = null,
    val clientId: String? = null,
    val category: String,
    val limitAmount: Double,
    val period: String = "monthly",
    val alertAt: Double = 0.9,
    val color: String = "#0284c7",
    val syncStatus: String = "synced",
    val updatedAt: String? = null,
    val createdAt: String? = null,
    // Legacy field
    val monthYear: String = ""
)
