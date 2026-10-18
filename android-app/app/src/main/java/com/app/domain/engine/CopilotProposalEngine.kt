package com.app.domain.engine

import com.app.data.local.entity.SharedSpaceEntity
import com.app.domain.model.AutonomousActionProposal
import com.app.domain.model.Budget
import com.app.domain.model.Transaction
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Autonomous Copilot Proposal Engine
 * Dynamically analyzes live Room DB state (Budgets, Transactions, Shared Spaces)
 * to formulate deterministic, high-impact financial action proposals.
 * Zero dummy arrays, zero mocked data.
 */
@Singleton
class CopilotProposalEngine @Inject constructor() {

    private val gson = Gson()
    private val listMapType = object : TypeToken<List<Map<String, Any>>>() {}.type

    fun evaluateProposals(
        budgets: List<Budget>,
        transactions: List<Transaction>,
        spaces: List<SharedSpaceEntity>,
        dismissedIds: Set<String> = emptySet()
    ): List<AutonomousActionProposal> {
        val proposals = mutableListOf<AutonomousActionProposal>()

        // 1. Budget Rebalance Proposal
        evaluateBudgetRebalance(budgets, dismissedIds)?.let { proposals.add(it) }

        // 2. Shared Space Debt Reconciliation
        evaluateSpaceDebtReconciliation(transactions, spaces, dismissedIds)?.let { proposals.add(it) }

        // 3. Payday Blueprint Detection
        evaluatePaydayBlueprint(transactions, budgets, dismissedIds)?.let { proposals.add(it) }

        return proposals
    }

    /**
     * Identifies when one budget is strained or breached while another category holds healthy surplus.
     */
    fun evaluateBudgetRebalance(
        budgets: List<Budget>,
        dismissedIds: Set<String> = emptySet()
    ): AutonomousActionProposal.BudgetRebalanceProposal? {
        if (budgets.size < 2) return null

        // Find strained categories: spent >= 80% or spent > limit
        val strained = budgets.filter { it.limit > 0 && (it.spentAmount / it.limit) >= 0.80 }
            .sortedByDescending { it.spentAmount / it.limit }

        // Find donor categories: spent <= 45% and surplus >= ₹1,000
        val surplusCategories = budgets.filter {
            it.limit > 0 &&
            (it.spentAmount / it.limit) <= 0.45 &&
            (it.limit - it.spentAmount) >= 1000.0
        }.sortedByDescending { it.limit - it.spentAmount }

        if (strained.isNotEmpty() && surplusCategories.isNotEmpty()) {
            val target = strained.first()
            val donor = surplusCategories.first()
            if (target.category.equals(donor.category, ignoreCase = true)) return null

            val deficit = (target.spentAmount - target.limit).coerceAtLeast(0.0)
            val availableSurplus = donor.limit - donor.spentAmount

            // Rebalance either the deficit + 15% buffer or minimum ₹1,000, rounded to ₹500
            val rawShift = if (deficit > 0) (deficit * 1.15) else 1000.0
            val shiftAmount = (Math.ceil(rawShift / 500.0) * 500.0)
                .coerceAtMost(availableSurplus * 0.70)
                .coerceAtLeast(500.0)

            val proposalId = "rebal_${donor.category}_to_${target.category}"
            if (dismissedIds.contains(proposalId)) return null

            return AutonomousActionProposal.BudgetRebalanceProposal(
                id = proposalId,
                fromCategory = donor.category,
                toCategory = target.category,
                amount = shiftAmount,
                fromSurplus = availableSurplus,
                toDeficit = deficit
            )
        }
        return null
    }

    /**
     * Cross-references recent incoming credit transactions against pending debts in Shared Spaces.
     */
    private fun evaluateSpaceDebtReconciliation(
        transactions: List<Transaction>,
        spaces: List<SharedSpaceEntity>,
        dismissedIds: Set<String> = emptySet()
    ): AutonomousActionProposal.SpaceDebtSettlementProposal? {
        if (spaces.isEmpty() || transactions.isEmpty()) return null

        val fortyEightHoursAgo = System.currentTimeMillis() - (48 * 60 * 60 * 1000L)
        val recentCredits = transactions.filter {
            val txTime = it.timestamp.takeIf { t -> t > 0L } ?: (it.date.toEpochMilliOrNull() ?: 0L)
            (it.type == "credit" || it.type == "income") &&
            txTime >= fortyEightHoursAgo &&
            it.amount > 0.0
        }

        for (tx in recentCredits) {
            val candidateText = (tx.sender + " " + tx.merchant.orEmpty() + " " + tx.description + " " + tx.rawSms.orEmpty()).lowercase()

            for (space in spaces) {
                val members: List<Map<String, Any>> = try {
                    gson.fromJson(space.membersJson, listMapType) ?: emptyList()
                } catch (_: Exception) {
                    emptyList()
                }

                val expenses: List<Map<String, Any>> = try {
                    gson.fromJson(space.expensesJson, listMapType) ?: emptyList()
                } catch (_: Exception) {
                    emptyList()
                }

                for (member in members) {
                    val mId = member["id"] as? String ?: continue
                    val mName = (member["name"] as? String)?.trim() ?: continue
                    if (mName.isBlank() || mName.equals("You", ignoreCase = true) || mName.equals("me", ignoreCase = true)) continue

                    val cleanFirstName = mName.split(" ").first().lowercase()
                    if (cleanFirstName.length >= 3 && candidateText.contains(cleanFirstName)) {
                        // Calculate debt
                        var memberOwedAmount = 0.0
                        for (exp in expenses) {
                            val isSettle = exp["isSettlement"] as? Boolean ?: false
                            val amt = (exp["amount"] as? Number)?.toDouble() ?: 0.0
                            val paidById = exp["paidById"] as? String ?: ""
                            val splitMembers = (exp["splitWithMemberIds"] as? List<*>)?.mapNotNull { it?.toString() } ?: emptyList()

                            if (isSettle) {
                                if (paidById == mId) memberOwedAmount -= amt
                            } else {
                                if (splitMembers.contains(mId) && paidById != mId) {
                                    val count = if (splitMembers.isNotEmpty()) splitMembers.size else 1
                                    memberOwedAmount += (amt / count)
                                }
                            }
                        }

                        if (memberOwedAmount > 0.0 && (Math.abs(tx.amount - memberOwedAmount) <= 50.0 || tx.amount <= memberOwedAmount * 1.1)) {
                            val proposalId = "settle_${space.id}_${tx.id}_$mId"
                            if (dismissedIds.contains(proposalId)) continue

                            val numericTxId = tx.id.toLongOrNull() ?: 0L
                            return AutonomousActionProposal.SpaceDebtSettlementProposal(
                                id = proposalId,
                                spaceId = space.id,
                                spaceName = space.name,
                                memberId = mId,
                                memberName = mName,
                                amount = tx.amount,
                                txId = numericTxId
                            )
                        }
                    }
                }
            }
        }
        return null
    }

    /**
     * Checks if a recent salary credit needs allocation blueprinting.
     */
    private fun evaluatePaydayBlueprint(
        transactions: List<Transaction>,
        budgets: List<Budget>,
        dismissedIds: Set<String> = emptySet()
    ): AutonomousActionProposal.PaydayBlueprintProposal? {
        val fortyEightHoursAgo = System.currentTimeMillis() - (48 * 60 * 60 * 1000L)
        val salaryTx = transactions.firstOrNull { tx ->
            val txTime = tx.timestamp.takeIf { t -> t > 0L } ?: (tx.date.toEpochMilliOrNull() ?: 0L)
            (tx.type == "income" || tx.type == "credit") &&
            txTime >= fortyEightHoursAgo &&
            (tx.category.equals("Salary", ignoreCase = true) || tx.amount >= 20000.0 || tx.sender.contains("SALARY", ignoreCase = true))
        } ?: return null

        val proposalId = "payday_${salaryTx.id}"
        if (dismissedIds.contains(proposalId)) return null

        val salaryAmount = salaryTx.amount
        val fixedBills = (salaryAmount * 0.25).coerceAtLeast(3000.0)
        val goals = (salaryAmount * 0.20).coerceAtLeast(2000.0)
        val living = (salaryAmount - fixedBills - goals).coerceAtLeast(salaryAmount * 0.40)

        return AutonomousActionProposal.PaydayBlueprintProposal(
            id = proposalId,
            salaryAmount = salaryAmount,
            livingAmount = living,
            billsAmount = fixedBills,
            goalsAmount = goals
        )
    }

    private fun String.toEpochMilliOrNull(): Long? {
        return try {
            java.time.Instant.parse(this).toEpochMilli()
        } catch (_: Exception) {
            try {
                java.time.LocalDate.parse(this).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
            } catch (_: Exception) {
                null
            }
        }
    }
}
