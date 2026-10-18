package com.app.domain.model

data class Budget(
    val id: String = "",
    val category: String = "",
    val limit: Double = 0.0,
    val period: String = "monthly",
    val alertAt: Double = 0.9,
    val color: String = "#0284c7",
    val spentAmount: Double = 0.0,
    // Legacy
    val limitAmount: Double = 0.0,
    val monthYear: String = ""
) {
    val effectiveLimit: Double get() = if (limit > 0) limit else limitAmount
    val remainingAmount: Double get() = effectiveLimit - spentAmount
    val progress: Float get() = if (effectiveLimit > 0) (spentAmount / effectiveLimit).toFloat() else 0f
    val isOverBudget: Boolean get() = spentAmount > effectiveLimit
    val isWarning: Boolean get() = progress >= alertAt.toFloat()
}
