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
        """^(rzp|payu|ccavenue|billdesk|cashfree|razorpay|paytm|phonepe|gpay|upi|amazonpay|simpl|cred)\*?""",
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
     * Extracts a clean, human-readable display name from a raw payee name or UPI ID.
     * E.g. "ramesh.kumar98@okaxis" → "Ramesh Kumar"
     *      "gupta.brothers.sweets@icici" → "Gupta Brothers Sweets"
     *      "tea.stall.01@paytm" → "Tea Stall"
     */
    fun extractFriendlyName(merchant: String?, upiId: String?): String {
        if (!merchant.isNullOrBlank() && !merchant.equals("merchant", ignoreCase = true) && !merchant.matches(Regex("""^\d+$"""))) {
            val cleaned = merchant.replace(Regex("""\s+(ref|on|via|txn|at|date|using|upi).*$""", RegexOption.IGNORE_CASE), "").trim()
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
