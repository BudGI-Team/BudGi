package com.app.data.remote.dto

import com.google.gson.annotations.SerializedName

data class BudgetDto(
    @SerializedName("_id") val id: String? = null,
    @SerializedName("userId") val userId: String? = null,
    @SerializedName("category") val category: String,
    @SerializedName("limit") val limit: Double,
    @SerializedName("period") val period: String = "monthly",
    @SerializedName("alertAt") val alertAt: Double = 0.9,
    @SerializedName("color") val color: String = "#0284c7",
    @SerializedName("clientId") val clientId: String? = null,
    @SerializedName("isDeleted") val isDeleted: Boolean = false,
    @SerializedName("createdAt") val createdAt: String? = null,
    @SerializedName("updatedAt") val updatedAt: String? = null
)

data class BudgetListData(
    @SerializedName("budgets") val budgets: List<BudgetDto>
)

data class BudgetSingleData(
    @SerializedName("budget") val budget: BudgetDto
)

// ── Goal ─────────────────────────────────────────────────────────────

data class GoalDto(
    @SerializedName("_id") val id: String? = null,
    @SerializedName("userId") val userId: String? = null,
    @SerializedName("title") val title: String,
    @SerializedName("targetAmount") val targetAmount: Double,
    @SerializedName("currentAmount") val currentAmount: Double = 0.0,
    @SerializedName("deadline") val deadline: String? = null,
    @SerializedName("category") val category: String = "savings",
    @SerializedName("icon") val icon: String = "🎯",
    @SerializedName("color") val color: String = "#7C6AF5",
    @SerializedName("isCompleted") val isCompleted: Boolean = false,
    @SerializedName("completedAt") val completedAt: String? = null,
    @SerializedName("progress") val progress: Int = 0,
    @SerializedName("clientId") val clientId: String? = null,
    @SerializedName("isDeleted") val isDeleted: Boolean = false,
    @SerializedName("createdAt") val createdAt: String? = null,
    @SerializedName("updatedAt") val updatedAt: String? = null
)

data class GoalListData(
    @SerializedName("goals") val goals: List<GoalDto>
)

data class GoalSingleData(
    @SerializedName("goal") val goal: GoalDto
)

fun GoalDto.toEntity(): com.app.data.local.entity.GoalEntity = com.app.data.local.entity.GoalEntity(
    serverId = id,
    title = title,
    targetAmount = targetAmount,
    currentAmount = currentAmount,
    deadline = deadline,
    category = category,
    icon = icon,
    color = color,
    isCompleted = isCompleted,
    completedAt = completedAt,
    syncStatus = "synced",
    updatedAt = updatedAt,
    createdAt = createdAt
)


// ── Alert ────────────────────────────────────────────────────────────

data class AlertDto(
    @SerializedName("_id") val id: String? = null,
    @SerializedName("type") val type: String,
    @SerializedName("message") val message: String,
    @SerializedName("severity") val severity: String = "info",
    @SerializedName("category") val category: String? = null,
    @SerializedName("isRead") val isRead: Boolean = false,
    @SerializedName("createdAt") val createdAt: String? = null
)
