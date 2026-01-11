package com.saikumar.expensetracker.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Pattern matching type for categorization rules.
 * Ordered by confidence (highest first).
 */
enum class PatternType {
    UPI_ID,           // swiggy@paytm (99% confidence)
    MERCHANT_NAME,    // SWIGGY (95% confidence)
    NEFT_REFERENCE,   // Full NEFT ref DEUTN52025... (85% confidence)
    NEFT_BANK_CODE,   // Bank code prefix DEUT (80% confidence) - for matching similar
    PAYEE_NAME        // John Doe (75% confidence)
}

/**
 * A user-defined rule for automatically categorizing transactions.
 * Rules match transaction identifiers (UPI ID, merchant, etc.) to categories.
 */
@Entity(
    tableName = "categorization_rules",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["pattern"], unique = true),
        Index(value = ["categoryId"]),
        Index(value = ["patternType"])
    ]
)
data class CategorizationRule(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    
    /** The match pattern (e.g., "swiggy@paytm", "AMAZON") */
    val pattern: String,
    
    /** Type of pattern for matching strategy */
    val patternType: PatternType,
    
    /** Target category ID for matched transactions */
    val categoryId: Long,
    
    /** Human-readable name for display (e.g., "Swiggy Orders") */
    val displayName: String,
    
    /** When this rule was created */
    val createdAt: Long = System.currentTimeMillis(),
    
    /** Is this rule active? (false = soft-deleted) */
    val isActive: Boolean = true,
    
    /** Number of transactions matched by this rule */
    val matchCount: Int = 0
)
