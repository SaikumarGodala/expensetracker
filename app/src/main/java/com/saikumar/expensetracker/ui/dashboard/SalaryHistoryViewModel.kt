package com.saikumar.expensetracker.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.saikumar.expensetracker.data.db.TransactionWithCategory
import com.saikumar.expensetracker.data.entity.TransactionType
import com.saikumar.expensetracker.data.repository.ExpenseRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class SalaryHistoryViewModel(
    private val repository: ExpenseRepository
) : ViewModel() {

    val salaryTransactions: StateFlow<List<TransactionWithCategory>> = repository
        .getTransactionsInPeriod(0L, Long.MAX_VALUE)  // All time
        .map { transactions ->
            transactions
                .filter { 
                    it.transaction.isSalaryCredit ||
                    (it.transaction.transactionType == TransactionType.INCOME && 
                     it.category.name.equals("Salary", ignoreCase = true))
                }
                .sortedByDescending { it.transaction.timestamp }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    class Factory(private val repository: ExpenseRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return SalaryHistoryViewModel(repository) as T
        }
    }
}