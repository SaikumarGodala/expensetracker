package com.saikumar.expensetracker.data.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a financial transaction.
 * 
 * FINANCIAL CORRECTNESS NOTES:
 * - amountPaisa stores amount in smallest currency unit (1 rupee = 100 paisa)
 * - timestamp stores UTC epoch milliseconds for timezone-independent dates
 * - transactionType determines how this affects financial calculations
 */
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_DEFAULT
        ),
        ForeignKey(
            entity = SmsRaw::class,
            parentColumns = ["rawSmsId"],
            childColumns = ["rawSmsId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["categoryId"]),
        Index(value = ["timestamp"]),
        // CRITICAL FIX 2: UNIQUE constraint prevents duplicate SMS from creating duplicate transactions
        // Idempotency is enforced at the DB level - duplicate inserts are ignored
        Index(value = ["smsHash"], unique = true),
        Index(value = ["referenceNo"]),
        Index(value = ["amountPaisa", "timestamp"]), // P1 Performance: Composite index for fuzzy duplicate search
        Index(value = ["merchantName"]), // P1 Performance: For similar transaction lookup
        Index(value = ["rawSmsId"]), // P1 Provenance: FK index
        Index(value = ["upiId"]), // Performance: For UPI-based similar transaction lookup
        Index(value = ["status"]), // Performance: For filtering by transaction status
        Index(value = ["transactionType"]) // Performance: For type-based filtering
    ]
)
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    
    /** Amount in paisa (smallest currency unit). ₹100.50 = 10050 */
    val amountPaisa: Long,
    
    /** Foreign key to Category. SET_DEFAULT on category deletion. */
    val categoryId: Long,
    
    /** UTC epoch milliseconds. Timezone-independent storage. */
    val timestamp: Long,
    
    /** User-provided note or auto-generated description */
    val note: String? = null,
    
    /** How this transaction was created */
    val source: TransactionSource = TransactionSource.MANUAL,
    
    /** The type of account this transaction occurred from */
    val accountType: AccountType = AccountType.UNKNOWN,
    
    /** Optional link to Account entity */
    val accountId: Long? = null,
    
    /** Primary classification for financial calculations */
    val transactionType: TransactionType = TransactionType.EXPENSE,
    
    /** SHA-256 hash of SMS body for deduplication (first 16 chars) */
    val smsHash: String? = null,
    
    /** Extracted merchant name from SMS */
    val merchantName: String? = null,
    
    /** Cleaned SMS snippet for display (excludes noise like IDs, hashes) */
    val smsSnippet: String? = null,
    
    /** Full raw SMS body for detailed viewing (unprocessed) */
    val fullSmsBody: String? = null,
    
    /** Foreign key to SmsRaw. RESTRICT on delete to preserve provenance. */
    val rawSmsId: Long? = null,
    
    /** Manual classification override: "INCOME", "EXPENSE", "NEUTRAL", or null for auto */
    val manualClassification: String? = null,
    
    /** Transaction reference number (UPI ref, NEFT ref, RRN, etc.) */
    val referenceNo: String? = null,
    
    /** Whether this transaction was reversed/refunded */
    val isReversal: Boolean = false,
    
    /** Whether this is identified as a subscription/recurring payment */
    val isSubscription: Boolean = false,

    /** Lifecycle status of the transaction (INTENT, PENDING, COMPLETED) */
    val status: TransactionStatus = TransactionStatus.COMPLETED,

    /** Inferred entity type of the counterparty (BUSINESS, PERSON) */
    val entityType: EntityType = EntityType.UNKNOWN,

    /** Whether this transaction meets strict expense eligibility rules */
    val isExpenseEligible: Boolean = true,
    
    /** Last 4 digits of the bank account this transaction belongs to (from SMS) */
    val accountNumberLast4: String? = null,

    /** UPI Virtual Payment Address (VPA) of the counterparty (e.g., user@paytm) */
    val upiId: String? = null,

    /** Timestamp when this transaction was soft-deleted (null if active) */
    val deletedAt: Long? = null,
    
    /**
     * Classification confidence score (0-100).
     * - 100: User manually verified/categorized
     * - 80-99: High confidence (exact merchant match, strong pattern)
     * - 50-79: Medium confidence (partial match, fallback category)
     * - 0-49: Low confidence (uncategorized, generic fallback)
     * Lower scores may trigger "Needs Review" UI indicators.
     */
    val confidenceScore: Int = 75 // Default to medium-high for auto-classified
) {
    companion object {
        /** Default category ID for uncategorized transactions */
        const val DEFAULT_CATEGORY_ID = 1L
    }
}

enum class TransactionSource {
    MANUAL,
    SMS
}
