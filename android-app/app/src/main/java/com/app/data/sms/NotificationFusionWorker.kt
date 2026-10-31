package com.app.data.sms

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.app.data.local.dao.TransactionDao
import com.app.data.local.entity.TransactionEntity
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.time.Instant
import java.util.UUID

/**
 * Worker that periodically reviews [TransactionFusionEngine] for tentative transactions
 * that never received a matching bank SMS (e.g. Paytm Wallet, Amazon Pay Wallet, PhonePe Wallet).
 *
 * After 5 minutes, these entries are officially confirmed and inserted into the local Room database
 * as legitimate wallet transactions.
 */
class NotificationFusionWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "NotificationFusionWorker"
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface FusionWorkerEntryPoint {
        fun fusionEngine(): TransactionFusionEngine
        fun transactionDao(): TransactionDao
        fun categoryClassifier(): TransactionCategoryClassifier
    }

    override suspend fun doWork(): Result {
        return try {
            val entryPoint = EntryPointAccessors.fromApplication(
                applicationContext,
                FusionWorkerEntryPoint::class.java
            )
            val fusionEngine = entryPoint.fusionEngine()
            val transactionDao = entryPoint.transactionDao()
            val classifier = entryPoint.categoryClassifier()

            val confirmedWalletTxs = fusionEngine.promoteStaleEntries()

            if (confirmedWalletTxs.isNotEmpty()) {
                Log.i(TAG, "Persisting ${confirmedWalletTxs.size} wallet-only transactions from notification fusion")

                val entities = confirmedWalletTxs.map { tentative ->
                    val cleanMerchant = tentative.merchant ?: tentative.sourceApp
                    val normalizedMerchant = MerchantNormalizer.normalize(cleanMerchant)
                    val category = classifier.classify(
                        message = tentative.rawText,
                        upiId = null,
                        merchant = cleanMerchant
                    )
                    val type = if (tentative.type.equals("credit", ignoreCase = true)) "income" else "expense"
                    val isoDate = Instant.ofEpochMilli(tentative.timestamp).toString()

                    TransactionEntity(
                        clientId = UUID.randomUUID().toString(),
                        amount = tentative.amount,
                        type = type,
                        category = category,
                        categorySource = "Rule",
                        description = "${tentative.sourceApp}: $cleanMerchant",
                        merchant = cleanMerchant,
                        normalizedMerchant = normalizedMerchant.ifBlank { null },
                        date = isoDate,
                        source = "wallet_notification",
                        syncStatus = "pending_create",
                        isRecurring = false,
                        isAnomaly = false,
                        method = "Wallet",
                        timestamp = tentative.timestamp,
                        sender = tentative.sourceApp,
                        upiId = null,
                        referenceNumber = null,
                        accountLast4 = null,
                        availableBalance = null,
                        rawSms = tentative.rawText,
                        smsBodyDate = null,
                        alias = null,
                        isReviewed = false
                    )
                }

                transactionDao.insertAll(entities)
                try {
                    com.app.presentation.widget.BudGiWidget.updateWidget(applicationContext)
                } catch (_: Exception) {}
            }

            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Error running NotificationFusionWorker", e)
            Result.retry()
        }
    }
}

