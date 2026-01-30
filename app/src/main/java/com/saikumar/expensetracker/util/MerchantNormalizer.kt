package com.saikumar.expensetracker.util

import java.util.Locale

object MerchantNormalizer {
    private val REMOVAL_TOKENS = setOf(
        "inc", "llc", "store", "pvt", "ltd", "corp", "corporation",
        "limited", "private", "services", "solutions", "enterprise",
        "global", "technologies", "tech", "com", "net", "org",
        "payments", "pay", "upi", "india", "ind",
        "cf", "rzp", "payu", "pyu", "ccav", "billdesk", "fs", "in",
        "atos", "razorpay", "ecom", "retail", "merchants",
        "cas", "subsc", "subscription", "cy", "llp"
    )

    // Known merchant name mappings (for recognition and normalization)
    private val KNOWN_MERCHANTS = mapOf(
        // Food Delivery
        "swiggy" to "Swiggy",
        "zomato" to "Zomato",
        "zepto" to "Zepto",
        "zeptonow" to "Zepto",
        "blinkit" to "Blinkit",
        "instamart" to "Instamart",
        "instama" to "Instamart",
        "bigbasket" to "BigBasket",
        "dunzo" to "Dunzo",
        "licious" to "Licious",
        "gokhana" to "Gokhana",

        // Shopping/E-commerce
        "amazon" to "Amazon",
        "flipkart" to "Flipkart",
        "myntra" to "Myntra",
        "ajio" to "Ajio",
        "nykaa" to "Nykaa",
        "meesho" to "Meesho",

        // Entertainment/Streaming
        "netflix" to "Netflix",
        "hotstar" to "Hotstar",
        "spotify" to "Spotify",
        "youtube" to "YouTube",
        "youtubegoogle" to "YouTube",
        "google play" to "Google Play",
        "googleplay" to "Google Play",
        "bookmyshow" to "BookMyShow",
        "pvrinox" to "PVR Inox",
        "gameon" to "Game On",
        "gameonlevel" to "Game On",
        "gameonlevelupyourf" to "Game On",

        // Travel
        "uber" to "Uber",
        "ola" to "Ola",
        "rapido" to "Rapido",
        "redbus" to "RedBus",
        "makemytrip" to "MakeMyTrip",
        "goibibo" to "Goibibo",
        "irctc" to "IRCTC",
        "cleartrip" to "Cleartrip",

        // Fintech/Payments
        "cred" to "CRED",
        "cred club" to "CRED",
        "paytm" to "Paytm",
        "paytmqr" to "Paytm",
        "phonepe" to "PhonePe",
        "googlepay" to "Google Pay",
        "gpay" to "Google Pay",
        "amazonpay" to "Amazon Pay",
        "zerodha" to "Zerodha",
        "groww" to "Groww",
        "upstox" to "Upstox",
        "iccl" to "Indian Clearing Corp",

        // Utilities
        "airtel" to "Airtel",
        "jio" to "Jio",
        "tatapay" to "Tata Payments",
        "tatapayments" to "Tata Payments",
        "tatasky" to "Tata Sky",

        // Software/Tech
        "udemy" to "Udemy",
        "adobe" to "Adobe",
        "microsoft" to "Microsoft",
        "google" to "Google",
        "apple" to "Apple",
        "claude" to "Claude AI",
        "claude.ai" to "Claude AI",

        // Restaurants (common)
        "starbucks" to "Starbucks",
        "dominos" to "Dominos",
        "mcdonalds" to "McDonalds",
        "kfc" to "KFC",
        "subway" to "Subway",
        "burgerking" to "Burger King",
        "pizzahut" to "Pizza Hut",
        "mandiking" to "Mandi King",

        // Common truncations from Axis Bank
        "bundl" to "Bundle",
        "bundl techn" to "Bundle Technologies",
        "airtel paym" to "Airtel",
        "avenue supermar" to "Avenue Supermarts",
        "udemy subscript" to "Udemy",
        "adobe premiere" to "Adobe",
        "amazon pay in e" to "Amazon",
        "amazon india cy" to "Amazon"
    )

    fun normalize(rawName: String?): String? {
        if (rawName.isNullOrBlank()) return null

        var normalized = rawName.lowercase(Locale.getDefault()).trim()

        // 0. Check for known merchant name first (before any modification)
        for ((pattern, name) in KNOWN_MERCHANTS) {
            if (normalized == pattern || normalized.startsWith(pattern) || normalized.contains(pattern)) {
                return name
            }
        }

        // 0.5 Remove known gateway prefixes
        val prefixes = listOf("pyu*", "atos*", "upi-", "rzp*", "cas*", "payu*", "ccav*")
        for (prefix in prefixes) {
            if (normalized.startsWith(prefix)) {
                normalized = normalized.removePrefix(prefix)
                // Re-check known merchants after removing prefix
                for ((pattern, name) in KNOWN_MERCHANTS) {
                    if (normalized == pattern || normalized.startsWith(pattern)) {
                        return name
                    }
                }
            }
        }

        // Special case: paytmqr -> Paytm
        if (normalized.startsWith("paytmqr")) {
            return "Paytm"
        }

        // Strip suffix noise (e.g. .6603, .store)
        normalized = normalized.replace(Regex("\\.[a-z0-9]+$"), "")

        // Remove digits (SWIGGY8 -> swiggy)
        normalized = normalized.replace(Regex("[0-9]"), " ")

        // Remove punctuation
        normalized = normalized.replace(Regex("[^a-z ]"), " ")

        // Tokenize
        val tokens = normalized.split("\\s+".toRegex()).toMutableList()

        // Remove common noise tokens
        val filteredTokens = tokens.filter { token ->
            token.length > 1 && !REMOVAL_TOKENS.contains(token)
        }

        // Rejoin
        var result = filteredTokens.joinToString(" ").trim()

        // Final check: If result matches known merchant after cleanup
        val resultLower = result.lowercase()
        for ((pattern, name) in KNOWN_MERCHANTS) {
            if (resultLower == pattern || resultLower.startsWith(pattern)) {
                return name
            }
        }

        // Return original if normalization wiped it out
        return if (result.isBlank()) {
            rawName.trim()
        } else {
            // Capitalize first letter of each word
            result.split(" ").joinToString(" ") { it.replaceFirstChar { char -> char.uppercase() } }
        }
    }

    /**
     * Check if a normalized merchant name can be recognized back to a known merchant
     * Useful for verifying normalization quality
     */
    fun recognize(normalizedName: String?): String? {
        if (normalizedName.isNullOrBlank()) return null
        val lower = normalizedName.lowercase()

        for ((pattern, name) in KNOWN_MERCHANTS) {
            if (lower == pattern || lower.contains(pattern) || name.lowercase() == lower) {
                return name
            }
        }
        return normalizedName // Return as-is if not recognized
    }

    /**
     * Get the canonical merchant name for a given raw name
     * Returns the known merchant name if recognized, otherwise normalizes
     */
    fun getCanonicalName(rawName: String?): String? {
        val normalized = normalize(rawName) ?: return null
        return recognize(normalized) ?: normalized
    }
}
