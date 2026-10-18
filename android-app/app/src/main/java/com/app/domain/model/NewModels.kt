package com.app.domain.model

data class Goal(
    val id: String = "",
    val title: String = "",
    val targetAmount: Double = 0.0,
    val currentAmount: Double = 0.0,
    val deadline: String? = null,
    val category: String = "savings",
    val icon: String = "🎯",
    val color: String = "#7C6AF5",
    val isCompleted: Boolean = false,
    val completedAt: String? = null
) {
    val progress: Int
        get() = if (targetAmount > 0) {
            ((currentAmount / targetAmount) * 100).toInt().coerceAtMost(100)
        } else 100

    val remainingAmount: Double get() = (targetAmount - currentAmount).coerceAtLeast(0.0)
}


data class Alert(
    val id: String = "",
    val type: String = "",
    val message: String = "",
    val severity: String = "info",
    val category: String? = null,
    val isRead: Boolean = false,
    val createdAt: String? = null
)

data class RecurringSubscription(
    val merchantName: String,
    val category: String,
    val averageAmount: Double,
    val cadence: String = "monthly", // "weekly", "monthly", "yearly"
    val occurrences: Int = 1,
    val lastDate: String = "",
    val nextExpectedDate: String = "",
    val daysUntilDue: Int = 0,
    val transactionIds: List<String> = emptyList()
)
