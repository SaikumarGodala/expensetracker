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
import com.saikumar.expensetracker.data.dao.BudgetDao
import com.saikumar.expensetracker.domain.SpendingTrendsCalculator

import com.saikumar.expensetracker.util.PreferencesManager

import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll

data class BudgetStatus(
    val budgetPaisa: Long,    // Explicit budget if set
    val typicalSpendPaisa: Long, // Calculated average ("Ghost Budget")
    val isSoftCap: Boolean // From budget entity, or true for ghost
) {
    // Effective target is explicit budget if set, else typical spend (Ghost)
    val targetAmountPaisa: Long get() = if (budgetPaisa > 0) budgetPaisa else typicalSpendPaisa
    val isGhost: Boolean get() = budgetPaisa <= 0
}

data class CategorySummary(
    val category: Category,
    val total: Double,
    val transactions: List<TransactionWithCategory>,
    val budgetStatus: BudgetStatus? = null // Added for Smart Budgeting
)

data class BudgetRecommendation(
    val category: Category,
    val recommendedAmount: Long, // "Ghost Budget"
    val existingBudget: Long?,   // If already set
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
    private val userAccountDao: com.saikumar.expensetracker.data.db.UserAccountDao,
    private val budgetDao: BudgetDao,
    private val trendsCalculator: SpendingTrendsCalculator
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
        val accounts: List<UserAccount>,
        val month: Int,
        val year: Int
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

    /**
     * Detect salary day from actual salary transactions.
     * Returns the day of month if salary found, otherwise 0 (use last working day).
     */
    private suspend fun detectSalaryDay(): Int {
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
                    val salaryDate = java.time.Instant.ofEpochMilli(recentSalary.timestamp)
                        .atZone(ZoneId.systemDefault())
                        .toLocalDate()
                    return salaryDate.dayOfMonth
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("MonthlyOverviewViewModel", "Failed to detect salary day: ${e.message}")
        }

        return 0 // Fallback to last working day
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<MonthlyOverviewUiState> = combine(
        _referenceDate,
        _customRange,
        selectedAccounts,
        detectedAccounts
    ) { refDate, customRange, selectedAccts, accounts ->
        Quadruple(refDate, customRange, selectedAccts, accounts)
    }.flatMapLatest { params ->
        flow {
            // Detect salary day dynamically from actual transactions
            val salaryDay = detectSalaryDay()

            val range = params.second ?: CycleUtils.getCurrentCycleRange(params.first, salaryDay)
            // Extract Month/Year from the center of the range for budgeting
            val midPoint = range.startDate.plusDays(15)
            emit(FilterParams(range, params.third, params.fourth, midPoint.monthValue, midPoint.year))
        }
    }.flatMapLatest { params ->
        // Convert range to timestamps for query
        val startTimestamp = params.range.startDate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endTimestamp = params.range.endDate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        
        // 1. Fetch Transactions
        val transactionsFlow = repository.getTransactionsInPeriod(startTimestamp, endTimestamp)
        
        // 2. Fetch Budgets for this month
        val budgetsFlow = budgetDao.getBudgetsForMonth(params.month, params.year)

        combine(transactionsFlow, budgetsFlow) { transactions, budgets ->
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
            
            // Expenses = EXPENSE type + Investment flows
            val expenseTransactions = activeTransactions.filter { 
                it.transaction.transactionType == TransactionType.EXPENSE ||
                it.transaction.transactionType == TransactionType.INVESTMENT_CONTRIBUTION ||
                it.transaction.transactionType == TransactionType.INVESTMENT_OUTFLOW
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
            
            // Pre-calculate typical spends (Ghost Budgets) for visible categories
            // This is async inside the flow mapping? 
            // Since trendsCalculator is suspend, strictly we can't call it easily in map {} without flow builder
            // But getting it "lazily" or pre-fetching?
            // For now, let's assume we fetch typical spend for ALL active categories just once or optimize later.
            // Actually, calculating typical spend for every category on every UI update might be heavy.
            // Let's defer calculation or cache it. 
            // For MVP Phase 1: We will compute typical spend for categories present in the breakdown.
            
            val uniqueCategories = activeTransactions.map { it.category }.distinctBy { it.id }
            val typicalSpends = uniqueCategories.associate { cat ->
                cat.id to trendsCalculator.calculateTypicalSpend(cat.id)
            }
            
            val budgetMap = budgets.associateBy { it.categoryId }

            // Group by category and calculate per-category totals
            val breakdown = activeTransactions
                .groupBy { it.category }
                .map { (category, txns) ->
                    val explicitBudget = budgetMap[category.id]
                    val budgetAmount = explicitBudget?.amountPaisa ?: 0L
                    val typicalSpend = typicalSpends[category.id] ?: 0L
                    
                    val status = if (budgetAmount > 0 || typicalSpend > 0) {
                        BudgetStatus(
                            budgetPaisa = budgetAmount,
                            typicalSpendPaisa = typicalSpend,
                            isSoftCap = explicitBudget?.isSoftCap ?: true
                        )
                    } else null

                    CategorySummary(
                        category = category,
                        total = txns.sumOf { it.transaction.amountPaisa } / 100.0,
                        transactions = txns.sortedByDescending { it.transaction.timestamp },
                        budgetStatus = status
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
        
        // Resolve type using centralized Rule engine
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

                    // Backup merchant memory after user confirmation (preserves across app updates)
                    val app = preferencesManager.context.applicationContext as com.saikumar.expensetracker.ExpenseTrackerApplication
                    app.merchantBackupManager.backupMerchantMemory()
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

    suspend fun loadBudgetRecommendations(): List<BudgetRecommendation> {
        val categories = repository.allEnabledCategories.first()
            .filter { it.type == CategoryType.VARIABLE_EXPENSE || it.type == CategoryType.FIXED_EXPENSE }
            
        // For the current cycle reference date
        val refDate = _referenceDate.value
        val month = refDate.monthValue
        val year = refDate.year
        
        val existingBudgets = budgetDao.getBudgetsForMonth(month, year).first().associateBy { it.categoryId }
        
        return categories.map { category ->
            // Use async/awaitAll if we want parallel execution for speed, but calculator is likely fast enough
            val typical = trendsCalculator.calculateTypicalSpend(category.id)
            BudgetRecommendation(
                category = category,
                recommendedAmount = typical,
                existingBudget = existingBudgets[category.id]?.amountPaisa
            )
        }.sortedByDescending { it.recommendedAmount }
    }

    fun saveBudgets(budgets: Map<Long, Long>) {
        viewModelScope.launch {
            val refDate = _referenceDate.value
            val month = refDate.monthValue
            val year = refDate.year
            
            budgets.forEach { (categoryId, amount) ->
                if (amount >= 0) {
                    val entity = com.saikumar.expensetracker.data.entity.CategoryBudget(
                        categoryId = categoryId,
                        amountPaisa = amount,
                        month = month,
                        year = year,
                        isSoftCap = true // Default to soft cap for one-tap planning
                    )
                    budgetDao.upsertBudget(entity)
                }
            }
            // Trigger refresh is handled by Flows automatically
        }
    }

    class Factory(
        private val repository: ExpenseRepository,
        private val preferencesManager: PreferencesManager,
        private val userAccountDao: com.saikumar.expensetracker.data.db.UserAccountDao,
        private val budgetDao: BudgetDao,
        private val trendsCalculator: SpendingTrendsCalculator
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MonthlyOverviewViewModel(repository, preferencesManager, userAccountDao, budgetDao, trendsCalculator) as T
        }
    }
}

