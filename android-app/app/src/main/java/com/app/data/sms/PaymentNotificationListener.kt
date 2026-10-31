package com.app.data.sms

import android.app.Notification
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.Locale
import java.util.regex.Pattern

/**
 * Parsed result from a payment app notification.
 */
data class NotificationTransaction(
    val amount: Double,
    val type: String,           // "debit" or "credit"
    val merchant: String?,
    val sourceApp: String,      // "Google Pay", "Paytm", etc.
    val packageName: String,
    val notificationId: Int,
    val timestamp: Long,
    val rawText: String
)

/**
 * Payment Notification Listener (Phase 5A)
 *
 * Listens to notifications from whitelisted Indian payment apps (Google Pay, Paytm,
 * PhonePe, BHIM, CRED, Amazon Pay, WhatsApp Pay) and extracts transaction data.
 *
 * Parsed notifications are fed into the [TransactionFusionEngine] as TENTATIVE entries
 * which either merge with an incoming bank SMS (preventing duplicates) or are promoted
 * to standalone wallet transactions after a timeout.
 *
 * Blacklisted packages (SMS aggregators like Truecaller, Google Messages) are ignored
 * to avoid re-reading bank SMS that the [SmsReceiver] already handles.
 */
class PaymentNotificationListener : NotificationListenerService() {

    companion object {
        private const val TAG = "PaymentNotifListener"

        /**
         * Whitelisted payment app packages → human-readable names.
         */
        private val PAYMENT_PACKAGES = mapOf(
            "com.google.android.apps.nbu.paisa.user" to "Google Pay",
            "net.one97.paytm" to "Paytm",
            "com.phonepe.app" to "PhonePe",
            "in.org.npci.upiapp" to "BHIM UPI",
            "com.dreamplug.androidapp" to "CRED",
            "in.amazon.mShop.android.shopping" to "Amazon Pay",
            "com.whatsapp" to "WhatsApp Pay",
        )

        /**
         * Blacklisted SMS aggregator packages — ignored to prevent duplicate bank SMS reading.
         */
        private val BLACKLISTED_PACKAGES = setOf(
            "com.truecaller",
            "com.google.android.apps.messaging",
            "com.samsung.android.messaging",
            "com.microsoft.android.smsorganizer",
            "com.contapps.android.messaging",
            "com.jb.gosms",
            "com.handcent.app.nextsms",
        )

        // ── Amount extraction from notification text ────────────────────
        private val NOTIF_AMOUNT_PATTERNS = listOf(
            Pattern.compile("""(?:₹|Rs\.?\s*|INR\s*)([\d,]+\.?\d*)""", Pattern.CASE_INSENSITIVE),
            Pattern.compile("""([\d,]+\.?\d*)\s*(?:₹|Rs|INR)""", Pattern.CASE_INSENSITIVE),
        )

        // ── Type detection keywords ────────────────────────────────────
        private val DEBIT_NOTIF_KEYWORDS = listOf(
            "paid", "sent", "debited", "spent", "payment of", "transferred"
        )
        private val CREDIT_NOTIF_KEYWORDS = listOf(
            "received", "credited", "cashback", "refund", "added", "reversed"
        )

        // ── Merchant extraction patterns ───────────────────────────────
        private val MERCHANT_PATTERNS = listOf(
            Pattern.compile(
                """(?:paid to|sent to|payment to|transferred to)\s+(.+?)(?:\s+via|\s+using|\s+successful|\.|\s+on|$)""",
                Pattern.CASE_INSENSITIVE
            ),
            Pattern.compile(
                """(?:received from|from)\s+(.+?)(?:\s+via|\s+successful|\.|\s+on|$)""",
                Pattern.CASE_INSENSITIVE
            ),
            Pattern.compile(
                """(?:paid for)\s+(.+?)(?:\s+order|\s+via|\.|\s+on|$)""",
                Pattern.CASE_INSENSITIVE
            ),
            Pattern.compile(
                """(?:paid towards)\s+(.+?)(?:\s+via|\s+using|\.|\s+on|$)""",
                Pattern.CASE_INSENSITIVE
            ),
        )

        // ── Promotional / non-transactional noise keywords ─────────────
        private val PROMO_KEYWORDS = listOf(
            "offer", "cashback up to", "win ", "earn ", "install", "update",
            "try ", "explore", "reminder", "complete your", "verify", "kyc",
            "congratulations", "invite", "share", "tap to", "check out",
            "discover", "limited time", "hurry", "last chance", "coupon",
            "voucher", "free", "reward points", "gold", "scratch"
        )
    }

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ListenerEntryPoint {
        fun fusionEngine(): TransactionFusionEngine
    }

    // ── Service lifecycle ───────────────────────────────────────────────

    override fun onListenerConnected() {
        Log.i(TAG, "NotificationListenerService connected — listening for payment notifications")
    }

    override fun onListenerDisconnected() {
        Log.w(TAG, "NotificationListenerService disconnected")
        // Request rebind if the system disconnected us
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            requestRebind(
                ComponentName(applicationContext, PaymentNotificationListener::class.java)
            )
        }
    }

    // ── Notification processing ─────────────────────────────────────────

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        val pkg = sbn.packageName ?: return

        // Immediately reject blacklisted packages
        if (pkg in BLACKLISTED_PACKAGES) return

        // Only process whitelisted payment apps
        val appName = PAYMENT_PACKAGES[pkg] ?: return

        try {
            val notification = sbn.notification ?: return
            val text = extractNotificationText(notification) ?: return

            // Filter out promotional / non-transactional notifications
            if (isPromotionalNotification(text)) {
                Log.d(TAG, "Skipping promotional notification from $appName: ${text.take(80)}")
                return
            }

            // Must contain an amount to be a transaction notification
            val amount = extractAmount(text) ?: return
            if (amount < 1.0 || amount > 10_000_000.0) return

            // Detect type
            val type = detectType(text) ?: return

            // Extract merchant
            val merchant = extractMerchant(text, appName)

            val notifTx = NotificationTransaction(
                amount = amount,
                type = type,
                merchant = merchant,
                sourceApp = appName,
                packageName = pkg,
                notificationId = sbn.id,
                timestamp = sbn.postTime,
                rawText = text.take(500)
            )

            Log.i(TAG, "Parsed $appName notification: $type ₹$amount → ${merchant ?: "unknown"}")

            // Feed into the fusion engine
            val entryPoint = EntryPointAccessors.fromApplication(
                applicationContext, ListenerEntryPoint::class.java
            )
            entryPoint.fusionEngine().onNotificationReceived(notifTx)

            // Trigger the fusion worker to handle promotion/expiry
            triggerFusionWorker(applicationContext)

        } catch (e: Exception) {
            Log.e(TAG, "Error processing notification from $appName", e)
        }
    }

    // ── Text extraction ─────────────────────────────────────────────────

    /**
     * Extract readable text content from a notification by combining the title,
     * text, bigText, and subText extras.
     */
    private fun extractNotificationText(notification: Notification): String? {
        val extras = notification.extras ?: return null

        val parts = mutableListOf<String>()

        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()
        if (!title.isNullOrBlank()) parts.add(title)

        // Prefer bigText (expanded notification) over short text
        val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
        if (!bigText.isNullOrBlank()) {
            parts.add(bigText)
        } else if (!text.isNullOrBlank()) {
            parts.add(text)
        }

        val subText = extras.getCharSequence(Notification.EXTRA_SUB_TEXT)?.toString()
        if (!subText.isNullOrBlank()) parts.add(subText)

        val combined = parts.joinToString(" ").trim()
        return combined.ifBlank { null }
    }

    // ── Amount extraction ───────────────────────────────────────────────

    private fun extractAmount(text: String): Double? {
        for (pattern in NOTIF_AMOUNT_PATTERNS) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val raw = matcher.group(1)?.replace(",", "")?.toDoubleOrNull()
                if (raw != null && raw > 0) return raw
            }
        }
        return null
    }

    // ── Type detection ──────────────────────────────────────────────────

    private fun detectType(text: String): String? {
        val lower = text.lowercase(Locale.US)

        // Score proximity: find the first debit and first credit keyword
        val debitIdx = DEBIT_NOTIF_KEYWORDS
            .map { lower.indexOf(it) }
            .filter { it >= 0 }
            .minOrNull() ?: Int.MAX_VALUE

        val creditIdx = CREDIT_NOTIF_KEYWORDS
            .map { lower.indexOf(it) }
            .filter { it >= 0 }
            .minOrNull() ?: Int.MAX_VALUE

        return when {
            debitIdx < creditIdx -> "debit"
            creditIdx < debitIdx -> "credit"
            // If both are MAX_VALUE, neither was found
            debitIdx == Int.MAX_VALUE -> null
            else -> null
        }
    }

    // ── Merchant extraction ─────────────────────────────────────────────

    private fun extractMerchant(text: String, appName: String): String? {
        // Try structured regex patterns first
        for (pattern in MERCHANT_PATTERNS) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val raw = matcher.group(1)?.trim()
                if (!raw.isNullOrBlank() && raw.length > 1 && !raw.matches(Regex("""^\d+$"""))) {
                    // Clean up trailing noise
                    val cleaned = raw
                        .replace(Regex("""\s+(ref|on|via|txn|at|date|using|upi).*$""", RegexOption.IGNORE_CASE), "")
                        .trim()
                    if (cleaned.isNotBlank()) {
                        return cleaned.split(" ").joinToString(" ") {
                            it.replaceFirstChar { c -> c.uppercase() }
                        }
                    }
                }
            }
        }

        // App-specific fallback heuristics
        return extractAppSpecificMerchant(text, appName)
    }

    /**
     * App-specific merchant extraction as a fallback when generic patterns fail.
     *
     * Google Pay:  "₹500 paid to Raj Kumar"  /  "₹1,200 received from Priya Sharma"
     * Paytm:       "Paid Rs.200 to Merchant Store via Paytm UPI"
     * PhonePe:     "₹800 paid to Swiggy"  /  "Received ₹500 from Rahul"
     * CRED:        "Rs.5,000 paid towards HDFC credit card bill via CRED"
     * Amazon Pay:  "₹899 paid for Amazon order"
     */
    private fun extractAppSpecificMerchant(text: String, appName: String): String? {
        val lower = text.lowercase(Locale.US)

        when (appName) {
            "Google Pay" -> {
                // "Payment of ₹350 to Starbucks Coffee successful"
                val paymentOf = Pattern.compile(
                    """payment of\s+(?:₹|Rs\.?\s*|INR\s*)[\d,]+\.?\d*\s+to\s+(.+?)(?:\s+successful|\.|\s+via|$)""",
                    Pattern.CASE_INSENSITIVE
                ).matcher(text)
                if (paymentOf.find()) return paymentOf.group(1)?.trim()
            }
            "Paytm" -> {
                // "Rs.500 added to Paytm Wallet"
                if (lower.contains("added to paytm wallet") || lower.contains("paytm wallet")) {
                    return "Paytm Wallet"
                }
                // "Payment of Rs.150 to 9876543210 successful" — phone number as merchant
                val phonePattern = Pattern.compile(
                    """(?:to)\s+(\d{10})""", Pattern.CASE_INSENSITIVE
                ).matcher(text)
                if (phonePattern.find()) return phonePattern.group(1)
            }
            "CRED" -> {
                // "Rs.5,000 paid towards HDFC credit card bill via CRED"
                val credBill = Pattern.compile(
                    """paid towards\s+(.+?)(?:\s+via|\s+on|\.|\s+using|$)""",
                    Pattern.CASE_INSENSITIVE
                ).matcher(text)
                if (credBill.find()) return credBill.group(1)?.trim()
                // "CRED cashback of Rs.50 credited"
                if (lower.contains("cashback")) return "CRED Cashback"
            }
            "Amazon Pay" -> {
                if (lower.contains("amazon order") || lower.contains("amazon pay")) return "Amazon"
                if (lower.contains("balance") && lower.contains("added")) return "Amazon Pay"
            }
            "PhonePe" -> {
                // "₹300 sent to 9876543210"
                val phonePattern = Pattern.compile(
                    """(?:sent to|paid to)\s+(\d{10})""", Pattern.CASE_INSENSITIVE
                ).matcher(text)
                if (phonePattern.find()) return phonePattern.group(1)
            }
            "WhatsApp Pay" -> {
                // Generic: extract from "paid to X" / "received from X"
                // Already handled by MERCHANT_PATTERNS above
            }
        }
        return null
    }

    // ── Promotional filter ──────────────────────────────────────────────

    /**
     * Returns true if the notification text looks like a promotional push
     * rather than a real transaction confirmation.
     */
    private fun isPromotionalNotification(text: String): Boolean {
        val lower = text.lowercase(Locale.US)
        // If it has a clear transaction amount pattern AND a debit/credit keyword, it's likely real
        val hasAmount = NOTIF_AMOUNT_PATTERNS.any { it.matcher(text).find() }
        val hasTransactionVerb = DEBIT_NOTIF_KEYWORDS.any { lower.contains(it) } ||
                CREDIT_NOTIF_KEYWORDS.any { lower.contains(it) }

        if (hasAmount && hasTransactionVerb) return false

        // Otherwise check for promo keywords
        return PROMO_KEYWORDS.any { lower.contains(it) }
    }

    // ── Worker trigger ──────────────────────────────────────────────────

    /**
     * Enqueue a one-time [NotificationFusionWorker] to promote stale tentative entries.
     */
    private fun triggerFusionWorker(context: Context) {
        try {
            val request = OneTimeWorkRequestBuilder<NotificationFusionWorker>()
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .setInitialDelay(5, java.util.concurrent.TimeUnit.MINUTES)
                .addTag("notification_fusion")
                .build()
            WorkManager.getInstance(context).enqueue(request)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to enqueue NotificationFusionWorker", e)
        }
    }
}
