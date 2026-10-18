package com.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Room entity for storing per-user merchant → category learning.
 * When a user manually categorizes a transaction, we store the normalized merchant
 * so all future transactions from the same merchant are auto-categorized.
 */
@Entity(
    tableName = "merchant_category_mappings",
    indices = [
        Index(value = ["normalizedMerchant"], unique = true),
        Index(value = ["category"])
    ]
)
data class MerchantCategoryMappingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val merchantKey: String,           // Original merchant/UPI as-is
    val normalizedMerchant: String,    // Cleaned fingerprint for matching
    val category: String,
    val alias: String? = null,         // User-given friendly nickname (e.g. "Sharma Ji Chai")
    val confidence: Double = 1.0,
    val frequency: Int = 1,            // Number of times used/visited
    val lastUsedTimestamp: Long = 0L,  // Epoch millis of last visit
    val learnedFromUser: Boolean = true,
    val source: String = "UserLearned",  // "UserLearned", "AI", "Rule"
    val syncStatus: String = "pending_create",
    val createdAt: String? = null,
    val updatedAt: String? = null
)
