package com.app.data.sms

import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.time.ZoneId
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SMS Classification result
 */
enum class SmsClassification {
    TRANSACTION,
    PROMOTIONAL,
    OTP,
    SERVICE,
    IGNORED
}

/**
 * Rich parsed SMS result with all extracted financial fields.
 */
data class ParsedSmsTransaction(
    val amount: Double,
    val type: String,             // "debit" or "credit"
    val category: String,
    val method: String,
    val merchant: String?,
    val upiId: String?,
    val referenceNumber: String?,
    val accountLast4: String?,
    val availableBalance: Double?,
    val transactionDate: Long,    // epoch millis — best date (body > sms timestamp)
    val smsDate: Long,            // original SMS timestamp from Android
    val sender: String,
    val rawSms: String,
    val isRecurring: Boolean = false,
    val isReversal: Boolean = false,
    val isPreDebitMandate: Boolean = false
)

/**
 * Production-grade SMS transaction parser (BudGi Precision Intelligent Heuristics).
 *
 * Designed for Indian bank SMS formats (HDFC, SBI, BOB, ICICI, Axis, Kotak, etc.)
 */
@Singleton
class SmsTransactionParser @Inject constructor(
    private val categoryClassifier: TransactionCategoryClassifier,
    private val bankTemplateEngine: BankSmsTemplateEngine,
    private val upiRegistry: UpiMerchantRegistry
) {
    // ── Amount patterns ─────────────────────────────────────────────────
    private val amountPatterns = listOf(
        Pattern.compile("""(?:Rs\.?|INR|₹)\s*([\d,]+\.?\d*)""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""(?:amt|amount|txn\s*amt|trx\s*amt|trx)[:\s]*(?:Rs\.?|INR|₹)?\s*([\d,]+\.?\d*)""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""(?:trx|transaction)\s+(?:of|for)\s+(?:Rs\.?|INR|₹)?\s*([\d,]+\.?\d*)""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""(?:debited|credited|transferred|withdrawn|spent|paid)\s*(?:by|with|for)?\s*(?:Rs\.?|INR|₹)?\s*([\d,]+\.?\d*)""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""([\d,]+\.?\d*)\s*(?:debited|credited|paid|received|spent|withdrawn)""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""(?:Rs\.?|INR|₹)\s*([\d,]+)""", Pattern.CASE_INSENSITIVE)
    )

    // Direct debit patterns prioritizing the actual deducted amount over mandate limits/plans
    private val directDebitAmountPatterns = listOf(
        Pattern.compile("""(?:debited|paid|spent|withdrawn|dr\.?)\s*(?:by|with|for|of)?\s*(?:Rs\.?|INR|₹)?\s*([\d,]+\.?\d*)""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""(?:Rs\.?|INR|₹)\s*([\d,]+\.?\d*)\s*(?:has\s+been\s+)?(?:debited|paid|withdrawn|spent|dr\.?)""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""(?:debited\s+towards\s+(?:authentication|authorization|verification|mandate))\s*(?:of)?\s*(?:Rs\.?|INR|₹)?\s*([\d,]+\.?\d*)""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""(?:authentication|authorization|verification)\s*(?:fee|charge|amount)?\s*(?:of)?\s*(?:Rs\.?|INR|₹)?\s*([\d,]+\.?\d*)""", Pattern.CASE_INSENSITIVE)
    )

    // ── Reference number ────────────────────────────────────────────────
    private val refPatterns = listOf(
        Pattern.compile("""(?:ref\s*no|ref|txn\s*id|txn\s*no|txn|upi\s*ref|imps\s*ref)[:\s-]*(\d{8,22})""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""from:CRA/(\d{10,22})""", Pattern.CASE_INSENSITIVE) // Bank of Baroda pattern
    )

    // ── UPI ID ──────────────────────────────────────────────────────────
    private val upiIdPattern = Pattern.compile(
        """([a-zA-Z0-9._-]+@[a-zA-Z]{2,20})""",
        Pattern.CASE_INSENSITIVE
    )

    // ── Account number (last 4-6 digits) ────────────────────────────────
    private val accountPatterns = listOf(
        Pattern.compile("""(?:a/c|acct|ac|account)\s*(?:no\.?\s*)?[-:\s]*[*Xx.]+\s*(\d{4,6})""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""(?:a/c|acct|ac|account)\s*(?:no\.?\s*)?[-:\s]*X+(\d{4,6})""", Pattern.CASE_INSENSITIVE), // e.g. A/c X7933
        Pattern.compile("""(?:a/c|acct|ac|account)\s*(?:no\.?\s*)?[-:\s]*x+(\d{4,6})""", Pattern.CASE_INSENSITIVE) // e.g. A/C xx1052
    )

    // ── Available balance ───────────────────────────────────────────────
    private val balancePattern = Pattern.compile(
        """(?:avl\s*bal|avlbl\s*amt|avlbl|available\s*balance|bal|avlbal|total\s*bal|balance)\s*(?:is|:|-)?\s*(?:rs\.?|inr|₹)?\s*[:\s]*([\d,]+\.?\d*)(?:cr|dr)?""",
        Pattern.CASE_INSENSITIVE
    )

    // ── Date from SMS body (Multi-Bank Triangulation) ───────────────────
    private val bodyDatePatterns = listOf(
        // 1. Bank of Baroda / ISO: (2026:03:31 02:43:21) or (2026-03-31 02:43:21)
        Pattern.compile("""\(?(\d{4})[:/-](\d{2})[:/-](\d{2})\s+(\d{2}):(\d{2}):(\d{2})\)?"""),
        // 2. HDFC / Kotak / PNB: 18-OCT-26 14:23:45 or 18-Oct-2026 14:23:45 or 18 Oct 2026 14:23
        Pattern.compile("""(\d{1,2})[-/\s]([A-Za-z]{3})[-/\s](\d{2,4})\s+(?:at\s*)?(\d{1,2}):(\d{2})(?::(\d{2}))?""", Pattern.CASE_INSENSITIVE),
        // 3. ICICI 12-hr format: at 02:23 PM on 18-Oct-26 or on 18-Oct-26 at 02:23 PM
        Pattern.compile("""(?:at\s*)?(\d{1,2}):(\d{2})(?::(\d{2}))?\s*(AM|PM)\s*(?:on\s*)?(\d{1,2})[-/\s]([A-Za-z]{3})[-/\s](\d{2,4})""", Pattern.CASE_INSENSITIVE),
        Pattern.compile("""(?:on\s*)?(\d{1,2})[-/\s]([A-Za-z]{3})[-/\s](\d{2,4})\s*at\s*(\d{1,2}):(\d{2})(?::(\d{2}))?\s*(AM|PM)""", Pattern.CASE_INSENSITIVE),
        // 4. Numeric dd-MM-yyyy or dd/MM/yyyy or dd.MM.yyyy with optional time (24-hr or 12-hr)
        Pattern.compile("""(\d{1,2})[-/. ](\d{1,2})[-/. ](\d{4}|\d{2})(?:\s+(?:at\s*)?(\d{1,2}):(\d{2})(?::(\d{2}))?(?:\s*(AM|PM))?)?""", Pattern.CASE_INSENSITIVE),
        // 5. Compact SBI: 18Oct26 14:23:45 or 18Oct26 or 18Oct2026
        Pattern.compile("""(\d{1,2})([A-Za-z]{3})(\d{4}|\d{2})(?:\s+(?:at\s*)?(\d{1,2}):(\d{2})(?::(\d{2}))?)?""", Pattern.CASE_INSENSITIVE),
        // 6. Named date with year, no time: 18 Oct 2026 or 18-Oct-26
        Pattern.compile("""(\d{1,2})\s*(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\s*'?(\d{2,4})""", Pattern.CASE_INSENSITIVE),
        // 7. Named date missing year: 18 Oct
        Pattern.compile("""(\d{1,2})\s+(Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)\b(?!\s*'?\d{2,4})""", Pattern.CASE_INSENSITIVE)
    )

    // ── Duplicate tracking ──────────────────────────────────────────────
    private val processedHashes = ConcurrentHashMap.newKeySet<String>()
    private val recentTimestamps = ConcurrentHashMap<String, Long>()

    /**
     * Parse an SMS body into a ParsedSmsTransaction, or null if not a real transaction.
     */
    fun parse(body: String, sender: String, timestamp: Long): ParsedSmsTransaction? {
        val trimmed = body.trim()
        if (trimmed.length < 15) return null

        // 1. Classify
        val classification = classify(trimmed, sender)
        if (classification != SmsClassification.TRANSACTION) return null

        // 1b. Phase 1: Try Bank-Specific Template Engine first (95%+ accuracy)
        val templateResult = bankTemplateEngine.tryParse(trimmed, sender, timestamp)
        if (templateResult != null) {
            val tAmount = templateResult.amount
            if (tAmount in 1.0..10_000_000.0) {
                val tRefNumber = templateResult.referenceNumber ?: extractRefNumber(trimmed)
                val tDedupKey = if (!tRefNumber.isNullOrBlank()) "ref:$tRefNumber" else computeHash(sender, tAmount, timestamp)
                if (processedHashes.add(tDedupKey)) {
                    val tSenderAmountKey = "${sender.lowercase(Locale.US)}|${"%.2f".format(tAmount)}"
                    val tLastSeen = recentTimestamps[tSenderAmountKey]
                    if (tLastSeen == null || kotlin.math.abs(timestamp - tLastSeen) >= 30_000L) {
                        recentTimestamps[tSenderAmountKey] = timestamp

                        // Phase 2: UPI Registry lookup for clean merchant identity
                        val regInfo = upiRegistry.lookup(templateResult.upiId ?: templateResult.merchant)
                        val finalMerchant = regInfo?.merchantName ?: templateResult.merchant ?: extractMerchant(trimmed, templateResult.upiId)
                        val finalCategory = regInfo?.category ?: categoryClassifier.classify(
                            message = trimmed,
                            upiId = templateResult.upiId,
                            merchant = finalMerchant
                        )
                        val bodyDate = extractDateFromBody(trimmed, timestamp)
                        val txDate = bodyDate ?: timestamp

                        val isMandate = templateResult.isPreDebitMandate || BankSmsTemplateEngine.isPreDebitMandate(trimmed)
                        val isRev = templateResult.isReversal || BankSmsTemplateEngine.isReversalOrRefund(trimmed)

                        return ParsedSmsTransaction(
                            amount = tAmount,
                            type = if (isRev) "credit" else templateResult.type,
                            category = finalCategory,
                            method = templateResult.method,
                            merchant = finalMerchant,
                            upiId = templateResult.upiId,
                            referenceNumber = tRefNumber,
                            accountLast4 = templateResult.accountLast4,
                            availableBalance = templateResult.availableBalance,
                            transactionDate = txDate,
                            smsDate = timestamp,
                            sender = sender,
                            rawSms = trimmed.take(500),
                            isRecurring = detectRecurring(trimmed.lowercase(Locale.US), finalMerchant, finalCategory),
                            isReversal = isRev,
                            isPreDebitMandate = isMandate
                        )
                    }
                }
            }
        }

        // 2. Extract amount
        val amount = extractAmount(trimmed) ?: return null
        if (amount < 1.0 || amount > 10_000_000.0) return null

        // 3. Extract reference number (primary dedup key)
        val refNumber = extractRefNumber(trimmed)

        // 4. Dedup check using reference number or hash
        val dedupKey = if (!refNumber.isNullOrBlank()) {
            "ref:$refNumber"
        } else {
            computeHash(sender, amount, timestamp)
        }
        if (!processedHashes.add(dedupKey)) return null

        // Secondary dedup: same amount + same sender within 30 seconds
        val senderAmountKey = "${sender.lowercase(Locale.US)}|${"%.2f".format(amount)}"
        val lastSeen = recentTimestamps[senderAmountKey]
        if (lastSeen != null && kotlin.math.abs(timestamp - lastSeen) < 30_000L) {
            return null
        }
        recentTimestamps[senderAmountKey] = timestamp

        // 5. Determine transaction type (context-aware heuristic)
        val type = detectTransactionType(trimmed) ?: return null

        // 6. Extract other fields
        val upiId = extractUpiId(trimmed)
        val accountLast4 = extractAccountNumber(trimmed)
        val balance = extractBalance(trimmed)
        val bodyDate = extractDateFromBody(trimmed, timestamp)

        // 7. Determine payment method
        val normalized = trimmed.lowercase(Locale.US)
        val method = detectPaymentMethod(normalized)

        // 8. Extract merchant
        val merchant = extractMerchant(trimmed, upiId)

        // 9. Classify category
        val category = categoryClassifier.classify(normalized, upiId, merchant)

        // 10. Detect recurring cadence / mandates / subscriptions
        val isRecurring = detectRecurring(normalized, merchant, category)

        // 11. Best date
        val transactionDate = bodyDate ?: timestamp

        val isMandateFallback = BankSmsTemplateEngine.isPreDebitMandate(trimmed)
        val isRevFallback = BankSmsTemplateEngine.isReversalOrRefund(trimmed)

        return ParsedSmsTransaction(
            amount = amount,
            type = if (isRevFallback) "credit" else type,
            category = category,
            method = method,
            merchant = merchant,
            upiId = upiId,
            referenceNumber = refNumber,
            accountLast4 = accountLast4,
            availableBalance = balance,
            transactionDate = transactionDate,
            smsDate = timestamp,
            sender = sender,
            rawSms = trimmed.take(500),
            isRecurring = isRecurring,
            isReversal = isRevFallback,
            isPreDebitMandate = isMandateFallback
        )
    }

    /**
     * Classify an SMS message.
     */
    fun classify(body: String, sender: String): SmsClassification {
        val text = body.lowercase(Locale.US)

        // OTP — highest priority rejection
        if (OTP_KEYWORDS.any { text.contains(it) }) return SmsClassification.OTP

        // Rejections: Failed, declined, unsuccessful transactions (unless refund/reversal)
        if (FAILED_KEYWORDS.any { text.contains(it) } && !text.contains("refund") && !text.contains("reversed")) {
            return SmsClassification.SERVICE
        }

        // Cancellation / revocation notices (always service, never an active debit)
        if (text.contains("cancelled") || text.contains("canceled") || text.contains("revoked") || text.contains("deregistered")) {
            if (text.contains("mandate") || text.contains("autopay") || text.contains("si") || text.contains("card")) {
                return SmsClassification.SERVICE
            }
        }

        // Mandate / Autopay registration, setup, or scheduled debit reminders
        if (MANDATE_SERVICE_KEYWORDS.any { text.contains(it) }) {
            // Only classify as TRANSACTION if money was actually debited at this moment (e.g. ₹1 authorization charge)
            if (!hasExplicitDebitAction(text)) {
                return SmsClassification.SERVICE
            }
        }

        // Promotional — but only if no strong transaction signal
        if (hasStrongPromoSignal(text) && !hasStrongTransactionSignal(text)) {
            return SmsClassification.PROMOTIONAL
        }

        // Service messages without amounts or without actual debit/credit confirmation
        if (SERVICE_KEYWORDS.any { text.contains(it) } && !hasExplicitDebitAction(text) && !text.contains("credited")) {
            return SmsClassification.SERVICE
        }

        // Must have BOTH amount AND transaction action
        if (hasTransactionAmount(body) && hasStrongTransactionSignal(text)) {
            return SmsClassification.TRANSACTION
        }

        return SmsClassification.IGNORED
    }

    /**
     * Fast local heuristic to detect potential smishing (fraud) messages.
     * Use this as a pre-filter before calling the expensive LLM backend API.
     */
    fun isPotentialScam(body: String): Boolean {
        val lower = body.lowercase(Locale.US)
        return SCAM_KEYWORDS.any { lower.contains(it) } &&
               // Ignore clear legitimate bank alerts that might contain these words (e.g. "card blocked successfully")
               !lower.contains("successfully") && !lower.contains("requested")
    }

    // ── Private helpers ─────────────────────────────────────────────────

    private fun extractAmount(body: String): Double? {
        val lower = body.lowercase(Locale.US)
        val isMandateContext = lower.contains("mandate") || lower.contains("autopay") || lower.contains("standing instruction") || lower.contains("limit")

        // In a mandate or autopay setup message, prioritize the explicitly debited amount (e.g. ₹1 auth charge)
        if (isMandateContext) {
            for (pattern in directDebitAmountPatterns) {
                val matcher = pattern.matcher(body)
                if (matcher.find()) {
                    val raw = matcher.group(1)?.replace(",", "")?.toDoubleOrNull()
                    if (raw != null && raw > 0) return raw
                }
            }
        }

        for (pattern in amountPatterns) {
            val matcher = pattern.matcher(body)
            while (matcher.find()) {
                val start = matcher.start()
                val preContext = lower.substring(maxOf(0, start - 30), start)
                val isBalance = preContext.contains("bal") || preContext.contains("balance") || preContext.contains("avl")
                val isMandateLimit = isMandateContext && (
                    preContext.contains("mandate of") || preContext.contains("mandate for") ||
                    preContext.contains("autopay of") || preContext.contains("autopay for") ||
                    preContext.contains("limit of") || preContext.contains("max limit") ||
                    preContext.contains("maximum of") || preContext.contains("upto") || preContext.contains("up to")
                )
                if (!isBalance && !isMandateLimit) {
                    val raw = matcher.group(1)?.replace(",", "")?.toDoubleOrNull()
                    if (raw != null && raw > 0) return raw
                }
            }
        }

        // Fallback for direct debit
        for (pattern in directDebitAmountPatterns) {
            val matcher = pattern.matcher(body)
            if (matcher.find()) {
                val raw = matcher.group(1)?.replace(",", "")?.toDoubleOrNull()
                if (raw != null && raw > 0) return raw
            }
        }

        return null
    }

    private fun extractRefNumber(body: String): String? {
        for (pattern in refPatterns) {
            val matcher = pattern.matcher(body)
            if (matcher.find()) {
                return matcher.group(1)
            }
        }
        return null
    }

    private fun extractUpiId(body: String): String? {
        val matcher = upiIdPattern.matcher(body)
        if (matcher.find()) {
            val id = matcher.group(1)
            if (id != null && !id.contains("gmail") && !id.contains("yahoo")
                && !id.contains("hotmail") && !id.contains("outlook")
                && id.length >= 5 && id.length <= 50
            ) {
                return id.lowercase(Locale.US)
            }
        }
        return null
    }

    private fun extractAccountNumber(body: String): String? {
        for (pattern in accountPatterns) {
            val matcher = pattern.matcher(body)
            if (matcher.find()) {
                return matcher.group(1)
            }
        }
        // Fallback for "A/c ...1052"
        val matcher2 = Pattern.compile("""a/c\s*\.{3}(\d{4,6})""", Pattern.CASE_INSENSITIVE).matcher(body)
        if (matcher2.find()) {
            return matcher2.group(1)
        }
        return null
    }

    private fun extractBalance(body: String): Double? {
        val matcher = balancePattern.matcher(body)
        if (matcher.find()) {
            return matcher.group(1)?.replace(",", "")?.toDoubleOrNull()
        }
        // Fallback for BOB format: "Total Bal:Rs.75CR."
        val matcher2 = Pattern.compile("""Total\s*Bal[:\s]*(?:Rs\.?|INR|₹)?\s*([\d,]+\.?\d*)""", Pattern.CASE_INSENSITIVE).matcher(body)
        if (matcher2.find()) {
            return matcher2.group(1)?.replace(",", "")?.toDoubleOrNull()
        }
        return null
    }

    private fun parseMonthIndex(monthStr: String): Int? {
        val lower = monthStr.lowercase(Locale.US).trim()
        return when {
            lower.startsWith("jan") -> 0
            lower.startsWith("feb") -> 1
            lower.startsWith("mar") -> 2
            lower.startsWith("apr") -> 3
            lower.startsWith("may") -> 4
            lower.startsWith("jun") -> 5
            lower.startsWith("jul") -> 6
            lower.startsWith("aug") -> 7
            lower.startsWith("sep") -> 8
            lower.startsWith("oct") -> 9
            lower.startsWith("nov") -> 10
            lower.startsWith("dec") -> 11
            else -> null
        }
    }

    private fun buildTriangulatedCalendar(
        year: Int,
        month: Int,
        day: Int,
        hour: Int?,
        minute: Int?,
        second: Int?,
        smsTimestamp: Long,
        smsZone: ZoneId
    ): Long? {
        val cal = java.util.Calendar.getInstance(TimeZone.getTimeZone(smsZone))
        cal.timeInMillis = smsTimestamp

        val finalYear = if (year < 100) year + 2000 else year
        val hasExplicitTime = hour != null && minute != null

        // If time is missing from body, inherit hour/min/sec from SMS arrival (prevents 00:00 midnight bug)
        val finalHour = if (hasExplicitTime) hour else cal.get(java.util.Calendar.HOUR_OF_DAY)
        val finalMinute = if (hasExplicitTime) minute else cal.get(java.util.Calendar.MINUTE)
        val finalSecond = if (hasExplicitTime) (second ?: 0) else cal.get(java.util.Calendar.SECOND)
        val finalMs = if (hasExplicitTime) 0 else cal.get(java.util.Calendar.MILLISECOND)

        if (day in 1..31 && month in 0..11 && finalYear in 2020..2032) {
            cal.set(finalYear, month, day, finalHour, finalMinute, finalSecond)
            cal.set(java.util.Calendar.MILLISECOND, finalMs)
            val date = cal.time
            if (isReasonableDate(date)) return cal.timeInMillis
        }
        return null
    }

    private fun extractDateFromBody(body: String, smsTimestamp: Long): Long? {
        val smsZone = ZoneId.systemDefault()
        val smsYear = java.time.Instant.ofEpochMilli(smsTimestamp).atZone(smsZone).year

        // 1. Bank of Baroda / ISO: (yyyy:MM:dd HH:mm:ss) or (yyyy-MM-dd HH:mm:ss)
        val ymdMatcher = bodyDatePatterns[0].matcher(body)
        if (ymdMatcher.find()) {
            try {
                val year = ymdMatcher.group(1)!!.toInt()
                val month = ymdMatcher.group(2)!!.toInt() - 1
                val day = ymdMatcher.group(3)!!.toInt()
                val hour = ymdMatcher.group(4)!!.toInt()
                val min = ymdMatcher.group(5)!!.toInt()
                val sec = ymdMatcher.group(6)!!.toInt()
                val res = buildTriangulatedCalendar(year, month, day, hour, min, sec, smsTimestamp, smsZone)
                if (res != null) return res
            } catch (_: Exception) { }
        }

        // 2. HDFC / Kotak / PNB: 18-OCT-26 14:23:45 or 18 Oct 2026 at 14:23
        val hdfcMatcher = bodyDatePatterns[1].matcher(body)
        if (hdfcMatcher.find()) {
            try {
                val day = hdfcMatcher.group(1)!!.toInt()
                val month = parseMonthIndex(hdfcMatcher.group(2)!!)
                val year = hdfcMatcher.group(3)!!.toInt()
                val hour = hdfcMatcher.group(4)?.toIntOrNull()
                val min = hdfcMatcher.group(5)?.toIntOrNull()
                val sec = hdfcMatcher.group(6)?.toIntOrNull()
                if (month != null) {
                    val res = buildTriangulatedCalendar(year, month, day, hour, min, sec, smsTimestamp, smsZone)
                    if (res != null) return res
                }
            } catch (_: Exception) { }
        }

        // 3. ICICI 12-hr AM/PM: at 02:23 PM on 18-Oct-26
        val iciciMatcher1 = bodyDatePatterns[2].matcher(body)
        if (iciciMatcher1.find()) {
            try {
                var hour = iciciMatcher1.group(1)!!.toInt()
                val min = iciciMatcher1.group(2)!!.toInt()
                val sec = iciciMatcher1.group(3)?.toIntOrNull() ?: 0
                val ampm = iciciMatcher1.group(4)?.uppercase(Locale.US)
                if (ampm == "PM" && hour < 12) hour += 12
                if (ampm == "AM" && hour == 12) hour = 0

                val day = iciciMatcher1.group(5)!!.toInt()
                val month = parseMonthIndex(iciciMatcher1.group(6)!!)
                val year = iciciMatcher1.group(7)!!.toInt()
                if (month != null) {
                    val res = buildTriangulatedCalendar(year, month, day, hour, min, sec, smsTimestamp, smsZone)
                    if (res != null) return res
                }
            } catch (_: Exception) { }
        }

        // 3b. ICICI 12-hr AM/PM: on 18-Oct-26 at 02:23 PM
        val iciciMatcher2 = bodyDatePatterns[3].matcher(body)
        if (iciciMatcher2.find()) {
            try {
                val day = iciciMatcher2.group(1)!!.toInt()
                val month = parseMonthIndex(iciciMatcher2.group(2)!!)
                val year = iciciMatcher2.group(3)!!.toInt()

                var hour = iciciMatcher2.group(4)!!.toInt()
                val min = iciciMatcher2.group(5)!!.toInt()
                val sec = iciciMatcher2.group(6)?.toIntOrNull() ?: 0
                val ampm = iciciMatcher2.group(7)?.uppercase(Locale.US)
                if (ampm == "PM" && hour < 12) hour += 12
                if (ampm == "AM" && hour == 12) hour = 0

                if (month != null) {
                    val res = buildTriangulatedCalendar(year, month, day, hour, min, sec, smsTimestamp, smsZone)
                    if (res != null) return res
                }
            } catch (_: Exception) { }
        }

        // 4. Numeric dd-MM-yyyy or dd/MM/yyyy or dd.MM.yyyy
        val numMatcher = bodyDatePatterns[4].matcher(body)
        if (numMatcher.find()) {
            try {
                val day = numMatcher.group(1)!!.toInt()
                val month = numMatcher.group(2)!!.toInt() - 1
                val year = numMatcher.group(3)!!.toInt()

                var hour = numMatcher.group(4)?.toIntOrNull()
                val min = numMatcher.group(5)?.toIntOrNull()
                val sec = numMatcher.group(6)?.toIntOrNull()
                val ampm = numMatcher.group(7)?.uppercase(Locale.US)
                if (hour != null && ampm != null) {
                    if (ampm == "PM" && hour < 12) hour += 12
                    if (ampm == "AM" && hour == 12) hour = 0
                }

                val res = buildTriangulatedCalendar(year, month, day, hour, min, sec, smsTimestamp, smsZone)
                if (res != null) return res
            } catch (_: Exception) { }
        }

        // 5. Compact SBI: 18Oct26 14:23:45 or 18Oct26
        val sbiMatcher = bodyDatePatterns[5].matcher(body)
        if (sbiMatcher.find()) {
            try {
                val day = sbiMatcher.group(1)!!.toInt()
                val month = parseMonthIndex(sbiMatcher.group(2)!!)
                val year = sbiMatcher.group(3)!!.toInt()
                val hour = sbiMatcher.group(4)?.toIntOrNull()
                val min = sbiMatcher.group(5)?.toIntOrNull()
                val sec = sbiMatcher.group(6)?.toIntOrNull()
                if (month != null) {
                    val res = buildTriangulatedCalendar(year, month, day, hour, min, sec, smsTimestamp, smsZone)
                    if (res != null) return res
                }
            } catch (_: Exception) { }
        }

        // 6. Named date with year, no time: 18 Oct 2026
        val namedMatcher = bodyDatePatterns[6].matcher(body)
        if (namedMatcher.find()) {
            try {
                val day = namedMatcher.group(1)!!.toInt()
                val month = parseMonthIndex(namedMatcher.group(2)!!)
                val year = namedMatcher.group(3)!!.toInt()
                if (month != null) {
                    val res = buildTriangulatedCalendar(year, month, day, null, null, null, smsTimestamp, smsZone)
                    if (res != null) return res
                }
            } catch (_: Exception) { }
        }

        // 7. Named date missing year: 18 Oct (inherits year from SMS arrival)
        val missingYearMatcher = bodyDatePatterns[7].matcher(body)
        if (missingYearMatcher.find()) {
            try {
                val day = missingYearMatcher.group(1)!!.toInt()
                val month = parseMonthIndex(missingYearMatcher.group(2)!!)
                if (month != null) {
                    val res = buildTriangulatedCalendar(smsYear, month, day, null, null, null, smsTimestamp, smsZone)
                    if (res != null) return res
                }
            } catch (_: Exception) { }
        }

        return null
    }

    private fun isReasonableDate(date: Date): Boolean {
        val now = System.currentTimeMillis()
        val sixMonthsAgo = now - (180L * 24 * 60 * 60 * 1000)
        val oneDayAhead = now + (24L * 60 * 60 * 1000)
        return date.time in sixMonthsAgo..oneDayAhead
    }

    /**
     * Context-aware transaction type detection.
     * Replaces simple keyword matching with relative proximity heuristic.
     */
    private fun detectTransactionType(body: String): String? {
        val lower = body.lowercase(Locale.US)
        
        // 1. Explicit combined contexts (highest priority)
        if (lower.contains("salary credited") || lower.contains("interest credited") || lower.contains("cashback") || lower.contains("reversed") || lower.contains("refund") || lower.contains("refunded")) return "credit"
        if (lower.contains("auto-debit") || lower.contains("auto debit") || lower.contains("mandate debit")) return "debit"
        if (lower.contains("dr. from a/c") && lower.contains("cr. to")) return "debit"
        if (lower.contains("dr. from") || lower.contains("debited from") || lower.contains("withdrawn from")) return "debit"
        if (lower.contains("cr. in a/c") || lower.contains("credited to")) return "credit"
        
        // 2. Proximity-based detection
        // Find the amount index in the string
        var amountIdx = -1
        for (pattern in amountPatterns) {
            val matcher = pattern.matcher(lower)
            if (matcher.find()) {
                amountIdx = matcher.start()
                break
            }
        }
        
        if (amountIdx != -1) {
            // Check window around amount (80 chars before and after)
            val windowStart = maxOf(0, amountIdx - 80)
            val windowEnd = minOf(lower.length, amountIdx + 80)
            val window = lower.substring(windowStart, windowEnd)
            
            val debitIdx = DEBIT_KEYWORDS.map { window.indexOf(it) }.filter { it != -1 }.minOrNull() ?: Int.MAX_VALUE
            val creditIdx = CREDIT_KEYWORDS.map { window.indexOf(it) }.filter { it != -1 }.minOrNull() ?: Int.MAX_VALUE
            
            if (debitIdx < creditIdx) return "debit"
            if (creditIdx < debitIdx) return "credit"
        }
        
        // 3. Fallback to simple presence
        if (DEBIT_KEYWORDS.any { lower.contains(it) }) return "debit"
        if (CREDIT_KEYWORDS.any { lower.contains(it) }) return "credit"

        return null
    }

    private fun detectPaymentMethod(normalized: String): String {
        return when {
            normalized.contains("upi") || normalized.contains("@") -> "UPI"
            normalized.contains("neft") -> "NEFT"
            normalized.contains("rtgs") -> "RTGS"
            normalized.contains("imps") -> "IMPS"
            normalized.contains("atm") -> "ATM"
            normalized.contains("card") || normalized.contains("pos") -> "Card"
            normalized.contains("wallet") || normalized.contains("paytm") || normalized.contains("phonepe") -> "Wallet"
            else -> "Bank Transfer"
        }
    }

    /**
     * Extract merchant name with enhanced heuristics.
     */
    private fun extractMerchant(body: String, upiId: String?): String? {
        val lowerBody = body.lowercase(Locale.US)

        // 1. Direct match for known popular Indian merchants
        for ((key, displayName) in KNOWN_MERCHANT_MAP) {
            if (lowerBody.contains(key)) {
                return displayName
            }
        }

        // 2. Extract from UPI ID first
        if (!upiId.isNullOrBlank()) {
            val upiMerchant = extractMerchantFromUpiId(upiId)
            if (upiMerchant != null) return upiMerchant
        }

        // 3. Try structured text patterns
        val toPatterns = listOf(
            // VPA: merchant@bank or to VPA merchant.name@okaxis
            Pattern.compile("""(?:vpa[:\s]+|to\s+vpa\s+)([A-Za-z0-9._-]+@[a-zA-Z]{2,20})""", Pattern.CASE_INSENSITIVE),
            // Info: NEFT/merchant name/ref
            Pattern.compile("""(?:Info[:\s]*NEFT[/-]|NEFT/(?:DR|CR)/\d+/)([A-Za-z0-9\s._-]+?)(?:[/-]|\s+ref|\s*\.|\s*$)""", Pattern.CASE_INSENSITIVE),
            // "Cr. to MERCHANT." or "transfer to MERCHANT Ref" or "towards MERCHANT"
            Pattern.compile("""(?:cr\.?\s+to|paid\s+to|sent\s+to|transferred\s+to|transfer\s+to|towards|thru|through|to)\s+([A-Za-z0-9@._\-]+(?:\s+[A-Za-z0-9@._\-]+)*)(?:\.|\s+ref|\s+on|\s+via|\s+using|\n|$)""", Pattern.CASE_INSENSITIVE),
            // Info: UPI-BLINKIT or Info: UPI/ZEPTO/381283
            Pattern.compile("""(?:Info[:\s]*UPI[/-]|UPI/(?:DR|CR)/\d+/)([A-Za-z0-9\s._-]+?)(?:[/-]|\s+ref|\s*\.|\s*$)""", Pattern.CASE_INSENSITIVE),
            // "transfer from Divyanshu Dixit Ref No"
            Pattern.compile("""(?:from)\s+([A-Z][A-Za-z0-9\s&'.\-]{2,30}?)(?:\s+ref|\s*\.|\s*$)""", Pattern.CASE_INSENSITIVE),
            // "AT POS TID-3074036A/P"
            Pattern.compile("""AT\s+POS\s+([A-Za-z0-9\s\-]+?)(?:,|\.|\s+ref)""", Pattern.CASE_INSENSITIVE)
        )

        for (pattern in toPatterns) {
            val matcher = pattern.matcher(body)
            if (matcher.find()) {
                var merchant = matcher.group(1)?.trim()
                if (merchant != null && merchant.length > 1
                    && !NOISE_WORDS.contains(merchant.lowercase(Locale.US))
                    && !merchant.matches(Regex("""^\d+$"""))
                ) {
                    if (merchant.contains("@")) {
                        val fromVpa = extractMerchantFromUpiId(merchant)
                        if (fromVpa != null) return fromVpa
                    }
                    // Clean up trailing noise words matched greedily
                    merchant = merchant.replace(Regex("""\s+(ref|on|via|txn|at|date|using|upi).*$""", RegexOption.IGNORE_CASE), "")
                    val cleaned = merchant.trim()
                    if (cleaned.isNotBlank() && !NOISE_WORDS.contains(cleaned.lowercase(Locale.US))) {
                        return cleaned.replaceFirstChar { it.uppercase() }
                    }
                }
            }
        }
        return null
    }

    /**
     * Extract a human-readable merchant name from a UPI ID.
     */
    private fun extractMerchantFromUpiId(upiId: String): String? {
        val localPart = upiId.substringBefore("@")
        if (localPart.matches(Regex("""^\d+$"""))) return null
        if (localPart.length < 2) return null

        val cleaned = localPart
            .replace(Regex("""\.(payu|razorpay|payments?|pay|merchant|upi)$""", RegexOption.IGNORE_CASE), "")
            .replace(".", " ")
            .replace("-", " ")
            .replace("_", " ")
            .trim()

        if (cleaned.isBlank() || cleaned.length < 2) return null
        val known = KNOWN_MERCHANT_MAP[cleaned.lowercase(Locale.US)]
        if (known != null) return known
        return cleaned.split(" ").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
    }

    private fun hasTransactionAmount(body: String): Boolean {
        return amountPatterns.any { it.matcher(body).find() }
    }

    private fun hasStrongTransactionSignal(text: String): Boolean {
        return TRANSACTION_ACTION_KEYWORDS.any { text.contains(it) }
    }

    private fun hasExplicitDebitAction(text: String): Boolean {
        val debitVerbs = listOf(
            "debited towards", "debited for", "debited with", "debited by",
            "debited from", "has been debited", "is debited", "amount debited",
            "dr. from", "dr. of", "dr:rs", "dr:inr", "was debited", "paid rs", "paid inr"
        )
        return debitVerbs.any { text.contains(it) }
    }

    private fun hasStrongPromoSignal(text: String): Boolean {
        return PROMO_KEYWORDS.any { text.contains(it) }
    }

    private fun computeHash(sender: String, amount: Double, timestamp: Long): String {
        // Round timestamp to nearest 5 minutes for near-duplicate detection
        val rounded = (timestamp / 300000) * 300000
        val input = "$sender|${"%.2f".format(amount)}|$rounded"
        val md = MessageDigest.getInstance("SHA-256")
        return md.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }.take(20)
    }

    fun clearProcessedHashes() {
        processedHashes.clear()
        recentTimestamps.clear()
    }

    companion object {
        private val KNOWN_MERCHANT_MAP = mapOf(
            "swiggy" to "Swiggy",
            "zomato" to "Zomato",
            "blinkit" to "Blinkit",
            "zepto" to "Zepto",
            "instamart" to "Swiggy Instamart",
            "bigbasket" to "BigBasket",
            "dunzo" to "Dunzo",
            "uber" to "Uber",
            "ola" to "Ola Cabs",
            "rapido" to "Rapido",
            "amazon" to "Amazon",
            "flipkart" to "Flipkart",
            "myntra" to "Myntra",
            "ajio" to "Ajio",
            "nykaa" to "Nykaa",
            "tata cliq" to "Tata CLiQ",
            "tataneu" to "Tata Neu",
            "netflix" to "Netflix",
            "spotify" to "Spotify",
            "prime" to "Amazon Prime",
            "hotstar" to "Disney+ Hotstar",
            "bookmyshow" to "BookMyShow",
            "airtel" to "Airtel",
            "jio" to "Reliance Jio",
            "vodafone" to "Vi",
            "starbucks" to "Starbucks",
            "mcdonald" to "McDonald's",
            "kfc" to "KFC",
            "domino" to "Domino's Pizza",
            "pizza hut" to "Pizza Hut",
            "burger king" to "Burger King",
            "irctc" to "IRCTC",
            "makemytrip" to "MakeMyTrip",
            "goibibo" to "Goibibo",
            "cleartrip" to "Cleartrip",
            "apollo" to "Apollo Pharmacy",
            "pharmeasy" to "PharmEasy",
            "netmeds" to "Netmeds",
            "licious" to "Licious",
            "country delight" to "Country Delight",
            "milkbasket" to "Milkbasket",
            "tata 1mg" to "Tata 1mg",
            "1mg" to "Tata 1mg",
            "healthians" to "Healthians",
            "ixigo" to "ixigo",
            "navi" to "Navi",
            "slice" to "Slice",
            "jupiter" to "Jupiter",
            "fi money" to "Fi Money",
            "samsung" to "Samsung",
            "apple" to "Apple",
            "microsoft" to "Microsoft",
            "linkedin" to "LinkedIn",
            "notion" to "Notion",
            "figma" to "Figma",
            "canva" to "Canva",
            "grammarly" to "Grammarly",
            "github" to "GitHub",
            "chatgpt" to "ChatGPT",
            "openai" to "OpenAI",
            "youtube" to "YouTube",
            "airbnb" to "Airbnb",
            "urban company" to "Urban Company",
            "cult.fit" to "Cult.fit",
            "decathlon" to "Decathlon",
            "lenskart" to "Lenskart",
            "firstcry" to "FirstCry",
            "cred" to "CRED",
            "phonepe" to "PhonePe",
            "paytm" to "Paytm",
            "google pay" to "Google Pay",
            "gpay" to "Google Pay"
        )

        private val TRANSACTION_ACTION_KEYWORDS = listOf(
            "dr.", "dr ", "cr.", "cr ", "dr. from", "cr. to", "cr. in",
            "debited", "credited", "withdrawn", "transferred", "paid", "received", "sent",
            "purchase", "payment", "debit", "credit", "withdrawal", "txn", 
            "transaction", "spent", "deposited", "refund", "a/c", "avlbal", "avl bal", "available balance"
        )

        private val DEBIT_KEYWORDS = listOf(
            "dr.", "dr ", "debited", "debit", "withdrawn", "spent", "paid", "sent",
            "purchase", "payment", "sent to", "transferred to", "withdrawal",
            "debited from", "dr. from", "withdrawn from", "auto-debit", "auto debit",
            "mandate debit", "dr. from a/c"
        )

        private val CREDIT_KEYWORDS = listOf(
            "cr. in", "credited", "received", "deposited", "refund", "reversed", 
            "salary", "salary credited", "interest credited", "cashback",
            "transferred from", "neft from", "imps from", "cr.", "credited to",
            "refunded"
        )

        private val OTP_KEYWORDS = listOf(
            "otp", "one time password", "verification code", "do not share", 
            "don't share", "valid for", "expires in", "one-time", "verify your", 
            "security code", "2fa", "two factor", "login code", "authenticate"
        )

        private val FAILED_KEYWORDS = listOf(
            "transaction failed", "payment failed", "txn failed", "mandate failed",
            "declined", "unsuccessful", "could not be processed", "insufficient balance",
            "timed out", "reversed due to failure"
        )

        private val PROMO_KEYWORDS = listOf(
            "pre-approved", "preapproved", "loan offer", "apply now", "instant loan", 
            "personal loan", "home loan", "credit limit increase", "credit limit", 
            "pre-qualified", "loan amount", "get loan", "loan up to", "loan upto", 
            "emi offer", "0% emi", "no-cost emi", "easy emi", "cashback offer", 
            "reward points", "bonus", "win ", "winner", "lucky", "contest", "prize", 
            "limited time", "limited period", "special offer", "exclusive offer", 
            "hurry", "last chance", "don't miss", "grab now", "offer valid", "offer ends", 
            "use code", "coupon", "promo code", "discount", "% off", "flat off", 
            "subscribe", "upgrade now", "activate now", "unlock", "click here", "click now", 
            "tap here", "bit.ly", "tinyurl", "goo.gl", "download app", "selected for", 
            "congratulations", "congrats", "complimentary", "gift card", "invest now", 
            "insurance plan", "start sip", "tax saving"
        )

        private val MANDATE_SERVICE_KEYWORDS = listOf(
            "mandate created", "mandate registered", "mandate approved", "mandate requested",
            "mandate received", "mandate modified", "mandate set", "mandate scheduled",
            "mandate cancelled", "mandate canceled", "mandate revoked",
            "autopay registered", "autopay set", "autopay scheduled", "autopay created",
            "autopay cancelled", "autopay canceled", "autopay revoked",
            "e-mandate registered", "e-mandate created", "e-mandate scheduled",
            "standing instruction registered", "standing instruction created", "si registered", "si created",
            "is scheduled to be debited", "scheduled to be debited", "scheduled for debit",
            "due for debit", "due for auto-debit", "is due on", "will be debited on",
            "will be debited from", "maintain sufficient balance", "ensure sufficient balance",
            "keep sufficient balance", "maintain balance"
        )

        private val SERVICE_KEYWORDS = listOf(
            "mandate", "standing instruction", "auto-debit registered", "deregistered", 
            "registered for", "linked", "de-linked", "blocked", "unblocked", "activated", 
            "deactivated", "card blocked", "new card", "card dispatched", "statement ready", 
            "statement generated", "bill generated", "minimum due", "bill due", "due date", 
            "kyc", "pan update", "aadhaar", "aadhar", "nominee", "update your", "complete your", 
            "dear customer", "dear user", "important notice"
        )

        private val SCAM_KEYWORDS = listOf(
            "disconnected", "power will be cut", "electricity office", "call this number",
            "kyc suspended", "account suspended", "card is blocked", "pan card blocked",
            "click here to update", "urgent", "immediate action required", "win lottery",
            "prize money", "approved for loan", "click link", "download apk"
        )

        private val NOISE_WORDS = setOf(
            "a", "an", "the", "to", "from", "for", "of", "in", "on",
            "your", "bank", "account", "card", "upi", "neft", "imps",
            "and", "is", "was", "has", "been", "ref", "avlbal"
        )

        private val RECURRING_KEYWORDS = listOf(
            "mandate", "standing instruction", "si processed", "si debited", "auto-debit", "auto debit",
            "autopay", "auto pay", "auto-renewal", "auto renewal", "nach debit", "nach",
            "ach debit", "recurring", "monthly installment", "emi debited", "emi of",
            "sip of", "sip installment", "mutual fund sip"
        )

        private val RECURRING_MERCHANTS = listOf(
            "netflix", "spotify", "prime video", "amazon prime", "hotstar", "disney",
            "youtube premium", "apple.com/bill", "itunes", "icloud", "google one",
            "google *services", "chatgpt", "openai", "coursera", "cult.fit",
            "cultfit", "times prime", "sony liv", "zee5", "jio fiber", "airtel broadband"
        )
    }

    private fun detectRecurring(normalizedText: String, merchant: String?, category: String): Boolean {
        if (category.equals("Subscriptions", ignoreCase = true)) return true
        if (RECURRING_KEYWORDS.any { normalizedText.contains(it) }) return true
        val merchantLower = merchant?.lowercase(Locale.US) ?: ""
        if (merchantLower.isNotBlank() && RECURRING_MERCHANTS.any { merchantLower.contains(it) }) return true
        return false
    }
}
