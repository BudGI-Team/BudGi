package com.app.data.sms

import java.util.Locale
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Parsed result from a bank-specific SMS template.
 * All fields are optional since not every SMS format includes every field.
 */
data class TemplateParseResult(
    val amount: Double,
    val type: String,              // "debit" or "credit"
    val merchant: String? = null,
    val upiId: String? = null,
    val referenceNumber: String? = null,
    val accountLast4: String? = null,
    val availableBalance: Double? = null,
    val method: String = "Bank Transfer",
    val dateString: String? = null,  // Raw date string from SMS body
    val bankName: String = "",
    val confidence: Double = 0.95,   // Template matches are high confidence
    val isReversal: Boolean = false,
    val isPreDebitMandate: Boolean = false
)

/**
 * A single SMS template: a compiled regex pattern and the group-index
 * mapping that tells the engine which capture group holds which field.
 *
 * @property pattern        Compiled regex that matches a specific bank SMS format.
 * @property type           Transaction type this template represents: "debit" or "credit".
 * @property amountGroup    Capture group index for the transaction amount.
 * @property merchantGroup  Capture group index for the merchant/payee name (nullable).
 * @property upiIdGroup     Capture group index for the UPI VPA (nullable).
 * @property refGroup       Capture group index for the reference/transaction number (nullable).
 * @property accountGroup   Capture group index for the last 4-6 digits of the account (nullable).
 * @property balanceGroup   Capture group index for the available balance (nullable).
 * @property dateGroup      Capture group index for the raw date string (nullable).
 * @property methodHint     Optional explicit method hint (e.g., "ATM", "Card"). If null,
 *                          the engine detects the method from matched text keywords.
 */
data class SmsTemplate(
    val pattern: Pattern,
    val type: String,
    val amountGroup: Int,
    val merchantGroup: Int? = null,
    val upiIdGroup: Int? = null,
    val refGroup: Int? = null,
    val accountGroup: Int? = null,
    val balanceGroup: Int? = null,
    val dateGroup: Int? = null,
    val methodHint: String? = null
)

/**
 * Profile for a single bank: the set of sender short-codes that identify
 * SMS from this bank, plus ordered lists of debit and credit templates.
 *
 * @property bankName        Human-readable bank name (e.g., "HDFC Bank").
 * @property senderPatterns  Uppercase short-code suffixes used in SMS sender addresses
 *                           (e.g., "HDFCBK"). Matched case-insensitively against the sender.
 * @property templates       Ordered list of [SmsTemplate]s to try. First match wins.
 */
data class BankProfile(
    val bankName: String,
    val senderPatterns: List<String>,
    val templates: List<SmsTemplate>
)

/**
 * Bank-specific SMS Template Engine for BudGi.
 *
 * Replaces fragile generic regex with precise, per-bank SMS patterns for
 * all major Indian banks, neo-banks, and payment apps. Each bank's SMS
 * format is captured as a set of [SmsTemplate]s grouped into a [BankProfile].
 *
 * **Priority order:**
 * 1. Bank-specific templates (highest accuracy, ~0.95 confidence)
 * 2. Payment-app sender templates (Paytm, PhonePe, CRED)
 * 3. Returns `null` → caller falls through to generic [SmsTransactionParser]
 *
 * This class is thread-safe and stateless (no mutable state).
 */
@Singleton
class BankSmsTemplateEngine @Inject constructor() {

    // ── All bank profiles ───────────────────────────────────────────────
    private val bankProfiles: List<BankProfile> = buildBankProfiles()
    private val paymentAppProfiles: List<BankProfile> = buildPaymentAppProfiles()

    /**
     * Attempt to parse an SMS using bank-specific templates.
     *
     * @param body      The SMS body text.
     * @param sender    The SMS sender address (e.g., "AD-HDFCBK", "VM-SBIINB").
     * @param timestamp The SMS timestamp in epoch millis (unused here but kept
     *                  for API symmetry with [SmsTransactionParser.parse]).
     * @return A [TemplateParseResult] if a bank template matched, or `null` to
     *         signal the caller should fall through to the generic parser.
     */
    fun tryParse(body: String, sender: String, timestamp: Long): TemplateParseResult? {
        val senderUpper = sender.uppercase(Locale.US)

        // 1. Try bank-specific templates FIRST (highest accuracy)
        for (profile in bankProfiles) {
            if (matchesSender(senderUpper, profile.senderPatterns)) {
                val result = tryTemplates(body, profile)
                if (result != null) return result
            }
        }

        // 2. Payment app senders SECOND
        for (profile in paymentAppProfiles) {
            if (matchesSender(senderUpper, profile.senderPatterns)) {
                val result = tryTemplates(body, profile)
                if (result != null) return result
            }
        }

        // 3. No match → falls through to existing generic parser
        return null
    }

    // ── Sender matching ─────────────────────────────────────────────────

    /**
     * Check if the SMS sender address contains any of the bank's known
     * short-code identifiers. Handles prefixes like "AD-", "VM-", "BZ-",
     * etc., that carriers prepend.
     */
    private fun matchesSender(senderUpper: String, patterns: List<String>): Boolean {
        return patterns.any { code ->
            senderUpper.contains(code.uppercase(Locale.US))
        }
    }

    // ── Template matching ───────────────────────────────────────────────

    /**
     * Try each template in the profile against the SMS body.
     * Returns on first match.
     */
    private fun tryTemplates(body: String, profile: BankProfile): TemplateParseResult? {
        for (template in profile.templates) {
            val matcher = template.pattern.matcher(body)
            if (matcher.find()) {
                val amount = extractGroupAmount(matcher, template.amountGroup) ?: continue
                if (amount < 1.0 || amount > 10_000_000.0) continue

                val merchant = safeGroup(matcher, template.merchantGroup)?.trim()
                val upiId = safeGroup(matcher, template.upiIdGroup)?.trim()
                val ref = safeGroup(matcher, template.refGroup)?.trim()
                val account = safeGroup(matcher, template.accountGroup)?.trim()
                val balance = extractGroupAmount(matcher, template.balanceGroup)
                val dateStr = safeGroup(matcher, template.dateGroup)?.trim()

                // Detect method from template hint or from matched body text
                val method = template.methodHint
                    ?: detectMethodFromBody(body)

                val isMandate = isPreDebitMandate(body)
                val isRev = isReversalOrRefund(body)

                return TemplateParseResult(
                    amount = amount,
                    type = if (isRev) "credit" else template.type,
                    merchant = cleanMerchant(merchant),
                    upiId = cleanUpiId(upiId),
                    referenceNumber = ref,
                    accountLast4 = account,
                    availableBalance = balance,
                    method = method,
                    dateString = dateStr,
                    bankName = profile.bankName,
                    confidence = 0.95,
                    isReversal = isRev,
                    isPreDebitMandate = isMandate
                )
            }
        }
        return null
    }

    // ── Field helpers ───────────────────────────────────────────────────

    private fun extractGroupAmount(
        matcher: java.util.regex.Matcher,
        groupIndex: Int?
    ): Double? {
        if (groupIndex == null) return null
        val raw = safeGroup(matcher, groupIndex) ?: return null
        return raw.replace(",", "").replace(" ", "").toDoubleOrNull()
    }

    private fun safeGroup(matcher: java.util.regex.Matcher, index: Int?): String? {
        if (index == null) return null
        return try {
            matcher.group(index)
        } catch (_: Exception) {
            null
        }
    }

    private fun cleanMerchant(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw
            .replace(Regex("""^\s*[./\\-]+"""), "")
            .replace(Regex("""\s*[./\\-]+\s*$"""), "")
            .trim()
        if (cleaned.isBlank() || cleaned.length < 2) return null
        // Don't return purely numeric merchants
        if (cleaned.matches(Regex("""^\d+$"""))) return null
        return cleaned
    }

    private fun cleanUpiId(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim().lowercase(Locale.US)
        // Basic UPI ID validation
        return if (trimmed.contains("@") && trimmed.length in 5..50
            && !trimmed.contains("gmail") && !trimmed.contains("yahoo")
            && !trimmed.contains("hotmail") && !trimmed.contains("outlook")
        ) trimmed else null
    }

    /**
     * Detect payment method from the full SMS body text using keyword heuristics.
     * Matches: UPI, NEFT, IMPS, RTGS, ATM, Card/POS.
     */
    private fun detectMethodFromBody(body: String): String {
        val lower = body.lowercase(Locale.US)
        return when {
            lower.contains("upi") || lower.contains("@") -> "UPI"
            lower.contains("neft") -> "NEFT"
            lower.contains("imps") -> "IMPS"
            lower.contains("rtgs") -> "RTGS"
            lower.contains("atm") -> "ATM"
            lower.contains("card") || lower.contains("pos") -> "Card"
            else -> "Bank Transfer"
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  BANK PROFILE BUILDERS
    // ══════════════════════════════════════════════════════════════════════

    private fun buildBankProfiles(): List<BankProfile> = listOf(
        buildHdfcProfile(),
        buildSbiProfile(),
        buildIciciProfile(),
        buildAxisProfile(),
        buildKotakProfile(),
        buildBobProfile(),
        buildPnbProfile(),
        buildIndusIndProfile(),
        buildYesBankProfile(),
        buildUnionBankProfile(),
        buildCanaraBankProfile(),
        buildFederalBankProfile(),
        buildIdbiBankProfile(),
        buildJupiterProfile(),
        buildFiMoneyProfile(),
        buildSliceProfile()
    )

    private fun buildPaymentAppProfiles(): List<BankProfile> = listOf(
        buildPaytmProfile(),
        buildPhonePeProfile(),
        buildCredProfile()
    )

    // ── HDFC Bank ───────────────────────────────────────────────────────

    private fun buildHdfcProfile(): BankProfile {
        val templates = listOf(
            // 1. UPI debit: "Rs.500.00 debited from a/c **1234 on 18-Oct-26 to Swiggy. Avl bal Rs.12,345.67"
            SmsTemplate(
                pattern = Pattern.compile(
                    """Rs\.?\s*([\d,]+\.?\d*)\s+debited\s+from\s+a/c\s*\*{1,2}\s*(\d{4,6})\s+on\s+(\S+(?:\s+\S+)?)\s+to\s+(.+?)\.\s*Avl\s*bal\s*Rs\.?\s*([\d,]+\.?\d*)""",
                    Pattern.CASE_INSENSITIVE
                ),
                type = "debit",
                amountGroup = 1,
                accountGroup = 2,
                dateGroup = 3,
                merchantGroup = 4,
                balanceGroup = 5,
                methodHint = null // auto-detect from body
            ),
            // 2. UPI debit with VPA: "Rs 500 debited from HDFC Bank A/c **1234 via UPI to merchant@bank on 18-Oct-26. Ref 412345678901"
            SmsTemplate(
                pattern = Pattern.compile(
                    """Rs\.?\s*([\d,]+\.?\d*)\s+debited\s+from\s+HDFC\s+Bank\s+A/c\s*\*{1,2}\s*(\d{4,6})\s+via\s+UPI\s+to\s+(\S+@\S+)\s+on\s+(\S+(?:\s+\S+)?)\.\s*Ref\s+(\d{8,22})""",
                    Pattern.CASE_INSENSITIVE
                ),
                type = "debit",
                amountGroup = 1,
                accountGroup = 2,
                upiIdGroup = 3,
                dateGroup = 4,
                refGroup = 5,
                methodHint = "UPI"
            ),
            // 3. NEFT/IMPS credit: "Rs 5000 credited to a/c **1234 on 18-Oct-26. Info: NEFT/REF123456/ACME Corp"
            SmsTemplate(
                pattern = Pattern.compile(
                    """Rs\.?\s*([\d,]+\.?\d*)\s+credited\s+to\s+a/c\s*\*{1,2}\s*(\d{4,6})\s+on\s+(\S+(?:\s+\S+)?)\.\s*Info:\s*(\w+)/(\w+)/(.+?)(?:\s*\.|$)""",
                    Pattern.CASE_INSENSITIVE
                ),
                type = "credit",
                amountGroup = 1,
                accountGroup = 2,
                dateGroup = 3,
                refGroup = 5,
                merchantGroup = 6,
                methodHint = null // auto-detect NEFT/IMPS/UPI from body
            ),
            // 4. Card purchase: "Rs.500.00 spent on card ending 1234 at Amazon on 18-Oct-26. Avl bal Rs.12,345.67"
            SmsTemplate(
                pattern = Pattern.compile(
                    """Rs\.?\s*([\d,]+\.?\d*)\s+spent\s+on\s+card\s+ending\s+(\d{4,6})\s+at\s+(.+?)\s+on\s+(\S+(?:\s+\S+)?)\.\s*Avl\s*bal\s*Rs\.?\s*([\d,]+\.?\d*)""",
                    Pattern.CASE_INSENSITIVE
                ),
                type = "debit",
                amountGroup = 1,
                accountGroup = 2,
                merchantGroup = 3,
                dateGroup = 4,
                balanceGroup = 5,
                methodHint = "Card"
            ),
            // 5. ATM withdrawal: "Rs 5000 withdrawn from ATM for A/c **1234 on 18-Oct-26. Avl Bal Rs 12,345.67"
            SmsTemplate(
                pattern = Pattern.compile(
                    """Rs\.?\s*([\d,]+\.?\d*)\s+withdrawn\s+from\s+ATM\s+for\s+A/c\s*\*{1,2}\s*(\d{4,6})\s+on\s+(\S+(?:\s+\S+)?)\.\s*Avl\s*Bal\s*Rs\.?\s*([\d,]+\.?\d*)""",
                    Pattern.CASE_INSENSITIVE
                ),
                type = "debit",
                amountGroup = 1,
                accountGroup = 2,
                dateGroup = 3,
                balanceGroup = 4,
                methodHint = "ATM"
            ),
            // 6. Generic HDFC debit (broader match)
            SmsTemplate(
                pattern = Pattern.compile(
                    """Rs\.?\s*([\d,]+\.?\d*)\s+debited\s+from\s+(?:HDFC\s+Bank\s+)?[Aa]/c\s*\*{1,2}\s*(\d{4,6})\s+(?:on\s+)?(\S+)""",
                    Pattern.CASE_INSENSITIVE
                ),
                type = "debit",
                amountGroup = 1,
                accountGroup = 2,
                dateGroup = 3
            ),
            // 7. Generic HDFC credit (broader match)
            SmsTemplate(
                pattern = Pattern.compile(
                    """Rs\.?\s*([\d,]+\.?\d*)\s+credited\s+to\s+(?:HDFC\s+Bank\s+)?[Aa]/c\s*\*{1,2}\s*(\d{4,6})\s+(?:on\s+)?(\S+)""",
                    Pattern.CASE_INSENSITIVE
                ),
                type = "credit",
                amountGroup = 1,
                accountGroup = 2,
                dateGroup = 3
            )
        )
        return BankProfile("HDFC Bank", listOf("HDFCBK", "HDFC"), templates)
    }

    // ── SBI ─────────────────────────────────────────────────────────────

    private fun buildSbiProfile(): BankProfile {
        val templates = listOf(
            // 1. Debit: "Your a/c no. XXXXXXX1234 is debited for Rs.500.00 on 18Oct26 and XXXXXXX5678 is credited. Ref No 412345678901"
            SmsTemplate(
                pattern = Pattern.compile(
                    """[Yy]our\s+a/c\s*(?:no\.?)?\s*X+(\d{4,6})\s+is\s+debited\s+for\s+Rs\.?\s*([\d,]+\.?\d*)\s+on\s+(\S+)\s+and\s+X+(\d{4,6})\s+is\s+credited\.\s*Ref\s*No\.?\s*(\d{8,22})""",
                    Pattern.CASE_INSENSITIVE
                ),
                type = "debit",
                accountGroup = 1,
                amountGroup = 2,
                dateGroup = 3,
                refGroup = 5
            ),
            // 2. UPI debit: "Dear Customer, Rs.500 debited from your A/c XXXXX1234 on 18Oct26(UPI Ref No 412345678901)"
            SmsTemplate(
                pattern = Pattern.compile(
                    """Rs\.?\s*([\d,]+\.?\d*)\s+debited\s+from\s+your\s+A/c\s*X+(\d{4,6})\s+on\s+(\S+)\s*\(?UPI\s+Ref\s*No\.?\s*(\d{8,22})\)?""",
                    Pattern.CASE_INSENSITIVE
                ),
                type = "debit",
                amountGroup = 1,
                accountGroup = 2,
                dateGroup = 3,
                refGroup = 4,
                methodHint = "UPI"
            ),
            // 3. Credit: "Your a/c XXXXX1234 has been credited by Rs.5000 on 18Oct26(UPI Ref No 412345678901)"
            SmsTemplate(
                pattern = Pattern.compile(
                    """[Aa]/c\s*X+(\d{4,6})\s+has\s+been\s+credited\s+by\s+Rs\.?\s*([\d,]+\.?\d*)\s+on\s+(\S+)\s*\(?UPI\s+Ref\s*No\.?\s*(\d{8,22})\)?""",
                    Pattern.CASE_INSENSITIVE
                ),
                type = "credit",
                accountGroup = 1,
                amountGroup = 2,
                dateGroup = 3,
                refGroup = 4,
                methodHint = "UPI"
            ),
            // 4. ATM: "Rs 5000 debited from A/c XXXXX1234 for ATM withdrawal on 18Oct26. Avl bal Rs 12345"
            SmsTemplate(
                pattern = Pattern.compile(
                    """Rs\.?\s*([\d,]+\.?\d*)\s+debited\s+from\s+A/c\s*X+(\d{4,6})\s+for\s+ATM\s+withdrawal\s+on\s+(\S+)\.\s*Avl\s*bal\s*Rs\.?\s*([\d,]+\.?\d*)""",
                    Pattern.CASE_INSENSITIVE
                ),
                type = "debit",
                amountGroup = 1,
                accountGroup = 2,
                dateGroup = 3,
                balanceGroup = 4,
                methodHint = "ATM"
            ),
            // 5. Compact format: "500 debited from A/c ...1234 on 18Oct26. Clr Bal Rs.12345"
            SmsTemplate(
                pattern = Pattern.compile(
                    """([\d,]+\.?\d*)\s+debited\s+from\s+A/c\s*\.{2,3}\s*(\d{4,6})\s+on\s+(\S+)\.\s*(?:Clr\s*)?Bal\s*Rs\.?\s*([\d,]+\.?\d*)""",
                    Pattern.CASE_INSENSITIVE
                ),
                type = "debit",
                amountGroup = 1,
                accountGroup = 2,
                dateGroup = 3,
                balanceGroup = 4
            ),
            // 6. Generic SBI credit with balance
            SmsTemplate(
                pattern = Pattern.compile(
                    """[Aa]/c\s*X+(\d{4,6})\s+(?:has\s+been\s+)?credited\s+(?:by\s+|with\s+)?Rs\.?\s*([\d,]+\.?\d*)\s+on\s+(\S+)""",
                    Pattern.CASE_INSENSITIVE
                ),
                type = "credit",
                accountGroup = 1,
                amountGroup = 2,
                dateGroup = 3
            )
        )
        return BankProfile("SBI", listOf("SBI", "SBIINB", "SBIATM"), templates)
    }

    // ── ICICI Bank ──────────────────────────────────────────────────────

    private fun buildIciciProfile(): BankProfile {
        val templates = listOf(
            // 1. UPI debit: "A/c XX1234 debited with Rs 500 on 18-Oct-26 at 14:23 (UPI Ref No 412345678901). Avl Bal Rs 12345"
            SmsTemplate(
                pattern = Pattern.compile(
                    """A/c\s*XX?(\d{4,6})\s+debited\s+with\s+Rs\.?\s*([\d,]+\.?\d*)\s+on\s+(\S+)\s+at\s+(\S+)\s*\(?\s*UPI\s+Ref\s*No\.?\s*(\d{8,22})\)?\.\s*Avl\s*Bal\s*Rs\.?\s*([\d,]+\.?\d*)""",
                    Pattern.CASE_INSENSITIVE
                ),
                type = "debit",
                accountGroup = 1,
                amountGroup = 2,
                dateGroup = 3,
                refGroup = 5,
                balanceGroup = 6,
                methodHint = "UPI"
            ),
            // 2. Card: "Rs 500 was spent on ICICI Credit Card 1234 on 18-Oct-26 at Amazon. Avl bal: Rs 12345"
            SmsTemplate(
                pattern = Pattern.compile(
                    """Rs\.?\s*([\d,]+\.?\d*)\s+was\s+spent\s+on\s+ICICI\s+Credit\s+Card\s+(\d{4,6})\s+on\s+(\S+(?:\s+\S+)?)\s+at\s+(.+?)\.\s*Avl\s*bal:?\s*Rs\.?\s*([\d,]+\.?\d*)""",
                    Pattern.CASE_INSENSITIVE
                ),
                type = "debit",
                amountGroup = 1,
                accountGroup = 2,
                dateGroup = 3,
                merchantGroup = 4,
                balanceGroup = 5,
                methodHint = "Card"
            ),
            // 3. NEFT credit: "A/c XX1234 credited Rs.5000 on 18-Oct-26. NEFT from ACME Corp. Avl bal Rs 12345"
            SmsTemplate(
                pattern = Pattern.compile(
                    """A/c\s*XX?(\d{4,6})\s+credited\s+Rs\.?\s*([\d,]+\.?\d*)\s+on\s+(\S+(?:\s+\S+)?)\.\s*NEFT\s+from\s+(.+?)\.\s*Avl\s*bal\s*Rs\.?\s*([\d,]+\.?\d*)""",
                    Pattern.CASE_INSENSITIVE
                ),
                type = "credit",
                accountGroup = 1,
                amountGroup = 2,
                dateGroup = 3,
                merchantGroup = 4,
                balanceGroup = 5,
                methodHint = "NEFT"
            ),
            // 4. With AM/PM: "A/c XX1234 debited with Rs 500 on 18-Oct-26 at 02:23 PM"
            SmsTemplate(
                pattern = Pattern.compile(
                    """A/c\s*XX?(\d{4,6})\s+debited\s+with\s+Rs\.?\s*([\d,]+\.?\d*)\s+on\s+(\S+)\s+at\s+(\d{1,2}:\d{2})\s*(AM|PM)""",
                    Pattern.CASE_INSENSITIVE
                ),
                type = "debit",
                accountGroup = 1,
                amountGroup = 2,
                dateGroup = 3
            ),
            // 5. Generic ICICI debit
            SmsTemplate(
                pattern = Pattern.compile(
                    """A/c\s*XX?(\d{4,6})\s+debited\s+(?:with\s+)?Rs\.?\s*([\d,]+\.?\d*)\s+on\s+(\S+)""",
                    Pattern.CASE_INSENSITIVE
                ),
                type = "debit",
                accountGroup = 1,
                amountGroup = 2,
                dateGroup = 3
            ),
            // 6. Generic ICICI credit
            SmsTemplate(
                pattern = Pattern.compile(
                    """A/c\s*XX?(\d{4,6})\s+credited\s+(?:with\s+)?Rs\.?\s*([\d,]+\.?\d*)\s+on\s+(\S+)""",
                    Pattern.CASE_INSENSITIVE
                ),
                type = "credit",
                accountGroup = 1,
                amountGroup = 2,
                dateGroup = 3
            )
        )
        return BankProfile("ICICI Bank", listOf("ICICIB", "ICICI"), templates)
    }

    // ── Axis Bank ───────────────────────────────────────────────────────

    private fun buildAxisProfile(): BankProfile {
        val templates = listOf(
            // 1. UPI: "INR 500 debited from A/c no. XX1234 on 18-Oct-26 to Swiggy. UPI Ref No 412345678901. Avl Bal: INR 12345"
            SmsTemplate(
                pattern = Pattern.compile(
                    """INR\s*([\d,]+\.?\d*)\s+debited\s+from\s+A/c\s*(?:no\.?)?\s*XX?(\d{4,6})\s+on\s+(\S+(?:\s+\S+)?)\s+to\s+(.+?)\.\s*UPI\s+Ref\s*No\.?\s*(\d{8,22})\.\s*Avl\s*Bal:?\s*INR\s*([\d,]+\.?\d*)""",
                    Pattern.CASE_INSENSITIVE
                ),
                type = "debit",
                amountGroup = 1,
                accountGroup = 2,
                dateGroup = 3,
                merchantGroup = 4,
                refGroup = 5,
                balanceGroup = 6,
                methodHint = "UPI"
            ),
            // 2. Card: "INR 500 spent on Axis Bank Card no. XX1234 at Amazon on 18-Oct-26. Avl Bal: INR 12345"
            SmsTemplate(
                pattern = Pattern.compile(
                    """INR\s*([\d,]+\.?\d*)\s+spent\s+on\s+Axis\s+Bank\s+Card\s*(?:no\.?)?\s*XX?(\d{4,6})\s+at\s+(.+?)\s+on\s+(\S+(?:\s+\S+)?)\.\s*Avl\s*Bal:?\s*INR\s*([\d,]+\.?\d*)""",
                    Pattern.CASE_INSENSITIVE
                ),
                type = "debit",
                amountGroup = 1,
                accountGroup = 2,
                merchantGroup = 3,
                dateGroup = 4,
                balanceGroup = 5,
                methodHint = "Card"
            ),
            // 3. Credit: "INR 5000 credited to A/c XX1234 on 18-Oct-26. Info: ACME Corp. Ref No 412345678901"
            SmsTemplate(
                pattern = Pattern.compile(
                    """INR\s*([\d,]+\.?\d*)\s+credited\s+to\s+A/c\s*XX?(\d{4,6})\s+on\s+(\S+(?:\s+\S+)?)\.\s*Info:\s*(.+?)\.\s*Ref\s*No\.?\s*(\d{8,22})""",
                    Pattern.CASE_INSENSITIVE
                ),
                type = "credit",
                amountGroup = 1,
                accountGroup = 2,
                dateGroup = 3,
                merchantGroup = 4,
                refGroup = 5
            ),
            // 4. Generic Axis debit
            SmsTemplate(
                pattern = Pattern.compile(
                    """INR\s*([\d,]+\.?\d*)\s+debited\s+from\s+A/c\s*(?:no\.?)?\s*XX?(\d{4,6})\s+on\s+(\S+)""",
                    Pattern.CASE_INSENSITIVE
                ),
                type = "debit",
                amountGroup = 1,
                accountGroup = 2,
                dateGroup = 3
            ),
            // 5. Generic Axis credit
            SmsTemplate(
                pattern = Pattern.compile(
                    """INR\s*([\d,]+\.?\d*)\s+credited\s+to\s+A/c\s*XX?(\d{4,6})\s+on\s+(\S+)""",
                    Pattern.CASE_INSENSITIVE
                ),
                type = "credit",
                amountGroup = 1,
                accountGroup = 2,
                dateGroup = 3
            )
        )
        return BankProfile("Axis Bank", listOf("AXISBK", "AXIS"), templates)
    }

    // ── Kotak Mahindra Bank ─────────────────────────────────────────────

    private fun buildKotakProfile(): BankProfile {
        val templates = listOf(
            // 1. Debit: "Debited INR 500 from A/c XX1234 on 18-Oct-26 to Swiggy. UPI Ref: 412345678901. Balance: INR 12345"
            SmsTemplate(
                pattern = Pattern.compile(
                    """Debited\s+INR\s*([\d,]+\.?\d*)\s+from\s+A/c\s*XX?(\d{4,6})\s+on\s+(\S+(?:\s+\S+)?)\s+to\s+(.+?)\.\s*UPI\s+Ref:?\s*(\d{8,22})\.\s*Balance:?\s*INR\s*([\d,]+\.?\d*)""",
                    Pattern.CASE_INSENSITIVE
                ),
                type = "debit",
                amountGroup = 1,
                accountGroup = 2,
                dateGroup = 3,
                merchantGroup = 4,
                refGroup = 5,
                balanceGroup = 6,
                methodHint = "UPI"
            ),
            // 2. Credit: "Credited INR 5000 to A/c XX1234 on 18-Oct-26. Info: ACME Corp. Ref: 412345678901"
            SmsTemplate(
                pattern = Pattern.compile(
                    """Credited\s+INR\s*([\d,]+\.?\d*)\s+to\s+A/c\s*XX?(\d{4,6})\s+on\s+(\S+(?:\s+\S+)?)\.\s*Info:\s*(.+?)\.\s*Ref:?\s*(\d{8,22})""",
                    Pattern.CASE_INSENSITIVE
                ),
                type = "credit",
                amountGroup = 1,
                accountGroup = 2,
                dateGroup = 3,
                merchantGroup = 4,
                refGroup = 5
            ),
            // 3. Generic Kotak debit
            SmsTemplate(
                pattern = Pattern.compile(
                    """Debited\s+INR\s*([\d,]+\.?\d*)\s+from\s+A/c\s*XX?(\d{4,6})\s+on\s+(\S+)""",
                    Pattern.CASE_INSENSITIVE
                ),
                type = "debit",
                amountGroup = 1,
                accountGroup = 2,
                dateGroup = 3
            ),
            // 4. Generic Kotak credit
            SmsTemplate(
                pattern = Pattern.compile(
                    """Credited\s+INR\s*([\d,]+\.?\d*)\s+to\s+A/c\s*XX?(\d{4,6})\s+on\s+(\S+)""",
                    Pattern.CASE_INSENSITIVE
                ),
                type = "credit",
                amountGroup = 1,
                accountGroup = 2,
                dateGroup = 3
            )
        )
        return BankProfile("Kotak Mahindra Bank", listOf("KOTAK", "KOTAKB"), templates)
    }

    // ── Bank of Baroda ──────────────────────────────────────────────────

    private fun buildBobProfile(): BankProfile {
        val templates = listOf(
            // 1. BOB unique format: "Dr. From A/C:....1234 Amt:Rs.500 Cr. To Swiggy from:CRA/412345678901 (2026:03:31 02:43:21) Total Bal:Rs.12345CR"
            SmsTemplate(
                pattern = Pattern.compile(
                    """Dr\.\s*From\s+A/C:?\.{2,4}(\d{4,6})\s+Amt:?\s*Rs\.?\s*([\d,]+\.?\d*)\s+Cr\.\s*To\s+(.+?)\s+from:?\s*CRA/(\d{8,22})\s*\(([^)]+)\)\s*Total\s*Bal:?\s*Rs\.?\s*([\d,]+\.?\d*)""",
                    Pattern.CASE_INSENSITIVE
                ),
                type = "debit",
                accountGroup = 1,
                amountGroup = 2,
                merchantGroup = 3,
                refGroup = 4,
                dateGroup = 5,
                balanceGroup = 6
            ),
            // 2. UPI: "Your BOB A/c XX1234 debited Rs.500 on 18-Oct-26. UPI/CR/412345678901/Swiggy"
            SmsTemplate(
                pattern = Pattern.compile(
                    """BOB\s+A/c\s*XX?(\d{4,6})\s+debited\s+Rs\.?\s*([\d,]+\.?\d*)\s+on\s+(\S+(?:\s+\S+)?)\.\s*UPI/(?:CR|DR)/(\d{8,22})/(.+?)(?:\s*\.|$)""",
                    Pattern.CASE_INSENSITIVE
                ),
                type = "debit",
                accountGroup = 1,
                amountGroup = 2,
                dateGroup = 3,
                refGroup = 4,
                merchantGroup = 5,
                methodHint = "UPI"
            ),
            // 3. BOB credit
            SmsTemplate(
                pattern = Pattern.compile(
                    """Cr\.\s*(?:In|To)\s+A/C:?\.{2,4}(\d{4,6})\s+Amt:?\s*Rs\.?\s*([\d,]+\.?\d*)\s+(?:from|by)\s+(.+?)\s+(?:from:?\s*CRA/(\d{8,22})\s*)?\(([^)]+)\)\s*Total\s*Bal:?\s*Rs\.?\s*([\d,]+\.?\d*)""",
                    Pattern.CASE_INSENSITIVE
                ),
                type = "credit",
                accountGroup = 1,
                amountGroup = 2,
                merchantGroup = 3,
                refGroup = 4,
                dateGroup = 5,
                balanceGroup = 6
            ),
            // 4. Generic BOB debit
            SmsTemplate(
                pattern = Pattern.compile(
                    """BOB\s+A/c\s*XX?(\d{4,6})\s+debited\s+Rs\.?\s*([\d,]+\.?\d*)\s+on\s+(\S+)""",
                    Pattern.CASE_INSENSITIVE
                ),
                type = "debit",
                accountGroup = 1,
                amountGroup = 2,
                dateGroup = 3
            )
        )
        return BankProfile("Bank of Baroda", listOf("BOBIRN", "BOB", "BOBIPL"), templates)
    }

    // ── PNB ─────────────────────────────────────────────────────────────

    private fun buildPnbProfile(): BankProfile {
        val templates = listOf(
            // 1. Debit: "Rs.500 debited from A/c XXXX1234 on 18-Oct-26 Ref: 412345678901. If not done by you, call..."
            SmsTemplate(
                pattern = Pattern.compile(
                    """Rs\.?\s*([\d,]+\.?\d*)\s+debited\s+from\s+A/c\s*X+(\d{4,6})\s+on\s+(\S+(?:\s+\S+)?)\s+Ref:?\s*(\d{8,22})""",
                    Pattern.CASE_INSENSITIVE
                ),
                type = "debit",
                amountGroup = 1,
                accountGroup = 2,
                dateGroup = 3,
                refGroup = 4
            ),
            // 2. Credit: "Rs.5000 credited to your A/c XXXX1234 on 18-Oct-26. Avl Bal: Rs.12345"
            SmsTemplate(
                pattern = Pattern.compile(
                    """Rs\.?\s*([\d,]+\.?\d*)\s+credited\s+to\s+(?:your\s+)?A/c\s*X+(\d{4,6})\s+on\s+(\S+(?:\s+\S+)?)\.\s*Avl\s*Bal:?\s*Rs\.?\s*([\d,]+\.?\d*)""",
                    Pattern.CASE_INSENSITIVE
                ),
                type = "credit",
                amountGroup = 1,
                accountGroup = 2,
                dateGroup = 3,
                balanceGroup = 4
            ),
            // 3. Generic PNB debit
            SmsTemplate(
                pattern = Pattern.compile(
                    """Rs\.?\s*([\d,]+\.?\d*)\s+debited\s+from\s+(?:your\s+)?A/c\s*X+(\d{4,6})\s+on\s+(\S+)""",
                    Pattern.CASE_INSENSITIVE
                ),
                type = "debit",
                amountGroup = 1,
                accountGroup = 2,
                dateGroup = 3
            )
        )
        return BankProfile("PNB", listOf("PUNBNK", "PNBSMS"), templates)
    }

    // ── IndusInd Bank ───────────────────────────────────────────────────

    private fun buildIndusIndProfile(): BankProfile {
        val templates = listOf(
            // 1. Full debit: "Rs 500 debited from IndusInd A/c XX1234 on 18-Oct-26 to Swiggy. Ref: 412345678901. Bal: Rs 12345"
            SmsTemplate(
                pattern = Pattern.compile(
                    """Rs\.?\s*([\d,]+\.?\d*)\s+debited\s+from\s+IndusInd\s+A/c\s*XX?(\d{4,6})\s+on\s+(\S+(?:\s+\S+)?)\s+to\s+(.+?)\.\s*Ref:?\s*(\d{8,22})\.\s*Bal:?\s*Rs\.?\s*([\d,]+\.?\d*)""",
                    Pattern.CASE_INSENSITIVE
                ),
                type = "debit",
                amountGroup = 1,
                accountGroup = 2,
                dateGroup = 3,
                merchantGroup = 4,
                refGroup = 5,
                balanceGroup = 6
            ),
            // 2. Generic IndusInd debit
            SmsTemplate(
                pattern = Pattern.compile(
                    """Rs\.?\s*([\d,]+\.?\d*)\s+debited\s+from\s+IndusInd\s+A/c\s*XX?(\d{4,6})\s+on\s+(\S+)""",
                    Pattern.CASE_INSENSITIVE
                ),
                type = "debit",
                amountGroup = 1,
                accountGroup = 2,
                dateGroup = 3
            )
        )
        return BankProfile("IndusInd Bank", listOf("INDBNK", "INDUSBK"), templates)
    }

    // ── Yes Bank ────────────────────────────────────────────────────────

    private fun buildYesBankProfile(): BankProfile {
        val templates = listOf(
            // 1. Full debit: "INR 500 debited from A/c XX1234 towards Swiggy. Ref: 412345678901. Avl Bal: INR 12345"
            SmsTemplate(
                pattern = Pattern.compile(
                    """INR\s*([\d,]+\.?\d*)\s+debited\s+from\s+A/c\s*XX?(\d{4,6})\s+towards\s+(.+?)\.\s*Ref:?\s*(\d{8,22})\.\s*Avl\s*Bal:?\s*INR\s*([\d,]+\.?\d*)""",
                    Pattern.CASE_INSENSITIVE
                ),
                type = "debit",
                amountGroup = 1,
                accountGroup = 2,
                merchantGroup = 3,
                refGroup = 4,
                balanceGroup = 5
            ),
            // 2. Generic Yes Bank debit
            SmsTemplate(
                pattern = Pattern.compile(
                    """INR\s*([\d,]+\.?\d*)\s+debited\s+from\s+A/c\s*XX?(\d{4,6})\s+towards\s+(.+?)(?:\.\s*|$)""",
                    Pattern.CASE_INSENSITIVE
                ),
                type = "debit",
                amountGroup = 1,
                accountGroup = 2,
                merchantGroup = 3
            )
        )
        return BankProfile("Yes Bank", listOf("YESBNK"), templates)
    }

    // ── Union Bank ──────────────────────────────────────────────────────

    private fun buildUnionBankProfile(): BankProfile {
        val templates = listOf(
            // 1. Full debit: "Rs.500 debited from your A/c XX1234 on 18-Oct-26. UPI/412345678901/Swiggy. Bal: Rs.12345"
            SmsTemplate(
                pattern = Pattern.compile(
                    """Rs\.?\s*([\d,]+\.?\d*)\s+debited\s+from\s+your\s+A/c\s*XX?(\d{4,6})\s+on\s+(\S+(?:\s+\S+)?)\.\s*UPI/(\d{8,22})/(.+?)\.\s*Bal:?\s*Rs\.?\s*([\d,]+\.?\d*)""",
                    Pattern.CASE_INSENSITIVE
                ),
                type = "debit",
                amountGroup = 1,
                accountGroup = 2,
                dateGroup = 3,
                refGroup = 4,
                merchantGroup = 5,
                balanceGroup = 6,
                methodHint = "UPI"
            ),
            // 2. Generic Union Bank debit
            SmsTemplate(
                pattern = Pattern.compile(
                    """Rs\.?\s*([\d,]+\.?\d*)\s+debited\s+from\s+your\s+A/c\s*XX?(\d{4,6})\s+on\s+(\S+)""",
                    Pattern.CASE_INSENSITIVE
                ),
                type = "debit",
                amountGroup = 1,
                accountGroup = 2,
                dateGroup = 3
            )
        )
        return BankProfile("Union Bank", listOf("UNIONB"), templates)
    }

    // ── Canara Bank ─────────────────────────────────────────────────────

    private fun buildCanaraBankProfile(): BankProfile {
        val templates = listOf(
            // 1. Full debit: "Rs.500 debited from your SB A/c XX1234 on 18-Oct-26 towards Swiggy. Avl Bal Rs 12345"
            SmsTemplate(
                pattern = Pattern.compile(
                    """Rs\.?\s*([\d,]+\.?\d*)\s+debited\s+from\s+your\s+(?:SB\s+)?A/c\s*XX?(\d{4,6})\s+on\s+(\S+(?:\s+\S+)?)\s+towards\s+(.+?)\.\s*Avl\s*Bal\s*Rs\.?\s*([\d,]+\.?\d*)""",
                    Pattern.CASE_INSENSITIVE
                ),
                type = "debit",
                amountGroup = 1,
                accountGroup = 2,
                dateGroup = 3,
                merchantGroup = 4,
                balanceGroup = 5
            ),
            // 2. Generic Canara debit
            SmsTemplate(
                pattern = Pattern.compile(
                    """Rs\.?\s*([\d,]+\.?\d*)\s+debited\s+from\s+your\s+(?:SB\s+)?A/c\s*XX?(\d{4,6})\s+on\s+(\S+)""",
                    Pattern.CASE_INSENSITIVE
                ),
                type = "debit",
                amountGroup = 1,
                accountGroup = 2,
                dateGroup = 3
            )
        )
        return BankProfile("Canara Bank", listOf("CANBNK"), templates)
    }

    // ── Federal Bank ────────────────────────────────────────────────────

    private fun buildFederalBankProfile(): BankProfile {
        val templates = listOf(
            // 1. Full UPI debit: "Rs 500 debited from your A/c XX1234 towards UPI txn to Swiggy. Ref No. 412345678901. Bal: Rs 12345"
            SmsTemplate(
                pattern = Pattern.compile(
                    """Rs\.?\s*([\d,]+\.?\d*)\s+debited\s+from\s+your\s+A/c\s*XX?(\d{4,6})\s+towards\s+UPI\s+txn\s+to\s+(.+?)\.\s*Ref\s*No\.?\s*(\d{8,22})\.\s*Bal:?\s*Rs\.?\s*([\d,]+\.?\d*)""",
                    Pattern.CASE_INSENSITIVE
                ),
                type = "debit",
                amountGroup = 1,
                accountGroup = 2,
                merchantGroup = 3,
                refGroup = 4,
                balanceGroup = 5,
                methodHint = "UPI"
            ),
            // 2. Generic Federal debit
            SmsTemplate(
                pattern = Pattern.compile(
                    """Rs\.?\s*([\d,]+\.?\d*)\s+debited\s+from\s+your\s+A/c\s*XX?(\d{4,6})\s+towards\s+(.+?)(?:\.\s*|$)""",
                    Pattern.CASE_INSENSITIVE
                ),
                type = "debit",
                amountGroup = 1,
                accountGroup = 2,
                merchantGroup = 3
            )
        )
        return BankProfile("Federal Bank", listOf("FEDBNK"), templates)
    }

    // ── IDBI Bank ───────────────────────────────────────────────────────

    private fun buildIdbiBankProfile(): BankProfile {
        val templates = listOf(
            // 1. Full debit: "Rs.500 debited from IDBI Bank A/c XX1234 on 18-Oct-26 to Swiggy. Ref: 412345678901. Bal: Rs 12345"
            SmsTemplate(
                pattern = Pattern.compile(
                    """Rs\.?\s*([\d,]+\.?\d*)\s+debited\s+from\s+IDBI\s+Bank\s+A/c\s*XX?(\d{4,6})\s+on\s+(\S+(?:\s+\S+)?)\s+to\s+(.+?)\.\s*Ref:?\s*(\d{8,22})\.\s*Bal:?\s*Rs\.?\s*([\d,]+\.?\d*)""",
                    Pattern.CASE_INSENSITIVE
                ),
                type = "debit",
                amountGroup = 1,
                accountGroup = 2,
                dateGroup = 3,
                merchantGroup = 4,
                refGroup = 5,
                balanceGroup = 6
            ),
            // 2. Generic IDBI debit
            SmsTemplate(
                pattern = Pattern.compile(
                    """Rs\.?\s*([\d,]+\.?\d*)\s+debited\s+from\s+IDBI\s+Bank\s+A/c\s*XX?(\d{4,6})\s+on\s+(\S+)""",
                    Pattern.CASE_INSENSITIVE
                ),
                type = "debit",
                amountGroup = 1,
                accountGroup = 2,
                dateGroup = 3
            )
        )
        return BankProfile("IDBI Bank", listOf("IDBI"), templates)
    }

    // ── Jupiter (Neo-bank) ──────────────────────────────────────────────

    private fun buildJupiterProfile(): BankProfile {
        val templates = listOf(
            // 1. Full: "Rs 500 spent via Jupiter UPI to Swiggy. Ref: 412345678901. Bal: Rs 12345"
            SmsTemplate(
                pattern = Pattern.compile(
                    """Rs\.?\s*([\d,]+\.?\d*)\s+spent\s+via\s+Jupiter\s+UPI\s+to\s+(.+?)\.\s*Ref:?\s*(\d{8,22})\.\s*Bal:?\s*Rs\.?\s*([\d,]+\.?\d*)""",
                    Pattern.CASE_INSENSITIVE
                ),
                type = "debit",
                amountGroup = 1,
                merchantGroup = 2,
                refGroup = 3,
                balanceGroup = 4,
                methodHint = "UPI"
            ),
            // 2. Generic Jupiter
            SmsTemplate(
                pattern = Pattern.compile(
                    """Rs\.?\s*([\d,]+\.?\d*)\s+spent\s+via\s+Jupiter\s+(?:UPI\s+)?to\s+(.+?)(?:\.\s*|$)""",
                    Pattern.CASE_INSENSITIVE
                ),
                type = "debit",
                amountGroup = 1,
                merchantGroup = 2,
                methodHint = "UPI"
            )
        )
        return BankProfile("Jupiter", listOf("JUPBNK"), templates)
    }

    // ── Fi Money (Neo-bank) ─────────────────────────────────────────────

    private fun buildFiMoneyProfile(): BankProfile {
        val templates = listOf(
            // 1. Full: "Rs.500 debited from Fi A/c to Swiggy. UPI Ref: 412345678901. Balance: Rs.12345"
            SmsTemplate(
                pattern = Pattern.compile(
                    """Rs\.?\s*([\d,]+\.?\d*)\s+debited\s+from\s+Fi\s+A/c\s+to\s+(.+?)\.\s*UPI\s+Ref:?\s*(\d{8,22})\.\s*Balance:?\s*Rs\.?\s*([\d,]+\.?\d*)""",
                    Pattern.CASE_INSENSITIVE
                ),
                type = "debit",
                amountGroup = 1,
                merchantGroup = 2,
                refGroup = 3,
                balanceGroup = 4,
                methodHint = "UPI"
            ),
            // 2. Generic Fi
            SmsTemplate(
                pattern = Pattern.compile(
                    """Rs\.?\s*([\d,]+\.?\d*)\s+debited\s+from\s+Fi\s+A/c\s+to\s+(.+?)(?:\.\s*|$)""",
                    Pattern.CASE_INSENSITIVE
                ),
                type = "debit",
                amountGroup = 1,
                merchantGroup = 2,
                methodHint = "UPI"
            )
        )
        return BankProfile("Fi Money", listOf("FIBANK"), templates)
    }

    // ── Slice (Neo-bank, ICICI backend) ─────────────────────────────────

    private fun buildSliceProfile(): BankProfile {
        // Slice uses ICICI backend; patterns are similar to ICICI
        val templates = listOf(
            // 1. Slice debit with balance
            SmsTemplate(
                pattern = Pattern.compile(
                    """A/c\s*XX?(\d{4,6})\s+debited\s+(?:with\s+)?Rs\.?\s*([\d,]+\.?\d*)\s+on\s+(\S+)\s+at\s+(\S+)\s*\(?\s*UPI\s+Ref\s*No\.?\s*(\d{8,22})\)?\.\s*Avl\s*Bal\s*Rs\.?\s*([\d,]+\.?\d*)""",
                    Pattern.CASE_INSENSITIVE
                ),
                type = "debit",
                accountGroup = 1,
                amountGroup = 2,
                dateGroup = 3,
                refGroup = 5,
                balanceGroup = 6,
                methodHint = "UPI"
            ),
            // 2. Generic Slice debit
            SmsTemplate(
                pattern = Pattern.compile(
                    """Rs\.?\s*([\d,]+\.?\d*)\s+(?:debited|spent)\s+(?:from\s+|on\s+)?(?:Slice\s+)?(?:A/c\s*)?XX?(\d{4,6})?\s*(?:on\s+)?(\S+)?""",
                    Pattern.CASE_INSENSITIVE
                ),
                type = "debit",
                amountGroup = 1,
                accountGroup = 2,
                dateGroup = 3
            )
        )
        return BankProfile("Slice", listOf("SLICE"), templates)
    }

    // ══════════════════════════════════════════════════════════════════════
    //  PAYMENT APP PROFILES
    // ══════════════════════════════════════════════════════════════════════

    // ── Paytm ───────────────────────────────────────────────────────────

    private fun buildPaytmProfile(): BankProfile {
        val templates = listOf(
            // 1. Full: "Paid Rs.500 to Swiggy via Paytm UPI. UPI Ref: 412345678901"
            SmsTemplate(
                pattern = Pattern.compile(
                    """Paid\s+Rs\.?\s*([\d,]+\.?\d*)\s+to\s+(.+?)\s+via\s+Paytm\s+UPI\.\s*UPI\s+Ref:?\s*(\d{8,22})""",
                    Pattern.CASE_INSENSITIVE
                ),
                type = "debit",
                amountGroup = 1,
                merchantGroup = 2,
                refGroup = 3,
                methodHint = "UPI"
            ),
            // 2. Generic Paytm paid
            SmsTemplate(
                pattern = Pattern.compile(
                    """Paid\s+Rs\.?\s*([\d,]+\.?\d*)\s+to\s+(.+?)\s+via\s+Paytm""",
                    Pattern.CASE_INSENSITIVE
                ),
                type = "debit",
                amountGroup = 1,
                merchantGroup = 2,
                methodHint = "UPI"
            ),
            // 3. Paytm wallet debit
            SmsTemplate(
                pattern = Pattern.compile(
                    """Rs\.?\s*([\d,]+\.?\d*)\s+(?:paid|debited|sent)\s+(?:to|from)\s+(.+?)(?:\s+via|\s+using|\s*\.|$)""",
                    Pattern.CASE_INSENSITIVE
                ),
                type = "debit",
                amountGroup = 1,
                merchantGroup = 2,
                methodHint = "Wallet"
            )
        )
        return BankProfile("Paytm", listOf("PAYTM"), templates)
    }

    // ── PhonePe ─────────────────────────────────────────────────────────

    private fun buildPhonePeProfile(): BankProfile {
        val templates = listOf(
            // 1. Full: "Rs.500 paid to Swiggy successfully. UPI Ref No: 412345678901"
            SmsTemplate(
                pattern = Pattern.compile(
                    """Rs\.?\s*([\d,]+\.?\d*)\s+paid\s+to\s+(.+?)\s+successfully\.\s*UPI\s+Ref\s*No:?\s*(\d{8,22})""",
                    Pattern.CASE_INSENSITIVE
                ),
                type = "debit",
                amountGroup = 1,
                merchantGroup = 2,
                refGroup = 3,
                methodHint = "UPI"
            ),
            // 2. Received
            SmsTemplate(
                pattern = Pattern.compile(
                    """Rs\.?\s*([\d,]+\.?\d*)\s+received\s+from\s+(.+?)\s+(?:successfully|via\s+PhonePe)\.\s*(?:UPI\s+Ref\s*No:?\s*(\d{8,22}))?""",
                    Pattern.CASE_INSENSITIVE
                ),
                type = "credit",
                amountGroup = 1,
                merchantGroup = 2,
                refGroup = 3,
                methodHint = "UPI"
            ),
            // 3. Generic PhonePe paid
            SmsTemplate(
                pattern = Pattern.compile(
                    """Rs\.?\s*([\d,]+\.?\d*)\s+paid\s+to\s+(.+?)\s+successfully""",
                    Pattern.CASE_INSENSITIVE
                ),
                type = "debit",
                amountGroup = 1,
                merchantGroup = 2,
                methodHint = "UPI"
            )
        )
        return BankProfile("PhonePe", listOf("PHONEPE", "PHONPE"), templates)
    }

    // ── CRED ────────────────────────────────────────────────────────────

    private fun buildCredProfile(): BankProfile {
        val templates = listOf(
            // 1. Bill payment: "CRED: Rs.500 paid towards Amazon credit card bill"
            SmsTemplate(
                pattern = Pattern.compile(
                    """CRED:?\s*Rs\.?\s*([\d,]+\.?\d*)\s+paid\s+towards\s+(.+?)\s+credit\s+card\s+bill""",
                    Pattern.CASE_INSENSITIVE
                ),
                type = "debit",
                amountGroup = 1,
                merchantGroup = 2,
                methodHint = "UPI"
            ),
            // 2. Generic CRED payment
            SmsTemplate(
                pattern = Pattern.compile(
                    """CRED:?\s*Rs\.?\s*([\d,]+\.?\d*)\s+paid\s+(?:to|towards)\s+(.+?)(?:\.\s*|$)""",
                    Pattern.CASE_INSENSITIVE
                ),
                type = "debit",
                amountGroup = 1,
                merchantGroup = 2,
                methodHint = "UPI"
            )
        )
        return BankProfile("CRED", listOf("CRED"), templates)
    }

    companion object {
        fun isPreDebitMandate(body: String): Boolean {
            val lower = body.lowercase(Locale.US)
            return (lower.contains("will be debited") ||
                    lower.contains("scheduled for debit") ||
                    lower.contains("mandate scheduled") ||
                    lower.contains("mandate for inr") ||
                    lower.contains("mandate for rs") ||
                    lower.contains("pre-debit") ||
                    lower.contains("si scheduled") ||
                    lower.contains("autopay scheduled") ||
                    lower.contains("standing instruction scheduled")) &&
                   !lower.contains("has been debited") &&
                   !lower.contains("is debited")
        }

        fun isReversalOrRefund(body: String): Boolean {
            val lower = body.lowercase(Locale.US)
            return lower.contains("refund") ||
                   lower.contains("reversed") ||
                   lower.contains("reversal") ||
                   lower.contains("failed txn credit") ||
                   lower.contains("auto-refund") ||
                   lower.contains("credited back")
        }
    }
}
