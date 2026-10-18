package com.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class SyncRequest(
    @SerializedName("lastSyncAt") val lastSyncAt: String? = null,
    @SerializedName("pendingTransactions") val pendingTransactions: List<TransactionDto> = emptyList(),
    @SerializedName("pendingUpdates") val pendingUpdates: List<PendingUpdate> = emptyList(),
    @SerializedName("pendingDeletes") val pendingDeletes: List<String> = emptyList(),
    @SerializedName("pendingGoals") val pendingGoals: List<GoalDto> = emptyList(),
    @SerializedName("pendingBudgets") val pendingBudgets: List<BudgetDto> = emptyList(),
    @SerializedName("deviceId") val deviceId: String? = null,
    @SerializedName("clientVersion") val clientVersion: String? = null
)

data class PendingUpdate(
    @SerializedName("id") val id: String,
    @SerializedName("changes") val changes: Map<String, Any>,
    @SerializedName("updatedAt") val updatedAt: String
)

data class SyncResponseData(
    @SerializedName("newTransactions") val newTransactions: List<TransactionDto> = emptyList(),
    @SerializedName("updatedTransactions") val updatedTransactions: List<TransactionDto> = emptyList(),
    @SerializedName("deletedTransactionIds") val deletedTransactionIds: List<String> = emptyList(),
    @SerializedName("budgets") val budgets: List<BudgetDto> = emptyList(),
    @SerializedName("goals") val goals: List<GoalDto> = emptyList(),
    @SerializedName("alerts") val alerts: List<AlertDto> = emptyList(),
    @SerializedName("importResult") val importResult: ImportResult? = null,
    @SerializedName("conflicts") val conflicts: List<SyncConflict> = emptyList(),
    @SerializedName("syncTimestamp") val syncTimestamp: String
)

data class ImportResult(
    @SerializedName("imported") val imported: Int = 0,
    @SerializedName("duplicates") val duplicates: Int = 0,
    @SerializedName("failed") val failed: Int = 0,
    @SerializedName("total") val total: Int = 0,
    @SerializedName("error") val error: String? = null
)

data class SyncConflict(
    @SerializedName("id") val id: String? = null,
    @SerializedName("type") val type: String? = null,
    @SerializedName("resolution") val resolution: String = "server_wins",
    @SerializedName("error") val error: String? = null
)

data class DeltaPullData(
    @SerializedName("transactions") val transactions: List<TransactionDto> = emptyList(),
    @SerializedName("deletedTransactionIds") val deletedTransactionIds: List<String> = emptyList(),
    @SerializedName("budgets") val budgets: List<BudgetDto> = emptyList(),
    @SerializedName("goals") val goals: List<GoalDto> = emptyList(),
    @SerializedName("alerts") val alerts: List<AlertDto> = emptyList(),
    @SerializedName("syncTimestamp") val syncTimestamp: String
)

// ── Analytics ────────────────────────────────────────────────────────

data class AnalyticsSummaryData(
    @SerializedName("totalIncome") val totalIncome: Double = 0.0,
    @SerializedName("totalExpense") val totalExpense: Double = 0.0,
    @SerializedName("savings") val savings: Double = 0.0,
    @SerializedName("byCategory") val byCategory: Map<String, Double> = emptyMap(),
    @SerializedName("transactionCount") val transactionCount: Int = 0,
    @SerializedName("avgDaily") val avgDaily: Double = 0.0,
    @SerializedName("topCategories") val topCategories: List<CategoryStat> = emptyList()
)

data class CategoryStat(
    @SerializedName("category") val category: String,
    @SerializedName("total") val total: Double,
    @SerializedName("count") val count: Int = 0,
    @SerializedName("percentage") val percentage: Double = 0.0
)
