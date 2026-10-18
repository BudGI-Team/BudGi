package com.app.domain.model

data class DailyProjection(
    val dayOffset: Int,
    val date: String,
    val projectedBalance: Double
)

data class CashflowProjection(
    val currentBalance: Double,
    val averageDailyBurnRate: Double,
    val identifiedRecurringExpenses: Double,
    val projectedBalances: List<DailyProjection>
)
