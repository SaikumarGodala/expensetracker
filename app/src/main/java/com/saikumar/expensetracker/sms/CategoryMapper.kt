package com.saikumar.expensetracker.sms

import com.saikumar.expensetracker.data.entity.*

object CategoryMapper {

    private val MERCHANT_CATEGORIES = mapOf(
        // Salary (User Specific Overrides)
      //  "ZF IND" to "Salary",
        //"OPEN TEXT" to "Salary",

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

        // Groceries
        "DMART" to "Groceries",
        "RATNADEEP" to "Groceries",
        "VIJETHA" to "Groceries",
        "BHARAT BAZAR" to "Groceries",
        "SMPOORNA" to "Groceries",
        "AVENUE SUPERMAR" to "Groceries",
        "ZEPTO" to "Groceries",
        "BLINKIT" to "Groceries",
        // "LICIOUS" to "Food Delivery", // Removed duplicate

        // Shopping
        "AMAZON" to "Shopping",
        "FLIPKART" to "Shopping",
        "RELIANCE" to "Shopping",
        "JOCKEY" to "Shopping",
        "ZUDIO" to "Shopping",
        "MYNTRA" to "Shopping",
        "AJIO" to "Shopping",

        // Travel / Cab
        "TSRTC" to "Travel",
        "IRCTC" to "Travel",
        "REDBUS" to "Travel",
        "RAPIDO" to "Cab & Taxi",
        "OLA" to "Cab & Taxi",
        "UBER" to "Cab & Taxi",

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
        "PAYTMQR" to "Miscellaneous",
        "VYAPAR" to "Miscellaneous",
        "GPAY" to "Miscellaneous",

        // Financial Services
        "CRED" to "Credit Bill Payments", // Covers "CRED Club"
        "STATEMENT" to "Credit Bill Payments",
        "SBICARD" to "Credit Bill Payments",
        "HDFCCARD" to "Credit Bill Payments",
        "AXISCARD" to "Credit Bill Payments",
        "AMEX" to "Credit Bill Payments",
        "CITIBANK" to "Credit Bill Payments",
        "ONECARD" to "Credit Bill Payments",
        "BILLDESK" to "Credit Bill Payments",
        "CCPAY" to "Credit Bill Payments",

        // P2P Transfers (Person Names identified from logs)
        "SHIVKUMAR" to "P2P Transfers",
        "MANOHARACHARI" to "P2P Transfers",
        "VANKAYALA" to "P2P Transfers",
        "GADDAM" to "P2P Transfers",
        "GODALA" to "P2P Transfers",
        "NEERUDI" to "P2P Transfers",
        "PRATIKSHA" to "P2P Transfers",
        "MD ASIF" to "P2P Transfers",
        "KALALU" to "P2P Transfers",
        "PARIM" to "P2P Transfers",
        "RANGINENI" to "P2P Transfers",
        "PARIGI" to "P2P Transfers",

        // Bank Interest
        "IDFC FIRST BANK" to "Savings Interest",
        "INTEREST" to "Savings Interest"
    )

    private val TYPE_DEFAULT_CATEGORIES = mapOf(
        TransactionExtractor.TransactionType.PENSION to "Mutual Funds", // Proxy for Pension
        TransactionExtractor.TransactionType.INVESTMENT to "Mutual Funds",
        TransactionExtractor.TransactionType.LIABILITY to "Credit Bill Payments",
        TransactionExtractor.TransactionType.TRANSFER to "P2P Transfers",
        TransactionExtractor.TransactionType.INCOME to "Other Income", // Changed from Salary
        TransactionExtractor.TransactionType.STATEMENT to "Credit Bill Payments" // Show under Credit Bills
    )

    fun categorize(
        counterparty: CounterpartyExtractor.Counterparty,
        transactionType: TransactionExtractor.TransactionType,
        rules: List<CategorizationRule> = emptyList(),
        categoryMap: Map<Long, String> = emptyMap(),
        userAccounts: List<UserAccount> = emptyList(),
        messageBody: String = "", // Optional: raw SMS body for advanced detection
        salarySources: Set<Pair<String, String>> = emptySet() // Known salary sources (IFSC, sender)
    ): String {
        // 0. CHECK NEFT SELF-TRANSFER (sender == receiver)
        // This catches deposits from your own account at another bank
        if (messageBody.contains("NEFT", ignoreCase = true)) {
            if (CounterpartyExtractor.isNeftSelfTransfer(messageBody)) {
                return "Self Transfer"
            }
            
            // 0.5. CHECK IF NEFT SOURCE IS A KNOWN SALARY SOURCE
            if (salarySources.isNotEmpty()) {
                val neftSource = CounterpartyExtractor.extractNeftSource(messageBody)
                if (neftSource != null && salarySources.contains(neftSource)) {
                    return "Salary"
                }
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
                return categoryMap[rule.categoryId] ?: "Uncategorized"
            }
        }

        // 1.5. CHECK SELF-TRANSFER / BILL PAYMENT (Discovered Accounts)
        // If the counterparty name mentions one of our accounts (e.g. "Account XX2725" or "Card 1234")
        if (counterparty.name != null) {
            val matchedAccount = userAccounts.find { myAccount ->
                counterparty.name.contains(myAccount.accountNumberLast4, ignoreCase = true)
            }
            if (matchedAccount != null) {
                return if (matchedAccount.accountType == AccountType.CREDIT_CARD) {
                    "Credit Bill Payments" // Payment to my own Credit Card
                } else {
                    "Self Transfer" // Transfer to my own Bank Account
                }
            }
            
            // 1.6. CHECK IF RECIPIENT NAME MATCHES USER'S OWN NAME (Self-Transfer)
            // This catches transfers to yourself at different banks
            if (isUserOwnName(counterparty.name, userAccounts)) {
                return "Self Transfer"
            }
        }

        // 2. Try merchant name match (Hardcoded Map)
        counterparty.name?.let { name ->
            val upper = name.uppercase()
            for ((key, category) in MERCHANT_CATEGORIES) {
                if (upper.contains(key)) return category
            }
        }
        
        // 3. Handle UPI Income explicitly (User req: UPI is P2P, not Salary)
        if (transactionType == TransactionExtractor.TransactionType.INCOME && counterparty.upiId != null) {
            return "P2P Transfers"
        }

        // 4. Fall back to type-based default
        return TYPE_DEFAULT_CATEGORIES[transactionType] ?: "Uncategorized"
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
        
        // Check if counterparty name contains any of the discovered holder names
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
}
