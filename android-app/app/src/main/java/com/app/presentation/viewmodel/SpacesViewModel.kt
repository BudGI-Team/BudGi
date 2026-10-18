package com.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.data.local.entity.SharedSpaceEntity
import com.app.domain.repository.SpaceRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SpacesUiState(
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class SpacesViewModel @Inject constructor(
    private val spaceRepository: SpaceRepository,
    private val userDao: com.app.data.local.dao.UserDao
) : ViewModel() {

    val currentUser: StateFlow<com.app.data.local.entity.UserEntity?> = userDao.getLoggedInUser()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )

    val spaces: StateFlow<List<SharedSpaceEntity>> = spaceRepository.observeSpaces()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _uiState = MutableStateFlow(SpacesUiState())
    val uiState = _uiState.asStateFlow()

    init {
        refreshSpaces()
    }

    fun refreshSpaces() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = spaceRepository.refreshSpaces()
            if (result.isFailure) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = result.exceptionOrNull()?.message ?: "Unknown error"
                )
            } else {
                _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }

    fun createSpace(name: String, type: String, onCreated: ((String) -> Unit)? = null) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = spaceRepository.createSpace(name, type)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(isLoading = false)
                result.getOrNull()?.id?.let { onCreated?.invoke(it) }
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = result.exceptionOrNull()?.message ?: "Failed to create space"
                )
            }
        }
    }

    fun joinSpace(inviteCode: String, onJoined: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)
            val result = spaceRepository.joinSpace(inviteCode)
            if (result.isSuccess) {
                _uiState.value = _uiState.value.copy(isLoading = false)
                onJoined()
            } else {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = result.exceptionOrNull()?.message ?: "Failed to join space"
                )
            }
        }
    }

    fun getSpaceById(id: String): StateFlow<SharedSpaceEntity?> {
        return spaceRepository.observeSpaceById(id).stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = null
        )
    }

    fun addGoal(spaceId: String, title: String, amount: Double, category: String) {
        viewModelScope.launch {
            spaceRepository.addGoal(spaceId, title, amount, category)
        }
    }

    fun contributeToGoal(spaceId: String, goalTitle: String, amount: Double) {
        viewModelScope.launch {
            spaceRepository.contributeToGoal(spaceId, goalTitle, amount)
        }
    }

    fun deleteGoal(spaceId: String, goalTitle: String) {
        viewModelScope.launch {
            spaceRepository.deleteGoal(spaceId, goalTitle)
        }
    }

    fun addExpense(
        spaceId: String,
        description: String,
        amount: Double,
        paidById: String,
        paidByName: String,
        splitWithMemberIds: List<String>,
        category: String = "General"
    ) {
        viewModelScope.launch {
            spaceRepository.addExpense(spaceId, description, amount, paidById, paidByName, splitWithMemberIds, category)
        }
    }

    fun settleUp(
        spaceId: String,
        fromMemberId: String,
        fromMemberName: String,
        toMemberId: String,
        toMemberName: String,
        amount: Double
    ) {
        viewModelScope.launch {
            spaceRepository.settleUp(spaceId, fromMemberId, fromMemberName, toMemberId, toMemberName, amount)
        }
    }

    fun deleteExpense(spaceId: String, expenseId: String) {
        viewModelScope.launch {
            spaceRepository.deleteExpense(spaceId, expenseId)
        }
    }

    fun deleteSpace(spaceId: String, onDeleted: () -> Unit = {}) {
        viewModelScope.launch {
            spaceRepository.deleteSpace(spaceId)
            onDeleted()
        }
    }
}
