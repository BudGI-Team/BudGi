package com.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.app.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<TransactionEntity>): List<Long>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: TransactionEntity): Long

    @Query("SELECT * FROM transactions WHERE syncStatus != 'pending_delete' ORDER BY date DESC, id DESC")
    fun observeTransactions(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE serverId = :serverId LIMIT 1")
    suspend fun getByServerId(serverId: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE clientId = :clientId LIMIT 1")
    suspend fun getByClientId(clientId: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE syncStatus != 'synced'")
    suspend fun getPendingSync(): List<TransactionEntity>

    @Query("UPDATE transactions SET syncStatus = :status WHERE id = :id")
    suspend fun updateSyncStatus(id: Long, status: String)

    @Query("UPDATE transactions SET serverId = :serverId, syncStatus = 'synced' WHERE id = :id")
    suspend fun markSynced(id: Long, serverId: String)

    @Query("DELETE FROM transactions WHERE serverId IN (:serverIds)")
    suspend fun deleteByServerIds(serverIds: List<String>)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    // ── SMS dedup: primary check by reference number ────────────────────
    @Query("""
        SELECT COUNT(*) FROM transactions 
        WHERE referenceNumber = :refNumber 
        AND referenceNumber IS NOT NULL 
        AND referenceNumber != ''
        AND syncStatus != 'pending_delete'
    """)
    suspend fun countByReferenceNumber(refNumber: String): Int

    @Query("""
        SELECT * FROM transactions 
        WHERE referenceNumber = :refNumber 
        AND referenceNumber IS NOT NULL 
        AND referenceNumber != ''
        AND syncStatus != 'pending_delete'
        LIMIT 1
    """)
    suspend fun getByReferenceNumber(refNumber: String): TransactionEntity?

    // ── SMS dedup: fallback check by amount + timestamp range + sender ──
    @Query("""
        SELECT COUNT(*) FROM transactions 
        WHERE amount = :amount 
        AND timestamp BETWEEN :minTimestamp AND :maxTimestamp
        AND sender = :sender
        AND syncStatus != 'pending_delete'
    """)
    suspend fun countByAmountTimestampRangeSender(
        amount: Double,
        minTimestamp: Long,
        maxTimestamp: Long,
        sender: String
    ): Int

    // ── SMS dedup: Advanced logical check ──
    @Query("""
        SELECT * FROM transactions 
        WHERE amount = :amount 
        AND timestamp BETWEEN :minTimestamp AND :maxTimestamp
        AND (:accountLast4 IS NULL OR accountLast4 = :accountLast4 OR accountLast4 IS NULL)
        AND syncStatus != 'pending_delete'
    """)
    suspend fun getPotentialDuplicates(
        amount: Double,
        minTimestamp: Long,
        maxTimestamp: Long,
        accountLast4: String?
    ): List<TransactionEntity>

    // ── SMS dedup: exact rawSms check ──
    @Query("""
        SELECT COUNT(*) FROM transactions 
        WHERE rawSms = :rawSms 
        AND rawSms IS NOT NULL 
        AND rawSms != ''
        AND syncStatus != 'pending_delete'
    """)
    suspend fun countByRawSms(rawSms: String): Int

    @Query("""
        SELECT * FROM transactions 
        WHERE rawSms = :rawSms 
        AND rawSms IS NOT NULL 
        AND rawSms != ''
        AND syncStatus != 'pending_delete'
        LIMIT 1
    """)
    suspend fun getByRawSms(rawSms: String): TransactionEntity?

    // ── Broad deduplication for same calendar day ──
    @Query("""
        SELECT * FROM transactions 
        WHERE amount = :amount 
        AND (substr(date, 1, 10) = :dateDay OR (timestamp >= :minTimestamp AND timestamp <= :maxTimestamp))
        AND syncStatus != 'pending_delete'
    """)
    suspend fun getPotentialDuplicatesBroad(
        amount: Double,
        dateDay: String,
        minTimestamp: Long,
        maxTimestamp: Long
    ): List<TransactionEntity>

    @Query("DELETE FROM transactions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM transactions WHERE id IN (:ids)")
    suspend fun deleteByIds(ids: List<Long>)

    @Query("SELECT * FROM transactions WHERE syncStatus != 'pending_delete'")
    suspend fun getAllActiveTransactions(): List<TransactionEntity>

    // Legacy dedup query (kept for backward compat)
    @Query("""
        SELECT COUNT(*) FROM transactions 
        WHERE amount = :amount AND date = :date AND sender = :sender
        AND syncStatus != 'pending_delete'
    """)
    suspend fun countByAmountDateSender(amount: Double, date: String, sender: String): Int

    // ── Get latest SMS timestamp for incremental scan ───────────────────
    @Query("""
        SELECT MAX(timestamp) FROM transactions 
        WHERE source = 'sms' AND timestamp > 0
    """)
    suspend fun getLatestSmsTimestamp(): Long?

    // ── Analytics queries ───────────────────────────────────────────────
    @Query("""
        SELECT category, SUM(amount) as total 
        FROM transactions 
        WHERE (type = 'expense' OR type = 'debit') AND syncStatus != 'pending_delete'
        AND substr(date, 1, 7) = :yearMonth 
        GROUP BY category
    """)
    fun observeCategoryTotalsByMonth(yearMonth: String): Flow<List<CategoryTotal>>

    @Query("""
        SELECT SUM(amount) FROM transactions 
        WHERE (type = 'expense' OR type = 'debit') AND syncStatus != 'pending_delete'
        AND substr(date, 1, 7) = :yearMonth
    """)
    fun observeTotalSpendingByMonth(yearMonth: String): Flow<Double?>

    @Query("""
        SELECT SUM(amount) FROM transactions 
        WHERE (type = 'income' OR type = 'credit') AND syncStatus != 'pending_delete'
        AND substr(date, 1, 7) = :yearMonth
    """)
    fun observeTotalIncomeByMonth(yearMonth: String): Flow<Double?>

    @Query("""
        SELECT SUM(amount) FROM transactions 
        WHERE (type = 'expense' OR type = 'debit') AND syncStatus != 'pending_delete'
        AND substr(date, 1, 10) = :todayIsoDate
    """)
    suspend fun getDailySpending(todayIsoDate: String): Double?

    @Query("""
        SELECT SUM(amount) FROM transactions 
        WHERE (type = 'expense' OR type = 'debit') AND syncStatus != 'pending_delete'
        AND substr(date, 1, 7) = :yearMonth
    """)
    suspend fun getMonthlySpending(yearMonth: String): Double?

    @Query("""
        SELECT SUM(amount) FROM transactions 
        WHERE (type = 'income' OR type = 'credit') AND syncStatus != 'pending_delete'
        AND substr(date, 1, 7) = :yearMonth
    """)
    suspend fun getMonthlyIncome(yearMonth: String): Double?

    // ── Merchant Learning queries ───────────────────────────────────────
    @Query("""
        UPDATE transactions 
        SET category = :category, 
            categorySource = 'UserLearned',
            syncStatus = CASE WHEN serverId IS NOT NULL THEN 'pending_update' ELSE syncStatus END,
            updatedAt = :updatedAt
        WHERE normalizedMerchant = :normalizedMerchant
        AND syncStatus != 'pending_delete'
    """)
    suspend fun updateCategoryByNormalizedMerchant(normalizedMerchant: String, category: String, updatedAt: String)

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): TransactionEntity?

    @Query("""
        UPDATE transactions 
        SET category = :category, 
            categorySource = :categorySource,
            syncStatus = CASE WHEN serverId IS NOT NULL THEN 'pending_update' ELSE syncStatus END,
            updatedAt = :updatedAt
        WHERE id = :id
    """)
    suspend fun updateCategoryById(id: Long, category: String, categorySource: String, updatedAt: String)

    @Query("""
        UPDATE transactions 
        SET syncStatus = 'synced'
        WHERE normalizedMerchant = :normalizedMerchant
        AND syncStatus = 'pending_update'
    """)
    suspend fun markMerchantTransactionsSynced(normalizedMerchant: String)

    @Query("""
        SELECT amount FROM transactions 
        WHERE type = 'expense' AND category = :category AND syncStatus != 'pending_delete'
        ORDER BY date DESC LIMIT :limit
    """)
    suspend fun getRecentExpensesByCategory(category: String, limit: Int = 30): List<Double>

    @Query("""
        UPDATE transactions 
        SET isRecurring = 1, 
            syncStatus = CASE WHEN syncStatus = 'synced' THEN 'pending_update' ELSE syncStatus END 
        WHERE id IN (:localIds)
    """)
    suspend fun markRecurringByLocalIds(localIds: List<Long>)

    @Query("""
        UPDATE transactions 
        SET isRecurring = 1, 
            syncStatus = CASE WHEN syncStatus = 'synced' THEN 'pending_update' ELSE syncStatus END 
        WHERE serverId IN (:serverIds)
    """)
    suspend fun markRecurringByServerIds(serverIds: List<String>)

    // ── Unreviewed Micro-Spends & Catch-Up Engine ──────────────────────
    @Query("""
        SELECT * FROM transactions 
        WHERE isReviewed = 0 
        AND type = 'expense' 
        AND syncStatus != 'pending_delete'
        ORDER BY timestamp DESC
    """)
    fun observeUnreviewedTransactions(): kotlinx.coroutines.flow.Flow<List<TransactionEntity>>

    @Query("""
        UPDATE transactions 
        SET category = :category, 
            isReviewed = 1, 
            categorySource = 'UserLearned', 
            syncStatus = CASE WHEN syncStatus = 'synced' THEN 'pending_update' ELSE syncStatus END 
        WHERE id = :id
    """)
    suspend fun updateCategoryAndReview(id: Long, category: String)

    @Query("""
        UPDATE transactions 
        SET alias = :alias, 
            syncStatus = CASE WHEN syncStatus = 'synced' THEN 'pending_update' ELSE syncStatus END 
        WHERE id = :id
    """)
    suspend fun updateTransactionAlias(id: Long, alias: String)

    @Query("""
        UPDATE transactions 
        SET alias = :alias, 
            syncStatus = CASE WHEN syncStatus = 'synced' THEN 'pending_update' ELSE syncStatus END 
        WHERE normalizedMerchant = :normalizedMerchant
    """)
    suspend fun updateAliasForMerchant(normalizedMerchant: String, alias: String)

    @Query("""
        UPDATE transactions 
        SET isReviewed = 1 
        WHERE id = :id
    """)
    suspend fun markReviewed(id: Long)

    @Query("""
        UPDATE transactions 
        SET isReviewed = 1 
        WHERE id IN (:ids)
    """)
    suspend fun markAllReviewed(ids: List<Long>)

    // ── Reversal & Refund Auto-Reconciliation ──
    @Query("""
        SELECT * FROM transactions 
        WHERE type = 'expense' 
          AND amount = :amount 
          AND (:accountLast4 IS NULL OR accountLast4 = :accountLast4)
          AND timestamp BETWEEN :minTimestamp AND :maxTimestamp
          AND syncStatus != 'pending_delete'
        ORDER BY timestamp DESC 
        LIMIT 1
    """)
    suspend fun findMatchingDebitForRefund(
        amount: Double,
        accountLast4: String?,
        minTimestamp: Long,
        maxTimestamp: Long
    ): TransactionEntity?

    @Query("""
        UPDATE transactions 
        SET category = 'Reversal', 
            description = description || ' (Reversed / Refunded)',
            syncStatus = CASE WHEN syncStatus = 'synced' THEN 'pending_update' ELSE syncStatus END 
        WHERE id = :id
    """)
    suspend fun markTransactionReversed(id: Long)

    // ── Detected Bank Accounts & Liquidity Matrix ──
    @Query("""
        SELECT 
            t1.accountLast4,
            t1.sender,
            (SELECT t_bal.availableBalance FROM transactions t_bal 
             WHERE t_bal.accountLast4 = t1.accountLast4 AND t_bal.availableBalance IS NOT NULL 
             ORDER BY t_bal.timestamp DESC LIMIT 1) as latestBalance,
            MAX(t1.timestamp) as latestTimestamp,
            COUNT(*) as transactionCount
        FROM transactions t1
        WHERE t1.accountLast4 IS NOT NULL AND t1.accountLast4 != '' AND t1.syncStatus != 'pending_delete'
        GROUP BY t1.accountLast4
        ORDER BY latestTimestamp DESC
    """)
    fun observeDetectedBankAccounts(): kotlinx.coroutines.flow.Flow<List<DetectedBankSummary>>
}

data class DetectedBankSummary(
    val accountLast4: String,
    val sender: String,
    val latestBalance: Double?,
    val latestTimestamp: Long,
    val transactionCount: Int
)
data class CategoryTotal(
    val category: String,
    val total: Double
)
