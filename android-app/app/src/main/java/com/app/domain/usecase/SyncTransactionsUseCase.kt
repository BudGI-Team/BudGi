package com.app.domain.usecase

import com.app.domain.repository.TransactionRepository
import javax.inject.Inject

class SyncTransactionsUseCase @Inject constructor(
    private val repository: TransactionRepository
) {
    suspend operator fun invoke() = repository.syncWithServer()
}
