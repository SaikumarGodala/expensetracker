package com.saikumar.expensetracker.data.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A bank-reported account balance captured from an SMS ("Avl bal INR 1,54,162.75",
 * "New bal: INR.3,25,425.00"). The bank states the truth after each transaction; comparing
 * consecutive snapshots against the ledger's transactions in between is a self-audit that
 * catches missed or double-counted transactions automatically.
 */
@Entity(
    tableName = "balance_snapshots",
    indices = [
        Index(value = ["smsHash"], unique = true),
        Index(value = ["accountLast4", "timestamp"])
    ]
)
data class BalanceSnapshot(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** Last 4 digits of the account the balance belongs to */
    val accountLast4: String,

    /** Bank-reported balance, in paisa */
    val balancePaisa: Long,

    /** When the SMS (and therefore the balance) is from */
    val timestamp: Long,

    /** Hash of the source SMS - unique index makes re-scans idempotent */
    val smsHash: String
)
