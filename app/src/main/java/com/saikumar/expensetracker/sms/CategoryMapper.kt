package com.saikumar.expensetracker.sms

import com.saikumar.expensetracker.data.entity.*
import com.saikumar.expensetracker.core.AppConstants

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

        // ATM / Cash
        "ATM" to "Cash Withdrawal",
        "SBI ATM" to "Cash Withdrawal",
        "CASH" to "Cash Withdrawal",
        "NFS" to "Cash Withdrawal",
        "CASH WDL" to "Cash Withdrawal",
        "WDL" to "Cash Withdrawal",
        "CASH WITHDRAWAL" to "Cash Withdrawal",

        // Cashback
        "BHIMCASHBACK" to "Cashback",

        // Clothing
        "AJIO" to "Clothing",
        "H&M" to "Clothing",
        "LEVIS" to "Clothing",
        "LIFESTYLE" to "Clothing",
        "MAX FASHION" to "Clothing",
        "MYNTRA" to "Clothing",
        "PANTALOONS" to "Clothing",
        "UNIQLO" to "Clothing",
        "WESTSIDE" to "Clothing",
        "ZARA" to "Clothing",
        "ZUDIO" to "Clothing",
        "JOCKEY" to "Clothing",


        // Credit Bill Payments
        "AMEX" to "Credit Bill Payments",
        "AXISCARD" to "Credit Bill Payments",
        "BILLDESK" to "Credit Bill Payments",
        "CCPAY" to "Credit Bill Payments",
        "CITIBANK" to "Credit Bill Payments",
        "CRED" to "Credit Bill Payments",
        "CRED CLUB" to "Credit Bill Payments",
        "HDFCCARD" to "Credit Bill Payments",
        "ONECARD" to "Credit Bill Payments",
        "SBICARD" to "Credit Bill Payments",
        "STATEMENT" to "Credit Card Statement",

        // Dining Out
        "ARABIAN REST" to "Dining Out",
        "ARUN ICE CREAMS" to "Dining Out",
        "BAKE CENTRAL" to "Dining Out",
        "CREAM STONE" to "Dining Out",
        "DINEOUT" to "Dining Out",
        "ELIOR FOOD LLP" to "Dining Out",
        "ELIORINDIAFOODS" to "Dining Out",
        "GOKHANA" to "Dining Out",
        "HOUSE OF SPIRITS" to "Dining Out",
        "KINGS FAMILY" to "Dining Out",
        "KRITUNGA" to "Dining Out",
        "LASTHOUSECOFFEE" to "Dining Out",
        "MAHARAJA CHAT" to "Dining Out",
        "MAHARAJA CHAT AND FOODS" to "Dining Out",
        "MANDI KING" to "Dining Out",
        "MANDIKING" to "Dining Out",
        "MANDIKINGARABIANREST" to "Dining Out",
        "NAIDU GARI" to "Dining Out",
        "NALBHEEMA KITCH" to "Dining Out",
        "PARAMPARA MITHAI SHOP BHEL MIG" to "Dining Out",
        "PISTA HOUSE" to "Dining Out",
        "SPUDATOFOODS" to "Dining Out",
        "STONE SPOT" to "Dining Out",
        "THE GREAT WALL" to "Dining Out",
        "THE NAWAABS" to "Dining Out",
        "THE NAWAABS RESTAURANT" to "Dining Out",
        "YUM RESTAURANT" to "Dining Out",
        "YUM YUM TREE ARABIAN FOOD COURT" to "Dining Out",
        "ZAIKA RESTAURANT" to "Dining Out",
        "SWIGGYDINEIN" to "Dining Out",

        // Education
        "UDEMY" to "Education",

        // Education / Fees
        "BYJU" to "Education / Fees",
        "COACHING" to "Education / Fees",
        "COLLEGE" to "Education / Fees",
        "SCHOOL" to "Education / Fees",
        "UNACADEMY" to "Education / Fees",
        "UNIVERSITY" to "Education / Fees",
        "VEDANTU" to "Education / Fees",

        // Electronics
        "APPLE" to "Electronics",
        "CROMA" to "Electronics",
        "DELL" to "Electronics",
        "HP STORE" to "Electronics",
        "LENOVO" to "Electronics",
        "MI STORE" to "Electronics",
        "ONEPLUS" to "Electronics",
        "SAMSUNG" to "Electronics",
        "VIJAY SALES" to "Electronics",
        "SHOPSAMS" to "Electronics",

        // Entertainment
        "AMOEBA" to "Entertainment",
        "BOOKMYSHOW" to "Entertainment",
        "DISTRICT MOVIES" to "Entertainment",
        "DREAM11" to "Entertainment",
        "GAMEON" to "Entertainment",
        "GAMES" to "Entertainment",
        "GOOGLEPLAY" to "Entertainment",
        "NINTENDO" to "Entertainment",
        "PLAYSTATION" to "Entertainment",
        "PRASADS" to "Entertainment",
        "PRIVEPLEX" to "Entertainment",
        "RUMMY" to "Entertainment",
        "SCRATCHBOARDS" to "Entertainment",
        "SPOTIFY" to "Entertainment",
        "STEAM" to "Entertainment",
        "SVMBOWLING" to "Entertainment",
        "XBOX" to "Entertainment",

        // Fixed Deposits
        "SHIVALIK SMALL FINANCE BANK" to "Fixed Deposits",
        "SURYODAY SMALL FINANCE BANK" to "Fixed Deposits",

        // Food Delivery
        "CASSWIGGY" to "Food Delivery",
        "DOMINOS" to "Food Delivery",
        "KFC" to "Food Delivery",
        "LICIOUS" to "Food Delivery",
        "PIZZA HUT" to "Food Delivery",
        "PPSL SWIGGY" to "Food Delivery",
        "SUBWAY" to "Food Delivery",
        "SWIGGY" to "Food Delivery",
        "SWIGGY STORES" to "Food Delivery",
        "SWIGGYSTORES" to "Food Delivery",
        "ZOMATO" to "Food Delivery",

        // Fuel
        "BPCL" to "Fuel",
        "FUEL" to "Fuel",
        "HINDUSTAN PETRO" to "Fuel",
        "HP" to "Fuel",
        "IOCL" to "Fuel",
        "PETROL" to "Fuel",
        "VR FUELS" to "Fuel",

        // Furniture
        "GODREJ INTERIO" to "Furniture",
        "HOMETOWN" to "Furniture",
        "IKEA" to "Furniture",
        "NILKAMAL" to "Furniture",
        "NILKAMALFURNITU" to "Furniture",
        "PEPPERFRY" to "Furniture",
        "URBAN LADDER" to "Furniture",

        // Gold
        "AUGMONT" to "Gold",
        "GOLD" to "Gold",
        "SAFEGOLD" to "Gold",

        // Groceries
        "AVENUE SUPERMAR" to "Groceries",
        "BHARAT BAZAR" to "Groceries",
        "BIGBASKET" to "Groceries",
        "BLINKIT" to "Groceries",
        "BLINKITJKB" to "Groceries",
        "BUNDL TECHN" to "Groceries",
        "CP ZEPTO" to "Groceries",
        "CREDPAYZEPTO" to "Groceries",
        "DMART" to "Groceries",
        "DUNZO" to "Groceries",
        "GROFERS PAYTM" to "Groceries",
        "INSTAM" to "Groceries",
        "INSTAMART" to "Groceries",
        "KPN FF KOT" to "Groceries",
        "RATNADEEP" to "Groceries",
        "SMPOORNA" to "Groceries",
        "SWIGGY INSTAMART" to "Groceries",
        "VIJETHA" to "Groceries",
        "ZEPTO" to "Groceries",
        "ZEPTO MARKETPLA" to "Groceries",
        "ZEPTONOW ESBZ" to "Groceries",
        "ZEPTOONLINE" to "Groceries",

        // Gym & Fitness
        "CROSSFIT" to "Gym & Fitness",
        "CULT" to "Gym & Fitness",
        "DECATHLON" to "Gym & Fitness",
        "FITNESS" to "Gym & Fitness",
        "GYM" to "Gym & Fitness",

        // Insurance
        "ICICI PRU" to "Insurance",
        "LIC" to "Insurance",
        "POLICYBAZAAR" to "Insurance",
        "WWW POLICYBAZAAR" to "Insurance",

        // Interest
        "IDFC FIRST BANK" to "Interest",
        "INT CREDIT" to "Interest",
        "INTEREST" to "Interest",

        // Medical
        "ALEKHYA DIAGNOS" to "Medical",
        "MEDIBUDDY PHASO" to "Medical",

        // Miscellaneous
        "EASEBUZZ" to "Miscellaneous",
        "GPAY" to "Miscellaneous",
        "RAZORPAY" to "Miscellaneous",
        "VYAPAR" to "Miscellaneous",

        // Mobile + WiFi
        "ACT FIBERNET" to "Mobile + WiFi",
        "AIRTEL" to "Mobile + WiFi",
        "BSNL" to "Mobile + WiFi",
        "HATHWAY" to "Mobile + WiFi",
        "JIO" to "Mobile + WiFi",
        "PHONEPEBSNLSOUTH" to "Mobile + WiFi",

        // Mutual Funds
        "GROWW" to "Mutual Funds",
        "ICCL" to "Mutual Funds",
        "ICCL ZERODHA" to "Mutual Funds",
        "INDIAN CLEARING" to "Mutual Funds",
        "INDIAN CLEARING CORPORATION" to "Mutual Funds",
        "ZERODHA" to "Mutual Funds",
        "ZERODHA BROKING" to "Mutual Funds",

        // Offline Merchant
        "PAYTM" to "Offline Merchant",
        "PAYTMQR" to "Offline Merchant",

        // Parking & Tolls
        "FASTAG" to "Parking & Tolls",
        "NETC" to "Parking & Tolls",
        "PARKING" to "Parking & Tolls",
        "RTO" to "Parking & Tolls",
        "TELANGANA STATE" to "Parking & Tolls",
        "TELANGANA STATE ROAD TRANSPORT" to "Parking & Tolls",
        "TOLL" to "Parking & Tolls",

        // Personal Care
        "BARBER" to "Personal Care",
        "BEAUTY" to "Personal Care",
        "GREENTRENDS" to "Personal Care",
        "PARLOUR" to "Personal Care",
        "SALON" to "Personal Care",
        "SPA" to "Personal Care",

        // Salary
        "NEFT DEUTH IND" to AppConstants.Categories.SALARY,
        "OPEN TEXT" to AppConstants.Categories.SALARY,
        "OPEN TEXT TECHNOLOGIES" to AppConstants.Categories.SALARY,

        // Service
        "SIVAM AUTO" to "Service",
        "SRI MOTORS" to "Service",

        // Shopping
        "AMAZON" to "Shopping",
        "AMAZON CY" to "Shopping",
        "AVENUE E" to "Shopping",
        "DIVERSE RETAIL" to "Shopping",
        "FLIPKART" to "Shopping",
        "FLIPKART PAYMENTS" to "Shopping",
        "IND" to "Shopping",
        "INFINITI LIMITEMUM" to "Shopping",
        "RELIANCE" to "Shopping",
        "SOUTH JEW" to "Shopping",

        // Stocks
        "5PAISA" to "Stocks",
        "ANGEL" to "Stocks",
        "ICICIDIRECT" to "Stocks",
        "KITE" to "Stocks",
        "KOTAK SEC" to "Stocks",
        "UPSTOX" to "Stocks",

        // Subscriptions
        "ADOBE PREMIERE" to "Subscriptions",
        "ADOBE SYSTEMS" to "Subscriptions",
        "EENADU TELEVISI" to "Subscriptions",
        "GOOGLE PLAY" to "Subscriptions",
        "GOOGLE PLAY APP" to "Subscriptions",
        "UDEMY SUBSCRIPT" to "Subscriptions",
        "YOUTUBE CYBS" to "Subscriptions",
        "NETFLIX" to "Subscriptions",
        "YOUTUBE" to "Subscriptions",
        "YOUTUBEGOOGLE" to "Subscriptions",
        "ZEEENTERTAINMEN" to "Subscriptions",


        // Transportation
        "ABHIBUS" to "Transportation",
        "APSRTC" to "Transportation",
        "HYDMETROINAPP" to "Transportation",
        "IRCTC" to "Transportation",
        "IRCTCPGONLINE" to "Transportation",
        "METRO" to "Transportation",
        "NAMMA YATRI" to "Transportation",
        "OLA" to "Transportation",
        "RAPIDO" to "Transportation",
        "REDBUS" to "Transportation",
        "TSRTC" to "Transportation",
        "TTDCLBOATHOUSE" to "Transportation",
        "UBER" to "Transportation",
        "YULU" to "Transportation",

        // Utilities
        "BESCOM" to "Utilities",
        "ELECTRICITY" to "Utilities",
        "GAS" to "Utilities",
        "WATER" to "Utilities",
    )

    // AUDIT: Using unified TransactionType from data.entity
    private val TYPE_DEFAULT_CATEGORIES = mapOf(
        TransactionType.PENSION to AppConstants.Categories.MUTUAL_FUNDS, // Proxy for Pension
        TransactionType.INVESTMENT_CONTRIBUTION to AppConstants.Categories.MUTUAL_FUNDS,
        TransactionType.LIABILITY_PAYMENT to AppConstants.Categories.CREDIT_BILL_PAYMENTS,
        TransactionType.TRANSFER to AppConstants.Categories.P2P_TRANSFERS,
        TransactionType.INCOME to AppConstants.Categories.OTHER_INCOME, 
        TransactionType.STATEMENT to "Credit Card Statement" // Separate category for Statements
    )

    fun categorize(
        counterparty: CounterpartyExtractor.Counterparty,
        transactionType: TransactionType,
        rules: List<CategorizationRule> = emptyList(),
        categoryMap: Map<Long, String> = emptyMap(),
        userAccounts: List<UserAccount> = emptyList(),
        messageBody: String = "", 
        salarySources: Set<Pair<String, String>> = emptySet(),
        salaryCompanyNames: Set<String> = emptySet(),
        merchantMemories: Map<String, Long> = emptyMap(), // ADAPTIVE CATEGORIZATION
        trace: MutableList<String>? = null
    ): String {
        // NEFT Self-Transfer Check
        if (messageBody.contains("NEFT", ignoreCase = true)) {
            if (CounterpartyExtractor.isNeftSelfTransfer(messageBody)) {
                trace?.add("Matched: NEFT Self Transfer pattern")
                return AppConstants.Categories.SELF_TRANSFER
            }
            
            // Known Salary Source Check
            if (salarySources.isNotEmpty()) {
                val neftSource = CounterpartyExtractor.extractNeftSource(messageBody)
                if (neftSource != null && salarySources.contains(neftSource)) {
                    trace?.add("Matched: Known Salary Source (NEFT)")
                    return AppConstants.Categories.SALARY
                }
            }
        }
        
        // Salary Company Name Check (User-configured)
        if (transactionType == TransactionType.INCOME && salaryCompanyNames.isNotEmpty()) {
            val upperBody = messageBody.uppercase()
            for (companyName in salaryCompanyNames) {
                if (upperBody.contains(companyName.uppercase())) {
                    trace?.add("Matched: Salary Company Name '$companyName'")
                    return AppConstants.Categories.SALARY
                }
            }
        }

        // Interest Income Check (Body-based)
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
        
        // Priority 1: User Rules
        val sortedRules = rules.sortedBy { it.patternType.ordinal } 
        
        for (rule in sortedRules) {
            val isMatch = when (rule.patternType) {
                PatternType.UPI_ID -> counterparty.upiId?.equals(rule.pattern, ignoreCase = true) == true
                PatternType.MERCHANT_NAME -> counterparty.name?.contains(rule.pattern, ignoreCase = true) == true
                PatternType.PAYEE_NAME -> counterparty.name?.contains(rule.pattern, ignoreCase = true) == true
                else -> false
            }
            
            if (isMatch) {
                trace?.add("Matched User Rule: ${rule.id} (${rule.pattern}) -> Category ID ${rule.categoryId}")
                return categoryMap[rule.categoryId] ?: AppConstants.Categories.UNCATEGORIZED
            }
        }
        if (rules.isNotEmpty()) trace?.add("No User Rules matched (${rules.size} checked)")

        // Priority 2: Hardcoded Merchant Map
        // MOVED UP: System Truth should override Adaptive Memory (which might contain bad learnings)
        counterparty.name?.let { name ->
            val upper = name.uppercase()
            
            // Special case: Chits
            if (upper.contains("CHIT") || upper.contains("SHRIRAM")) {
                 if (transactionType == TransactionType.INCOME) {
                     trace?.add("Matched: Chit/Shriram -> Investment Redemption")
                     return "Investment Redemption"
                 } else {
                     trace?.add("Matched: Chit/Shriram -> Chits")
                     return "Chits"
                 }
            }

            // Scan for the LONGEST matching merchant key to ensure specificity
            // e.g. Match "SWIGGY INSTAMART" (length 16) over "SWIGGY" (length 6)
            var bestMatchCategory: String? = null
            var maxMatchLength = 0
            var bestMatchKey = ""

            for ((key, defaultCategory) in MERCHANT_CATEGORIES) {
                if (upper.contains(key)) {
                    // Prevent "CRED" matching "CREDITED"
                    if (key == "CRED" && upper.contains("CREDITED") && 
                        !upper.contains("CRED APP") && !upper.contains("CRED CLUB")) {
                        continue
                    }
                    
                    if (key.length > maxMatchLength) {
                        // Investment Redemption check
                        if (transactionType == TransactionType.INCOME) {
                            if (defaultCategory == "Mutual Funds" || 
                                key == "ICCL" || 
                                key == "INDIAN CLEARING") {
                                // Special handling return immediately or treat strings differently?
                                // Let's just track it as the category
                                bestMatchCategory = "Investment Redemption"
                            } else {
                                bestMatchCategory = defaultCategory
                            }
                        } else {
                            bestMatchCategory = defaultCategory
                        }
                        
                        // Override for known redemption entities
                        if (upper.contains("INDIAN CLEARING") || upper.contains("ICCL")) {
                             if (transactionType == TransactionType.INCOME) bestMatchCategory = "Investment Redemption"
                        }
                        
                        maxMatchLength = key.length
                        bestMatchKey = key
                    }
                }
            }
            
            if (bestMatchCategory != null) {
                trace?.add("Matched Hardcoded Merchant: $bestMatchKey -> $bestMatchCategory")
                return enforceCategoryTypeCompatibility(bestMatchCategory, transactionType, trace)
            }
        }
        
        // Priority 2.5: Account Discovery matches (Self-Transfer / CC Bill)
        // Check this BEFORE Memory but AFTER Hardcoded Map (to allow explicit CC names to map to Credit Bills)
        if (counterparty.name != null) {
            val matchedAccount = userAccounts.find { myAccount ->
                counterparty.name.contains(myAccount.accountNumberLast4, ignoreCase = true)
            }
            if (matchedAccount != null) {
                trace?.add("Matched: Discovered Account ${matchedAccount.accountNumberLast4}")
                return if (matchedAccount.accountType == AccountType.CREDIT_CARD) {
                    AppConstants.Categories.CREDIT_BILL_PAYMENTS // Payment to my own Credit Card
                } else {
                    AppConstants.Categories.SELF_TRANSFER // Transfer to my own Bank Account
                }
            }
            
            if (isUserOwnName(counterparty.name, userAccounts)) {
                trace?.add("Matched: User Own Name detected (Self Transfer)")
                return AppConstants.Categories.SELF_TRANSFER
            }
        }

        // Recurring Deposit Check
        val lowerBody = messageBody.lowercase()
        if (SmsConstants.RD_PATTERNS.any { lowerBody.contains(it) }) {
             trace?.add("Matched: Recurring Deposit pattern")
             return AppConstants.Categories.RECURRING_DEPOSITS
        }

        // Cashback Check
        if (transactionType != TransactionType.EXPENSE && 
            (messageBody.contains("cashback", ignoreCase = true) || 
             messageBody.contains("reward", ignoreCase = true) ||
             (counterparty.name?.contains("cashback", ignoreCase = true) == true))) {
            
            trace?.add("Matched: Cashback/Reward keyword (Non-Expense)")
            return AppConstants.Categories.CASHBACK
        }
        
        // UPI Self-Transfer Check
        if (!counterparty.upiId.isNullOrBlank() && isUserOwnName(counterparty.upiId, userAccounts)) {
             trace?.add("Matched: User Own UPI detected (Self Transfer)")
             return "Self Transfer"
        }
        
        // Priority 3: ADAPTIVE MEMORY (Learned from User Corrections)
        if (!counterparty.name.isNullOrBlank()) {
            val normalized = counterparty.name.uppercase().trim()
            var memoryCategoryId = merchantMemories[normalized]
            
            // Try fuzzy match if exact match fails
            if (memoryCategoryId == null) {
                 // Simple containment check for memories
                 val match = merchantMemories.keys.find { normalized.contains(it) || it.contains(normalized) }
                 if (match != null) memoryCategoryId = merchantMemories[match]
            }
            
            if (memoryCategoryId != null) {
                val learnedCategory = categoryMap[memoryCategoryId]
                if (learnedCategory != null) {
                    trace?.add("Matched: Learned Memory for '${counterparty.name}' -> $learnedCategory")
                    return enforceCategoryTypeCompatibility(learnedCategory, transactionType, trace)
                }
            }
        }

        // Priority 5: ML Classifier (Name > Body)
        // DISABLED TEMPORARILY AS PER USER REQUEST
        var mlCategory: String? = null
        var mlConfidence = 0
        
        /*
        // Strategy A: Classify Name
        if (!counterparty.name.isNullOrBlank()) {
             val mlResult = com.saikumar.expensetracker.ml.NaiveBayesClassifier.classify(counterparty.name)
             if (mlResult != null) {
                 mlCategory = mlResult.category
                 mlConfidence = mlResult.confidence
                 trace?.add("Matched: ML Classifier (Name) -> $mlCategory (Conf: $mlConfidence)")
             }
        }
        
        // Strategy B: Classify Body (Fallback)
        if (mlCategory == null && messageBody.isNotBlank()) {
            val cleanedBody = SmsConstants.cleanMessageBody(messageBody)
            
            if (cleanedBody.length > 3) {
                val mlResult = com.saikumar.expensetracker.ml.NaiveBayesClassifier.classify(cleanedBody)
                // Enforce higher threshold and specific guardrails
                if (mlResult != null && mlResult.confidence >= 70) {
                     val candidate = mlResult.category
                     
                     // GUARDRAIL: "Dining Out" is often a false positive in ML (overfitted).
                     // Only accept it from Body ML if we see specific food keywords.
                     if (candidate == "Dining Out") {
                         val foodKeywords = setOf("food", "restaurant", "swiggy", "zomato", "hotel", "kitchen", "biryani", "pizza", "burger", "cafe", "coffee", "tea", "bakery", "cake", "sweet", "bar", "pub", "brewery", "diner", "mess")
                         val hasFoodKeyword = foodKeywords.any { cleanedBody.contains(it, ignoreCase = true) }
                         
                         if (hasFoodKeyword) {
                             mlCategory = candidate
                             mlConfidence = mlResult.confidence
                             trace?.add("Matched: ML Classifier (Body) -> $candidate (Validated with Food Keyword)")
                         } else {
                             trace?.add("Ignored: ML predicted 'Dining Out' but no food keywords found in body.")
                         }
                     } else {
                         // Accept other categories normally
                         mlCategory = candidate
                         mlConfidence = mlResult.confidence
                         trace?.add("Matched: ML Classifier (Body) -> $candidate (Conf: $mlConfidence)")
                     }
                }
            }
        }
        */
        
        if (mlCategory != null) {
            return enforceCategoryTypeCompatibility(mlCategory, transactionType, trace)
        }
        
        // Priority 5: Type-based Fallback
        val defaultCategory = TYPE_DEFAULT_CATEGORIES[transactionType] ?: AppConstants.Categories.UNCATEGORIZED

        // 5. Handling Generic/Offline Merchants
        // a) Q-Code / Card-Machine UPI (Generic Offline)
        if (counterparty.upiId?.matches(Regex("(?i)^q\\d+.*")) == true) {
             trace?.add("Matched: Q-Code VPA -> Offline Merchant")
             return enforceCategoryTypeCompatibility("Offline Merchant", transactionType, trace)
        }

        // b) Generic Fallback
        if (defaultCategory == "Uncategorized" && 
            (counterparty.type == CounterpartyExtractor.CounterpartyType.MERCHANT || 
             (transactionType == TransactionType.EXPENSE && !counterparty.name.isNullOrBlank()))) {
            trace?.add("Fallback: Generic Merchant/Expense with Name -> Miscellaneous")
            return enforceCategoryTypeCompatibility("Miscellaneous", transactionType, trace)
        }
        
        // Flag large unverified income
        if (defaultCategory == AppConstants.Categories.OTHER_INCOME && transactionType == TransactionType.INCOME) {
            val lowerBody = messageBody.lowercase()
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
     * Enforce compatibility between Category and Transaction Type.
     * Prevents ML/Keyword logic from assigning "Dining Out" (Expense) to an INCOME transaction.
     */
    private fun enforceCategoryTypeCompatibility(
        category: String,
        type: TransactionType,
        trace: MutableList<String>?
    ): String {
        // REFUND and CASHBACK types can be assigned to any category (returning money for purchases)
        if (type == TransactionType.REFUND || type == TransactionType.CASHBACK) return category

        // If it's NOT Income, we generally trust the category (Expenses can be anything)
        if (type != TransactionType.INCOME) return category

        // List of categories that are strictly expenses and should NEVER be Income
        // P2P Transfers and Self Transfer are excluded as they can be valid for incoming P2P returns
        val strictExpenseCategories = setOf(
            "Dining Out", "Groceries", "Fuel", "Utilities", "Shopping",
            "Entertainment", "Travel", "Cab & Taxi", "Food Delivery",
            "Medical", "Education", "Personal Care", "Furniture",
            "Electronics", "Mobile + WiFi", "Insurance", "Service",
            "Credit Bill Payments", "Miscellaneous", "Offline Merchant",
            "Gym & Fitness", "Stocks", "Gold", "Parking & Tolls"
            // Note: P2P Transfers and Self Transfer removed - valid for incoming transfers
        )

        if (category in strictExpenseCategories) {
            trace?.add("Incompatibility detected: INCOME type cannot be '$category' -> Reverting to 'Other Income'")
            return AppConstants.Categories.OTHER_INCOME
        }

        return category
    }
    
    /**
     * Check if the counterparty name matches the user's own name.
     * This helps detect self-transfers when money moves between user's accounts at different banks.
     * 
     * Uses account holder names discovered from NEFT/salary deposits.
     */
    private fun isUserOwnName(name: String, userAccounts: List<UserAccount>): Boolean {
        val lower = name.lowercase()
        val holderNames = userAccounts.mapNotNull { it.accountHolderName }.distinct()
        
        if (holderNames.isEmpty()) return false
        
        for (holderName in holderNames) {
            val holderLower = holderName.lowercase()
            
            if (lower.contains(holderLower)) return true
            
            // Check inverted formats (e.g. "Last First" matches "First Last")
            val holderParts = holderLower.split(" ").filter { it.length >= 3 }
            if (holderParts.size >= 2) {
                val matchCount = holderParts.count { part -> lower.contains(part) }
                if (matchCount >= 2) return true
            }
        }
        return false
    }
    
    fun calculateConfidence(category: String, wasUserRule: Boolean = false): Int {
        if (wasUserRule) return Confidence.USER_RULE
        
        return when (category) {
            AppConstants.Categories.SELF_TRANSFER -> Confidence.SELF_TRANSFER
            AppConstants.Categories.SALARY -> Confidence.SALARY_PATTERN
            "Interest" -> Confidence.PATTERN_MATCH
            "Investment Redemption" -> Confidence.PATTERN_MATCH
            
            in MERCHANT_CATEGORIES.values -> Confidence.MERCHANT_EXACT
            
            AppConstants.Categories.MUTUAL_FUNDS, 
            AppConstants.Categories.CREDIT_BILL_PAYMENTS, 
            AppConstants.Categories.P2P_TRANSFERS, 
            AppConstants.Categories.OTHER_INCOME -> Confidence.TYPE_DEFAULT
            
            AppConstants.Categories.MISCELLANEOUS -> Confidence.FALLBACK
            AppConstants.Categories.UNCATEGORIZED, "Unknown Expense" -> Confidence.UNCATEGORIZED
            "Unverified Income" -> Confidence.UNVERIFIED
            
            else -> Confidence.PATTERN_MATCH
        }
    }
}
