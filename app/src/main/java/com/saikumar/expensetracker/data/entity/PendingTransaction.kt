package com.saikumar.expensetracker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index
import androidx.room.ForeignKey

/**
 * Represents a future-dated or pending transaction (Standing Instructions, Mandates, Upcoming Bills).
 * 
 * CRITICAL FINANCIAL INVARIANT:
 * These records must NOT be in the main 'transactions' table to prevent them from
 * affecting current balance/spending totals. They are promoted to 'transactions'
 * only when they are actually realized/debited.
 */
@Entity(
    tableName = "pending_transactions",
    foreignKeys = [
        ForeignKey(
            entity = SmsRaw::class,
            parentColumns = ["rawSmsId"],
            childColumns = ["rawSmsId"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["dueDate"]),
        Index(value = ["smsHash"], unique = true),
        Index(value = ["rawSmsId"])
    ]
)
data class PendingTransaction(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    
    /** When this payment is expected to happen */
    val dueDate: Long,
    
    /** Original SMS timestamp */
    val createdDate: Long,
    
    val amountPaisa: Long,
    val merchantName: String?,
    val fullSmsBody: String,
    
    /** Hash for deduplication */
    val smsHash: String,
    
    /** Foreign key to SmsRaw */
    val rawSmsId: Long? = null,
    
    /** Status: PENDING, PROCESSED, CANCELLED */
    val status: String = "PENDING",
    
    /** ID of the realized transaction in 'transactions' table (if processed) */
    val realizedTransactionId: Long? = null,
    
    val note: String? = null
)
