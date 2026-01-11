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
        )
    ],
    indices = [Index(value = ["categoryId"]), Index(value = ["timestamp"]), Index(value = ["smsHash"])]
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
    
    /** Whether this is a transfer between own accounts (legacy, use transactionType=TRANSFER) */
    val isSelfTransfer: Boolean = false,
    
    /** Whether this was detected as salary (legacy, use transactionType=INCOME) */
    val isSalaryCredit: Boolean = false,
    
    /** User explicitly marked this as income */
    val isIncomeManuallyIncluded: Boolean = false,
    
    /** SHA-256 hash of SMS body for deduplication (first 16 chars) */
    val smsHash: String? = null,
    
    /** Extracted merchant name from SMS */
    val merchantName: String? = null,
    
    /** Cleaned SMS snippet for display (excludes noise like IDs, hashes) */
    val smsSnippet: String? = null,
    
    /** Manual classification override: "INCOME", "EXPENSE", "NEUTRAL", or null for auto */
    val manualClassification: String? = null,
    
    /** Transaction reference number (UPI ref, NEFT ref, RRN, etc.) */
    val referenceNo: String? = null,
    
    /** Whether this transaction was reversed/refunded */
    val isReversal: Boolean = false,
    
    /** Whether this is identified as a subscription/recurring payment */
    val isSubscription: Boolean = false
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
