package com.saikumar.expensetracker.util

import com.saikumar.expensetracker.data.entity.Category
import com.saikumar.expensetracker.data.entity.CategoryType
import com.saikumar.expensetracker.data.entity.Transaction
import com.saikumar.expensetracker.data.entity.TransactionType
import com.saikumar.expensetracker.data.entity.EntityType
import com.saikumar.expensetracker.data.entity.TransactionStatus

/**
 * Single source of truth for determining transaction type during manual edits.
 * 
 * This resolver consolidates the duplicate logic from multiple ViewModels
 * (DashboardViewModel, FilteredTransactionsViewModel, MonthlyOverviewViewModel)
 * to ensure consistent transaction type determination across the app.
 * 
 * **Finance-First Rules:**
 * 1. User's explicit classification (isSelfTransfer, manualClassification) takes priority
 * 2. Category type (INCOME vs EXPENSE) is secondary
 * 3. Special category names (Credit Bill) override category type
 * 4. Default to EXPENSE if nothing else matches
 */
object TransactionTypeResolver {
    
    /**
     * Determine the correct transaction type for a manual edit/update.
     * 
     * @param transaction The transaction being edited (optional, only used for context if needed)
     * @param manualClassification User's explicit classification ("INCOME", "EXPENSE", "NEUTRAL", or null)
     * @param isSelfTransfer Whether user marked this as a self-transfer
     * @param newCategory The category being assigned (may be null)
     * @return The appropriate TransactionType based on all inputs
     */
    fun determineTransactionType(
        transaction: Transaction?,
        manualClassification: String?,
        isSelfTransfer: Boolean,
        newCategory: Category?
    ): TransactionType {
        
        // Rule 1: Self-transfer flag overrides everything
        if (isSelfTransfer) {
            return TransactionType.TRANSFER
        }
        
        // Rule 2: User's explicit classification
        when (manualClassification) {
            "INCOME" -> return TransactionType.INCOME
            "EXPENSE" -> return TransactionType.EXPENSE
            "NEUTRAL" -> return TransactionType.TRANSFER
            "LIABILITY_PAYMENT" -> return TransactionType.LIABILITY_PAYMENT
            "REFUND" -> return TransactionType.REFUND
            "INVESTMENT" -> return TransactionType.INVESTMENT_OUTFLOW
            "CASHBACK" -> return TransactionType.CASHBACK
            "PENDING" -> return TransactionType.PENDING
            "IGNORE" -> return TransactionType.IGNORE
        }
        
        // Rule 3: Special category names (Finance-First Invariant)
        // Credit card bill payments should always be LIABILITY_PAYMENT
        if (newCategory?.name?.contains("Credit Bill", ignoreCase = true) == true ||
            newCategory?.name?.contains("Credit Card", ignoreCase = true) == true) {
            return TransactionType.LIABILITY_PAYMENT
        }
        
        // Rule 4: Category type
        if (newCategory != null) {
            return when (newCategory.type) {
                CategoryType.INCOME -> TransactionType.INCOME
                // All other category types map to EXPENSE
                CategoryType.FIXED_EXPENSE,
                CategoryType.VARIABLE_EXPENSE,
                CategoryType.INVESTMENT,
                CategoryType.VEHICLE -> TransactionType.EXPENSE
            }
        }
        
        // Rule 5: Fallback to EXPENSE (most common case)
        return TransactionType.EXPENSE
    }
    
    /**
     * Simplified version for cases where category is known to be non-null.
     */
    fun determineTransactionType(
        manualClassification: String?,
        isSelfTransfer: Boolean,
        category: Category
    ): TransactionType {
        return determineTransactionType(
            transaction = null,
            manualClassification = manualClassification,
            isSelfTransfer = isSelfTransfer,
            newCategory = category
        )
    }
}
