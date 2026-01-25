package com.saikumar.expensetracker.domain

import com.saikumar.expensetracker.data.db.TransactionDao

/**
 * Domain service for detecting duplicate transactions.
 * Implements "Waterfall" detection logic:
 * 1. Exact SMS Hash
 * 2. Reference Number + Amount
 * 3. Fuzzy Match (Time + Amount + Context)
 */
class DuplicateDetector(
    private val transactionDao: TransactionDao
) {

    data class CheckResult(
        val isDuplicate: Boolean,
        val tier: Tier?,
        val confidence: Double,
        val reason: String,
        val matchedTransactionId: Long? = null
    )

    enum class Tier {
        EXACT_HASH,       // Tier 1: Byte-for-byte identical SMS
        REFERENCE_MATCH,  // Tier 2: Same reference number + amount
        FUZZY_MATCH       // Tier 3: Amount + time + context
    }

    suspend fun check(
        smsHash: String,
        referenceNo: String?,
        amountPaisa: Long,
        timestamp: Long,
        merchantName: String? = null,
        accountNumberLast4: String? = null
    ): CheckResult {
        // TIER 1: Exact SMS Hash Match (99.9% confidence)
        if (transactionDao.existsBySmsHash(smsHash)) {
            return CheckResult(
                isDuplicate = true,
                tier = Tier.EXACT_HASH,
                confidence = 0.999,
                reason = "Exact SMS hash match"
            )
        }

        // TIER 2: Reference Number + Amount (95% confidence)
        if (referenceNo != null && referenceNo.isNotBlank()) {
            if (transactionDao.existsByReferenceAndAmount(referenceNo, amountPaisa)) {
                return CheckResult(
                    isDuplicate = true,
                    tier = Tier.REFERENCE_MATCH,
                    confidence = 0.95,
                    reason = "Reference number '$referenceNo' + amount match"
                )
            }
        }

        // TIER 3: Fuzzy Match with Context (70-85% confidence)
        val timeWindow = 15 * 60 * 1000L // ±15 minutes
        val windowStart = timestamp - timeWindow
        val windowEnd = timestamp + timeWindow

        val candidates = transactionDao.findPotentialDuplicates(amountPaisa, windowStart, windowEnd)

        for (candidate in candidates) {
            var confidenceBoost = 0.0
            val matchReasons = mutableListOf<String>()

            // Context 1: Merchant name match (adds 30% confidence)
            if (merchantName != null && candidate.merchantName != null) {
                val normalized1 = merchantName.uppercase().trim()
                val normalized2 = candidate.merchantName.uppercase().trim()
                if (normalized1 == normalized2 || 
                    normalized1.contains(normalized2) || 
                    normalized2.contains(normalized1)) {
                    confidenceBoost += 0.30
                    matchReasons.add("merchant match")
                }
            }

            // Context 2: Account number (last 4 digits) match (adds 25% confidence)
            if (accountNumberLast4 != null && candidate.accountNumberLast4 != null &&
                accountNumberLast4 == candidate.accountNumberLast4) {
                confidenceBoost += 0.25
                matchReasons.add("same account")
            }

            // Base confidence for amount + time match: 40%
            val totalConfidence = 0.40 + confidenceBoost

            if (totalConfidence >= 0.70) {
                val timeDiffMinutes = kotlin.math.abs(timestamp - candidate.timestamp) / 60000
                return CheckResult(
                    isDuplicate = true,
                    tier = Tier.FUZZY_MATCH,
                    confidence = totalConfidence,
                    reason = "Same amount \u20B9${amountPaisa/100} within ${timeDiffMinutes}min + ${matchReasons.joinToString(" + ")}",
                    matchedTransactionId = candidate.id
                )
            }
        }

        return CheckResult(
            isDuplicate = false,
            tier = null,
            confidence = 0.0,
            reason = "No duplicate detected"
        )
    }
}
