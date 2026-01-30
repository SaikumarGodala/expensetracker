package com.saikumar.expensetracker.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * TransactionLink: Persistent record of relationships between transactions
 * 
 * Use Cases:
 * - SELF_TRANSFER: Links debit/credit pair for money moving between own accounts
 * - REFUND: Links refund to original purchase
 * - CC_PAYMENT: Links credit card spend to bill payment
 * 
 * Benefits:
 * - Audit trail (who created, when, confidence score)
 * - User can break/recreate links manually
 * - Foundation for refund netting and CC reconciliation
 */
@Entity(
    tableName = "transaction_links",
    foreignKeys = [
        ForeignKey(
            entity = Transaction::class,
            parentColumns = ["id"],
            childColumns = ["primary_txn_id"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = Transaction::class,
            parentColumns = ["id"],
            childColumns = ["secondary_txn_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index("primary_txn_id"), 
        Index("secondary_txn_id"), 
        Index("link_type")
    ]
)
data class TransactionLink(
    @PrimaryKey(autoGenerate = true) 
    val id: Long = 0,
    
    /** Usually the outgoing/debit transaction */
    @ColumnInfo(name = "primary_txn_id") 
    val primaryTxnId: Long,
    
    /** Usually the incoming/credit transaction */
    @ColumnInfo(name = "secondary_txn_id") 
    val secondaryTxnId: Long,
    
    /** Type of relationship between transactions */
    @ColumnInfo(name = "link_type") 
    val linkType: LinkType,
    
    /** Confidence score 0-100 (only for AUTO links) */
    @ColumnInfo(name = "confidence_score") 
    val confidenceScore: Int,
    
    /** Whether system detected or user created */
    @ColumnInfo(name = "created_by") 
    val createdBy: LinkSource,
    
    /** Timestamp of link creation */
    @ColumnInfo(name = "created_at") 
    val createdAt: Long = System.currentTimeMillis()
)

enum class LinkType {
    /** Money moving between user's own accounts */
    SELF_TRANSFER,
    
    /** Refund for a previous purchase */
    REFUND,
    
    /** Credit card spend matched to bill payment */
    CC_PAYMENT
}

enum class LinkSource {
    /** System automatically detected and created link */
    AUTO,
    
    /** User manually created or confirmed link */
    MANUAL,

    /** System suggested, waiting for user confirmation */
    SUGGESTED
}
