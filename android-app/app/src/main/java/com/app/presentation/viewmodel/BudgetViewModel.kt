package com.app.presentation.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.data.local.dao.BudgetDao
import com.app.data.local.dao.CategoryDao
import com.app.data.sync.SyncWorkScheduler
import com.app.domain.engine.CopilotProposalEngine
import com.app.domain.model.AutonomousActionProposal
import com.app.domain.model.Budget
import com.app.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BudgetUiState(
    val budgets: List<Budget> = emptyList(),
    val totalBudget: Double = 0.0,
    val totalSpent: Double = 0.0,
    val isLoading: Boolean = false,
    val calibrationProposal: AutonomousActionProposal.BudgetRebalanceProposal? = null
)

@HiltViewModel
class BudgetViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: TransactionRepository,
    private val categoryDao: CategoryDao,
    private val budgetDao: BudgetDao,
    private val transactionDao: com.app.data.local.dao.TransactionDao,
    private val copilotProposalEngine: CopilotProposalEngine
) : ViewModel() {

    private val _uiState = MutableStateFlow(BudgetUiState())
    val uiState: StateFlow<BudgetUiState> = _uiState.asStateFlow()

    private val dismissedCalibrationIds = mutableSetOf<String>()

    val customCategories: StateFlow<List<com.app.data.local.entity.CategoryEntity>> = categoryDao.observeAllCategories().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    init {
        loadBudgets()
    }

    private fun loadBudgets() {
        repository.observeBudgets()
            .onEach { budgets ->
                val proposal = copilotProposalEngine.evaluateBudgetRebalance(budgets, dismissedCalibrationIds)
                _uiState.update { it.copy(
                    budgets = budgets,
                    totalBudget = budgets.sumOf { b -> b.limit },
                    totalSpent = budgets.sumOf { b -> b.spentAmount },
                    calibrationProposal = proposal
                ) }
            }.launchIn(viewModelScope)
    }

    fun dismissCalibration(proposal: AutonomousActionProposal.BudgetRebalanceProposal) {
        dismissedCalibrationIds.add(proposal.id)
        _uiState.update { it.copy(calibrationProposal = null) }
    }

    fun rebalanceBudgets(fromCategory: String, toCategory: String, amount: Double) {
        viewModelScope.launch {
            try {
                val fromBudget = budgetDao.getByCategory(fromCategory)
                val toBudget = budgetDao.getByCategory(toCategory)
                if (fromBudget != null && toBudget != null) {
                    val newFrom = (fromBudget.limitAmount - amount).coerceAtLeast(0.0)
                    val newTo = toBudget.limitAmount + amount
                    budgetDao.insertBudget(fromBudget.copy(limitAmount = newFrom, syncStatus = "pending_update"))
                    budgetDao.insertBudget(toBudget.copy(limitAmount = newTo, syncStatus = "pending_update"))
                    SyncWorkScheduler.scheduleImmediateSync(context)
                }
                dismissedCalibrationIds.add("rebal_${fromCategory}_to_${toCategory}")
                _uiState.update { it.copy(calibrationProposal = null) }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun addCustomCategory(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            try {
                categoryDao.insertCategory(com.app.data.local.entity.CategoryEntity(name = trimmed, keywordsCsv = ""))
            } catch (_: Exception) {}
        }
    }

    fun addOrUpdateBudget(category: String, limit: Double) {
        viewModelScope.launch {
            try {
                categoryDao.insertCategory(com.app.data.local.entity.CategoryEntity(name = category.trim(), keywordsCsv = ""))
            } catch (_: Exception) {}
            repository.saveBudget(
                Budget(
                    category = category.trim(),
                    limit = limit,
                    period = "monthly"
                )
            )
        }
    }
    
    fun deleteBudget(id: String) {
        viewModelScope.launch {
            repository.deleteBudget(id)
        }
    }
    /**
     * Auto-calibrates smart budget limits for all active categories
     * based on the user's real monthly spending cadence + 15% safety buffer.
     */
    fun autoCalibrateBudgets() {
        viewModelScope.launch {
            try {
                val currentMonth = java.time.YearMonth.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"))
                val totals = transactionDao.observeCategoryTotalsByMonth(currentMonth).first()
                for (catTotal in totals) {
                    if (catTotal.total > 0.0 && catTotal.category != "Others" && catTotal.category != "Transfer") {
                        val raw = catTotal.total * 1.15
                        val smartLimit = (Math.ceil(raw / 500.0) * 500.0).coerceAtLeast(1000.0)
                        addOrUpdateBudget(catTotal.category, smartLimit)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}

