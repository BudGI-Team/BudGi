package com.app.domain.util

import com.app.data.remote.dto.CopilotIntentDto
import com.app.domain.model.CopilotActionIntent
import org.json.JSONObject

object CopilotMessageParser {

    data class ParsedResult(
        val cleanText: String,
        val chartJson: JSONObject?,
        val intent: CopilotActionIntent?
    )

    fun parse(content: String): ParsedResult {
        var text = content
        var chartJson: JSONObject? = null
        var intent: CopilotActionIntent? = null

        // 1. Extract action_intent JSON
        val intentRegex = Regex("\\{[\\s\\S]*?\"type\"\\s*:\\s*\"action_intent\"[\\s\\S]*?\\}")
        val intentMatch = intentRegex.find(text)
        if (intentMatch != null) {
            val jsonStr = intentMatch.value
            try {
                val obj = JSONObject(jsonStr)
                val intentType = obj.optString("intentType", "")
                val title = obj.optString("title", "Copilot Action")
                val reason = obj.optString("reason", "")

                intent = when (intentType) {
                    "ADJUST_BUDGET" -> {
                        CopilotActionIntent.AdjustBudget(
                            title = title,
                            reason = reason,
                            category = obj.optString("category", "General"),
                            currentLimit = if (obj.has("currentLimit")) obj.optDouble("currentLimit") else null,
                            proposedLimit = obj.optDouble("proposedLimit", 0.0)
                        )
                    }
                    "REBALANCE_BUDGET" -> {
                        CopilotActionIntent.RebalanceBudget(
                            title = title,
                            reason = reason,
                            fromCategory = obj.optString("fromCategory", ""),
                            toCategory = obj.optString("toCategory", ""),
                            amount = obj.optDouble("amount", 0.0)
                        )
                    }
                    "CREATE_GOAL" -> {
                        CopilotActionIntent.CreateGoal(
                            title = title,
                            reason = reason,
                            goalTitle = obj.optString("goalTitle", obj.optString("title", "Goal")),
                            targetAmount = obj.optDouble("targetAmount", 0.0),
                            deadline = if (obj.has("deadline")) obj.optString("deadline") else null
                        )
                    }
                    "AFFORDABILITY_VERDICT" -> {
                        CopilotActionIntent.AffordabilityVerdict(
                            title = title,
                            reason = reason,
                            itemName = obj.optString("itemName", "Item"),
                            itemAmount = obj.optDouble("itemAmount", 0.0),
                            verdict = obj.optString("verdict", "CAUTION"),
                            safeDailySpendAfter = obj.optDouble("safeDailySpendAfter", 0.0),
                            daysUntilPayday = obj.optInt("daysUntilPayday", 15)
                        )
                    }
                    "BULK_CATEGORIZE" -> {
                        CopilotActionIntent.BulkCategorize(
                            title = title,
                            reason = reason,
                            merchantKeyword = obj.optString("merchantKeyword", ""),
                            targetCategory = obj.optString("targetCategory", "Other"),
                            count = obj.optInt("count", 1)
                        )
                    }
                    else -> null
                }
                text = text.replace(jsonStr, "").trim()
            } catch (_: Exception) {}
        }

        // 2. Extract chart JSON
        val chartRegex = Regex("\\{[\\s\\S]*?\"type\"\\s*:\\s*\"chart\"[\\s\\S]*?\\}")
        val chartMatch = chartRegex.find(text)
        if (chartMatch != null) {
            val jsonStr = chartMatch.value
            try {
                chartJson = JSONObject(jsonStr)
                text = text.replace(jsonStr, "").trim()
            } catch (_: Exception) {}
        }

        return ParsedResult(cleanText = text, chartJson = chartJson, intent = intent)
    }

    fun fromDto(dto: CopilotIntentDto): CopilotActionIntent? {
        val t = dto.intentType ?: return null
        val r = dto.reason ?: ""
        val tit = dto.title ?: "Copilot Action"
        return when (t) {
            "ADJUST_BUDGET" -> CopilotActionIntent.AdjustBudget(
                title = tit,
                reason = r,
                category = dto.category ?: "General",
                currentLimit = dto.currentLimit,
                proposedLimit = dto.proposedLimit ?: 0.0
            )
            "REBALANCE_BUDGET" -> CopilotActionIntent.RebalanceBudget(
                title = tit,
                reason = r,
                fromCategory = dto.fromCategory ?: "",
                toCategory = dto.toCategory ?: "",
                amount = dto.amount ?: 0.0
            )
            "CREATE_GOAL" -> CopilotActionIntent.CreateGoal(
                title = tit,
                reason = r,
                goalTitle = dto.goalTitle ?: tit,
                targetAmount = dto.targetAmount ?: 0.0,
                deadline = dto.deadline
            )
            "AFFORDABILITY_VERDICT" -> CopilotActionIntent.AffordabilityVerdict(
                title = tit,
                reason = r,
                itemName = dto.itemName ?: "Item",
                itemAmount = dto.itemAmount ?: 0.0,
                verdict = dto.verdict ?: "CAUTION",
                safeDailySpendAfter = dto.safeDailySpendAfter ?: 0.0,
                daysUntilPayday = dto.daysUntilPayday ?: 15
            )
            "BULK_CATEGORIZE" -> CopilotActionIntent.BulkCategorize(
                title = tit,
                reason = r,
                merchantKeyword = dto.merchantKeyword ?: "",
                targetCategory = dto.targetCategory ?: "Other",
                count = dto.count ?: 1
            )
            else -> null
        }
    }
}
