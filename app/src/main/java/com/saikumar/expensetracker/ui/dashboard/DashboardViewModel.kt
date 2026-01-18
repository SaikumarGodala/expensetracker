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
import com.saikumar.expensetracker.sms.SimilarityResult
import com.saikumar.expensetracker.util.CycleRange
import com.saikumar.expensetracker.util.CycleUtils
import com.saikumar.expensetracker.util.PreferencesManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.time.*
import java.time.format.DateTimeFormatter
import com.saikumar.expensetracker.util.TransactionTypeResolver

// Helper to store link details
data class LinkDetail(
    val linkedTxnId: Long,
    val type: LinkType
)

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
    val transactions: List<TransactionWithCategory> = emptyList(),
    val statements: List<TransactionWithCategory> = emptyList(), // Separated statements
    // Stores detail about the link: Linked Transaction ID + Type
    val transactionLinks: Map<Long, LinkDetail> = emptyMap()
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
            } ?: CycleUtils.getCurrentCycleRange(refDate)
            FilterParams(range, categories, query)
        }
    }.flatMapLatest { params ->
        // Convert CycleRange to epoch millis for query
        val startTimestamp = params.range.startDate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endTimestamp = params.range.endDate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        
        combine(
            repository.getTransactionsInPeriod(startTimestamp, endTimestamp),
            repository.allTransactionLinks
        ) { transactions, links ->
                val sortedTransactions = transactions.sortedByDescending { it.transaction.timestamp }
                
                // Build map of linked transaction IDs to their Details
                val linkMap = links
                    .flatMap { l -> listOf(
                        l.primaryTxnId to LinkDetail(l.secondaryTxnId, l.linkType), 
                        l.secondaryTxnId to LinkDetail(l.primaryTxnId, l.linkType)
                    )}
                    .toMap()
                
                // Separate Statements
                val (statements, otherTransactions) = sortedTransactions.partition { 
                    it.transaction.transactionType == TransactionType.STATEMENT 
                }

                // ACCOUNTING RULE: Use TransactionType for filtering
                // P0 FIX: Only count COMPLETED transactions to prevent pending/future transactions from inflating totals
                // Example: "Standing instruction will be debited on Feb 1" received Jan 28 shouldn't count in January
                val activeTransactions = otherTransactions.filter { 
                    it.transaction.status == TransactionStatus.COMPLETED &&  // CRITICAL: Only completed transactions
                    it.transaction.transactionType != TransactionType.LIABILITY_PAYMENT &&
                    it.transaction.transactionType != TransactionType.TRANSFER &&  // Self-transfers excluded
                    it.transaction.transactionType != TransactionType.PENDING &&   // Future debits
                    it.transaction.transactionType != TransactionType.IGNORE &&      // Excluded transactions
                    it.transaction.transactionType != TransactionType.STATEMENT      // Statements are excluded
                }
                
                // ACCOUNTING RULE: Income = TransactionType.INCOME + CASHBACK (positive adjustments)
                val income = activeTransactions
                    .filter { 
                        it.transaction.transactionType == TransactionType.INCOME ||
                        it.transaction.transactionType == TransactionType.CASHBACK  // Cashback is positive
                    }
                    .sumOf { it.transaction.amountPaisa / 100.0 }
                
                // FINANCE-FIRST: Calculate refund amount separately for netting
                val refundAmount = activeTransactions
                    .filter { it.transaction.transactionType == TransactionType.REFUND }
                    .sumOf { it.transaction.amountPaisa / 100.0 }
                
                // ACCOUNTING RULE: Expenses = TransactionType.EXPENSE OR INVESTMENT_OUTFLOW
                // Fix: Include INVESTMENT_CONTRIBUTION (SIPs) in spending charts if configured as Expense?
                // User wants Investments Blue. 
                // Usually Investment Contribution IS an outflow/expense for budget.
                val expenseTransactions = activeTransactions.filter { 
                    it.transaction.transactionType == TransactionType.EXPENSE ||
                    it.transaction.transactionType == TransactionType.INVESTMENT_OUTFLOW ||
                    it.transaction.transactionType == TransactionType.INVESTMENT_CONTRIBUTION // SIPs should be tracked
                }
                
                val fixed = expenseTransactions
                    .filter { it.category.type == CategoryType.FIXED_EXPENSE }
                    .sumOf { it.transaction.amountPaisa / 100.0 }
                    
                val variable = expenseTransactions
                    .filter { it.category.type == CategoryType.VARIABLE_EXPENSE }
                    .sumOf { it.transaction.amountPaisa / 100.0 }
                    
                // Fix Issue 3: Investment includes both CategoryType.INVESTMENT with EXPENSE or INVESTMENT_OUTFLOW
                val investment = expenseTransactions
                    .filter { it.category.type == CategoryType.INVESTMENT }
                    .sumOf { it.transaction.amountPaisa / 100.0 }
                    
                val vehicle = expenseTransactions
                    .filter { it.category.type == CategoryType.VEHICLE }
                    .sumOf { it.transaction.amountPaisa / 100.0 }
                
                // FINANCE-FIRST: Net refunds against expenses (Issue 5)
                val totalExpenses = (fixed + variable) - refundAmount

                val filteredTransactions = if (params.query.isBlank()) {
                    otherTransactions
                } else {
                    otherTransactions.filter {
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
                    transactions = filteredTransactions, // Main list (Excludes Statements)
                    statements = statements, // Separate list
                    transactionLinks = linkMap
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
        accountType: AccountType, 
        updateSimilar: Boolean, 
        manualClassification: String? = null
    ) {
        viewModelScope.launch {
            // Determine transaction type from manual classification or category
        val categories = repository.allEnabledCategories.first()
        val newCategory = categories.find { it.id == newCategoryId }
        
        val newTransactionType = TransactionTypeResolver.determineTransactionType(
            transaction = transaction,
            manualClassification = manualClassification,
            isSelfTransfer = false, // Not available in this context
            newCategory = newCategory
        )
            
        repository.updateTransaction(transaction.copy(
            categoryId = newCategoryId, 
            note = newNote, 
            accountType = accountType,
            manualClassification = manualClassification,
            transactionType = newTransactionType
        ))
        
        // ===== MERCHANT MEMORY: User Confirmation =====
        // When user manually categorizes a transaction, learn this mapping permanently
        if (transaction.merchantName != null && newCategory != null) {
            try {
                repository.userConfirmMerchantMapping(
                    merchantName = transaction.merchantName,
                    categoryId = newCategoryId,
                    transactionType = newTransactionType.name,
                    timestamp = System.currentTimeMillis()
                )
                android.util.Log.d("DashboardViewModel", "MERCHANT_MEMORY: User confirmed mapping '${transaction.merchantName}' → '${newCategory.name}'")
            } catch (e: Exception) {
                android.util.Log.w("DashboardViewModel", "MERCHANT_MEMORY: Failed to record user confirmation: ${e.message}")
            }
        }
        
        // Apply to similar transactions using SmsProcessor if updateSimilar is true
        // The function will automatically extract UPI/NEFT IDs for matching
        // and propagate isSelfTransfer and transactionType to similar transactions
        if (updateSimilar) {
            SmsProcessor.assignCategoryToTransaction(
                context = preferencesManager.context,
                transactionId = transaction.id,
                categoryId = newCategoryId,
                applyToSimilar = true,
                transactionType = newTransactionType
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
    suspend fun findSimilarTransactions(transaction: Transaction): SimilarityResult {
        // Optimized: Only fetch relevant candidates from DB instead of all transactions
        val candidates = if (!transaction.merchantName.isNullOrBlank()) {
             repository.getTransactionsByMerchant(transaction.merchantName)
        } else {
             // Fallback: Fetch last 6 months (covers most relevant history without loading entire DB)
             val sixMonthsAgo = System.currentTimeMillis() - (180L * 24 * 60 * 60 * 1000)
             repository.getTransactionsInPeriod(sixMonthsAgo, Long.MAX_VALUE).first()
                 .map { it.transaction }
        }
        
        return SmsProcessor.findSimilarTransactions(transaction, candidates)
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
