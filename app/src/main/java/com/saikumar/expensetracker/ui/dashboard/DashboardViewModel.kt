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
import kotlinx.coroutines.delay
import java.time.*
import java.time.format.DateTimeFormatter

import com.saikumar.expensetracker.util.SnackbarController

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
    val statements: List<TransactionWithCategory> = emptyList(),
    val transactionLinks: Map<Long, LinkDetail> = emptyMap(),
    // Account filter
    val detectedAccounts: List<UserAccount> = emptyList(),
    val selectedAccounts: Set<String> = emptySet(),

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
    private val cycleOverrideDao: CycleOverrideDao,
    private val userAccountDao: com.saikumar.expensetracker.data.db.UserAccountDao
) : ViewModel() {

    init {
        viewModelScope.launch {
            repository.seedCategories()
        }
    }

    val snackbarController = SnackbarController()

    private data class FilterParams(
        val range: CycleRange,
        val categories: List<Category>,
        val query: String,
        val selectedAccounts: Set<String>,
        val accounts: List<UserAccount>
    )

    private val _referenceDate = MutableStateFlow(LocalDate.now())
    private val _searchQuery = MutableStateFlow("")
    val searchQuery = _searchQuery.asStateFlow()
    
    // Account filter state (synced via PreferencesManager)
    val selectedAccounts = preferencesManager.selectedAccounts
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())
    
    // Detected accounts flow
    val detectedAccounts: StateFlow<List<UserAccount>> = userAccountDao.getAllAccountsFlow()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())
    
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
            preferencesManager.setSelectedAccounts(emptySet())
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<DashboardUiState> = combine(
        combine(
            _referenceDate,
            repository.allEnabledCategories,
            _searchQuery,
            preferencesManager.salaryDay
        ) { refDate, categories, query, salaryDay ->
            Quadruple(refDate, categories, query, salaryDay)
        },
        selectedAccounts,
        detectedAccounts,
    ) { quad, selectedAccts, accounts ->
        FilterParams(
            CycleUtils.getCurrentCycleRange(quad.first),
            quad.second,
            quad.third,
            selectedAccts,
            accounts
        )
    }
    .flatMapLatest { params ->
        val refDate = LocalDate.now().withMonth(params.range.startDate.monthValue)
        val yearMonth = refDate.format(DateTimeFormatter.ofPattern("yyyy-MM"))
        cycleOverrideDao.getOverride(yearMonth).map { override ->
            val range = override?.let {
                CycleRange(
                    Instant.ofEpochMilli(it.startDate).atZone(ZoneId.systemDefault()).toLocalDateTime(),
                    Instant.ofEpochMilli(it.endDate).atZone(ZoneId.systemDefault()).toLocalDateTime()
                )
            } ?: params.range
            FilterParams(range, params.categories, params.query, params.selectedAccounts, params.accounts)
        }
    }.flatMapLatest { params ->
        // Convert CycleRange to epoch millis for query
        val startTimestamp = params.range.startDate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endTimestamp = params.range.endDate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        
        // Conditional Source: Search vs Period
        val transactionsFlow = if (params.query.isNotBlank()) {
            repository.searchTransactions(params.query)
        } else {
            repository.getTransactionsInPeriod(startTimestamp, endTimestamp)
        }
        
        combine(
            transactionsFlow,
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
                
                // Filter by selected accounts
                val accountFilteredTransactions = if (params.selectedAccounts.isEmpty()) {
                    otherTransactions
                } else {
                    otherTransactions.filter { txn ->
                        val smsBody = txn.transaction.fullSmsBody ?: ""
                        params.selectedAccounts.any { last4 -> smsBody.contains(last4) }
                    }
                }

                // Filter for active/completed transactions only
                val activeTransactions = accountFilteredTransactions.filter { 
                    it.transaction.status == TransactionStatus.COMPLETED &&
                    it.transaction.transactionType != TransactionType.LIABILITY_PAYMENT &&
                    it.transaction.transactionType != TransactionType.TRANSFER &&
                    it.transaction.transactionType != TransactionType.PENDING &&
                    it.transaction.transactionType != TransactionType.IGNORE &&
                    it.transaction.transactionType != TransactionType.STATEMENT
                }
                
                // Calculate Total Income (Excluding P2P Transfers)
                val income = activeTransactions
                    .filter { 
                        (it.transaction.transactionType == TransactionType.INCOME && 
                         it.category.name != "P2P Transfers") ||
                        it.transaction.transactionType == TransactionType.CASHBACK
                    }
                    .sumOf { it.transaction.amountPaisa } / 100.0
                
                // Calculate refund amount for netting
                val refundAmount = activeTransactions
                    .filter { it.transaction.transactionType == TransactionType.REFUND }
                    .sumOf { it.transaction.amountPaisa } / 100.0
                
                // Calculate categorized expenses
                val expenseTransactions = activeTransactions.filter { 
                    it.transaction.transactionType == TransactionType.EXPENSE ||
                    it.transaction.transactionType == TransactionType.INVESTMENT_OUTFLOW ||
                    it.transaction.transactionType == TransactionType.INVESTMENT_CONTRIBUTION
                }
                
                val fixed = expenseTransactions
                    .filter { it.category.type == CategoryType.FIXED_EXPENSE }
                    .sumOf { it.transaction.amountPaisa } / 100.0
                    
                val variable = expenseTransactions
                    .filter { it.category.type == CategoryType.VARIABLE_EXPENSE }
                    .sumOf { it.transaction.amountPaisa } / 100.0
                    
                val investment = expenseTransactions
                    .filter { it.category.type == CategoryType.INVESTMENT }
                    .sumOf { it.transaction.amountPaisa } / 100.0
                    
                val vehicle = expenseTransactions
                    .filter { it.category.type == CategoryType.VEHICLE }
                    .sumOf { it.transaction.amountPaisa } / 100.0
                
                // Net refunds against expenses
                val totalExpenses = (fixed + variable) - refundAmount

                val filteredTransactions = accountFilteredTransactions
                
                DashboardUiState(
                    cycleRange = if (params.query.isNotBlank()) null else params.range,
                    categories = params.categories,
                    totalIncome = income,
                    totalFixedExpenses = fixed,
                    totalVariableExpenses = variable,
                    totalInvestments = investment,
                    totalVehicleExpenses = vehicle,
                    totalExpenses = totalExpenses,
                    extraMoney = income - (totalExpenses + vehicle + investment),
                    transactions = filteredTransactions,
                    statements = statements,
                    transactionLinks = linkMap,
                    detectedAccounts = params.accounts,
                    selectedAccounts = params.selectedAccounts
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
        
        // ===== MERCHANT MEMORY: User Confirmation =====
        // When user manually categorizes a transaction, learn this mapping permanently
        val trainingKey = if (!transaction.merchantName.isNullOrBlank()) {
            transaction.merchantName
        } else if (!transaction.fullSmsBody.isNullOrBlank()) {
            com.saikumar.expensetracker.sms.SmsConstants.cleanMessageBody(transaction.fullSmsBody)
        } else {
            null
        }

        if (trainingKey != null && newCategory != null) {
            try {
                repository.userConfirmMerchantMapping(
                    merchantName = trainingKey,
                    categoryId = newCategoryId,
                    transactionType = newTransactionType.name,
                    timestamp = System.currentTimeMillis()
                )
                android.util.Log.d("DashboardViewModel", "MERCHANT_MEMORY: User confirmed mapping '$trainingKey' → '${newCategory.name}'")
                
                // --- ML TRAINING LOGGING (GOLD LABEL) ---
                if (!transaction.fullSmsBody.isNullOrBlank()) {
                    com.saikumar.expensetracker.util.TrainingDataLogger.logSample(
                        context = preferencesManager.context, // Use context from PrefManager
                        smsBody = transaction.fullSmsBody,
                        merchantName = transaction.merchantName, 
                        confidence = 1.0f, // GOLD Label (User confirmed)
                        isUserCorrection = true
                    )
                }
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
            try {
                repository.deleteTransaction(transaction)
                snackbarController.showSuccess("Transaction deleted")
            } catch (e: Exception) {
                android.util.Log.e("DashboardViewModel", "Delete failed", e)
                snackbarController.showError(
                    "Failed to delete transaction",
                    actionLabel = "Retry",
                    onAction = { deleteTransaction(transaction) }
                )
            }
        }
    }

    fun reclassifyAll(context: Context) {
        viewModelScope.launch {
            try {
                SmsProcessor.reclassifyTransactions(context)
                snackbarController.showSuccess("Reclassification complete")
            } catch (e: Exception) {
                android.util.Log.e("DashboardViewModel", "Reclassify failed", e)
                snackbarController.showError(
                    "Failed to reclassify transactions: ${e.message}",
                    actionLabel = "Retry",
                    onAction = { reclassifyAll(context) }
                )
            }
        }
    }

    // ============ BATCH CATEGORIZATION ============

    /**
     * Find transactions similar to the given transaction for batch categorization
     */
    suspend fun findSimilarTransactions(transaction: Transaction): SimilarityResult {
        return repository.findSimilarTransactions(transaction)
    }

    fun addCategory(name: String, type: CategoryType) {
        viewModelScope.launch {
            try {
                val category = Category(name = name, type = type, isEnabled = true, isDefault = false, icon = name)
                repository.insertCategory(category)
                snackbarController.showSuccess("Category added")
            } catch (e: android.database.sqlite.SQLiteConstraintException) {
                snackbarController.showError("A category with this name already exists")
            } catch (e: Exception) {
                android.util.Log.e("DashboardViewModel", "Category add failed", e)
                snackbarController.showError("Failed to add category")
            }
        }
    }



    class Factory(
        private val repository: ExpenseRepository, 
        private val preferencesManager: PreferencesManager, 
        private val cycleOverrideDao: CycleOverrideDao,
        private val userAccountDao: com.saikumar.expensetracker.data.db.UserAccountDao
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return DashboardViewModel(repository, preferencesManager, cycleOverrideDao, userAccountDao) as T
        }
    }
}
