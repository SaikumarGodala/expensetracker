package com.saikumar.expensetracker.ui.add

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.saikumar.expensetracker.data.entity.*
import com.saikumar.expensetracker.data.repository.ExpenseRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import com.saikumar.expensetracker.domain.TransactionValidator
import kotlinx.coroutines.launch

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

            // Use TransactionTypeResolver for consistent type determination
            val transactionType = TransactionValidator.getAllowedTypes(category).let { allowedTypes ->
                val resolvedType = when (manualClassification) {
                    "INCOME" -> TransactionType.INCOME
                    "EXPENSE" -> TransactionType.EXPENSE
                    "NEUTRAL" -> TransactionType.TRANSFER
                    "LIABILITY_PAYMENT" -> TransactionType.LIABILITY_PAYMENT
                    "REFUND" -> TransactionType.REFUND
                    "INVESTMENT" -> TransactionType.INVESTMENT_OUTFLOW
                    "CASHBACK" -> TransactionType.CASHBACK
                    "IGNORE" -> TransactionType.IGNORE
                    else -> {
                        // Heuristic based on category
                        when {
                            category.name.contains("Credit Bill", ignoreCase = true) -> TransactionType.LIABILITY_PAYMENT
                            category.type == CategoryType.INCOME -> TransactionType.INCOME
                            else -> allowedTypes.first()
                        }
                    }
                }
                // Validation: Ensure the resolved type is allowed. If not, fallback to first allowed.
                if (resolvedType in allowedTypes) resolvedType else allowedTypes.first()
            }
            
            val transaction = Transaction(
                amountPaisa = amountPaisa,
                categoryId = categoryId,
                timestamp = timestamp,
                note = note.ifBlank { null },
                source = TransactionSource.MANUAL,
                transactionType = transactionType,
                manualClassification = manualClassification
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
