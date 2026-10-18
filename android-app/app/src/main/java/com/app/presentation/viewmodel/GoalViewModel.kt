package com.app.presentation.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.data.local.dao.GoalDao
import com.app.data.local.entity.GoalEntity
import com.app.data.remote.api.GoalApiService
import com.app.data.remote.dto.GoalDto
import com.app.data.remote.dto.toEntity
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class GoalUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val message: String? = null
)

@HiltViewModel
class GoalViewModel @Inject constructor(
    private val goalDao: GoalDao,
    private val goalApi: GoalApiService,
    private val categoryDao: com.app.data.local.dao.CategoryDao
) : ViewModel() {

    private val TAG = "GoalViewModel"
    private val _uiState = MutableStateFlow(GoalUiState())
    val uiState: StateFlow<GoalUiState> = _uiState.asStateFlow()

    val goals = goalDao.observeAll().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    val customCategories: StateFlow<List<com.app.data.local.entity.CategoryEntity>> = categoryDao.observeAllCategories().stateIn(
        viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList()
    )

    fun addCustomCategory(name: String) {
        val trimmed = name.trim()
        if (trimmed.isBlank()) return
        viewModelScope.launch {
            try {
                categoryDao.insertCategory(com.app.data.local.entity.CategoryEntity(name = trimmed, keywordsCsv = ""))
            } catch (e: Exception) {
                Log.w(TAG, "Failed to insert custom category: $trimmed", e)
            }
        }
    }

    init {
        refreshFromServer()
    }

    fun refreshFromServer() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val response = goalApi.list()
                if (response.isSuccessful && response.body()?.data != null) {
                    val serverGoals = response.body()!!.data!!.goals
                    val serverEntities = serverGoals.map { it.toEntity() }
                    // Preserve any unsynced local goals
                    val pendingLocal = goalDao.getPendingSync()
                    goalDao.deleteAll()
                    goalDao.insertAll(serverEntities)
                    if (pendingLocal.isNotEmpty()) {
                        goalDao.insertAll(pendingLocal)
                    }
                } else {
                    Log.w(TAG, "Failed to refresh goals: HTTP ${response.code()}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Offline — using cached goals", e)
            }
            _uiState.update { it.copy(isLoading = false) }
        }
    }

    fun createGoal(
        title: String,
        targetAmount: Double,
        deadline: String?,
        category: String,
        color: String = "#7C6AF5",
        icon: String = "🎯"
    ) {
        if (title.isBlank() || targetAmount <= 0) {
            _uiState.update { it.copy(error = "Goal name and a valid target amount are required") }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            val clientId = UUID.randomUUID().toString()
            val nowIso = java.time.Instant.now().toString()
            val entity = GoalEntity(
                clientId = clientId,
                title = title.trim(),
                targetAmount = targetAmount,
                currentAmount = 0.0,
                deadline = deadline?.takeIf { it.isNotBlank() },
                category = category.ifBlank { "savings" },
                icon = icon,
                color = color,
                isCompleted = false,
                syncStatus = "pending_create",
                createdAt = nowIso,
                updatedAt = nowIso
            )
            // 1. Save locally immediately
            val localId = goalDao.insert(entity)
            try {
                categoryDao.insertCategory(com.app.data.local.entity.CategoryEntity(name = entity.category, keywordsCsv = ""))
            } catch (_: Exception) {}
            _uiState.update { it.copy(message = "Goal created!", isLoading = false) }

            // 2. Sync to server in background
            try {
                val dto = GoalDto(
                    title = title.trim(),
                    targetAmount = targetAmount,
                    deadline = deadline?.takeIf { it.isNotBlank() },
                    category = category.ifBlank { "savings" },
                    clientId = clientId
                )
                val response = goalApi.create(dto)
                if (response.isSuccessful && response.body()?.data?.goal != null) {
                    val serverGoal = response.body()!!.data!!.goal
                    goalDao.deleteById(localId)
                    goalDao.insert(serverGoal.toEntity())
                }
            } catch (e: Exception) {
                Log.w(TAG, "Offline — goal will sync when reconnected", e)
            }
        }
    }

    fun addSavings(goal: GoalEntity, amount: Double) {
        if (amount <= 0.0) {
            _uiState.update { it.copy(error = "Please enter an amount greater than 0") }
            return
        }
        viewModelScope.launch {
            val newAmount = goal.currentAmount + amount
            val isNowCompleted = newAmount >= goal.targetAmount
            val completedAt = if (isNowCompleted && !goal.isCompleted) {
                java.time.Instant.now().toString()
            } else {
                goal.completedAt
            }
            val nowIso = java.time.Instant.now().toString()

            val updatedGoal = goal.copy(
                currentAmount = newAmount,
                isCompleted = isNowCompleted,
                completedAt = completedAt,
                updatedAt = nowIso,
                syncStatus = if (goal.serverId != null) "pending_update" else goal.syncStatus
            )

            // 1. Update in local Room DB immediately
            goalDao.update(updatedGoal)
            if (isNowCompleted && !goal.isCompleted) {
                _uiState.update { it.copy(message = "🎉 Goal '${goal.title}' reached!") }
            } else {
                _uiState.update { it.copy(message = "Added ₹${"%,.0f".format(amount)} to ${goal.title}") }
            }

            // 2. Sync to server in background if available
            val serverId = goal.serverId
            if (serverId != null) {
                try {
                    val response = goalApi.update(serverId, mapOf("addAmount" to amount))
                    if (response.isSuccessful && response.body()?.data?.goal != null) {
                        val serverUpdated = response.body()!!.data!!.goal
                        goalDao.insert(serverUpdated.toEntity())
                    }
                } catch (e: Exception) {
                    Log.w(TAG, "Offline — savings synced locally", e)
                }
            }
        }
    }

    fun deleteGoal(goal: GoalEntity) {
        viewModelScope.launch {
            // 1. Delete from local Room DB immediately
            if (goal.id != 0L) {
                goalDao.deleteById(goal.id)
            }
            goal.clientId?.let { goalDao.deleteByClientId(it) }
            goal.serverId?.let { goalDao.deleteByServerId(it) }
            goalDao.delete(goal)
            _uiState.update { it.copy(message = "Goal '${goal.title}' removed") }

            // 2. Sync deletion to server if it exists remotely
            val serverId = goal.serverId
            if (serverId != null) {
                try {
                    goalApi.delete(serverId)
                } catch (e: Exception) {
                    Log.w(TAG, "Offline — delete synced locally", e)
                }
            }
        }
    }

    fun clearMessage() {
        _uiState.update { it.copy(error = null, message = null) }
    }
}
