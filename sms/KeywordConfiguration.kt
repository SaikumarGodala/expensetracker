package com.saikumar.expensetracker.sms

/**
 * Centralized keyword configuration for SMS transaction classification.
 * Single source of truth for all keyword lists used in pattern matching.
 *
 * PHASE 7d OPTIMIZATION: Includes both List (for iteration) and Set (for lookups).
 * Sets provide O(1) average lookup vs O(n) for lists. Use .contains() on Sets for fast filtering.
 * Example: CC_EXPLICIT_KEYWORDS_SET.contains(keyword) // O(1) instead of O(n)
 *
 * Organization:
 * - SALARY keywords: Identify salary/income transactions
 * - EXCLUSION keywords: Exclude certain transaction types
 * - CREDIT CARD keywords: CC payment detection (explicit, issuers, verbs, context)
 * - CREDIT CARD SPEND: CC spend detection keywords
 * - CARD NETWORKS: Identify card usage
 * - MERCHANT PATTERNS: Map merchant names to categories
 * - NOISE PATTERNS: Remove noise from extraction
 * - TRANSFER PATTERNS: Identify P2P transfers
 * - PAYMENT PATTERNS: Identify payment types
 * - EXTRACTION PATTERNS: Extract merchant/counterparty info
 */
object KeywordConfiguration {

    // ============ SALARY & INCOME KEYWORDS ============
    val SALARY_KEYWORDS = listOf(
        "SALARY", "PAYROLL", "SAL CREDIT", "MONTHLY SALARY", "EMP SAL",
        "EPF", "PROVIDENT FUND", "PF CONTRIBUTION", "PF CREDIT"
    )
    // Phase 7d: HashSet for O(1) lookups instead of O(n) list searches
    val SALARY_KEYWORDS_SET = SALARY_KEYWORDS.toSet()

    val EXCLUSION_KEYWORDS = listOf(
        "REFUND", "CASHBACK", "REVERSAL", "REWARD", "INCENTIVE",
        "BONUS", "INTEREST", "WALLET", "LOAN"
    )
    val EXCLUSION_KEYWORDS_SET = EXCLUSION_KEYWORDS.toSet()

    // ============ CREDIT CARD PAYMENT DETECTION ============
    // Priority 1: Explicit CC payment keywords (99% confidence)
    val CC_EXPLICIT_KEYWORDS = listOf(
        "CC PAYMENT", "CC PAY", "CC BILL",
        "CREDIT CARD PAYMENT", "CREDIT CARD BILL PAYMENT",
        "CARD BILL PAYMENT", "CREDITCARD BILL", "CREDITCARD PAYMENT",
        "PAYMENT RECEIVED ON CREDIT CARD", "RECEIVED ON YOUR CREDIT CARD",
        "CREDITED TO YOUR CREDIT CARD", "CREDITED TO YOUR CC",
        "PAYMENT RECEIVED TOWARDS YOUR CREDIT CARD"
    )
    val CC_EXPLICIT_KEYWORDS_SET = CC_EXPLICIT_KEYWORDS.toSet()

    // Known Indian CC issuers for context matching
    val CC_ISSUERS = listOf(
        "HDFC", "ICICI", "SBI CARD", "AXIS", "KOTAK", "AMEX",
        "AMERICAN EXPRESS", "CITI", "CITIBANK", "RBL", "INDUSIND",
        "YES BANK", "HSBC", "STANDARD CHARTERED", "AU BANK", "IDFC FIRST",
        "BOB", "BANK OF BARODA", "PNB", "CANARA", "UNION BANK"
    )
    val CC_ISSUERS_SET = CC_ISSUERS.toSet()

    // Payment-indicating verbs
    val CC_PAYMENT_VERBS = listOf(
        "PAID", "PAYMENT", "PAY", "TRANSFERRED", "DEBITED",
        "AUTO-DEBIT", "AUTODEBIT", "AUTOPAY", "AUTO PAY",
        "NACH", "ECS", "SI ", "STANDING INSTRUCTION"
    )
    val CC_PAYMENT_VERBS_SET = CC_PAYMENT_VERBS.toSet()

    // Card context keywords
    val CC_CARD_CONTEXT = listOf(
        "CC", "CREDIT CARD", "CARD", "CREDITCARD"
    )
    val CC_CARD_CONTEXT_SET = CC_CARD_CONTEXT.toSet()

    // Third-party CC payment apps (Priority 3: 90% confidence)
    val CC_PAYMENT_APPS = listOf(
        "CRED"  // CRED is ONLY for CC payments
    )
    val CC_PAYMENT_APPS_SET = CC_PAYMENT_APPS.toSet()

    // Negative signals - these BLOCK CC payment classification
    val CC_NEGATIVE_SIGNALS = listOf(
        "PURCHASE", "SHOPPING", "SHOPPED",
        "REFUND", "CASHBACK", "CASH BACK",
        "REVERSAL", "REVERSED",
        "EMI", "NO COST EMI",
        "POS", "ECOM", "E-COM",
        "SWIPE", "TAP", "CONTACTLESS",
        "REWARD", "POINTS",
        "LIMIT", "AVAILABLE",
        "DUE DATE", "REMINDER",
        "STATEMENT READY", "BILL GENERATED"
    )
    val CC_NEGATIVE_SIGNALS_SET = CC_NEGATIVE_SIGNALS.toSet()

    // ============ CREDIT CARD SPEND DETECTION ============
    // Keywords indicating a purchase/spend USING a credit card
    val CC_SPEND_KEYWORDS = listOf(
        "SPENT", "SPEND", "PURCHASE", "PURCHASED",
        "SHOPPED", "SHOPPING",
        "SWIPE", "SWIPED", "TAP", "TAPPED",
        "TXN AT", "TRANSACTION AT", "USED AT",
        "POS", "ECOM", "E-COM", "ECOMMERCE", "E-COMMERCE",
        "CONTACTLESS", "CHIP TRANSACTION",
        "ONLINE PAYMENT", "ONLINE TXN"
    )
    val CC_SPEND_KEYWORDS_SET = CC_SPEND_KEYWORDS.toSet()

    // ============ CARD NETWORKS ============
    // Card network identifiers - indicate card usage
    val CARD_NETWORKS = listOf(
        "VISA", "MASTERCARD", "RUPAY", "AMEX",
        "AMERICAN EXPRESS", "DINERS", "MAESTRO", "DISCOVER"
    )
    val CARD_NETWORKS_SET = CARD_NETWORKS.toSet()

    // Masked card number patterns (to identify card transactions)
    val CARD_NUMBER_PATTERN = Regex("\\b(XX|\\*\\*|X{2,4})\\s*\\d{4}\\b", RegexOption.IGNORE_CASE)

    // ============ MERCHANT PATTERNS (DEFAULT) ============
    val DEFAULT_MERCHANT_PATTERNS = mapOf(
        // Food Delivery
        "SWIGGY" to "Food Outside",
        "ZOMATO" to "Food Outside",
        "DOMINOS" to "Food Outside",
        "PIZZA HUT" to "Food Outside",
        "MCDONALDS" to "Food Outside",
        "SUBWAY" to "Food Outside",
        "KFC" to "Food Outside",
        "BURGER KING" to "Food Outside",
        "DUNKIN" to "Food Outside",
        "STARBUCKS" to "Food Outside",
        "CAFE COFFEE DAY" to "Food Outside",
        "CCD" to "Food Outside",
        "HALDIRAMS" to "Food Outside",
        "BIKANERVALA" to "Food Outside",
        "SAMRATPIZZA" to "Food Outside",
        "FRESHWORKS" to "Food Outside",
        "BEHROUZ" to "Food Outside",
        "BIRYANI" to "Food Outside",
        "TACO BELL" to "Food Outside",
        "FRIED CHICKEN" to "Food Outside",
        "CHINESE GARDEN" to "Food Outside",
        "RESTAURANT" to "Food Outside",
        "BAKERY" to "Food Outside",
        "CAFE" to "Food Outside",
        "DHABA" to "Food Outside",
        "DHABHA" to "Food Outside",
        "FOOD COURT" to "Food Outside",
        "QUICK BITE" to "Food Outside",
        "CHAAYOS" to "Food Outside",
        "BIRYANI BY KILO" to "Food Outside",

        // Groceries & Shopping
        "BLINKIT" to "Groceries",
        "DUNZO" to "Groceries",
        "INSTAMART" to "Groceries",
        "ZEPTO" to "Groceries",
        "MILKBASKET" to "Groceries",
        "MILKMAN" to "Groceries",
        "DMart" to "Groceries",
        "DMART" to "Groceries",
        "DMART EXPRESS" to "Groceries",
        "RELIANCE MART" to "Groceries",
        "RELIANCE RETAIL" to "Groceries",
        "BIGBASKET" to "Groceries",
        "AMAZON FRESH" to "Groceries",
        "FLIPKART" to "Online Shopping",
        "AMAZON" to "Online Shopping",
        "EBAY" to "Online Shopping",
        "SNAPDEAL" to "Online Shopping",
        "MYNTRA" to "Online Shopping",
        "VOONIK" to "Online Shopping",
        "NYKAA" to "Online Shopping",
        "AJIO" to "Online Shopping",

        // Utilities & Services
        "ELECTRIC SUPPLY" to "Bills & Utilities",
        "ELECTRICITY BOARD" to "Bills & Utilities",
        "POWER" to "Bills & Utilities",
        "JIO RECHARGE" to "Phone Recharge",
        "AIRTEL RECHARGE" to "Phone Recharge",
        "VODAFONE RECHARGE" to "Phone Recharge",
        "IDEA RECHARGE" to "Phone Recharge",
        "BSNL RECHARGE" to "Phone Recharge",
        "TATA DOCOMO" to "Phone Recharge",

        // Fuel & Transport
        "BPCL" to "Fuel",
        "IOCL" to "Fuel",
        "SHELL" to "Fuel",
        "RELIANCE PETROL" to "Fuel",
        "FUEL STATION" to "Fuel",
        "OLA" to "Transport",
        "UBER" to "Transport",
        "IXER" to "Transport",
        "RAPIDO" to "Transport",
        "METRO" to "Transport",

        // Healthcare
        "PHARMACY" to "Healthcare",
        "MEDICAL" to "Healthcare",
        "HOSPITAL" to "Healthcare",
        "DOCTOR" to "Healthcare",
        "CLINIC" to "Healthcare",
        "NETMEDS" to "Healthcare",
        "PHARMEASY" to "Healthcare",
        "1MG" to "Healthcare",
        "APOLLO" to "Healthcare",
        "FORTIS" to "Healthcare",
        "MAX HEALTHCARE" to "Healthcare",

        // Entertainment
        "NETFLIX" to "Entertainment",
        "AMAZON PRIME" to "Entertainment",
        "HOTSTAR" to "Entertainment",
        "SONY LIV" to "Entertainment",
        "ALT BALAJI" to "Entertainment",
        "ZEE5" to "Entertainment",
        "BIGFLIX" to "Entertainment",
        "YOUTUBE" to "Entertainment",
        "SPOTIFY" to "Entertainment",
        "GAANA" to "Entertainment",
        "WYNK" to "Entertainment",
        "MOVIE TICKET" to "Entertainment",
        "CINEMA" to "Entertainment",
        "BOOKMYSHOW" to "Entertainment",

        // Finance & Investment
        "GROWW" to "Investment",
        "ZERODHA" to "Investment",
        "UPSTOX" to "Investment",
        "SHOONYA" to "Investment",
        "MOTILAL OSWAL" to "Investment",
        "ICICI DIRECT" to "Investment",
        "HDFC SECURITIES" to "Investment",
        "SBI SECURITIES" to "Investment",
        "ANGEL BROKING" to "Investment",

        // Transfers & P2P
        "GOOGLE PAY" to "Transfer",
        "GPAY" to "Transfer",
        "PHONEPE" to "Transfer",
        "PAYTM" to "Transfer",
        "WHATSAPP PAY" to "Transfer",
        "UPI" to "Transfer",

        // Investments & SIP/Mandate (ACH patterns)
        "MUTUAL FUND" to "Investment",
        "MUTUAL FUNDS" to "Investment",
        "SIP DEBIT" to "Investment",
        "DEMAT" to "Investment",
        "RECURRING" to "Investment",
        "MANDATE" to "Investment",
        "MUTUAL FUND INVESTMENT" to "Investment",

        // Insurance
        "INSURANCE" to "Insurance",
        "PREMIUM" to "Insurance",
        "POLICY" to "Insurance",
        "BAJAJ" to "Insurance",
        "HDFC INSURANCE" to "Insurance",
        "ICICI INSURANCE" to "Insurance",
        "LIC" to "Insurance",

        // Loan EMI and Home Loan patterns
        "EMI" to "Loan EMI",
        "HOME LOAN" to "Loan EMI",
        "PERSONAL LOAN" to "Loan EMI",
        "CAR LOAN" to "Loan EMI",
        "AUTO LOAN" to "Loan EMI",
        "EDUCATION LOAN" to "Loan EMI",
        "LOAN EMI" to "Loan EMI"
    )

    // ============ NOISE PATTERNS (for removing noise) ============
    // Patterns that should be removed/ignored from text
    val NOISE_PATTERNS = listOf(
        "FOR MORE DETAILS",
        "CUSTOMER CARE",
        "CALL CENTRE",
        "CALL CENTER",
        "TOLL FREE",
        "WWW",
        "HTTP",
        "DOWNLOAD",
        "APP",
        "LOGIN",
        "MORE DETAILS"
    )

    // ============ MERCHANT EXTRACTION PATTERNS ============
    val MERCHANT_EXTRACTION_PATTERNS = listOf(
        "at\\s+([\\w\\s\\.\\-@']+?)(?:\\s+(?:on|for|using|via))?",
        "spent\\s+.*?at\\s+([\\w\\s\\.\\-@']+?)(?:\\s|$|\\.|,)",
        "spent\\s+.*?\\s+at\\s+([\\w\\s\\.\\-@']+?)\\s+",
        "purchase\\s+at\\s+([\\w\\s\\.\\-@']+?)(?:\\s|$|\\.|,)",
        "txn\\s+at\\s+([\\w\\s\\.\\-@']+?)(?:\\s|$|\\.|,)"
    )

    // ============ UPI ID PATTERNS ============
    val UPI_ID_PATTERN = Regex("([\\w.-]+@[\\w]+)")

    // ============ TRANSFER PATTERNS ============
    val EXPLICIT_TRANSFER_PATTERNS = listOf(
        "to\\s+([\\w\\s\\.\\-@']+?)\\s+on",
        "from\\s+([\\w\\s\\.\\-@']+?)\\s+on",
        "credited\\s+from\\s+([\\w\\s\\.\\-@']+?)(?:\\s|$|\\.|,)",
        "credited\\s+to\\s+([\\w\\s\\.\\-@']+?)(?:\\s|$|\\.|,)",
        "debited\\s+to\\s+([\\w\\s\\.\\-@']+?)(?:\\s|$|\\.|,)",
        "debited\\s+from\\s+([\\w\\s\\.\\-@']+?)(?:\\s|$|\\.|,)",
        "transferred\\s+to\\s+([\\w\\s\\.\\-@']+?)(?:\\s|$|\\.|,)",
        "transferred\\s+from\\s+([\\w\\s\\.\\-@']+?)(?:\\s|$|\\.|,)"
    )

    // Self-transfer detection patterns
    val SELF_TRANSFER_PATTERNS = listOf(
        Regex("Credited from [\\w.-]+@\\w+", RegexOption.IGNORE_CASE),
        Regex("Debited to [\\w.-]+@\\w+", RegexOption.IGNORE_CASE),
        Regex("transferred to own account", RegexOption.IGNORE_CASE),
        Regex("self transfer", RegexOption.IGNORE_CASE)
    )

    // ============ PAYMENT TYPE PATTERNS ============
    val NEFT_PATTERNS = listOf(
        "NEFT",
        "RTGS",
        "IMPS"
    )

    val CHEQUE_PATTERNS = listOf(
        "CHEQUE",
        "CHQ",
        "CHECK"
    )

    val UPI_PAYMENT_PATTERNS = listOf(
        "UPI",
        "UNIFIED PAYMENT"
    )

    // ============ PERSON NAME PATTERNS ============
    // Patterns to identify personal names vs generic merchants
    val PERSON_PATTERNS = listOf(
        "Mr\\s+",
        "Mrs\\s+",
        "Ms\\s+",
        "Dr\\s+",
        "Prof\\s+",
        "Sri\\s+",
        "Smt\\s+",
        "Shri\\s+"
    )

    // ============ PLACEHOLDER PATTERNS ============
    // Common placeholders that need to be overridden
    val PLACEHOLDER_PATTERNS = listOf(
        "SELF",
        "OWN",
        "ACCOUNT",
        "ME",
        "MYSELF",
        "TRANSFER",
        "TXN",
        "TRANSACTION"
    )

    // ============ HELPER METHODS ============

    /**
     * Check if text contains credit card payment indicators
     */
    fun isCreditCardPayment(text: String): Boolean {
        val upper = text.uppercase()
        return CC_EXPLICIT_KEYWORDS.any { upper.contains(it) } ||
               (CC_PAYMENT_APPS.any { upper.contains(it) })
    }

    /**
     * Check if text contains credit card payment context
     */
    fun hasCreditCardContext(text: String): Boolean {
        val upper = text.uppercase()
        return CC_ISSUERS.any { upper.contains(it) } &&
               CC_PAYMENT_VERBS.any { upper.contains(it) }
    }

    /**
     * Check if text contains negative signals (blocks CC payment classification)
     */
    fun hasNegativeSignals(text: String): Boolean {
        val upper = text.uppercase()
        return CC_NEGATIVE_SIGNALS.any { upper.contains(it) }
    }

    /**
     * Check if text indicates a CC spend transaction
     */
    fun isCreditCardSpend(text: String): Boolean {
        val upper = text.uppercase()
        return CC_SPEND_KEYWORDS.any { upper.contains(it) } &&
               (CARD_NETWORKS.any { upper.contains(it) } ||
                CARD_NUMBER_PATTERN.containsMatchIn(upper))
    }

    /**
     * Check if text is salary/income related
     */
    fun isSalaryCredit(text: String): Boolean {
        val upper = text.uppercase()
        return SALARY_KEYWORDS.any { upper.contains(it) }
    }

    /**
     * Check if text should be excluded from processing
     */
    fun shouldExclude(text: String): Boolean {
        val upper = text.uppercase()
        return EXCLUSION_KEYWORDS.any { upper.contains(it) }
    }

    /**
     * Check if text contains UPI-related content
     */
    fun isUPITransaction(text: String): Boolean {
        val upper = text.uppercase()
        return UPI_PAYMENT_PATTERNS.any { upper.contains(it) } ||
               UPI_ID_PATTERN.containsMatchIn(text)
    }

    /**
     * Check if text is a self-transfer
     */
    fun isSelfTransfer(text: String): Boolean {
        return SELF_TRANSFER_PATTERNS.any { it.containsMatchIn(text) }
    }

    /**
     * Check if text contains debit keywords
     */
    fun hasDebitKeywords(text: String): Boolean {
        val upper = text.uppercase()
        return listOf("DEBITED", "DEBIT", "SPENT", "SPEND", "PAID", "PAYMENT").any { upper.contains(it) }
    }

    /**
     * Check if text contains credit keywords
     */
    fun hasCreditKeywords(text: String): Boolean {
        val upper = text.uppercase()
        return listOf("CREDITED", "CREDIT", "RECEIVED", "DEPOSITED").any { upper.contains(it) }
    }

    /**
     * Extract merchant name from known patterns
     */
    fun extractMerchantKeyword(text: String): String? {
        val upper = text.uppercase()
        // Check user patterns first (would be passed separately in actual use)
        // Then check default patterns
        for ((keyword, _) in DEFAULT_MERCHANT_PATTERNS) {
            if (upper.contains(keyword.uppercase())) {
                return keyword
            }
        }
        return null
    }

    /**
     * Get category for merchant keyword
     */
    fun getCategoryForMerchant(merchant: String): String? {
        return DEFAULT_MERCHANT_PATTERNS[merchant]
    }
}
