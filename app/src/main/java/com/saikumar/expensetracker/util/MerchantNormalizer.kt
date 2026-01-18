package com.saikumar.expensetracker.util

import java.util.Locale

object MerchantNormalizer {
    private val REMOVAL_TOKENS = setOf(
        "inc", "llc", "store", "pvt", "ltd", "corp", "corporation", 
        "limited", "private", "services", "solutions", "enterprise", 
        "global", "technologies", "tech", "com", "net", "org",
        "payments", "pay", "upi", "india", "ind",
        "cf", "rzp", "payu", "ccav", "billdesk", "fs", "in"
    )

    fun normalize(rawName: String?): String? {
        if (rawName.isNullOrBlank()) return null

        // 1. Lowercase and trim
        var normalized = rawName.lowercase(Locale.getDefault()).trim()

        // Special case: paytmqr -> paytm
        if (normalized.startsWith("paytmqr")) {
             normalized = "paytm"
        }

        // 2. Remove digits (SWIGGY8 -> swiggy)
        normalized = normalized.replace(Regex("[0-9]"), "")

        // 3. Remove punctuation
        normalized = normalized.replace(Regex("[^a-z ]"), " ")

        // 4. Tokenize
        val tokens = normalized.split("\\s+".toRegex()).toMutableList()

        // 5. Remove common noise tokens
        val filteredTokens = tokens.filter { token ->
            token.length > 1 && !REMOVAL_TOKENS.contains(token)
        }
        
        // 6. Rejoin
        var result = filteredTokens.joinToString(" ").trim()
        
        // Return original if normalization wiped it out (e.g. valid name was "INC")
        return if (result.isBlank()) rawName.trim() else result
    }
}
