package com.saikumar.expensetracker.ui.dashboard

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.saikumar.expensetracker.data.db.CycleOverrideDao
import com.saikumar.expensetracker.data.db.TransactionWithCategory
import com.saikumar.expensetracker.data.entity.*
import com.saikumar.expensetracker.data.repository.ExpenseRepository
import com.saikumar.expensetracker.sms.SmsProcessor
import com.saikumar.expensetracker.util.CycleRange
import com.saikumar.expensetracker.util.CycleUtils
import com.saikumar.expensetracker.util.PreferencesManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.*
import java.time.format.DateTimeFormatter

data class DashboardUiState(
    val cycleRange: CycleRange? = null,
    val categories: List<Category> = emptyList(),
    val totalIncome: Double = 0.0,
    val totalFixedExpenses: Double = 0.0,
    val totalVariableExpenses: Double = 0.0,
    val totalInvestments: Double = 0.0,
    val totalVehicleExpenses: Double = 0.0,
    val totalExpenses: Double = 0.0,
    val extraMoney: Double = 0.0,
    val transactions: List<TransactionWithCategory> = emptyList()
)

data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

class DashboardViewModel(
    private val repository: ExpenseRepository,
    private val preferencesManager: PreferencesManager,
    private val cycleOverrideDao: CycleOverrideDao
) : ViewModel() {

    private data class FilterParams(
        val range: CycleRange,
        val categories: List<Category>,
        val query: String
    )

    private val _referenceDate = MutableStateFlow(LocalDate.now())
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<DashboardUiState> = combine(
        _referenceDate,
        repository.allEnabledCategories,
        _searchQuery,
        preferencesManager.salaryDay
    ) { refDate, categories, query, salaryDay ->
        Quadruple(refDate, categories, query, salaryDay)
    }
    .flatMapLatest { quad ->
        val refDate = quad.first
        val categories = quad.second
        val query = quad.third
        val salaryDay = quad.fourth
        val yearMonth = refDate.format(DateTimeFormatter.ofPattern("yyyy-MM"))
        cycleOverrideDao.getOverride(yearMonth).map { override ->
            val range = override?.let {
                CycleRange(
                    Instant.ofEpochMilli(it.startDate).atZone(ZoneId.systemDefault()).toLocalDateTime(),
                    Instant.ofEpochMilli(it.endDate).atZone(ZoneId.systemDefault()).toLocalDateTime()
                )
            } ?: CycleUtils.getCurrentCycleRange(salaryDay, refDate)
            FilterParams(range, categories, query)
        }
    }.flatMapLatest { params ->
        // Convert CycleRange to epoch millis for query
        val startTimestamp = params.range.startDate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endTimestamp = params.range.endDate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        
        repository.getTransactionsInPeriod(startTimestamp, endTimestamp).map { transactions ->
                val sortedTransactions = transactions.sortedByDescending { it.transaction.timestamp }
                
                // ACCOUNTING RULE: Use TransactionType for filtering, not flags or category names
                // Exclude TRANSFER and LIABILITY_PAYMENT from income/expense totals
                val activeTransactions = sortedTransactions.filter { 
                    it.transaction.transactionType != TransactionType.TRANSFER &&
                    it.transaction.transactionType != TransactionType.LIABILITY_PAYMENT
                }
                
                // ACCOUNTING RULE: Income = TransactionType.INCOME only
                val income = activeTransactions
                    .filter { it.transaction.transactionType == TransactionType.INCOME }
                    .sumOf { it.transaction.amountPaisa / 100.0 }
                
                // ACCOUNTING RULE: Expenses = TransactionType.EXPENSE, categorized by CategoryType
                val expenseTransactions = activeTransactions.filter { 
                    it.transaction.transactionType == TransactionType.EXPENSE 
                }
                
                val fixed = expenseTransactions
                    .filter { it.category.type == CategoryType.FIXED_EXPENSE }
                    .sumOf { it.transaction.amountPaisa / 100.0 }
                    
                val variable = expenseTransactions
                    .filter { it.category.type == CategoryType.VARIABLE_EXPENSE }
                    .sumOf { it.transaction.amountPaisa / 100.0 }
                    
                val investment = expenseTransactions
                    .filter { it.category.type == CategoryType.INVESTMENT }
                    .sumOf { it.transaction.amountPaisa / 100.0 }
                    
                val vehicle = expenseTransactions
                    .filter { it.category.type == CategoryType.VEHICLE }
                    .sumOf { it.transaction.amountPaisa / 100.0 }
                
                val totalExpenses = fixed + variable

                val filteredTransactions = if (params.query.isBlank()) {
                    sortedTransactions
                } else {
                    sortedTransactions.filter {
                        it.category.name.contains(params.query, ignoreCase = true) ||
                        (it.transaction.note?.contains(params.query, ignoreCase = true) == true) ||
                        (it.transaction.merchantName?.contains(params.query, ignoreCase = true) == true)
                    }
                }
                
                DashboardUiState(
                    cycleRange = params.range,
                    categories = params.categories,
                    totalIncome = income,
                    totalFixedExpenses = fixed,
                    totalVariableExpenses = variable,
                    totalInvestments = investment,
                    totalVehicleExpenses = vehicle,
                    totalExpenses = totalExpenses,
                    extraMoney = income - (totalExpenses + vehicle + investment),
                    transactions = filteredTransactions
                )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardUiState())

    fun previousCycle() {
        _referenceDate.value = _referenceDate.value.minusMonths(1)
    }

    fun nextCycle() {
        _referenceDate.value = _referenceDate.value.plusMonths(1)
    }

    fun setCustomCycle(startDate: LocalDate, endDate: LocalDate) {
        viewModelScope.launch {
            val yearMonth = _referenceDate.value.format(DateTimeFormatter.ofPattern("yyyy-MM"))
            val startMillis = startDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endMillis = endDate.atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            
            cycleOverrideDao.setOverride(CycleOverride(yearMonth, startMillis, endMillis))
        }
    }

    fun updateTransactionDetails(
        transaction: Transaction, 
        newCategoryId: Long, 
        newNote: String, 
        isSelfTransfer: Boolean, 
        accountType: AccountType, 
        isIncomeManuallyIncluded: Boolean, 
        updateSimilar: Boolean, 
        manualClassification: String? = null
    ) {
        viewModelScope.launch {
            // Determine transaction type from manual classification or category
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
            
            // Apply to similar transactions using SmsProcessor if updateSimilar is true
            // The function will automatically extract UPI/NEFT IDs for matching
            if (updateSimilar) {
                SmsProcessor.assignCategoryToTransaction(
                    context = preferencesManager.context,
                    transactionId = transaction.id,
                    categoryId = newCategoryId,
                    applyToSimilar = true
                )
            }
        }
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            repository.deleteTransaction(transaction)
        }
    }

    fun reclassifyAll(context: Context) {
        viewModelScope.launch {
            SmsProcessor.reclassifyTransactions(context)
        }
    }

    // ============ BATCH CATEGORIZATION ============

    /**
     * Find transactions similar to the given transaction for batch categorization
     */
    suspend fun findSimilarTransactions(transaction: Transaction): SmsProcessor.SimilarityResult {
        // Get all transactions
        val allTransactions = repository.getTransactionsInPeriod(0L, Long.MAX_VALUE).first()
            .map { it.transaction }
        
        return SmsProcessor.findSimilarTransactions(transaction, allTransactions)
    }

    fun addCategory(name: String, type: CategoryType) {
        viewModelScope.launch {
            val category = Category(name = name, type = type, isEnabled = true, isDefault = false, icon = name)
            repository.insertCategory(category)
        }
    }

    class Factory(
        private val repository: ExpenseRepository, 
        private val preferencesManager: PreferencesManager, 
        private val cycleOverrideDao: CycleOverrideDao
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DashboardViewModel(repository, preferencesManager, cycleOverrideDao) as T
        }
    }
}
