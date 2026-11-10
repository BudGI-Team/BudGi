package com.app.data.sms

import java.util.Locale

/**
 * Smart merchant normalization engine.
 * Cleans merchant names and UPI IDs into a consistent fingerprint
 * for category learning lookups.
 *
 * Examples:
 *   "raj_kirana@ybl"        → "rajkirana"
 *   "RAJ KIRANA 123@paytm"  → "rajkirana"
 *   "paytmqr2810050501@paytm" → "paytmqr"
 *   "SWIGGY"                → "swiggy"
 *   "RZP*Zomato"            → "zomato"
 */
object MerchantNormalizer {

    private val UPI_HANDLES = listOf(
        "@ybl", "@paytm", "@okaxis", "@okhdfcbank", "@okicici", "@oksbi",
        "@apl", "@ibl", "@axl", "@upi", "@kotak", "@icici", "@sbi",
        "@hdfcbank", "@axisbank", "@rbl", "@federal", "@idbi", "@boi",
        "@citi", "@indus", "@unionbank", "@pnb", "@canara", "@bob",
        "@allbank", "@hsbc", "@dbs", "@yes", "@sc", "@bandhan",
        "@jupiteraxis", "@freecharge", "@amazonpay", "@slice", "@niyoicici",
    )

    private val BUSINESS_SUFFIXES = Regex(
        """\b(private|pvt|ltd|limited|inc|corp|llp|llc)\b""",
        RegexOption.IGNORE_CASE
    )

    private val GATEWAY_PREFIXES = Regex(
        """^(bil|billdesk|rzp|razorpay|payu|ccavenue|cashfree|pinelabs|airpay|paytm|phonepe|gpay|upi|amazonpay|simpl|cred|pos|tid|info|vpa|to\s+vpa|towards)[*_\-/:\s]+""",
        RegexOption.IGNORE_CASE
    )

    private val TRAILING_NOISE = Regex(
        """\s+(?:ref|ref\s*no|txn|txn\s*id|id|rrn|on|at|via|using|by|date|avlbal|avl|bal|cr|dr|dated)\b.*$""",
        RegexOption.IGNORE_CASE
    )

    private val TERMINAL_LOCATION_NOISE = Regex(
        """(store|branch|terminal|tid|pos|atm)[\s\-_]*[a-zA-Z0-9]*$""",
        RegexOption.IGNORE_CASE
    )

    private val TRAILING_DIGITS = Regex("""\d{4,}$""")
    private val NON_ALPHANUMERIC = Regex("""[^a-z0-9]""")

    /**
     * Normalize a merchant name or UPI ID into a clean fingerprint.
     * Returns empty string if input is null/blank.
     */
    fun normalize(raw: String?): String {
        if (raw.isNullOrBlank()) return ""

        var cleaned = raw.lowercase(Locale.US).trim()

        // Strip UPI handles
        for (handle in UPI_HANDLES) {
            if (cleaned.endsWith(handle)) {
                cleaned = cleaned.substring(0, cleaned.length - handle.length)
                break
            }
        }

        // Preserve QR vendor identity: paytmqr, bharatpe, phonepeqr should keep their unique merchant IDs
        val isQrMerchant = cleaned.startsWith("paytmqr") || cleaned.startsWith("bharatpe") || 
                           cleaned.startsWith("phonepeqr") || cleaned.contains("qr")

        if (!isQrMerchant) {
            // Strip Payment Gateway Prefixes only for non-QR vendor handles
            cleaned = GATEWAY_PREFIXES.replace(cleaned, "")
            // Remove trailing long digit sequences (order numbers, ref IDs) for standard merchants
            cleaned = TRAILING_DIGITS.replace(cleaned, "")
        }

        // Strip Location/Terminal Noise
        cleaned = TERMINAL_LOCATION_NOISE.replace(cleaned, "")

        // Remove business suffixes
        cleaned = BUSINESS_SUFFIXES.replace(cleaned, "")

        // Remove underscores, dots, hyphens, spaces
        cleaned = cleaned.replace(Regex("""[_.\-\s]"""), "")

        // Keep only alphanumeric
        cleaned = NON_ALPHANUMERIC.replace(cleaned, "")

        return cleaned.trim()
    }

    /**
     * Strips gateway prefixes like BIL*, BILLDESK*, RAZORPAY*, PAYTM*, PINELABS* from raw merchant string.
     */
    fun stripGatewayPrefix(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        return GATEWAY_PREFIXES.replace(raw.trim(), "").trim()
    }

    /**
     * Extracts a clean, human-readable display name from a raw payee name or UPI ID.
     * E.g. "ramesh.kumar98@okaxis" → "Ramesh Kumar"
     *      "gupta.brothers.sweets@icici" → "Gupta Brothers Sweets"
     *      "tea.stall.01@paytm" → "Tea Stall"
     *      "BIL*NETFLIX" → "Netflix"
     *      "RAZORPAY*SWIGGY" → "Swiggy"
     */
    fun extractFriendlyName(merchant: String?, upiId: String?): String {
        if (!merchant.isNullOrBlank() && !merchant.equals("merchant", ignoreCase = true) && !merchant.matches(Regex("""^\d+$"""))) {
            val deMasked = stripGatewayPrefix(merchant)
            val cleaned = TRAILING_NOISE.replace(deMasked, "").trim()
            val lower = cleaned.lowercase(Locale.US)
            val known = SmsTransactionParser.KNOWN_MERCHANT_MAP[lower]
            if (known != null) return known
            if (cleaned.isNotBlank() && !cleaned.contains("@")) {
                return cleaned.split(" ").filter { it.isNotBlank() }.joinToString(" ") { word ->
                    word.lowercase(Locale.US).replaceFirstChar { it.uppercase() }
                }
            }
        }

        if (!upiId.isNullOrBlank()) {
            val localPart = upiId.substringBefore("@")
            if (!localPart.matches(Regex("""^\d+$"""))) {
                val words = localPart
                    .replace(Regex("""\.(payu|razorpay|payments?|pay|merchant|upi)$""", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("""\d+$"""), "") // Remove trailing numbers (e.g. ramesh98 → ramesh)
                    .replace(Regex("""[._\-]"""), " ")
                    .trim()
                val known = SmsTransactionParser.KNOWN_MERCHANT_MAP[words.lowercase(Locale.US)]
                if (known != null) return known
                if (words.isNotBlank() && words.length >= 2) {
                    return words.split(" ").filter { it.isNotBlank() }.joinToString(" ") { word ->
                        word.lowercase(Locale.US).replaceFirstChar { it.uppercase() }
                    }
                }
            }
        }

        return merchant?.ifBlank { null } ?: upiId ?: "Local Vendor"
    }

    /**
     * Resolves an industry-grade, human-readable display merchant name for any transaction.
     * Guarantees telecommunication short-codes (e.g. "VM-HDFCBK", "AD-SBIINB") are NEVER shown
     * to the user as transaction titles.
     */
    fun getDisplayMerchant(
        alias: String? = null,
        merchant: String? = null,
        upiId: String? = null,
        sender: String = "",
        method: String = "",
        category: String = "",
        type: String = "",
        rawSms: String? = null,
        description: String = ""
    ): String {
        if (!alias.isNullOrBlank()) return alias.trim()

        val candidate = merchant?.takeIf { it.isNotBlank() && !isTelecomSender(it) }
        if (candidate != null) {
            val clean = extractFriendlyName(candidate, upiId)
            if (clean.isNotBlank() && !isTelecomSender(clean)) return clean
        }

        if (!upiId.isNullOrBlank()) {
            val fromVpa = extractFriendlyName(null, upiId)
            if (fromVpa.isNotBlank() && !fromVpa.equals("Local Vendor", ignoreCase = true) && !isTelecomSender(fromVpa)) {
                return fromVpa
            }
        }

        val rawLower = (rawSms ?: description).lowercase(Locale.US)
        val bankName = extractBankName(sender)

        return when {
            method.equals("ATM", ignoreCase = true) || rawLower.contains("atm") -> "Cash Withdrawal (ATM)"
            category.equals("Income", ignoreCase = true) || rawLower.contains("salary") -> "Salary Credit"
            rawLower.contains("interest credited") || rawLower.contains("bank interest") -> "Bank Interest"
            rawLower.contains("refund") || rawLower.contains("reversed") -> "Refund / Reversal"
            rawLower.contains("fastag") || rawLower.contains("toll") -> "FASTag Toll"
            rawLower.contains("recharge") -> "Mobile / DTH Recharge"
            method.equals("UPI", ignoreCase = true) || rawLower.contains("upi") -> "UPI Transfer"
            method.equals("Card", ignoreCase = true) || rawLower.contains("card") || rawLower.contains("pos") -> "Card Payment"
            type.equals("income", ignoreCase = true) || type.equals("credit", ignoreCase = true) -> if (bankName.isNotBlank()) "$bankName Credit" else "Bank Credit"
            else -> if (bankName.isNotBlank()) "$bankName Payment" else "Bank Transfer"
        }
    }

    private fun isTelecomSender(text: String): Boolean {
        val trimmed = text.trim().uppercase(Locale.US)
        return trimmed.matches(Regex("""^[A-Z]{2}-[A-Z0-9]{3,}$""")) ||
               trimmed.matches(Regex("""^[A-Z]{2}[A-Z0-9]{4,}$""")) ||
               trimmed.contains("HDFCBK") || trimmed.contains("SBIINB") ||
               trimmed.contains("ICICIB") || trimmed.contains("AXISBK") ||
               trimmed.contains("BOBTXN") || trimmed.contains("KOTAKB")
    }

    private fun extractBankName(sender: String): String {
        val upper = sender.uppercase(Locale.US)
        return when {
            upper.contains("HDFC") -> "HDFC Bank"
            upper.contains("SBI") -> "State Bank of India"
            upper.contains("ICICI") -> "ICICI Bank"
            upper.contains("AXIS") -> "Axis Bank"
            upper.contains("KOTAK") -> "Kotak Mahindra"
            upper.contains("BOB") || upper.contains("BARODA") -> "Bank of Baroda"
            upper.contains("PNB") -> "Punjab National Bank"
            upper.contains("CANARA") -> "Canara Bank"
            upper.contains("UNION") -> "Union Bank"
            upper.contains("IDFC") -> "IDFC First Bank"
            upper.contains("INDUS") -> "IndusInd Bank"
            upper.contains("YES") -> "Yes Bank"
            upper.contains("PAYTM") -> "Paytm"
            upper.contains("PHONEPE") -> "PhonePe"
            else -> ""
        }
    }

    /**
     * Get the best merchant key to use for learning.
     * Prefers UPI ID over merchant name since it's more specific.
     */
    fun getBestKey(merchant: String?, upiId: String?): String {
        return when {
            !upiId.isNullOrBlank() -> upiId
            !merchant.isNullOrBlank() -> merchant
            else -> ""
        }
    }
}

/**
 * Extension function on domain [Transaction] to always get a clean, human-readable display merchant name.
 */
fun com.app.domain.model.Transaction.getCleanDisplayName(): String =
    MerchantNormalizer.getDisplayMerchant(
        alias = alias,
        merchant = merchant,
        upiId = upiId,
        sender = sender,
        method = method,
        category = category,
        type = type,
        rawSms = rawSms,
        description = description
    )
