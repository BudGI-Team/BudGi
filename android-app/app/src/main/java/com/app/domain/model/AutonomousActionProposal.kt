package com.app.domain.model

/**
 * Domain model representing real-time autonomous recommendations formulated by the BudGi Copilot Engine.
 * Enables 1-tap in-app and lockscreen native execution on the user's behalf.
 */
sealed class AutonomousActionProposal {
    abstract val id: String
    abstract val title: String
    abstract val subtitle: String
    abstract val description: String
    abstract val actionButtonText: String
    abstract val categoryOrContext: String

    data class BudgetRebalanceProposal(
        override val id: String,
        val fromCategory: String,
        val toCategory: String,
        val amount: Double,
        val fromSurplus: Double,
        val toDeficit: Double,
        override val title: String = "Autonomous Budget Rebalance",
        override val subtitle: String = "Deficit Mitigation",
        override val description: String = "Rebalance ₹${"%,.0f".format(amount)} from $fromCategory surplus to $toCategory to prevent month-end breach.",
        override val actionButtonText: String = "Confirm & Rebalance",
        override val categoryOrContext: String = toCategory
    ) : AutonomousActionProposal()

    data class SpaceDebtSettlementProposal(
        override val id: String,
        val spaceId: String,
        val spaceName: String,
        val memberId: String,
        val memberName: String,
        val amount: Double,
        val txId: Long,
        override val title: String = "Split Debt Reconciliation",
        override val subtitle: String = "Incoming UPI Match",
        override val description: String = "Received ₹${"%,.0f".format(amount)} from $memberName. Auto-settle their pending share in $spaceName?",
        override val actionButtonText: String = "Settle Split",
        override val categoryOrContext: String = spaceName
    ) : AutonomousActionProposal()

    data class PaydayBlueprintProposal(
        override val id: String,
        val salaryAmount: Double,
        val livingAmount: Double,
        val billsAmount: Double,
        val goalsAmount: Double,
        override val title: String = "Payday Allocation Blueprint",
        override val subtitle: String = "Income Distribution",
        override val description: String = "Salary of ₹${"%,.0f".format(salaryAmount)} detected. Proposing automated allocation across Living (₹${"%,.0f".format(livingAmount)}), Bills (₹${"%,.0f".format(billsAmount)}) & Goals (₹${"%,.0f".format(goalsAmount)}).",
        override val actionButtonText: String = "Apply Blueprint",
        override val categoryOrContext: String = "Salary"
    ) : AutonomousActionProposal()
}
