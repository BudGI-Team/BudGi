package com.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["serverId"], unique = true),
        Index(value = ["clientId"], unique = true),
        Index(value = ["syncStatus"]),
        Index(value = ["date"]),
        Index(value = ["referenceNumber"])
    ]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverId: String? = null,          // MongoDB _id
    val clientId: String? = null,          // UUID for offline-created items
    val amount: Double,
    val type: String,                      // "income" or "expense"
    val category: String,
    val description: String = "",
    val merchant: String? = null,
    val normalizedMerchant: String? = null,
    val date: String,                      // ISO date string
    val source: String = "manual",         // "manual", "csv", "sms", "import"
    val categorySource: String = "Default", // "AI", "Rule", "UserLearned", "Manual", "Default"
    val aiCategory: String? = null,
    val isRecurring: Boolean = false,
    val isAnomaly: Boolean = false,
    val splitDetailsJson: String? = null,  // Serialized List<SplitDetail>
    val syncStatus: String = "synced",     // "synced", "pending_create", "pending_update", "pending_delete"
    val updatedAt: String? = null,
    val createdAt: String? = null,
    // SMS-specific fields
    val method: String = "",
    val timestamp: Long = 0,
    val sender: String = "",
    val upiId: String? = null,
    val referenceNumber: String? = null,
    val accountLast4: String? = null,
    val availableBalance: Double? = null,
    val rawSms: String? = null,
    val smsBodyDate: String? = null,
    val alias: String? = null,             // Custom user nickname (e.g. "Corner Tea Stall")
    val isReviewed: Boolean = true         // false = needs review in Daily Catch-Up Deck
)
