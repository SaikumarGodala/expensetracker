package com.saikumar.expensetracker.sms

/**
 * Shared constants and helpers for SMS processing.
 */
import com.saikumar.expensetracker.core.AppConstants
object SmsConstants {
    
    /**
     * Pattern to extract last 4 digits of bank account number.
     * Matches: "A/c XX1234", "Acct XX1234", "Account *1234"
     */
    val ACCOUNT_PATTERN = Regex("""(?:A/c|Acct|Account)\s+[*X]*(\d{4})""", RegexOption.IGNORE_CASE)
    
    /**
     * Pattern to extract last 4 digits of credit/debit card.
     * Matches: "Card ending 1234", "Credit Card XX1234"
     */
    val CARD_PATTERN = Regex("""(?:Card|Credit Card)\s+(?:ending\s+)?(?:[*X]*\s*)?(\d{4})""", RegexOption.IGNORE_CASE)
    
    /**
     * Extract account number last 4 digits from SMS body.
     * Tries account pattern first, then falls back to card pattern.
     */
    fun extractAccountLast4(body: String): String? {
        return ACCOUNT_PATTERN.find(body)?.groupValues?.get(1)
            ?: CARD_PATTERN.find(body)?.groupValues?.get(1)
    }
    
    // =====================================================
    // CASHBACK / REWARD PATTERNS
    // =====================================================
    
    /**
     * Patterns indicating cashback or reward transactions.
     * Used to detect CASHBACK transaction type from UPI IDs.
     */
    val CASHBACK_VPA_PATTERNS = listOf(
        "cashback", "reward", "promo", "bhimcashback"
    )
    
    /**
     * Check if a UPI ID indicates a cashback/reward transaction.
     */
    fun isCashbackVpa(upiId: String?): Boolean {
        val lower = upiId?.lowercase() ?: return false
        return CASHBACK_VPA_PATTERNS.any { lower.contains(it) }
    }

    /**
     * Patterns indicating Recurring Deposit transactions.
     */
    val RD_PATTERNS = listOf(
        "infoto rd", 
        "recurring deposit", 
        "rd ac no", 
        "towards rd",
        "credit to rd"
    )
    
    /**
     * Data class to hold invariant check inputs for transaction type resolution.
     */
    data class InvariantContext(
        val isDebit: Boolean?,
        val category: String,
        val upiId: String?,
        val counterpartyType: String, // "PERSON", "MERCHANT", "UNKNOWN"
        val isUntrustedP2P: Boolean = false
    )
    
    /**
     * Apply transaction type invariants.
     * 
     * Invariants:
     * 1. Credit transactions cannot be expenses -> convert to INCOME
     * 2. Cashback VPA patterns + credit -> CASHBACK
     * 3. P2P/Person entities (trusted) must be TRANSFER
     * 
     * @param currentType The current transaction type
     * @param ctx Context containing all fields needed for invariant checks
     * @return The corrected transaction type after applying invariants
     */
    @Deprecated("Use TransactionRuleEngine.resolveTransactionType instead")

    
    
    fun mapInitialTransactionType(
        parsedType: com.saikumar.expensetracker.data.entity.TransactionType,
        category: String,
        isUntrustedP2P: Boolean
    ): com.saikumar.expensetracker.data.entity.TransactionType {
        if (isUntrustedP2P) {
            return com.saikumar.expensetracker.data.entity.TransactionType.EXPENSE
        }
        
        return when {
            parsedType == com.saikumar.expensetracker.data.entity.TransactionType.STATEMENT -> 
                com.saikumar.expensetracker.data.entity.TransactionType.STATEMENT
            category == AppConstants.Categories.CREDIT_BILL_PAYMENTS -> 
                com.saikumar.expensetracker.data.entity.TransactionType.LIABILITY_PAYMENT
            category == AppConstants.Categories.CASHBACK || category == AppConstants.Keywords.CASHBACK_REWARDS -> 
                com.saikumar.expensetracker.data.entity.TransactionType.CASHBACK
            category == AppConstants.Categories.RECURRING_DEPOSITS ->
                com.saikumar.expensetracker.data.entity.TransactionType.INVESTMENT_OUTFLOW
            else -> parsedType // Pass through as-is
        }
    }
    
    
    data class NormalizedMerchant(
        val raw: String?,
        val normalized: String?,
        val shouldCreateAlias: Boolean
    )
    
    /**
     * Normalize merchant name and determine if an alias should be created.
     * 
     * @param rawMerchant The raw merchant name from counterparty extraction
     * @return NormalizedMerchant containing both names and whether to create alias
     */
    fun normalizeMerchant(rawMerchant: String?): NormalizedMerchant {
        val normalized = com.saikumar.expensetracker.util.MerchantNormalizer.normalize(rawMerchant)
        val shouldCreateAlias = !rawMerchant.isNullOrBlank() && 
                                !normalized.isNullOrBlank() && 
                                rawMerchant != normalized
        return NormalizedMerchant(rawMerchant, normalized, shouldCreateAlias)
    }

    /**
     * Clean message body for ML training/prediction.
     * Removes:
     * - Numbers, Special chars
     * - Common bank stop words (debited, credited, bank, etc)
     * - Short words (< 3 chars)
     */
    fun cleanMessageBody(body: String): String {
        // 1. Lowercase
        var text = body.lowercase()
        
        // 2. Remove common headers/footers loosely
        text = text.replace(Regex("(?i)(hdfc|icici|sbi|axis|kotak|bob|pnb|paytm|phonepe|gpay|upi).*?bank"), "")
        
        // 3. Replace all non-alphabetic chars with space
        text = text.replace(Regex("[^a-z]"), " ")
        
        // 4. Tokenize
        val tokens = text.split(" ").filter { it.isNotBlank() }
        
        // 5. Filter Stop Words
        val stopWords = setOf(
            "debited", "credited", "account", "acct", "bank", "balance", "bal", "available",
            "transaction", "txn", "ref", "reference", "info", "sms", "call", "block", "card",
            "spent", "using", "ending", "with", "for", "from", "limit", "updated", "click",
            "link", "points", "reward", "total", "amount", "inr", "rs", "payment", "received",
            "successful", "sent", "done", "request", "dear", "customer", "your", "yours",
            "date", "time", "help", "care", "number", "alert", "update", "available"
        )
        
        return tokens
            .filter { it.length > 2 } // Remove short junk
            .filter { !stopWords.contains(it) } // Remove stop words
            .joinToString(" ")
    }
}
