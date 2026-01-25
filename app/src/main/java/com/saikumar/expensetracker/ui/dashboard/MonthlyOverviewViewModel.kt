package com.saikumar.expensetracker.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.saikumar.expensetracker.data.db.TransactionWithCategory
import com.saikumar.expensetracker.data.entity.Category
import com.saikumar.expensetracker.data.entity.CategoryType
import com.saikumar.expensetracker.data.entity.TransactionType
import com.saikumar.expensetracker.data.entity.TransactionStatus
import com.saikumar.expensetracker.data.entity.AccountType
import com.saikumar.expensetracker.data.entity.Transaction
import com.saikumar.expensetracker.data.entity.UserAccount
import com.saikumar.expensetracker.data.repository.ExpenseRepository

import com.saikumar.expensetracker.util.CycleRange
import com.saikumar.expensetracker.util.CycleUtils
import com.saikumar.expensetracker.domain.TransactionRuleEngine
import com.saikumar.expensetracker.sms.SmsConstants
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.time.ZoneId
import com.saikumar.expensetracker.sms.SmsProcessor

import com.saikumar.expensetracker.util.PreferencesManager

import kotlinx.coroutines.launch

data class CategorySummary(
    val category: Category,
    val total: Double,
    val transactions: List<TransactionWithCategory>
)

data class MonthlyOverviewUiState(
    val cycleRange: CycleRange? = null,
    val income: Double = 0.0,
    val expenses: Double = 0.0,
    val investments: Double = 0.0,
    val categoryBreakdown: Map<CategoryType, List<CategorySummary>> = emptyMap(),
    val detectedAccounts: List<UserAccount> = emptyList(),
    val selectedAccounts: Set<String> = emptySet()
)

class MonthlyOverviewViewModel(
    private val repository: ExpenseRepository,
    private val preferencesManager: PreferencesManager,
    private val userAccountDao: com.saikumar.expensetracker.data.db.UserAccountDao
) : ViewModel() {

    private val _referenceDate = MutableStateFlow(LocalDate.now())
    private val _customRange = MutableStateFlow<CycleRange?>(null)
    
    // Account Filter State (Persisted)
    val selectedAccounts = preferencesManager.selectedAccounts
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
        
    val detectedAccounts: StateFlow<List<UserAccount>> = userAccountDao.getAllAccountsFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val allCategories = repository.allEnabledCategories
    
    private data class FilterParams(
        val range: CycleRange,
        val selectedAccounts: Set<String>,
        val accounts: List<UserAccount>
    )

    fun toggleAccountFilter(accountNumber: String) {
        viewModelScope.launch {
            val current = selectedAccounts.value.toMutableSet()
            if (current.contains(accountNumber)) {
                current.remove(accountNumber)
            } else {
                current.add(accountNumber)
            }
            preferencesManager.updateSelectedAccounts(current)
        }
    }
    
    fun clearAccountFilter() {
        viewModelScope.launch {
            preferencesManager.updateSelectedAccounts(emptySet())
        }
    }

    /**
     * Find transactions similar to the given transaction for batch categorization
     */
    suspend fun findSimilarTransactions(transaction: Transaction): com.saikumar.expensetracker.sms.SimilarityResult {
        return repository.findSimilarTransactions(transaction)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<MonthlyOverviewUiState> = combine(
        _referenceDate, 
        _customRange,
        selectedAccounts,
        detectedAccounts
    ) { refDate, customRange, selectedAccts, accounts ->
        val range = customRange ?: CycleUtils.getCurrentCycleRange(refDate)
        FilterParams(range, selectedAccts, accounts)
    }.flatMapLatest { params ->
        // Convert range to timestamps for query
        val startTimestamp = params.range.startDate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endTimestamp = params.range.endDate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        
        repository.getTransactionsInPeriod(startTimestamp, endTimestamp).map { transactions ->
            // Filter by selected accounts first
            val accountFilteredParams = if (params.selectedAccounts.isEmpty()) {
                transactions
            } else {
                transactions.filter { txn ->
                    val smsBody = txn.transaction.fullSmsBody ?: ""
                    params.selectedAccounts.any { last4 -> smsBody.contains(last4) }
                }
            }
            
            // Filter out only true self-transfers and liability payments
            // P0 FIX: Only count COMPLETED transactions to prevent pending/future transactions from inflating totals
            val activeTransactions = accountFilteredParams.filter { 
                it.transaction.status == TransactionStatus.COMPLETED &&  // CRITICAL: Only completed transactions
                it.transaction.transactionType != TransactionType.LIABILITY_PAYMENT &&
                it.transaction.transactionType != TransactionType.TRANSFER  // Self-transfers excluded
            }
            
            // Calculate totals using TransactionType
            val income = activeTransactions
                .filter { it.transaction.transactionType == TransactionType.INCOME }
                .sumOf { it.transaction.amountPaisa } / 100.0
            
            // Expenses = EXPENSE type + P2P transfers to other people
            val expenseTransactions = activeTransactions.filter { 
                it.transaction.transactionType == TransactionType.EXPENSE ||
                // P2P to other people (TRANSFER but NOT self-transfer) counts as spending
                false  // P2P is now EXPENSE type
            }
            
            val expenses = expenseTransactions
                .filter { 
                    it.category.type == CategoryType.FIXED_EXPENSE || 
                    it.category.type == CategoryType.VARIABLE_EXPENSE ||
                    it.category.type == CategoryType.VEHICLE
                }
                .sumOf { it.transaction.amountPaisa } / 100.0
            
            val investments = expenseTransactions
                .filter { it.category.type == CategoryType.INVESTMENT }
                .sumOf { it.transaction.amountPaisa } / 100.0
            
            // Group by category and calculate per-category totals
            val breakdown = activeTransactions
                .groupBy { it.category }
                .map { (category, txns) ->
                    CategorySummary(
                        category = category,
                        total = txns.sumOf { it.transaction.amountPaisa } / 100.0,
                        transactions = txns.sortedByDescending { it.transaction.timestamp }
                    )
                }
                .sortedByDescending { it.total }
                .groupBy { it.category.type }
            
            MonthlyOverviewUiState(
                cycleRange = params.range,
                income = income,
                expenses = expenses,
                investments = investments,
                categoryBreakdown = breakdown,
                detectedAccounts = params.accounts,
                selectedAccounts = params.selectedAccounts
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MonthlyOverviewUiState())

    fun previousCycle() {
        if (_customRange.value != null) {
            _customRange.value = null
        } else {
            _referenceDate.value = _referenceDate.value.minusMonths(1)
        }
    }

    fun nextCycle() {
        if (_customRange.value != null) {
            _customRange.value = null
        } else {
            _referenceDate.value = _referenceDate.value.plusMonths(1)
        }
    }
    
    fun setCustomRange(startDate: LocalDate, endDate: LocalDate) {
        _customRange.value = CycleRange(
            startDate.atStartOfDay(),
            endDate.atTime(23, 59, 59)
        )
    }
    
    fun clearCustomRange() {
        _customRange.value = null
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
        
        // Resolve type using centralized Rule Engine
        val resolvedType = TransactionRuleEngine.resolveTransactionType(
            transaction = transaction,
            manualClassification = manualClassification,
            isSelfTransfer = false,
            category = newCategory
        )

        // P3 QUALITY FIX: Validate against allowed types for this category
        val finalTransactionType = if (newCategory != null) {
             val validation = TransactionRuleEngine.validateCategoryType(resolvedType, newCategory)
             if (validation is TransactionRuleEngine.ValidationResult.Valid) {
                 resolvedType
             } else {
                 TransactionRuleEngine.getAllowedTypes(newCategory).first()
             }
        } else {
             resolvedType
        }

            repository.updateTransaction(transaction.copy(
                categoryId = newCategoryId, 
                note = newNote, 
                accountType = accountType,
                manualClassification = manualClassification,
                transactionType = finalTransactionType
            ))
            
            // ===== MERCHANT MEMORY: User Confirmation =====
            // When user manually categorizes a transaction, learn this mapping permanently
            val trainingKey = if (!transaction.merchantName.isNullOrBlank()) {
                transaction.merchantName
            } else if (!transaction.fullSmsBody.isNullOrBlank()) {
                SmsConstants.cleanMessageBody(transaction.fullSmsBody)
            } else {
                null
            }

            if (trainingKey != null && newCategory != null) {
                try {
                    repository.userConfirmMerchantMapping(
                        merchantName = trainingKey,
                        categoryId = newCategoryId,
                        transactionType = finalTransactionType.name,
                        timestamp = System.currentTimeMillis()
                    )
                    android.util.Log.d("MonthlyOverviewViewModel", "MERCHANT_MEMORY: User confirmed mapping '$trainingKey' → '${newCategory.name}'")
                } catch (e: Exception) {
                    android.util.Log.w("MonthlyOverviewViewModel", "MERCHANT_MEMORY: Failed to record user confirmation: ${e.message}")
                }
            }
            
            // Apply to similar transactions using SmsProcessor if updateSimilar is true
            if (updateSimilar) {
                SmsProcessor.assignCategoryToTransaction(
                    context = preferencesManager.context,
                    transactionId = transaction.id,
                    categoryId = newCategoryId,
                    applyToSimilar = true,
                    transactionType = finalTransactionType
                )
            }
        }
    }

    fun deleteTransaction(transaction: Transaction) {
        viewModelScope.launch {
            repository.deleteTransaction(transaction)
        }
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
        private val userAccountDao: com.saikumar.expensetracker.data.db.UserAccountDao
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MonthlyOverviewViewModel(repository, preferencesManager, userAccountDao) as T
        }
    }
}
