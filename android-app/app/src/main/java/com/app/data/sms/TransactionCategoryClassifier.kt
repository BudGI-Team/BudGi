package com.app.data.sms

import java.util.Locale
import javax.inject.Inject

/**
 * Intelligent transaction category classifier.
 * Uses merchant name, UPI ID, and SMS body keywords.
 */
class TransactionCategoryClassifier @Inject constructor() {

    /**
     * Classify with optional UPI ID and merchant for better accuracy.
     */
    fun classify(message: String, upiId: String? = null, merchant: String? = null): String {
        val normalized = message.lowercase(Locale.US)
        val upiLower = upiId?.lowercase(Locale.US) ?: ""
        val merchantLower = merchant?.lowercase(Locale.US) ?: ""

        // Combine all signals for matching
        val combined = "$normalized $upiLower $merchantLower"

        // Check salary/income first (NEFT credits from companies)
        if (INCOME_KEYWORDS.any { combined.contains(it) }) return "Income"

        // Check each category
        for ((category, keywords) in rules) {
            if (keywords.any { combined.contains(it) }) return category
        }

        // UPI-specific fallback: check for person-to-person bank transfers
        if (upiLower.isNotBlank()) {
            val localPart = upiLower.substringBefore("@")
            val isPhoneOrNumericUpi = localPart.matches(Regex("""\d{10}""")) || localPart.matches(Regex("""\d+"""))
            val isExplicitTransferAction = normalized.contains("transferred") || normalized.contains("transfer") || normalized.contains("sent")
            if (isPhoneOrNumericUpi && isExplicitTransferAction) {
                return "Transfer"
            }
        }

        return "Others"
    }

    /**
     * Legacy single-argument classify for backward compatibility.
     */
    fun classify(message: String): String = classify(message, null, null)

    private val INCOME_KEYWORDS = listOf(
        "salary", "payroll", "stipend", "pension", "interest credited",
        "dividend", "commission", "freelance", "consulting",
        "neft from", "imps from", "rtgs from"
    )

    private val rules: Map<String, List<String>> = mapOf(
        "Food & Dining" to listOf(
            "swiggy", "zomato", "restaurant", "cafe",
            "food", "dining", "mcdonalds", "kfc", "starbucks", "dominos",
            "domino", "pizza", "burger", "biryani", "dunzo", "eatsure",
            "box8", "faasos", "eatfit", "magicpin", "dineout",
            "behrouz", "lunchbox", "subway", "haldiram", "baskinrobbins",
            "chaayos", "chai", "juice", "bakery", "pastry"
        ),
        "Groceries" to listOf(
            "bigbasket", "grofers", "instamart", "blinkit", "zepto", "licious",
            "country delight", "milkbasket", "bbdaily", "fresho", "dunzo daily",
            "milk", "dairy", "supermarket", "kirana", "grocery", "jiomart",
            "spencers", "nature basket", "organic", "vegetables", "fruits", "ration"
        ),
        "Subscriptions" to listOf(
            "netflix", "spotify", "prime video", "hotstar", "disney", "youtube premium",
            "apple music", "gaana", "notion", "figma", "canva", "grammarly", "github",
            "chatgpt", "openai", "linkedin premium", "icloud", "google one",
            "subscription", "recurring", "membership"
        ),
        "Rent" to listOf(
            "rent", "house rent", "flat rent", "pg rent", "nobroker", "nestaway", "landlord"
        ),
        "Transport" to listOf(
            "uber", "ola", "rapido", "irctc", "metro", "petrol", "fuel",
            "shell", "hpcl", "bpcl", "iocl", "makemytrip", "indigo",
            "goibibo", "yatra", "cleartrip", "vistara", "spicejet",
            "airindia", "parking", "toll", "fastag", "nhai",
            "redbus", "abhibus", "blablacar", "drivezy", "bounce"
        ),
        "Shopping" to listOf(
            "amazon", "flipkart", "myntra", "ajio", "nykaa", "meesho",
            "shopping", "mall", "dmart", "reliance", "tatacliq",
            "snapdeal", "shopclues", "firstcry", "bewakoof", "decathlon",
            "croma", "vijaysales", "lenskart", "pepperfry", "ikea",
            "urbanladder", "fabindia", "westside", "pantaloons",
            "shoppers", "lifestyle", "max fashion", "h&m", "zara",
            "ekart"
        ),
        "Utilities & Bills" to listOf(
            "electricity", "water", "gas bill", "broadband", "jio",
            "airtel", "vi ", "bsnl", "bill pay",
            "maintenance", "internet", "wifi", "tatasky", "dth",
            "dish tv", "postpaid", "prepaid", "landline"
        ),
        "Insurance" to listOf(
            "insurance", "lic", "policybazaar", "acko", "digit insurance",
            "hdfc ergo", "star health", "max life", "term insurance", "health insurance",
            "car insurance", "bike insurance"
        ),
        "Health & Wellness" to listOf(
            "pharmacy", "apollo", "1mg", "tata 1mg", "hospital", "doctor", "gym",
            "cult.fit", "cultfit", "curefit", "medical", "medplus", "netmeds",
            "pharmeasy", "practo", "lybrate", "clinic", "diagnostic",
            "lab test", "healthian", "thyrocare", "dental", "eye care"
        ),
        "Entertainment" to listOf(
            "bookmyshow", "pvr", "inox", "gaming", "steam", "playstation",
            "xbox", "jiocinema", "zee5", "sonyliv", "voot", "mubi"
        ),
        "Investment" to listOf(
            "zerodha", "groww", "upstox", "sip", "mutual fund",
            "stock", "demat", "trading", "coin", "kuvera", "smallcase",
            "angel", "edelweiss", "paytm money", "et money", "ppf", "nps",
            "fixed deposit", "navi", "slice", "jupiter", "fi money"
        ),
        "Education" to listOf(
            "school", "college", "university", "tuition", "course",
            "udemy", "coursera", "byjus", "unacademy", "vedantu",
            "exam", "books", "stationery", "library", "coaching"
        ),
        "Recharge" to listOf(
            "recharge", "top up", "topup", "mobile recharge",
            "data pack", "talktime"
        ),
        "Transfer" to listOf(
            "self transfer", "fund transfer", "funds transfer", "transferred to own",
            "transfer to own", "transferred to a/c", "transfer to a/c", "internal transfer",
            "wallet transfer", "bank transfer", "account transfer", "transfer between"
        )
    )
}
