package com.app.domain.model

data class SplitDetail(
    val personName: String,
    val amount: Double,
    val settled: Boolean = false
)

data class Transaction(
    val id: String = "",
    val clientId: String? = null,
    val amount: Double = 0.0,
    val type: String = "expense",       // "income" or "expense"
    val category: String = "Others",
    val description: String = "",
    val merchant: String? = null,
    val normalizedMerchant: String? = null,
    val date: String = "",              // ISO date string (YYYY-MM-DD)
    val source: String = "manual",      // "manual", "csv", "sms", "import"
    val categorySource: String = "Default", // "AI", "Rule", "UserLearned", "Manual", "Default"
    val isRecurring: Boolean = false,
    val isAnomaly: Boolean = false,
    val aiCategory: String? = null,
    val splitDetails: List<SplitDetail> = emptyList(),
    // SMS-specific fields
    val method: String = "",
    val timestamp: Long = 0,
    val sender: String = "",
    val upiId: String? = null,
    val referenceNumber: String? = null,
    val accountLast4: String? = null,
    val availableBalance: Double? = null,
    val rawSms: String? = null,
    val smsBodyDate: String? = null,    // ISO date from SMS body (when different from SMS timestamp)
    val alias: String? = null,          // Custom user nickname (e.g. "Corner Tea Stall")
    val isReviewed: Boolean = true      // false = needs review in Daily Catch-Up Deck
)
