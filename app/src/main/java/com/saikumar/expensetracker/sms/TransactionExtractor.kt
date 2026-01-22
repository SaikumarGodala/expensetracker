package com.saikumar.expensetracker.sms

import com.saikumar.expensetracker.data.entity.TransactionType
import java.util.regex.Pattern

object TransactionExtractor {

    // AUDIT: Removed duplicate enum - now using data.entity.TransactionType directly
    // Type aliases for mapping compatibility (old names → new names)
    // LIABILITY → LIABILITY_PAYMENT
    // INVESTMENT → INVESTMENT_CONTRIBUTION

    data class ExtractedTransaction(
        val amount: Double?,
        val type: TransactionType,
        val isDebit: Boolean?,
        val accountHint: String? = null
    )

    // Amount patterns - order matters! More specific patterns first
    private val AMOUNT_PATTERNS = listOf(
        // Currency prefix patterns (most reliable)
        Pattern.compile("Rs\\.?\\s*([\\d,]+\\.?\\d*)"),
        Pattern.compile("INR\\s*([\\d,]+\\.?\\d*)"),
        Pattern.compile("₹\\s*([\\d,]+\\.?\\d*)"),
        // "debited by X" / "credited with X" patterns (SBI format)
        Pattern.compile("(?:debited|credited|spent|withdrawn|deposited)\\s*(?:by|with|of)?\\s*([\\d,]+\\.?\\d*)", Pattern.CASE_INSENSITIVE),
        // Amount before keyword pattern (fallback)
        Pattern.compile("([\\d,]+\\.?\\d*)\\s*(?:debited|credited|spent|withdrawn|deposited)")
    )

    // Debit indicators
    private val DEBIT_KEYWORDS = listOf(
        "debited", "spent", "withdrawn", "w/d", "deducted", "paid", "sent"
    )

    // Credit indicators
    private val CREDIT_KEYWORDS = listOf(
        "credited", "deposited", "received", "added", "refund"
    )

    // Transfer indicators (between own accounts)
    private val TRANSFER_KEYWORDS = listOf(
        "to a/c", "to VPA", "IMPS", "NEFT", "transferred"
    )

    // Credit card payment indicators
    private val CC_PAYMENT_KEYWORDS = listOf(
        "card ending", "credit card", "cardmember", "payment received towards"
    )
    
    // Statement indicators
    private val STATEMENT_KEYWORDS = listOf(
        "total amount due", "min due", "statement generated", "stmt generated", "total due", 
        "statement is sent", "total of", "minimum of", "e-stmt", "estmt",
        "amt due", "pay by"
    )

    // Regex for "Payment for" pattern
    private val PAYMENT_FOR_PATTERN = Pattern.compile("payment.*?for", Pattern.CASE_INSENSITIVE)
    
    // Merchant signals - words that indicate a business, not a person
    private val MERCHANT_SIGNALS = listOf(
        "REDBUS", "SWIGGY", "ZOMATO", "AMAZON", "FLIPKART", "MYNTRA", "UBER", "OLA",
        "CENTRAL", "BAKERY", "BAKE", "CAFE", "RESTAURANT", "FOODS", "FOOD",
        "STORE", "SHOP", "MART", "MALL", "AUTO", "PRIVATE", "LIMITED", "LTD", "PVT",
        "INC", "CORP", "LLC", "SERVICES", "TRAVELS", "TRAVEL", "TECH", "TECHNOLOGIES",
        "BROADBAND", "INTERNET", "TELECOM", "MOBILE", "INSURANCE", "BROKER", "BROKING",
        "INDIA", "INTERNATIONAL", "GLOBAL", "ENTERPRISES", "SOLUTIONS", "SYSTEMS",
        "SUPERMARKET", "HYPERMARKET", "GROCERY", "GROCER", "MARKET", "EXPRESS",
        "CLUB", "CRED", "HOTEL", "LODGE", "RESORT", "SPA", "SALON", "PARLOUR",
        "PHARMACY", "MEDICAL", "HOSPITAL", "CLINIC", "DIAGNOSTIC", "LAB", "LABS",
        "PETROL", "DIESEL", "FUEL", "GAS", "STATION", "PUMP"
    )

    fun extract(body: String, senderType: SenderClassifier.SenderType): ExtractedTransaction {
        val amount = extractAmount(body)
        val isDebit = detectDebit(body)
        
        val type = when (senderType) {
            SenderClassifier.SenderType.PENSION -> TransactionType.PENSION
            SenderClassifier.SenderType.INVESTMENT -> TransactionType.INVESTMENT_CONTRIBUTION
            SenderClassifier.SenderType.INSURANCE -> if (isDebit == true) TransactionType.EXPENSE else TransactionType.UNKNOWN
            SenderClassifier.SenderType.BANK -> detectBankTransactionType(body, isDebit)
            SenderClassifier.SenderType.VIRTUAL_CARD -> detectBankTransactionType(body, isDebit) // Treat like bank
            else -> TransactionType.UNKNOWN
        }

        return ExtractedTransaction(amount, type, isDebit)
    }

    private fun extractAmount(body: String): Double? {
        // if (body == null) return null // Removed redundant check
        for (pattern in AMOUNT_PATTERNS) {
            val matcher = pattern.matcher(body)
            if (matcher.find()) {
                val matchedGroup = matcher.group(1)
                if (matchedGroup != null) {
                    val amountStr = matchedGroup.replace(",", "")
                    return amountStr.toDoubleOrNull()
                }
            }
        }
        return null
    }

    private fun detectDebit(body: String): Boolean? {
        val lower = body.lowercase()
        
        // FIX #3: Card spending is ALWAYS a debit, regardless of other keywords
        // Pattern: "On HDFC Bank Card XXX At <merchant>" or "Spent Rs.XXX On Card"
        if ((lower.contains("on") && lower.contains("bank card") && lower.contains("at")) ||
            (lower.contains("spent") && lower.contains("card"))) {
            return true  // DEBIT
        }
        
        val hasDebit = DEBIT_KEYWORDS.any { lower.contains(it) }
        val hasCredit = CREDIT_KEYWORDS.any { lower.contains(it) }
        
        return when {
            hasDebit && !hasCredit -> true
            hasCredit && !hasDebit -> false
            hasDebit && hasCredit -> {
                // FIX #4: First-keyword-wins rule
                // Find position of first debit and first credit keyword
                val firstDebitPos = DEBIT_KEYWORDS.mapNotNull { 
                    val idx = lower.indexOf(it)
                    if (idx >= 0) idx else null 
                }.minOrNull() ?: Int.MAX_VALUE
                
                val firstCreditPos = CREDIT_KEYWORDS.mapNotNull { 
                    val idx = lower.indexOf(it)
                    if (idx >= 0) idx else null 
                }.minOrNull() ?: Int.MAX_VALUE
                
                // The first keyword determines direction (your transaction is stated first)
                when {
                    firstDebitPos < firstCreditPos -> true   // "debited...credited" = DEBIT
                    firstCreditPos < firstDebitPos -> false  // "credited...debited" = CREDIT
                    else -> null  // Shouldn't happen
                }
            }
            else -> null
        }
    }

    private fun detectBankTransactionType(body: String, isDebit: Boolean?): TransactionType {
        val lower = body.lowercase()
        
        // Statement detection
        if (STATEMENT_KEYWORDS.any { lower.contains(it) }) {
            return TransactionType.STATEMENT
        }
        
        // P2P Transfer detection (Priority: High)
        // "Sent Rs.X To <NAME>" = UPI P2P transfer, NOT an expense
        // "debited for Rs X; <NAME> credited" = UPI P2P transfer
        // BUT: If the name matches a known merchant, treat as EXPENSE
        if ((lower.contains("sent") || lower.contains("debited for")) && 
            (lower.contains(" to ") || lower.contains("credited")) &&
            !lower.contains("card") &&  // Not a card transaction
            !lower.contains("merchant")) {  // Not a merchant
            // Check for patterns that indicate a PERSON recipient
            val hasPersonName = Regex("""(?:To|to)\s+[A-Z][A-Za-z\s]{2,}(?:\n|\r|On)""", RegexOption.MULTILINE).containsMatchIn(body) ||
                               Regex("""for\s+Rs\s+[\d,\.]+[^;]*;\s*[A-Z][A-Za-z\s]+\s+credited""", RegexOption.IGNORE_CASE).containsMatchIn(body)
            if (hasPersonName) {
                // Extract the name to check if it's a merchant
                val nameMatch = Regex("""(?:To|to)\s+([A-Za-z][A-Za-z\s]+?)(?:\n|\r|On)""").find(body)
                val recipientName = nameMatch?.groupValues?.getOrNull(1)?.trim()?.uppercase() ?: ""
                
                // Check if recipient is a known merchant (not a person)
                // Expanded list to include Investment entities which often look like Person names
                val isMerchant = MERCHANT_SIGNALS.any { recipientName.contains(it) } ||
                                 recipientName.contains("ZERODHA") || 
                                 recipientName.contains("ICCL") || 
                                 recipientName.contains("INDIAN CLEARING") ||
                                 recipientName.contains("GROWW") ||
                                 recipientName.contains("KUVERA")
                
                if (!isMerchant) {
                    return TransactionType.TRANSFER
                }
            }
        }
        
        // 2. DETECT INVESTMENT (High Priority)
        // Check for known investment keywords/merchants
        if (lower.contains("zerodha") || lower.contains("groww") || 
            lower.contains("coin") || lower.contains("kuvera") || 
            lower.contains("indmoney") || lower.contains("smallcase") ||
            (lower.contains("mutual") && lower.contains("fund")) ||
            lower.contains("iccl") || lower.contains("indian clearing")) {
            
            // If it's a credit, it's Redemption (Income), otherwise Contribution
            if (lower.contains("credited") || lower.contains("received") || lower.contains("deposited")) {
                return TransactionType.INCOME // Will be mapped to Investment Redemption category
            } else {
                return TransactionType.INVESTMENT_CONTRIBUTION
            }
        }
        
        // CARD SPEND DETECTION (Priority High)
        // Matches: "Txn Rs... On ... Card"
        if ((lower.contains("txn") || lower.contains("spent")) && lower.contains("card")) {
             // Ensure it's not a payment received *for* a card
             if (!lower.contains("payment received")) {
                 return TransactionType.EXPENSE
             }
        }

        // CC payment detection
        if (CC_PAYMENT_KEYWORDS.any { lower.contains(it) } && lower.contains("payment")) {
            // Refinement: "Payment for Youtube" or "Debited from Credit Card" is an EXPENSE.
            // Use regex to catch "Payment of X for Y"
            val isExpense = PAYMENT_FOR_PATTERN.matcher(lower).find() || 
                           lower.contains("debited from") ||
                           lower.contains("spent on") ||
                           lower.contains("using")
                           
            if (!isExpense) {
                return TransactionType.LIABILITY_PAYMENT
            }
        }

        // Self-transfer detection (same user accounts)
        if (TRANSFER_KEYWORDS.any { lower.contains(it) } && 
            (lower.contains("to a/c") || lower.contains("from a/c"))) {
            return TransactionType.TRANSFER
        }

        return when (isDebit) {
            true -> TransactionType.EXPENSE
            false -> TransactionType.INCOME
            null -> TransactionType.UNKNOWN
        }
    }
    
    /**
     * Detect all keywords matched in the SMS body for debug logging.
     * Returns a list of matched keywords grouped by category.
     */
    fun detectKeywords(body: String): List<String> {
        val lower = body.lowercase()
        val matched = mutableListOf<String>()
        
        // Check debit keywords
        DEBIT_KEYWORDS.filter { lower.contains(it) }.forEach { matched.add("DEBIT:$it") }
        
        // Check credit keywords
        CREDIT_KEYWORDS.filter { lower.contains(it) }.forEach { matched.add("CREDIT:$it") }
        
        // Check transfer keywords
        TRANSFER_KEYWORDS.filter { lower.contains(it.lowercase()) }.forEach { matched.add("TRANSFER:$it") }
        
        // Check CC payment keywords
        CC_PAYMENT_KEYWORDS.filter { lower.contains(it) }.forEach { matched.add("CC_PAY:$it") }
        
        // Check statement keywords
        STATEMENT_KEYWORDS.filter { lower.contains(it) }.forEach { matched.add("STATEMENT:$it") }
        
        // Check merchant signals (case-insensitive)
        val upper = body.uppercase()
        MERCHANT_SIGNALS.filter { upper.contains(it) }.forEach { matched.add("MERCHANT:$it") }
        
        return matched
    }
}
