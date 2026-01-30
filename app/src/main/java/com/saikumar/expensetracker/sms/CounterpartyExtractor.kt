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
    // Captures full VPA prefix including merchant names like "svmbowling.67079608"
    private val HDFC_CARD_UPI_PATTERN = Pattern.compile(
        "Txn\\s+Rs\\.?[\\d,\\.]+\\s+On\\s+HDFC[^\\n]*At\\s+([a-zA-Z][a-zA-Z0-9._-]*)@[a-zA-Z]+",
        Pattern.CASE_INSENSITIVE
    )
    
    // ICICI "INR XXX spent using ICICI Bank Card on DD-MON-YY on <MERCHANT>" pattern
    // Also supports USD and other currencies
    private val ICICI_SPENT_ON_PATTERN = Pattern.compile(
        "(?:INR|USD|EUR|GBP|AED|SGD)\\s+[\\d,\\.]+\\s+spent\\s+using\\s+ICICI[^\\n]*on\\s+\\d{2}-[A-Z][a-z]{2}-\\d{2}\\s+on\\s+([A-Z][A-Za-z0-9\\s\\.]{2,30})",
        Pattern.CASE_INSENSITIVE
    )

    // SBI Credit Card "Rs.XXX spent on your SBI Credit Card at <MERCHANT>" pattern
    private val SBI_SPENT_AT_PATTERN = Pattern.compile(
        "Rs\\.?[\\d,\\.]+\\s+spent\\s+on\\s+your\\s+SBI\\s+Credit\\s+Card[^\\n]*at\\s+([A-Z][A-Za-z0-9\\s]{2,30})",
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

    // ICICI NEFT pattern: "Info NEFT-<REF>-<SENDER>."
    // Example: "Info NEFT-DEUTH05533207350-ZF IND. Available Balance"
    private val ICICI_NEFT_PATTERN = Pattern.compile(
        "Info\\s+NEFT-[A-Z0-9]+-([A-Z][A-Za-z0-9\\s&]+?)\\.",
        Pattern.CASE_INSENSITIVE
    )

    // FT/Clearing deposit: "FT-XXXX-XXXX - <ENTITY> - Avl bal"
    // Captures entity name between the dashes after FT reference
    // Example: "for FT- 2334233915-XXXXXXXXXXXX9159 - INDIAN CLEARING CORPORATION LIMITED - Avl bal"
    // Note: Handles optional space after "FT-"
    private val FT_DEPOSIT_PATTERN = Pattern.compile(
        "for\\s+FT-\\s*[\\dX]+(?:-[\\dX]+)?\\s*-\\s*([^-]+?)\\s*-",
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

        // 5c. SBI Credit Card "spent at <MERCHANT>" pattern
        SBI_SPENT_AT_PATTERN.matcher(body).let { m ->
            if (m.find()) {
                val rawName = m.group(1) ?: return@let
                trace.add("Matched Template: SBI_SPENT_AT")
                val name = cleanName(rawName, trace)
                if (isValidName(name)) {
                    return Counterparty(name, null, CounterpartyType.MERCHANT, trace)
                } else trace.add("Invalid name rejected: $name")
            }
        }
        
        // 6. ICICI "debited; <NAME> credited" - Usually P2P but check for merchants first
        ICICI_DEBITED_CREDITED_PATTERN.matcher(body).let { m ->
            if (m.find()) {
                val rawName = m.group(1) ?: return@let
                trace.add("Matched Template: ICICI_DEBITED_CREDITED")
                val name = cleanName(rawName, trace)
                if (isValidName(name)) {
                    // Check if this is a known merchant/credit card service, not a person
                    val type = classifyDebitedCreditedName(name)
                    trace.add("Classified as ${type.name}")
                    return Counterparty(name, extractUpiId(body), type, trace)
                } else trace.add("Invalid name rejected: $name")
            }
        }

        // 6b. IDFC FIRST Bank "debited by Rs XXX; <NAME> credited" - Usually P2P but check for merchants first
        IDFC_DEBITED_CREDITED_PATTERN.matcher(body).let { m ->
            if (m.find()) {
                val rawName = m.group(1) ?: return@let
                trace.add("Matched Template: IDFC_DEBITED_CREDITED")
                val name = cleanName(rawName, trace)
                if (isValidName(name)) {
                    val type = classifyDebitedCreditedName(name)
                    trace.add("Classified as ${type.name}")
                    return Counterparty(name, extractUpiId(body), type, trace)
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

        // 7b. ICICI NEFT deposits (alternative format)
        ICICI_NEFT_PATTERN.matcher(body).let { m ->
            if (m.find()) {
                val rawName = m.group(1) ?: return@let
                trace.add("Matched Template: ICICI_NEFT")
                val name = cleanEmployerName(rawName)
                if (name.isNotBlank() && name.length >= 3) {
                    trace.add("Cleaned Employer Name: $name")
                    return Counterparty(name, null, CounterpartyType.MERCHANT, trace)
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

    /**
     * Split camelCase/PascalCase into separate words
     * "svmbowling" -> "svm bowling"
     * "lastHouseCoffee" -> "last House Coffee"
     */
    private fun splitCamelCase(input: String): String {
        // Insert space before uppercase letters
        return input
            .replace(Regex("([a-z])([A-Z])"), "$1 $2")
            .replace(Regex("([A-Z]+)([A-Z][a-z])"), "$1 $2")
    }

    /**
     * Extract readable merchant name from VPA-style strings
     * "svmbowling.67079608" -> "Svm Bowling"
     * "lasthousecoffee.96120121" -> "Last House Coffee"
     */
    private fun cleanVpaMerchantName(vpaPart: String): String? {
        // Remove numbers and dots to get base name
        var cleaned = vpaPart
            .substringBefore(".")
            .replace(Regex("[0-9]"), "")
            .trim()

        if (cleaned.length < 3) return null

        // Check if it's a known merchant
        for ((pattern, name) in KNOWN_MERCHANT_VPAS) {
            if (cleaned.lowercase().contains(pattern)) return name
        }

        // Try to split camelCase
        cleaned = splitCamelCase(cleaned)

        // Title case each word
        return cleaned.split(" ")
            .filter { it.isNotBlank() && it.length > 1 }
            .joinToString(" ") { it.replaceFirstChar { c -> c.uppercaseChar() } }
            .takeIf { it.length >= 3 }
    }

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
     * Returns false for known merchant VPAs that should be extracted.
     */
    private fun isJunkVpaPrefix(lower: String): Boolean {
        // First check if it's a known merchant - those are NEVER junk
        for (pattern in KNOWN_MERCHANT_VPAS.keys) {
            if (lower.contains(pattern)) return false
        }

        // Check for meaningful merchant VPAs (not generic payment gateways)
        // These VPAs have actual merchant info embedded
        if (lower.contains(".rzp@") ||  // blinkitjkb.rzp@
            lower.contains("@hdfcbank") ||  // May have merchant prefix
            lower.contains("@icici") ||
            lower.contains("@axisbank") ||
            lower.contains("@okbizaxis")) {
            // These might have merchant names, let extraction proceed
            return false
        }

        // Generic/junk VPA patterns
        return lower.startsWith("paytm.") ||
               lower.startsWith("paytm-") ||
               lower.startsWith("bharatpe") && !lower.contains("merchant") ||
               lower.startsWith("phonemerchant") ||
               lower.startsWith("gpay-") && lower.matches(Regex("gpay-\\d+.*")) ||  // gpay-12345 is junk
               lower.startsWith("ezetap") ||
               lower.startsWith("vyapar.") && lower.matches(Regex("vyapar\\.\\d+.*")) ||  // vyapar.123 is junk
               lower.startsWith("googlepay") ||
               lower.matches(Regex("^\\d{10}.*")) ||  // Phone numbers as VPA
               lower.matches(Regex("^q\\d{6,}.*"))    // Q-codes without merchant info
    }

    private val KNOWN_MERCHANT_VPAS = mapOf(
        // Food Delivery
        "zeptonow" to "Zepto",
        "swiggy" to "Swiggy",
        "zomato" to "Zomato",
        "licious" to "Licious",
        "cf.licious" to "Licious",
        "blinkit" to "Blinkit",
        "instamart" to "Instamart",
        "bigbasket" to "BigBasket",
        "gokhana" to "Gokhana",
        "dunzo" to "Dunzo",

        // Shopping
        "myntra" to "Myntra",
        "flipkart" to "Flipkart",
        "amazon" to "Amazon",
        "ajio" to "Ajio",
        "nykaa" to "Nykaa",
        "meesho" to "Meesho",

        // Entertainment
        "dineout" to "Dineout",
        "bookmyshow" to "BookMyShow",
        "priveplex" to "PVR",
        "pvrinox" to "PVR Inox",
        "gameonlevel" to "Game On",
        "netflix" to "Netflix",
        "hotstar" to "Hotstar",
        "spotify" to "Spotify",

        // Restaurants/Food
        "mandikinga" to "Mandi King",
        "mandiking" to "Mandi King",
        "svmbowling" to "SVM Bowling",
        "lasthousecoffee" to "Last House Coffee",
        "starbucks" to "Starbucks",
        "dominos" to "Dominos",
        "mcdonalds" to "McDonalds",
        "kfc" to "KFC",
        "subway" to "Subway",
        "burgerking" to "Burger King",
        "pizzahut" to "Pizza Hut",

        // Travel
        "uber" to "Uber",
        "ola" to "Ola",
        "rapido" to "Rapido",
        "redbus" to "RedBus",
        "makemytrip" to "MakeMyTrip",
        "goibibo" to "Goibibo",
        "irctc" to "IRCTC",
        "cleartrip" to "Cleartrip",

        // Payments/Fintech
        "cred" to "CRED",
        "paytm" to "Paytm",
        "phonepe" to "PhonePe",
        "googlepay" to "Google Pay",
        "amazonpay" to "Amazon Pay",

        // Utilities/Bills
        "airtel" to "Airtel",
        "jio" to "Jio",
        "tatasky" to "Tata Sky",
        "tatapay" to "Tata Payments",

        // Others
        "zerodha" to "Zerodha",
        "groww" to "Groww",
        "upstox" to "Upstox",
        "udemy" to "Udemy",
        "adobe" to "Adobe",
        "google" to "Google",
        "microsoft" to "Microsoft",
        "apple" to "Apple",
        "youtube" to "YouTube",
        "claude" to "Claude AI"
    )
    
    private fun extractMerchantFromVpa(vpaPrefix: String): String? {
        val lower = vpaPrefix.lowercase()

        // Known merchant VPA patterns - check first
        for ((pattern, name) in KNOWN_MERCHANT_VPAS) {
            if (lower.contains(pattern)) return name
        }

        // Junk VPA prefixes - return null (no valid merchant)
        if (isJunkVpaPrefix(lower)) {
            return null
        }

        // Handle VPA patterns like "blinkitjkb.rzp" or "svmbowling.67079608"
        // Extract the meaningful part before dots/numbers
        var cleaned = vpaPrefix
            .substringBefore(".")  // Take part before first dot
            .substringBefore("@")  // Remove @bank suffix if present

        // Remove gateway prefixes
        val gatewayPrefixes = listOf("paytm", "bharatpe", "ezetap", "gpay", "phonepe", "razorpay")
        for (prefix in gatewayPrefixes) {
            if (cleaned.lowercase().startsWith(prefix) && cleaned.length > prefix.length + 2) {
                cleaned = cleaned.substring(prefix.length)
            }
        }

        // Remove trailing digits and common suffixes
        cleaned = cleaned
            .replace(REGEX_VPA_TRAILING_DIGITS, "")  // Remove trailing numbers
            .replace(REGEX_VPA_SEPARATORS, " ")      // Replace separators with spaces
            .replace(Regex("(?i)(rzp|payu|jkb|esbz)$"), "")  // Remove gateway suffixes
            .trim()

        // Final validation and formatting
        return if (cleaned.length >= 3 && cleaned.any { it.isLetter() }) {
            // Check known merchants one more time with cleaned name
            val cleanedLower = cleaned.lowercase()
            for ((pattern, name) in KNOWN_MERCHANT_VPAS) {
                if (cleanedLower.contains(pattern)) return name
            }

            // Format as title case
            cleaned.split(" ")
                .filter { it.isNotBlank() && it.length > 1 }
                .joinToString(" ") { it.replaceFirstChar { c -> c.uppercaseChar() } }
                .takeIf { it.isNotBlank() && !isJunkVpaPrefix(it.lowercase()) }
        } else null
    }
    
    /**
     * Clean employer name from NEFT/FT messages
     * IMPORTANT: For FT deposits, preserve entity names like "INDIAN CLEARING CORPORATION"
     * which are crucial for investment redemption detection
     */
    private fun cleanEmployerName(rawName: String): String {
        val cleaned = rawName
            .replace(REGEX_SPACES, " ")
            .trim()

        // DON'T remove CORPORATION/LIMITED/CLEARING for known investment entities
        val upper = cleaned.uppercase()
        val isInvestmentEntity = upper.contains("INDIAN CLEARING") ||
                                 upper.contains("ICCL") ||
                                 upper.contains("ZERODHA") ||
                                 upper.contains("CLEARING CORPORATION")

        val final = if (isInvestmentEntity) {
            // Keep full name for investment entities (up to 5 words)
            cleaned
                .split(" ")
                .filter { it.isNotBlank() }
                .take(5)
                .joinToString(" ") { it.replaceFirstChar { c -> c.uppercaseChar() } }
        } else {
            // Remove corporate suffixes for employers/salary sources
            cleaned
                .replace(REGEX_EMPLOYER_NOISE, "")
                .trim()
                .split(" ")
                .filter { it.isNotBlank() }
                .take(3) // Max 3 words
                .joinToString(" ") { it.replaceFirstChar { c -> c.uppercaseChar() } }
        }

        return final
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
        
        // Handle truncated merchant names (common in Axis Bank SMS)
        val truncatedMerchants = mapOf(
            "sw" to "Swiggy",
            "swi" to "Swiggy",
            "swiggy" to "Swiggy",
            "instama" to "Instamart",
            "bundl techn" to "Bundle Technologies",
            "bundl" to "Bundle",
            "airtel paym" to "Airtel",
            "airtel" to "Airtel",
            "tatapay" to "Tata Payments",
            "tatapayments" to "Tata Payments",
            "amazon pay in e" to "Amazon",
            "amazon india cy" to "Amazon",
            "amazon" to "Amazon",
            "youtubegoogle" to "YouTube",
            "google play" to "Google Play",
            "netflix" to "Netflix",
            "udemy subscript" to "Udemy",
            "adobe premiere" to "Adobe",
            "claude.ai subsc" to "Claude AI",
            "avenue supermar" to "Avenue Supermarts",
            "dineout" to "Dineout",
            "bookmyshow" to "BookMyShow",
            "gameonlevelupyourf" to "Game On",
            "gameonlevelupyour" to "Game On",
            "prasads" to "Prasads",
            "mapro garden llp" to "Mapro Garden",
            "a food affair" to "A Food Affair"
        )

        val lowerCleaned = cleaned.lowercase().trim()
        for ((partial, fullName) in truncatedMerchants) {
            if (lowerCleaned == partial || lowerCleaned.startsWith(partial)) {
                trace?.add("Normalized truncated '$cleaned' -> $fullName")
                cleaned = fullName
                break
            }
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
    
    /**
     * Special classification for "debited; <NAME> credited" patterns.
     * This pattern can be either P2P (person-to-person) or merchant payment (like credit card bills).
     * Check for known merchants first before defaulting to PERSON.
     */
    private fun classifyDebitedCreditedName(name: String): CounterpartyType {
        val upper = name.uppercase()

        // Credit Card Payment Services - these are MERCHANTS, not PERSON
        if (upper.contains("CRED CLUB") ||
            upper.contains("CRED APP") ||
            upper.contains("CRED") && upper.split(" ").size <= 2 ||
            upper.contains("AMEX") ||
            upper.contains("ONE CARD") ||
            upper.contains("ONECARD") ||
            upper.contains("SBI CARD") ||
            upper.contains("SBICARD") ||
            upper.contains("HDFC CARD") ||
            upper.contains("HDFCCARD") ||
            upper.contains("AXIS CARD") ||
            upper.contains("AXISCARD") ||
            upper.contains("ICICI CARD") ||
            upper.contains("ICICCARD") ||
            upper.contains("BILLDESK") ||
            upper.contains("BILL DESK") ||
            upper.contains("PAYTM") ||
            upper.contains("PHONEPE") ||
            upper.contains("GOOGLEPAY") ||
            upper.contains("GOOGLE PAY")) {
            return CounterpartyType.MERCHANT
        }

        // Known Merchants that might appear in this pattern
        for ((pattern, _) in KNOWN_MERCHANT_VPAS) {
            if (upper.contains(pattern.uppercase())) {
                return CounterpartyType.MERCHANT
            }
        }

        // Check for company/business indicators
        if (upper.contains("TECHNOLOGIES") ||
            upper.contains("PRIVATE") ||
            upper.contains("LIMITED") ||
            upper.contains("BROKING") ||
            upper.contains("PVT LTD") ||
            upper.contains("LLC") ||
            upper.contains("INC") ||
            upper.contains("CORP")) {
            return CounterpartyType.MERCHANT
        }

        // Default to PERSON for this pattern (typical P2P transfer)
        return CounterpartyType.PERSON
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

                // IMPROVED: Better fuzzy matching for name variations
                if (areNamesEquivalent(sender, receiver)) {
                    return true
                }
            }
        }

        return false
    }

    /**
     * Improved name matching that handles various name formats:
     * - Initials: "S KUMAR" matches "SAIKUMAR"
     * - Partial names: "GODALA S REDDY" matches "GODALA SAIKUMAR REDDY"
     * - Word order variations: "KUMAR SAIKUMAR" matches "SAIKUMAR KUMAR"
     * - Middle name variations: "GODALA SAIKUMAR" matches "GODALA SAIKUMAR REDDY"
     */
    private fun areNamesEquivalent(name1: String, name2: String): Boolean {
        val parts1 = name1.split("\\s+".toRegex()).filter { it.isNotEmpty() }
        val parts2 = name2.split("\\s+".toRegex()).filter { it.isNotEmpty() }

        if (parts1.isEmpty() || parts2.isEmpty()) return false

        // Extract significant parts (length >= 3 and mostly letters)
        val significantParts1 = parts1.filter { it.length >= 3 && it.count { c -> c.isLetter() } >= 2 }
        val significantParts2 = parts2.filter { it.length >= 3 && it.count { c -> c.isLetter() } >= 2 }

        // If at least 2 significant parts match (regardless of order), likely same person
        val commonSignificant = significantParts1.intersect(significantParts2.toSet())
        if (commonSignificant.size >= 2) {
            return true
        }

        // Check if single significant part matches AND has initials that match
        if (commonSignificant.size == 1) {
            val initials1 = parts1.filter { it.length == 1 }.toSet()
            val initials2 = parts2.filter { it.length == 1 }.toSet()

            // Check if initials from one name match the first letters of parts in other name
            val matches1to2 = initials1.any { initial ->
                significantParts2.any { it.startsWith(initial) }
            }
            val matches2to1 = initials2.any { initial ->
                significantParts1.any { it.startsWith(initial) }
            }

            if (matches1to2 || matches2to1) {
                return true
            }
        }

        // Check if all parts of shorter name are contained in longer name
        val (shorterParts, longerParts) = if (parts1.size <= parts2.size) {
            parts1 to parts2
        } else {
            parts2 to parts1
        }

        val allShorterPartsMatched = shorterParts.all { shortPart ->
            longerParts.any { longPart ->
                // Exact match or one is prefix of other
                shortPart == longPart ||
                (shortPart.length == 1 && longPart.startsWith(shortPart)) ||
                (longPart.length == 1 && shortPart.startsWith(longPart))
            }
        }

        return allShorterPartsMatched && shorterParts.size >= 2
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
