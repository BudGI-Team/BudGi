package com.app.data.sms

import android.util.Log
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Status of a transaction in the fusion lifecycle.
 */
enum class FusionStatus {
    TENTATIVE,          // Created from app notification, waiting for bank SMS match
    FUSED,              // Successfully merged with a bank SMS
    WALLET_CONFIRMED,   // No SMS arrived within 5 minutes → confirmed as wallet payment
    EXPIRED             // Stale, discarded without confirmation
}

/**
 * In-memory representation of a transaction captured from a payment app notification.
 */
data class TentativeTransaction(
    val amount: Double,
    val type: String,                  // "debit" or "credit"
    val merchant: String?,
    val sourceApp: String,             // "Google Pay", "Paytm", "PhonePe", etc.
    val packageName: String,
    val notificationId: Int,
    val timestamp: Long,
    val rawText: String,
    var status: FusionStatus = FusionStatus.TENTATIVE,
    val createdAt: Long = System.currentTimeMillis()
)

/**
 * Result of checking an incoming bank SMS against pending tentative notifications.
 */
sealed class FusionResult {
    /** No matching notification found; insert SMS transaction as normal. */
    object InsertNew : FusionResult()

    /** Already handled by notification and confirmed; skip SMS insertion to avoid duplication. */
    object Skip : FusionResult()

    /**
     * Matching notification found! Merge notification's clean merchant name
     * and source app into the bank SMS transaction data.
     */
    data class Enrich(
        val cleanMerchant: String?,
        val sourceApp: String,
        val tentativeTimestamp: Long
    ) : FusionResult()
}

/**
 * Transaction Fusion Engine (BudGi Precision Hybrid Reconciler)
 *
 * Solves the critical race condition between instant app notifications (GPay, Paytm,
 * PhonePe) and subsequent bank SMS alerts:
 * 1. App notifications arrive first (t = 0s) → stored as [TentativeTransaction].
 * 2. Bank SMS arrives (t = 3s-30s) → matched by amount, type, and 3-minute sliding window.
 *    - Reconciled with clean merchant name from notification + bank account/balance from SMS.
 * 3. Wallet payments (no bank SMS ever arrives) → promoted to WALLET_CONFIRMED after 5 mins.
 * 4. Duplicate prevention: never creates two transactions for the same real-world payment.
 */
@Singleton
class TransactionFusionEngine @Inject constructor() {

    companion object {
        private const val TAG = "TransactionFusion"
        private const val MATCH_WINDOW_MS = 180_000L   // 3 minutes
        private const val WALLET_TIMEOUT_MS = 300_000L // 5 minutes
        private const val EXPIRY_TIMEOUT_MS = 1_800_000L // 30 minutes
        private const val MAX_STORE_SIZE = 100
        private const val AMOUNT_TOLERANCE = 0.05
    }

    // In-memory store: notificationId/key → TentativeTransaction
    private val tentativeStore = ConcurrentHashMap<String, TentativeTransaction>()

    /**
     * Called when a payment app notification is successfully parsed.
     * Records it as [FusionStatus.TENTATIVE] waiting for a bank SMS match.
     */
    fun onNotificationReceived(notifTx: NotificationTransaction): TentativeTransaction {
        pruneExpiredEntries()

        val key = "${notifTx.packageName}_${notifTx.notificationId}_${notifTx.timestamp}"
        val tentative = TentativeTransaction(
            amount = notifTx.amount,
            type = notifTx.type,
            merchant = notifTx.merchant,
            sourceApp = notifTx.sourceApp,
            packageName = notifTx.packageName,
            notificationId = notifTx.notificationId,
            timestamp = notifTx.timestamp,
            rawText = notifTx.rawText,
            status = FusionStatus.TENTATIVE
        )

        tentativeStore[key] = tentative
        Log.i(TAG, "Recorded TENTATIVE transaction: ₹${notifTx.amount} to '${notifTx.merchant}' via ${notifTx.sourceApp}")
        return tentative
    }

    /**
     * Called by the SMS parser/repository before inserting a parsed bank SMS.
     * Checks if a matching tentative notification exists within a 3-minute window.
     *
     * @return [FusionResult.Enrich] if matched (update with clean merchant name),
     *         [FusionResult.InsertNew] if no match (standard SMS flow).
     */
    fun onSmsParsed(
        amount: Double,
        type: String,
        timestamp: Long,
        merchant: String?,
        referenceNumber: String?
    ): FusionResult {
        // Find matching tentative entry:
        // 1. Status is TENTATIVE
        // 2. Amount matches within tolerance
        // 3. Type matches ("debit" / "expense" or "credit" / "income")
        // 4. Timestamp is within MATCH_WINDOW_MS (±3 minutes)
        val normalizedType = if (type == "expense") "debit" else if (type == "income") "credit" else type

        val candidate = tentativeStore.values
            .filter { it.status == FusionStatus.TENTATIVE }
            .filter { kotlin.math.abs(it.amount - amount) <= AMOUNT_TOLERANCE }
            .filter { it.type.equals(normalizedType, ignoreCase = true) }
            .filter { kotlin.math.abs(it.timestamp - timestamp) <= MATCH_WINDOW_MS }
            .minByOrNull { kotlin.math.abs(it.timestamp - timestamp) }

        if (candidate != null) {
            candidate.status = FusionStatus.FUSED
            Log.i(
                TAG,
                "FUSED notification + SMS: ₹$amount. " +
                        "Notification Merchant: '${candidate.merchant}', SMS Merchant: '$merchant', App: ${candidate.sourceApp}"
            )
            return FusionResult.Enrich(
                cleanMerchant = candidate.merchant ?: merchant,
                sourceApp = candidate.sourceApp,
                tentativeTimestamp = candidate.timestamp
            )
        }

        return FusionResult.InsertNew
    }

    /**
     * Promotes stale tentative entries:
     * - Older than 5 minutes with NO bank SMS → [FusionStatus.WALLET_CONFIRMED]
     *   (these are true wallet payments like Paytm Wallet, Amazon Pay balance)
     * - Older than 30 minutes → [FusionStatus.EXPIRED]
     *
     * @return List of newly confirmed wallet transactions to be inserted into Room DB.
     */
    fun promoteStaleEntries(): List<TentativeTransaction> {
        val now = System.currentTimeMillis()
        val walletConfirmed = mutableListOf<TentativeTransaction>()

        for ((_, tentative) in tentativeStore) {
            if (tentative.status == FusionStatus.TENTATIVE) {
                val age = now - tentative.createdAt
                if (age >= WALLET_TIMEOUT_MS) {
                    tentative.status = FusionStatus.WALLET_CONFIRMED
                    walletConfirmed.add(tentative)
                    Log.i(TAG, "Promoted to WALLET_CONFIRMED: ₹${tentative.amount} '${tentative.merchant}' (${tentative.sourceApp})")
                }
            } else if (tentative.status != FusionStatus.EXPIRED) {
                val age = now - tentative.createdAt
                if (age >= EXPIRY_TIMEOUT_MS) {
                    tentative.status = FusionStatus.EXPIRED
                }
            }
        }

        return walletConfirmed
    }

    /**
     * Remove entries older than 30 minutes to prevent memory growth.
     */
    private fun pruneExpiredEntries() {
        val now = System.currentTimeMillis()
        if (tentativeStore.size > MAX_STORE_SIZE) {
            val iterator = tentativeStore.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (entry.value.status == FusionStatus.EXPIRED || (now - entry.value.createdAt) > EXPIRY_TIMEOUT_MS) {
                    iterator.remove()
                }
            }
        }
    }

    /**
     * Inspect active tentative transactions count (useful for UI diagnostics).
     */
    fun getPendingCount(): Int = tentativeStore.values.count { it.status == FusionStatus.TENTATIVE }

    /**
     * Clear all store data (e.g. on user logout / clear data).
     */
    fun clear() {
        tentativeStore.clear()
    }
}

