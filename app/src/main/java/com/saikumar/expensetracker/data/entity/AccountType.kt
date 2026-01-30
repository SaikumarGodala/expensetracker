package com.saikumar.expensetracker.data.entity

enum class AccountType {
    SAVINGS,
    SALARY,
    CURRENT,
    CREDIT_CARD,
    WALLET,
    UPI,        // For UPI-only accounts (VPA without linked bank account)
    UNKNOWN
}
