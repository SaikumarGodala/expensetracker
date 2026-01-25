package com.saikumar.expensetracker.domain

import com.saikumar.expensetracker.data.db.MerchantMemoryDao
import com.saikumar.expensetracker.data.entity.MerchantMemory

/**
 * Manages "Merchant Memory" - learning categories from recurring merchants.
 */
class MerchantManager(
    private val merchantMemoryDao: MerchantMemoryDao
) {

    /**
     * Get learned category for a merchant.
     */
    suspend fun getLearnedCategory(normalizedMerchant: String): MerchantMemory? {
        return merchantMemoryDao.getByMerchant(normalizedMerchant)
    }

    /**
     * Record or update a merchant occurrence.
     */
    suspend fun recordOccurrence(
        merchantName: String,
        categoryId: Long,
        transactionType: String?,
        timestamp: Long
    ) {
        val normalized = merchantName.uppercase().trim()
        val existing = merchantMemoryDao.getByMerchant(normalized)

        if (existing != null) {
            merchantMemoryDao.incrementOccurrence(normalized, timestamp)
        } else {
            val newMemory = MerchantMemory(
                normalizedMerchant = normalized,
                originalMerchant = merchantName,
                categoryId = categoryId,
                occurrenceCount = 1,
                firstSeenTimestamp = timestamp,
                lastSeenTimestamp = timestamp,
                isLocked = false,
                userConfirmed = false,
                transactionType = transactionType
            )
            merchantMemoryDao.insert(newMemory)
        }
    }

    /**
     * User confirms/overrides a merchant mapping, locking it.
     */
    suspend fun confirmMapping(
        merchantName: String,
        categoryId: Long,
        transactionType: String?,
        timestamp: Long
    ) {
        val normalized = merchantName.uppercase().trim()
        val existing = merchantMemoryDao.getByMerchant(normalized)

        if (existing != null) {
            merchantMemoryDao.userConfirmMapping(normalized, categoryId, timestamp, transactionType)
        } else {
            val newMemory = MerchantMemory(
                normalizedMerchant = normalized,
                originalMerchant = merchantName,
                categoryId = categoryId,
                occurrenceCount = 1,
                firstSeenTimestamp = timestamp,
                lastSeenTimestamp = timestamp,
                isLocked = true,
                userConfirmed = true,
                transactionType = transactionType
            )
            merchantMemoryDao.insert(newMemory)
        }
    }
}
