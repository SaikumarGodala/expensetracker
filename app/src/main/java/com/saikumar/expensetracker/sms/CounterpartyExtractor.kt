package com.saikumar.expensetracker.sms

import com.saikumar.expensetracker.data.entity.TransactionType
import java.util.regex.Pattern

/**
 * Robust Counterparty Extractor - Template Based
 *
 * This extractor uses a template-based approach rather than generic regex patterns.
 * Each bank SMS format is matched against a known template, and the counterparty is
 * extracted from the correct position in the template.
 */
object CounterpartyExtractor {

    data class Counterparty(
        val name: String?,
        val upiId: String?,
        val type: CounterpartyType,
        val trace: List<String> = emptyList(),
        val confidence: Float = 1.0f
    )

    enum class CounterpartyType {
        MERCHANT, PERSON, BANK_ACCOUNT, UNKNOWN
    }
    
    // Cashback/Reward VPA patterns - these are MERCHANT/SYSTEM, not PERSON
    private val CASHBACK_VPA_PATTERNS = listOf(
        "cashback", "reward", "promo", "refund", "bhimcashback",
        "googlepay", "gpay", "phoneperefund", 
        // Merchant/Gateway prefixes that indicate NOT a person
        "razorpay", "payu", "cashfree", "billdesk", "ccavenue", "paytmqr",
        "swiggy", "zomato", "uber", "ola", "licious", "instamart", "blinkit", "zepto"
    )

    // --- OPTIMIZED COMPILED REGEXES ---
    private val REGEX_VPA_TRAILING_DIGITS = Regex("\\d+$")
    private val REGEX_VPA_SEPARATORS = Regex("[._-]")
    private val REGEX_SPACES = Regex("\\s{2,}")
    private val REGEX_EMPLOYER_NOISE = Regex("(?i)(PRIVATE\\s+LIMITED|PVT\\s+LTD|LIMITED|CORPORATION|CLEARING|INDIA)")
    private val REGEX_PREPOSITION_PREFIX = Regex("(?i)^(At|To|From)\\s+")
    private val REGEX_TRAILING_DOTS_DIGITS_CHECK = Regex(".*[.\\d]+$")
    private val REGEX_TRAILING_DOTS_DIGITS = Regex("[.\\d]+$")
    private val REGEX_ALPHA_NUM_DIGITS_CHECK = Regex("^[a-zA-Z]+\\d+$")
    private val REGEX_NEWLINE_REST = Regex("[\\r\\n]+.*")
    private val REGEX_TRAILING_LOCATION_NOISE = Regex("(?i)^(Gur|new|On)\\s.*")
    private val REGEX_DIGITS_ONLY_SHORT = Regex("^\\d{2,4}$")

    // --- TEMPLATE PATTERNS ---
    
    // HDFC "Sent Rs.XXX To <NAME>" pattern (UPI P2P)
    private val HDFC_SENT_TO_PATTERN = Pattern.compile(
        "Sent\\s+Rs\\.?[\\d,\\.]+\\s+(?:From\\s+HDFC[^\\n]*\\n)?To\\s+([A-Z][A-Za-z\\s]{2,50})\\s*\\nOn",
        Pattern.CASE_INSENSITIVE
    )
    
    // HDFC "Spent Rs.XXX At <MERCHANT>" pattern (Credit Card POS)
    private val HDFC_SPENT_AT_PATTERN = Pattern.compile(
        "Spent\\s+Rs\\.?[\\d,\\.]+\\s+On\\s+HDFC[^\\n]*At\\s+([A-Z][A-Za-z0-9\\s]{2,50}?)(?:\\s+(?:Gur|new|On\\s+\\d))",
        Pattern.CASE_INSENSITIVE
    )
    
    // HDFC "Txn Rs.XXX On HDFC Bank Card At <VPA>" pattern (Card via UPI)
    private val HDFC_CARD_UPI_PATTERN = Pattern.compile(
        "Txn\\s+Rs\\.?[\\d,\\.]+\\s+On\\s+HDFC[^\\n]*At\\s+([a-zA-Z0-9._-]+)@[a-zA-Z]+",
        Pattern.CASE_INSENSITIVE
    )
    
    // ICICI "INR XXX spent using ICICI Bank Card on DD-MON-YY on <MERCHANT>" pattern
    private val ICICI_SPENT_ON_PATTERN = Pattern.compile(
        "INR\\s+[\\d,\\.]+\\s+spent\\s+using\\s+ICICI[^\\n]*on\\s+\\d{2}-[A-Z][a-z]{2}-\\d{2}\\s+on\\s+([A-Z][A-Za-z0-9\\s]{2,30})",
        Pattern.CASE_INSENSITIVE
    )
    
    // ICICI "Rs X,XXX spent on ICICI Bank Card XX... on DD-Mon-YY at <MERCHANT>" pattern
    private val ICICI_SPENT_AT_PATTERN = Pattern.compile(
        "Rs\\s+[\\d,\\.]+\\s+spent\\s+on\\s+ICICI[^\\n]*at\\s+([A-Z][A-Za-z0-9\\s\\.]+?)\\.",
        Pattern.CASE_INSENSITIVE
    )
    
    // ICICI "Acct debited for Rs XXX; <NAME> credited" pattern
    private val ICICI_DEBITED_CREDITED_PATTERN = Pattern.compile(
        "debited\\s+for\\s+Rs\\s+[\\d,\\.]+[^;]*;\\s*([A-Za-z][A-Za-z0-9\\s]{2,50})\\s+credited",
        Pattern.CASE_INSENSITIVE
    )
    
    // IDFC FIRST Bank "A/c debited by Rs XXX; <NAME> credited" pattern
    private val IDFC_DEBITED_CREDITED_PATTERN = Pattern.compile(
        "debited\\s+by\\s+Rs\\.?\\s*[\\d,\\.]+[^;]*;\\s*([A-Za-z][A-Za-z0-9\\s]{2,50})\\s+credited",
        Pattern.CASE_INSENSITIVE
    )

    // Axis Bank "Spent INR XXX ... PYU*Swiggy" pattern
    // Also handles "Razorpay*Sw", "AIRTEL PAYM", etc.
    // Handles optional timestamp line between Card No and Merchant
    private val AXIS_SPENT_PATTERN = Pattern.compile(
        "Spent\\s+INR\\s+[\\d,\\.]+\\s+Axis\\s+Bank\\s+Card\\s+no\\.\\s+XX\\d{4}[^\\n]*\\n(?:[^\\n]*\\d{2}:\\d{2}[^\\n]*\\n)?([A-Za-z0-9\\*\\s\\._-]+)\\n",
        Pattern.CASE_INSENSITIVE
    )
    
    // Credit Alert "credited from VPA <VPA>"
    private val CREDIT_ALERT_VPA_PATTERN = Pattern.compile(
        "credited\\s+to\\s+[^\\n]+from\\s+VPA\\s+([a-zA-Z0-9._-]+@[a-zA-Z]+)",
        Pattern.CASE_INSENSITIVE
    )
    
    // NEFT Salary pattern: "NEFT Cr-XXXX-<EMPLOYER>-<EMPLOYEE>-XXXX"
    private val NEFT_SALARY_PATTERN = Pattern.compile(
        "for\\s+NEFT\\s+Cr-[A-Z0-9]+-([A-Z][A-Za-z\\s]+(?:PRIVATE|PVT|LTD|LIMITED|TECHNOLOGIES|INDIA|CORP|INC|SOLUTIONS)?[^-]*)-",
        Pattern.CASE_INSENSITIVE
    )
    
    // FT/Clearing deposit: "FT- XXXX - <ENTITY> -"
    private val FT_DEPOSIT_PATTERN = Pattern.compile(
        "for\\s+FT-\\s*[\\dX-]+\\s*-\\s*([A-Z][A-Za-z\\s]+(?:CORPORATION|LIMITED|CLEARING)?[^-]*?)\\s*-",
        Pattern.CASE_INSENSITIVE
    )

    // ATM Withdrawal: "Rs.XXXX withdrawn at <ATM_ID/LOCATION> from A/c"
    private val WITHDRAWN_AT_PATTERN = Pattern.compile(
        "withdrawn\\s+at\\s+([A-Z0-9\\s]+?)\\s+(?:from|on)",
        Pattern.CASE_INSENSITIVE
    )
    
    // UPI ID extraction (fallback)
    private val UPI_PATTERN = Pattern.compile("([a-zA-Z0-9._-]+@[a-zA-Z]+)")

    // --- MAIN ENTRY POINT ---
    fun extract(body: String, transactionType: TransactionType): Counterparty {
        // Template matching in order of specificity
        val trace = mutableListOf<String>()
        
        // 1. HDFC "Sent To <NAME>" (P2P Transfer / UPI Payment)
        HDFC_SENT_TO_PATTERN.matcher(body).let { m ->
            if (m.find()) {
                val rawName = m.group(1) ?: return@let
                trace.add("Matched Template: HDFC_SENT_TO")
                val name = cleanName(rawName, trace)
                if (isValidName(name)) {
                    return Counterparty(name, extractUpiId(body), classifyName(name, transactionType), trace)
                } else trace.add("Invalid name rejected: $name")
            }
        }
        
        // 2. HDFC "Spent At <MERCHANT>" (CC POS)
        HDFC_SPENT_AT_PATTERN.matcher(body).let { m ->
            if (m.find()) {
                val rawName = m.group(1) ?: return@let
                trace.add("Matched Template: HDFC_SPENT_AT")
                val name = cleanName(rawName, trace)
                if (isValidName(name)) {
                    return Counterparty(name, null, CounterpartyType.MERCHANT, trace)
                } else trace.add("Invalid name rejected: $name")
            }
        }
        
        // 3. HDFC Card + UPI - Extract merchant from VPA prefix
        HDFC_CARD_UPI_PATTERN.matcher(body).let { m ->
            if (m.find()) {
                val vpaPrefix = m.group(1) ?: return@let
                trace.add("Matched Template: HDFC_CARD_UPI")
                val merchantName = extractMerchantFromVpa(vpaPrefix)
                return if (merchantName != null && isValidName(merchantName)) {
                    trace.add("Extracted Merchant from VPA prefix: $vpaPrefix -> $merchantName")
                    Counterparty(merchantName, null, CounterpartyType.MERCHANT, trace)
                } else {
                    trace.add("VPA prefix '$vpaPrefix' deemed junk/invalid")
                    Counterparty(null, null, CounterpartyType.UNKNOWN, trace)
                }
            }
        }
        
        // 4. ICICI "spent using ICICI Bank Card on <MERCHANT>"
        ICICI_SPENT_ON_PATTERN.matcher(body).let { m ->
            if (m.find()) {
                val rawName = m.group(1) ?: return@let
                trace.add("Matched Template: ICICI_SPENT_ON")
                val name = cleanName(rawName, trace)
                if (isValidName(name)) {
                    return Counterparty(name, null, CounterpartyType.MERCHANT, trace)
                } else trace.add("Invalid name rejected: $name")
            }
        }
        
        // 5. ICICI "spent on ICICI Bank Card at <MERCHANT>"
        ICICI_SPENT_AT_PATTERN.matcher(body).let { m ->
            if (m.find()) {
                val rawName = m.group(1) ?: return@let
                trace.add("Matched Template: ICICI_SPENT_AT")
                val name = cleanName(rawName, trace)
                if (isValidName(name)) {
                    return Counterparty(name, null, CounterpartyType.MERCHANT, trace)
                } else trace.add("Invalid name rejected: $name")
            }
        }

        // 5b. Axis Bank "Spent INR ... <MERCHANT>" pattern (captures messy merchant names like PYU*Swiggy)
        AXIS_SPENT_PATTERN.matcher(body).let { m ->
            if (m.find()) {
                val rawName = m.group(1) ?: return@let
                trace.add("Matched Template: AXIS_SPENT")
                val name = cleanName(rawName, trace)
                if (isValidName(name)) {
                    return Counterparty(name, null, CounterpartyType.MERCHANT, trace)
                } else trace.add("Invalid name rejected: $name")
            }
        }
        
        // 6. ICICI "debited; <NAME> credited" - ALWAYS P2P (PERSON transfer)
        ICICI_DEBITED_CREDITED_PATTERN.matcher(body).let { m ->
            if (m.find()) {
                val rawName = m.group(1) ?: return@let
                trace.add("Matched Template: ICICI_DEBITED_CREDITED")
                val name = cleanName(rawName, trace)
                if (isValidName(name)) {
                    // This pattern is always P2P transfer between people
                    return Counterparty(name, extractUpiId(body), CounterpartyType.PERSON, trace)
                } else trace.add("Invalid name rejected: $name")
            }
        }
        
        // 6b. IDFC FIRST Bank "debited by Rs XXX; <NAME> credited" - ALWAYS P2P
        IDFC_DEBITED_CREDITED_PATTERN.matcher(body).let { m ->
            if (m.find()) {
                val rawName = m.group(1) ?: return@let
                trace.add("Matched Template: IDFC_DEBITED_CREDITED")
                val name = cleanName(rawName, trace)
                if (isValidName(name)) {
                    return Counterparty(name, extractUpiId(body), CounterpartyType.PERSON, trace)
                } else trace.add("Invalid name rejected: $name")
            }
        }
        
        // 7. NEFT Salary deposits
        NEFT_SALARY_PATTERN.matcher(body).let { m ->
            if (m.find()) {
                val rawName = m.group(1) ?: return@let
                trace.add("Matched Template: NEFT_SALARY")
                val name = cleanEmployerName(rawName)
                if (name.isNotBlank() && name.length >= 3) {
                    trace.add("Cleaned Employer Name")
                    return Counterparty(name, null, CounterpartyType.MERCHANT, trace) // Employer is like a merchant
                }
            }
        }
        
        // 8. FT/Clearing deposits (stock sales, etc.)
        FT_DEPOSIT_PATTERN.matcher(body).let { m ->
            if (m.find()) {
                val rawName = m.group(1) ?: return@let
                trace.add("Matched Template: FT_DEPOSIT")
                val name = cleanEmployerName(rawName)
                if (name.isNotBlank() && name.length >= 3) {
                    trace.add("Cleaned Entity Name")
                    return Counterparty(name, null, CounterpartyType.MERCHANT, trace)
                }
            }
        }

        // 8.5 ATM "withdrawn at" pattern
        WITHDRAWN_AT_PATTERN.matcher(body).let { m ->
            if (m.find()) {
                val rawName = m.group(1) ?: return@let
                trace.add("Matched Template: WITHDRAWN_AT")
                val name = cleanName(rawName, trace)
                if (isValidName(name)) {
                    // ATM withdrawals are effectively Merchant transactions (Cash)
                    return Counterparty(name, null, CounterpartyType.MERCHANT, trace)
                }
            }
        }
        
        // 9. Credit Alert VPA
        CREDIT_ALERT_VPA_PATTERN.matcher(body).let { m ->
            if (m.find()) {
                trace.add("Matched Template: CREDIT_ALERT_VPA")
                val upi = m.group(1) ?: return@let
                val name = extractNameFromUpi(upi)
                val type = classifyVpa(upi, transactionType)
                return if (name != null && type != CounterpartyType.UNKNOWN) {
                    trace.add("Extracted Name from UPI: $name")
                    Counterparty(name, upi, type, trace)
                } else {
                    trace.add("Could not extract valid name/type from VPA")
                    Counterparty(null, upi, CounterpartyType.UNKNOWN, trace)
                }
            }
        }
        
        // 10. Fallback: Extract UPI ID if present
        val upi = extractUpiId(body)
        if (upi != null) {
            trace.add("Fallback: Found UPI ID")
            val name = extractNameFromUpi(upi)
            val type = classifyVpa(upi, transactionType)
            return if (name != null && type != CounterpartyType.UNKNOWN) {
                trace.add("Extracted Name from UPI: $name")
                Counterparty(name, upi, type, trace)
            } else {
                trace.add("Could not extract valid name/type from VPA")
                Counterparty(null, upi, CounterpartyType.UNKNOWN, trace)
            }
        }
        
        // No counterparty found
        trace.add("No Template Matched")
        return Counterparty(null, null, CounterpartyType.UNKNOWN, trace)
    }

    // --- UTILITIES ---
    
    private fun extractUpiId(body: String): String? {
        val m = UPI_PATTERN.matcher(body)
        return if (m.find()) m.group(1) else null
    }
    
    /**
     * Extract a human-readable name from a UPI ID.
     * Returns null for junk VPAs.
     */
    private fun extractNameFromUpi(upiId: String): String? {
        val prefix = upiId.substringBefore("@")
        val lower = prefix.lowercase()
        
        // Junk VPA prefixes - return null
        if (isJunkVpaPrefix(lower) && !lower.startsWith("paytmqr")) {
            return null
        }
        
        // Clean the prefix
        val cleaned = prefix
            .replace(".", " ")
            .replace("_", " ")
            .replace("-", " ")
            .trim()
        
        return if (cleaned.length >= 3 && (cleaned.any { it.isLetter() || it.isDigit() } || cleaned == "paytmqr")) {
            cleaned
        } else null
    }
    
    /**
     * Check if a VPA prefix is junk (paytmqr, q12345, phone numbers, etc.)
     */
    private fun isJunkVpaPrefix(lower: String): Boolean {
        // Special exception: paytmqr is a valid generic merchant identifier
        if (lower.startsWith("paytmqr")) return false
        
       return lower.startsWith("paytm.") ||
               lower.startsWith("paytm-") ||
               lower.startsWith("bharatpe") ||
               lower.startsWith("phonemerchant") ||
               lower.startsWith("gpay-") ||
               lower.startsWith("ezetap") ||
               lower.startsWith("vyapar.") ||
               lower.startsWith("googlepay")
    }

    private val KNOWN_MERCHANT_VPAS = mapOf(
        "zeptonow" to "Zepto",
        "swiggy" to "Swiggy",
        "zomato" to "Zomato",
        "myntra" to "Myntra",
        "licious" to "Licious",
        "cf.licious" to "Licious",
        "dineout" to "Dineout",
        "bookmyshow" to "BookMyShow",
        "priveplex" to "PVR",
        "gameonlevel" to "Game On",
        "mandikinga" to "Mandi King",
        "mandiking" to "Mandi King"
    )
    
    private fun extractMerchantFromVpa(vpaPrefix: String): String? {
        val lower = vpaPrefix.lowercase()
        
        // Junk VPA prefixes - return null (no valid merchant)
        if (isJunkVpaPrefix(lower)) {
            return null
        }
        
        // Known merchant VPA patterns
        for ((pattern, name) in KNOWN_MERCHANT_VPAS) {
            if (lower.contains(pattern)) return name
        }
        
        // Clean up generic VPA prefixes
        val cleaned = vpaPrefix
            .replace(REGEX_VPA_TRAILING_DIGITS, "")  // Remove trailing numbers (SWIGGY8 -> SWIGGY)
            .replace(REGEX_VPA_SEPARATORS, " ")  // Replace separators
            .trim()
        
        return if (cleaned.length >= 3 && cleaned.any { it.isLetter() }) {
            cleaned.split(" ")
                .filter { it.isNotBlank() && it.length > 1 }
                .joinToString(" ") { it.replaceFirstChar { c -> c.uppercaseChar() } }
                .takeIf { it.isNotBlank() && !isJunkVpaPrefix(it.lowercase()) }
        } else null
    }
    
    /**
     * Clean employer name from NEFT/FT messages
     */
    private fun cleanEmployerName(rawName: String): String {
        return rawName
            .replace(REGEX_SPACES, " ")
            .replace(REGEX_EMPLOYER_NOISE, "")
            .trim()
            .split(" ")
            .filter { it.isNotBlank() }
            .take(3) // Max 3 words
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercaseChar() } }
    }
    
    private fun cleanName(name: String, trace: MutableList<String>? = null): String {
        val original = name
        var cleaned = name.trim()
        
        // Remove common patterns
        val preReplace = cleaned
        cleaned = cleaned.replace(REGEX_PREPOSITION_PREFIX, "")
        if (preReplace != cleaned) trace?.add("Removed Preposition prefix")
        
        // Fix Axis/Payment Gateway prefixes
        if (cleaned.contains("*")) {
            val parts = cleaned.split("*")
            if (parts.size > 1) {
                // Handle cases like "PYU*Swiggy", "Razorpay*Sw", "CAS*Swiggy"
                val prefix = parts[0].uppercase()
                val suffix = parts[1]
                
                // Known gateway prefixes
                if (prefix in listOf("PYU", "CAS", "RAZORPAY", "PAYU", "CCAVENUE", "HDFC", "AXIS", "ICICI", "AIRTEL", "PAYTM")) {
                    cleaned = suffix
                    trace?.add("Stripped Gateway Prefix: $prefix")
                }
            }
        }
        
        // Handle "Razorpay*Sw" -> "Swiggy" specific partials
        if (cleaned.equals("Sw", ignoreCase = true) || cleaned.equals("Swi", ignoreCase = true)) {
            cleaned = "Swiggy"
            trace?.add("Normalized partial 'Sw/Swi' -> Swiggy")
        }
        if (cleaned.equals("Instama", ignoreCase = true)) {
             cleaned = "Instamart"
             trace?.add("Normalized partial 'Instama' -> Instamart")
        }
        
        // Clean trailing dots and digits (e.g. "MANDIKING.6603" -> "MANDIKING")
        if (cleaned.matches(REGEX_TRAILING_DOTS_DIGITS_CHECK)) {
             val pre = cleaned
             cleaned = cleaned.replace(REGEX_TRAILING_DOTS_DIGITS, "")
             if (pre != cleaned) trace?.add("Stripped trailing dots/digits")
        }
        
        // Clean UP digits in the middle (SWIGGY8@ybl -> SWIGGY)
        // If it's a VPA-like string or just has digits at end purely
        if (cleaned.matches(REGEX_ALPHA_NUM_DIGITS_CHECK)) {
             val pre = cleaned
             cleaned = cleaned.replace(REGEX_VPA_TRAILING_DIGITS, "")
             if (pre != cleaned) trace?.add("Stripped end-digits from alpha-numeric name")
        }

        cleaned = cleaned
            .replace(REGEX_SPACES, " ")  // Collapse multiple spaces
            .replace(REGEX_NEWLINE_REST, "")  // Remove everything after newline
            .replace(REGEX_TRAILING_LOCATION_NOISE, "")  // Remove trailing noise
            .trim()
            
        if (cleaned != original && trace?.isEmpty() == true) {
             trace.add("General cleanup (spacing/newlines)")
        }
        return cleaned
    }
    
    private fun isValidName(name: String): Boolean {
        val lower = name.lowercase()
        
        // Blocklist
        val blocked = setOf(
            "not you", "on", "at", "to", "via", "from", "for", "by",
            "upi", "imps", "neft", "ref", "info", "update", "alert",
            "your card", "card ending", "hdfc bank", "icici bank", "sbi card",
            "rs", "inr", "payment", "transaction", "txn", "https", "http",
            "call", "sms", "block"
        )
        
        if (blocked.any { lower.startsWith(it) || lower == it }) return false
        if (name.length < 3) return false
        if (name.all { it.isDigit() || it.isWhitespace() }) return false
        if (lower.matches(REGEX_DIGITS_ONLY_SHORT)) return false  // Just numbers like "100"
        
        return true
    }
    
    private fun classifyName(name: String, transactionType: TransactionType): CounterpartyType {
        // For Expenses (card spends, shopping), always MERCHANT
        if (transactionType == TransactionType.EXPENSE) {
            return CounterpartyType.MERCHANT
        }
        
        // For TRANSFER and INCOME, check if looks like a person name (2-4 words, all alphabetic)
    val words = name.split(" ").filter { it.isNotBlank() }
    val allAlphabetic = words.all { w -> w.all { it.isLetter() || it == '.' } }
    val upper = name.uppercase()

    // KNOWN MERCHANTS that look like people or have 2-3 words
    if (upper.contains("ZERODHA") || 
        upper.contains("ICCL") || 
        upper.contains("INDIAN CLEARING") || 
        upper.contains("TECHNOLOGIES") || 
        upper.contains("PRIVATE") || 
        upper.contains("LIMITED") ||
        upper.contains("BROKING") ||
        upper.contains("CRED CLUB") ||
        upper.contains("CRED APP") ||
        upper.contains("AMEX") ||
        upper.contains("ONE CARD") ||
        upper.contains("SBI CARD")) {
        return CounterpartyType.MERCHANT
    }
    
    return if (words.size in 2..4 && allAlphabetic) {
            CounterpartyType.PERSON
        } else if (words.size == 1 && words[0].all { it.isLetter() } && words[0].length >= 3) {
            // Single word names like "SHIVKUMAR", "PRATIKSHA" are also PERSON
            CounterpartyType.PERSON
        } else {
            CounterpartyType.MERCHANT
        }
    }
    
    private fun classifyVpa(upiId: String, transactionType: TransactionType): CounterpartyType {
        val prefix = upiId.substringBefore("@").lowercase()
        
        // 0. Known Merchants -> MERCHANT
        for (pattern in KNOWN_MERCHANT_VPAS.keys) {
            if (prefix.contains(pattern)) return CounterpartyType.MERCHANT
        }

        // Q-codes (Card-UPI) -> MERCHANT (Offline Merchant)
        if (prefix.matches(Regex("^q\\d+.*"))) {
            return CounterpartyType.MERCHANT
        }
        
        // Junk VPAs
        if (isJunkVpaPrefix(prefix)) {
            return CounterpartyType.UNKNOWN
        }
        
        // FIX #2: Cashback/Reward VPAs are MERCHANT (system), not PERSON
        // Example: bhimcashback@hdfcbank, googlepay@axisbank (rewards), etc.
        if (CASHBACK_VPA_PATTERNS.any { prefix.contains(it) }) {
            return CounterpartyType.MERCHANT
        }
        
        // For Expenses, default to MERCHANT (even for VPAs)
        if (transactionType == TransactionType.EXPENSE) {
            return CounterpartyType.MERCHANT
        }
        
        // For Income/Transfer, VPAs are likely PERSON
        return CounterpartyType.PERSON
    }
    
    /**
     * Extract the account holder name from NEFT/deposit messages.
     * 
     * NEFT format: "NEFT Cr-{IFSC}-{SENDER}-{RECEIVER_NAME}-{REF}"
     * Example: "NEFT Cr-BOFA0CN6215-OPEN TEXT TECHNOLOGIES-GODALA SAIKUMAR REDDY-BOFAH25364"
     * 
     * Returns the receiver name (which is the user's account holder name) or null.
     */
    fun extractAccountHolderName(body: String): String? {
        // Pattern 1: NEFT Cr-IFSC-SENDER-RECEIVER-REF
        // Use non-greedy match with [^-]+ for sender/receiver
        val neftPattern = Pattern.compile(
            "NEFT\\s+Cr-([A-Z0-9]+)-([^-]+)-([^-]+)-([A-Z0-9]+)",
            Pattern.CASE_INSENSITIVE
        )
        
        neftPattern.matcher(body).let { m ->
            if (m.find()) {
                val name = m.group(3)?.trim() // Receiver is group 3
                if (name != null && name.length >= 3 && name.contains(" ")) {
                    // Skip if looks like a reference number (mostly digits)
                    if (name.count { it.isDigit() } > name.length / 2) return@let
                    return name.uppercase()
                }
            }
        }
        
        // Pattern 2: credited to HDFC Bank A/c XX... for NEFT...-SENDER-RECEIVER-REF
        val depositPattern = Pattern.compile(
            "for\\s+NEFT[^-]*-([^-]+)-([^-]+)-[A-Z0-9]+",
            Pattern.CASE_INSENSITIVE
        )
        
        depositPattern.matcher(body).let { m ->
            if (m.find()) {
                val name = m.group(2)?.trim() // Receiver is group 2
                if (name != null && name.length >= 3 && name.contains(" ")) {
                    if (name.count { it.isDigit() } > name.length / 2) return@let
                    return name.uppercase()
                }
            }
        }
        
        return null
    }
    
    /**
     * Detect if an NEFT message is a self-transfer.
     * 
     * Self-transfer pattern: NEFT Cr-{IFSC}-{SENDER_NAME}-{RECEIVER_NAME}-{REF}
     * When SENDER_NAME == RECEIVER_NAME (or very similar), it's a self-transfer.
     * 
     * Example: "NEFT Cr-SURY0BK0000-GODALA SAIKUMAR REDDY-GODALA SAIKUMAR REDDY-SURYN25351683110"
     * This is money moving from user's Suryoday account to user's HDFC account.
     */
    fun isNeftSelfTransfer(body: String): Boolean {
        // Pattern to extract both sender and receiver names from NEFT
        // NEFT Cr-{IFSC}-{SENDER}-{RECEIVER}-{REF}
        // Use non-greedy match for names (anything between dashes that isn't a dash)
        val neftPattern = Pattern.compile(
            "NEFT\\s+Cr-([A-Z0-9]+)-([^-]+)-([^-]+)-([A-Z0-9]+)",
            Pattern.CASE_INSENSITIVE
        )
        
        neftPattern.matcher(body).let { m ->
            if (m.find()) {
                val sender = m.group(2)?.trim()?.uppercase() ?: return false
                val receiver = m.group(3)?.trim()?.uppercase() ?: return false
                
                // Skip if either name looks like a reference number (mostly digits)
                if (sender.count { it.isDigit() } > sender.length / 2) return false
                if (receiver.count { it.isDigit() } > receiver.length / 2) return false
                
                // Check if sender and receiver are the same or very similar
                if (sender == receiver) {
                    return true
                }
                
                // Check fuzzy match - if both have same significant name parts
                val senderParts = sender.split("\\s+".toRegex()).filter { it.length >= 3 && it.all { c -> c.isLetter() } }.toSet()
                val receiverParts = receiver.split("\\s+".toRegex()).filter { it.length >= 3 && it.all { c -> c.isLetter() } }.toSet()
                
                // If at least 2 significant parts match, it's likely the same person
                val commonParts = senderParts.intersect(receiverParts)
                if (commonParts.size >= 2) {
                    return true
                }
            }
        }
        
        return false
    }
    
    /**
     * Extract NEFT source information (IFSC + sender) for salary pattern tracking.
     * 
     * NEFT format: "NEFT Cr-{IFSC}-{SENDER}-{RECEIVER}-{REF}"
     * Example: "NEFT Cr-BOFA0CN6215-OPEN TEXT TECHNOLOGIES-GODALA SAIKUMAR-BOFAH25364"
     * 
     * @return Pair of (IFSC, Sender) or null if not an NEFT message
     */
    fun extractNeftSource(body: String): Pair<String, String>? {
        // Pattern to extract IFSC and sender from NEFT credit
        // NEFT Cr-{IFSC}-{SENDER}-{RECEIVER}-{REF}
        // Use non-greedy match with [^-]+ for sender
        val neftPattern = Pattern.compile(
            "NEFT\\s+Cr-([A-Z0-9]+)-([^-]+)-([^-]+)-([A-Z0-9]+)",
            Pattern.CASE_INSENSITIVE
        )
        
        neftPattern.matcher(body).let { m ->
            if (m.find()) {
                val ifsc = m.group(1)?.trim()?.uppercase() ?: return null
                val sender = m.group(2)?.trim()?.uppercase() ?: return null
                
                // Skip if sender looks like a reference number (mostly digits)
                if (sender.count { it.isDigit() } > sender.length / 2) return@let
                
                // Normalize sender (remove trailing spaces, normalize whitespace)
                val normalizedSender = sender.split("\\s+".toRegex())
                    .filter { it.isNotEmpty() }
                    .joinToString(" ")
                
                if (normalizedSender.length >= 3) {
                    return Pair(ifsc, normalizedSender)
                }
            }
        }
        
        // Also check ICICI format: "NEFT-IFSC-SENDER"
        val iciciPattern = Pattern.compile(
            "NEFT-([A-Z0-9]+)-([^-\\.]+)",
            Pattern.CASE_INSENSITIVE
        )
        
        iciciPattern.matcher(body).let { m ->
            if (m.find()) {
                val ifsc = m.group(1)?.trim()?.uppercase() ?: return null
                val sender = m.group(2)?.trim()?.uppercase() ?: return null
                
                if (sender.count { it.isDigit() } > sender.length / 2) return@let
                
                val normalizedSender = sender.split("\\s+".toRegex())
                    .filter { it.isNotEmpty() }
                    .joinToString(" ")
                
                if (normalizedSender.length >= 3) {
                    return Pair(ifsc, normalizedSender)
                }
            }
        }
        
        return null
    }
}
