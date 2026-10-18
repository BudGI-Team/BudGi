package com.app.domain.model

data class DailySpend(val date: String, val amount: Double)
data class WeeklySpend(val weekLabel: String, val amount: Double)
data class MonthlySpend(val monthLabel: String, val amount: Double)

data class AnalyticsSummary(
    val monthlySpending: Double = 0.0,
    val monthlyIncome: Double = 0.0,
    val categoryBreakdown: Map<String, Double> = emptyMap(),
    val estimatedSavings: Double = 0.0,
    val previousMonthSpending: Double = 0.0,
    val totalBudget: Double = 0.0,
    val spentToday: Double = 0.0,
    val safeToSpendDaily: Double = 0.0,
    val dailyBurnRate: Double = 0.0,
    val upcomingBillsTotal: Double = 0.0,
    val spendingByDay: List<DailySpend> = emptyList(),
    val spendingByWeek: List<WeeklySpend> = emptyList(),
    val spendingByMonth: List<MonthlySpend> = emptyList(),
    val incomeByMonth: List<MonthlySpend> = emptyList(),
    val savingsByMonth: List<MonthlySpend> = emptyList(),
    val healthScore: Int = 85,
    val topMerchants: List<Pair<String, Double>> = emptyList()
) {
    val spendingChangePercentage: Double
        get() = if (previousMonthSpending > 0) {
            ((monthlySpending - previousMonthSpending) / previousMonthSpending) * 100
        } else 0.0

    val budgetProgress: Float
        get() = if (totalBudget > 0) (monthlySpending / totalBudget).toFloat() else 0f

    val savingsRate: Double
        get() = if (monthlyIncome > 0) ((monthlyIncome - monthlySpending) / monthlyIncome) * 100 else 0.0
}
