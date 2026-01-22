package com.saikumar.expensetracker.domain

import com.saikumar.expensetracker.data.entity.Category
import com.saikumar.expensetracker.data.entity.CategoryType
import com.saikumar.expensetracker.data.entity.TransactionType

/**
 * Validates transaction data integrity and business rules.
 * Use this to prevent invalid states in the database.
 */
object TransactionValidator {

    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val reason: String) : ValidationResult()
    }

    /**
     * Checks if the Transaction Type is valid for the given Category.
     * 
     * Rules:
     * - INCOME Category -> Must be INCOME or TRANSFER (if incoming) or REFUND (if incoming)
     * - EXPENSE Category -> Must be EXPENSE or LIABILITY_PAYMENT
     * - P2P Transfers -> Must be TRANSFER
     * 
     * To keep it simple for V1:
     * - INCOME Category -> INCOME
     * - EXPENSE Category -> EXPENSE
     * - P2P Transfers -> TRANSFER
     * - Bill Payments -> EXPENSE / LIABILITY_PAYMENT
     */
    fun validateCategoryTypeStart(transactionType: TransactionType, category: Category): ValidationResult {
        // Map detailed category types to allowed transaction types
        val mapResult = when (category.type) {
            CategoryType.INCOME -> {
                if (transactionType == TransactionType.INCOME || transactionType == TransactionType.REFUND) ValidationResult.Valid 
                else ValidationResult.Invalid("Income categories can only be used for Income transactions.")
            }
            CategoryType.FIXED_EXPENSE, 
            CategoryType.VARIABLE_EXPENSE, 
            CategoryType.VEHICLE -> {
                if (transactionType == TransactionType.EXPENSE || 
                    transactionType == TransactionType.LIABILITY_PAYMENT ||
                    transactionType == TransactionType.TRANSFER) ValidationResult.Valid 
                else ValidationResult.Invalid("Expense categories valid for Expense, Liability Payment, or Transfer.")
            }
            CategoryType.INVESTMENT -> {
                if (transactionType == TransactionType.EXPENSE || 
                    transactionType == TransactionType.INVESTMENT_OUTFLOW ||
                    transactionType == TransactionType.TRANSFER) ValidationResult.Valid 
                else ValidationResult.Invalid("Investment categories valid for Expense, Investment, or Transfer.")
            }
            CategoryType.IGNORE, CategoryType.STATEMENT -> {
                // IGNORE and STATEMENT categories are flexible but generally map to IGNORE/STATEMENT types
                ValidationResult.Valid
            }
            CategoryType.LIABILITY -> {
                // LIABILITY categories should only be used for LIABILITY_PAYMENT transactions
                if (transactionType == TransactionType.LIABILITY_PAYMENT) ValidationResult.Valid
                else ValidationResult.Invalid("Liability categories can only be used for CC Bill Payments.")
            }
        }
        return mapResult
    }

    /**
     * Returns the allowed Transaction Types for a given Category.
     * Useful for filtering UI dropdowns.
     */
    fun getAllowedTypes(category: Category): List<TransactionType> {
        return when (category.type) {
            CategoryType.INCOME -> listOf(TransactionType.INCOME, TransactionType.REFUND)
            CategoryType.FIXED_EXPENSE,
            CategoryType.VARIABLE_EXPENSE,
            CategoryType.VEHICLE -> listOf(TransactionType.EXPENSE, TransactionType.LIABILITY_PAYMENT, TransactionType.TRANSFER)
            CategoryType.INVESTMENT -> listOf(TransactionType.EXPENSE, TransactionType.INVESTMENT_OUTFLOW, TransactionType.TRANSFER)
            CategoryType.IGNORE -> listOf(TransactionType.IGNORE)
            CategoryType.STATEMENT -> listOf(TransactionType.STATEMENT)
            CategoryType.LIABILITY -> listOf(TransactionType.LIABILITY_PAYMENT)
        }
    }
}
