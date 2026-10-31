package com.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.data.local.dao.BudgetDao
import com.app.data.local.dao.ChatMessageDao
import com.app.data.local.dao.GoalDao
import com.app.data.local.dao.MerchantCategoryMappingDao
import com.app.data.local.dao.TransactionDao
import com.app.data.local.entity.BudgetEntity
import com.app.data.local.entity.ChatMessageEntity
import com.app.data.local.entity.GoalEntity
import com.app.data.local.entity.MerchantCategoryMappingEntity
import com.app.data.remote.api.AIChatApiService
import com.app.data.remote.api.GoalApiService
import com.app.data.remote.dto.AIChatRequest
import com.app.data.remote.dto.ChatMessageDto
import com.app.data.remote.dto.GoalDto
import com.app.data.remote.dto.toEntity
import com.app.domain.model.Budget
import com.app.domain.model.CashflowProjection
import com.app.domain.model.CopilotActionIntent
import com.app.domain.repository.TransactionRepository
import com.app.domain.usecase.PredictiveCashflowUseCase
import com.app.domain.util.CopilotMessageParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

data class AIChatMessage(
    val id: String = UUID.randomUUID().toString(),
    val role: String,    // "user" or "assistant"
    val content: String,
    val isLoading: Boolean = false,
    val cashflowProjection: CashflowProjection? = null,
    val actionIntent: CopilotActionIntent? = null,
    val isActionExecuted: Boolean = false
)

data class AIChatUiState(
    val messages: List<AIChatMessage> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val healthScore: Int? = null,
    val healthLabel: String? = null
)

@HiltViewModel
class AIChatViewModel @Inject constructor(
    private val aiChatApi: AIChatApiService,
    private val chatMessageDao: ChatMessageDao,
    private val transactionRepository: TransactionRepository,
    private val predictiveCashflowUseCase: PredictiveCashflowUseCase,
    private val budgetDao: BudgetDao,
    private val goalDao: GoalDao,
    private val transactionDao: TransactionDao,
    private val merchantCategoryMappingDao: MerchantCategoryMappingDao,
    private val goalApi: GoalApiService
) : ViewModel() {

    private val _uiState = MutableStateFlow(AIChatUiState())
    val uiState: StateFlow<AIChatUiState> = _uiState.asStateFlow()

    init {
        loadLocalCache()
        loadHistory()
        loadHealthScore()
    }

    /**
     * Load cached messages from Room DB first (instant, offline-capable)
     */
    private fun loadLocalCache() {
        viewModelScope.launch {
            try {
                val cached = chatMessageDao.getAll()
                if (cached.isNotEmpty()) {
                    _uiState.update { state ->
                        state.copy(messages = cached.map {
                            val parsed = CopilotMessageParser.parse(it.content)
                            AIChatMessage(
                                role = it.role,
                                content = parsed.cleanText,
                                actionIntent = parsed.intent
                            )
                        })
                    }
                }
            } catch (_: Exception) { /* Room not ready */ }
        }
    }

    /**
     * Load history from server and update local cache
     */
    private fun loadHistory() {
        viewModelScope.launch {
            try {
                val response = aiChatApi.getHistory()
                if (response.isSuccessful && response.body()?.data != null) {
                    val history = response.body()!!.data!!.messages
                    val messages = history.map {
                        val parsed = CopilotMessageParser.parse(it.content)
                        AIChatMessage(
                            role = it.role,
                            content = parsed.cleanText,
                            actionIntent = parsed.intent
                        )
                    }
                    _uiState.update { state -> state.copy(messages = messages) }

                    // Update local cache
                    chatMessageDao.deleteAll()
                    chatMessageDao.insertAll(history.map {
                        ChatMessageEntity(role = it.role, content = it.content)
                    })
                }
            } catch (_: Exception) { /* Offline — using local cache */ }
        }
    }

    private fun loadHealthScore() {
        viewModelScope.launch {
            try {
                val response = aiChatApi.getHealthScore()
                if (response.isSuccessful && response.body()?.data != null) {
                    val data = response.body()!!.data!!
                    _uiState.update { it.copy(healthScore = data.score, healthLabel = data.label) }
                }
            } catch (_: Exception) { /* Offline */ }
        }
    }

    fun sendMessage(userMessage: String) {
        if (userMessage.isBlank()) return

        val userMsg = AIChatMessage(role = "user", content = userMessage.trim())
        val lowerText = userMessage.lowercase(Locale.US)

        // ── AGENTIC EXECUTION ENGINE: Execute user imperative commands locally & immediately ──

        // Agentic Intent 1: Direct Budget Creation / Update ("set food budget to 5000", "create budget 4000 for groceries")
        val budgetRegex = Regex("(?:set|create|update|adjust)\\s+(?:budget\\s+for\\s+|budget\\s+)?([a-zA-Z\\s&]+?)\\s+(?:budget\\s+)?(?:to|of|at)\\s*(?:₹|rs\\.?|inr)?\\s*([0-9]+(?:,[0-9]+)*(?:\\.[0-9]+)?)", RegexOption.IGNORE_CASE)
        val budgetMatch = budgetRegex.find(userMessage)
        if (budgetMatch != null) {
            val rawCategory = budgetMatch.groupValues[1].trim()
            val rawLimit = budgetMatch.groupValues[2].replace(",", "").toDoubleOrNull()
            if (rawLimit != null && rawLimit > 0) {
                val matchedCategory = when {
                    rawCategory.contains("food", ignoreCase = true) || rawCategory.contains("dining", ignoreCase = true) -> "Food & Dining"
                    rawCategory.contains("groc", ignoreCase = true) -> "Groceries"
                    rawCategory.contains("shop", ignoreCase = true) -> "Shopping"
                    rawCategory.contains("trans", ignoreCase = true) || rawCategory.contains("travel", ignoreCase = true) -> "Transport"
                    rawCategory.contains("bill", ignoreCase = true) || rawCategory.contains("util", ignoreCase = true) -> "Utilities & Bills"
                    rawCategory.contains("sub", ignoreCase = true) -> "Subscriptions"
                    rawCategory.contains("rent", ignoreCase = true) -> "Rent"
                    rawCategory.contains("health", ignoreCase = true) || rawCategory.contains("med", ignoreCase = true) -> "Health & Wellness"
                    rawCategory.contains("invest", ignoreCase = true) -> "Investment"
                    else -> rawCategory.replaceFirstChar { it.uppercase() }
                }

                _uiState.update { state -> state.copy(messages = state.messages + userMsg) }
                viewModelScope.launch {
                    val intent = CopilotActionIntent.AdjustBudget(
                        title = "Set $matchedCategory Budget",
                        reason = "Direct user instruction via Copilot terminal",
                        category = matchedCategory,
                        currentLimit = null,
                        proposedLimit = rawLimit
                    )
                    executeActionIntent(UUID.randomUUID().toString(), intent)
                    val confirmMsg = AIChatMessage(
                        role = "assistant",
                        content = "⚡ **Executed**: Monthly budget for **$matchedCategory** set to **₹${"%,.0f".format(rawLimit)}**.\nYour limits and progress bars have been updated across your dashboard.",
                        actionIntent = intent,
                        isActionExecuted = true
                    )
                    _uiState.update { state -> state.copy(messages = state.messages + confirmMsg) }
                }
                return
            }
        }

        // Agentic Intent 2: Direct Goal Creation ("create goal vacation 25000", "save 15000 for laptop")
        val goalRegex = Regex("(?:create\\s+goal|new\\s+goal|save)\\s+(?:for\\s+)?([a-zA-Z\\s]+?)\\s+(?:of|for|with\\s+target)?\\s*(?:₹|rs\\.?|inr)?\\s*([0-9]+(?:,[0-9]+)*(?:\\.[0-9]+)?)", RegexOption.IGNORE_CASE)
        val goalMatch = goalRegex.find(userMessage)
        if (goalMatch != null && !lowerText.contains("can i") && !lowerText.contains("budget")) {
            val goalTitle = goalMatch.groupValues[1].trim().replaceFirstChar { it.uppercase() }
            val goalTarget = goalMatch.groupValues[2].replace(",", "").toDoubleOrNull()
            if (goalTarget != null && goalTarget > 0 && goalTitle.length >= 2) {
                _uiState.update { state -> state.copy(messages = state.messages + userMsg) }
                viewModelScope.launch {
                    val intent = CopilotActionIntent.CreateGoal(
                        title = "Create Goal: $goalTitle",
                        reason = "Direct user instruction via Copilot terminal",
                        goalTitle = goalTitle,
                        targetAmount = goalTarget,
                        deadline = null
                    )
                    executeActionIntent(UUID.randomUUID().toString(), intent)
                    val confirmMsg = AIChatMessage(
                        role = "assistant",
                        content = "🎯 **Executed**: Created savings goal **'$goalTitle'** with target **₹${"%,.0f".format(goalTarget)}**.\nTrack its progress anytime in your Goals tab.",
                        actionIntent = intent,
                        isActionExecuted = true
                    )
                    _uiState.update { state -> state.copy(messages = state.messages + confirmMsg) }
                }
                return
            }
        }

        // Agentic Intent 3: Auto-Calibrate Budgets ("auto set budgets", "calibrate limits", "/budget auto-calibrate")
        if (lowerText.contains("auto-calibrate") || lowerText.contains("auto set budget") || lowerText.contains("calibrate budget") || lowerText.contains("calibrate limits")) {
            _uiState.update { state -> state.copy(messages = state.messages + userMsg) }
            viewModelScope.launch {
                try {
                    val currentMonth = YearMonth.now().format(DateTimeFormatter.ofPattern("yyyy-MM"))
                    val totals = transactionDao.observeCategoryTotalsByMonth(currentMonth).first()
                    var count = 0
                    if (totals.isNotEmpty()) {
                        for (catTotal in totals) {
                            if (catTotal.total > 0.0 && catTotal.category != "Others" && catTotal.category != "Transfer") {
                                val raw = catTotal.total * 1.15 // 15% safety buffer
                                val smartLimit = (Math.ceil(raw / 500.0) * 500.0).coerceAtLeast(1000.0)
                                transactionRepository.saveBudget(
                                    Budget(category = catTotal.category, limit = smartLimit, period = "monthly")
                                )
                                count++
                            }
                        }
                    }
                    val confirmMsg = AIChatMessage(
                        role = "assistant",
                        content = "⚡ **Executed**: Auto-calibrated smart budget limits for **$count active categories** based on your real spending velocity with a 15% discretionary buffer."
                    )
                    _uiState.update { state -> state.copy(messages = state.messages + confirmMsg) }
                } catch (e: Exception) {
                    _uiState.update { state -> state.copy(error = "Auto-calibration failed: ${e.message}") }
                }
            }
            return
        }

        // 1. Intercept Cashflow Prediction locally
        if (lowerText.contains("predict") || lowerText.contains("cashflow") || lowerText.contains("forecast")) {
            _uiState.update { state ->
                state.copy(messages = state.messages + userMsg + AIChatMessage(role = "assistant", content = "", isLoading = true))
            }
            viewModelScope.launch {
                try {
                    val txs = transactionRepository.observeAllTransactions().first()
                    val projection = predictiveCashflowUseCase.invoke(txs)
                    val replyMsg = "Here is your 30-day cashflow projection based on your daily burn rate and recurring bills:"
                    _uiState.update { state ->
                        state.copy(
                            messages = state.messages.dropLast(1) + AIChatMessage(
                                role = "assistant",
                                content = replyMsg,
                                cashflowProjection = projection
                            ),
                            isLoading = false
                        )
                    }
                } catch (e: Exception) {
                    _uiState.update { state -> state.copy(isLoading = false, error = "Failed to generate prediction") }
                }
            }
            return
        }

        // 2. Intercept "Can I Afford This?" Affordability Simulation locally
        var localAffordabilityIntent: CopilotActionIntent.AffordabilityVerdict? = null
        if (lowerText.contains("afford") || lowerText.contains("can i buy") || lowerText.contains("should i buy")) {
            val amountRegex = Regex("(?:₹|rs\\.?|inr)?\\s*([0-9]+(?:,[0-9]+)*(?:\\.[0-9]+)?)", RegexOption.IGNORE_CASE)
            val match = amountRegex.find(userMessage)
            val parsedAmount = match?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull()

            if (parsedAmount != null && parsedAmount > 0) {
                try {
                    viewModelScope.launch {
                        val txs = transactionRepository.observeAllTransactions().first()
                        val projection = predictiveCashflowUseCase.invoke(txs)
                        val currentBalance = projection.currentBalance
                        val dailyBurnRate = projection.averageDailyBurnRate.coerceAtLeast(1.0)
                        val upcomingBills = projection.identifiedRecurringExpenses
                        val today = LocalDate.now()
                        val daysLeftInMonth = (today.lengthOfMonth() - today.dayOfMonth).coerceAtLeast(1)
                        val remainingAfterSpend = currentBalance - upcomingBills - parsedAmount
                        val safeDailySpendAfter = (remainingAfterSpend / daysLeftInMonth).coerceAtLeast(0.0)

                        val verdict = when {
                            remainingAfterSpend <= 0.0 -> "UNSAFE"
                            safeDailySpendAfter < dailyBurnRate * 0.75 -> "CAUTION"
                            else -> "SAFE"
                        }

                        val reason = when (verdict) {
                            "SAFE" -> "Leaves ₹${"%,.0f".format(safeDailySpendAfter)}/day for daily burn (avg is ₹${"%,.0f".format(dailyBurnRate)}/day). All recurring commitments remain protected."
                            "CAUTION" -> "Reduces daily safe-to-spend to ₹${"%,.0f".format(safeDailySpendAfter)}/day (below your ₹${"%,.0f".format(dailyBurnRate)}/day pace). Discretionary cut required."
                            else -> "Deficit risk of ₹${"%,.0f".format(-remainingAfterSpend)}. Upcoming fixed commitments (₹${"%,.0f".format(upcomingBills)}) will be strained."
                        }

                        localAffordabilityIntent = CopilotActionIntent.AffordabilityVerdict(
                            title = "Affordability Simulation",
                            reason = reason,
                            itemName = "Purchase",
                            itemAmount = parsedAmount,
                            verdict = verdict,
                            safeDailySpendAfter = safeDailySpendAfter,
                            daysUntilPayday = daysLeftInMonth
                        )
                    }
                } catch (_: Exception) {}
            }
        }

        // 3. Regular API call
        val loadingMsg = AIChatMessage(role = "assistant", content = "", isLoading = true)

        _uiState.update { state ->
            state.copy(
                messages = state.messages + userMsg + loadingMsg,
                isLoading = true,
                error = null
            )
        }

        // Cache user message locally
        viewModelScope.launch {
            chatMessageDao.insert(ChatMessageEntity(role = "user", content = userMessage.trim()))
        }

        viewModelScope.launch {
            try {
                val chatMessages = _uiState.value.messages
                    .filter { !it.isLoading }
                    .map { ChatMessageDto(it.role, it.content) }

                val response = aiChatApi.chat(AIChatRequest(chatMessages))
                if (response.isSuccessful && response.body()?.data != null) {
                    val rawReply = response.body()!!.data!!.reply
                    val parsed = CopilotMessageParser.parse(rawReply)

                    // Pick intent from DTO or parsed embedded JSON or local affordability simulation
                    val finalIntent = response.body()!!.data!!.intents?.firstOrNull()?.let { CopilotMessageParser.fromDto(it) }
                        ?: parsed.intent
                        ?: localAffordabilityIntent

                    _uiState.update { state ->
                        state.copy(
                            messages = state.messages.dropLast(1) + AIChatMessage(
                                role = "assistant",
                                content = parsed.cleanText,
                                actionIntent = finalIntent
                            ),
                            isLoading = false
                        )
                    }
                    // Cache assistant reply locally
                    chatMessageDao.insert(ChatMessageEntity(role = "assistant", content = rawReply))
                } else {
                    _uiState.update { state ->
                        state.copy(
                            messages = state.messages.dropLast(1),
                            isLoading = false,
                            error = "Failed to get response"
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { state ->
                    state.copy(
                        messages = state.messages.dropLast(1),
                        isLoading = false,
                        error = e.message ?: "Network error"
                    )
                }
            }
        }
    }

    /**
     * Execute a structured Action Intent on user's behalf with 100% offline-first Room DB + API sync.
     */
    fun executeActionIntent(messageId: String, intent: CopilotActionIntent) {
        viewModelScope.launch {
            try {
                when (intent) {
                    is CopilotActionIntent.AdjustBudget -> {
                        val existing = budgetDao.getByCategory(intent.category)
                        val budgetEntity = existing?.copy(
                            limitAmount = intent.proposedLimit,
                            updatedAt = Instant.now().toString(),
                            syncStatus = if (existing.serverId != null) "pending_update" else existing.syncStatus
                        ) ?: BudgetEntity(
                            category = intent.category,
                            limitAmount = intent.proposedLimit,
                            period = "monthly",
                            syncStatus = "pending_create"
                        )
                        budgetDao.insertBudget(budgetEntity)
                        transactionRepository.saveBudget(
                            Budget(category = intent.category, limit = intent.proposedLimit, period = "monthly")
                        )
                    }
                    is CopilotActionIntent.RebalanceBudget -> {
                        val fromExisting = budgetDao.getByCategory(intent.fromCategory)
                        if (fromExisting != null) {
                            val newFromLimit = (fromExisting.limitAmount - intent.amount).coerceAtLeast(0.0)
                            budgetDao.insertBudget(fromExisting.copy(limitAmount = newFromLimit, syncStatus = "pending_update"))
                            transactionRepository.saveBudget(
                                Budget(category = intent.fromCategory, limit = newFromLimit, period = fromExisting.period)
                            )
                        }
                        val toExisting = budgetDao.getByCategory(intent.toCategory)
                        if (toExisting != null) {
                            val newToLimit = toExisting.limitAmount + intent.amount
                            budgetDao.insertBudget(toExisting.copy(limitAmount = newToLimit, syncStatus = "pending_update"))
                            transactionRepository.saveBudget(
                                Budget(category = intent.toCategory, limit = newToLimit, period = toExisting.period)
                            )
                        }
                    }
                    is CopilotActionIntent.CreateGoal -> {
                        val entity = GoalEntity(
                            title = intent.goalTitle,
                            targetAmount = intent.targetAmount,
                            deadline = intent.deadline,
                            syncStatus = "pending_create"
                        )
                        val localId = goalDao.insert(entity)
                        try {
                            val response = goalApi.create(
                                GoalDto(
                                    title = intent.goalTitle,
                                    targetAmount = intent.targetAmount,
                                    deadline = intent.deadline,
                                    category = "savings"
                                )
                            )
                            if (response.isSuccessful && response.body()?.data?.goal != null) {
                                val serverGoal = response.body()!!.data!!.goal
                                goalDao.deleteById(localId)
                                goalDao.insert(serverGoal.toEntity())
                            }
                        } catch (_: Exception) {}
                    }
                    is CopilotActionIntent.BulkCategorize -> {
                        transactionDao.updateCategoryByNormalizedMerchant(
                            normalizedMerchant = intent.merchantKeyword,
                            category = intent.targetCategory,
                            updatedAt = Instant.now().toString()
                        )
                        merchantCategoryMappingDao.upsert(
                            MerchantCategoryMappingEntity(
                                merchantKey = intent.merchantKeyword,
                                normalizedMerchant = intent.merchantKeyword,
                                category = intent.targetCategory,
                                confidence = 1.0,
                                frequency = intent.count,
                                lastUsedTimestamp = System.currentTimeMillis(),
                                source = "CopilotAgent"
                            )
                        )
                    }
                    is CopilotActionIntent.AffordabilityVerdict -> {
                        // Simulation verdict acknowledged
                    }
                }

                _uiState.update { state ->
                    state.copy(
                        messages = state.messages.map { msg ->
                            if (msg.id == messageId) msg.copy(isActionExecuted = true) else msg
                        }
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Action failed: ${e.message}") }
            }
        }
    }

    /**
     * Dismiss an action card without executing.
     */
    fun dismissActionIntent(messageId: String) {
        _uiState.update { state ->
            state.copy(
                messages = state.messages.map { msg ->
                    if (msg.id == messageId) msg.copy(actionIntent = null) else msg
                }
            )
        }
    }

    fun sendSuggestion(suggestion: String) {
        val cleanText = suggestion.replace(Regex("^[\\p{So}\\p{Cn}]+\\s*"), "").trim()
        if (cleanText.isNotBlank()) {
            sendMessage(cleanText)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            try {
                aiChatApi.clearHistory()
                chatMessageDao.deleteAll()
                _uiState.update { it.copy(messages = emptyList()) }
            } catch (_: Exception) {
                _uiState.update { it.copy(error = "Failed to clear history") }
            }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
