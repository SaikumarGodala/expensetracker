package com.saikumar.expensetracker.util

import java.util.Locale

object MerchantNormalizer {
    private val REMOVAL_TOKENS = setOf(
        "inc", "llc", "store", "pvt", "ltd", "corp", "corporation", 
        "limited", "private", "services", "solutions", "enterprise", 
        "global", "technologies", "tech", "com", "net", "org",
        "payments", "pay", "upi", "india", "ind",
        "cf", "rzp", "payu", "pyu", "ccav", "billdesk", "fs", "in",
        "atos", "razorpay", "ecom", "retail", "merchants"
    )

    fun normalize(rawName: String?): String? {
        if (rawName.isNullOrBlank()) return null

        var normalized = rawName.lowercase(Locale.getDefault()).trim()

        // 0. Remove known gateway prefixes explicitly if present
        // Handles "PYU*Swiggy", "ATOS*Name", "UPI-Name"
        val prefixes = listOf("pyu*", "atos*", "upi-", "rzp*")
        for (prefix in prefixes) {
            if (normalized.startsWith(prefix)) {
                normalized = normalized.removePrefix(prefix)
            }
        }
        
        // Special case: paytmqr -> paytm
        if (normalized.startsWith("paytmqr")) {
             normalized = "paytm"
        }

        // 0.5 Strip suffix noise (e.g. .6603, .store) before digit removal to preserve boundaries
        // This handles "merchant.1234" -> "merchant"
        normalized = normalized.replace(Regex("\\.[a-z0-9]+$"), "")

        // 1. Remove digits (SWIGGY8 -> swiggy)
        // CAREFUL: Don't remove if whole name is digits (rare) or if digits are meaningful?
        // For general merchants, digits are usually noise.
        normalized = normalized.replace(Regex("[0-9]"), " ")

        // 2. Remove punctuation
        normalized = normalized.replace(Regex("[^a-z ]"), " ")

        // 3. Tokenize
        val tokens = normalized.split("\\s+".toRegex()).toMutableList()

        // 4. Remove common noise tokens
        val filteredTokens = tokens.filter { token ->
            token.length > 1 && !REMOVAL_TOKENS.contains(token)
        }
        
        // 5. Rejoin
        var result = filteredTokens.joinToString(" ").trim()
        
        // Return original if normalization wiped it out (e.g. valid name was "INC")
        // But capitalize words for display
        return if (result.isBlank()) {
            rawName.trim()
        } else {
            // Capitalize first letter of each word
            result.split(" ").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
        }
    }
}
