package com.saikumar.expensetracker.data.entity

/**
 * Represents the type of financial transaction.
 * This is the primary classification that determines how the transaction
 * affects financial calculations.
 * 
 * CRITICAL: Transaction nature (nature) must be determined BEFORE category assignment.
 * Order of detection: Direction → Account Type → Nature → Category
 */
enum class TransactionType {
    /** Regular income: salary, freelance, interest, dividends */
    INCOME,
    
    /** Regular spending: purchases, bills, subscriptions */
    EXPENSE,
    
    /** Movement between own accounts: bank to wallet, savings to current */
    TRANSFER,
    
    /** Credit card bill payment: settling liability, not an expense */
    LIABILITY_PAYMENT,
    
    /** Cashback or rewards credited to account - positive adjustment */
    CASHBACK,
    
    /** Investment outflow: buying stocks, mutual funds, crypto */
    INVESTMENT_OUTFLOW,
    
    /** Pending transaction - standing instruction alert, not yet debited */
    PENDING,
    
    /** Transaction should be ignored in analytics */
    IGNORE
}
