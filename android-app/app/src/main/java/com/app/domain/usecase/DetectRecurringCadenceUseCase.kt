package com.app.domain.usecase

import com.app.domain.model.RecurringSubscription
import com.app.domain.model.Transaction
import com.app.utils.DateUtils
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import javax.inject.Inject

/**
 * Algorithmic Recurring Cadence & Subscription Detector.
 * Identifies periodic bills, subscriptions, mandates, EMIs, and recurring expenses
 * using statistical interval analysis and financial heuristics (Copilot / Monarch Money tier).
 */
class DetectRecurringCadenceUseCase @Inject constructor() {

    fun invoke(transactions: List<Transaction>): List<RecurringSubscription> {
        val now = LocalDate.now()

        // 1. Filter out non-expenses and internal transfers
        val eligibleTxs = transactions.filter { tx ->
            val isExpense = tx.type.equals("expense", ignoreCase = true) || tx.type.equals("debit", ignoreCase = true)
            val isNotTransfer = !tx.category.equals("Transfer", ignoreCase = true)
            isExpense && isNotTransfer && tx.amount > 0.0
        }

        if (eligibleTxs.isEmpty()) return emptyList()

        // 2. Group transactions by merchant / identity key
        val byMerchant = eligibleTxs.groupBy { tx ->
            (tx.normalizedMerchant
                ?: tx.merchant?.ifBlank { null }
                ?: tx.sender.ifBlank { null }
                ?: tx.category).trim().lowercase(Locale.US)
        }

        val detectedList = mutableListOf<RecurringSubscription>()

        for ((_, txList) in byMerchant) {
            // Case A: Explicitly flagged recurring (from SMS mandate / keyword / Subscriptions category)
            val explicitlyRecurring = txList.filter { it.isRecurring || it.category.equals("Subscriptions", ignoreCase = true) }
            if (explicitlyRecurring.isNotEmpty()) {
                val sub = buildSubscriptionFromTransactions(explicitlyRecurring, now)
                if (sub != null) {
                    detectedList.add(sub)
                    continue
                }
            }

            // Case B: Algorithmic Cadence Detection across historical occurrences
            if (txList.size >= 2) {
                // Group by approximate amount (within 5% tolerance for variable bills, or exact)
                val amountGroups = groupSimilarAmounts(txList)
                for (group in amountGroups) {
                    if (group.size >= 2) {
                        val sub = analyzeCadence(group, now)
                        if (sub != null) {
                            detectedList.add(sub)
                        }
                    }
                }
            }
        }

        // Return unique by merchant, sorted by days until due ascending
        return detectedList
            .distinctBy { it.merchantName.lowercase(Locale.US) }
            .sortedBy { it.daysUntilDue }
    }

    private fun groupSimilarAmounts(txs: List<Transaction>): List<List<Transaction>> {
        val sorted = txs.sortedBy { it.amount }
        val groups = mutableListOf<MutableList<Transaction>>()

        for (tx in sorted) {
            val matchingGroup = groups.firstOrNull { g ->
                val avg = g.map { it.amount }.average()
                Math.abs(tx.amount - avg) / avg <= 0.08 // 8% tolerance
            }
            if (matchingGroup != null) {
                matchingGroup.add(tx)
            } else {
                groups.add(mutableListOf(tx))
            }
        }

        return groups
    }

    private fun analyzeCadence(group: List<Transaction>, now: LocalDate): RecurringSubscription? {
        val datedTxs = group.mapNotNull { tx ->
            val date = parseLocalDate(tx.date, tx.timestamp)
            if (date != null) tx to date else null
        }.sortedBy { it.second }

        if (datedTxs.size < 2) return null

        val intervals = mutableListOf<Long>()
        for (i in 1 until datedTxs.size) {
            val days = ChronoUnit.DAYS.between(datedTxs[i - 1].second, datedTxs[i].second)
            intervals.add(days)
        }

        val avgInterval = intervals.average()

        val (cadence, expectedCycleDays) = when {
            avgInterval in 24.0..36.0 -> "monthly" to 30L
            avgInterval in 6.0..8.5 -> "weekly" to 7L
            avgInterval in 12.0..16.0 -> "bi-weekly" to 14L
            avgInterval in 80.0..105.0 -> "quarterly" to 90L
            avgInterval in 340.0..390.0 -> "yearly" to 365L
            else -> return null
        }

        val latestPair = datedTxs.last()
        val latestTx = latestPair.first
        val latestDate = latestPair.second

        var nextDate = latestDate.plusDays(expectedCycleDays)
        while (nextDate.isBefore(now)) {
            nextDate = nextDate.plusDays(expectedCycleDays)
        }

        val daysUntilDue = ChronoUnit.DAYS.between(now, nextDate).toInt()
        val avgAmount = group.map { it.amount }.average()
        val displayName = latestTx.merchant?.ifBlank { null }
            ?: latestTx.normalizedMerchant?.ifBlank { null }
            ?: latestTx.sender.ifBlank { null }
            ?: latestTx.category

        return RecurringSubscription(
            merchantName = displayName,
            category = latestTx.category,
            averageAmount = Math.round(avgAmount * 100.0) / 100.0,
            cadence = cadence,
            occurrences = group.size,
            lastDate = latestDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
            nextExpectedDate = nextDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
            daysUntilDue = daysUntilDue,
            transactionIds = group.map { it.id }
        )
    }

    private fun buildSubscriptionFromTransactions(txList: List<Transaction>, now: LocalDate): RecurringSubscription? {
        val datedTxs = txList.mapNotNull { tx ->
            val date = parseLocalDate(tx.date, tx.timestamp)
            if (date != null) tx to date else null
        }.sortedBy { it.second }

        val latestPair = datedTxs.lastOrNull() ?: return null
        val latestTx = latestPair.first
        val latestDate = latestPair.second

        var nextDate = latestDate.plusMonths(1)
        while (nextDate.isBefore(now)) {
            nextDate = nextDate.plusMonths(1)
        }

        val daysUntilDue = ChronoUnit.DAYS.between(now, nextDate).toInt()
        val avgAmount = txList.map { it.amount }.average()
        val displayName = latestTx.merchant?.ifBlank { null }
            ?: latestTx.normalizedMerchant?.ifBlank { null }
            ?: latestTx.sender.ifBlank { null }
            ?: latestTx.category

        return RecurringSubscription(
            merchantName = displayName,
            category = latestTx.category,
            averageAmount = Math.round(avgAmount * 100.0) / 100.0,
            cadence = "monthly",
            occurrences = txList.size,
            lastDate = latestDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
            nextExpectedDate = nextDate.format(DateTimeFormatter.ISO_LOCAL_DATE),
            daysUntilDue = daysUntilDue,
            transactionIds = txList.map { it.id }
        )
    }

    private fun parseLocalDate(dateStr: String?, timestamp: Long): LocalDate? {
        if (!dateStr.isNullOrBlank()) {
            try {
                return DateUtils.parseIsoDateToLocalDate(dateStr)
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
