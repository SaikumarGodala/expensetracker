package com.saikumar.expensetracker.sms

import android.content.Context
import android.net.Uri
import android.util.Log
import com.saikumar.expensetracker.ExpenseTrackerApplication
import com.saikumar.expensetracker.data.entity.*
import com.saikumar.expensetracker.util.ClassificationDebugLogger
import com.saikumar.expensetracker.data.entity.RawInputCapture
import com.saikumar.expensetracker.data.entity.ParsedFields
import com.saikumar.expensetracker.data.entity.FinalDecision
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.time.Duration
import java.util.regex.Pattern

object SmsProcessor {

    private const val TAG = "SmsProcessor"
    private const val MAX_TRANSACTION_PAISA = 10_00_00_000_00L // ₹10 crore in paisa

    // Removed company-specific keywords (ZF, OPEN TEXT) - these were developer-specific
    private val SALARY_KEYWORDS = listOf(
        "SALARY", "PAYROLL", "SAL CREDIT", "MONTHLY SALARY", "EMP SAL",
        "EPF", "PROVIDENT FUND", "PF CONTRIBUTION", "PF CREDIT"
    )
    private val EXCLUSION_KEYWORDS = listOf("REFUND", "CASHBACK", "REVERSAL", "REWARD", "INCENTIVE", "BONUS", "INTEREST", "WALLET", "LOAN")

    // ============ CREDIT CARD PAYMENT DETECTION ============
    // Priority 1: Explicit CC payment keywords (99% confidence)
    private val CC_EXPLICIT_KEYWORDS = listOf(
        "CC PAYMENT", "CC PAY", "CC BILL",
        "CREDIT CARD PAYMENT", "CREDIT CARD BILL PAYMENT",
        "CARD BILL PAYMENT", "CREDITCARD BILL", "CREDITCARD PAYMENT",
        "PAYMENT RECEIVED ON CREDIT CARD", "RECEIVED ON YOUR CREDIT CARD",
        "CREDITED TO YOUR CREDIT CARD", "CREDITED TO YOUR CC",
        "PAYMENT RECEIVED TOWARDS YOUR CREDIT CARD"
    )

    // Known Indian CC issuers for context matching
    private val CC_ISSUERS = listOf(
        "HDFC", "ICICI", "SBI CARD", "AXIS", "KOTAK", "AMEX",
        "AMERICAN EXPRESS", "CITI", "CITIBANK", "RBL", "INDUSIND",
        "YES BANK", "HSBC", "STANDARD CHARTERED", "AU BANK", "IDFC FIRST",
        "BOB", "BANK OF BARODA", "PNB", "CANARA", "UNION BANK"
    )

    // Payment-indicating verbs
    private val CC_PAYMENT_VERBS = listOf(
        "PAID", "PAYMENT", "PAY", "TRANSFERRED", "DEBITED",
        "AUTO-DEBIT", "AUTODEBIT", "AUTOPAY", "AUTO PAY",
        "NACH", "ECS", "SI ", "STANDING INSTRUCTION"
    )

    // Card context keywords
    private val CC_CARD_CONTEXT = listOf(
        "CC", "CREDIT CARD", "CARD", "CREDITCARD"
    )

    // Third-party CC payment apps (Priority 3: 90% confidence)
    private val CC_PAYMENT_APPS = listOf(
        "CRED"  // CRED is ONLY for CC payments
    )

    // Negative signals - these BLOCK CC payment classification
    // If ANY of these are present, it's NOT a CC payment (it's likely a spend)
    private val CC_NEGATIVE_SIGNALS = listOf(
        "PURCHASE", "SHOPPING", "SHOPPED",
        "REFUND", "CASHBACK", "CASH BACK",
        "REVERSAL", "REVERSED",
        "EMI", "NO COST EMI",
        "POS", "ECOM", "E-COM",
        "SWIPE", "TAP", "CONTACTLESS",
        "REWARD", "POINTS",
        "LIMIT", "AVAILABLE",  // Balance/limit notifications
        "DUE DATE", "REMINDER",  // Just reminders, not payments
        "STATEMENT READY", "BILL GENERATED"  // Notifications, not payments
    )

    // ============ CREDIT CARD SPEND DETECTION ============
    // Keywords indicating a purchase/spend USING a credit card
    private val CC_SPEND_KEYWORDS = listOf(
        "SPENT", "SPEND", "PURCHASE", "PURCHASED",
        "SHOPPED", "SHOPPING",
        "SWIPE", "SWIPED", "TAP", "TAPPED",
        "TXN AT", "TRANSACTION AT", "USED AT",
        "POS", "ECOM", "E-COM", "ECOMMERCE", "E-COMMERCE",
        "CONTACTLESS", "CHIP TRANSACTION",
        "ONLINE PAYMENT", "ONLINE TXN"
    )

    // Card network identifiers - indicate card usage
    private val CARD_NETWORKS = listOf(
        "VISA", "MASTERCARD", "RUPAY", "AMEX",
        "AMERICAN EXPRESS", "DINERS", "MAESTRO", "DISCOVER"
    )

    // Masked card number patterns (to identify card transactions)
    private val CARD_NUMBER_PATTERN = Regex("\\b(XX|\\*\\*|X{2,4})\\s*\\d{4}\\b", RegexOption.IGNORE_CASE)

    // Enhanced merchant patterns with more coverage
    private val DEFAULT_MERCHANT_PATTERNS = mapOf(
        // Food Delivery
        "SWIGGY" to "Food Outside",
        "ZOMATO" to "Food Outside",
        "DOMINOS" to "Food Outside",
        "PIZZA HUT" to "Food Outside",
        "MCDONALDS" to "Food Outside",
        "KFC" to "Food Outside",
        "SUBWAY" to "Food Outside",
        "LICIOUS" to "Food Outside",
        "INSTAMA" to "Groceries",

        // Ride Sharing
        "UBER" to "Travel",
        "OLA" to "Travel",
        "RAPIDO" to "Travel",
        "TAXI" to "Travel",
        "AUTO" to "Travel",
        "REDBUS" to "Travel",

        // E-commerce
        "AMAZON" to "Apparel / Shopping",
        "FLIPKART" to "Apparel / Shopping",
        "MYNTRA" to "Apparel / Shopping",
        "AJIO" to "Apparel / Shopping",
        "MEESHO" to "Apparel / Shopping",
        "NYKAA" to "Grooming",

        // Fuel
        "PETROL" to "Fuel",
        "DIESEL" to "Fuel",
        "IOCL" to "Fuel",
        "BPCL" to "Fuel",
        "HPCL" to "Fuel",
        "SHELL" to "Fuel",
        "BHARAT PETROLEUM" to "Fuel",
        "INDIAN OIL" to "Fuel",

        // Gaming & Entertainment
        "GAME" to "Entertainment",
        "GAMING" to "Entertainment",

        // Electronics
        "CROMA" to "Electronics",
        "RELIANCE DIGITAL" to "Electronics",
        "VIJAY SALES" to "Electronics",
        "SAMSUNG" to "Electronics",
        "MI STORE" to "Electronics",

        // Medical
        "APOLLO" to "Medical",
        "MEDPLUS" to "Medical",
        "PHARMA" to "Medical",
        "1MG" to "Medical",
        "NETMEDS" to "Medical",
        "PHARMEASY" to "Medical",
        "HOSPITAL" to "Medical",
        "CLINIC" to "Medical",
        "DR " to "Medical",

        // Entertainment
        "NETFLIX" to "Subscriptions",
        "AMAZON PRIME" to "Subscriptions",
        "HOTSTAR" to "Subscriptions",
        "DISNEY" to "Subscriptions",
        "SPOTIFY" to "Subscriptions",
        "YOUTUBE" to "Subscriptions",
        "BOOKMYSHOW" to "Entertainment",
        "PVR" to "Entertainment",
        "INOX" to "Entertainment",
        "CINEPOLIS" to "Entertainment",

        // Coffee & Restaurants
        "STARBUCKS" to "Food Outside",
        "CAFE COFFEE DAY" to "Food Outside",
        "CAFE" to "Food Outside",
        "COFFEE" to "Food Outside",
        "RESTAURANT" to "Food Outside",
        "HOTEL" to "Food Outside",

        // Public Transport
        "METRO" to "Travel",
        "RAILWAY" to "Travel",
        "IRCTC" to "Travel",
        "DMRC" to "Travel",
        "BUS" to "Travel",

        // Groceries
        "BIGBASKET" to "Groceries",
        "BLINKIT" to "Groceries",
        "INSTAMART" to "Groceries",
        "ZEPTO" to "Groceries",
        "DUNZO" to "Groceries",
        "DMART" to "Groceries",
        "RELIANCE FRESH" to "Groceries",
        "MORE" to "Groceries",
        "SUPERMARKET" to "Groceries",
        "GROCERY" to "Groceries",

        // Utilities
        "ELECTRICITY" to "Home Expenses",
        "WATER" to "Home Expenses",
        "GAS" to "Home Expenses",
        "PIPED GAS" to "Home Expenses",

        // Telecom
        "AIRTEL" to "Mobile + WiFi",
        "JIO" to "Mobile + WiFi",
        "VODAFONE" to "Mobile + WiFi",
        "BSNL" to "Mobile + WiFi",
        "ACT FIBERNET" to "Mobile + WiFi",
        "HATHWAY" to "Mobile + WiFi",

        // Insurance
        "LIC" to "Insurance (Life + Health + Term)",
        "HDFC LIFE" to "Insurance (Life + Health + Term)",
        "ICICI PRUDENTIAL" to "Insurance (Life + Health + Term)",
        "MAX LIFE" to "Insurance (Life + Health + Term)",
        "INSURANCE" to "Insurance (Life + Health + Term)",

        // Investments
        "ZERODHA" to "Investments / Dividends",
        "GROWW" to "Investments / Dividends",
        "UPSTOX" to "Investments / Dividends",
        "KITE" to "Investments / Dividends",
        "COIN" to "Investments / Dividends",

        // Credit Card Payments & Online Transfers
        "CREDIT CARD" to "Credit Bill Payments",
        "CRED" to "Credit Bill Payments",
        "CREDITCARD" to "Credit Bill Payments",
        "CC PAYMENT" to "Credit Bill Payments",
        "CARD BILL" to "Credit Bill Payments",
        "ONLINE PAYMENT" to "Credit Bill Payments",
        "BHARAT BILL PAYMENT" to "Credit Bill Payments"
    )

    // This will store user-defined patterns loaded from database
    private val userDefinedPatterns = mutableMapOf<String, String>()

    /**
     * Initialize user-defined patterns from database
     */
    suspend fun loadUserPatterns(context: Context) = withContext(Dispatchers.IO) {
        val app = context.applicationContext as ExpenseTrackerApplication
        try {
            val patterns = app.preferencesManager.loadMerchantPatternsSync()
            userDefinedPatterns.clear()
            userDefinedPatterns.putAll(patterns)
            Log.d(TAG, "Loaded ${patterns.size} user patterns from preferences")
        } catch (e: Exception) {
            Log.e(TAG, "Error loading user patterns", e)
        }
    }

    /**
     * Generate a hash of the SMS body for deduplication.
     * Returns first 16 characters of SHA-256.
     */
    private fun generateSmsHash(body: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(body.toByteArray())
        return hashBytes.take(8).joinToString("") { "%02x".format(it) }
    }

    /**
     * Extract a clean, meaningful snippet from SMS for display.
     * Removes noise like transaction IDs, hashes, long numbers.
     * Keeps: merchant name, UPI ID, purpose, meaningful reference.
     * 
     * @param smsBody Full SMS text
     * @param maxLength Maximum snippet length (default 120 chars)
     * @return Cleaned snippet for display
     */
    fun extractSmsSnippet(smsBody: String, maxLength: Int = 120): String {
        var cleaned = smsBody
        
        // Step 1: Remove patterns that are noise
        val noisePatterns = listOf(
            // Transaction IDs / Reference numbers (long alphanumeric strings)
            Pattern.compile("\\b[A-Z0-9]{15,}\\b"),
            // OTP / temporary codes
            Pattern.compile("\\bOTP\\s*[:\\-]?\\s*\\d+\\b", Pattern.CASE_INSENSITIVE),
            // Long numeric sequences (>10 digits) - usually IDs
            Pattern.compile("\\b\\d{11,}\\b"),
            // Hash-like strings
            Pattern.compile("\\b[a-f0-9]{16,}\\b", Pattern.CASE_INSENSITIVE),
            // Standard bank SMS prefixes
            Pattern.compile("(?i)^(ALERT|Dear Customer|Dear User)[,:]?\\s*"),
            // Common noise phrases
            Pattern.compile("(?i)\\b(if not done by you|call|contact)\\b.*$"),
            Pattern.compile("(?i)\\bAvl\\s*Bal[:\\s]*Rs\\.?\\s*[\\d,\\.]+\\b"),
            // URLs
            Pattern.compile("https?://\\S+"),
            // "Do not share" warnings
            Pattern.compile("(?i)\\bdo\\s*not\\s*share.*$")
        )
        
        for (pattern in noisePatterns) {
            cleaned = pattern.matcher(cleaned).replaceAll(" ")
        }
        
        // Step 2: Normalize whitespace
        cleaned = cleaned.replace("\\s+".toRegex(), " ").trim()
        
        // Step 3: Extract key information if we can identify it
        val extractedParts = mutableListOf<String>()
        
        // Extract amount (keep this as it's useful context)
        val amountPattern = Pattern.compile("(?i)Rs\\.?\\s*([\\d,]+\\.?\\d*)")
        val amountMatcher = amountPattern.matcher(smsBody)
        if (amountMatcher.find()) {
            extractedParts.add("₹${amountMatcher.group(1)?.replace(",", "")}")
        }
        
        // Extract transaction type
        val txnType = when {
            smsBody.contains("debited", ignoreCase = true) -> "debited"
            smsBody.contains("credited", ignoreCase = true) -> "credited"
            smsBody.contains("paid", ignoreCase = true) -> "paid"
            smsBody.contains("received", ignoreCase = true) -> "received"
            else -> null
        }
        if (txnType != null) {
            extractedParts.add(txnType)
        }
        
        // Extract UPI ID (useful for identifying sender/receiver)
        val upiPattern = Pattern.compile("(?i)([a-zA-Z0-9._-]+@[a-zA-Z]+)")
        val upiMatcher = upiPattern.matcher(smsBody)
        if (upiMatcher.find()) {
            val upiId = upiMatcher.group(1)
            if (upiId != null && upiId.length <= 40) {
                extractedParts.add(upiId)
            }
        }
        
        // Extract "to/from/at" merchant
        val merchantPatterns = listOf(
            Pattern.compile("(?i)(?:to|at|from)\\s+([A-Z][A-Za-z\\s&]{2,30})(?=\\s+on|\\s+for|\\.|$)"),
            Pattern.compile("(?i)VPA\\s*:?\\s*([a-zA-Z0-9._@-]+)")
        )
        for (pattern in merchantPatterns) {
            val matcher = pattern.matcher(smsBody)
            if (matcher.find()) {
                val merchant = matcher.group(1)?.trim()
                if (merchant != null && merchant.length >= 3 && merchant.length <= 35) {
                    extractedParts.add("→ $merchant")
                    break
                }
            }
        }
        
        // Step 4: Build final snippet
        // If we extracted structured parts, use them; otherwise use cleaned text
        val result = if (extractedParts.size >= 2) {
            extractedParts.joinToString(" ")
        } else if (cleaned.length > 10) {
            cleaned
        } else {
            // Fallback: first meaningful part of original
            smsBody.take(maxLength)
        }
        
        // Truncate if too long
        return if (result.length > maxLength) {
            result.take(maxLength - 3) + "..."
        } else {
            result
        }
    }

    // ============ SIMILARITY MATCHING FOR CATEGORIZATION ============
    
    /**
     * Result of similarity matching for categorization
     */
    data class SimilarityResult(
        val pattern: String?,
        val patternType: PatternType?,
        val matchingTransactionIds: List<Long>,
        val confidence: Double,
        val displayDescription: String
    )

    /**
     * Extract UPI ID from text (e.g., swiggy@paytm, user@ybl)
     */
    fun extractUpiId(text: String): String? {
        val upiPattern = Pattern.compile("(?i)([a-zA-Z0-9._-]+@[a-zA-Z]{2,})(?![a-zA-Z])")
        val matcher = upiPattern.matcher(text)
        if (matcher.find()) {
            val upiId = matcher.group(1)?.lowercase()
            // Filter out email-like patterns (contains common email domains)
            if (upiId != null && !upiId.contains("gmail") && !upiId.contains("yahoo") 
                && !upiId.contains("hotmail") && !upiId.contains(".com")) {
                return upiId
            }
        }
        return null
    }

    /**
     * Extract NEFT/IMPS reference description from text.
     * Returns the full reference after NEFT- prefix.
     * Example: "NEFT-DEUTH05533207350-ZF IND" → "DEUTH05533207350"
     */
    fun extractNeftReference(text: String): String? {
        // Pattern: NEFT-REFERENCE or NEFT Cr-REFERENCE
        val patterns = listOf(
            Pattern.compile("(?i)NEFT[\\s-]+(?:Cr[\\s-]+)?([A-Z0-9]+)"),
            Pattern.compile("(?i)IMPS[\\s-]+([A-Z0-9]+)"),
            Pattern.compile("(?i)RTGS[\\s-]+([A-Z0-9]+)")
        )
        for (pattern in patterns) {
            val matcher = pattern.matcher(text.uppercase())
            if (matcher.find()) {
                return matcher.group(1)?.trim()
            }
        }
        return null
    }
    
    /**
     * Extract generic transaction reference number.
     * Supports UPI refs, RRN, generic Ref numbers.
     * Example: "UPI:637552882537" → "637552882537"
     *          "RRN 343936797870" → "343936797870"
     */
    fun extractReferenceNumber(text: String): String? {
        val patterns = listOf(
            // UPI reference
            Pattern.compile("(?i)UPI[:\\s]+([0-9]+)"),
            // RRN (Retrieval Reference Number)
            Pattern.compile("(?i)RRN[:\\s]+([0-9]+)"),
            // Generic Ref
            Pattern.compile("(?i)Ref[:\\s#]+([0-9A-Z]+)", Pattern.CASE_INSENSITIVE),
            // Transaction ID
            Pattern.compile("(?i)(?:Txn|Transaction)\\s*(?:ID|No)[:\\s#]+([0-9A-Z]+)", Pattern.CASE_INSENSITIVE),
            // Reference number
            Pattern.compile("(?i)Reference[:\\s#]+([0-9A-Z]+)", Pattern.CASE_INSENSITIVE)
        )
        
        for (pattern in patterns) {
            val matcher = pattern.matcher(text)
            if (matcher.find()) {
                val ref = matcher.group(1)?.trim()
                // Only return if reference is meaningful (6+ characters)
                if (ref != null && ref.length >= 6) {
                    return ref
                }
            }
        }
        return null
    }
    
    /**
     * Extract bank code prefix from NEFT reference.
     * Used for matching similar transactions from same sender.
     * 
     * Examples:
     *   - DEUTN52025... → DEUT (Deutsche Bank)
     *   - DEUTH05533... → DEUT (Deutsche Bank)
     *   - BOFA0CN6215 → BOFA (Bank of America)
     *   
     * Logic: Extract first 4 letters before any digit.
     */
    fun extractNeftBankCode(text: String): String? {
        val neftRef = extractNeftReference(text) ?: return null
        
        // Find first 4-5 letters before digits in the reference
        val bankCodePattern = Pattern.compile("^([A-Z]{4,5})")
        val matcher = bankCodePattern.matcher(neftRef.uppercase())
        if (matcher.find()) {
            val code = matcher.group(1)
            // Truncate to 4 chars for consistency (DEUTN → DEUT, BOFA0 → BOFA)
            return code?.take(4)
        }
        return null
    }

    /**
     * Normalize text for matching by removing noise
     */
    fun normalizeForMatching(input: String): String {
        return input
            .uppercase()
            .replace(Regex("\\b\\d{10,}\\b"), "")      // Long numbers (IDs)
            .replace(Regex("@[A-Z]+$"), "")            // Bank suffix (@ICICI)
            .replace(Regex("-[A-Z]{3,5}$"), "")         // Bank codes (-HDFC)
            .replace(Regex("\\b(MR|MRS|DR|MS|SHRI)\\.?\\s*"), "") // Honorifics
            .replace(Regex("\\d{2,}$"), "")            // Trailing numbers
            .replace(Regex("\\s+"), " ")               // Normalize whitespace
            .trim()
    }

    /**
     * Find transactions similar to a source transaction for batch categorization.
     * Uses priority-based matching: UPI ID > Merchant Name > NEFT Reference
     */
    suspend fun findSimilarTransactions(
        sourceTransaction: Transaction,
        allTransactions: List<Transaction>
    ): SimilarityResult {
        val sourceSnippet = sourceTransaction.smsSnippet ?: sourceTransaction.merchantName ?: ""
        
        // Extract identifiers from source
        val sourceUpi = extractUpiId(sourceSnippet)
        val sourceMerchant = normalizeForMatching(sourceTransaction.merchantName ?: "")
        val sourceNeft = extractNeftReference(sourceSnippet)
        
        val upiMatches = mutableListOf<Long>()
        val merchantMatches = mutableListOf<Long>()
        val neftMatches = mutableListOf<Long>()
        
        for (txn in allTransactions) {
            if (txn.id == sourceTransaction.id) continue
            
            val txnSnippet = txn.smsSnippet ?: txn.merchantName ?: ""
            val txnUpi = extractUpiId(txnSnippet)
            val txnMerchant = normalizeForMatching(txn.merchantName ?: "")
            val txnNeft = extractNeftReference(txnSnippet)
            
            // Priority 1: UPI ID exact match (99% confidence)
            if (sourceUpi != null && sourceUpi == txnUpi) {
                upiMatches.add(txn.id)
                continue
            }
            
            // Priority 2: Merchant name exact match after normalization (95%)
            if (sourceMerchant.isNotEmpty() && sourceMerchant.length >= 3 
                && sourceMerchant == txnMerchant) {
                merchantMatches.add(txn.id)
                continue
            }
            
            // Priority 3: NEFT reference match (85%)
            if (sourceNeft != null && sourceNeft == txnNeft) {
                neftMatches.add(txn.id)
            }
        }
        
        // Return best match type (most matches with highest confidence)
        return when {
            upiMatches.isNotEmpty() -> SimilarityResult(
                pattern = sourceUpi,
                patternType = PatternType.UPI_ID,
                matchingTransactionIds = upiMatches,
                confidence = 0.99,
                displayDescription = "UPI ID: $sourceUpi"
            )
            merchantMatches.isNotEmpty() -> SimilarityResult(
                pattern = sourceMerchant,
                patternType = PatternType.MERCHANT_NAME,
                matchingTransactionIds = merchantMatches,
                confidence = 0.95,
                displayDescription = "Merchant: $sourceMerchant"
            )
            neftMatches.isNotEmpty() -> SimilarityResult(
                pattern = sourceNeft,
                patternType = PatternType.NEFT_REFERENCE,
                matchingTransactionIds = neftMatches,
                confidence = 0.85,
                displayDescription = "Reference: $sourceNeft"
            )
            else -> SimilarityResult(
                pattern = null,
                patternType = null,
                matchingTransactionIds = emptyList(),
                confidence = 0.0,
                displayDescription = "No similar transactions found"
            )
        }
    }

    private fun getDebitCategory(smsBody: String, categories: List<Category>): Category? {
        val upperBody = smsBody.uppercase()

        // First check user-defined patterns (higher priority)
        for ((keyword, categoryName) in userDefinedPatterns) {
            if (upperBody.contains(keyword)) {
                val category = categories.find { it.name.equals(categoryName, ignoreCase = true) }
                if (category != null) {
                    Log.d(TAG, "Debit categorized as: $categoryName (user pattern: $keyword)")
                    return category
                }
            }
        }

        // Then check default patterns
        for ((keyword, categoryName) in DEFAULT_MERCHANT_PATTERNS) {
            if (upperBody.contains(keyword)) {
                val category = categories.find { it.name.equals(categoryName, ignoreCase = true) }
                if (category != null) {
                    Log.d(TAG, "Debit categorized as: $categoryName (matched: $keyword)")
                    return category
                }
            }
        }

        // Try to extract merchant name from common SMS patterns
        val merchantCategory = extractMerchantFromSmsPattern(upperBody, categories)
        if (merchantCategory != null) {
            Log.d(TAG, "Debit categorized from SMS pattern: ${merchantCategory.name}")
            return merchantCategory
        }

        return null
    }

    private fun extractMerchantFromSmsPattern(smsBody: String, categories: List<Category>): Category? {
        val patterns = listOf(
            Pattern.compile("(?:AT|TO)\\s+([A-Z][A-Z\\s&]+?)(?:\\s+ON|\\s+FOR|\\s+VIA|\\.|$)"),
            Pattern.compile("(?:PAID TO|DEBITED FROM)\\s+([A-Z][A-Z\\s&]+?)(?:\\s+ON|\\s+FOR|\\.|$)"),
            Pattern.compile("TXN\\s+(?:AT|TO)\\s+([A-Z][A-Z\\s&]+?)(?:\\s+ON|\\.|$)")
        )

        for (pattern in patterns) {
            val matcher = pattern.matcher(smsBody)
            if (matcher.find()) {
                val merchantName = matcher.group(1)?.trim() ?: continue
                Log.d(TAG, "Extracted potential merchant: $merchantName")

                for ((keyword, categoryName) in DEFAULT_MERCHANT_PATTERNS) {
                    if (merchantName.contains(keyword)) {
                        return categories.find { it.name.equals(categoryName, ignoreCase = true) }
                    }
                }
            }
        }

        return null
    }

    // ============ CREDIT CARD SPEND DETECTION ============
    
    /**
     * Result of CC spend detection
     */
    data class CCSpendResult(
        val isCCSpend: Boolean,
        val confidence: Double = 0.0,
        val reason: String = ""
    )

    /**
     * Detect if a transaction is a CREDIT CARD SPEND (purchase made USING a credit card).
     * 
     * FINANCIAL RULE: CC spends are EXPENSES on the spend date.
     * They should NOT be confused with CC bill payments.
     * 
     * @param smsBody The full SMS text
     * @return Detection result with confidence
     */
    private fun detectCCSpend(smsBody: String): CCSpendResult {
        val upper = smsBody.uppercase()
        
        // Step 1: Check for explicit spend keywords
        val matchedSpendKeyword = CC_SPEND_KEYWORDS.find { upper.contains(it) }
        if (matchedSpendKeyword != null) {
            // Verify it's not a payment (check for payment context)
            val hasPaymentContext = CC_PAYMENT_VERBS.any { upper.contains(it) } &&
                                   CC_ISSUERS.any { upper.contains(it) } &&
                                   !CC_SPEND_KEYWORDS.any { upper.contains(it) }
            
            if (!hasPaymentContext) {
                Log.d(TAG, "CC spend detected (keyword): $matchedSpendKeyword")
                return CCSpendResult(
                    isCCSpend = true,
                    confidence = 0.95,
                    reason = "Spend keyword: $matchedSpendKeyword"
                )
            }
        }
        
        // Step 2: Check for card network identifiers (VISA, Mastercard, etc.)
        val matchedNetwork = CARD_NETWORKS.find { upper.contains(it) }
        if (matchedNetwork != null) {
            // Card network + merchant context = likely a spend
            val hasMerchantContext = listOf("AT ", "TO ", "FOR ", "@ ").any { upper.contains(it) }
            if (hasMerchantContext) {
                Log.d(TAG, "CC spend detected (network + merchant): $matchedNetwork")
                return CCSpendResult(
                    isCCSpend = true,
                    confidence = 0.90,
                    reason = "Card network $matchedNetwork + merchant"
                )
            }
        }
        
        // Step 3: Check for masked card number pattern (XX1234, **5678)
        if (CARD_NUMBER_PATTERN.containsMatchIn(upper)) {
            // Has card number pattern + no bank debit = likely card spend
            val noBankDebit = !upper.contains("A/C ") && !upper.contains("ACCOUNT ")
            val hasMerchant = listOf("AT ", "TO ", "FOR ").any { upper.contains(it) }
            
            if (noBankDebit && hasMerchant) {
                Log.d(TAG, "CC spend detected (card pattern + no bank debit)")
                return CCSpendResult(
                    isCCSpend = true,
                    confidence = 0.80,
                    reason = "Card number pattern + no bank account"
                )
            }
        }
        
        return CCSpendResult(
            isCCSpend = false,
            reason = "No CC spend indicators"
        )
    }

    // ============ SELF-TRANSFER DETECTION ============
    
    /**
     * Result of self-transfer pair detection
     */
    data class SelfTransferPair(
        val debitTransactionId: Long,
        val creditTransactionId: Long,
        val amountPaisa: Long,
        val timeDiffMinutes: Long
    )

    /**
     * Find self-transfer pairs in a list of transactions.
     * 
     * ALGORITHM: Self-transfers appear as matching pairs:
     * - Same amount
     * - One debit (EXPENSE), one credit (INCOME)
     * - Within 5 minutes of each other
     * 
     * @param transactions List of transactions to analyze
     * @param maxMinutesApart Maximum time difference (default: 5 minutes)
     * @return List of detected self-transfer pairs
     */
    fun findSelfTransferPairs(
        transactions: List<Transaction>,
        maxMinutesApart: Long = 5
    ): List<SelfTransferPair> {
        val pairs = mutableListOf<SelfTransferPair>()
        val sorted = transactions.sortedBy { it.timestamp }
        val usedIds = mutableSetOf<Long>()
        
        for (i in sorted.indices) {
            val t1 = sorted[i]
            if (t1.id in usedIds) continue
            
            // Look for potential pair within the time window
            for (j in i + 1 until sorted.size) {
                val t2 = sorted[j]
                if (t2.id in usedIds) continue
                
                val timeDiffMinutes = (t2.timestamp - t1.timestamp) / 60000
                
                // Stop searching if too far apart
                if (timeDiffMinutes > maxMinutesApart) break
                
                // Check for matching pair: same amount, opposite types
                if (t1.amountPaisa == t2.amountPaisa) {
                    val t1IsCredit = t1.transactionType == TransactionType.INCOME
                    val t2IsCredit = t2.transactionType == TransactionType.INCOME
                    
                    // One must be income (credit), one must be expense (debit)
                    if (t1IsCredit != t2IsCredit) {
                        val debitTxn = if (t1IsCredit) t2 else t1
                        val creditTxn = if (t1IsCredit) t1 else t2
                        
                        Log.d(TAG, "Self-transfer pair found: ₹${t1.amountPaisa/100} in ${timeDiffMinutes}min")
                        
                        pairs.add(SelfTransferPair(
                            debitTransactionId = debitTxn.id,
                            creditTransactionId = creditTxn.id,
                            amountPaisa = t1.amountPaisa,
                            timeDiffMinutes = timeDiffMinutes
                        ))
                        
                        usedIds.add(t1.id)
                        usedIds.add(t2.id)
                        break  // Move to next transaction
                    }
                }
            }
        }
        
        Log.d(TAG, "Found ${pairs.size} self-transfer pairs")
        return pairs
    }

    /**
     * STEP 0.5: Extract counterparty (PERSON) from SMS with STRICT validation.
     * Runs BEFORE merchant sanitization/resolution.
     * 
     * ✅ DIRECTION INDEPENDENT: Extraction runs regardless of transaction direction.
     * The presence of "credited" or "debited" phrases indicates WHO was involved, 
     * NOT the transaction direction constraint.
     * 
     * Example:
     * - SMS: "debited ... aishwarya rao00 credited"
     * - Direction: DEBIT (amount left account)
     * - Extraction: "aishwarya rao" (beneficiary/counterparty)
     * - These are orthogonal: direction answers "what happened to me"
     *   extraction answers "who was involved"
     * 
     * ✅ Extracts: "aishwarya rao00 credited" → PERSON "aishwarya rao"
     * ✅ Extracts: "VAJRAPU SUVARNA PRAK credited" → PERSON "VAJRAPU SUVARNA PRAK"
     * ❌ Rejects: "for Amazon to be debited" → system phrase
     * ❌ Rejects: "Cashback of INR 517 has been credited" → monetary phrase
     * 
     * VALIDATION RULES (ALL REQUIRED):
     * 1. Word count: 2-4 words only
     * 2. Alphabetic ratio ≥ 70% (mostly letters, not numbers)
     * 3. No placeholders (CRED, BANK, CARD, UPI, gateways)
     * 4. No account tokens (A/C, XX, XXXX, account numbers)
     * 5. No monetary tokens (INR, RS, ₹, PAISA, AMOUNT) - NOT action verbs like "credited"
     * 6. No stop-phrases (TO BE, HAS BEEN, WILL BE, FOR, etc.)
     * 7. Not a known merchant or brand (AMAZON, SWIGGY, etc.)
     * 8. At least one word starts with capital letter
     * 
     * NORMALIZATION (MANDATORY):
     * - Remove trailing digits from words: "rao00" → "rao"
     * - Trim extra spaces
     * 
     * Returns: CounterpartyExtraction with validation details and direction-independence diagnostics
     */
    fun extractCounterparty(smsBody: String): CounterpartyExtraction {
        val placeholderMerchants = setOf(
            "CRED", "BANK", "CARD", "UPI", "IMPS", "NEFT", "RTGS", "BBPS", "AEPS",
            "ICICI", "HDFC", "SBI", "AXIS", "KOTAK", "IDBI", "UPSC", "HSBC", "STANDARD CHARTERED",
            "PAYTM", "GOOGLEPAY", "PHONEPE", "WHATSAPP", "RAZORPAY", "CASHFREE", "BILLDESK"
        )
        
        val realMerchants = setOf(
            "AMAZON", "FLIPKART", "SWIGGY", "UBER", "ZOMATO", "NETFLIX", "HOTSTAR",
            "AIRTEL", "JIO", "VODAFONE", "BSNL", "SPOTIFY", "ADOBE", "MICROSOFT",
            "AIRBNB", "BOOKING", "MAKEMYTRIP", "CLEARTRIP"
        )
        
        val accountTokens = setOf("A/C", "AC", "ACCOUNT", "XX", "XXXX", "XXXXX", "XXXXXX")
        // NOTE: Removed DEBIT, CREDIT, DEBITED, CREDITED from monetaryTokens
        // These are action verbs used as pattern DELIMITERS (e.g., "<NAME> credited")
        // They should NEVER appear in the extracted name because regex captures only the name part
        // Including them was causing direction-bias suppression of valid extractions
        val monetaryTokens = setOf("INR", "RS", "RUPEE", "PAISA", "AMOUNT", "₹")
        val stopPhrases = setOf("TO BE", "HAS BEEN", "WILL BE", "FOR", "OF", "THE")
        
        /**
         * Validate extracted candidate against all mandatory rules
         * Returns: Pair<isValid, rejectionReason>
         */
        fun validateExtraction(candidate: String): Pair<Boolean, String?> {
            val trimmed = candidate.trim()
            val words = trimmed.split("\\s+".toRegex())
            val upper = trimmed.uppercase()
            
            // Rule 1: Word count 2-4 words
            if (words.size < 2 || words.size > 4) {
                return false to "Word count ${words.size} (need 2-4)"
            }
            
            // Rule 2: Alphabetic ratio ≥ 70%
            val letterCount = trimmed.count { it.isLetter() }
            val digitCount = trimmed.count { it.isDigit() }
            val totalAlpha = letterCount + digitCount
            if (totalAlpha == 0) {
                return false to "No alphanumeric characters"
            }
            val alphabeticRatio = letterCount.toDouble() / totalAlpha
            if (alphabeticRatio < 0.70) {
                return false to "Alphabetic ratio ${(alphabeticRatio * 100).toInt()}% (need ≥70%)"
            }
            
            // Rule 3: No placeholders
            if (placeholderMerchants.any { upper.contains(it) }) {
                return false to "Contains placeholder merchant"
            }
            
            // Rule 4: No account tokens
            if (accountTokens.any { upper.contains(it) }) {
                return false to "Contains account token"
            }
            
            // Rule 5: No monetary tokens
            if (monetaryTokens.any { upper.contains(it) }) {
                return false to "Contains monetary token"
            }
            
            // Rule 6: No stop-phrases (check word boundaries)
            for (stopPhrase in stopPhrases) {
                if (upper.contains(stopPhrase)) {
                    // "FOR" is in "TRANSFER" but we want to block "FOR" as standalone stop
                    val pattern = Pattern.compile("\\b$stopPhrase\\b", Pattern.CASE_INSENSITIVE)
                    if (pattern.matcher(trimmed).find()) {
                        return false to "Contains stop-phrase: '$stopPhrase'"
                    }
                }
            }
            
            // Rule 7: Not a known merchant or brand
            if (realMerchants.any { upper.contains(it) }) {
                return false to "Contains known brand/merchant"
            }
            
            // Rule 8: At least one word starts with capital letter
            val hasCapitalStart = words.any { it.isNotEmpty() && it[0].isUpperCase() }
            if (!hasCapitalStart) {
                return false to "No word starts with capital letter"
            }
            
            return true to null
        }
        
        /**
         * Normalize extracted name: remove trailing digits from words
         * Example: "rao00" → "rao", "aishwarya" → "aishwarya"
         */
        fun normalizeExtraction(candidate: String): String {
            val words = candidate.split("\\s+".toRegex())
            val normalized = words.map { word ->
                word.replace(Regex("\\d+$"), "")  // Remove trailing digits
            }.filter { it.isNotEmpty() }
            return normalized.joinToString(" ").trim()
        }
        
        // Pattern 0: IDFC/ICICI-style "; <NAME> credited" (semicolon separator before name)
        // Handles: "debited; aishwarya rao00 credited", "debited by Rs. 1,00,000.00; VAJRAPU SUVARNA PRAK credited"
        // This pattern MUST run before generic NAME_BEFORE_ACTION to catch this specific format
        val semicolonNamePattern = Pattern.compile(
            ";\\s*([A-Za-z][A-Za-z0-9\\s]{2,40}?)\\s+credited",
            Pattern.CASE_INSENSITIVE
        )
        var matcher = semicolonNamePattern.matcher(smsBody)
        if (matcher.find()) {
            val extracted = matcher.group(1)?.trim()
            if (extracted != null) {
                val (isValid, rejectionReason) = validateExtraction(extracted)
                if (isValid) {
                    val normalized = normalizeExtraction(extracted)
                    Log.d(TAG, "COUNTERPARTY_EXTRACTION_SUCCESS: Extracted='$extracted' → Normalized='$normalized' (SEMICOLON_NAME_PATTERN, IDFC/ICICI format)")
                    return CounterpartyExtraction(
                        extractedName = normalized,
                        extractionRule = "SEMICOLON_NAME_PATTERN",
                        confidence = "HIGH",  // High confidence for this specific format
                        found = true,
                        directionAtExtraction = "DEBIT_OR_CREDIT_INDEPENDENT",
                        phrasesMatched = listOf("semicolon_separator", extracted),
                        suppressedByDirection = false
                    )
                } else {
                    Log.d(TAG, "COUNTERPARTY_EXTRACTION_REJECTED: Candidate='$extracted' rejected: $rejectionReason (pattern: SEMICOLON_NAME_PATTERN)")
                }
            }
        }
        
        // Pattern 1: "<NAME> credited" or "<NAME> debited" (name BEFORE action verb)
        // Handles: "aishwarya rao00 credited", "VAJRAPU SUVARNA PRAK credited"
        val nameBeforeActionPattern = Pattern.compile(
            "\\b([A-Za-z][A-Za-z0-9\\s]{2,40}?)\\s+(?:credited|debited|transferred|paid|received)\\b",
            Pattern.CASE_INSENSITIVE
        )
        matcher = nameBeforeActionPattern.matcher(smsBody)
        if (matcher.find()) {
            val extracted = matcher.group(1)?.trim()
            if (extracted != null) {
                val (isValid, rejectionReason) = validateExtraction(extracted)
                if (isValid) {
                    val normalized = normalizeExtraction(extracted)
                    Log.d(TAG, "COUNTERPARTY_EXTRACTION_SUCCESS: Extracted='$extracted' → Normalized='$normalized' (NAME_BEFORE_ACTION, alphabetic ratio OK, valid person name)")
                    return CounterpartyExtraction(
                        extractedName = normalized,
                        extractionRule = "NAME_BEFORE_ACTION",
                        confidence = "MEDIUM",
                        found = true,
                        directionAtExtraction = "DEBIT_OR_CREDIT_INDEPENDENT",
                        phrasesMatched = listOf("credited_or_debited", extracted),
                        suppressedByDirection = false
                    )
                } else {
                    Log.d(TAG, "COUNTERPARTY_EXTRACTION_REJECTED: Candidate='$extracted' rejected: $rejectionReason (pattern: NAME_BEFORE_ACTION)")
                }
            }
        }
        
        // Pattern 2: "credited from <NAME>" or "debited to <NAME>"
        // Handles explicit transfer patterns
        val explicitTransferPatterns = listOf(
            Pattern.compile("(?:credited from|deposited from|transferred from|received from)\\s+([A-Za-z0-9][A-Za-z0-9\\s]{2,40}?)(?:\\s+on|\\.|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?:debited to|paid to|transferred to|sent to)\\s+([A-Za-z0-9][A-Za-z0-9\\s]{2,40}?)(?:\\s+on|\\.|$)", Pattern.CASE_INSENSITIVE)
        )
        for (pattern in explicitTransferPatterns) {
            matcher = pattern.matcher(smsBody)
            if (matcher.find()) {
                val extracted = matcher.group(1)?.trim()
                if (extracted != null) {
                    val (isValid, rejectionReason) = validateExtraction(extracted)
                    if (isValid) {
                        val normalized = normalizeExtraction(extracted)
                        Log.d(TAG, "COUNTERPARTY_EXTRACTION_SUCCESS: Extracted='$extracted' → Normalized='$normalized' (EXPLICIT_TRANSFER, all rules passed)")
                        return CounterpartyExtraction(
                            extractedName = normalized,
                            extractionRule = "EXPLICIT_TRANSFER",
                            confidence = "MEDIUM",
                            found = true,
                            directionAtExtraction = "DEBIT_OR_CREDIT_INDEPENDENT",
                            phrasesMatched = listOf("credited_from_or_debited_to", extracted),
                            suppressedByDirection = false
                        )
                    } else {
                        Log.d(TAG, "COUNTERPARTY_EXTRACTION_REJECTED: Candidate='$extracted' rejected: $rejectionReason (pattern: EXPLICIT_TRANSFER)")
                    }
                }
            }
        }
        
        // Pattern 3: UPI handle extraction
        // Handles: "raj@ybl", "aishwarya.rao@okaxis", etc.
        if (smsBody.contains("@")) {
            val upiPattern = Pattern.compile("[a-zA-Z0-9._-]+@[a-zA-Z0-9]+")
            matcher = upiPattern.matcher(smsBody)
            if (matcher.find()) {
                val upiId = matcher.group()
                val beforeAt = upiId.substringBefore("@")
                if (beforeAt.length >= 3 && beforeAt.any { it.isLetter() }) {
                    val humanName = beforeAt
                        .replace(".", " ")
                        .replace("_", " ")
                        .replace("-", " ")
                        .trim()
                    
                    val (isValid, rejectionReason) = validateExtraction(humanName)
                    if (isValid) {
                        val normalized = normalizeExtraction(humanName)
                        Log.d(TAG, "COUNTERPARTY_EXTRACTION_SUCCESS: UPI='$upiId' → Name='$humanName' → Normalized='$normalized' (UPI_HANDLE, validation passed)")
                        return CounterpartyExtraction(
                            extractedName = normalized,
                            extractionRule = "UPI_HANDLE",
                            confidence = "MEDIUM",
                            found = true,
                            directionAtExtraction = "DEBIT_OR_CREDIT_INDEPENDENT",
                            phrasesMatched = listOf("upi_handle", upiId),
                            suppressedByDirection = false
                        )
                    } else {
                        Log.d(TAG, "COUNTERPARTY_EXTRACTION_REJECTED: UPI='$upiId' extracted name='$humanName' rejected: $rejectionReason (pattern: UPI_HANDLE)")
                    }
                }
            }
        }
        
        // No valid counterparty found
        Log.d(TAG, "COUNTERPARTY_EXTRACTION_FAILED: No valid person name extracted from SMS")
        return CounterpartyExtraction(
            found = false,
            directionAtExtraction = "DEBIT_OR_CREDIT_INDEPENDENT",
            suppressedByDirection = false  // Extraction was not suppressed; patterns simply didn't match
        )
    }

    /**
     * STEP 0: Sanitize merchant name to remove placeholders (CRED, bank names, gateways).
     * 
     * FROZEN: Does NOT change transactionType, category, confidence.
     * ONLY removes invalid defaults when no real merchant/person/UPI exists.
     * 
     * DEFERRAL GUARD: Does NOT sanitize if message appears to be a counter-party transaction
     * (transfer/person payment) but extraction is not yet complete. This prevents premature
     * removal of placeholder merchants before Step 1 (Resolution) can extract the real name.
     * 
     * Returns Triple<sanitizedMerchant, wasChanged, wasDeferred>
     */
    fun sanitizeMerchantName(smsBody: String, merchantName: String?, counterpartyFound: Boolean): Triple<String?, Boolean, Boolean> {
        if (merchantName == null) return Triple(null, false, false)
        
        val placeholders = setOf(
            "CRED",
            "BANK",
            // Bank names
            "ICICI", "HDFC", "SBI", "AXIS", "KOTAK", "IDBI", "UPSC", "HSBC", "STANDARD CHARTERED",
            "PUNJAB NATIONAL", "BANK OF INDIA", "CENTRAL BANK", "UNION BANK", "INDIAN BANK",
            // Payment gateways
            "UPI", "IMPS", "NEFT", "RTGS", "BBPS", "AEPS",
            // Wallet/payment apps
            "PAYTM", "GOOGLEPAY", "PHONEPE", "WHATSAPP"
        )
        
        val upperMerchant = merchantName.uppercase()
        val isPlaceholder = placeholders.any { upperMerchant.contains(it) }
        
        if (!isPlaceholder) {
            return Triple(merchantName, false, false)  // Not a placeholder, no sanitization needed
        }
        
        // DEFERRAL GUARD: If counterparty was already extracted, defer sanitization
        // (Step 1 Resolution will use the counterparty to override placeholder)
        if (counterpartyFound) {
            Log.d(TAG, "MERCHANT_SANITIZATION_DEFERRED: Placeholder '$merchantName' - counterparty already extracted, deferring to Step 1")
            return Triple(merchantName, false, true)  // Don't sanitize, mark as deferred
        }
        
        val upperBody = smsBody.uppercase()
        
        // This IS a placeholder. Check if better candidates exist in SMS
        // Check for explicit merchant token (known brands)
        val knownBrands = setOf(
            "SWIGGY", "ZOMATO", "AMAZON", "FLIPKART", "UBER", "OLA",
            "NETFLIX", "HOTSTAR", "SPOTIFY", "ADOBE", "MICROSOFT",
            "AIRTEL", "JIO", "VODAFONE", "BSNL", "NOTION",
            "AIRBNB", "BOOKING", "MAKEMYTRIP", "CLEARTRIP"
        )
        val hasBrand = knownBrands.any { upperBody.contains(it) }
        if (hasBrand) {
            return Triple(merchantName, false, false)  // Known brand exists, keep original
        }
        
        // Check for person name pattern: "credited from <NAME>", "<NAME> credited", "debited to <NAME>", etc.
        val personPatterns = listOf(
            // Pattern 1: "credited from/debited to <NAME>"
            Pattern.compile("(?:credited from|deposited from|transferred from|received from)\\s+([A-Za-z][A-Za-z\\s]{2,30}?)(?:\\s+on|\\.|$)", Pattern.CASE_INSENSITIVE),
            Pattern.compile("(?:debited to|paid to|transferred to|sent to)\\s+([A-Za-z][A-Za-z\\s]{2,30}?)(?:\\s+on|\\.|$)", Pattern.CASE_INSENSITIVE),
            // Pattern 2: "<NAME> credited/debited" (name comes before action)
            Pattern.compile("\\b([A-Za-z][A-Za-z\\s]{2,30}?)\\s+(?:credited|debited|transferred|paid|received)\\b", Pattern.CASE_INSENSITIVE),
            // Pattern 3: "from/to <NAME>" (generic transfer)
            Pattern.compile("(?:from|to)\\s+([A-Za-z][A-Za-z\\s]{2,30}?)(?:\\s+on|\\.|,|$)", Pattern.CASE_INSENSITIVE)
        )
        for (pattern in personPatterns) {
            val matcher = pattern.matcher(smsBody)
            if (matcher.find()) {
                return Triple(merchantName, false, false)  // Person name found, keep placeholder for Step 1 to override
            }
        }
        
        // Check for UPI ID
        if (smsBody.contains("@")) {
            val upiPattern = Pattern.compile("[a-zA-Z0-9._-]+@[a-zA-Z0-9]+")
            val matcher = upiPattern.matcher(smsBody)
            if (matcher.find()) {
                return Triple(merchantName, false, false)  // UPI ID found, keep placeholder for Step 1 to override
            }
        }
        
        // No valid merchant/person/UPI candidate found
        // SANITIZE: Remove placeholder merchant
        Log.d(TAG, "MERCHANT_SANITIZATION: Removing placeholder '$merchantName' (no candidate in message)")
        return Triple(null, true, false)
    }

    /**
     * STEP 1: Resolve merchant name to fix incorrect extractions.
     * FROZEN: Does NOT change transactionType, category, confidence, or decision flow.
     * 
     * ✅ EARLY PIPELINE INTEGRATION:
     * Uses counterpartyExtraction from STEP 0.5 (early extraction).
     * If a PERSON was already extracted early, uses it directly.
     * NO re-extraction happens here — trust the early result.
     * 
     * PRECEDENCE RULES (STRICT ORDER):
     * 1. If originalMerchant is a PLACEHOLDER + earlyCounterparty found → OVERRIDE with early result
     * 2. If originalMerchant is a PLACEHOLDER + upiId exists → OVERRIDE with UPI name
     * 3. Keep original if no valid override found
     * 4. Never override REAL merchants (Amazon, Swiggy, etc.)
     * 
     * Placeholder merchants (CRED, BANK, CARD, gateways) are NEVER kept when real candidates exist.
     */
    fun resolveMerchantName(smsBody: String, originalMerchant: String?, upiId: String?, earlyCounterparty: CounterpartyExtraction?): Pair<String?, String> {
        // Define what counts as a placeholder merchant (no business value)
        val placeholderMerchants = setOf(
            "CRED",
            "BANK",
            "CARD",
            // Bank names
            "ICICI", "HDFC", "SBI", "AXIS", "KOTAK", "IDBI", "UPSC", "HSBC", "STANDARD CHARTERED",
            "PUNJAB NATIONAL", "BANK OF INDIA", "CENTRAL BANK", "UNION BANK", "INDIAN BANK",
            // Payment gateways
            "UPI", "IMPS", "NEFT", "RTGS", "BBPS", "AEPS",
            // Wallet/payment apps
            "PAYTM", "GOOGLEPAY", "PHONEPE", "WHATSAPP",
            "RAZORPAY", "CASHFREE", "BILLDESK"
        )
        
        // Define real merchants that should never be overridden
        val realMerchants = setOf(
            "AMAZON", "FLIPKART", "SWIGGY", "UBER", "ZOMATO", "NETFLIX", "HOTSTAR",
            "AIRTEL", "JIO", "VODAFONE", "BSNL",
            "SPOTIFY", "ADOBE", "MICROSOFT",
            "AIRBNB", "BOOKING", "MAKEMYTRIP", "CLEARTRIP"
        )
        
        val upperBody = smsBody.uppercase()
        val upperOriginal = originalMerchant?.uppercase() ?: ""
        var resolvedMerchant = originalMerchant
        var resolutionRule = "NO_CHANGE"
        
        // PRECEDENCE CHECK 1: If original is a PLACEHOLDER, ALWAYS try to use EARLY counterparty
        val isPlaceholder = placeholderMerchants.any { upperOriginal.contains(it) }
        val isRealMerchant = realMerchants.any { upperOriginal.contains(it) }
        
        if (isPlaceholder && !isRealMerchant) {
            // This IS a placeholder. Use early counterparty extraction result if available.
            
            // Rule 1a: USE EARLY COUNTERPARTY if it was already extracted
            if (earlyCounterparty != null && earlyCounterparty.found && !earlyCounterparty.extractedName.isNullOrEmpty()) {
                resolvedMerchant = earlyCounterparty.extractedName
                resolutionRule = "EARLY_COUNTERPARTY_OVERRIDE"
                Log.d(TAG, "MERCHANT_RESOLUTION: Placeholder '$originalMerchant' overridden by EARLY counterparty -> '${earlyCounterparty.extractedName}' (rule: ${earlyCounterparty.extractionRule})")
                return Pair(resolvedMerchant, resolutionRule)
            }
            
            // Rule 1b: NO early counterparty. Fall back to UPI ID if it looks like a person name
            if (!upiId.isNullOrEmpty() && upiId.contains("@")) {
                val beforeAt = upiId.substringBefore("@")
                // Check if it looks like a human name (contains letters, not just numbers)
                if (beforeAt.length >= 3 && beforeAt.any { it.isLetter() }) {
                    // Try to extract human-readable name from UPI (remove dots, underscores, etc.)
                    val humanName = beforeAt
                        .replace(".", " ")
                        .replace("_", " ")
                        .replace("-", " ")
                        .trim()
                    if (humanName.length >= 3 && humanName.any { it.isLetter() }) {
                        resolvedMerchant = humanName
                        resolutionRule = "PLACEHOLDER_OVERRIDDEN_BY_UPI_NAME"
                        Log.d(TAG, "MERCHANT_RESOLUTION: Placeholder '$originalMerchant' overridden by UPI -> '$humanName'")
                        return Pair(resolvedMerchant, resolutionRule)
                    }
                }
                
                // FIX for Issue #3: UPI ID doesn't look like a human name (e.g., 9618809138@ybl)
                // Use the RAW UPI ID as merchant - this is still more informative than "CRED"
                // User can see their actual counterparty (VPA) in the transaction list
                resolvedMerchant = upiId
                resolutionRule = "PLACEHOLDER_OVERRIDDEN_BY_UPI_ID"
                Log.d(TAG, "MERCHANT_RESOLUTION: Placeholder '$originalMerchant' overridden by raw UPI ID -> '$upiId'")
                return Pair(resolvedMerchant, resolutionRule)
            }
            
            // Rule 1c: No override found. Keep placeholder but log it.
            resolutionRule = "PLACEHOLDER_RETAINED_NO_CANDIDATE"
            Log.d(TAG, "MERCHANT_RESOLUTION: Placeholder '$originalMerchant' retained (no early counterparty or UPI candidate found)")
            return Pair(resolvedMerchant, resolutionRule)
        }
        
        // PRECEDENCE CHECK 2: Original is NOT a placeholder (already a real merchant)
        // Only try extraction if no real merchant is currently set
        if (!isRealMerchant && isPlaceholder) {
            // Already handled above (isPlaceholder = true)
            return Pair(resolvedMerchant, resolutionRule)
        }
        
        // If original is a REAL merchant (Amazon, Swiggy, etc.), NEVER override it
        if (isRealMerchant) {
            resolutionRule = "RULE_3_BRAND_RETAINED"
            return Pair(resolvedMerchant, resolutionRule)
        }
        
        // Otherwise, no changes needed
        return Pair(resolvedMerchant, resolutionRule)
    }

    /**
     * Post-Fallback Reconciliation: Override placeholder merchants when fallback decision exists with valid counterparty
     * 
     * PRINCIPLE: Fallback answers "what type is this?" not "who was involved?"
     * If a fallback rule (LEVEL_6_EXPENSE_FALLBACK or LEVEL_5_INCOME_FALLBACK) was used,
     * and a valid counterparty was extracted, we can safely override the placeholder merchant.
     * 
     * This ensures that:
     * - Valid person names aren't trapped by fallback decisions
     * - Transaction type remains fixed (no re-classification)
     * - Only merchant/counterparty fields are reconciled
     * 
     * @param matchedRule The rule that made the decision (from decision tree)
     * @param currentMerchant The current merchant name (may still be placeholder)
     * @param counterpartyExtraction The early counterparty extraction result
     * @return Pair<newMerchant, logEntry> or Pair<currentMerchant, null> if no override
     */
    fun performPostFallbackReconciliation(
        matchedRule: String?,
        currentMerchant: String?,
        counterpartyExtraction: CounterpartyExtraction
    ): Pair<String?, Map<String, Any?>> {
        // Define fallback rules that allow soft merchant reconciliation
        val fallbackRules = setOf("LEVEL_5_INCOME_FALLBACK", "LEVEL_6_EXPENSE_FALLBACK")
        
        // Define placeholders that can be overridden after fallback
        val placeholderMerchants = setOf(
            "CRED", "BANK", "CARD",
            "ICICI", "HDFC", "SBI", "AXIS", "KOTAK", "IDBI", "UPSC", "HSBC", "STANDARD CHARTERED",
            "PUNJAB NATIONAL", "BANK OF INDIA", "CENTRAL BANK", "UNION BANK", "INDIAN BANK",
            "UPI", "IMPS", "NEFT", "RTGS", "BBPS", "AEPS",
            "PAYTM", "GOOGLEPAY", "PHONEPE", "WHATSAPP",
            "RAZORPAY", "CASHFREE", "BILLDESK"
        )
        
        // Condition 1: Was the decision made by a fallback rule?
        val isFallbackDecision = matchedRule != null && fallbackRules.contains(matchedRule)
        
        // Condition 2: Does a valid counterparty exist?
        val hasValidCounterparty = counterpartyExtraction.found && 
                                   !counterpartyExtraction.extractedName.isNullOrBlank()
        
        // Condition 3: Is the current merchant still a placeholder?
        val isPlaceholder = currentMerchant != null && 
                            placeholderMerchants.any { currentMerchant.uppercase().contains(it) }
        
        val logEntry = mutableMapOf<String, Any?>()
        
        // If all conditions met, perform override
        if (isFallbackDecision && hasValidCounterparty && isPlaceholder) {
            val newMerchant = counterpartyExtraction.extractedName
            logEntry["applied"] = true
            logEntry["previousMerchant"] = currentMerchant
            logEntry["newMerchant"] = newMerchant
            logEntry["reason"] = "Fallback is soft; valid counterparty exists - overriding placeholder with extracted person"
            logEntry["fallbackRule"] = matchedRule
            logEntry["extractionRule"] = counterpartyExtraction.extractionRule
            
            Log.d(TAG, "POST_FALLBACK_RECONCILIATION: Applied override: '$currentMerchant' → '$newMerchant' (fallback rule: $matchedRule, counterparty: ${counterpartyExtraction.extractionRule})")
            
            return Pair(newMerchant, logEntry)
        }
        
        // Not applied - log why
        val reason = when {
            !isFallbackDecision -> "Decision not from fallback rule (rule: $matchedRule)"
            !hasValidCounterparty -> "No valid counterparty extraction"
            !isPlaceholder -> "Merchant is not a placeholder (merchant: $currentMerchant)"
            else -> "Unknown"
        }
        
        logEntry["applied"] = false
        logEntry["reason"] = reason
        
        Log.d(TAG, "POST_FALLBACK_RECONCILIATION: Not applied - $reason")
        
        return Pair(currentMerchant, logEntry)
    }

    /**
     * 🔒 P2P OUTGOING TRANSFER FINANCIAL INVARIANT
     * 
     * CRITICAL: This invariant runs AFTER the decision tree completes.
     * It corrects fallback EXPENSE classifications to TRANSFER for outgoing P2P payments.
     * 
     * FINANCIAL PRINCIPLE:
     * - Expenses represent consumption (money spent on goods/services)
     * - Transfers represent movement (money moving to another person/account)
     * - Mixing them destroys financial truth
     * 
     * CONDITIONS (ALL must be true):
     * 1. transactionDirection == DEBIT
     * 2. counterpartyExtraction.found == true
     * 3. The counterparty is a PERSON (not a merchant)
     * 4. selfTransfer == false
     * 5. finalDecisionSource == FALLBACK (EXPENSE or similar)
     * 
     * RESULT:
     * - transactionType = TRANSFER
     * - transferDirection = OUT
     * - merchantName = counterpartyName
     * - confidence = MEDIUM
     * 
     * @param isDebit True if transaction is a debit (money going OUT)
     * @param isSelfTransfer True if already detected as self-transfer
     * @param matchedRule The rule that matched in decision tree
     * @param currentTransactionType The transaction type from decision tree
     * @param counterpartyExtraction The counterparty extraction result
     * @param currentMerchant The current merchant name
     * @return Pair<P2PTransferInvariantLog, Pair<TransactionType?, String?>> - log entry and (correctedType, correctedMerchant)
     */
    fun applyP2POutgoingTransferInvariant(
        isDebit: Boolean,
        isSelfTransfer: Boolean,
        matchedRule: String?,
        currentTransactionType: TransactionType,
        counterpartyExtraction: CounterpartyExtraction,
        currentMerchant: String?
    ): Pair<P2PTransferInvariantLog, Pair<TransactionType?, String?>> {
        
        // Define fallback rules that trigger this invariant
        val fallbackRules = setOf("EXPENSE", "EXPENSE_FALLBACK", "LEVEL_6_EXPENSE_FALLBACK", "INCOME_UNIDENTIFIED")
        
        // ===== INVARIANT CONDITIONS =====
        
        // Condition 1: Must be a DEBIT (money going OUT)
        if (!isDebit) {
            return Pair(
                P2PTransferInvariantLog(
                    applied = false,
                    reason = "Not a debit transaction (direction != OUT)"
                ),
                Pair(null, null)
            )
        }
        
        // Condition 2: Counterparty must be found
        if (!counterpartyExtraction.found || counterpartyExtraction.extractedName.isNullOrBlank()) {
            return Pair(
                P2PTransferInvariantLog(
                    applied = false,
                    reason = "No counterparty extracted (counterpartyExtraction.found == false)"
                ),
                Pair(null, null)
            )
        }
        
        // Condition 3: Must NOT be a self-transfer (already handled by LEVEL_4)
        if (isSelfTransfer) {
            return Pair(
                P2PTransferInvariantLog(
                    applied = false,
                    reason = "Already classified as self-transfer (isSelfTransfer == true)"
                ),
                Pair(null, null)
            )
        }
        
        // Condition 4: Must have come from FALLBACK rule (soft decision)
        val isFallbackDecision = fallbackRules.any { matchedRule?.uppercase()?.contains(it) == true }
        if (!isFallbackDecision) {
            return Pair(
                P2PTransferInvariantLog(
                    applied = false,
                    reason = "Not a fallback decision (matchedRule=$matchedRule is a hard rule)"
                ),
                Pair(null, null)
            )
        }
        
        // Condition 5: Only apply to EXPENSE type (don't override CC_SPEND, CC_PAYMENT, etc.)
        if (currentTransactionType != TransactionType.EXPENSE) {
            return Pair(
                P2PTransferInvariantLog(
                    applied = false,
                    reason = "Transaction type is $currentTransactionType, not EXPENSE - invariant only fixes EXPENSE fallback"
                ),
                Pair(null, null)
            )
        }
        
        // ===== ALL CONDITIONS MET: APPLY INVARIANT =====
        val counterpartyName = counterpartyExtraction.extractedName!!
        
        Log.d(TAG, """
            ╔═══════════════════════════════════════════════════════════
            ║ 🔒 P2P_OUTGOING_TRANSFER INVARIANT TRIGGERED
            ║─────────────────────────────────────────────────────────────
            ║ Counterparty: $counterpartyName
            ║ Original: EXPENSE (from $matchedRule)
            ║ Corrected: TRANSFER (direction=OUT)
            ║─────────────────────────────────────────────────────────────
            ║ REASON: Money moved to another person; not consumption
            ╚═══════════════════════════════════════════════════════════
        """.trimIndent())
        
        return Pair(
            P2PTransferInvariantLog(
                applied = true,
                invariantName = "P2P_OUTGOING_TRANSFER",
                originalTransactionType = "EXPENSE_FALLBACK",
                correctedTransactionType = "TRANSFER_OUT",
                transferDirection = "OUT",
                counterparty = counterpartyName,
                reason = "Money moved to another person; not consumption",
                confidence = "MEDIUM"
            ),
            Pair(TransactionType.TRANSFER, counterpartyName)
        )
    }


    /**
     * Extract merchant keyword from SMS body
     */
    fun extractMerchantKeyword(smsBody: String): String? {
        val upperBody = smsBody.uppercase()

        // Check user patterns first
        for ((keyword, _) in userDefinedPatterns) {
            if (upperBody.contains(keyword)) {
                return keyword
            }
        }

        // Check default patterns
        for ((keyword, _) in DEFAULT_MERCHANT_PATTERNS) {
            if (upperBody.contains(keyword)) {
                return keyword
            }
        }

        // Try to extract from SMS pattern
        val patterns = listOf(
            Pattern.compile("(?:AT|TO)\\s+([A-Z][A-Z\\s&]+?)(?:\\s+ON|\\s+FOR|\\s+VIA|\\.|$)"),
            Pattern.compile("TXN\\s+(?:AT|TO)\\s+([A-Z][A-Z\\s&]+?)(?:\\s+ON|\\.|$)")
        )

        for (pattern in patterns) {
            val matcher = pattern.matcher(upperBody)
            if (matcher.find()) {
                val extracted = matcher.group(1)?.trim()
                if (extracted != null && extracted.length > 3) {
                    return extracted
                }
            }
        }

        return null
    }

    /**
     * Find all transactions with matching UPI ID or NEFT reference and update them.
     * 
     * BUG FIX: Changed from merchant keyword matching to UPI/NEFT ID matching only.
     * Merchant keyword matching was too broad and caused incorrect categorization.
     * 
     * @param pattern The UPI ID or NEFT reference to match
     * @param patternType The type of pattern (UPI_ID or NEFT_REFERENCE)
     */
    suspend fun applyCategoryToSimilarTransactions(
        context: Context,
        pattern: String,
        categoryId: Long,
        categories: List<Category>,
        patternType: PatternType? = null
    ) = withContext(Dispatchers.IO) {
        val app = context.applicationContext as ExpenseTrackerApplication
        try {
            val start = 0L
            val end = Long.MAX_VALUE

            val allTransactions = app.repository.getTransactionsInPeriod(start, end).first()
            val transactionsToUpdate = mutableListOf<Transaction>()

            allTransactions.forEach { item ->
                val txn = item.transaction
                val content = txn.smsSnippet ?: txn.merchantName ?: return@forEach

                val shouldUpdate = when (patternType) {
                    PatternType.UPI_ID -> {
                        // Match by UPI ID only
                        val txnUpi = extractUpiId(content)
                        txnUpi != null && txnUpi.equals(pattern, ignoreCase = true)
                    }
                    PatternType.NEFT_REFERENCE -> {
                        // Match by NEFT reference only
                        val txnNeft = extractNeftReference(content)
                        txnNeft != null && txnNeft.equals(pattern, ignoreCase = true)
                    }
                    PatternType.MERCHANT_NAME -> {
                        // Match by exact merchant name (normalized)
                        val txnMerchant = normalizeForMatching(txn.merchantName ?: "")
                        val normalizedPattern = normalizeForMatching(pattern)
                        txnMerchant.isNotEmpty() && txnMerchant == normalizedPattern
                    }
                    PatternType.PAYEE_NAME -> {
                        // Match by payee name - similar to merchant name
                        val txnMerchant = normalizeForMatching(txn.merchantName ?: "")
                        val normalizedPattern = normalizeForMatching(pattern)
                        txnMerchant.isNotEmpty() && txnMerchant == normalizedPattern
                    }
                    PatternType.NEFT_BANK_CODE -> {
                        // Match by NEFT bank code prefix (e.g., DEUT for Deutsche Bank)
                        // This matches ALL transactions from the same bank/sender
                        val txnBankCode = extractNeftBankCode(content)
                        txnBankCode != null && txnBankCode.equals(pattern.take(4), ignoreCase = true)
                    }
                    null -> {
                        // Try to auto-detect pattern type
                        val txnUpi = extractUpiId(content)
                        val txnNeft = extractNeftReference(content)
                        
                        // Check if pattern looks like a UPI ID
                        if (pattern.contains("@")) {
                            txnUpi != null && txnUpi.equals(pattern, ignoreCase = true)
                        } else if (extractNeftReference(pattern) != null) {
                            txnNeft != null && txnNeft.equals(pattern, ignoreCase = true)
                        } else {
                            // Skip merchant keyword matching - too imprecise
                            false
                        }
                    }
                }

                if (shouldUpdate && txn.categoryId != categoryId) {
                    transactionsToUpdate.add(txn.copy(categoryId = categoryId))
                    Log.d(TAG, "Marked transaction ${txn.id} for category update (pattern: $pattern)")
                }
            }

            transactionsToUpdate.forEach { app.repository.updateTransaction(it) }
            Log.d(TAG, "Updated ${transactionsToUpdate.size} similar transactions for pattern: $pattern")
        } catch (e: Exception) {
            Log.e(TAG, "Error applying category to similar transactions", e)
        }
    }

    /**
     * Assign category to transaction and optionally apply to similar ones.
     * Similar transactions are matched by UPI ID or NEFT reference, not merchant name.
     */
    suspend fun assignCategoryToTransaction(
        context: Context,
        transactionId: Long,
        categoryId: Long,
        pattern: String? = null,
        patternType: PatternType? = null,
        applyToSimilar: Boolean = true
    ) = withContext(Dispatchers.IO) {
        val app = context.applicationContext as ExpenseTrackerApplication
        try {
            val categories = app.repository.allEnabledCategories.first()
            val category = categories.find { it.id == categoryId } ?: return@withContext

            val start = 0L
            val end = Long.MAX_VALUE
            val allTransactions = app.repository.getTransactionsInPeriod(start, end).first()

            val transaction = allTransactions.find { it.transaction.id == transactionId }?.transaction
            if (transaction != null) {
                app.repository.updateTransaction(transaction.copy(categoryId = categoryId))
                Log.d(TAG, "Transaction $transactionId assigned to category: ${category.name}")

                // Apply to similar transactions only if we have a valid UPI/NEFT pattern
                if (applyToSimilar) {
                    val content = transaction.smsSnippet ?: transaction.merchantName ?: ""
                    
                    // Use provided pattern/type or try to extract from transaction
                    val (matchPattern, matchType) = when {
                        pattern != null && patternType != null -> pattern to patternType
                        else -> {
                            // Extract UPI ID or NEFT bank code from the transaction
                            val upiId = extractUpiId(content)
                            val neftBankCode = extractNeftBankCode(content)  // Use bank code, not full ref
                            when {
                                upiId != null -> upiId to PatternType.UPI_ID
                                neftBankCode != null -> neftBankCode to PatternType.NEFT_BANK_CODE
                                else -> null to null
                            }
                        }
                    }
                    
                    // Only apply to similar if we have a valid UPI or NEFT pattern
                    if (matchPattern != null && matchType != null) {
                        Log.d(TAG, "Applying category to similar: pattern=$matchPattern, type=$matchType")
                        applyCategoryToSimilarTransactions(context, matchPattern, categoryId, categories, matchType)
                    } else {
                        Log.d(TAG, "No UPI/NEFT ID found, skipping similar transaction update")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error assigning category to transaction", e)
        }
    }

    private fun containsWholeWord(body: String, word: String): Boolean {
        return try {
            val pattern = Pattern.compile("\\b${Pattern.quote(word)}\\b", Pattern.CASE_INSENSITIVE)
            pattern.matcher(body).find()
        } catch (e: Exception) {
            false
        }
    }

    private fun getSalaryDetectionResult(body: String, isCredit: Boolean, userCompanyNames: Set<String> = emptySet()): Pair<Boolean, String> {
        if (!isCredit) return false to "Not a credit transaction"
        
        // Check for exclusion keywords first (refunds, cashbacks, etc.)
        val matchedExclusionKeyword = EXCLUSION_KEYWORDS.find { body.contains(it, ignoreCase = true) }
        if (matchedExclusionKeyword != null) return false to "Exclusion keyword found: $matchedExclusionKeyword"
        
        // EXPANSION 1: Check user-defined company names FIRST (highest priority)
        // These are explicitly set by user, so we trust them
        val matchedCompany = userCompanyNames.find { containsWholeWord(body, it) }
        if (matchedCompany != null) {
            return true to "User-defined company: $matchedCompany"
        }
        
        // EXPANSION 2: Check for salary-specific keywords (broader than NEFT-only)
        val salaryKeywords = listOf(
            "SALARY", "PAYROLL", "SAL CREDIT", "MONTHLY SALARY", "EMP SAL",
            "EPF", "PROVIDENT FUND", "PF CONTRIBUTION", "PF CREDIT"
        )
        val matchedSalaryKeyword = salaryKeywords.find { containsWholeWord(body, it) }
        
        if (matchedSalaryKeyword != null) {
            // If salary keyword found, accept any valid transfer type
            val isValidTransfer = body.contains("NEFT", ignoreCase = true) ||
                body.contains("IMPS", ignoreCase = true) ||
                body.contains("UPI", ignoreCase = true) ||
                body.uppercase().contains("CREDITED")
            
            if (isValidTransfer) {
                return true to "Salary keyword '$matchedSalaryKeyword' + valid transfer"
            }
        }
        
        // LEGACY: NEFT with salary keyword (original logic)
        val isNeft = body.contains("NEFT", ignoreCase = true)
        if (isNeft && matchedSalaryKeyword != null) {
            return true to "NEFT + $matchedSalaryKeyword keyword matched"
        }
        
        // Not detected as salary
        return false to "No salary indicators found"
    }

    private fun detectSelfTransfer(body: String): Boolean {
        val upper = body.uppercase()
        
        // Keyword-based detection (high confidence)
        val selfTransferKeywords = listOf("SELF", "OWN ACCOUNT", "TRANSFER BETWEEN YOUR ACCOUNTS")
        if (selfTransferKeywords.any { upper.contains(it) }) {
            return true
        }
        
        // Pattern-based detection for common self-transfer messages
        val selfTransferPatterns = listOf(
            // Self transfer via UPI: "Credited from <UPI_ID>@ybl" where UPI is same as account holder
            "CREDITED FROM.*@(YBL|OKI|OKSBI|AIRTEL|GOOGLEPLAY)".toRegex(RegexOption.IGNORE_CASE),
            // Bank transfers to own account
            "TRANSFER.*YOUR.*ACCOUNT".toRegex(RegexOption.IGNORE_CASE),
            "CREDIT.*OWN.*ACCOUNT".toRegex(RegexOption.IGNORE_CASE)
        )
        
        return selfTransferPatterns.any { it.containsMatchIn(body) }
    }

    /**
     * Result of CC payment detection with confidence score
     */
    data class CCPaymentDetectionResult(
        val isCCPayment: Boolean,
        val confidence: Double = 0.0,
        val priority: Int = 0,
        val reason: String = ""
    )

    /**
     * Robust credit card bill payment detection.
     * Uses priority-based rules with negative signal blocking.
     * 
     * FINANCIAL RULE: CC payments are LIABILITY_PAYMENT, not expenses.
     * 
     * @param smsBody The full SMS text
     * @param isDebit True if money is going OUT of bank account
     * @return Detection result with confidence
     */
    private fun detectCCPayment(smsBody: String, isDebit: Boolean): CCPaymentDetectionResult {
        val upper = smsBody.uppercase()

        // Step 1: MUST be a debit transaction (money leaving bank account)
        if (!isDebit) {
            return CCPaymentDetectionResult(false, reason = "Not a debit transaction")
        }

        // Step 2: Check negative signals FIRST - these block CC payment classification
        // This prevents card spends from being classified as payments
        val matchedNegative = CC_NEGATIVE_SIGNALS.find { upper.contains(it) }
        if (matchedNegative != null) {
            Log.d(TAG, "CC detection blocked by negative signal: $matchedNegative")
            return CCPaymentDetectionResult(false, reason = "Negative signal: $matchedNegative")
        }

        // Priority 1: Explicit CC payment keywords (99% confidence)
        val matchedExplicit = CC_EXPLICIT_KEYWORDS.find { upper.contains(it) }
        if (matchedExplicit != null) {
            Log.d(TAG, "CC payment detected (P1 explicit): $matchedExplicit")
            return CCPaymentDetectionResult(
                isCCPayment = true,
                confidence = 0.99,
                priority = 1,
                reason = "Explicit keyword: $matchedExplicit"
            )
        }

        // Priority 2: Issuer + Payment verb + Card context (85% confidence)
        val hasIssuer = CC_ISSUERS.any { upper.contains(it) }
        val hasPaymentVerb = CC_PAYMENT_VERBS.any { upper.contains(it) }
        val hasCardContext = CC_CARD_CONTEXT.any { upper.contains(it) }

        if (hasIssuer && hasPaymentVerb && hasCardContext) {
            Log.d(TAG, "CC payment detected (P2 issuer+verb+card)")
            return CCPaymentDetectionResult(
                isCCPayment = true,
                confidence = 0.85,
                priority = 2,
                reason = "Issuer + payment verb + card context"
            )
        }

        // Priority 3: CRED app detection (90% confidence)
        // CRED is ONLY used for CC payments, so if CRED + issuer, highly confident
        if (upper.contains("CRED")) {
            // If CRED + any issuer name, it's definitely a CC payment
            if (hasIssuer) {
                Log.d(TAG, "CC payment detected (P3 CRED + issuer)")
                return CCPaymentDetectionResult(
                    isCCPayment = true,
                    confidence = 0.95,
                    priority = 3,
                    reason = "CRED app + CC issuer"
                )
            }
            // CRED alone (without issuer) still suggests CC payment
            // but lower confidence to avoid false positives
            if (hasPaymentVerb) {
                Log.d(TAG, "CC payment detected (P3 CRED + payment verb)")
                return CCPaymentDetectionResult(
                    isCCPayment = true,
                    confidence = 0.85,
                    priority = 3,
                    reason = "CRED app + payment verb"
                )
            }
        }

        // Priority 4: Issuer + Payment verb (no explicit card context) (75% confidence)
        // Lower confidence as this could be regular bank transfers
        if (hasIssuer && hasPaymentVerb && (upper.contains("BILL") || upper.contains("DUE"))) {
            Log.d(TAG, "CC payment detected (P4 issuer+verb+bill/due)")
            return CCPaymentDetectionResult(
                isCCPayment = true,
                confidence = 0.75,
                priority = 4,
                reason = "Issuer + payment verb + bill context"
            )
        }

        // Not detected as CC payment
        return CCPaymentDetectionResult(false, reason = "No CC payment indicators")
    }

    /**
     * Detect PAYMENT CONFIRMATIONS (money paid TO a service/credit card).
     * These are NOT income and NOT cashback.
     * Examples: "Payment received on your credit card", "payment for your Airtel bill"
     * 
     * @param smsBody The full SMS text
     * @return True if this is a payment confirmation
     */
    private fun detectPaymentConfirmation(smsBody: String): Boolean {
        val upper = smsBody.uppercase()
        
        // Credit card payment received confirmations
        val ccPaymentPatterns = listOf(
            "PAYMENT RECEIVED ON",
            "PAYMENT.*RECEIVED.*CREDIT CARD",
            "RECEIVED.*CREDIT CARD",
            "CREDITED TO.*CREDIT CARD",
            "RECEIVED.*TOWARDS YOUR CREDIT CARD"
        )
        
        // Utility/service payment confirmations
        val utilityPatterns = listOf(
            "PAYMENT RECEIVED FOR YOUR",
            "WE HAVE RECEIVED.*PAYMENT",
            "PAYMENT CONFIRMATION FOR"
        )
        
        // Check if it matches CC payment patterns (case-insensitive contains)
        val matchesCCPayment = ccPaymentPatterns.any { pattern ->
            try {
                Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(upper).find()
            } catch (e: Exception) {
                upper.contains(pattern)
            }
        }
        
        val matchesUtility = utilityPatterns.any { pattern ->
            try {
                Pattern.compile(pattern, Pattern.CASE_INSENSITIVE).matcher(upper).find()
            } catch (e: Exception) {
                upper.contains(pattern)
            }
        }
        
        return matchesCCPayment || matchesUtility
    }

    /**
     * Detect CASHBACK or REWARDS credited to account.
     * These are positive adjustments, not income and definitely not expenses.
     * 
     * @param smsBody The full SMS text
     * @param isCredit True if money is coming IN
     * @return True if this is a cashback/reward
     */
    private fun detectCashback(smsBody: String, isCredit: Boolean): Boolean {
        if (!isCredit) return false  // Cashback is always credited
        
        val upper = smsBody.uppercase()
        val cashbackKeywords = listOf(
            "CASHBACK", "CASH BACK", "REWARDS CREDITED",
            "REWARD POINTS", "POINTS CREDITED",
            "LOYALTY BONUS", "REFERRAL BONUS",
            "SHOPPING CASHBACK"
        )
        
        return cashbackKeywords.any { upper.contains(it) }
    }

    /**
     * Detect INVESTMENT OUTFLOWS (money going to investments).
     * These are NOT expenses in the traditional sense.
     * 
     * @param smsBody The full SMS text
     * @param isDebit True if money is going OUT
     * @return True if this is an investment outflow
     */
    private fun detectInvestmentOutflow(smsBody: String, isDebit: Boolean): Boolean {
        if (!isDebit) return false  // Investment outflows are debits
        
        val upper = smsBody.uppercase()
        val investmentKeywords = listOf(
            "ZERODHA", "GROWW", "UPSTOX", "KITE", "COIN",
            "STOCK", "MUTUAL FUND", "INVESTMENT",
            "EQUITY", "TRADING", "BROKER"
        )
        
        return investmentKeywords.any { upper.contains(it) }
    }

    /**
     * Detect STANDING INSTRUCTION ALERTS (future debits, not yet executed).
     * These should be PENDING/IGNORED until the actual debit SMS arrives.
     * 
     * @param smsBody The full SMS text
     * @return True if this is a standing instruction alert
     */
    private fun detectStandingInstructionAlert(smsBody: String): Boolean {
        val upper = smsBody.uppercase()
        val pendingKeywords = listOf(
            "WILL BE DEBITED",
            "DUE BY",
            "STANDING INSTRUCTION",
            "RECURRING CHARGE",
            "SCHEDULED",
            "IS DUE",
            "AUTO-DEBIT",
            "FUTURE DEBIT"
        )
        
        return pendingKeywords.any { upper.contains(it) }
    }

    /**
     * Determine the transaction type based on robust detection logic.
     * 
     * CRITICAL FINANCIAL RULES:
     * - CC SPEND (purchase USING card) → EXPENSE, affects spending
     * - CC PAYMENT (paying card bill) → LIABILITY_PAYMENT, does NOT affect spending
     * - Cashback/Rewards → CASHBACK, positive adjustment
     * - Investment outflow → INVESTMENT_OUTFLOW
     * - Standing instruction alert → PENDING (ignore for now)
     * - Self-transfer → TRANSFER
     * - Salary → INCOME
     * 
     * Priority: CC payment > Cashback > Investment > Pending > CC spend > self-transfer > salary > income > expense
     * 
     * CRITICAL: This must run BEFORE category-based classification!
     */
    /**
     * Determine transaction type using the TransactionNatureResolver.
     * 
     * CRITICAL: This function MUST use the nature resolver to determine
     * what the transaction is fundamentally before applying any category logic.
     * 
     * The nature resolver enforces hard accounting invariants that CANNOT be violated.
     */
    private fun determineTransactionType(
        smsBody: String,
        category: Category,
        isSelfTransfer: Boolean,
        isSalaryCredit: Boolean,
        isCredit: Boolean
    ): TransactionType {
        val isDebit = !isCredit
        val invariantViolations = mutableListOf<String>()
        val forcedCorrections = mutableListOf<String>()
        
        // ===== STEP 1: EXPLICIT DIRECTION DETECTION & STORAGE =====
        // Explicitly derive and store direction (not UNKNOWN)
        val directionDetected: String = when {
            isDebit && !isCredit -> "DEBIT"
            isCredit && !isDebit -> "CREDIT"
            else -> "UNKNOWN"
        }
        
        Log.d(TAG, "═══════════════════════════════════════════════════════════")
        Log.d(TAG, "STEP_1_DIRECTION_GATE")
        Log.d(TAG, "  directionDetected=$directionDetected")
        Log.d(TAG, "  isDebit=$isDebit, isCredit=$isCredit")
        
        // ===== STEP 2: RESOLVE TRANSACTION NATURE (CRITICAL) =====
        // This must happen FIRST, before any category logic
        // NOTE: Already resolved in processSmsInternal before this function is called
        // Re-resolving here to ensure consistency
        val natureResolution = TransactionNatureResolver.resolveNature(
            smsBody = smsBody,
            isDebit = isDebit,
            isCredit = isCredit,
            detectedAccountType = detectAccountType(smsBody).name
        )
        
        Log.d(TAG, "NATURE_RESOLVED: ${natureResolution.nature} (rule: ${natureResolution.matchedRule}, confidence: ${(natureResolution.confidence * 100).toInt()}%)")
        Log.d(TAG, "RESOLVER_DIRECTION: ${natureResolution.detectedDirection}")
        
        // ===== STEP 3: MAP NATURE TO TRANSACTION TYPE =====
        // This is straightforward once nature is known
        val transactionType = when (natureResolution.nature) {
            TransactionNatureResolver.TransactionNature.PENDING -> TransactionType.PENDING
            
            TransactionNatureResolver.TransactionNature.CREDIT_CARD_PAYMENT -> TransactionType.LIABILITY_PAYMENT
            
            TransactionNatureResolver.TransactionNature.CREDIT_CARD_SPEND -> TransactionType.EXPENSE
            
            TransactionNatureResolver.TransactionNature.SELF_TRANSFER -> TransactionType.TRANSFER
            
            TransactionNatureResolver.TransactionNature.INCOME -> {
                // Special handling for cashback and interest
                if (smsBody.contains("CASHBACK", ignoreCase = true)) {
                    TransactionType.CASHBACK
                } else if (smsBody.contains("INTEREST", ignoreCase = true)) {
                    TransactionType.INCOME  // Interest is income
                } else {
                    TransactionType.INCOME
                }
            }
            
            TransactionNatureResolver.TransactionNature.EXPENSE -> {
                // Check if this is investment outflow
                if (detectInvestmentOutflow(smsBody, isDebit = isDebit)) {
                    TransactionType.INVESTMENT_OUTFLOW
                } else {
                    TransactionType.EXPENSE
                }
            }
        }
        
        // ===== STEP 4: HARD INVARIANT ENFORCEMENT (DEBIT ≠ INCOME) =====
        // INVARIANT: Debited transactions can NEVER be INCOME
        if (isDebit && transactionType == TransactionType.INCOME) {
            invariantViolations.add("DEBIT_CANNOT_BE_INCOME")
            forcedCorrections.add("INCOME → EXPENSE")
            Log.w(TAG, "⚠️ STEP_1_INVARIANT_VIOLATION: DEBIT_CANNOT_BE_INCOME")
            Log.w(TAG, "⚠️ FORCED_CORRECTION: INCOME → EXPENSE")
            return TransactionType.EXPENSE
        }
        
        // Log Step 1 result
        if (invariantViolations.isEmpty()) {
            Log.d(TAG, "✓ STEP_1_RESULT:")
            Log.d(TAG, "  directionDetected=$directionDetected")
            Log.d(TAG, "  invariantViolations=[]")
            Log.d(TAG, "  forcedCorrections=[]")
            Log.d(TAG, "  transactionType=$transactionType")
        } else {
            Log.w(TAG, "⚠️ STEP_1_RESULT:")
            Log.w(TAG, "  directionDetected=$directionDetected")
            Log.w(TAG, "  invariantViolations=${invariantViolations.joinToString(", ")}")
            Log.w(TAG, "  forcedCorrections=${forcedCorrections.joinToString(", ")}")
            Log.w(TAG, "  transactionType=$transactionType (FORCED)")
        }
        
        // INVARIANT 2: Credit card payments are NEVER income or expense
        if (smsBody.contains("credit card", ignoreCase = true) && 
            smsBody.contains("received", ignoreCase = true) &&
            transactionType != TransactionType.LIABILITY_PAYMENT &&
            transactionType != TransactionType.PENDING) {
            val violation = "INVARIANT_VIOLATION: CC payment classified as $transactionType. Forcing to LIABILITY_PAYMENT."
            invariantViolations.add(violation)
            Log.w(TAG, "⚠️ $violation")
            return TransactionType.LIABILITY_PAYMENT
        }
        
        // Log final result
        if (invariantViolations.isEmpty()) {
            Log.d(TAG, "✓ INVARIANT_CHECK_PASSED: No violations detected")
        } else {
            Log.w(TAG, "INVARIANT_VIOLATIONS: ${invariantViolations.joinToString("; ")}")
        }
        
        Log.d(TAG, "✓ FINAL_TRANSACTION_TYPE: $transactionType")
        return transactionType
    }

    /**
     * Detect account type from SMS body (CREDIT_CARD, BANK, etc.)
     */
    private fun detectAccountType(smsBody: String): AccountType {
        val upper = smsBody.uppercase()
        
        // Check for credit card indicators
        val ccSpendResult = detectCCSpend(smsBody)
        if (ccSpendResult.isCCSpend) {
            return AccountType.CREDIT_CARD
        }
        
        // Check for card network or card number
        if (CARD_NETWORKS.any { upper.contains(it) } || CARD_NUMBER_PATTERN.containsMatchIn(upper)) {
            return AccountType.CREDIT_CARD
        }
        
        // Check for bank account indicators
        if (upper.contains("A/C ") || upper.contains("ACCOUNT ") || upper.contains("SAVINGS")) {
            return AccountType.SAVINGS
        }
        
        if (upper.contains("CURRENT")) {
            return AccountType.CURRENT
        }
        
        // Check for wallet indicators
        if (upper.contains("WALLET") || upper.contains("PAYTM") || upper.contains("PHONEPE")) {
            return AccountType.WALLET
        }
        
        return AccountType.UNKNOWN
    }

    suspend fun scanInbox(context: Context) = withContext(Dispatchers.IO) {
        try {
            val uri = Uri.parse("content://sms/inbox")
            val projection = arrayOf("body", "date", "address")
            val cursor = context.contentResolver.query(uri, projection, null, null, "date DESC")

            val app = context.applicationContext as ExpenseTrackerApplication
            val categories = app.repository.allEnabledCategories.first()

            // Start batch logging session
            ClassificationDebugLogger.startBatchSession()

            cursor?.use {
                val bodyIdx = it.getColumnIndex("body")
                val dateIdx = it.getColumnIndex("date")
                val addrIdx = it.getColumnIndex("address")

                while (it.moveToNext()) {
                    val body = it.getString(bodyIdx)
                    val date = it.getLong(dateIdx)
                    val address = if (addrIdx != -1) it.getString(addrIdx) else null
                    processSmsInternal(app, categories, body, date, address)
                }
            }
            
            // ===== POST-PROCESSING: Pair Related Transactions =====
            // After all SMS are processed, find and pair:
            // 1. Self-transfers (debit + credit, same amount, ±30 min)
            // 2. CC payments (CC receipt + bank debit, same amount, ±24 hrs)
            pairRelatedTransactions(context)
            
            // End batch logging session and save file
            ClassificationDebugLogger.endBatchSession(context)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error scanning inbox", e)
        }
    }
    
    /**
     * ===== TRANSACTION PAIRING =====
     * Post-processing step to find and pair related transactions:
     * 1. Self-transfers: Debit + Credit with same amount within 30 minutes → Both TRANSFER
     * 2. CC payments: CC receipt + Bank debit with same amount within 24 hours → Both LIABILITY_PAYMENT
     */
    private suspend fun pairRelatedTransactions(context: Context) {
        val app = context.applicationContext as ExpenseTrackerApplication
        
        try {
            // Get transactions from last 30 days for pairing
            val thirtyDaysAgo = System.currentTimeMillis() - (30L * 24 * 60 * 60 * 1000)
            val allTransactions = app.repository.getTransactionsInPeriod(thirtyDaysAgo, Long.MAX_VALUE).first()
            
            val updates = mutableListOf<Transaction>()
            val pairedIds = mutableSetOf<Long>()  // Track already paired transactions
            
            // ===== SELF-TRANSFER PAIRING (CONSERVATIVE) =====
            // DISABLED: Aggressive pairing caused too many false positives.
            // 
            // Self-transfers are now detected via:
            // 1. TransactionNatureResolver LEVEL_4_SELF_TRANSFER (keywords like "own account")
            // 2. Manual user marking via "Mark as Self-Transfer" checkbox
            // 3. Post-processing pairing ONLY when explicit evidence exists
            //
            // The old logic of "same amount + 30 min = self-transfer" was wrong because:
            // - Salary credit + bill payment could have same amount by coincidence
            // - UPI P2P payments to others could match with unrelated credits
            //
            // To enable pairing, user should manually mark one side as self-transfer,
            // then the pairing will find the other side.
            
            val selfTransferWindow = 10 * 60 * 1000L  // 10 minutes - tighter window
            
            // Only look for pairs where ONE SIDE is already marked as self-transfer
            val markedSelfTransfers = allTransactions.filter { 
                it.transaction.isSelfTransfer && 
                it.transaction.id !in pairedIds
            }
            
            for (marked in markedSelfTransfers) {
                if (marked.transaction.id in pairedIds) continue
                
                val isDebit = marked.transaction.transactionType == TransactionType.TRANSFER
                val targetType = if (isDebit) TransactionType.INCOME else TransactionType.EXPENSE
                
                // Find matching opposite transaction (same amount, short time window, NOT already paired)
                val matchingOpposite = allTransactions.find { other ->
                    other.transaction.id !in pairedIds &&
                    other.transaction.id != marked.transaction.id &&
                    !other.transaction.isSelfTransfer &&  // Don't pair two already-marked
                    other.transaction.amountPaisa == marked.transaction.amountPaisa &&
                    kotlin.math.abs(other.transaction.timestamp - marked.transaction.timestamp) <= selfTransferWindow
                }
                
                if (matchingOpposite != null) {
                    Log.d(TAG, "SELF_TRANSFER_PAIRED: Found pair for already-marked ${marked.transaction.id} -> ${matchingOpposite.transaction.id}")
                    
                    updates.add(matchingOpposite.transaction.copy(
                        transactionType = TransactionType.TRANSFER,
                        isSelfTransfer = true
                    ))
                    
                    pairedIds.add(marked.transaction.id)
                    pairedIds.add(matchingOpposite.transaction.id)
                }
            }
            
            // ===== CC PAYMENT PAIRING =====
            // Find CC receipt + Bank debit with same amount within 24 hours
            val ccPaymentWindow = 24 * 60 * 60 * 1000L  // 24 hours in ms
            
            val ccReceipts = allTransactions.filter { 
                it.transaction.transactionType == TransactionType.LIABILITY_PAYMENT &&
                it.transaction.id !in pairedIds
            }
            
            // Look for bank debits that match CC receipts
            for (ccReceipt in ccReceipts) {
                // Find matching bank debit: same amount, within time window, still EXPENSE
                val matchingDebit = allTransactions.find { txn ->
                    txn.transaction.id !in pairedIds &&
                    txn.transaction.transactionType == TransactionType.EXPENSE &&
                    txn.transaction.amountPaisa == ccReceipt.transaction.amountPaisa &&
                    kotlin.math.abs(txn.transaction.timestamp - ccReceipt.transaction.timestamp) <= ccPaymentWindow
                }
                
                if (matchingDebit != null) {
                    Log.d(TAG, "CC_PAYMENT_PAIRED: CC Receipt ${ccReceipt.transaction.id} + Bank Debit ${matchingDebit.transaction.id}, Amount: ${ccReceipt.transaction.amountPaisa / 100.0}")
                    
                    // Mark the bank debit as LIABILITY_PAYMENT since it paid the CC
                    updates.add(matchingDebit.transaction.copy(
                        transactionType = TransactionType.LIABILITY_PAYMENT
                    ))
                    
                    pairedIds.add(matchingDebit.transaction.id)
                }
            }
            
            // Apply all updates
            for (update in updates) {
                app.repository.updateTransaction(update)
            }
            
            Log.d(TAG, "PAIRING_COMPLETE: ${updates.size} transactions updated (${pairedIds.size / 2} pairs)")
            
        } catch (e: Exception) {
            Log.e(TAG, "Error pairing related transactions", e)
        }
    }

    suspend fun processSms(context: Context, body: String, timestamp: Long, sender: String?) = withContext(Dispatchers.IO) {
        val app = context.applicationContext as ExpenseTrackerApplication
        try {
            val categories = app.repository.allEnabledCategories.first()
            processSmsInternal(app, categories, body, timestamp, sender)
        } catch (e: Exception) {
            Log.e(TAG, "Error processing SMS", e)
        }
    }

    private suspend fun processSmsInternal(app: ExpenseTrackerApplication, categories: List<Category>, body: String, timestamp: Long, sender: String?) {
        /**
         * ✅ MANDATORY PIPELINE ORDER (FROZEN):
         * 
         * 1. Raw SMS received + amount extraction
         * 2. Direction detection (DEBIT | CREDIT | UNKNOWN)
         * 3. EARLY counterparty extraction (STEP 0.5) ← CRITICAL: Must happen before sanitization
         *    - PERSON name patterns
         *    - UPI handle extraction  
         *    - NEFT/IMPS sender parsing
         * 4. Merchant sanitization (STEP 0) with early extraction guard
         *    - IF early counterparty found → SKIP sanitization (deferred to Step 1)
         *    - ELSE → remove placeholder merchants
         * 5. Merchant resolution (STEP 1) using early counterparty
         *    - IF placeholder + early counterparty found → USE early result
         *    - ELSE IF placeholder + UPI → USE UPI name
         *    - ELSE → keep original
         * 6. Decision tree execution
         * 7. Category refinement
         * 
         * KEY INVARIANT: Counterparty data extracted EARLY is available downstream.
         * No re-extraction happens. Extraction quality frozen at Step 0.5.
         */
        val amountPattern = Pattern.compile("(?i)(?:Rs\\.?|INR)\\s*([\\d,]+\\.?\\d*)")
        val matcher = amountPattern.matcher(body)

        if (matcher.find()) {
            val amountStr = matcher.group(1)?.replace(",", "")
            val amountDouble = amountStr?.toDoubleOrNull() ?: return
            
            // Convert to paisa (Long) - multiply by 100 and round
            val amountPaisa = (amountDouble * 100).toLong()
            
            // Validate amount
            if (amountPaisa <= 0 || amountPaisa > MAX_TRANSACTION_PAISA) {
                Log.w(TAG, "Invalid amount: $amountPaisa paisa, skipping")
                return
            }

            // ===== STEP 1: EXPLICIT DIRECTION DETECTION =====
            // Debit detection MUST block credit detection
            // A transaction saying "debited...credited to" is still a DEBIT, not a credit
            val isDebit = body.contains("debited", ignoreCase = true) ||
                    body.contains("spent", ignoreCase = true) ||
                    body.contains("paid", ignoreCase = true) ||
                    body.contains("sent", ignoreCase = true) ||           // HDFC: "Sent Rs."
                    body.contains("transferred", ignoreCase = true) ||    // Generic transfers
                    body.contains("withdrawn", ignoreCase = true) ||       // ATM withdrawals
                    body.contains("withdrawal", ignoreCase = true) ||      // Withdrawal transactions
                    body.contains("ATM") ||                                // ATM (case-sensitive)
                    body.contains("purchased", ignoreCase = true) ||       // Purchase transactions
                    body.contains("charged", ignoreCase = true) ||         // Charged to account
                    body.contains("deducted", ignoreCase = true) ||        // Amount deducted
                    body.contains("auto debit", ignoreCase = true)         // Auto-debit payments
                    
            val isCredit = !isDebit && (body.contains("credited", ignoreCase = true) ||
                    body.contains("received", ignoreCase = true) ||
                    body.contains("deposited", ignoreCase = true) ||
                    body.contains("loaded", ignoreCase = true) ||          // Wallet/prepaid loaded
                    body.contains("recharge", ignoreCase = true))          // Recharge credited

            // Determine and log direction explicitly
            val directionDetected = when {
                isDebit && !isCredit -> "DEBIT"
                isCredit && !isDebit -> "CREDIT"
                else -> "UNKNOWN"
            }
            Log.d(TAG, "STEP_1_DIRECTION_DETECTED: $directionDetected (isDebit=$isDebit, isCredit=$isCredit)")

            // Start debug log with actual direction
            val rawInput = RawInputCapture(
                fullMessageText = body,
                source = "SMS",
                receivedTimestamp = timestamp,
                sender = sender,
                amount = amountPaisa,
                direction = directionDetected, // Now properly set
                accountType = "UNKNOWN"
            )
            val logId = ClassificationDebugLogger.startLog(rawInput)

            if (!isCredit && !isDebit) {
                ClassificationDebugLogger.logRuleExecution(logId, ClassificationDebugLogger.createRuleExecution(
                    ruleId = "DIRECTION_CHECK",
                    ruleName = "Transaction Direction Check",
                    ruleType = "HEURISTIC",
                    input = "directionDetected=$directionDetected",
                    result = "FAILED",
                    reason = "No credit/debit keywords found"
                ))
                return
            }
            
            // ===== STEP 1.5: REVERSAL DETECTION =====
            // Detect reversed/refunded transactions - these should be flagged
            val isReversal = body.contains("reversed", ignoreCase = true) ||
                    body.contains("refunded", ignoreCase = true) ||
                    body.contains("unsuccessful", ignoreCase = true) ||
                    body.contains("failed and reversed", ignoreCase = true) ||
                    body.contains("transaction reversed", ignoreCase = true) ||
                    body.contains("payment reversed", ignoreCase = true) ||
                    body.contains("amount reversed", ignoreCase = true)
                    
            Log.d(TAG, "STEP_1.5_REVERSAL_CHECK: isReversal=$isReversal")
            
            // Log reversal detection
            if (isReversal) {
                ClassificationDebugLogger.logRuleExecution(logId, ClassificationDebugLogger.createRuleExecution(
                    ruleId = "REVERSAL_DETECTION",
                    ruleName = "Transaction Reversal Detection",
                    ruleType = "HEURISTIC",
                    input = body,
                    result = "MATCHED",
                    confidence = 1.0,
                    reason = "Transaction contains reversal/refund keywords"
                ))
                // Reversals are typically handled by TransactionNatureResolver
                // For now we just log them, actual handling will be added later
            }

            // Generate hash for deduplication
            val smsHash = generateSmsHash(body)
            
            // Check for duplicate
            if (app.repository.transactionExistsBySmsHash(smsHash)) {
                Log.d(TAG, "Duplicate SMS detected, skipping")
                ClassificationDebugLogger.logRuleExecution(logId, ClassificationDebugLogger.createRuleExecution(
                    ruleId = "DEDUPLICATION",
                    ruleName = "Duplicate Check",
                ruleType = "HEURISTIC",
                    input = smsHash,
                    result = "FAILED",
                    reason = "Duplicate SMS hash found"
                ))
                return
            }
            
            // Load user-defined salary company names for salary detection
            val userSalaryCompanyNames = app.preferencesManager.getSalaryCompanyNamesSync()

            val detectionResult: Pair<Boolean, String> = getSalaryDetectionResult(body, isCredit, userSalaryCompanyNames)
            val isSalaryCredit = detectionResult.first
            val detectionReason = detectionResult.second
            
            ClassificationDebugLogger.logRuleExecution(logId, ClassificationDebugLogger.createRuleExecution(
                ruleId = "SALARY_DETECTION",
                ruleName = "Salary Credit Detection",
                ruleType = "KEYWORD",
                input = body,
                result = if (isSalaryCredit) "MATCHED" else "FAILED",
                confidence = if (isSalaryCredit) 1.0 else 0.0,
                reason = detectionReason
            ))

            // Self-transfer detection happens via paired matching after all SMS are processed
            val isSelfTransfer = false
            
            // Extract merchant name for storage (privacy: don't store full SMS)
            val extractedMerchant = extractMerchantKeyword(body)
            val upiId = extractUpiId(body)
            
            // STEP 0.5: Extract counterparty (person/entity) FIRST
            val counterpartyExtraction = extractCounterparty(body)
            
            // STEP 0: Sanitize merchant name (remove placeholders when no candidate exists)
            // Guard: Skip sanitization if counterparty was already extracted
            val (sanitizedMerchant, sanitizationApplied, sanitizationDeferred) = sanitizeMerchantName(body, extractedMerchant, counterpartyExtraction.found)
            val sanitizationLog = when {
                sanitizationDeferred -> {
                    Log.d(TAG, "MERCHANT_SANITIZATION_DEFERRED: '$extractedMerchant' (counterparty found: ${counterpartyExtraction.extractedName})")
                    MerchantSanitization(
                        applied = false,
                        deferred = true,
                        originalMerchant = extractedMerchant,
                        reason = "Counterparty extraction found '${counterpartyExtraction.extractedName}' - deferring to Step 1 (Resolution)"
                    )
                }
                sanitizationApplied -> {
                    Log.d(TAG, "MERCHANT_SANITIZATION: Removed placeholder '$extractedMerchant' (no candidate in message)")
                    MerchantSanitization(
                        applied = true,
                        deferred = false,
                        originalMerchant = extractedMerchant,
                        sanitizedTo = null,
                        reason = "Placeholder merchant with no candidate in message"
                    )
                }
                else -> {
                    MerchantSanitization(
                        applied = false,
                        deferred = false
                    )
                }
            }
            
            // STEP 1: Resolve merchant name to fix incorrect extractions (CRED, BANK NAME, etc.)
            // Pass early counterparty result so resolution uses it, not re-extracts
            val (resolvedMerchant, merchantResolutionRule) = resolveMerchantName(body, sanitizedMerchant, upiId, counterpartyExtraction)
            val merchantName = resolvedMerchant
            
            // Log merchant resolution for audit trail
            if ((sanitizedMerchant ?: extractedMerchant) != resolvedMerchant) {
                Log.d(TAG, "MERCHANT_RESOLUTION: ${sanitizedMerchant ?: extractedMerchant} -> $resolvedMerchant (rule: $merchantResolutionRule)")
            }
            
            // Log early extraction + resolution interaction for debugging
            if (counterpartyExtraction.found) {
                Log.d(TAG, "PIPELINE_TRACE: Early counterparty extraction used in resolution: extractedName='${counterpartyExtraction.extractedName}' (rule: ${counterpartyExtraction.extractionRule}) -> final merchant='$resolvedMerchant' (resolutionRule: $merchantResolutionRule)")
            }
            val extractedSnippet = extractSmsSnippet(body)
            
            // Log parsed fields with counterparty, sanitization, and resolution details
            ClassificationDebugLogger.logParsedFields(logId, ParsedFields(
                merchantName = merchantName,
                upiId = extractUpiId(body),
                neftReference = extractNeftReference(body),
                detectedKeywords = emptyList(), // Could be populated more thoroughly
                accountTypeDetected = detectAccountType(body).name,
                senderInferred = sender,
                receiverInferred = null,
                extractedSnippet = extractedSnippet,
                counterpartyExtraction = counterpartyExtraction,
                merchantSanitization = sanitizationLog,
                merchantResolution = MerchantResolution(
                    originalMerchant = sanitizedMerchant ?: extractedMerchant,
                    resolvedMerchant = resolvedMerchant,
                    resolutionRule = merchantResolutionRule
                )
            ))

            var category: Category? = null

            if (isSalaryCredit) {
                category = categories.find { it.name.equals("Salary", ignoreCase = true) }
            } else if (isCredit) {
                category = when {
                    body.contains("REFUND", ignoreCase = true) -> categories.find { it.name.equals("Refund", ignoreCase = true) }
                    body.contains("CASHBACK", ignoreCase = true) -> categories.find { it.name.equals("Cashback", ignoreCase = true) }
                    body.contains("INTEREST", ignoreCase = true) -> categories.find { it.name.equals("Interest", ignoreCase = true) }
                    else -> categories.find { it.name.equals("Other Income", ignoreCase = true) }
                }
            } else if (isDebit) {
                category = getDebitCategory(body, categories)
            }

            // Fallback to Unknown category if no specific category found
            if (category == null) {
                category = if (isCredit) {
                    categories.find { it.name.equals("Unknown Income", ignoreCase = true) }
                        ?: categories.find { it.type == CategoryType.INCOME }
                } else {
                    categories.find { it.name.equals("Unknown Expense", ignoreCase = true) }
                        ?: categories.find { it.type == CategoryType.VARIABLE_EXPENSE }
                }
            }

            category?.let {
                // Resolve nature and log decision tree
                val natureResolution = TransactionNatureResolver.resolveNature(
                    smsBody = body,
                    isDebit = isDebit,
                    isCredit = isCredit,
                    detectedAccountType = detectAccountType(body).name
                )
                
                // Log decision tree trace to debug log
                for (traceItem in natureResolution.ruleTrace) {
                    ClassificationDebugLogger.logRuleExecution(logId, ClassificationDebugLogger.createRuleExecution(
                        ruleId = "DECISION_TREE",
                        ruleName = traceItem.split(":")[0].trim(), // Extract level name
                        ruleType = "DECISION_TREE",
                        input = body,
                        result = when {
                            traceItem.contains("MATCHED") -> "MATCHED"
                            traceItem.contains("SKIPPED") -> "SKIPPED"
                            else -> "FAILED"
                        },
                        reason = traceItem,
                        confidence = if (traceItem.contains("MATCHED")) 1.0 else 0.0
                    ))
                }
                
                val transactionType = determineTransactionType(body, it, isSelfTransfer, isSalaryCredit, isCredit)
                
                // ===== POST-FALLBACK RECONCILIATION =====
                // CRITICAL: After decision tree completes, if the decision came from a fallback rule
                // (LEVEL_5_INCOME_FALLBACK or LEVEL_6_EXPENSE_FALLBACK), and a valid counterparty
                // was extracted, we can safely override the placeholder merchant without changing transactionType.
                // 
                // This breaks the "fallback locking" problem where a soft decision (type classification)
                // would lock the merchant field permanently, even when better evidence exists.
                var finalMerchantName = merchantName
                val (reconciliedMerchant, postFallbackLog) = performPostFallbackReconciliation(
                    matchedRule = natureResolution.matchedRule,
                    currentMerchant = merchantName,
                    counterpartyExtraction = counterpartyExtraction
                )
                if (reconciliedMerchant != null && reconciliedMerchant != merchantName) {
                    finalMerchantName = reconciliedMerchant
                    Log.d(TAG, "FINAL_MERCHANT_AFTER_FALLBACK_RECONCILIATION: $finalMerchantName")
                }
                ClassificationDebugLogger.logPostFallbackReconciliation(logId, postFallbackLog)
                
                // ===== 🔒 P2P OUTGOING TRANSFER INVARIANT (POST-DECISION-TREE) =====
                // CRITICAL: This invariant runs AFTER the decision tree.
                // It corrects fallback EXPENSE classifications to TRANSFER for outgoing P2P payments.
                // 
                // FINANCIAL PRINCIPLE:
                // - Expenses represent consumption
                // - P2P payments represent movement of money
                // - Treating transfers as expenses breaks budgeting and spending analytics
                //
                // CONDITIONS: isDebit AND counterparty.found AND !selfTransfer AND fallbackDecision AND type==EXPENSE
                var finalTransactionType = transactionType
                var finalCategory = it
                val (p2pInvariantLog, p2pCorrection) = applyP2POutgoingTransferInvariant(
                    isDebit = isDebit,
                    isSelfTransfer = isSelfTransfer,
                    matchedRule = natureResolution.matchedRule,
                    currentTransactionType = transactionType,
                    counterpartyExtraction = counterpartyExtraction,
                    currentMerchant = finalMerchantName
                )
                
                // Log the invariant (MANDATORY - silent corrections are forbidden)
                ClassificationDebugLogger.logP2PTransferInvariant(logId, p2pInvariantLog)
                
                // Apply corrections if invariant was triggered
                if (p2pInvariantLog.applied && p2pCorrection.first != null) {
                    finalTransactionType = p2pCorrection.first!!
                    
                    // Update merchant to counterparty name
                    if (p2pCorrection.second != null) {
                        finalMerchantName = p2pCorrection.second
                    }
                    
                    // Try to find P2P Transfer category, fallback to "Transfers" or keep current
                    val p2pCategory = categories.find { cat -> 
                        cat.name.equals("P2P Transfer", ignoreCase = true) ||
                        cat.name.equals("Transfers", ignoreCase = true) ||
                        cat.name.equals("Transfer", ignoreCase = true)
                    }
                    if (p2pCategory != null) {
                        finalCategory = p2pCategory
                        Log.d(TAG, "P2P_INVARIANT: Category updated to '${p2pCategory.name}'")
                    } else {
                        Log.d(TAG, "P2P_INVARIANT: No P2P category found, keeping '${finalCategory.name}'")
                    }
                    
                    Log.d(TAG, "P2P_INVARIANT: Final transactionType=$finalTransactionType, merchant=$finalMerchantName, category=${finalCategory.name}")
                }
                
                // ===== SKIP PENDING AND IGNORE TRANSACTIONS =====
                if (finalTransactionType == TransactionType.PENDING || finalTransactionType == TransactionType.IGNORE) {
                    Log.d(TAG, "Transaction skipped (nature: $finalTransactionType)")
                    ClassificationDebugLogger.finalizeLog(logId, FinalDecision(
                        transactionType = finalTransactionType.name,
                        categoryId = finalCategory.id,
                        categoryName = finalCategory.name,
                        confidence = "INFO",
                        finalConfidence = 1.0,
                        requiresUserConfirmation = false,
                        reasoning = "Transaction skipped: $finalTransactionType"
                    ))
                    ClassificationDebugLogger.persistLog(app, logId)
                    return@let
                }
                
                // Extract cleaned snippet for display (privacy-safe context)
                val smsSnippet = extractSmsSnippet(body)
                
                val transaction = Transaction(
                    amountPaisa = amountPaisa,
                    categoryId = finalCategory.id,
                    timestamp = timestamp,  // Already in epoch millis
                    note = if (isCredit) "Salary Detection: $detectionReason" else null,
                    source = TransactionSource.SMS,
                    isSalaryCredit = isSalaryCredit,
                    isSelfTransfer = isSelfTransfer || (p2pInvariantLog.applied),  // Mark P2P as transfer-like
                    smsHash = smsHash,
                    merchantName = finalMerchantName,
                    smsSnippet = smsSnippet,
                    transactionType = finalTransactionType
                )
                app.repository.insertTransaction(transaction)
                Log.d(TAG, "Transaction inserted: ${finalCategory.name} - ₹${amountPaisa / 100.0}")
                
                // Finalize debug log with proper confidence and user confirmation flag
                val requiresConfirmation = (finalTransactionType == TransactionType.INCOME && 
                        natureResolution.matchedIncomeRule == null)  // Unidentified credit
                val confidenceLevel = if (requiresConfirmation) "LOW" else "HIGH"
                val finalConfidence = if (requiresConfirmation) natureResolution.incomeConfidence else 1.0
                
                ClassificationDebugLogger.finalizeLog(logId, FinalDecision(
                    transactionType = finalTransactionType.name,
                    categoryId = finalCategory.id,
                    categoryName = finalCategory.name,
                    confidence = if (p2pInvariantLog.applied) "MEDIUM" else confidenceLevel,
                    finalConfidence = if (p2pInvariantLog.applied) 0.7 else finalConfidence,
                    requiresUserConfirmation = requiresConfirmation,
                    reasoning = when {
                        p2pInvariantLog.applied -> "P2P_OUTGOING_TRANSFER invariant applied: ${p2pInvariantLog.originalTransactionType} → ${p2pInvariantLog.correctedTransactionType}, counterparty: ${p2pInvariantLog.counterparty}"
                        requiresConfirmation -> "Unidentified credit (direction=CREDIT, no income rule matched); requires user confirmation"
                        else -> "Category identified: ${finalCategory.name}, Type: $finalTransactionType (matched rule: ${natureResolution.matchedIncomeRule ?: natureResolution.matchedRule})"
                    }
                ))
                ClassificationDebugLogger.persistLog(app, logId)
            }
        }
    }

    suspend fun reclassifyTransactions(context: Context) = withContext(Dispatchers.IO) {
        val app = context.applicationContext as ExpenseTrackerApplication
        try {
            val start = 0L
            val end = Long.MAX_VALUE
            
            // Start batch logging
            ClassificationDebugLogger.startBatchSession()

            val transactionsWithCategory = app.repository.getTransactionsInPeriod(start, end).first()
            val categories = app.repository.allEnabledCategories.first()
            val updates = mutableMapOf<Long, Transaction>()

            transactionsWithCategory.forEach { item ->
                val txn = item.transaction
                
                // Start debug log for reclassification
                val rawInput = RawInputCapture(
                    fullMessageText = txn.smsSnippet ?: txn.merchantName ?: "Unknown Content",
                    source = "RECLASSIFICATION",
                    receivedTimestamp = txn.timestamp,
                    sender = "Unknown",
                    amount = txn.amountPaisa,
                    direction = if (txn.transactionType == TransactionType.INCOME) "CREDIT" else "DEBIT",
                    accountType = txn.accountType.name
                )
                val logId = ClassificationDebugLogger.startLog(rawInput)
                
                // Use smsSnippet as primary content source for classification
                val content = txn.smsSnippet ?: txn.merchantName ?: return@forEach
                val originalMerchant = txn.merchantName ?: ""
                
                // NOTE: Sanitization (Step 0) is NOT applied here because:
                // - Reclassification uses truncated smsSnippet (120 chars), not full SMS
                // - Pattern matching for person names would fail on truncated text
                // - Step 0 (Sanitization) is for fresh SMS only
                // - We only do Step 1 (Resolution) on reclassified transactions
                
                // STEP 0.5: Extract counterparty from content for resolution
                val earlyCounterparty = extractCounterparty(content)
                
                // STEP 1: Resolve merchant name to fix incorrect extractions (CRED, BANK NAME, etc.)
                // Pass early counterparty so resolution uses it, not re-extracts
                val (resolvedMerchant, merchantResolutionRule) = resolveMerchantName(content, originalMerchant, extractUpiId(content), earlyCounterparty)
                val merchant = resolvedMerchant ?: originalMerchant
                
                // Log merchant resolution if changed
                if (originalMerchant != resolvedMerchant) {
                    Log.d(TAG, "RECLASSIFICATION_MERCHANT_RESOLUTION: $originalMerchant -> $resolvedMerchant (rule: $merchantResolutionRule)")
                }
                
                // Log parsed fields with resolution details only (no sanitization in reclassification)
                ClassificationDebugLogger.logParsedFields(logId, ParsedFields(
                    merchantName = merchant,
                    upiId = extractUpiId(content),
                    neftReference = extractNeftReference(content),
                    detectedKeywords = emptyList(),
                    accountTypeDetected = detectAccountType(content).name,
                    senderInferred = null,
                    receiverInferred = null,
                    extractedSnippet = content,
                    merchantSanitization = MerchantSanitization(applied = false),
                    merchantResolution = MerchantResolution(
                        originalMerchant = originalMerchant,
                        resolvedMerchant = resolvedMerchant,
                        resolutionRule = merchantResolutionRule
                    )
                ))

                // BUG FIX: Re-detect credit/debit from SMS content instead of using existing transactionType
                val isCredit = content.contains("credited", ignoreCase = true) ||
                        content.contains("received", ignoreCase = true) ||
                        content.contains("deposited", ignoreCase = true)
                val isDebit = !isCredit && (content.contains("debited", ignoreCase = true) ||
                        content.contains("spent", ignoreCase = true) ||
                        content.contains("paid", ignoreCase = true))
                
                ClassificationDebugLogger.logRuleExecution(logId, ClassificationDebugLogger.createRuleExecution(
                    ruleId = "RECLASS_DIRECTION_CHECK",
                    ruleName = "Reclassification Direction Check",
                    ruleType = "HEURISTIC",
                    input = "isCredit=$isCredit, isDebit=$isDebit",
                    result = if (isCredit || isDebit) "MATCHED" else "SKIPPED",
                    reason = "Content-based direction check"
                ))

                // If we can't determine credit/debit from content, fall back to existing type
                val finalIsCredit = if (isCredit || isDebit) isCredit else txn.transactionType == TransactionType.INCOME
                
                // Load user-defined salary company names
                val userSalaryCompanyNames = app.preferencesManager.getSalaryCompanyNamesSync()
                
                val detectionResult: Pair<Boolean, String> = getSalaryDetectionResult(content, finalIsCredit, userSalaryCompanyNames)
                val isSalaryCredit = detectionResult.first
                val detectionReason = detectionResult.second
                val isAutoSelfTransfer = detectSelfTransfer(content)

                var newCategoryId = txn.categoryId

                if (isSalaryCredit) {
                    val salaryCategory = categories.find { it.name.equals("Salary", ignoreCase = true) }
                    if (salaryCategory != null) {
                        newCategoryId = salaryCategory.id
                    }
                } else if (finalIsCredit) {
                    // Use content for keyword detection
                    val creditCategory = when {
                        content.contains("REFUND", ignoreCase = true) -> categories.find { it.name.equals("Refund", ignoreCase = true) }
                        content.contains("CASHBACK", ignoreCase = true) -> categories.find { it.name.equals("Cashback", ignoreCase = true) }
                        content.contains("INTEREST", ignoreCase = true) -> categories.find { it.name.equals("Interest", ignoreCase = true) }
                        content.contains("DIVIDEND", ignoreCase = true) -> categories.find { it.name.equals("Investments / Dividends", ignoreCase = true) }
                        content.contains("BONUS", ignoreCase = true) -> categories.find { it.name.equals("Bonus", ignoreCase = true) }
                        else -> categories.find { it.name.equals("Other Income", ignoreCase = true) }
                    }
                    if (creditCategory != null) {
                        newCategoryId = creditCategory.id
                    }
                } else {
                    // For debit transactions, check both content and merchant for category detection
                    val debitCategory = getDebitCategoryFromMerchant(merchant.ifEmpty { content }, categories)
                    if (debitCategory != null) {
                        newCategoryId = debitCategory.id
                        Log.d(TAG, "Reclassified transaction to: ${debitCategory.name}")
                    }
                }

                val newCategory = categories.find { it.id == newCategoryId }
                val newTransactionType = if (newCategory != null) {
                    determineTransactionType(content, newCategory, isAutoSelfTransfer, isSalaryCredit, finalIsCredit)
                } else {
                    txn.transactionType
                }

                updates[txn.id] = txn.copy(
                    isSalaryCredit = isSalaryCredit,
                    categoryId = newCategoryId,
                    isSelfTransfer = isAutoSelfTransfer,
                    transactionType = newTransactionType,
                    note = if (finalIsCredit) "Salary Detection: $detectionReason" else txn.note
                )
                
                // Finalize and persist log
                ClassificationDebugLogger.finalizeLog(logId, FinalDecision(
                    transactionType = newTransactionType.name,
                    categoryId = newCategoryId,
                    categoryName = newCategory?.name ?: "Unknown",
                    confidence = "HIGH", // Simplified for now
                    finalConfidence = 1.0, 
                    requiresUserConfirmation = false,
                    reasoning = "Category identified: ${newCategory?.name}, Type: $newTransactionType"
                ))
                ClassificationDebugLogger.persistLog(app, logId)
            }

            // BUG FIX: Detect self-transfers using UPDATED transaction types
            // Previously was using original transaction types which caused mismatches
            val updatedList = transactionsWithCategory.map { item -> 
                updates[item.transaction.id] ?: item.transaction 
            }.sortedBy { it.timestamp }
            
            for (i in 0 until updatedList.size - 1) {
                val t1 = updatedList[i]
                for (j in i + 1 until updatedList.size) {
                    val t2 = updatedList[j]
                    val durationMinutes = (t2.timestamp - t1.timestamp) / 60000
                    if (durationMinutes > 2) break
                    if (t1.amountPaisa == t2.amountPaisa && t1.id != t2.id) {
                        // Check transaction types from the UPDATED transactions
                        val t1Updated = updates[t1.id] ?: t1
                        val t2Updated = updates[t2.id] ?: t2
                        val isT1Income = t1Updated.transactionType == TransactionType.INCOME
                        val isT2Income = t2Updated.transactionType == TransactionType.INCOME
                        if (isT1Income != isT2Income) {
                            updates[t1.id] = t1Updated.copy(isSelfTransfer = true, transactionType = TransactionType.TRANSFER)
                            updates[t2.id] = t2Updated.copy(isSelfTransfer = true, transactionType = TransactionType.TRANSFER)
                            Log.d(TAG, "Detected self-transfer pair: ${t1.id} <-> ${t2.id}, amount: ₹${t1.amountPaisa/100}")
                        }
                    }
                }
            }

            updates.values.forEach { app.repository.updateTransaction(it) }
            Log.d(TAG, "Reclassification completed for ${updates.size} transactions")
            
            // End batch logging
            ClassificationDebugLogger.endBatchSession(context)
            
        } catch (e: Exception) {
            Log.e(TAG, "Error reclassifying transactions", e)
        }
    }


    private fun getDebitCategoryFromMerchant(merchant: String, categories: List<Category>): Category? {
        val upperMerchant = merchant.uppercase()

        for ((keyword, categoryName) in userDefinedPatterns) {
            if (upperMerchant.contains(keyword)) {
                return categories.find { it.name.equals(categoryName, ignoreCase = true) }
            }
        }

        for ((keyword, categoryName) in DEFAULT_MERCHANT_PATTERNS) {
            if (upperMerchant.contains(keyword)) {
                return categories.find { it.name.equals(categoryName, ignoreCase = true) }
            }
        }

        return null
    }
}