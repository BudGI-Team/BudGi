package com.app.data.sync

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * Persistent Sync Queue Scheduler using WorkManager
 * Manages periodic 15-minute background synchronization and
 * expedited on-demand sync for offline operations.
 */
object SyncWorkScheduler {

    private const val PERIODIC_SYNC_WORK_NAME = "budgi_periodic_sync_work"
    private const val EXPEDITED_SYNC_WORK_NAME = "budgi_expedited_sync_work"

    private val networkConstraints = Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()

    /**
     * Schedules periodic background sync every 15 minutes with connected network constraint
     */
    fun schedulePeriodicSync(context: Context) {
        val periodicRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            15, TimeUnit.MINUTES,
            5, TimeUnit.MINUTES
        )
            .setConstraints(networkConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_SYNC_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicRequest
        )
    }

    /**
     * Enqueues an expedited, one-time sync request as soon as network is available
     * Call this whenever an offline operation is added to the pending queue.
     */
    fun scheduleImmediateSync(context: Context) {
        val oneTimeRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(networkConstraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            EXPEDITED_SYNC_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            oneTimeRequest
        )
    }
}
