package com.saikumar.expensetracker.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Merchant Dictionary Memory - Soft Learning System
 * 
 * Stores merchant→category mappings learned from transaction patterns.
 * When a merchant appears N times with the same category without user override,
 * the mapping is "locked" and used for future classifications.
 * 
 * This provides deterministic improvement without ML infrastructure.
 */
@Entity(
    tableName = "merchant_memory",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["categoryId"]), Index(value = ["normalizedMerchant"], unique = true)]
)
data class MerchantMemory(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    
    /** Normalized merchant name (uppercase, trimmed) for matching */
    val normalizedMerchant: String,
    
    /** Original merchant name as first seen */
    val originalMerchant: String,
    
    /** Learned category ID */
    val categoryId: Long,
    
    /** Number of times this merchant has been seen with this category */
    val occurrenceCount: Int = 1,
    
    /** Timestamp of first occurrence */
    val firstSeenTimestamp: Long,
    
    /** Timestamp of last occurrence */
    val lastSeenTimestamp: Long,
    
    /** Whether this mapping is locked (count >= threshold OR user confirmed) */
    val isLocked: Boolean = false,
    
    /** Whether user explicitly confirmed/set this mapping */
    val userConfirmed: Boolean = false,
    
    /** Transaction type associated with this merchant */
    val transactionType: String? = null
) {
    companion object {
        /** Number of occurrences required to auto-lock a merchant mapping */
        const val AUTO_LOCK_THRESHOLD = 3
    }
}
