package com.app.domain.model

import java.util.UUID

/**
 * Structured Actionable Intent emitted by BudGi Copilot.
 * Represents an autonomous recommendation or financial calculation that the user
 * can execute in 1-tap with deterministic Room DB + server synchronization.
 */
sealed class CopilotActionIntent {
    abstract val id: String
    abstract val title: String
    abstract val reason: String

    /**
     * Propose setting or adjusting a monthly category budget.
     */
    data class AdjustBudget(
        override val id: String = UUID.randomUUID().toString(),
        override val title: String = "Adjust Budget",
        override val reason: String,
        val category: String,
        val currentLimit: Double?,
        val proposedLimit: Double
    ) : CopilotActionIntent()

    /**
     * Propose shifting surplus funds from an under-spent category to an over-spent category.
     */
    data class RebalanceBudget(
        override val id: String = UUID.randomUUID().toString(),
        override val title: String = "Auto-Rebalance Budgets",
        override val reason: String,
        val fromCategory: String,
        val toCategory: String,
        val amount: Double
    ) : CopilotActionIntent()

    /**
     * Propose creating a new dedicated savings goal.
     */
    data class CreateGoal(
        override val id: String = UUID.randomUUID().toString(),
        override val title: String = "Set Savings Goal",
        override val reason: String,
        val goalTitle: String,
        val targetAmount: Double,
        val deadline: String? = null
    ) : CopilotActionIntent()

    /**
     * Evaluates a potential discretionary purchase against cash runway, recurring bills, and burn rate.
     */
    data class AffordabilityVerdict(
        override val id: String = UUID.randomUUID().toString(),
        override val title: String = "Purchase Simulation",
        override val reason: String,
        val itemName: String,
        val itemAmount: Double,
        val verdict: String, // "SAFE", "CAUTION", "UNSAFE"
        val safeDailySpendAfter: Double,
        val daysUntilPayday: Int
    ) : CopilotActionIntent()

    /**
     * Propose bulk updating transactions for an ambiguous merchant once user confirms category.
     */
    data class BulkCategorize(
        override val id: String = UUID.randomUUID().toString(),
        override val title: String = "Batch Categorization",
        override val reason: String,
        val merchantKeyword: String,
        val targetCategory: String,
        val count: Int
    ) : CopilotActionIntent()
}
