package com.app.data.watchdog

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

/**
 * WorkManager Scheduler for the Autonomous Financial Watchdog.
 * Operates periodically every 12 hours or immediately on demand when high-value SMS arrives.
 */
object WatchdogWorkScheduler {

    private const val PERIODIC_WATCHDOG_WORK_NAME = "budgi_periodic_watchdog_work"
    private const val EXPEDITED_WATCHDOG_WORK_NAME = "budgi_expedited_watchdog_work"

    /**
     * Schedule background financial watchdog evaluation twice daily.
     */
    fun schedulePeriodicWatchdog(context: Context) {
        val periodicRequest = PeriodicWorkRequestBuilder<FinancialWatchdogWorker>(
            12, TimeUnit.HOURS,
            1, TimeUnit.HOURS
        )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC_WATCHDOG_WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            periodicRequest
        )
    }

    /**
     * Run an immediate watchdog pass (e.g. after SMS parsing detects salary or bill).
     */
    fun scheduleImmediateWatchdog(context: Context) {
        val oneTimeRequest = OneTimeWorkRequestBuilder<FinancialWatchdogWorker>()
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniqueWork(
            EXPEDITED_WATCHDOG_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            oneTimeRequest
        )
    }
}
