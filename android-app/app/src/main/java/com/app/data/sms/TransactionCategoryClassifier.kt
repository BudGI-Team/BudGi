package com.app.data.sms

import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Detailed result of multi-signal category classification.
 */
data class CategoryResult(
    val category: String,
    val confidence: Double,                 // 0.0 to 1.0
    val source: String,                     // "UpiRegistry", "Rule", "AmountHeuristic", "TimePattern", "Default"
    val alternateCategory: String? = null,  // Second best category guess
    val alternateConfidence: Double = 0.0
)

/**
 * Intelligent Multi-Signal Category Classifier for Indian Financial Transactions.
 *
 * Combines 4 orthogonal scoring signals:
 * 1. Weighted Keyword Scoring (40% weight) - Matches comprehensive brand/expense keywords
 * 2. UPI & Merchant Intelligence (25% weight) - Resolves known UPI handles, QR IDs, and merchant VPAs
 * 3. Amount-Based Heuristics (20% weight) - Probability distribution based on typical INR brackets
 * 4. Temporal Patterns (15% weight) - Time-of-day contextual affinity (e.g. dinner vs morning tea)
 */
@Singleton
class TransactionCategoryClassifier @Inject constructor() {

    // ── Public API ───────────────────────────────────────────────────────

    /**
     * Legacy backward-compatible classification method.
     */
    fun classify(message: String, upiId: String? = null, merchant: String? = null): String {
        return classifyWithConfidence(
            message = message,
            upiId = upiId,
            merchant = merchant,
            amount = 0.0,
            hourOfDay = -1
        ).category
    }

    /**
     * Single-argument overload for backward compatibility.
     */
    fun classify(message: String): String = classify(message, null, null)

    /**
     * Full multi-signal classification with confidence score and alternative guess.
     */
    fun classifyWithConfidence(
        message: String,
        upiId: String? = null,
        merchant: String? = null,
        amount: Double = 0.0,
        hourOfDay: Int = -1,
        dayOfWeek: Int = -1
    ): CategoryResult {
        val normalized = message.lowercase(Locale.US)
        val upiLower = upiId?.lowercase(Locale.US) ?: ""
        val merchantLower = merchant?.lowercase(Locale.US) ?: ""
        val combinedText = "$normalized $upiLower $merchantLower"

        // 1. Income check has absolute priority for credits
        if (INCOME_KEYWORDS.any { combinedText.contains(it) }) {
            return CategoryResult(
                category = "Income",
                confidence = 0.98,
                source = "Rule"
            )
        }

        // 2. UPI P2P Check: 10-digit mobile number transfer
        if (upiLower.isNotBlank()) {
            val localPart = upiLower.substringBefore("@")
            val isPhoneUpi = localPart.matches(Regex("""^\d{10}$""")) || localPart.matches(Regex("""^91\d{10}$"""))
            val isTransferAction = normalized.contains("transferred") || normalized.contains("transfer") ||
                    normalized.contains("sent") || normalized.contains("received")
            if (isPhoneUpi && isTransferAction) {
                return CategoryResult(
                    category = "Transfer",
                    confidence = 0.92,
                    source = "UpiRegistry",
                    alternateCategory = "Others",
                    alternateConfidence = 0.3
                )
            }
        }

        // 3. Multi-Signal Scoring across all categories
        val scores = mutableMapOf<String, Double>()

        for (category in ALL_CATEGORIES) {
            var score = 0.0

            // Signal 1: Keyword score (weight: 0.40)
            val kwScore = calculateKeywordScore(category, combinedText)
            score += kwScore * 0.40

            // Signal 2: Amount heuristic score (weight: 0.20)
            if (amount > 0.0) {
                val amtScore = calculateAmountScore(category, amount)
                score += amtScore * 0.20
            }

            // Signal 3: Temporal score (weight: 0.15)
            if (hourOfDay in 0..23) {
                val timeScore = calculateTimeScore(category, hourOfDay)
                score += timeScore * 0.15
            }

            // Signal 4: Merchant / UPI exact match bonus (weight: 0.25)
            val upiScore = calculateUpiMerchantBonus(category, upiLower, merchantLower)
            score += upiScore * 0.25

            scores[category] = score
        }

        // Sort descending by calculated score
        val sorted = scores.entries.sortedByDescending { it.value }
        val top = sorted.firstOrNull()
        val second = sorted.getOrNull(1)

        val topCategory = top?.key ?: "Others"
        val topScore = top?.value ?: 0.0

        if (topScore < 0.25) {
            return CategoryResult(
                category = "Others",
                confidence = 0.40,
                source = "Default",
                alternateCategory = if (topScore > 0.10) topCategory else null,
                alternateConfidence = topScore
            )
        }

        val normalizedConfidence = (topScore / 0.85).coerceIn(0.50, 0.98)
        val secondConfidence = second?.let { (it.value / 0.85).coerceIn(0.20, 0.80) } ?: 0.0

        return CategoryResult(
            category = topCategory,
            confidence = normalizedConfidence,
            source = "Rule",
            alternateCategory = second?.key,
            alternateConfidence = secondConfidence
        )
    }

    // ── Signal scoring helpers ──────────────────────────────────────────

    private fun calculateKeywordScore(category: String, text: String): Double {
        val keywords = RULES[category] ?: return 0.0
        var matches = 0
        var totalWeight = 0.0

        for (kw in keywords) {
            if (text.contains(kw)) {
                matches++
                totalWeight += if (kw.length > 5) 1.2 else 1.0
            }
        }

        return if (matches > 0) (totalWeight / 2.0).coerceAtMost(1.0) else 0.0
    }

    private fun calculateAmountScore(category: String, amount: Double): Double {
        return when (category) {
            "Recharge" -> if (amount in 10.0..1500.0) 0.9 else 0.1
            "Food & Dining" -> when {
                amount in 20.0..2500.0 -> 0.85
                amount in 2500.0..6000.0 -> 0.50
                else -> 0.1
            }
            "Groceries" -> when {
                amount in 50.0..5000.0 -> 0.85
                amount in 5000.0..15000.0 -> 0.60
                else -> 0.2
            }
            "Transport" -> when {
                amount in 20.0..1200.0 -> 0.85 // Cab / Auto / Metro / Petrol
                amount in 1200.0..8000.0 -> 0.70 // Train / Flight ticket
                else -> 0.2
            }
            "Shopping" -> when {
                amount in 300.0..25000.0 -> 0.80
                else -> 0.4
            }
            "Rent" -> when {
                amount >= 4000.0 -> 0.85
                else -> 0.05
            }
            "Investment" -> when {
                amount >= 500.0 -> 0.80
                else -> 0.1
            }
            "Insurance" -> when {
                amount >= 1000.0 -> 0.80
                else -> 0.1
            }
            "Subscriptions" -> when {
                amount in 49.0..2500.0 -> 0.85
                else -> 0.2
            }
            "Vegetables & Mandi" -> when {
                amount in 15.0..1000.0 -> 0.90
                amount in 1000.0..3000.0 -> 0.50
                else -> 0.15
            }
            "Dairy & Milk" -> when {
                amount in 25.0..3000.0 -> 0.90
                else -> 0.20
            }
            "Personal Care & Salon" -> when {
                amount in 80.0..4000.0 -> 0.85
                else -> 0.20
            }
            "Domestic & Services" -> when {
                amount in 200.0..15000.0 -> 0.85
                else -> 0.15
            }
            "Pooja & Devotion" -> when {
                amount in 10.0..2500.0 -> 0.85
                else -> 0.20
            }
            else -> 0.4
        }
    }

    private fun calculateTimeScore(category: String, hourOfDay: Int): Double {
        return when (category) {
            "Food & Dining" -> when (hourOfDay) {
                in 8..10 -> 0.75  // Breakfast
                in 12..15 -> 0.90 // Lunch
                in 16..18 -> 0.70 // Evening tea/snacks
                in 19..23 -> 0.95 // Dinner
                else -> 0.3
            }
            "Groceries" -> when (hourOfDay) {
                in 6..9 -> 0.85   // Morning milk / dairy / veggies
                in 17..21 -> 0.85 // Evening grocery shopping
                else -> 0.4
            }
            "Transport" -> when (hourOfDay) {
                in 8..11 -> 0.90  // Morning commute
                in 17..21 -> 0.90 // Evening commute
                else -> 0.4
            }
            "Subscriptions" -> when (hourOfDay) {
                in 0..5 -> 0.85   // Auto-renewals usually hit at midnight/early hours
                else -> 0.4
            }
            "Vegetables & Mandi" -> when (hourOfDay) {
                in 6..10 -> 0.90  // Morning mandi / sabzi vendors
                in 17..21 -> 0.90 // Evening sabzi shopping
                else -> 0.40
            }
            "Dairy & Milk" -> when (hourOfDay) {
                in 5..10 -> 0.95  // Morning fresh milk delivery / dairy booth
                in 17..20 -> 0.85 // Evening milk
                else -> 0.30
            }
            "Personal Care & Salon" -> when (hourOfDay) {
                in 10..20 -> 0.80 // Salon hours
                else -> 0.20
            }
            else -> 0.5
        }
    }

    private fun calculateUpiMerchantBonus(category: String, upiId: String, merchant: String): Double {
        if (upiId.isBlank() && merchant.isBlank()) return 0.0
        val targetKeywords = RULES[category] ?: return 0.0

        for (kw in targetKeywords) {
            if (merchant.contains(kw) || upiId.contains(kw)) {
                return 1.0
            }
        }
        return 0.0
    }

    // ── Keyword Rules Dictionary ────────────────────────────────────────

    /**
     * Returns the top [limit] category recommendations sorted by confidence score.
     * Essential for 1-tap interactive push notifications and micro-spend catch-up deck.
     */
    fun getTopCategorySuggestions(
        message: String = "",
        upiId: String? = null,
        merchant: String? = null,
        amount: Double = 0.0,
        hourOfDay: Int = -1,
        limit: Int = 3
    ): List<String> {
        val result = classifyWithConfidence(
            message = message,
            upiId = upiId,
            merchant = merchant,
            amount = amount,
            hourOfDay = hourOfDay
        )

        val list = mutableListOf<String>()
        if (result.category != "Others") {
            list.add(result.category)
        }
        if (result.alternateCategory != null && result.alternateCategory != "Others" && !list.contains(result.alternateCategory)) {
            list.add(result.alternateCategory)
        }

        val contextualFallbacks = when {
            hourOfDay in 6..11 -> listOf("Food & Dining", "Vegetables & Mandi", "Dairy & Milk", "Transport")
            hourOfDay in 12..16 -> listOf("Food & Dining", "Groceries", "Shopping")
            hourOfDay in 17..21 -> listOf("Vegetables & Mandi", "Food & Dining", "Groceries", "Transport")
            else -> listOf("Food & Dining", "Groceries", "Shopping", "Entertainment")
        }

        for (fallback in contextualFallbacks) {
            if (list.size >= limit) break
            if (!list.contains(fallback)) {
                list.add(fallback)
            }
        }

        return list.take(limit)
    }

    companion object {
        val ALL_CATEGORIES = listOf(
            "Food & Dining", "Groceries", "Vegetables & Mandi", "Dairy & Milk",
            "Personal Care & Salon", "Domestic & Services", "Pooja & Devotion",
            "Subscriptions", "Rent", "Transport", "Shopping", "Utilities & Bills",
            "Insurance", "Health & Wellness", "Entertainment", "Investment",
            "Education", "Recharge", "Transfer"
        )

        private val INCOME_KEYWORDS = listOf(
            "salary", "payroll", "stipend", "pension", "interest credited",
            "dividend", "commission", "freelance", "consulting", "neft from",
            "imps from", "rtgs from", "ach credit", "direct credit"
        )

        private val RULES: Map<String, List<String>> = mapOf(
            "Food & Dining" to listOf(
                "swiggy", "zomato", "restaurant", "cafe", "food", "dining", "mcdonalds",
                "kfc", "starbucks", "dominos", "domino", "pizza", "burger", "biryani",
                "eatsure", "box8", "faasos", "eatfit", "magicpin", "dineout", "behrouz",
                "lunchbox", "subway", "haldiram", "baskinrobbins", "chaayos", "chai",
                "juice", "bakery", "pastry", "coffee", "dhaba", "bhojanalaya", "barbeque",
                "sweets", "mithai", "kitchen", "canteen", "mess", "tiffin"
            ),
            "Groceries" to listOf(
                "bigbasket", "grofers", "instamart", "blinkit", "zepto", "licious",
                "fresho", "dunzo daily", "supermarket", "kirana", "grocery", "jiomart",
                "spencers", "nature basket", "organic", "ration", "store", "provisions",
                "general store", "mart", "more retail", "smart point"
            ),
            "Vegetables & Mandi" to listOf(
                "mandi", "sabzi", "sabji", "vegetables", "vegetable", "bhindi", "aloo",
                "pyaaz", "tomato", "fruits", "fruit", "thela", "vendor", "farmer",
                "apna mandi", "safal", "kisan", "green grocer", "fresh produce", "tarkari"
            ),
            "Dairy & Milk" to listOf(
                "milk", "dairy", "doodh", "mother dairy", "amul", "country delight",
                "nandini", "milkbasket", "bbdaily", "curd", "paneer", "dahi", "ghee",
                "parag", "verka", "saras", "heritage foods", "avadh", "dairy parlour"
            ),
            "Personal Care & Salon" to listOf(
                "salon", "parlour", "parlor", "barber", "haircut", "shaving", "grooming",
                "spa", "naturals", "geetanjali", "jawed habib", "vlcc", "enrich",
                "bodycraft", "looks salon", "hair studio", "beauty clinic"
            ),
            "Domestic & Services" to listOf(
                "maid", "bai", "cook", "driver", "dhobi", "presswala", "laundry",
                "dryclean", "ironing", "cleaner", "car wash", "sweeper", "plumber",
                "electrician", "carpenter", "urban company", "helper", "wages",
                "salary to maid", "kamwali"
            ),
            "Pooja & Devotion" to listOf(
                "mandir", "temple", "pooja", "puja", "prasad", "dakshina", "agarbatti",
                "phool", "flowers", "dargah", "gurudwara", "church", "trust", "donation",
                "bhajan", "samiti", "ashram"
            ),
            "Subscriptions" to listOf(
                "netflix", "spotify", "prime video", "hotstar", "disney", "youtube premium",
                "apple music", "gaana", "notion", "figma", "canva", "grammarly", "github",
                "chatgpt", "openai", "linkedin premium", "icloud", "google one",
                "subscription", "recurring", "membership", "patreon", "medium"
            ),
            "Rent" to listOf(
                "rent", "house rent", "flat rent", "pg rent", "nobroker", "nestaway",
                "landlord", "maintenance fee", "society charges", "room rent"
            ),
            "Transport" to listOf(
                "uber", "ola", "rapido", "irctc", "metro", "petrol", "fuel", "shell",
                "hpcl", "bpcl", "iocl", "makemytrip", "indigo", "goibibo", "yatra",
                "cleartrip", "vistara", "spicejet", "airindia", "parking", "toll",
                "fastag", "nhai", "redbus", "abhibus", "blablacar", "drivezy", "bounce",
                "cab", "taxi", "diesel", "cng", "indian oil", "bharat petroleum"
            ),
            "Shopping" to listOf(
                "amazon", "flipkart", "myntra", "ajio", "nykaa", "meesho", "shopping",
                "mall", "dmart", "reliance", "tatacliq", "snapdeal", "shopclues",
                "firstcry", "bewakoof", "decathlon", "croma", "vijaysales", "lenskart",
                "pepperfry", "ikea", "urbanladder", "fabindia", "westside", "pantaloons",
                "shoppers", "lifestyle", "max fashion", "h&m", "zara", "ekart",
                "footwear", "clothing", "apparel", "jewellers", "bazaar"
            ),
            "Utilities & Bills" to listOf(
                "electricity", "water", "gas bill", "broadband", "jio", "airtel", "vi ",
                "bsnl", "bill pay", "maintenance", "internet", "wifi", "tatasky", "dth",
                "dish tv", "postpaid", "prepaid", "landline", "piped gas", "electricity bill",
                "power corp", "vidyut"
            ),
            "Insurance" to listOf(
                "insurance", "lic", "policybazaar", "acko", "digit insurance", "hdfc ergo",
                "star health", "max life", "term insurance", "health insurance", "car insurance",
                "bike insurance", "pramercia", "bajaj allianz", "icici lombard"
            ),
            "Health & Wellness" to listOf(
                "pharmacy", "apollo", "1mg", "tata 1mg", "hospital", "doctor", "gym",
                "cult.fit", "cultfit", "curefit", "medical", "medplus", "netmeds",
                "pharmeasy", "practo", "lybrate", "clinic", "diagnostic", "lab test",
                "healthian", "thyrocare", "dental", "eye care", "medicos", "druggist",
                "chemist", "fitness"
            ),
            "Entertainment" to listOf(
                "bookmyshow", "pvr", "inox", "gaming", "steam", "playstation", "xbox",
                "jiocinema", "zee5", "sonyliv", "voot", "mubi", "cinema", "theatre",
                "concert", "event", "carnival"
            ),
            "Investment" to listOf(
                "zerodha", "groww", "upstox", "sip", "mutual fund", "stock", "demat",
                "trading", "coin", "kuvera", "smallcase", "angel", "edelweiss",
                "paytm money", "et money", "ppf", "nps", "fixed deposit", "navi",
                "slice", "jupiter", "fi money", "bonds", "gold"
            ),
            "Education" to listOf(
                "school", "college", "university", "tuition", "course", "udemy",
                "coursera", "byjus", "unacademy", "vedantu", "exam", "books",
                "stationery", "library", "coaching", "fees", "academy"
            ),
            "Recharge" to listOf(
                "recharge", "top up", "topup", "mobile recharge", "data pack",
                "talktime", "dth recharge", "metro recharge"
            ),
            "Transfer" to listOf(
                "self transfer", "fund transfer", "funds transfer", "transferred to own",
                "transfer to own", "transferred to a/c", "transfer to a/c", "internal transfer",
                "wallet transfer", "bank transfer", "account transfer", "transfer between"
            )
        )
    }
}
