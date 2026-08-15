package com.saikumar.expensetracker.sms

/**
 * Shared constants and helpers for SMS processing.
 */
import com.saikumar.expensetracker.core.AppConstants
object SmsConstants {

    // =====================================================
    // WORD-BOUNDARY TEXT MATCHING
    // =====================================================
    //
    // BUGFIX: Plain `String.contains(key)` checks throughout the categorization
    // pipeline caused systematic false positives because short/generic keys
    // matched as substrings of unrelated words. For example, with a naive
    // `contains` check:
    //   - "IND"   matched inside "INDIA", "INDIAN CLEARING", "INDUSIND BANK"
    //   - "CASH"  matched inside "CASHBACK", "CASHFREE"
    //   - "GOLD"  matched inside "GOLDEN DRAGON RESTAURANT"
    //   - "SPA"   matched inside a person's name like "SPARSH"
    //   - "CRED"  matched inside "CREDIT" (not just "CREDITED")
    // `containsToken` treats a match as valid only when it is not directly
    // adjacent to another letter, i.e. it behaves like a word-boundary match
    // but still allows adjacency to digits/punctuation (common in merchant
    // codes like "BPCL0091234").

    /**
     * Returns true if [token] appears in [text] as a standalone word/phrase -
     * i.e. not embedded inside a longer alphabetic word.
     *
     * Both [text] and [token] should already be normalized to the same case
     * (this function does not itself change case).
     */
    fun containsToken(text: String, token: String): Boolean {
        if (token.isEmpty()) return false
        var fromIndex = 0
        while (true) {
            val idx = text.indexOf(token, fromIndex)
            if (idx == -1) return false

            val beforeOk = idx == 0 || !text[idx - 1].isLetter()
            val afterIdx = idx + token.length
            val afterOk = afterIdx >= text.length || !text[afterIdx].isLetter()

            if (beforeOk && afterOk) return true
            fromIndex = idx + 1
        }
    }

    /** True if any of [tokens] appears in [text] as a standalone word/phrase. */
    fun containsAnyToken(text: String, tokens: Collection<String>): Boolean =
        tokens.any { containsToken(text, it) }

    /**
     * Credit-card bill payment services / card issuers as they appear in counterparty
     * names. Shared by CategoryMapper (Priority-0 CC override) and CounterpartyExtractor
     * (debited-credited classification) - previously duplicated in both.
     *
     * [text] must already be uppercase. "CRED" uses word-boundary matching so it doesn't
     * fire inside "CREDIT"/"CREDITED".
     */
    fun isCreditCardServiceName(text: String): Boolean {
        return text.contains("CRED CLUB") ||
            text.contains("CRED APP") ||
            containsToken(text, "CRED") ||
            text.contains("AMEX") ||
            text.contains("ONE CARD") ||
            text.contains("ONECARD") ||
            text.contains("SBI CARD") ||
            text.contains("SBICARD") ||
            text.contains("HDFC CARD") ||
            text.contains("HDFCCARD") ||
            text.contains("AXIS CARD") ||
            text.contains("AXISCARD") ||
            text.contains("ICICI CARD") ||
            text.contains("ICICCARD") ||
            text.contains("BILLDESK")
    }

    /**
     * Pattern to extract the bank account number tail. Captures the FULL digit run;
     * extractAccountLast4 keeps its last 4. A fixed (\d{4}) grabbed the FIRST 4 digits of
     * longer masks ("A/C XXXXX286210" -> "2862" instead of "6210"), splitting one physical
     * account into two identities in the ledger.
     * Matches: "A/c XX1234", "Acct XX1234", "Account *1234", "A/C XXXXX286210"
     */
    val ACCOUNT_PATTERN = Regex("""(?:A/c|Acct|Account)\s+[*X]*(\d{4,})""", RegexOption.IGNORE_CASE)

    /**
     * Pattern to extract last 4 digits of credit/debit card.
     * Matches: "Card ending 1234", "Credit Card XX1234"
     */
    val CARD_PATTERN = Regex("""(?:Card|Credit Card)\s+(?:ending\s+)?(?:[*X]*\s*)?(\d{4,})""", RegexOption.IGNORE_CASE)

    /**
     * Extract account number last 4 digits from SMS body.
     * Tries account pattern first, then falls back to card pattern.
     */
    fun extractAccountLast4(body: String): String? {
        val run = ACCOUNT_PATTERN.find(body)?.groupValues?.get(1)
            ?: CARD_PATTERN.find(body)?.groupValues?.get(1)
            ?: return null
        return run.takeLast(4)
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
