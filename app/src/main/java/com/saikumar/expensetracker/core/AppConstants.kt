package com.saikumar.expensetracker.core

object AppConstants {
    
    // Category Names
    object Categories {
        const val SALARY = "Salary"
        const val CASHBACK = "Cashback"
        const val P2P_TRANSFERS = "P2P Transfers"
        const val OTHER_INCOME = "Other Income"
        const val CREDIT_BILL_PAYMENTS = "Credit Bill Payments"
        const val MUTUAL_FUNDS = "Mutual Funds"
        const val RECURRING_DEPOSITS = "Recurring Deposits"
        const val UNCATEGORIZED = "Uncategorized"
        const val MISCELLANEOUS = "Miscellaneous"
        const val SELF_TRANSFER = "Self Transfer"
    }
    
    // Keyword Fragments (for fuzzy matching)
    object Keywords {
        const val CASHBACK_REWARDS = "Cashback / Rewards"
        const val CREDIT_BILL = "Credit Bill"
        const val CREDIT_CARD = "Credit Card"
        const val STATEMENT = "Statement"
    }
}
