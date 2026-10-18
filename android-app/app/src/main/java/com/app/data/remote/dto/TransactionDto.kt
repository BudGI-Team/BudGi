package com.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class TransactionDto(
    @SerializedName("_id") val id: String? = null,
    @SerializedName("userId") val userId: String? = null,
    @SerializedName("amount") val amount: Double,
    @SerializedName("category") val category: String,
    @SerializedName("type") val type: String, // "income" or "expense"
    @SerializedName("description") val description: String = "",
    @SerializedName("merchant") val merchant: String = "",
    @SerializedName("date") val date: String, // ISO date string
    @SerializedName("source") val source: String = "manual",
    @SerializedName("normalizedMerchant") val normalizedMerchant: String? = null,
    @SerializedName("categorySource") val categorySource: String? = null,
    @SerializedName("aiCategory") val aiCategory: String? = null,
    @SerializedName("aiConfidence") val aiConfidence: Double? = null,
    @SerializedName("isRecurring") val isRecurring: Boolean = false,
    @SerializedName("isAnomaly") val isAnomaly: Boolean = false,
    @SerializedName("clientId") val clientId: String? = null,
    @SerializedName("isDeleted") val isDeleted: Boolean = false,
    @SerializedName("splitDetails") val splitDetails: List<SplitDetailDto>? = null,
    @SerializedName("createdAt") val createdAt: String? = null,
    @SerializedName("updatedAt") val updatedAt: String? = null,
    // SMS-specific fields
    @SerializedName("timestamp") val timestamp: Long? = null,
    @SerializedName("upiId") val upiId: String? = null,
    @SerializedName("referenceNumber") val referenceNumber: String? = null,
    @SerializedName("accountLast4") val accountLast4: String? = null,
    @SerializedName("availableBalance") val availableBalance: Double? = null,
    @SerializedName("rawSms") val rawSms: String? = null,
    @SerializedName("smsBodyDate") val smsBodyDate: String? = null,
    @SerializedName("sender") val sender: String? = null,
    @SerializedName("alias") val alias: String? = null,
    @SerializedName("isReviewed") val isReviewed: Boolean = true
)

data class SplitDetailDto(
    @SerializedName("personName") val personName: String,
    @SerializedName("amount") val amount: Double,
    @SerializedName("settled") val settled: Boolean = false
)

data class TransactionListData(
    @SerializedName("transactions") val transactions: List<TransactionDto>,
    @SerializedName("pagination") val pagination: PaginationMeta? = null
)

data class TransactionSingleData(
    @SerializedName("transaction") val transaction: TransactionDto
)

data class TransactionSummaryData(
    @SerializedName("totalIncome") val totalIncome: Double = 0.0,
    @SerializedName("totalExpense") val totalExpense: Double = 0.0,
    @SerializedName("byCategory") val byCategory: Map<String, Double> = emptyMap(),
    @SerializedName("count") val count: Int = 0
)

data class ScannedReceiptDto(
    @SerializedName("merchant") val merchant: String? = null,
    @SerializedName("amount") val amount: Double = 0.0,
    @SerializedName("date") val date: String? = null,
    @SerializedName("category") val category: String? = null,
    @SerializedName("description") val description: String? = null
)

data class CsvImportResultDto(
    @SerializedName("imported") val imported: Int = 0,
    @SerializedName("skipped") val skipped: Int = 0,
    @SerializedName("duplicates") val duplicates: Int = 0,
    @SerializedName("failed") val failed: Int = 0,
    @SerializedName("total") val total: Int = 0,
    @SerializedName("parseErrors") val parseErrors: Int = 0
)
