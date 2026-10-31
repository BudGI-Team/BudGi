package com.app.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.app.data.local.dao.DetectedBankSummary
import com.app.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class BankUiState(
    val isSyncing: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class BanksViewModel @Inject constructor(
    private val repository: TransactionRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(BankUiState())
    val uiState: StateFlow<BankUiState> = _uiState.asStateFlow()

    val detectedBanks: StateFlow<List<DetectedBankSummary>> = repository.observeDetectedBankAccounts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val totalLiquidity: StateFlow<Double> = detectedBanks.map { list ->
        list.sumOf { it.latestBalance ?: 0.0 }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    fun refreshAccounts() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSyncing = true)
            try {
                repository.readParseAndStoreSms()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(error = e.message)
            } finally {
                _uiState.value = _uiState.value.copy(isSyncing = false)
            }
        }
    }
}

