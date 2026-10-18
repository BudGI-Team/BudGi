package com.app.presentation.viewmodel

import java.util.UUID

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.data.local.dao.BudgetDao
import com.app.data.local.dao.GoalDao
import com.app.data.local.dao.SpaceDao
import com.app.data.remote.websocket.WebSocketEvent
import com.app.data.remote.websocket.WebSocketManager
import com.app.data.sync.SyncWorkScheduler
import com.app.domain.engine.CopilotProposalEngine
import com.app.domain.model.AnalyticsSummary
import com.app.domain.model.AutonomousActionProposal
import com.app.domain.model.Transaction
import com.app.domain.usecase.GetDashboardAnalyticsUseCase
import com.app.domain.usecase.GetTransactionsUseCase
import com.app.domain.usecase.ProcessSmsUseCase
import com.app.domain.usecase.SyncTransactionsUseCase
import com.app.utils.NotificationHelper
import com.app.domain.repository.TransactionRepository
import com.app.domain.repository.SpaceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class DateFilter(val displayName: String) {
    THIS_WEEK("This Week"),
    THIS_MONTH("This Month"),
    LAST_MONTH("Last Month"),
    LAST_3_MONTHS("Last 3 Months"),
    LAST_6_MONTHS("Last 6 Months"),
    ALL_TIME("All Time")
}

data class DashboardUiState(
    val loading: Boolean = false,
    val syncing: Boolean = false,
    val dateFilter: DateFilter = DateFilter.THIS_MONTH,
    val analyticsSummary: AnalyticsSummary = AnalyticsSummary(),
    val transactions: List<Transaction> = emptyList(),
    val error: String? = null,
    val syncMessage: String? = null,
    val activeScamAlert: com.app.data.local.entity.NotificationEntity? = null,
    val activeProposal: AutonomousActionProposal? = null,
    val hasPendingSpaces: Boolean = false,
    val user: com.app.domain.model.User? = null
)

private data class CombinedDashboardData(
    val transactions: List<Transaction>,
    val budgets: List<com.app.domain.model.Budget>,
    val scamAlert: com.app.data.local.entity.NotificationEntity?,
    val hasPendingSpaces: Boolean,
    val spaces: List<com.app.data.local.entity.SharedSpaceEntity>
)

@HiltViewModel
class DashboardViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val getDashboardAnalyticsUseCase: GetDashboardAnalyticsUseCase,
    private val getTransactionsUseCase: GetTransactionsUseCase,
    private val processSmsUseCase: ProcessSmsUseCase,
    private val syncTransactionsUseCase: SyncTransactionsUseCase,
    private val webSocketManager: WebSocketManager,
    private val notificationHelper: NotificationHelper,
    private val notificationDao: com.app.data.local.dao.NotificationDao,
    private val transactionRepository: TransactionRepository,
    private val spaceRepository: SpaceRepository,
    private val authRepository: com.app.domain.repository.AuthRepository,
    private val themeManager: com.app.data.local.ThemeManager,
    private val copilotProposalEngine: CopilotProposalEngine,
    private val budgetDao: BudgetDao,
    private val goalDao: GoalDao,
    private val spaceDao: SpaceDao
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = _uiState.asStateFlow()

    private val dismissedProposalIds = mutableSetOf<String>()

    val unreviewedTransactions: StateFlow<List<Transaction>> = transactionRepository.observeUnreviewedTransactions()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        observeTransactionsAndAnalytics()
        observeWebSocketEvents()
        observeCurrentUser()
        // Ensure WebSocket is connected (handles app restart when already logged in)
        webSocketManager.connect()
    }

    private fun observeCurrentUser() {
        viewModelScope.launch {
            authRepository.currentUser.collect { user ->
                _uiState.update { it.copy(user = user) }
            }
        }
        viewModelScope.launch {
            authRepository.getProfile()
        }
    }

    private fun observeTransactionsAndAnalytics() {
        viewModelScope.launch {
            combine(
                getTransactionsUseCase(),
                transactionRepository.observeBudgets(),
                notificationDao.getActiveSmishingAlert(),
                spaceRepository.observeSpaces()
            ) { items, budgets, scamAlert, spaces ->
                CombinedDashboardData(
                    transactions = items,
                    budgets = budgets,
                    scamAlert = scamAlert,
                    hasPendingSpaces = spaces.any { it.syncStatus != "SYNCED" },
                    spaces = spaces
                )
            }.collect { data ->
                val totalBudget = data.budgets.sumOf { it.limit }
                val analytics = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                    getDashboardAnalyticsUseCase.invoke(
                        transactions = data.transactions,
                        dateFilter = _uiState.value.dateFilter,
                        totalBudget = totalBudget
                    )
                }

                val proposal = copilotProposalEngine.evaluateProposals(
                    budgets = data.budgets,
                    transactions = data.transactions,
                    spaces = data.spaces,
                    dismissedIds = dismissedProposalIds
                ).firstOrNull()

                _uiState.update { state ->
                    state.copy(
                        transactions = data.transactions, 
                        analyticsSummary = analytics, 
                        error = null,
                        activeScamAlert = data.scamAlert,
                        activeProposal = proposal,
                        hasPendingSpaces = data.hasPendingSpaces
                    )
                }
            }
        }
    }

    fun dismissProposal(proposal: AutonomousActionProposal) {
        dismissedProposalIds.add(proposal.id)
        _uiState.update { it.copy(activeProposal = null) }
    }

    fun executeProposal(proposal: AutonomousActionProposal) {
        viewModelScope.launch {
            try {
                when (proposal) {
                    is AutonomousActionProposal.BudgetRebalanceProposal -> {
                        val fromBudget = budgetDao.getByCategory(proposal.fromCategory)
                        val toBudget = budgetDao.getByCategory(proposal.toCategory)
                        if (fromBudget != null && toBudget != null) {
                            val newFrom = (fromBudget.limitAmount - proposal.amount).coerceAtLeast(0.0)
                            val newTo = toBudget.limitAmount + proposal.amount
                            budgetDao.insertBudget(fromBudget.copy(limitAmount = newFrom, syncStatus = "pending_update"))
                            budgetDao.insertBudget(toBudget.copy(limitAmount = newTo, syncStatus = "pending_update"))
                            SyncWorkScheduler.scheduleImmediateSync(context)
                        }
                    }
                    is AutonomousActionProposal.SpaceDebtSettlementProposal -> {
                        val localSpace = spaceDao.getSpaceById(proposal.spaceId)
                        if (localSpace != null) {
                            val gson = com.google.gson.Gson()
                            val mapType = object : com.google.gson.reflect.TypeToken<List<Map<String, Any>>>() {}.type
                            val expenses: MutableList<Map<String, Any>> = try {
                                gson.fromJson<List<Map<String, Any>>>(localSpace.expensesJson, mapType).toMutableList()
                            } catch (_: Exception) {
                                mutableListOf()
                            }
                            val dateStr = java.time.LocalDate.now().toString()
                            val settleExpense = mapOf(
                                "id" to "settle_${UUID.randomUUID().toString().take(10)}",
                                "description" to "${proposal.memberName} paid (Auto-Reconciled)",
                                "amount" to proposal.amount,
                                "paidById" to proposal.memberId,
                                "paidByName" to proposal.memberName,
                                "splitWithMemberIds" to listOf("me"),
                                "category" to "Settlement",
                                "date" to dateStr,
                                "timestamp" to System.currentTimeMillis(),
                                "isSettlement" to true
                            )
                            expenses.add(0, settleExpense)
                            spaceDao.insertSpace(localSpace.copy(expensesJson = gson.toJson(expenses), syncStatus = "pending_update"))
                            SyncWorkScheduler.scheduleImmediateSync(context)
                        }
                    }
                    is AutonomousActionProposal.PaydayBlueprintProposal -> {
                        val livingAmount = proposal.livingAmount
                        val allocations = mapOf(
                            "Food & Dining" to (livingAmount * 0.40),
                            "Shopping" to (livingAmount * 0.25),
                            "Transportation" to (livingAmount * 0.20),
                            "Personal Care" to (livingAmount * 0.15)
                        )
                        for ((cat, limit) in allocations) {
                            val roundedLimit = (Math.ceil(limit / 500.0) * 500.0).coerceAtLeast(1000.0)
                            val existing = budgetDao.getByCategory(cat)
                            if (existing != null) {
                                budgetDao.insertBudget(existing.copy(limitAmount = roundedLimit, syncStatus = "pending_update"))
                            } else {
                                budgetDao.insertBudget(com.app.data.local.entity.BudgetEntity(category = cat, limitAmount = roundedLimit, period = "monthly", syncStatus = "pending_create"))
                            }
                        }
                        val goals = goalDao.observeAll().first().filter { !it.isCompleted }
                        if (goals.isNotEmpty() && proposal.goalsAmount > 0) {
                            val targetGoal = goals.first()
                            goalDao.update(targetGoal.copy(
                                currentAmount = targetGoal.currentAmount + proposal.goalsAmount,
                                syncStatus = "pending_update",
                                updatedAt = java.time.Instant.now().toString()
                            ))
                        }
                        SyncWorkScheduler.scheduleImmediateSync(context)
                    }
                }
                dismissedProposalIds.add(proposal.id)
                _uiState.update { it.copy(activeProposal = null, syncMessage = "Applied: ${proposal.title}") }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun setDateFilter(filter: DateFilter) {
        viewModelScope.launch {
            val items = _uiState.value.transactions
            val totalBudget = _uiState.value.analyticsSummary.totalBudget
            val analytics = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Default) {
                getDashboardAnalyticsUseCase.invoke(
                    transactions = items,
                    dateFilter = filter,
                    totalBudget = totalBudget
                )
            }
            _uiState.update { state ->
                state.copy(dateFilter = filter, analyticsSummary = analytics)
            }
        }
    }

    /**
     * Subscribe to WebSocket events and trigger appropriate actions.
     * This bridges the real-time push from the backend to the UI layer.
     */
    private fun observeWebSocketEvents() {
        viewModelScope.launch {
            webSocketManager.events.collect { event ->
                when (event) {
                    is WebSocketEvent.Connected -> {
                        // Connection established — do a background sync
                        syncWithServer()
                    }
                    is WebSocketEvent.Disconnected -> {
                        // Handled by connection logic
                    }
                    is WebSocketEvent.TransactionAdded, is WebSocketEvent.TransactionDeleted -> {
                        // New or deleted transaction from web or another device — refresh from server
                        refreshFromServer()
                    }
                    is WebSocketEvent.ImportDone -> {
                        // CSV import completed on web — pull new data
                        refreshFromServer()
                        _uiState.update { it.copy(syncMessage = "Import synced from web") }
                    }
                    is WebSocketEvent.BudgetsUpdated, is WebSocketEvent.GoalsUpdated -> {
                        // Refresh data when budgets or goals are updated elsewhere
                        refreshFromServer()
                    }
                    is WebSocketEvent.AlertNew -> {
                        // New alert — show notification
                        notificationHelper.showAlert(
                            type = "alert",
                            message = event.message ?: "New alert received"
                        )
                    }
                    is WebSocketEvent.BudgetExceeded -> {
                        // Budget exceeded — show high-priority notification
                        val category = event.category ?: "Unknown"
                        val summary = _uiState.value.analyticsSummary
                        notificationHelper.showBudgetExceeded(
                            category = category,
                            spent = summary.monthlySpending,
                            limit = summary.totalBudget
                        )
                        // Also refresh to get updated budget data
                        refreshFromServer()
                    }
                    is WebSocketEvent.HealthScoreUpdated -> {
                        // Health score changed — refresh to update display
                        refreshFromServer()
                    }
                    is WebSocketEvent.WeeklySummary -> {
                        notificationHelper.showAlert(
                            type = "weekly_summary",
                            message = "Your weekly financial summary is ready!"
                        )
                    }
                }
            }
        }
    }

    fun readAndProcessSms() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            runCatching {
                processSmsUseCase()
            }.onSuccess {
                syncWithServer() // Automatically push newly parsed SMS to server
            }.onFailure { error ->
                _uiState.update { it.copy(error = error.message ?: "Failed to read SMS") }
            }
            _uiState.update { it.copy(loading = false) }
        }
    }

    fun syncWithServer() {
        viewModelScope.launch {
            _uiState.update { it.copy(syncing = true, syncMessage = null) }
            syncTransactionsUseCase()
                .onSuccess {
                    _uiState.update { it.copy(syncing = false, syncMessage = "Synced successfully") }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(syncing = false, error = e.message ?: "Sync failed") }
                }
        }
    }

    /**
     * Light refresh from server — pulls delta changes without pushing pending items.
     * Called in response to WebSocket events.
     */
    private fun refreshFromServer() {
        viewModelScope.launch {
            try {
                syncTransactionsUseCase()
            } catch (e: Exception) { android.util.Log.w("DashboardViewModel", "Silent refresh failure", e) }
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null, syncMessage = null) }
    }

    fun clearActivity() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            transactionRepository.clearAllTransactions()
                .onSuccess {
                    _uiState.update { it.copy(loading = false, syncMessage = "Activity cleared") }
                }
                .onFailure { e ->
                    _uiState.update { it.copy(loading = false, error = e.message ?: "Failed to clear activity") }
                }
        }
    }

    fun addTransaction(
        amount: Double,
        type: String,
        category: String,
        merchant: String,
        date: String,
        splitDetails: List<com.app.domain.model.SplitDetail> = emptyList()
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            val tx = Transaction(
                amount = amount,
                type = type,
                category = category,
                merchant = merchant,
                date = date,
                source = "manual",
                clientId = UUID.randomUUID().toString(),
                splitDetails = splitDetails
            )
            transactionRepository.createTransaction(tx)
                .onSuccess {
                    syncWithServer()
                }
                .onFailure { e ->
                    _uiState.update { it.copy(loading = false, error = e.message ?: "Failed to add transaction") }
                }
            _uiState.update { it.copy(loading = false) }
        }
    }

    fun scanReceipt(
        file: java.io.File,
        onSuccess: (com.app.data.remote.dto.ScannedReceiptDto) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            transactionRepository.scanReceipt(file)
                .onSuccess { dto ->
                    _uiState.update { it.copy(loading = false) }
                    onSuccess(dto)
                }
                .onFailure { e ->
                    val errorMsg = e.message ?: "Failed to scan receipt"
                    _uiState.update { it.copy(loading = false, error = errorMsg) }
                    onError(errorMsg)
                }
        }
    }

    fun importCsv(
        file: java.io.File,
        onSuccess: (com.app.data.remote.dto.CsvImportResultDto) -> Unit,
        onError: (String) -> Unit
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            transactionRepository.importCsv(file)
                .onSuccess { dto ->
                    val msg = "${dto.imported} imported • ${dto.duplicates} duplicates skipped"
                    _uiState.update { it.copy(loading = false, syncMessage = msg) }
                    onSuccess(dto)
                }
                .onFailure { e ->
                    val errorMsg = e.message ?: "Failed to import CSV"
                    _uiState.update { it.copy(loading = false, error = errorMsg) }
                    onError(errorMsg)
                }
        }
    }

    /**
     * Apply a category change to a transaction, with optional merchant learning.
     * @param transactionId The ID of the transaction being edited
     * @param newCategory The new category
     * @param applyToPast Bulk-update all past transactions from same merchant
     * @param applyToFuture Save mapping so future SMS transactions auto-categorize
     * @param alias Optional merchant nickname
     */
    fun applyCategoryToMerchant(
        transactionId: String,
        newCategory: String,
        applyToPast: Boolean,
        applyToFuture: Boolean,
        alias: String? = null
    ) {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            transactionRepository.applyCategoryToMerchant(
                transactionId = transactionId,
                newCategory = newCategory,
                applyToPast = applyToPast,
                applyToFuture = applyToFuture,
                alias = alias
            ).onSuccess {
                val msg = buildString {
                    append("Category updated")
                    if (applyToPast) append(" + past transactions")
                    if (applyToFuture) append(" + future auto-categorization")
                }
                _uiState.update { it.copy(loading = false, syncMessage = msg) }
            }.onFailure { e ->
                _uiState.update { it.copy(loading = false, error = e.message ?: "Failed to update category") }
            }
        }
    }

    /**
     * 1-Tap quick review from the Dashboard Catch-Up Card.
     * Automatically applies to future occurrences and marks reviewed in Room.
     */
    fun quickReviewTransaction(
        transactionId: String,
        newCategory: String,
        alias: String? = null
    ) {
        viewModelScope.launch {
            transactionRepository.applyCategoryToMerchant(
                transactionId = transactionId,
                newCategory = newCategory,
                applyToPast = false,
                applyToFuture = true,
                alias = alias
            )
        }
    }

    /**
     * Dismiss a transaction from unreviewed triage without changing category.
     */
    fun dismissReview(transactionId: String) {
        viewModelScope.launch {
            transactionRepository.dismissReview(transactionId)
        }
    }

    val themePreference: StateFlow<Boolean?> = themeManager.isDarkMode

    fun setDarkMode(enabled: Boolean) {
        themeManager.setDarkMode(enabled)
    }

    fun setThemePreference(preference: Boolean?) {
        themeManager.setThemePreference(preference)
    }

    fun logout(onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            authRepository.logout()
            onSuccess()
        }
    }

    fun deleteAccount(onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            authRepository.deleteAccount()
            onSuccess()
        }
    }
}
