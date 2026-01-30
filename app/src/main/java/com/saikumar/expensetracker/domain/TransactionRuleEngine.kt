package com.saikumar.expensetracker.domain

import com.saikumar.expensetracker.data.entity.Category
import com.saikumar.expensetracker.data.entity.CategoryType
import com.saikumar.expensetracker.data.entity.Transaction
import com.saikumar.expensetracker.data.entity.TransactionType
import com.saikumar.expensetracker.core.AppConstants

/**
 * centralized rule engine for transaction logic.
 * Consolidates validation, type resolution, and invariants from:
 * - TransactionValidator (Validation)
 * - TransactionTypeResolver (Resolution)
 * - SmsConstants (Invariants)
 */
object TransactionRuleEngine {

    sealed class ValidationResult {
        object Valid : ValidationResult()
        data class Invalid(val reason: String) : ValidationResult()
    }

    // =================================================================
    // 1. VALIDATION RULES (From TransactionValidator)
    // =================================================================

    /**
     * Checks if the Transaction Type is valid for the given Category.
     */
    fun validateCategoryType(transactionType: TransactionType, category: Category): ValidationResult {
        return when (category.type) {
            CategoryType.INCOME -> {
                if (transactionType == TransactionType.INCOME || 
                    transactionType == TransactionType.REFUND ||
                    transactionType == TransactionType.CASHBACK) ValidationResult.Valid 
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
                    transactionType == TransactionType.INVESTMENT_CONTRIBUTION ||
                    transactionType == TransactionType.PENSION ||
                    transactionType == TransactionType.TRANSFER) ValidationResult.Valid
                else ValidationResult.Invalid("Investment categories valid for Expense, Investment, Pension, or Transfer.")
            }
            CategoryType.IGNORE -> {
                if (transactionType == TransactionType.IGNORE) ValidationResult.Valid
                else ValidationResult.Invalid("Ignore categories can only be used for Ignore transactions.")
            }
            CategoryType.STATEMENT -> ValidationResult.Valid
            CategoryType.LIABILITY -> {
                if (transactionType == TransactionType.LIABILITY_PAYMENT) ValidationResult.Valid
                else ValidationResult.Invalid("Liability categories can only be used for CC Bill Payments.")
            }
            CategoryType.TRANSFER -> {
                if (transactionType == TransactionType.TRANSFER) ValidationResult.Valid
                else ValidationResult.Invalid("Transfer categories can only be used for Transfer transactions.")
            }
        }
    }

    fun getAllowedTypes(category: Category): List<TransactionType> {
        return when (category.type) {
            CategoryType.INCOME -> listOf(TransactionType.INCOME, TransactionType.REFUND, TransactionType.CASHBACK)
            CategoryType.FIXED_EXPENSE,
            CategoryType.VARIABLE_EXPENSE,
            CategoryType.VEHICLE -> listOf(TransactionType.EXPENSE, TransactionType.LIABILITY_PAYMENT, TransactionType.TRANSFER)
            CategoryType.INVESTMENT -> listOf(TransactionType.EXPENSE, TransactionType.INVESTMENT_OUTFLOW, TransactionType.INVESTMENT_CONTRIBUTION, TransactionType.PENSION, TransactionType.TRANSFER)
            CategoryType.IGNORE -> listOf(TransactionType.IGNORE)
            CategoryType.STATEMENT -> listOf(TransactionType.STATEMENT)
            CategoryType.LIABILITY -> listOf(TransactionType.LIABILITY_PAYMENT)
            CategoryType.TRANSFER -> listOf(TransactionType.TRANSFER)
        }
    }

    // =================================================================
    // 2. RESOLUTION RULES (From TransactionTypeResolver & SmsConstants)
    // =================================================================

    /**
     * Determine transaction type based on inputs.
     * Merges TransactionTypeResolver logic with SmsConstants invariants.
     */
    fun resolveTransactionType(
        transaction: Transaction? = null,
        manualClassification: String? = null,
        isSelfTransfer: Boolean = false,
        category: Category? = null,
        // Additional context for invariants (optional)
        isDebit: Boolean? = null,
        counterpartyType: String? = null, // "PERSON", "MERCHANT"
        isUntrustedP2P: Boolean = false,
        upiId: String? = null
    ): TransactionType {
        
        // 1. HARD OVERRIDES (Self Transfer)
        if (isSelfTransfer) return TransactionType.TRANSFER

        // 2. MANUAL CLASSIFICATION (User Intent)
        if (manualClassification != null) {
            return mapManualString(manualClassification)
        }

        // 3. FINANCE-FIRST INVARIANTS (From SmsConstants)
        // If we have specific context, apply invariants *before* category defaults if possible, 
        // OR apply them to the resolved type?
        // Let's stick to the Resolver logic first, then apply invariants.
        
        var type = resolveFromCategory(category)

        // 4. APPLY INVARIANTS (If context provided)
        if (isDebit != null) {
             type = applyInvariants(type, isDebit, category?.name, upiId, counterpartyType, isUntrustedP2P)
        }

        return type
    }
    
    private fun mapManualString(manual: String): TransactionType {
        // Handle special cases that don't map directly to enum values
        return when (manual.uppercase()) {
            "NEUTRAL", "TRANSFER" -> TransactionType.TRANSFER
            "INVESTMENT" -> TransactionType.INVESTMENT_OUTFLOW
            else -> try {
                TransactionType.valueOf(manual)
            } catch (e: Exception) {
                android.util.Log.w("TransactionRuleEngine", "Invalid manual classification: $manual", e)
                TransactionType.EXPENSE
            }
        }
    }

    private fun resolveFromCategory(category: Category?): TransactionType {
        if (category == null) return TransactionType.EXPENSE
        
        // Special name checks (Legacy logic preserved)
        if (category.name.contains(AppConstants.Keywords.STATEMENT, ignoreCase = true)) return TransactionType.STATEMENT
        if (category.name.contains(AppConstants.Keywords.CREDIT_BILL, ignoreCase = true) ||
            category.name.contains(AppConstants.Keywords.CREDIT_CARD, ignoreCase = true)) return TransactionType.LIABILITY_PAYMENT
        if (category.name.contains(AppConstants.Categories.CASHBACK, ignoreCase = true)) return TransactionType.CASHBACK

        return when (category.type) {
            CategoryType.INCOME -> TransactionType.INCOME
            CategoryType.INVESTMENT -> TransactionType.INVESTMENT_OUTFLOW
            CategoryType.FIXED_EXPENSE,
            CategoryType.VARIABLE_EXPENSE,
            CategoryType.VEHICLE -> TransactionType.EXPENSE
            CategoryType.IGNORE -> TransactionType.IGNORE
            CategoryType.STATEMENT -> TransactionType.STATEMENT
            CategoryType.LIABILITY -> TransactionType.LIABILITY_PAYMENT
            CategoryType.TRANSFER -> TransactionType.TRANSFER
        }
    }

    private fun applyInvariants(
        currentType: TransactionType,
        isDebit: Boolean,
        categoryName: String?,
        upiId: String?,
        counterpartyType: String?,
        isUntrustedP2P: Boolean
    ): TransactionType {
        var txnType = currentType

        // Invariant 1: Credit != Expense
        if (!isDebit && txnType == TransactionType.EXPENSE) {
            txnType = TransactionType.INCOME
        }

        // Invariant 2: Cashback
        val isCashbackCategory = categoryName == AppConstants.Categories.CASHBACK || 
                                 categoryName == AppConstants.Keywords.CASHBACK_REWARDS
        val isCashbackVpa = upiId != null && com.saikumar.expensetracker.sms.SmsConstants.isCashbackVpa(upiId)
        if ((isCashbackCategory || isCashbackVpa) && !isDebit) {
            txnType = TransactionType.CASHBACK
        }

        // Invariant 3: Trusted P2P = TRANSFER
        val isP2pOrPerson = !isUntrustedP2P && (categoryName == AppConstants.Categories.P2P_TRANSFERS || counterpartyType == "PERSON")
        if (isP2pOrPerson && 
            txnType != TransactionType.CASHBACK && 
            txnType != TransactionType.TRANSFER) {
            txnType = TransactionType.TRANSFER
        }

        return txnType
    }
}
