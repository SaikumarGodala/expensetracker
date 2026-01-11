package com.saikumar.expensetracker.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.saikumar.expensetracker.data.db.TransactionWithCategory
import com.saikumar.expensetracker.data.entity.*
import com.saikumar.expensetracker.data.repository.ExpenseRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class FilteredTransactionsViewModel(
    private val repository: ExpenseRepository
) : ViewModel() {

    private val _filterParams = MutableStateFlow<Triple<CategoryType, Long, Long>?>(null)

    val categories: StateFlow<List<Category>> = repository.allEnabledCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val transactions: StateFlow<List<TransactionWithCategory>> = _filterParams.flatMapLatest { params ->
        if (params == null) return@flatMapLatest flowOf(emptyList())
        
        val (type, startMillis, endMillis) = params

        repository.getTransactionsInPeriod(startMillis, endMillis).map { list ->
            // For INCOME type, show transactions with TransactionType.INCOME
            if (type == CategoryType.INCOME) {
                list.filter { it.transaction.transactionType == TransactionType.INCOME }
                    .sortedByDescending { it.transaction.timestamp }
            } else {
                list.filter { 
                    it.category.type == type && 
                    it.transaction.transactionType == TransactionType.EXPENSE 
                }.sortedByDescending { it.transaction.timestamp }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setFilter(type: CategoryType, start: Long, end: Long) {
        _filterParams.value = Triple(type, start, end)
    }

    fun updateTransactionDetails(
        transaction: Transaction, 
        newCategoryId: Long, 
        newNote: String, 
        isSelfTransfer: Boolean, 
        accountType: AccountType, 
        isIncomeManuallyIncluded: Boolean,
        manualClassification: String? = null
    ) {
        viewModelScope.launch {
            val categories = repository.allEnabledCategories.first()
            val newCategory = categories.find { it.id == newCategoryId }
            
            val newTransactionType = when {
                isSelfTransfer -> TransactionType.TRANSFER
                manualClassification == "INCOME" -> TransactionType.INCOME
                manualClassification == "EXPENSE" -> TransactionType.EXPENSE
                manualClassification == "NEUTRAL" -> TransactionType.TRANSFER
                newCategory?.name?.contains("Credit Bill", ignoreCase = true) == true -> TransactionType.LIABILITY_PAYMENT
                newCategory?.type == CategoryType.INCOME || isIncomeManuallyIncluded -> TransactionType.INCOME
                else -> TransactionType.EXPENSE
            }
            
            repository.updateTransaction(transaction.copy(
                categoryId = newCategoryId,
                note = newNote,
                isSelfTransfer = isSelfTransfer,
                accountType = accountType,
                isIncomeManuallyIncluded = isIncomeManuallyIncluded,
                manualClassification = manualClassification,
                transactionType = newTransactionType
            ))
        }
    }

    fun addCategory(name: String, type: CategoryType) {
        viewModelScope.launch {
            val category = Category(name = name, type = type, isEnabled = true, isDefault = false, icon = name)
            repository.insertCategory(category)
        }
    }

    class Factory(private val repository: ExpenseRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return FilteredTransactionsViewModel(repository) as T
        }
    }
}
