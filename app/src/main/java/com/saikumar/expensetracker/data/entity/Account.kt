package com.saikumar.expensetracker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Represents a financial account (bank account, wallet, credit card).
 * Used for tracking which account transactions occur from/to.
 */
@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: AccountType,
    /** True for credit cards and other liability accounts */
    val isLiability: Boolean = false,
    /** Whether this is the default account for new transactions */
    val isDefault: Boolean = false
)
