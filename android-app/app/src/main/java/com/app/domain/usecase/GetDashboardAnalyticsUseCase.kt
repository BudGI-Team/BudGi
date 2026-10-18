package com.app.domain.usecase

import com.app.domain.model.AnalyticsSummary
import com.app.domain.model.DailySpend
import com.app.domain.model.Transaction
import com.app.domain.model.WeeklySpend
import com.app.domain.model.MonthlySpend
import com.app.presentation.viewmodel.DateFilter
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale
import javax.inject.Inject
import com.app.utils.DateUtils

class GetDashboardAnalyticsUseCase @Inject constructor() {
    fun invoke(
        transactions: List<Transaction>,
        dateFilter: DateFilter,
        totalBudget: Double
    ): AnalyticsSummary {
        val now = LocalDate.now()
        val (startDate, endDate) = when (dateFilter) {
            DateFilter.THIS_WEEK -> {
                val start = now.minusDays(now.dayOfWeek.value.toLong() % 7)
                start to start.plusDays(6)
            }
            DateFilter.THIS_MONTH -> {
                now.withDayOfMonth(1) to now.withDayOfMonth(now.lengthOfMonth())
            }
            DateFilter.LAST_MONTH -> {
                val lastMonth = now.minusMonths(1)
                lastMonth.withDayOfMonth(1) to lastMonth.withDayOfMonth(lastMonth.lengthOfMonth())
            }
            DateFilter.LAST_3_MONTHS -> {
                now.minusMonths(3).withDayOfMonth(1) to now.withDayOfMonth(now.lengthOfMonth())
            }
            DateFilter.LAST_6_MONTHS -> {
                now.minusMonths(6).withDayOfMonth(1) to now.withDayOfMonth(now.lengthOfMonth())
            }
            DateFilter.ALL_TIME -> LocalDate.MIN to LocalDate.MAX
        }

        val filteredTx = transactions.filter { tx ->
            val txDate = DateUtils.parseIsoDateToLocalDate(tx.date)
            !txDate.isBefore(startDate) && !txDate.isAfter(endDate)
        }

        val isExpense = { tx: Transaction -> (tx.type.equals("expense", ignoreCase = true) || tx.type.equals("debit", ignoreCase = true)) && !isTransfer(tx.category) }
        val isIncome = { tx: Transaction -> (tx.type.equals("income", ignoreCase = true) || tx.type.equals("credit", ignoreCase = true)) && !isTransfer(tx.category) }

        val totalIncome = filteredTx.filter(isIncome).sumOf { it.amount }
        val totalSpending = filteredTx.filter(isExpense).sumOf { it.amount }
        val categoryBreakdown = filteredTx
            .filter(isExpense)
            .groupBy { it.category }
            .mapValues { entry -> entry.value.sumOf { it.amount } }

        val prevMonthStart = now.minusMonths(1).withDayOfMonth(1)
        val prevMonthEnd = now.minusMonths(1).withDayOfMonth(now.minusMonths(1).lengthOfMonth())
        val prevMonthSpending = transactions.filter { tx ->
            val txDate = DateUtils.parseIsoDateToLocalDate(tx.date)
            isExpense(tx) && !txDate.isBefore(prevMonthStart) && !txDate.isAfter(prevMonthEnd)
        }.sumOf { it.amount }

        // --- BudGi Precision Daily Metrics ---
        // 1. Spending By Day (Historical Bar Chart)
        val expensesOnly = filteredTx.filter(isExpense)
        val spendingByDayMap = expensesOnly.groupBy { tx ->
            val txDate = DateUtils.parseIsoDateToLocalDate(tx.date)
            txDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
        }.mapValues { entry -> entry.value.sumOf { it.amount } }
        
        // Fill missing days with 0.0 for the current filter period (max 30 days to avoid huge arrays)
        val daysToFill = minOf(ChronoUnit.DAYS.between(startDate, endDate).toInt(), 30)
        val displayEndDate = minOf(endDate, now)
        val displayStartDate = displayEndDate.minusDays(daysToFill.toLong())
        
        val spendingByDayList = mutableListOf<DailySpend>()
        var currDate = displayStartDate
        while (!currDate.isAfter(displayEndDate)) {
            val dateStr = currDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
            spendingByDayList.add(DailySpend(dateStr, spendingByDayMap[dateStr] ?: 0.0))
            currDate = currDate.plusDays(1)
        }

        // --- Weekly Spending (Last 12 Weeks across all transactions) ---
        val allExpenses = transactions.filter(isExpense)
        val allIncome = transactions.filter(isIncome)
        val weekFields = WeekFields.of(Locale.getDefault())
        val spendingByWeekMap = allExpenses.groupBy { tx ->
            val txDate = DateUtils.parseIsoDateToLocalDate(tx.date)
            val weekNum = txDate.get(weekFields.weekOfWeekBasedYear())
            val year = txDate.get(weekFields.weekBasedYear())
            "W$weekNum $year"
        }.mapValues { entry -> entry.value.sumOf { it.amount } }

        val spendingByWeekList = mutableListOf<WeeklySpend>()
        val currentWeek = now.get(weekFields.weekOfWeekBasedYear())
        val currentYear = now.get(weekFields.weekBasedYear())
        for (i in 11 downTo 0) {
            var w = currentWeek - i
            var y = currentYear
            if (w <= 0) {
                // approximate previous year's weeks
                val lastYearWeeks = LocalDate.of(y - 1, 12, 31).get(weekFields.weekOfWeekBasedYear())
                w += lastYearWeeks
                y -= 1
            }
            val label = "W$w $y"
            val displayLabel = "W$w"
            spendingByWeekList.add(WeeklySpend(displayLabel, spendingByWeekMap[label] ?: 0.0))
        }

        // --- Monthly Spending, Income & Savings (Last 12 Months) ---
        val spendingByMonthMap = allExpenses.groupBy { tx ->
            val txDate = DateUtils.parseIsoDateToLocalDate(tx.date)
            txDate.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.US))
        }.mapValues { entry -> entry.value.sumOf { it.amount } }

        val incomeByMonthMap = allIncome.groupBy { tx ->
            val txDate = DateUtils.parseIsoDateToLocalDate(tx.date)
            txDate.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.US))
        }.mapValues { entry -> entry.value.sumOf { it.amount } }

        val spendingByMonthList = mutableListOf<MonthlySpend>()
        val incomeByMonthList = mutableListOf<MonthlySpend>()
        val savingsByMonthList = mutableListOf<MonthlySpend>()

        for (i in 11 downTo 0) {
            val mDate = now.minusMonths(i.toLong())
            val label = mDate.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.US))
            val displayLabel = mDate.format(DateTimeFormatter.ofPattern("MMM", Locale.US))
            val sp = spendingByMonthMap[label] ?: 0.0
            val inc = incomeByMonthMap[label] ?: 0.0
            spendingByMonthList.add(MonthlySpend(displayLabel, sp))
            incomeByMonthList.add(MonthlySpend(displayLabel, inc))
            savingsByMonthList.add(MonthlySpend(displayLabel, inc - sp))
        }

        // 2. Spent Today
        val todayStr = now.format(DateTimeFormatter.ISO_LOCAL_DATE)
        val spentToday = spendingByDayMap[todayStr] ?: 0.0

        // 3. Upcoming Bills (Very basic heuristic: recurring transactions from previous month that haven't happened this month)
        val upcomingBillsTotal = transactions
            .filter { it.isRecurring && (it.type.equals("expense", ignoreCase = true) || it.type.equals("debit", ignoreCase = true)) }
            .groupBy { it.merchant ?: it.sender }
            .mapNotNull { (_, txs) ->
                // Check if this recurring charge has already happened this month
                val happenedThisMonth = txs.any { tx ->
                    val d = DateUtils.parseIsoDateToLocalDate(tx.date)
                    d.year == now.year && d.monthValue == now.monthValue
                }
                if (!happenedThisMonth) txs.maxByOrNull { it.timestamp }?.amount else null
            }.sum()

        // 4. Burn Rate & Safe To Spend (Only applies to THIS_MONTH)
        var dailyBurnRate = 0.0
        var safeToSpendDaily = 0.0
        if (dateFilter == DateFilter.THIS_MONTH) {
            val daysPassed = now.dayOfMonth.toDouble().coerceAtLeast(1.0)
            dailyBurnRate = totalSpending / daysPassed
            
            val daysLeft = (now.lengthOfMonth() - now.dayOfMonth).toDouble().coerceAtLeast(1.0)
            val remainingBudget = (totalBudget - totalSpending - upcomingBillsTotal).coerceAtLeast(0.0)
            safeToSpendDaily = remainingBudget / daysLeft
        }

        // 5. Robust Financial Health Score (0-100)
        var budgetScore = 35.0
        if (totalBudget > 0) {
            val budgetRatio = totalSpending / totalBudget
            budgetScore = when {
                budgetRatio <= 0.6 -> 40.0
                budgetRatio <= 0.8 -> 35.0
                budgetRatio <= 0.95 -> 28.0
                budgetRatio <= 1.0 -> 20.0
                else -> 10.0
            }
        }

        val savingsRateVal = if (totalIncome > 0) ((totalIncome - totalSpending) / totalIncome) else 0.2
        val savingsScore = when {
            savingsRateVal >= 0.4 -> 30.0
            savingsRateVal >= 0.2 -> 25.0
            savingsRateVal >= 0.1 -> 18.0
            savingsRateVal > 0.0 -> 12.0
            else -> 5.0
        }

        val trendScore = if (prevMonthSpending > 0) {
            val ratio = totalSpending / prevMonthSpending
            when {
                ratio <= 0.85 -> 15.0
                ratio <= 1.0 -> 12.0
                ratio <= 1.2 -> 8.0
                else -> 4.0
            }
        } else 12.0

        val categoryScore = when {
            categoryBreakdown.size >= 4 -> 15.0
            categoryBreakdown.size >= 2 -> 10.0
            else -> 6.0
        }

        val calculatedHealthScore = (budgetScore + savingsScore + trendScore + categoryScore).toInt().coerceIn(30, 99)

        // Top 5 Merchants by Spending
        val topMerchants = expensesOnly
            .groupBy { it.merchant?.ifBlank { null } ?: it.sender }
            .mapValues { it.value.sumOf { tx -> tx.amount } }
            .toList()
            .sortedByDescending { it.second }
            .take(5)

        return AnalyticsSummary(
            monthlySpending = totalSpending,
            monthlyIncome = totalIncome,
            categoryBreakdown = categoryBreakdown,
            estimatedSavings = totalIncome - totalSpending,
            previousMonthSpending = prevMonthSpending,
            totalBudget = totalBudget,
            spentToday = spentToday,
            safeToSpendDaily = safeToSpendDaily,
            dailyBurnRate = dailyBurnRate,
            upcomingBillsTotal = upcomingBillsTotal,
            spendingByDay = spendingByDayList,
            spendingByWeek = spendingByWeekList,
            spendingByMonth = spendingByMonthList,
            incomeByMonth = incomeByMonthList,
            savingsByMonth = savingsByMonthList,
            healthScore = calculatedHealthScore,
            topMerchants = topMerchants
        )
    }

    private fun isTransfer(category: String): Boolean = category.equals("Transfer", ignoreCase = true)
}
