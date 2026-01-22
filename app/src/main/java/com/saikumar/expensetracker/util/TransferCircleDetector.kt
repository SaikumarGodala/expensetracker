package com.saikumar.expensetracker.util

import com.saikumar.expensetracker.data.entity.CategoryType
import com.saikumar.expensetracker.data.repository.ExpenseRepository
import com.saikumar.expensetracker.data.repository.TransferCircleRepository
import kotlinx.coroutines.flow.first

/**
 * Data class for recipient suggestions
 */
data class RecipientSuggestion(
    val name: String,
    val transferCount: Int,
    val totalAmountPaisa: Long,
    val maxTransferAmountPaisa: Long,
    val lastTransferTimestamp: Long,
    val aliases: List<String> = emptyList()
)

/**
 * Detector to auto-suggest potential Transfer Circle members
 * based on transaction patterns
 */
class TransferCircleDetector(
    private val expenseRepository: ExpenseRepository,
    private val transferCircleRepository: TransferCircleRepository
) {
    
    companion object {
        const val MIN_TRANSFER_COUNT = 3
        const val MIN_AMOUNT_PAISA = 100000L // ₹1,000
    }
    
    /**
     * Detect potential circle members based on transaction history
     * Returns recipients with 3+ transfers of ₹1,000+
     */
    suspend fun detectPotentialMembers(): List<RecipientSuggestion> {
        return detectCandidates(MIN_TRANSFER_COUNT, MIN_AMOUNT_PAISA, excludeIgnored = true)
    }

    /**
     * Detect all potential candidates (even single transfer)
     */
    suspend fun detectAllCandidates(): List<RecipientSuggestion> {
        return detectCandidates(1, 0L, excludeIgnored = false)
    }
    
    private suspend fun detectCandidates(minCount: Int, minAmount: Long, excludeIgnored: Boolean): List<RecipientSuggestion> {
        // Get all transactions
        val allTransactions = expenseRepository.getTransactionsInPeriod(0L, Long.MAX_VALUE).first()
        
        // Determine exclusion list
        val existingNames: Set<String> = if (excludeIgnored) {
            // Exclude Active + Ignored
            val namesList = transferCircleRepository.getAllRecipientNames().first()
            namesList.map { it.lowercase() }.toSet()
        } else {
            // Only exclude Active members (Show Ignored ones in list)
            val members = transferCircleRepository.getAllMembers().first()
            members.map { it.recipientName.lowercase() }.toSet()
        }
        
        // Group by recipient and analyze
        val recipientStats = mutableMapOf<String, MutableList<Pair<Long, Long>>>() // name -> List<(amount, timestamp)>
        
        allTransactions.forEach { txn ->
            val category = txn.category.name
            val type = txn.transaction.transactionType
            
            // Check if this appears to be a P2P transfer
            // 1. Category is explicitly "P2P Transfers"
            // 2. Or entity type is PERSON
            // 3. Or it was uncategorized/miscellaneous but has a valid name that looks like a person
            val isPotentialP2P = category == "P2P Transfers" || 
                                 txn.transaction.entityType == com.saikumar.expensetracker.data.entity.EntityType.PERSON ||
                                 (type == com.saikumar.expensetracker.data.entity.TransactionType.TRANSFER && category != "Self Transfer")

            if (isPotentialP2P) {
                val name = txn.transaction.merchantName
                if (!name.isNullOrBlank() && name.length >= 3) {
                     // Filter out obvious non-person names if needed (or rely on user validation)
                     // Avoid common keywords if they leaked into merchantName
                     val invalidNames = setOf("UPI", "TRANSFER", "PAYMENT", "BANK", "IMPS", "NEFT")
                     if (!invalidNames.contains(name.uppercase())) {
                         recipientStats.getOrPut(name) { mutableListOf() }
                             .add(txn.transaction.amountPaisa to txn.transaction.timestamp)
                     }
                }
            } else {
                // FALLBACK: Try extracting from note if not identified as P2P otherwise
                // This handles cases where category might be wrong but note has "paid X"
                 val note = txn.transaction.note ?: ""
                 val extractedName = extractRecipientName(note)
                 if (extractedName != null) {
                      recipientStats.getOrPut(extractedName) { mutableListOf() }
                         .add(txn.transaction.amountPaisa to txn.transaction.timestamp)
                 }
            }
        }

        
        // Merge similar names (e.g. "Rajesh Kumar" and "Rajesh K")
        val mergedStats = mergeSimilarNames(recipientStats)
        
        // Filter and create suggestions
        return mergedStats
            .filterKeys { !existingNames.contains(it.lowercase()) } // Exclude existing members
            .mapNotNull { (name, data) ->
                val transfers = data.stats
                if (transfers.size >= minCount) {
                    val maxAmount = transfers.maxOf { it.first }
                    if (maxAmount >= minAmount) {
                        RecipientSuggestion(
                            name = name,
                            transferCount = transfers.size,
                            totalAmountPaisa = transfers.sumOf { it.first },
                            maxTransferAmountPaisa = maxAmount,
                            lastTransferTimestamp = transfers.maxOf { it.second },
                            aliases = data.aliases
                        )
                    } else null
                } else null
            }
            .sortedByDescending { it.totalAmountPaisa }
    }
    
    private data class MergedData(
        val stats: MutableList<Pair<Long, Long>>,
        val aliases: MutableList<String>
    )
    
    /**
     * Merge statistics for names that appear to be the same person
     */
    private fun mergeSimilarNames(rawStats: Map<String, MutableList<Pair<Long, Long>>>): Map<String, MergedData> {
        // Sort keys by length descending (prefer longer names as canonical)
        val sortedKeys = rawStats.keys.sortedByDescending { it.length }
        val merged = mutableMapOf<String, MergedData>()

        for (key in sortedKeys) {
            // Find if this key matches an existing canonical key
            var matchedCanonical: String? = null
            for (canonical in merged.keys) {
                if (isSamePerson(canonical, key)) {
                    matchedCanonical = canonical
                    break
                }
            }
            
            if (matchedCanonical != null) {
                merged[matchedCanonical]?.stats?.addAll(rawStats[key]!!)
                merged[matchedCanonical]?.aliases?.add(key)
            } else {
                merged[key] = MergedData(
                    stats = ArrayList(rawStats[key]!!),
                    aliases = mutableListOf(key)
                )
            }
        }
        return merged
    }

    /**
     * Check if two names likely refer to the same person.
     * @param longName The longer/canonical name (e.g. "Rajesh Kumar")
     * @param shortName The shorter/variant name (e.g. "Rajesh K")
     */
    private fun isSamePerson(longName: String, shortName: String): Boolean {
        val n1 = longName.uppercase().replace(Regex("[^A-Z ]"), " ").trim()
        val n2 = shortName.uppercase().replace(Regex("[^A-Z ]"), " ").trim()
        
        if (n1 == n2) return true
        if (n1.isEmpty() || n2.isEmpty()) return false
        
        // Split into tokens
        val tokens1 = n1.split("\\s+".toRegex())
        val tokens2 = n2.split("\\s+".toRegex())
        
        // If the short name is just one word
        if (tokens2.size == 1) {
            // It must match the first word of long name exactly OR be a known nickname?
            // "Rajesh" matches "Rajesh Kumar" -> Yes
            // "Kumar" matches "Rajesh Kumar" -> Risky (Kumar is common surname). 
            // Safer: Match first word.
            return tokens1.isNotEmpty() && tokens1[0] == tokens2[0]
        }
        
        // If multiple words, check token overlap
        // "Rajesh K" vs "Rajesh Kumar"
        // T1: RAJESH, KUMAR
        // T2: RAJESH, K
        
        var matches = 0
        var i1 = 0
        var i2 = 0
        
        while (i1 < tokens1.size && i2 < tokens2.size) {
            val t1 = tokens1[i1]
            val t2 = tokens2[i2]
            
            if (t1 == t2) {
                matches++
                i1++
                i2++
            } else if (t1.startsWith(t2) || t2.startsWith(t1)) {
                // Prefix match (K vs KUMAR)
                matches++
                i1++
                i2++
            } else {
                // Mismatch - try skipping one in long name (e.g. middle name missing)
                // "Rajesh Kumar Reddy" vs "Rajesh Reddy"
                i1++
            }
        }
        
        // Rule: All tokens in short name must have been matched
        // Or at least a high percentage?
        // Let's require all tokens in short name to find a 'mate' in long name (order preserved-ish)
        // My simple loop above skips in T1 only.
        
        // Let's use a simpler heuristic for stability:
        // Short name tokens must all be present (or be prefixes) in Long name tokens
        return tokens2.all { t2 ->
            tokens1.any { t1 -> t1 == t2 || (t1.length > t2.length && t1.startsWith(t2)) }
        }
    }
    
    /**
     * Extract recipient name from transaction note
     * Looks for patterns like "to Rajesh", "paid Amit", etc.
     */
    private fun extractRecipientName(note: String): String? {
        if (note.isBlank()) return null
        
        val lowerNote = note.lowercase()
        
        // Common patterns
        val patterns = listOf(
            "to\\s+([a-z]+(?:\\s+[a-z]+)?)".toRegex(),
            "paid\\s+([a-z]+(?:\\s+[a-z]+)?)".toRegex(),
            "transfer\\s+to\\s+([a-z]+(?:\\s+[a-z]+)?)".toRegex(),
            "sent\\s+to\\s+([a-z]+(?:\\s+[a-z]+)?)".toRegex()
        )
        
        patterns.forEach { pattern ->
            pattern.find(lowerNote)?.let { match ->
                val name = match.groupValues[1].trim()
                if (name.length >= 3) { // Minimum 3 characters for a name
                    return name.split(" ")
                        .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
                }
            }
        }
        
        return null
    }
}
