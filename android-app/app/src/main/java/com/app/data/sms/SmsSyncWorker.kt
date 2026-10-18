package com.app.data.sms

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.app.domain.usecase.ProcessSmsUseCase
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

class SmsSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SmsSyncWorkerEntryPoint {
        fun processSmsUseCase(): ProcessSmsUseCase
        fun transactionRepository(): com.app.domain.repository.TransactionRepository
    }

    override suspend fun doWork(): Result {
        return try {
            val entryPoint = EntryPointAccessors.fromApplication(
                applicationContext,
                SmsSyncWorkerEntryPoint::class.java
            )
            val processSmsUseCase = entryPoint.processSmsUseCase()
            processSmsUseCase()

            // Trigger autonomous financial watchdog pass immediately upon receiving and parsing new SMS
            try {
                com.app.data.watchdog.WatchdogWorkScheduler.scheduleImmediateWatchdog(applicationContext)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            
            // Try to sync with server in background; ignore failure (SMS is saved locally anyway)
            try {
                val repository = entryPoint.transactionRepository()
                repository.syncWithServer()
            } catch (e: Exception) {
                e.printStackTrace()
            }

            Result.success()
        } catch (e: Exception) {
            e.printStackTrace()
            Result.retry()
        }
    }
}
