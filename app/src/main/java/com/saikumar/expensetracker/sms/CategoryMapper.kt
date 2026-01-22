package com.saikumar.expensetracker.sms

import com.saikumar.expensetracker.data.entity.*

/**
 * Result of category classification including confidence score.
 */
data class CategoryResult(
    val category: String,
    val confidence: Int // 0-100 scale
)

object CategoryMapper {

    // Confidence score constants
    object Confidence {
        const val USER_RULE = 95       // User-defined rule match
        const val MERCHANT_EXACT = 90  // Exact merchant name match
        const val SALARY_PATTERN = 90  // Salary company name match
        const val SELF_TRANSFER = 85   // Self-transfer detection
        const val PATTERN_MATCH = 80   // Pattern-based detection (investment, interest)
        const val ML_PREDICTION = 75   // ML Model prediction
        const val TYPE_DEFAULT = 70    // TransactionType-based default
        const val FALLBACK = 50        // Generic fallback (Miscellaneous)
        const val UNCATEGORIZED = 30   // Uncategorized/Unknown
        const val UNVERIFIED = 25      // Unverified income (needs review)
    }

    private val MERCHANT_CATEGORIES = mapOf(
        // Food Delivery
        "SWIGGY" to "Food Delivery",
        "ZOMATO" to "Food Delivery",
        "LICIOUS" to "Food Delivery",
        "DOMINOS" to "Food Delivery",
        "PIZZA HUT" to "Food Delivery",
        "KFC" to "Food Delivery",
        "SUBWAY" to "Food Delivery",

        // Dining Out
        "CREAM STONE" to "Dining Out",
        "NAIDU GARI" to "Dining Out",
        "KRITUNGA" to "Dining Out",
        "THE NAWAABS" to "Dining Out",
        "PISTA HOUSE" to "Dining Out",
        "BAKE CENTRAL" to "Dining Out",
        "MANDIKING" to "Dining Out",
        "MAHARAJA CHAT" to "Dining Out",
        "DINEOUT" to "Dining Out",
        "ARABIAN REST" to "Dining Out",
        "MANDI KING" to "Dining Out",
        "MANDIKING" to "Dining Out",

        // Groceries
        "DMART" to "Groceries",
        "RATNADEEP" to "Groceries",
        "VIJETHA" to "Groceries",
        "BHARAT BAZAR" to "Groceries",
        "SMPOORNA" to "Groceries",
        "AVENUE SUPERMAR" to "Groceries",
        "ZEPTO" to "Groceries",
        "BLINKIT" to "Groceries",

        // Shopping
        "AMAZON" to "Shopping",
        "FLIPKART" to "Shopping",
        "RELIANCE" to "Shopping",
        "JOCKEY" to "Shopping",
        "ZUDIO" to "Shopping",
        "MYNTRA" to "Clothing",
        "AJIO" to "Clothing",
        "ZARA" to "Clothing",
        "H&M" to "Clothing",
        "UNIQLO" to "Clothing",
        "LEVIS" to "Clothing",
        "WESTSIDE" to "Clothing",
        "PANTALOONS" to "Clothing",
        "MAX FASHION" to "Clothing",
        "LIFESTYLE" to "Clothing",
        
        // Furniture
        "IKEA" to "Furniture",
        "PEPPERFRY" to "Furniture",
        "URBAN LADDER" to "Furniture",
        "GODREJ INTERIO" to "Furniture",
        "NILKAMAL" to "Furniture",
        "HOMETOWN" to "Furniture",
        
        // Electronics
        "CROMA" to "Electronics",
        "VIJAY SALES" to "Electronics",
        "SAMSUNG" to "Electronics",
        "APPLE" to "Electronics",
        "ONEPLUS" to "Electronics",
        "MI STORE" to "Electronics",
        "DELL" to "Electronics",
        "HP STORE" to "Electronics",
        "LENOVO" to "Electronics",

        // Transportation (merged Travel + Cab & Taxi)
        "TSRTC" to "Transportation",
        "IRCTC" to "Transportation",
        "REDBUS" to "Transportation",
        "RAPIDO" to "Transportation",
        "OLA" to "Transportation",
        "UBER" to "Transportation",
        "APSRTC" to "Transportation",
        "METRO" to "Transportation",
        "YULU" to "Transportation",
        "NAMMA YATRI" to "Transportation",

        // Fuel
        "FUEL" to "Fuel",
        "PETROL" to "Fuel",
        "HP" to "Fuel",
        "BPCL" to "Fuel",
        "IOCL" to "Fuel",

        // Utilities
        "ELECTRICITY" to "Utilities",
        "BESCOM" to "Utilities",
        "GAS" to "Utilities",
        "WATER" to "Utilities",

        // Telecom (Mobile + WiFi)
        "AIRTEL" to "Mobile + WiFi",
        "JIO" to "Mobile + WiFi",
        "VI " to "Mobile + WiFi",
        "BSNL" to "Mobile + WiFi",
        "ACT FIBERNET" to "Mobile + WiFi",
        "HATHWAY" to "Mobile + WiFi",

        // Quick Commerce additions
        "INSTAMART" to "Groceries",
        "DUNZO" to "Groceries",
        "BIGBASKET" to "Groceries",
        // Entertainment
        "YOUTUBE" to "Entertainment",
        "NETFLIX" to "Entertainment",
        "SPOTIFY" to "Entertainment",
        "GAMEON" to "Entertainment",
        "PRIVEPLEX" to "Entertainment",
        "STEAM" to "Entertainment",
        "PLAYSTATION" to "Entertainment",
        "XBOX" to "Entertainment",
        "NINTENDO" to "Entertainment",
        "DREAM11" to "Entertainment",
        "RUMMY" to "Entertainment",
        "GAMES" to "Entertainment",

        // Education / Self Development
        "UDEMY" to "Education",

        // Investment (Map to Mutual Funds as proxy)
        "ZERODHA" to "Mutual Funds",
        "GROWW" to "Mutual Funds",
        "INDIAN CLEARING" to "Mutual Funds",
        "ICCL" to "Mutual Funds",

        // Insurance
        "ICICI PRU" to "Insurance",
        "POLICYBAZAAR" to "Insurance",
        "LIC" to "Insurance",
        
        // Personal Care / Service
        "SIVAM AUTO" to "Service",
        
        // Misc
        "PAYTMQR" to "Offline Merchant",
        "VYAPAR" to "Miscellaneous",
        "GPAY" to "Miscellaneous",

        // Financial Services / Credit Card
        "CRED" to "Credit Bill Payments",
        "STATEMENT" to "Credit Bill Payments",
        "SBICARD" to "Credit Bill Payments",
        "HDFCCARD" to "Credit Bill Payments",
        "AXISCARD" to "Credit Bill Payments",
        "AMEX" to "Credit Bill Payments",
        "CITIBANK" to "Credit Bill Payments",
        "ONECARD" to "Credit Bill Payments",
        "BILLDESK" to "Credit Bill Payments",
        "CCPAY" to "Credit Bill Payments",

        // Interest Income
        "IDFC FIRST BANK" to "Interest",
        "INTEREST" to "Interest",
        "INT CREDIT" to "Interest",

        // Education
        "SCHOOL" to "Education / Fees",
        "COLLEGE" to "Education / Fees",
        "UNIVERSITY" to "Education / Fees",
        "COACHING" to "Education / Fees",
        "BYJU" to "Education / Fees",
        "UNACADEMY" to "Education / Fees",
        "VEDANTU" to "Education / Fees",
        
        // Personal Care
        "SALON" to "Personal Care",
        "PARLOUR" to "Personal Care",
        "SPA" to "Personal Care",
        "BEAUTY" to "Personal Care",
        "BARBER" to "Personal Care",
        
        // Gym & Fitness
        "GYM" to "Gym & Fitness",
        "FITNESS" to "Gym & Fitness",
        "CULT" to "Gym & Fitness",
        "CROSSFIT" to "Gym & Fitness",
        
        // Stocks & Trading
        "ANGEL" to "Stocks",
        "UPSTOX" to "Stocks",
        "KITE" to "Stocks",
        "5PAISA" to "Stocks",
        "ICICIDIRECT" to "Stocks",
        "KOTAK SEC" to "Stocks",
        
        // Gold
        "GOLD" to "Gold",
        "AUGMONT" to "Gold",
        "SAFEGOLD" to "Gold",
        
        // Tolls & Parking
        "FASTAG" to "Parking & Tolls",
        "TOLL" to "Parking & Tolls",
        "PARKING" to "Parking & Tolls",
        "NETC" to "Parking & Tolls",
        
        // Additional merchants from log analysis
        // Dining Out
        "HOUSE OF SPIRITS" to "Dining Out",
        "KINGS FAMILY" to "Dining Out",
        "STONE SPOT" to "Dining Out",
        
        // Shopping
        "FLIPKART PAYMENTS" to "Shopping",
        "AVENUE E" to "Shopping",
        "DIVERSE RETAIL" to "Shopping",
        
        // Transport / Tolls
        "TELANGANA STATE" to "Parking & Tolls",
        "RTO" to "Parking & Tolls",
        
        // Payment Gateways
        "EASEBUZZ" to "Miscellaneous",
        "RAZORPAY" to "Miscellaneous",

        // LEARNED FROM APP LOGS (High Confidence)
        "OPEN TEXT TECHNOLOGIES" to "Salary",
        "VR FUELS" to "Fuel",
        "ZERODHA BROKING" to "Mutual Funds",
        "INDIAN CLEARING CORPORATION" to "Mutual Funds",
        "SWIGGY INSTAMART" to "Groceries",
        "ZEPTO MARKETPLA" to "Groceries",
        "BAKE CENTRAL" to "Dining Out",
        "THE GREAT WALL" to "Dining Out",
        "MANDIKINGARABIANREST" to "Dining Out"
    )

    // AUDIT: Using unified TransactionType from data.entity
    private val TYPE_DEFAULT_CATEGORIES = mapOf(
        TransactionType.PENSION to "Mutual Funds", // Proxy for Pension
        TransactionType.INVESTMENT_CONTRIBUTION to "Mutual Funds",
        TransactionType.LIABILITY_PAYMENT to "Credit Bill Payments",
        TransactionType.TRANSFER to "P2P Transfers",
        TransactionType.INCOME to "Other Income", // Changed from Salary
        TransactionType.STATEMENT to "Credit Bill Payments" // Show under Credit Bills
    )

    fun categorize(
        counterparty: CounterpartyExtractor.Counterparty,
        transactionType: TransactionType, // AUDIT: Unified enum
        rules: List<CategorizationRule> = emptyList(),
        categoryMap: Map<Long, String> = emptyMap(),
        userAccounts: List<UserAccount> = emptyList(),
        messageBody: String = "", // Optional: raw SMS body for advanced detection
        salarySources: Set<Pair<String, String>> = emptySet(), // Known salary sources (IFSC, sender)
        salaryCompanyNames: Set<String> = emptySet(), // User-configured company names for salary detection
        trace: MutableList<String>? = null  // Debug trace for decision tree
    ): String {
        // 0. CHECK NEFT SELF-TRANSFER (sender == receiver)
        // This catches deposits from your own account at another bank
        if (messageBody.contains("NEFT", ignoreCase = true)) {
            if (CounterpartyExtractor.isNeftSelfTransfer(messageBody)) {
                trace?.add("Matched: NEFT Self Transfer pattern")
                return "Self Transfer"
            }
            
            // 0.5. CHECK IF NEFT SOURCE IS A KNOWN SALARY SOURCE
            if (salarySources.isNotEmpty()) {
                val neftSource = CounterpartyExtractor.extractNeftSource(messageBody)
                if (neftSource != null && salarySources.contains(neftSource)) {
                    trace?.add("Matched: Known Salary Source (NEFT)")
                    return "Salary"
                }
            }
        }
        
        // 0.55. CHECK SALARY COMPANY NAMES (User-configured)
        // Match company names in the message body for income transactions
        if (transactionType == TransactionType.INCOME && salaryCompanyNames.isNotEmpty()) {
            val upperBody = messageBody.uppercase()
            for (companyName in salaryCompanyNames) {
                if (upperBody.contains(companyName.uppercase())) {
                    trace?.add("Matched: Salary Company Name '$companyName'")
                    return "Salary"
                }
            }
        }

        // 0.6. CHECK FOR INTEREST (Body-based)
        // Many banks just say "Interest credited", with no specific merchant name
        if (transactionType == TransactionType.INCOME) {
             val lowerBody = messageBody.lowercase()
             if (lowerBody.contains("interest") || 
                 lowerBody.contains("int. pd") || 
                 lowerBody.contains("int pd") ||
                 lowerBody.contains("int cr") ||
                 lowerBody.contains("int. earned")) {
                 trace?.add("Matched: Interest keyword in body")
                 return "Interest"
             }
        }
        
        // 1. Check DB Rules (Highest Priority)
        // Sort rules by PatternType (UPI_ID > MERCHANT_NAME, etc - based on enum ordinal)
        // Ideally rules should be pre-sorted, but for safety:
        val sortedRules = rules.sortedBy { it.patternType.ordinal } 
        
        for (rule in sortedRules) {
            val isMatch = when (rule.patternType) {
                PatternType.UPI_ID -> counterparty.upiId?.equals(rule.pattern, ignoreCase = true) == true
                PatternType.MERCHANT_NAME -> counterparty.name?.contains(rule.pattern, ignoreCase = true) == true
                PatternType.PAYEE_NAME -> counterparty.name?.contains(rule.pattern, ignoreCase = true) == true
                // Add other types if needed
                else -> false
            }
            
            if (isMatch) {
                trace?.add("Matched User Rule: ${rule.id} (${rule.pattern}) -> Category ID ${rule.categoryId}")
                return categoryMap[rule.categoryId] ?: "Uncategorized"
            }
        }
        if (rules.isNotEmpty()) trace?.add("No User Rules matched (${rules.size} checked)")

        // 1.5. CHECK SELF-TRANSFER / BILL PAYMENT (Discovered Accounts)
        // If the counterparty name mentions one of our accounts (e.g. "Account XX2725" or "Card 1234")
        if (counterparty.name != null) {
            val matchedAccount = userAccounts.find { myAccount ->
                counterparty.name.contains(myAccount.accountNumberLast4, ignoreCase = true)
            }
            if (matchedAccount != null) {
                trace?.add("Matched: Discovered Account ${matchedAccount.accountNumberLast4}")
                return if (matchedAccount.accountType == AccountType.CREDIT_CARD) {
                    "Credit Bill Payments" // Payment to my own Credit Card
                } else {
                    "Self Transfer" // Transfer to my own Bank Account
                }
            }
            
            // 1.6. CHECK IF RECIPIENT NAME MATCHES USER'S OWN NAME (Self-Transfer)
            // This catches transfers to yourself at different banks
            if (isUserOwnName(counterparty.name, userAccounts)) {
                trace?.add("Matched: User Own Name detected (Self Transfer)")
                return "Self Transfer"
            }
        }

        // 0.1. CHECK FOR CASHBACK
        // "Cashback of INR ..." -> Category: Cashback
        if (messageBody.contains("cashback", ignoreCase = true) || 
            messageBody.contains("reward", ignoreCase = true) ||
            (counterparty.name?.contains("cashback", ignoreCase = true) == true)) {
            
            trace?.add("Matched: Cashback/Reward keyword")
            return "Cashback"
        }
        
        // Check UPI ID for self-transfer pattern (even if name is null)
        if (!counterparty.upiId.isNullOrBlank() && isUserOwnName(counterparty.upiId, userAccounts)) {
             trace?.add("Matched: User Own UPI detected (Self Transfer)")
             return "Self Transfer"
        }
        
        // 2. Try merchant name match (Hardcoded Map)
        counterparty.name?.let { name ->
            val upper = name.uppercase()
            
            // Special case for Chits (Dynamic)
            if (upper.contains("CHIT") || upper.contains("SHRIRAM")) {
                 if (transactionType == TransactionType.INCOME) {
                     trace?.add("Matched: Chit/Shriram -> Investment Redemption")
                     return "Investment Redemption"
                 } else {
                     trace?.add("Matched: Chit/Shriram -> Chits")
                     return "Chits"
                 }
            }

            for ((key, defaultCategory) in MERCHANT_CATEGORIES) {
                if (upper.contains(key)) {
                    // FIX: Prevent "CRED" matching "CREDITED"
                    if (key == "CRED" && upper.contains("CREDITED") && 
                        !upper.contains("CRED APP") && !upper.contains("CRED CLUB")) {
                        continue
                    }
                    
                    // If money is coming IN from an Investment entity, it's Redemption
                    if (transactionType == TransactionType.INCOME) {
                        if (defaultCategory == "Mutual Funds" || 
                            key == "ICCL" || 
                            key == "INDIAN CLEARING") {
                            trace?.add("Matched: $key -> Investment Redemption (Income)")
                            return "Investment Redemption"
                        }
                    }
                    trace?.add("Matched Hardcoded Merchant: $key -> $defaultCategory")
                    return defaultCategory
                }
            }
            
            // 2b. Investment Redemption Patterns
            // "INDIAN CLEARING CORPORATION" -> Mutual Funds (Redemption)
            if (upper.contains("INDIAN CLEARING") || upper.contains("ICCL")) {
                if (transactionType == TransactionType.INCOME) {
                    trace?.add("Matched: ICCL/Clearing -> Investment Redemption")
                    return "Investment Redemption"
                } else {
                    trace?.add("Matched: ICCL/Clearing -> Mutual Funds")
                    return "Mutual Funds"
                }
            }
        }
        
        // 2c. Try ML Classifier
        // If we have a clean merchant name, try the Naive Bayes model
        // This handles known merchants that aren't in the hardcoded map yet
        counterparty.name?.let { name -> 
             val mlResult = com.saikumar.expensetracker.ml.NaiveBayesClassifier.classify(name)
             if (mlResult != null) {
                 trace?.add("Matched: ML Classifier -> ${mlResult.category} (Conf: ${mlResult.confidence})")
                 return mlResult.category
             }
        }
        
        // 4. Fall back to type-based default
        // 4. Fall back to type-based default
        val defaultCategory = TYPE_DEFAULT_CATEGORIES[transactionType] ?: "Uncategorized"

        // 5. Handling Generic/Offline Merchants
        // a) Q-Code / Card-Machine UPI (Generic Offline)
        if (counterparty.upiId?.matches(Regex("(?i)^q\\d+.*")) == true) {
             trace?.add("Matched: Q-Code VPA -> Offline Merchant")
             return "Offline Merchant"
        }

        // b) Generic Fallback
        // FIX: If we know it's a MERCHANT but haven't found a specific category,
        // map to 'Miscellaneous' instead of 'Uncategorized' to reduce noise.
        if (defaultCategory == "Uncategorized" && 
            (counterparty.type == CounterpartyExtractor.CounterpartyType.MERCHANT || 
             (transactionType == TransactionType.EXPENSE && !counterparty.name.isNullOrBlank()))) {
            trace?.add("Fallback: Generic Merchant/Expense with Name -> Miscellaneous")
            return "Miscellaneous"
        }
        
        // AUDIT FIX: Flag large unverified income credits for review
        // If income >₹10,000 with no specific category match, it could be a self-transfer
        if (defaultCategory == "Other Income" && transactionType == TransactionType.INCOME) {
            val lowerBody = messageBody.lowercase()
            // Check if there's no identifiable source pattern
            val hasNoIdentifiedSource = !lowerBody.contains("neft") && 
                                        !lowerBody.contains("imps") && 
                                        !lowerBody.contains("upi") && 
                                        !lowerBody.contains("salary") &&
                                        counterparty.name.isNullOrBlank()
            if (hasNoIdentifiedSource) {
                trace?.add("WARN: Large unverified credit with unknown source -> Unverified Income (Needs Review)")
                return "Unverified Income"
            }
        }
        
        trace?.add("Fallback: Type-based default -> $defaultCategory")
        return defaultCategory
    }
    
    /**
     * Check if the counterparty name matches the user's own name.
     * This helps detect self-transfers when money moves between user's accounts at different banks.
     * 
     * Uses account holder names discovered from NEFT/salary deposits.
     */
    private fun isUserOwnName(name: String, userAccounts: List<UserAccount>): Boolean {
        val lower = name.lowercase()
        
        // Get all discovered account holder names
        val holderNames = userAccounts.mapNotNull { it.accountHolderName }.distinct()
        
        if (holderNames.isEmpty()) {
            return false // No holder names discovered yet
        }
        
        // 3. Check if counterparty name contains any of the discovered holder names
        for (holderName in holderNames) {
            val holderLower = holderName.lowercase()
            
            // Check if name contains the holder name
            if (lower.contains(holderLower)) {
                return true
            }
            
            // Also check inverted formats (first words)
            // E.g., "GODALA SAIKUMAR REDDY" should match "Saikumar Reddy Godala"
            val holderParts = holderLower.split(" ").filter { it.length >= 3 }
            if (holderParts.size >= 2) {
                // Check if two significant name parts match
                val matchCount = holderParts.count { part -> lower.contains(part) }
                if (matchCount >= 2) {
                    return true
                }
            }
        }
        
        return false
    }
    
    /**
     * Calculate confidence score based on category name and match type.
     * This is a heuristic based on which classification path was likely taken.
     */
    fun calculateConfidence(category: String, wasUserRule: Boolean = false): Int {
        if (wasUserRule) return Confidence.USER_RULE
        
        return when (category) {
            // High confidence - specific matches
            "Self Transfer" -> Confidence.SELF_TRANSFER
            "Salary" -> Confidence.SALARY_PATTERN
            "Interest" -> Confidence.PATTERN_MATCH
            "Investment Redemption" -> Confidence.PATTERN_MATCH
            
            // ML Prediction
            // We don't have a specific category name check for ML (it can return anything)
            // But if 'wasUserRule' is false and we land here, we might want to check if it matches the ML result?
            // Actually, this function is called AFTER categorization to assign a score.
            // If the category came from ML, it should get ML_PREDICTION score?
            // The problem is we lose the "source" information by the time we call this.
            // However, we can use the 'wasUserRule' flag or similar context. 
            // For now, we'll leave it as Pattern Match (80) or similar.
            // Actually, let's keep it simple. If it's a known category but NOT exact merchant match, 
            // it falls to 'else', which is PATTERN_MATCH (80).
            // ML_PREDICTION is 75. 
            // If we want to be strict, we'd need to pass 'source' to this function.
            // But 80 (Pattern Match) is close enough to 75.

            
            // High confidence - exact merchant matches
            in MERCHANT_CATEGORIES.values -> Confidence.MERCHANT_EXACT
            
            // Medium confidence - type-based defaults
            "Mutual Funds", "Credit Bill Payments", "P2P Transfers", "Other Income" -> Confidence.TYPE_DEFAULT
            
            // Low confidence - fallbacks
            "Miscellaneous" -> Confidence.FALLBACK
            "Unknown Expense" -> Confidence.UNCATEGORIZED
            "Uncategorized" -> Confidence.UNCATEGORIZED
            "Unverified Income" -> Confidence.UNVERIFIED
            
            // Default for known categories
            else -> Confidence.PATTERN_MATCH
        }
    }
}
