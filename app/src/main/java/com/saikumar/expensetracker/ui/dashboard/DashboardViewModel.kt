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
    val balanceContext: String = "",
    val ignoredCount: Int = 0
)

data class Quadruple<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)

data class Quintuple<A, B, C, D, E>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D,
    val fifth: E
)

class DashboardViewModel(
    private val repository: ExpenseRepository,
    private val preferencesManager: PreferencesManager,
    private val cycleOverrideDao: CycleOverrideDao,
    private val userAccountDao: com.saikumar.expensetracker.data.db.UserAccountDao
) : ViewModel() {

    // Note: Category seeding moved to ExpenseTrackerApplication.onCreate() for better performance

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

    // Cache for salary day detection to avoid repeated DB queries
    private var cachedSalaryDay: Int? = null
    private var cachedSalaryDayMonth: String? = null

    /**
     * Detect salary day from actual salary transactions.
     * Returns the day of month if salary found, otherwise 0 (use last working day).
     * Performance: Cached per month to avoid repeated DB queries.
     */
    private suspend fun detectSalaryDay(): Int {
        // Check if we have a cached value for current month
        val currentMonth = LocalDate.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM"))
        if (cachedSalaryDay != null && cachedSalaryDayMonth == currentMonth) {
            return cachedSalaryDay!!
        }

        try {
            val categories = repository.allEnabledCategories.first()
            val salaryCategory = categories.find { it.name == "Salary" } ?: return 0

            // Look back 4 months to find salary transactions
            val now = LocalDate.now()
            val fourMonthsAgo = now.minusMonths(4)
            val startTs = fourMonthsAgo.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
            val endTs = now.atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

            // Query salary transactions
            val salaryAmount = repository.transactionDao.getSalaryForPeriod(startTs, endTs)

            // If salary exists, get the most recent salary transaction to extract the day
            if (salaryAmount != null && salaryAmount > 0) {
                val transactions = repository.transactionDao.getTransactionsByCategoryId(salaryCategory.id)
                val recentSalary = transactions
                    .filter { it.timestamp >= startTs }
                    .maxByOrNull { it.timestamp }

                if (recentSalary != null) {
                    val salaryDate = Instant.ofEpochMilli(recentSalary.timestamp)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()
                    val detectedDay = salaryDate.dayOfMonth

                    // Cache the result for this month
                    cachedSalaryDay = detectedDay
                    cachedSalaryDayMonth = currentMonth
                    return detectedDay
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("DashboardViewModel", "Failed to detect salary day: ${e.message}")
        }

        // Cache fallback value too
        cachedSalaryDay = 0
        cachedSalaryDayMonth = currentMonth
        return 0 // Fallback to last working day
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<DashboardUiState> = combine(
        _referenceDate,
        repository.allEnabledCategories,
        _searchQuery,
        selectedAccounts,
        detectedAccounts,
    ) { refDate, categories, query, selectedAccts, accounts ->
        Quintuple(refDate, categories, query, selectedAccts, accounts)
    }
    .flatMapLatest { params ->
        flow {
            // Detect salary day dynamically from actual transactions
            val salaryDay = detectSalaryDay()

            emit(FilterParams(
                CycleUtils.getCurrentCycleRange(params.first, salaryDay),
                params.second,
                params.third,
                params.fourth,
                params.fifth
            ))
        }
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
            repository.getTransactionLinksInPeriod(startTimestamp, endTimestamp),
            repository.getStatementsInPeriod(startTimestamp, endTimestamp)
        ) { transactions, links, statements ->
                val sortedTransactions = transactions.sortedByDescending { it.transaction.timestamp }

                // Build map of linked transaction IDs to their Details
                val linkMap = links
                    .flatMap { l -> listOf(
                        l.primaryTxnId to LinkDetail(l.secondaryTxnId, l.linkType),
                        l.secondaryTxnId to LinkDetail(l.primaryTxnId, l.linkType)
                    )}
                    .toMap()

                // Statements are now fetched separately via dedicated query
                val otherTransactions = sortedTransactions
                
                // Filter by selected accounts
                val accountFilteredTransactions = if (params.selectedAccounts.isEmpty()) {
                    otherTransactions
                } else {
                    // Performance: Check indexed accountNumberLast4 field first before falling back to fullSmsBody search
                    otherTransactions.filter { txn ->
                        val accountLast4 = txn.transaction.accountNumberLast4
                        if (accountLast4 != null) {
                            // Fast path: Direct field comparison
                            params.selectedAccounts.contains(accountLast4)
                        } else {
                            // Fallback: String search in SMS body for transactions without extracted account number
                            val smsBody = txn.transaction.fullSmsBody ?: ""
                            params.selectedAccounts.any { last4 -> smsBody.contains(last4) }
                        }
                    }
                }

                // Filter for active/completed transactions only
                // Note: STATEMENT transactions are already filtered by query
                val activeTransactions = accountFilteredTransactions.filter {
                    it.transaction.status == TransactionStatus.COMPLETED &&
                    it.transaction.transactionType != TransactionType.LIABILITY_PAYMENT &&
                    it.transaction.transactionType != TransactionType.TRANSFER &&
                    it.transaction.transactionType != TransactionType.PENDING &&
                    it.transaction.transactionType != TransactionType.IGNORE
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
                // Count ignored transactions from pre-filtered list (before type exclusions)
                val ignoredCount = accountFilteredTransactions.count { it.transaction.transactionType == TransactionType.IGNORE }
                // Calculate Context for Hero Card
                val balanceContext = when {
                    income - (totalExpenses + vehicle + investment) < 0 -> {
                        when {
                            investment > income * 0.5 -> "High investment month"
                            variable > income * 0.4 -> "High variable spending"
                            else -> "Expenses exceed income"
                        }
                    }
                    else -> "On track for savings"
                }

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
                    selectedAccounts = params.selectedAccounts,
                    balanceContext = balanceContext,
                    ignoredCount = ignoredCount
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

                // Backup merchant memory after user confirmation (preserves across app updates)
                val app = preferencesManager.context.applicationContext as com.saikumar.expensetracker.ExpenseTrackerApplication
                app.merchantBackupManager.backupMerchantMemory()

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

                // Backup merchant memory after reclassification (preserves learned patterns)
                val app = context.applicationContext as com.saikumar.expensetracker.ExpenseTrackerApplication
                app.merchantBackupManager.backupMerchantMemory()

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
