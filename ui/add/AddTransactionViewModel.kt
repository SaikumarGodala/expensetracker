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

class AddTransactionViewModel(private val repository: ExpenseRepository) : ViewModel() {

    val categories: StateFlow<List<Category>> = repository.allEnabledCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    /**
     * Save a new transaction
     * @param amountPaisa Amount in paisa (smallest currency unit)
     * @param categoryId Category foreign key
     * @param timestamp UTC epoch milliseconds
     * @param note Optional user note
     */
    fun saveTransaction(amountPaisa: Long, categoryId: Long, timestamp: Long, note: String) {
        viewModelScope.launch {
            val categories = repository.allEnabledCategories.stateIn(viewModelScope).value
            val category = categories.find { it.id == categoryId }
            
            // Determine transaction type from category
            val transactionType = when {
                category?.type == CategoryType.INCOME -> TransactionType.INCOME
                category?.name?.contains("Credit Bill", ignoreCase = true) == true -> TransactionType.LIABILITY_PAYMENT
                else -> TransactionType.EXPENSE
            }
            
            val transaction = Transaction(
                amountPaisa = amountPaisa,
                categoryId = categoryId,
                timestamp = timestamp,
                note = note.ifBlank { null },
                source = TransactionSource.MANUAL,
                transactionType = transactionType
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
