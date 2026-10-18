package com.app.domain.usecase

import com.app.domain.model.CashflowProjection
import com.app.domain.model.DailyProjection
import com.app.domain.model.Transaction
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

class PredictiveCashflowUseCase @Inject constructor() {
    
    fun invoke(transactions: List<Transaction>): CashflowProjection {
        val now = LocalDate.now()
        
        // 1. Determine baseline balance across all known accounts
        // Group transactions with a reported available balance by account key
        val balanceTxsByAccount = transactions
            .filter { it.availableBalance != null && it.availableBalance >= 0.0 }
            .groupBy { tx ->
                when {
                    !tx.accountLast4.isNullOrBlank() -> "acc_${tx.accountLast4.trim()}"
                    tx.sender.isNotBlank() -> "snd_${tx.sender.trim().uppercase()}"
                    else -> "default"
                }
            }
        
        var currentBalance = if (balanceTxsByAccount.isNotEmpty()) {
            balanceTxsByAccount.values.sumOf { txList ->
                // Pick the most recent transaction by timestamp (or parsed date)
                val latestTx = txList.maxByOrNull { it.timestamp }
                latestTx?.availableBalance ?: 0.0
            }
        } else {
            0.0
        }

        if (currentBalance <= 0.0) {
            // Fallback: Total income - Total expense across all transactions
            val totalIncome = transactions.filter { isIncome(it.type) }.sumOf { it.amount }
            val totalExpense = transactions.filter { isExpense(it.type) }.sumOf { it.amount }
            currentBalance = (totalIncome - totalExpense).coerceAtLeast(0.0)
        }

        // 2. Calculate daily burn rate (Exclude recurring expenses and self-transfers)
        val thirtyDaysAgo = now.minusDays(30)
        val recentExpenses = transactions.filter { 
            isExpense(it.type) && 
            !it.isRecurring && 
            !isTransfer(it.category) &&
            parseDate(it.date, it.timestamp)?.let { d -> !d.isBefore(thirtyDaysAgo) && !d.isAfter(now) } ?: false
        }
        
        val totalRecentSpend = recentExpenses.sumOf { it.amount }
        val dailyBurnRate = (totalRecentSpend / 30.0).coerceAtLeast(0.0)

        // 3. Identify recurring expenses (upcoming in next 30 days)
        // Simulate them hitting on the same day of the month as their latest occurrence
        val recurringTxs = transactions.filter { it.isRecurring && isExpense(it.type) }
        val recurringByDay = mutableMapOf<Int, Double>()
        
        recurringTxs.groupBy { it.merchant ?: it.category }.forEach { (_, txs) ->
            // Use the most recent occurrence
            val latest = txs.maxByOrNull { it.timestamp }
            if (latest != null) {
                val parsed = parseDate(latest.date, latest.timestamp)
                if (parsed != null) {
                    val dayOfMonth = parsed.dayOfMonth
                    recurringByDay[dayOfMonth] = (recurringByDay[dayOfMonth] ?: 0.0) + latest.amount
                }
            }
        }

        val totalRecurring = recurringByDay.values.sum()

        // 4. Project balances for next 30 days
        val projections = mutableListOf<DailyProjection>()
        var runningBalance = currentBalance
        
        for (i in 1..30) {
            val projDate = now.plusDays(i.toLong())
            val dateStr = projDate.format(DateTimeFormatter.ISO_LOCAL_DATE)
            
            // Subtract daily burn
            runningBalance -= dailyBurnRate
            
            // Subtract recurring bills if any land on this day
            val recurringToday = recurringByDay[projDate.dayOfMonth] ?: 0.0
            runningBalance -= recurringToday
            
            projections.add(DailyProjection(dayOffset = i, date = dateStr, projectedBalance = runningBalance))
        }

        return CashflowProjection(
            currentBalance = currentBalance,
            averageDailyBurnRate = dailyBurnRate,
            identifiedRecurringExpenses = totalRecurring,
            projectedBalances = projections
        )
    }

    private fun isExpense(type: String): Boolean {
        val t = type.lowercase()
        return t == "expense" || t == "debit"
    }

    private fun isIncome(type: String): Boolean {
        val t = type.lowercase()
        return t == "income" || t == "credit"
    }

    private fun isTransfer(category: String): Boolean {
        val c = category.lowercase()
        return c.contains("transfer")
    }

    private fun parseDate(dateStr: String?, timestamp: Long): LocalDate? {
        if (!dateStr.isNullOrBlank()) {
            try {
                val clean = dateStr.take(10)
                return LocalDate.parse(clean, DateTimeFormatter.ISO_LOCAL_DATE)
            } catch (_: Exception) {}
        }
        if (timestamp > 0) {
            try {
                return java.time.Instant.ofEpochMilli(timestamp)
                    .atZone(java.time.ZoneId.systemDefault())
                    .toLocalDate()
            } catch (_: Exception) {}
        }
        return null
    }
}
