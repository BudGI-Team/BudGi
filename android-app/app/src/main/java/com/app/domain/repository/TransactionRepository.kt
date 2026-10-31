package com.app.domain.repository

import com.app.domain.model.AnalyticsSummary
import com.app.domain.model.Budget
import com.app.domain.model.Transaction
import kotlinx.coroutines.flow.Flow

interface TransactionRepository {
    fun observeAllTransactions(): Flow<List<Transaction>>
    fun observeCurrentMonthAnalytics(): Flow<AnalyticsSummary>
    fun observeBudgets(): Flow<List<Budget>>
    fun observeDetectedBankAccounts(): Flow<List<com.app.data.local.dao.DetectedBankSummary>>

    suspend fun createTransaction(transaction: Transaction): Result<Transaction>
    suspend fun updateTransaction(id: String, changes: Map<String, Any>): Result<Unit>
    suspend fun deleteTransaction(id: String): Result<Unit>
    suspend fun clearAllTransactions(): Result<Unit>

    suspend fun saveBudget(budget: Budget): Result<Unit>
    suspend fun deleteBudget(id: String): Result<Unit>

    suspend fun readParseAndStoreSms()
    suspend fun syncWithServer(): Result<Unit>
    suspend fun refreshFromServer(): Result<Unit>

    fun observeUnreviewedTransactions(): Flow<List<Transaction>>

    /**
     * Apply a learned category to a transaction and optionally to all past/future
     * transactions from the same merchant.
     * @param transactionId The local Room ID or serverId of the transaction being edited
     * @param newCategory The new category to apply
     * @param applyToPast If true, bulk-update all past transactions from same merchant
     * @param applyToFuture If true, save mapping so future SMS transactions auto-categorize
     * @param alias Optional custom merchant nickname (e.g. "Corner Tea Stall")
     */
    suspend fun applyCategoryToMerchant(
        transactionId: String,
        newCategory: String,
        applyToPast: Boolean,
        applyToFuture: Boolean,
        alias: String? = null
    ): Result<Unit>

    suspend fun dismissReview(transactionId: String): Result<Unit>

    suspend fun markTransactionsRecurring(transactionIds: List<String>): Result<Unit>
    suspend fun scanReceipt(file: java.io.File): Result<com.app.data.remote.dto.ScannedReceiptDto>
    suspend fun importCsv(file: java.io.File): Result<com.app.data.remote.dto.CsvImportResultDto>
}
