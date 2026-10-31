package com.app.data.sms

import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Type of merchant identified from a UPI ID or merchant string.
 */
enum class MerchantType {
    KNOWN_BRAND,        // Swiggy, Zomato, Amazon, Uber, etc.
    LOCAL_VENDOR_QR,    // PaytmQR, BharatPe, PhonePe merchant QR codes
    ONLINE_MERCHANT,    // Payment gateway handles (Razorpay, PayU, Cashfree)
    PERSON,             // 10-digit phone number or personal VPA
    UTILITY,            // Electricity, water, broadband, gas
    GOVERNMENT,         // IRCTC, tax, toll/Fastag
    UNKNOWN
}

/**
 * Result of looking up a UPI ID or merchant in [UpiMerchantRegistry].
 */
data class UpiMerchantInfo(
    val merchantName: String,
    val category: String,
    val merchantType: MerchantType,
    val confidence: Double = 0.90
)

/**
 * UPI Merchant Intelligence Registry
 *
 * Pre-populated on-device database mapping Indian UPI VPAs, merchant IDs,
 * and gateway patterns to clean merchant names and accurate categories.
 *
 * Lookup Priority:
 * 1. Exact UPI ID match (e.g. "swiggy@axisbank")
 * 2. Prefix match on local part (e.g. "swiggy.order123" → Swiggy)
 * 3. Payment gateway handle detection (e.g. "@razorpay" → Shopping)
 * 4. Structural analysis (10-digit phone number → Person / Transfer; "paytmqr" → Local Vendor)
 */
@Singleton
class UpiMerchantRegistry @Inject constructor() {

    companion object {
        // Payment gateway handles
        private val GATEWAY_HANDLES = setOf(
            "razorpay", "payu", "cashfree", "billdesk", "ccavenue", "paygate",
            "instamojo", "simpl", "lazypay", "pinelabs"
        )

        // Standard Indian bank handles
        private val BANK_HANDLES = setOf(
            "ybl", "paytm", "okaxis", "okhdfcbank", "okicici", "oksbi",
            "apl", "ibl", "axl", "upi", "kotak", "icici", "sbi",
            "hdfcbank", "axisbank", "rbl", "federal", "idbi", "boi",
            "indus", "unionbank", "pnb", "canara", "bob", "barodampay",
            "jupiteraxis", "freecharge", "amazonpay", "slice", "niyoicici",
            "postbank", "dbs", "sc", "hsbc", "yesbank", "citi"
        )

        // ── Brand dictionary: prefix/keyword → (Clean Name, Category) ──
        private val BRAND_MAP: Map<String, Pair<String, String>> = mapOf(
            // 🍔 Food & Dining
            "swiggy" to Pair("Swiggy", "Food & Dining"),
            "zomato" to Pair("Zomato", "Food & Dining"),
            "dominos" to Pair("Domino's Pizza", "Food & Dining"),
            "domino" to Pair("Domino's Pizza", "Food & Dining"),
            "mcdonalds" to Pair("McDonald's", "Food & Dining"),
            "mcd" to Pair("McDonald's", "Food & Dining"),
            "kfc" to Pair("KFC", "Food & Dining"),
            "pizzahut" to Pair("Pizza Hut", "Food & Dining"),
            "burgerking" to Pair("Burger King", "Food & Dining"),
            "starbucks" to Pair("Starbucks", "Food & Dining"),
            "subway" to Pair("Subway", "Food & Dining"),
            "haldiram" to Pair("Haldiram's", "Food & Dining"),
            "baskinrobbins" to Pair("Baskin Robbins", "Food & Dining"),
            "chaayos" to Pair("Chaayos", "Food & Dining"),
            "chai" to Pair("Chai Point", "Food & Dining"),
            "eatsure" to Pair("EatSure", "Food & Dining"),
            "box8" to Pair("Box8", "Food & Dining"),
            "faasos" to Pair("Faasos", "Food & Dining"),
            "behrouz" to Pair("Behrouz Biryani", "Food & Dining"),
            "eatfit" to Pair("EatFit", "Food & Dining"),
            "dineout" to Pair("Dineout", "Food & Dining"),
            "magicpin" to Pair("Magicpin", "Food & Dining"),
            "barbequenation" to Pair("Barbeque Nation", "Food & Dining"),
            "thirdwavecoffee" to Pair("Third Wave Coffee", "Food & Dining"),
            "bluechai" to Pair("Blue Tokai", "Food & Dining"),
            "bluetokai" to Pair("Blue Tokai", "Food & Dining"),
            "wowmomo" to Pair("Wow! Momo", "Food & Dining"),
            "ovenstory" to Pair("Oven Story", "Food & Dining"),
            "freshmenu" to Pair("FreshMenu", "Food & Dining"),

            // 🛒 Groceries & Daily Needs
            "bigbasket" to Pair("BigBasket", "Groceries"),
            "bbdaily" to Pair("BB Daily", "Groceries"),
            "blinkit" to Pair("Blinkit", "Groceries"),
            "grofers" to Pair("Blinkit", "Groceries"),
            "zepto" to Pair("Zepto", "Groceries"),
            "instamart" to Pair("Swiggy Instamart", "Groceries"),
            "jiomart" to Pair("JioMart", "Groceries"),
            "licious" to Pair("Licious", "Groceries"),
            "fresho" to Pair("Fresho", "Vegetables & Mandi"),
            "safal" to Pair("Safal", "Vegetables & Mandi"),
            "apnamandi" to Pair("Apna Mandi", "Vegetables & Mandi"),
            "countrydelight" to Pair("Country Delight", "Dairy & Milk"),
            "milkbasket" to Pair("Milkbasket", "Dairy & Milk"),
            "motherdairy" to Pair("Mother Dairy", "Dairy & Milk"),
            "amul" to Pair("Amul", "Dairy & Milk"),
            "nandini" to Pair("Nandini Dairy", "Dairy & Milk"),
            "saras" to Pair("Saras Dairy", "Dairy & Milk"),
            "verka" to Pair("Verka Dairy", "Dairy & Milk"),
            "urbancompany" to Pair("Urban Company", "Domestic & Services"),
            "geetanjali" to Pair("Geetanjali Salon", "Personal Care & Salon"),
            "naturals" to Pair("Naturals Salon", "Personal Care & Salon"),
            "jawedhabib" to Pair("Jawed Habib", "Personal Care & Salon"),
            "iskcon" to Pair("ISKCON", "Pooja & Devotion"),
            "dunzo" to Pair("Dunzo", "Groceries"),
            "dmart" to Pair("DMart", "Groceries"),
            "spencers" to Pair("Spencer's Retail", "Groceries"),
            "naturesbasket" to Pair("Nature's Basket", "Groceries"),
            "meatigo" to Pair("Meatigo", "Groceries"),

            // 🚕 Transport & Travel
            "uber" to Pair("Uber", "Transport"),
            "ola" to Pair("Ola Cabs", "Transport"),
            "olacabs" to Pair("Ola Cabs", "Transport"),
            "rapido" to Pair("Rapido", "Transport"),
            "irctc" to Pair("IRCTC", "Transport"),
            "makemytrip" to Pair("MakeMyTrip", "Transport"),
            "mmt" to Pair("MakeMyTrip", "Transport"),
            "goibibo" to Pair("Goibibo", "Transport"),
            "cleartrip" to Pair("Cleartrip", "Transport"),
            "yatra" to Pair("Yatra", "Transport"),
            "ixigo" to Pair("ixigo", "Transport"),
            "redbus" to Pair("redBus", "Transport"),
            "abhibus" to Pair("AbhiBus", "Transport"),
            "indigo" to Pair("IndiGo", "Transport"),
            "airindia" to Pair("Air India", "Transport"),
            "vistara" to Pair("Vistara", "Transport"),
            "spicejet" to Pair("SpiceJet", "Transport"),
            "akasa" to Pair("Akasa Air", "Transport"),
            "fastag" to Pair("FASTag Toll", "Transport"),
            "nhai" to Pair("NHAI Toll", "Transport"),
            "ihmcl" to Pair("FASTag Toll", "Transport"),
            "hpcl" to Pair("HP Petrol Pump", "Transport"),
            "bpcl" to Pair("Bharat Petroleum", "Transport"),
            "iocl" to Pair("Indian Oil", "Transport"),
            "shell" to Pair("Shell Petrol", "Transport"),
            "bounce" to Pair("Bounce", "Transport"),
            "yulu" to Pair("Yulu Bikes", "Transport"),

            // 🛍️ Shopping & E-Commerce
            "amazon" to Pair("Amazon", "Shopping"),
            "flipkart" to Pair("Flipkart", "Shopping"),
            "myntra" to Pair("Myntra", "Shopping"),
            "ajio" to Pair("Ajio", "Shopping"),
            "nykaa" to Pair("Nykaa", "Shopping"),
            "meesho" to Pair("Meesho", "Shopping"),
            "tatacliq" to Pair("Tata CLiQ", "Shopping"),
            "croma" to Pair("Croma", "Shopping"),
            "vijaysales" to Pair("Vijay Sales", "Shopping"),
            "lenskart" to Pair("Lenskart", "Shopping"),
            "pepperfry" to Pair("Pepperfry", "Shopping"),
            "ikea" to Pair("IKEA", "Shopping"),
            "urbanladder" to Pair("Urban Ladder", "Shopping"),
            "decathlon" to Pair("Decathlon", "Shopping"),
            "firstcry" to Pair("FirstCry", "Shopping"),
            "bewakoof" to Pair("Bewakoof", "Shopping"),
            "zara" to Pair("Zara", "Shopping"),
            "hm" to Pair("H&M", "Shopping"),
            "westside" to Pair("Westside", "Shopping"),
            "pantaloons" to Pair("Pantaloons", "Shopping"),
            "fabindia" to Pair("Fabindia", "Shopping"),
            "snitch" to Pair("Snitch", "Shopping"),
            "zudio" to Pair("Zudio", "Shopping"),
            "reliancedigital" to Pair("Reliance Digital", "Shopping"),

            // 📺 Subscriptions & Media
            "netflix" to Pair("Netflix", "Subscriptions"),
            "spotify" to Pair("Spotify", "Subscriptions"),
            "hotstar" to Pair("Disney+ Hotstar", "Subscriptions"),
            "primevideo" to Pair("Amazon Prime", "Subscriptions"),
            "apple" to Pair("Apple Services", "Subscriptions"),
            "googleplay" to Pair("Google Play", "Subscriptions"),
            "googleone" to Pair("Google One", "Subscriptions"),
            "youtube" to Pair("YouTube Premium", "Subscriptions"),
            "chatgpt" to Pair("OpenAI / ChatGPT", "Subscriptions"),
            "openai" to Pair("OpenAI", "Subscriptions"),
            "notion" to Pair("Notion", "Subscriptions"),
            "figma" to Pair("Figma", "Subscriptions"),
            "canva" to Pair("Canva", "Subscriptions"),
            "github" to Pair("GitHub", "Subscriptions"),
            "linkedin" to Pair("LinkedIn", "Subscriptions"),
            "jiocinema" to Pair("JioCinema", "Subscriptions"),
            "zee5" to Pair("ZEE5", "Subscriptions"),
            "sonyliv" to Pair("Sony LIV", "Subscriptions"),
            "audible" to Pair("Audible", "Subscriptions"),

            // 💡 Utilities & Telecom
            "airtel" to Pair("Airtel", "Utilities & Bills"),
            "jio" to Pair("Reliance Jio", "Utilities & Bills"),
            "vi" to Pair("Vodafone Idea", "Utilities & Bills"),
            "vodafone" to Pair("Vodafone Idea", "Utilities & Bills"),
            "bsnl" to Pair("BSNL", "Utilities & Bills"),
            "tatasky" to Pair("Tata Play", "Utilities & Bills"),
            "tataplay" to Pair("Tata Play", "Utilities & Bills"),
            "dishtv" to Pair("Dish TV", "Utilities & Bills"),
            "bescom" to Pair("BESCOM Electricity", "Utilities & Bills"),
            "msedcl" to Pair("MSEDCL Electricity", "Utilities & Bills"),
            "tatapower" to Pair("Tata Power", "Utilities & Bills"),
            "adani" to Pair("Adani Electricity", "Utilities & Bills"),
            "torrentpower" to Pair("Torrent Power", "Utilities & Bills"),
            "actfibernet" to Pair("ACT Fibernet", "Utilities & Bills"),

            // 💊 Health & Wellness
            "apollo" to Pair("Apollo Pharmacy", "Health & Wellness"),
            "pharmeasy" to Pair("PharmEasy", "Health & Wellness"),
            "1mg" to Pair("Tata 1mg", "Health & Wellness"),
            "tata1mg" to Pair("Tata 1mg", "Health & Wellness"),
            "netmeds" to Pair("Netmeds", "Health & Wellness"),
            "medplus" to Pair("MedPlus", "Health & Wellness"),
            "practo" to Pair("Practo", "Health & Wellness"),
            "cultfit" to Pair("Cult.fit", "Health & Wellness"),
            "curefit" to Pair("Cult.fit", "Health & Wellness"),
            "healthians" to Pair("Healthians", "Health & Wellness"),
            "thyrocare" to Pair("Thyrocare", "Health & Wellness"),
            "maxhealthcare" to Pair("Max Healthcare", "Health & Wellness"),
            "fortis" to Pair("Fortis Hospital", "Health & Wellness"),

            // 📈 Investment & Wealth
            "zerodha" to Pair("Zerodha", "Investment"),
            "groww" to Pair("Groww", "Investment"),
            "upstox" to Pair("Upstox", "Investment"),
            "angelone" to Pair("Angel One", "Investment"),
            "smallcase" to Pair("smallcase", "Investment"),
            "kuvera" to Pair("Kuvera", "Investment"),
            "etmoney" to Pair("ET Money", "Investment"),
            "paytmmoney" to Pair("Paytm Money", "Investment"),
            "indmoney" to Pair("INDmoney", "Investment"),
            "navi" to Pair("Navi", "Investment"),
            "jupiter" to Pair("Jupiter Money", "Investment"),
            "fimoney" to Pair("Fi Money", "Investment"),

            // 🛡️ Insurance
            "lic" to Pair("LIC of India", "Insurance"),
            "policybazaar" to Pair("Policybazaar", "Insurance"),
            "acko" to Pair("Acko Insurance", "Insurance"),
            "godigit" to Pair("Digit Insurance", "Insurance"),
            "hdfcergo" to Pair("HDFC ERGO", "Insurance"),
            "starhealth" to Pair("Star Health Insurance", "Insurance"),
            "maxlife" to Pair("Max Life Insurance", "Insurance"),
            "iciciprulife" to Pair("ICICI Prudential", "Insurance"),

            // 🎓 Education
            "udemy" to Pair("Udemy", "Education"),
            "coursera" to Pair("Coursera", "Education"),
            "unacademy" to Pair("Unacademy", "Education"),
            "byjus" to Pair("BYJU'S", "Education"),
            "vedantu" to Pair("Vedantu", "Education"),
            "pw" to Pair("Physics Wallah", "Education"),
            "physicswallah" to Pair("Physics Wallah", "Education"),

            // 🎟️ Entertainment
            "bookmyshow" to Pair("BookMyShow", "Entertainment"),
            "pvr" to Pair("PVR Cinemas", "Entertainment"),
            "inox" to Pair("INOX Leisure", "Entertainment"),
            "cinepolis" to Pair("Cinépolis", "Entertainment"),
            "steam" to Pair("Steam Games", "Entertainment"),
            "playstation" to Pair("PlayStation Network", "Entertainment"),

            // 🏠 Rent & Housing
            "nobroker" to Pair("NoBroker", "Rent"),
            "nestaway" to Pair("Nestaway", "Rent"),
            "housing" to Pair("Housing.com", "Rent")
        )
    }

    /**
     * Looks up a UPI VPA (e.g. "swiggy@axisbank" or "paytmqr2810050@paytm")
     * or merchant string and returns structured metadata if recognized.
     */
    fun lookup(upiIdOrMerchant: String?): UpiMerchantInfo? {
        if (upiIdOrMerchant.isNullOrBlank()) return null
        val lower = upiIdOrMerchant.lowercase(Locale.US).trim()

        val isUpiVpa = lower.contains("@")
        val localPart = if (isUpiVpa) lower.substringBefore("@") else lower
        val handlePart = if (isUpiVpa) lower.substringAfter("@") else ""

        // 1. P2P: 10-digit mobile number in local part
        if (localPart.matches(Regex("""^\d{10}$""")) || localPart.matches(Regex("""^91\d{10}$"""))) {
            return UpiMerchantInfo(
                merchantName = "Personal Contact",
                category = "Transfer",
                merchantType = MerchantType.PERSON,
                confidence = 0.95
            )
        }

        // 2. Direct Brand Prefix Lookup in brand dictionary
        for ((key, pair) in BRAND_MAP) {
            if (localPart.startsWith(key) || localPart.contains(key)) {
                return UpiMerchantInfo(
                    merchantName = pair.first,
                    category = pair.second,
                    merchantType = MerchantType.KNOWN_BRAND,
                    confidence = 0.95
                )
            }
        }

        // 3. Local Vendor QR Code patterns
        if (localPart.startsWith("paytmqr") || localPart.startsWith("bharatpe") ||
            localPart.startsWith("phonepeqr") || localPart.contains("qr")
        ) {
            // Attempt to extract readable merchant name from QR ID if available
            val readableName = extractReadableFromQr(localPart)
            return UpiMerchantInfo(
                merchantName = readableName ?: "Local Vendor (QR)",
                category = "Groceries", // Most Indian UPI QR payments at retail are Kirana / Groceries
                merchantType = MerchantType.LOCAL_VENDOR_QR,
                confidence = 0.65 // Needs confirmation from user
            )
        }

        // 4. Online Payment Gateway handle lookup (e.g. merchant@razorpay)
        if (GATEWAY_HANDLES.contains(handlePart)) {
            val cleanName = localPart
                .replace(Regex("""[._-]"""), " ")
                .split(" ")
                .filter { it.isNotBlank() && !it.matches(Regex("""\d+""")) }
                .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }

            return UpiMerchantInfo(
                merchantName = cleanName.ifBlank { "Online Merchant" },
                category = "Shopping",
                merchantType = MerchantType.ONLINE_MERCHANT,
                confidence = 0.75
            )
        }

        return null
    }

    /**
     * Extracts readable letters from merchant QR strings (e.g. "bharatpe.sharmasweets" -> "Sharma Sweets")
     */
    private fun extractReadableFromQr(localPart: String): String? {
        val cleaned = localPart
            .replace(Regex("""^(paytmqr|bharatpe|phonepeqr)"""), "")
            .replace(Regex("""\d+"""), "") // strip digits
            .replace(Regex("""[._-]"""), " ")
            .trim()

        if (cleaned.length >= 3) {
            return cleaned.split(" ")
                .filter { it.isNotBlank() }
                .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
        }
        return null
    }
}

