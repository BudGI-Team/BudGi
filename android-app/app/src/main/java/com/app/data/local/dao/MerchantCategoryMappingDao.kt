package com.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.app.data.local.entity.MerchantCategoryMappingEntity

@Dao
interface MerchantCategoryMappingDao {

    /**
     * Look up a learned category by normalized merchant fingerprint.
     */
    @Query("SELECT * FROM merchant_category_mappings WHERE normalizedMerchant = :normalizedMerchant LIMIT 1")
    suspend fun findByNormalizedMerchant(normalizedMerchant: String): MerchantCategoryMappingEntity?

    /**
     * Upsert a merchant→category mapping.
     * REPLACE strategy ensures we always keep the latest user preference.
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(mapping: MerchantCategoryMappingEntity): Long

    /**
     * Get all mappings as Flow (for merchant management).
     */
    @Query("SELECT * FROM merchant_category_mappings ORDER BY frequency DESC, lastUsedTimestamp DESC")
    fun observeAll(): kotlinx.coroutines.flow.Flow<List<MerchantCategoryMappingEntity>>

    /**
     * Update the nickname / alias for a merchant.
     */
    @Query("UPDATE merchant_category_mappings SET alias = :alias, updatedAt = :updatedAt, syncStatus = 'pending_update' WHERE normalizedMerchant = :normalizedMerchant")
    suspend fun updateAlias(normalizedMerchant: String, alias: String, updatedAt: String)

    /**
     * Increment frequency count and touch timestamp when an existing merchant is seen again.
     */
    @Query("UPDATE merchant_category_mappings SET frequency = frequency + 1, lastUsedTimestamp = :timestamp, updatedAt = :updatedAt WHERE normalizedMerchant = :normalizedMerchant")
    suspend fun incrementFrequency(normalizedMerchant: String, timestamp: Long, updatedAt: String)

    /**
     * Delete a single mapping.
     */
    @Query("DELETE FROM merchant_category_mappings WHERE normalizedMerchant = :normalizedMerchant")
    suspend fun deleteByNormalizedMerchant(normalizedMerchant: String)

    /**
     * Get all mappings (for sync to server).
     */
    @Query("SELECT * FROM merchant_category_mappings")
    suspend fun getAll(): List<MerchantCategoryMappingEntity>

    /**
     * Replace all mappings (used when syncing from server).
     */
    @Query("DELETE FROM merchant_category_mappings")
    suspend fun deleteAll()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(mappings: List<MerchantCategoryMappingEntity>)
}
