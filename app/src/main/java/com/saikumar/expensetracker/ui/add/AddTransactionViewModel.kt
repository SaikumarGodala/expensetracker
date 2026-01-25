package com.saikumar.expensetracker.ui.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.saikumar.expensetracker.data.entity.*
import com.saikumar.expensetracker.data.repository.ExpenseRepository

import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.saikumar.expensetracker.domain.TransactionRuleEngine

class AddTransactionViewModel(private val repository: ExpenseRepository) : ViewModel() {

    val categories: StateFlow<List<Category>> = repository.allEnabledCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Save a new transaction
     * @param amountPaisa Amount in paisa (smallest currency unit)
     * @param categoryId Category foreign key
     * @param timestamp UTC epoch milliseconds
     * @param note Optional user note
     * @param manualClassification Optional user override ("INCOME", "EXPENSE", "NEUTRAL", etc.)
     */


    fun saveTransaction(amountPaisa: Long, categoryId: Long, timestamp: Long, note: String, manualClassification: String? = null) {
        viewModelScope.launch {
            val categories = repository.allEnabledCategories.stateIn(viewModelScope).value
            val category = categories.find { it.id == categoryId }
            
            if (category == null) return@launch // Safety check

            // Use centralized TransactionRuleEngine
            val resolvedType = TransactionRuleEngine.resolveTransactionType(
                manualClassification = manualClassification,
                isSelfTransfer = false,
                category = category
            )
            
            // Validate: Ensure type is compatible with category
            val finalType = if (TransactionRuleEngine.validateCategoryType(resolvedType, category) is TransactionRuleEngine.ValidationResult.Valid) {
                resolvedType
            } else {
                TransactionRuleEngine.getAllowedTypes(category).first()
            }
            
            val transaction = Transaction(
                amountPaisa = amountPaisa,
                categoryId = categoryId,
                timestamp = timestamp,
                note = note.ifBlank { null },
                source = TransactionSource.MANUAL,
                transactionType = finalType,
                manualClassification = manualClassification,
                // Manual transactions have 100% confidence (user explicitly chose)
                confidenceScore = 100,
                // Only EXPENSE type is expense eligible
                isExpenseEligible = (finalType == TransactionType.EXPENSE)
            )
            repository.insertTransaction(transaction)
        }
    }

    class Factory(private val repository: ExpenseRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AddTransactionViewModel(repository) as T
        }
    }
}
