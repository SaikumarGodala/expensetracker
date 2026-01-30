package com.saikumar.expensetracker.data.entity

import androidx.room.Entity
import androidx.room.PrimaryKey



@Entity(
    tableName = "user_accounts",
    primaryKeys = ["accountNumberLast4", "bankName", "accountType"]
)
data class UserAccount(
    val accountNumberLast4: String, // e.g., "2725"
    val bankName: String,          // e.g., "HDFC"
    val accountType: AccountType,
    val isMyAccount: Boolean = true,
    val alias: String? = null,     // e.g., "Salary Account"
    val accountHolderName: String? = null, // e.g., "GODALA SAIKUMAR REDDY" - discovered from NEFT
    val upiVpa: String? = null,    // e.g., "9876543210@ybl" or "saikumar@okaxis" - user's UPI VPA for self-transfer detection
    val detectedAt: Long = System.currentTimeMillis()
)
