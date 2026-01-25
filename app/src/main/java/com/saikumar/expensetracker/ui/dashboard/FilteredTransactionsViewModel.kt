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

    private data class FilterState(
        val type: CategoryType, 
        val start: Long, 
        val end: Long,
        val categoryName: String? = null
    )

    private val _filterParams = MutableStateFlow<FilterState?>(null)

    val categories: StateFlow<List<Category>> = repository.allEnabledCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(ExperimentalCoroutinesApi::class)
    val transactions: StateFlow<List<TransactionWithCategory>> = _filterParams.flatMapLatest { params ->
        if (params == null) return@flatMapLatest flowOf(emptyList())
        
        val (type, startMillis, endMillis, categoryName) = params

        repository.getTransactionsInPeriod(startMillis, endMillis).map { list ->
            // Base filter: Status COMPLETED and Type matches CategoryType logic
            val baseFiltered = when (type) {
                CategoryType.INCOME -> {
                    list.filter { 
                        it.transaction.status == TransactionStatus.COMPLETED &&
                        (it.transaction.transactionType == TransactionType.INCOME ||
                         it.transaction.transactionType == TransactionType.CASHBACK)
                    }
                }
                CategoryType.INVESTMENT -> {
                    list.filter { 
                        it.transaction.status == TransactionStatus.COMPLETED &&
                        it.category.type == CategoryType.INVESTMENT &&
                        (it.transaction.transactionType == TransactionType.EXPENSE ||
                         it.transaction.transactionType == TransactionType.INVESTMENT_OUTFLOW ||
                         it.transaction.transactionType == TransactionType.INVESTMENT_CONTRIBUTION)
                    }
                }
                else -> {
                    list.filter { 
                        it.transaction.status == TransactionStatus.COMPLETED &&
                        it.category.type == type && 
                        (it.transaction.transactionType == TransactionType.EXPENSE ||
                         it.transaction.transactionType == TransactionType.INVESTMENT_OUTFLOW)
                    }
                }
            }
            
            if (categoryName != null) {
                 baseFiltered.filter { it.category.name == categoryName }
                     .sortedByDescending { it.transaction.timestamp }
            } else {
                 baseFiltered.sortedByDescending { it.transaction.timestamp }
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun setFilter(type: CategoryType, start: Long, end: Long, categoryName: String? = null) {
        _filterParams.value = FilterState(type, start, end, categoryName)
    }

    suspend fun findSimilarTransactions(transaction: Transaction): com.saikumar.expensetracker.sms.SimilarityResult {
        return repository.findSimilarTransactions(transaction)
    }

    fun updateTransactionDetails(
        transaction: Transaction, 
        newCategoryId: Long, 
        newNote: String, 
        accountType: AccountType, 
        updateSimilar: Boolean,
        manualClassification: String? = null
    ) {
        viewModelScope.launch {
            val categories = repository.allEnabledCategories.first()
            val newCategory = categories.find { it.id == newCategoryId }
            
            // Use centralized Rule Engine
            val newTransactionType = com.saikumar.expensetracker.domain.TransactionRuleEngine.resolveTransactionType(
                transaction = transaction,
                manualClassification = manualClassification,
                isSelfTransfer = false,
                category = newCategory
            )
            
            repository.updateTransaction(transaction.copy(
                categoryId = newCategoryId,
                note = newNote,
                accountType = accountType,
                manualClassification = manualClassification,
                transactionType = newTransactionType
            ))

            // Record user confirmation for ML training
            val trainingText = if (!transaction.merchantName.isNullOrBlank()) {
                transaction.merchantName
            } else if (!transaction.fullSmsBody.isNullOrBlank()) {
                com.saikumar.expensetracker.sms.SmsConstants.cleanMessageBody(transaction.fullSmsBody)
            } else {
                null
            }

            if (!trainingText.isNullOrBlank()) {
                repository.userConfirmMerchantMapping(
                    merchantName = trainingText,
                    categoryId = newCategoryId,
                    transactionType = newTransactionType.name,
                    timestamp = System.currentTimeMillis()
                )
            }
            
            // Log override for debug analysis
            val overrideLogId = com.saikumar.expensetracker.util.ClassificationDebugLogger.startLog(
                com.saikumar.expensetracker.data.entity.RawInputCapture(
                    fullMessageText = transaction.fullSmsBody ?: "Manual/Unknown",
                    source = "USER_EDIT",
                    receivedTimestamp = transaction.timestamp,
                    sender = "USER",
                    amount = transaction.amountPaisa
                )
            )
            com.saikumar.expensetracker.util.ClassificationDebugLogger.logUserOverride(
                overrideLogId,
                com.saikumar.expensetracker.data.entity.UserOverride(
                    originalType = transaction.transactionType.name,
                    originalCategoryId = transaction.categoryId,
                    userSelectedType = newTransactionType.name,
                    userSelectedCategoryId = newCategoryId,
                    overrideTimestamp = System.currentTimeMillis()
                )
            )
            com.saikumar.expensetracker.util.ClassificationDebugLogger.persistLog(
                 com.saikumar.expensetracker.ExpenseTrackerApplication.instance, 
                 overrideLogId
            )

            if (updateSimilar) {
                com.saikumar.expensetracker.sms.SmsProcessor.assignCategoryToTransaction(
                    context = com.saikumar.expensetracker.ExpenseTrackerApplication.instance,
                    transactionId = transaction.id,
                    categoryId = newCategoryId,
                    applyToSimilar = true,
                    transactionType = newTransactionType
                )
            }
        }
    }

    fun addCategory(name: String, type: CategoryType) {
        viewModelScope.launch {
            val category = Category(name = name, type = type, isEnabled = true, isDefault = false, icon = name)
            repository.insertCategory(category)
        }
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            repository.deleteTransaction(transaction)
        }
    }

    class Factory(private val repository: ExpenseRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return FilteredTransactionsViewModel(repository) as T
        }
    }
}
