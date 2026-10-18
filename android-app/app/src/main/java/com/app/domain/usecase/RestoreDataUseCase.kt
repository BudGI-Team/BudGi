package com.app.domain.usecase

import com.app.domain.repository.TransactionRepository
import javax.inject.Inject

/**
 * Restores data from the backend server.
 * Replaces the old Firebase-based RestoreDataUseCase.
 */
class RestoreDataUseCase @Inject constructor(
    private val repository: TransactionRepository
) {
    suspend operator fun invoke(): Result<Unit> = repository.refreshFromServer()
}
