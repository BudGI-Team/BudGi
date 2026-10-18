package com.app.data.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.app.domain.repository.TransactionRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

/**
 * Persistent Sync Worker
 * Flushes the Room pending_operations queue and synchronizes
 * pending transactions, budgets, and goals with exponential backoff.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SyncWorkerEntryPoint {
        fun transactionRepository(): TransactionRepository
    }

    companion object {
        const val TAG = "SyncWorker"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting persistent background sync queue execution (attempt $runAttemptCount)...")
        return try {
            val entryPoint = EntryPointAccessors.fromApplication(
                applicationContext,
                SyncWorkerEntryPoint::class.java
            )
            val repository = entryPoint.transactionRepository()
            val result = repository.syncWithServer()

            if (result.isSuccess) {
                Log.d(TAG, "Persistent sync queue completed successfully")
                Result.success()
            } else {
                Log.w(TAG, "Sync failed: ${result.exceptionOrNull()?.message}")
                if (runAttemptCount < 5) {
                    Result.retry()
                } else {
                    Result.failure()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error executing SyncWorker", e)
            if (runAttemptCount < 5) {
                Result.retry()
            } else {
                Result.failure()
            }
        }
    }
}
