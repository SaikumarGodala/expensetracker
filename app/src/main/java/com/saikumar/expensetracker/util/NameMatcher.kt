package com.saikumar.expensetracker.util

/**
 * Fuzzy human-name equivalence matching.
 *
 * ORGANIZATION FIX: This logic previously existed as three separate,
 * slowly-diverging copy-pasted implementations:
 *   - CounterpartyExtractor.areNamesEquivalent (most complete: handled initials)
 *   - CategoryMapper.areNamesEquivalent (duplicate, missing initials handling)
 *   - SmsProcessor.areNamesEquivalent (identical duplicate of the CategoryMapper copy)
 *
 * Having three copies meant a fix to one (e.g. the initials-matching support)
 * silently never made it to the other two call sites, so self-transfer
 * detection, salary-source detection, and P2P trust-circle matching could
 * disagree with each other on the same pair of names. This is now the single
 * source of truth; all three call sites delegate here.
 */
object NameMatcher {

    /**
     * Determines whether [name1] and [name2] plausibly refer to the same person,
     * tolerating common real-world variations:
     * - Exact match (case-insensitive)
     * - One name fully contains the other (e.g. "SAIKUMAR" within "GODALA SAIKUMAR REDDY")
     * - Word order variations ("KUMAR SAIKUMAR" vs "SAIKUMAR KUMAR")
     * - Middle name / extra word variations ("GODALA SAIKUMAR" vs "GODALA SAIKUMAR REDDY")
     * - Initials ("S KUMAR" vs "SAIKUMAR")
     */
    fun areNamesEquivalent(name1: String, name2: String): Boolean {
        val lower1 = name1.trim().lowercase()
        val lower2 = name2.trim().lowercase()

        if (lower1.isEmpty() || lower2.isEmpty()) return false
        if (lower1 == lower2) return true

        // Full containment shortcut - only meaningful once names are long enough
        // to avoid trivial short-string false positives (e.g. "A" contains-of-anything).
        if (lower1.length >= 3 && lower2.length >= 3 && (lower1.contains(lower2) || lower2.contains(lower1))) {
            return true
        }

        val parts1 = lower1.split("\\s+".toRegex()).filter { it.isNotEmpty() }
        val parts2 = lower2.split("\\s+".toRegex()).filter { it.isNotEmpty() }
        if (parts1.isEmpty() || parts2.isEmpty()) return false

        // Extract significant parts (length >= 3, mostly letters)
        val significantParts1 = parts1.filter { it.length >= 3 && it.count { c -> c.isLetter() } >= 2 }
        val significantParts2 = parts2.filter { it.length >= 3 && it.count { c -> c.isLetter() } >= 2 }

        // If at least 2 significant parts match (regardless of order), likely same person
        val commonSignificant = significantParts1.intersect(significantParts2.toSet())
        if (commonSignificant.size >= 2) return true

        // Single significant match + initials matching (e.g. "S KUMAR" vs "SAIKUMAR REDDY")
        if (commonSignificant.size == 1) {
            val initials1 = parts1.filter { it.length == 1 }.toSet()
            val initials2 = parts2.filter { it.length == 1 }.toSet()

            val matches1to2 = initials1.any { initial -> significantParts2.any { it.startsWith(initial) } }
            val matches2to1 = initials2.any { initial -> significantParts1.any { it.startsWith(initial) } }

            if (matches1to2 || matches2to1) return true
        }

        // All parts of the shorter name are found (exact or initial-prefix) in the longer name
        val (shorterParts, longerParts) = if (parts1.size <= parts2.size) parts1 to parts2 else parts2 to parts1

        val allShorterPartsMatched = shorterParts.all { shortPart ->
            longerParts.any { longPart ->
                shortPart == longPart ||
                    (shortPart.length == 1 && longPart.startsWith(shortPart)) ||
                    (longPart.length == 1 && shortPart.startsWith(longPart)) ||
                    longPart.startsWith(shortPart)
            }
        }

        return allShorterPartsMatched && shorterParts.size >= 2
    }
}
