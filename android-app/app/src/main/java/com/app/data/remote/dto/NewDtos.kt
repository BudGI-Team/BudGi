package com.app.data.remote.dto

import com.google.gson.annotations.SerializedName

// ── AI Chat ─────────────────────────────────────────────────────────

data class AIChatRequest(
    @SerializedName("messages") val messages: List<ChatMessageDto>
)

data class ChatMessageDto(
    @SerializedName("role") val role: String,        // "user" or "assistant"
    @SerializedName("content") val content: String
)

data class AIChatResponseData(
    @SerializedName("reply") val reply: String,
    @SerializedName("messageId") val messageId: String? = null,
    @SerializedName("executedTools") val executedTools: List<String>? = null,
    @SerializedName("intents") val intents: List<CopilotIntentDto>? = null
)

data class CopilotIntentDto(
    @SerializedName("type") val type: String? = null,
    @SerializedName("intentType") val intentType: String? = null,
    @SerializedName("title") val title: String? = null,
    @SerializedName("reason") val reason: String? = null,
    @SerializedName("category") val category: String? = null,
    @SerializedName("currentLimit") val currentLimit: Double? = null,
    @SerializedName("proposedLimit") val proposedLimit: Double? = null,
    @SerializedName("fromCategory") val fromCategory: String? = null,
    @SerializedName("toCategory") val toCategory: String? = null,
    @SerializedName("amount") val amount: Double? = null,
    @SerializedName("goalTitle") val goalTitle: String? = null,
    @SerializedName("targetAmount") val targetAmount: Double? = null,
    @SerializedName("deadline") val deadline: String? = null,
    @SerializedName("itemName") val itemName: String? = null,
    @SerializedName("itemAmount") val itemAmount: Double? = null,
    @SerializedName("verdict") val verdict: String? = null,
    @SerializedName("safeDailySpendAfter") val safeDailySpendAfter: Double? = null,
    @SerializedName("daysUntilPayday") val daysUntilPayday: Int? = null,
    @SerializedName("merchantKeyword") val merchantKeyword: String? = null,
    @SerializedName("targetCategory") val targetCategory: String? = null,
    @SerializedName("count") val count: Int? = null
)

data class AIChatHistoryData(
    @SerializedName("messages") val messages: List<ChatHistoryItem> = emptyList()
)

data class ChatHistoryItem(
    @SerializedName("_id") val id: String? = null,
    @SerializedName("role") val role: String,
    @SerializedName("content") val content: String,
    @SerializedName("createdAt") val createdAt: String? = null
)

// ── AI Insights ─────────────────────────────────────────────────────

data class AIInsightsData(
    @SerializedName("insights") val insights: List<InsightDto> = emptyList()
)

data class InsightDto(
    @SerializedName("title") val title: String,
    @SerializedName("description") val description: String,
    @SerializedName("type") val type: String = "info",
    @SerializedName("icon") val icon: String? = null,
    @SerializedName("actionLabel") val actionLabel: String? = null
)

// ── Health Score ────────────────────────────────────────────────────

data class HealthScoreData(
    @SerializedName("score") val score: Int = 0,
    @SerializedName("label") val label: String = "Unknown",
    @SerializedName("breakdown") val breakdown: Map<String, Int> = emptyMap(),
    @SerializedName("tips") val tips: List<String> = emptyList()
)

// ── Alerts ──────────────────────────────────────────────────────────

data class AlertListData(
    @SerializedName("alerts") val alerts: List<AlertDto> = emptyList()
)

data class AlertCountData(
    @SerializedName("count") val count: Int = 0
)

data class MarkReadRequest(
    @SerializedName("alertIds") val alertIds: List<String>? = null,
    @SerializedName("all") val all: Boolean? = null
)
