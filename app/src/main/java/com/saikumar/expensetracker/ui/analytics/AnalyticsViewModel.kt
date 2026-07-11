package com.saikumar.expensetracker.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.saikumar.expensetracker.data.db.CategorySpending
import com.saikumar.expensetracker.data.db.MonthlySpending
import com.saikumar.expensetracker.data.db.YearlySpending
import com.saikumar.expensetracker.data.entity.Category
import com.saikumar.expensetracker.data.repository.ExpenseRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import com.saikumar.expensetracker.ui.dashboard.Quadruple
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

enum class AnalyticsViewMode {
    OVERVIEW,   // Pie chart of categories for selected year
    TRENDS      // Line chart of monthly trends + Bar chart of yearly history
}

data class AnalyticsUiState(
    val selectedYear: Int = LocalDate.now().year,
    val viewMode: AnalyticsViewMode = AnalyticsViewMode.OVERVIEW,
    val selectedCategory: Category? = null,
    // comparisonCategory removed
    val categorySpending: List<CategorySpending> = emptyList(),
    val yearlySpending: List<YearlySpending> = emptyList(),
    val monthlyTrends: List<MonthlySpending> = emptyList(),
    val previousYearTotal: Long = 0L,
    val availableYears: List<Int> = listOf(2024, 2025, 2026), // Should be dynamic ideally
    val allCategories: List<Category> = emptyList(),
    val isLoading: Boolean = false
)

class AnalyticsViewModel(
    private val repository: ExpenseRepository
) : ViewModel() {

    private val _selectedYear = MutableStateFlow(LocalDate.now().year)
    private val _viewMode = MutableStateFlow(AnalyticsViewMode.OVERVIEW)
    private val _selectedCategory = MutableStateFlow<Category?>(null)
    private val _isLoading = MutableStateFlow(false)

    // Data Holders
    private val _categorySpending = MutableStateFlow<List<CategorySpending>>(emptyList())
    private val _yearlySpending = MutableStateFlow<List<YearlySpending>>(emptyList())
    private val _monthlyTrends = MutableStateFlow<List<MonthlySpending>>(emptyList())
    private val _previousYearTotal = MutableStateFlow(0L)
    // _comparisonCategory removed
    
    // Available Categories for selector
    val allCategories = repository.allEnabledCategories
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Combine inputs first (Year, Mode, Category, Loading)
    private val inputState = combine(
        _selectedYear,
        _viewMode,
        _selectedCategory,
        _isLoading
    ) { year, mode, category, loading ->
        Quadruple(year, mode, category, loading)
    }

    // Combine data flows
    // NOTE: combine() with exactly 5 flow arguments + a 5-param lambda is ambiguous between
    // kotlinx.coroutines' fixed 5-arity overload and its reified vararg overload (Flow is
    // covariant, so any 5 Flow<X> args also satisfy the vararg Flow<T> signature). This produces
    // confusing "SuspendFunctionN vs SuspendFunction1<Array<T>, R>" compile errors - see the same
    // fix applied in DashboardViewModel.uiState. Nesting two smaller combine() calls avoids it.
    private val dataState = combine(
        combine(_categorySpending, _yearlySpending, _monthlyTrends) { catSpending, yearSpending, trends ->
            Triple(catSpending, yearSpending, trends)
        },
        _previousYearTotal,
        allCategories
    ) { catSpendingYearlyTrends, prevTotal, categories ->
        Quadruple(
            catSpendingYearlyTrends.first,
            catSpendingYearlyTrends.second,
            catSpendingYearlyTrends.third,
            Pair(prevTotal, categories)
        )
    }

    val uiState: StateFlow<AnalyticsUiState> = combine(
        inputState,
        dataState
    ) { (year, mode, category, loading), (catSpending, yearSpending, trends, pair) ->
        val (prevTotal, categories) = pair // Unpack the Quadruple/Pair hack
        AnalyticsUiState(
            selectedYear = year,
            viewMode = mode,
            selectedCategory = category,
            // comparisonCategory = null,
            categorySpending = catSpending,
            yearlySpending = yearSpending,
            monthlyTrends = trends,
            previousYearTotal = prevTotal,
            allCategories = categories,
            isLoading = loading
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AnalyticsUiState())

    init {
        // Initial load
        refreshData()
    }

    // ===== NEEDS-ATTENTION GROUPS =====
    // The generic buckets (Uncategorized/Miscellaneous) grouped by counterparty, so the
    // user can see WHO the money went to/came from and categorize a whole group at once.
    data class AttentionGroup(val name: String, val count: Int, val totalPaisa: Long)

    private val _attentionGroups = MutableStateFlow<List<AttentionGroup>>(emptyList())
    val attentionGroups: StateFlow<List<AttentionGroup>> = _attentionGroups.asStateFlow()

    private val genericBuckets = setOf("Uncategorized", "Miscellaneous", "Unknown Expense")

    fun loadAttentionGroups() {
        viewModelScope.launch {
            try {
                // ALL-TIME, not just the selected year: this card is for clearing the whole
                // backlog, and a category decision for a counterparty is time-independent.
                val txns = repository.getTransactionsInPeriod(0L, Long.MAX_VALUE).first()
                _attentionGroups.value = txns
                    .filter {
                        it.category.name in genericBuckets &&
                        it.transaction.deletedAt == null &&
                        it.transaction.transactionType == com.saikumar.expensetracker.data.entity.TransactionType.EXPENSE
                    }
                    .groupBy { it.transaction.merchantName ?: it.transaction.upiId ?: "(no name)" }
                    .map { (name, group) ->
                        AttentionGroup(name, group.size, group.sumOf { it.transaction.amountPaisa })
                    }
                    .sortedWith(compareByDescending<AttentionGroup> { it.count }.thenByDescending { it.totalPaisa })
            } catch (e: Exception) {
                android.util.Log.w("AnalyticsViewModel", "loadAttentionGroups failed: ${e.message}")
            }
        }
    }

    /**
     * Categorize every generic-bucket transaction of [name] in the selected year to
     * [category] - user-confirmed (conf=100, survives reclassify) and learned into
     * merchant memory so FUTURE payments to the same counterparty map automatically.
     */
    // Transactions of one attention group, for the preview dialog (all-time)
    private val _groupTransactions = MutableStateFlow<List<com.saikumar.expensetracker.data.db.TransactionWithCategory>>(emptyList())
    val groupTransactions: StateFlow<List<com.saikumar.expensetracker.data.db.TransactionWithCategory>> = _groupTransactions.asStateFlow()

    fun loadGroupTransactions(name: String) {
        viewModelScope.launch {
            try {
                _groupTransactions.value = repository.getTransactionsInPeriod(0L, Long.MAX_VALUE).first()
                    .filter {
                        it.category.name in genericBuckets &&
                        it.transaction.deletedAt == null &&
                        (it.transaction.merchantName ?: it.transaction.upiId ?: "(no name)") == name
                    }
                    .sortedByDescending { it.transaction.timestamp }
            } catch (e: Exception) {
                _groupTransactions.value = emptyList()
            }
        }
    }

    fun bulkCategorize(name: String, category: Category) {
        viewModelScope.launch {
            try {
                // ALL-TIME (see loadAttentionGroups)
                val start = 0L
                val end = Long.MAX_VALUE
                val newType = when (category.type) {
                    com.saikumar.expensetracker.data.entity.CategoryType.INCOME -> com.saikumar.expensetracker.data.entity.TransactionType.INCOME
                    com.saikumar.expensetracker.data.entity.CategoryType.INVESTMENT -> com.saikumar.expensetracker.data.entity.TransactionType.INVESTMENT_OUTFLOW
                    com.saikumar.expensetracker.data.entity.CategoryType.TRANSFER -> com.saikumar.expensetracker.data.entity.TransactionType.TRANSFER
                    com.saikumar.expensetracker.data.entity.CategoryType.LIABILITY -> com.saikumar.expensetracker.data.entity.TransactionType.LIABILITY_PAYMENT
                    else -> com.saikumar.expensetracker.data.entity.TransactionType.EXPENSE
                }
                val txns = repository.getTransactionsInPeriod(start, end).first()
                    .filter {
                        it.category.name in genericBuckets &&
                        it.transaction.deletedAt == null &&
                        (it.transaction.merchantName ?: it.transaction.upiId ?: "(no name)") == name
                    }
                for (t in txns) {
                    repository.updateTransaction(
                        t.transaction.copy(
                            categoryId = category.id,
                            transactionType = newType,
                            isExpenseEligible = newType == com.saikumar.expensetracker.data.entity.TransactionType.EXPENSE,
                            confidenceScore = 100
                        )
                    )
                }
                // Learn for the future
                if (name != "(no name)") {
                    repository.userConfirmMerchantMapping(
                        merchantName = name,
                        categoryId = category.id,
                        transactionType = newType.name,
                        timestamp = System.currentTimeMillis()
                    )
                }
                loadAttentionGroups()
                refreshData()
            } catch (e: Exception) {
                android.util.Log.e("AnalyticsViewModel", "bulkCategorize failed", e)
            }
        }
    }

    fun setYear(year: Int) {
        _selectedYear.value = year
        refreshData()
    }

    fun setViewMode(mode: AnalyticsViewMode) {
        _viewMode.value = mode
        refreshData()
    }

    fun setSelectedCategory(category: Category?) {
        _selectedCategory.value = category
        if (_viewMode.value == AnalyticsViewMode.TRENDS) {
            viewModelScope.launch {
                fetchMonthlyTrends()
                fetchYearlySpending()
            }
        }
    }

    // setComparisonCategory removed

    private fun refreshData() {
        viewModelScope.launch {
            _isLoading.value = true

            // Performance: Run all data fetches in parallel using async
            val categorySpendingDeferred = async { fetchCategorySpending() }
            val monthlyTrendsDeferred = async { fetchMonthlyTrends() }
            val previousYearDeferred = async { fetchPreviousYearTotal() }

            // Await base data
            categorySpendingDeferred.await()
            monthlyTrendsDeferred.await()
            previousYearDeferred.await()

            // Fetch mode-specific data
            when (_viewMode.value) {
                AnalyticsViewMode.OVERVIEW -> { /* Already fetched above */ }
                AnalyticsViewMode.TRENDS -> {
                    // Fetch yearly spending for the unified Trends view
                    fetchYearlySpending()
                }
            }
            _isLoading.value = false
        }
    }

    private suspend fun fetchCategorySpending() {
        val year = _selectedYear.value
        val start = LocalDate.of(year, 1, 1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val end = LocalDate.of(year, 12, 31).atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        
        _categorySpending.value = repository.getCategorySpending(start, end)
    }

    private suspend fun fetchYearlySpending() {
        val category = _selectedCategory.value
        _yearlySpending.value = if (category != null) {
            repository.getYearlySpendingForCategory(category.id)
        } else {
            repository.getYearlySpending()
        }
    }

    private suspend fun fetchPreviousYearTotal() {
        // Only needed for Overview mode Hero Card context
        val currentSelectedYear = _selectedYear.value
        val prevYear = currentSelectedYear - 1
        
        // Optimize: We could use getYearlySpending() but that scans all years.
        // Let's just do a specific range query or reuse existing DAO method if cheap.
        // actually getYearlySpending() is what we have.
        // Let's just use getCategorySpending for previous year to sum it up.
        val start = LocalDate.of(prevYear, 1, 1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val end = LocalDate.of(prevYear, 12, 31).atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        
        val prevYearCatSpending = repository.getCategorySpending(start, end)
        _previousYearTotal.value = prevYearCatSpending.sumOf { it.totalAmount }
    }

    private suspend fun fetchMonthlyTrends() {
        val year = _selectedYear.value
        val start = LocalDate.of(year, 1, 1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val end = LocalDate.of(year, 12, 31).atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        val category = _selectedCategory.value
        val rawData = if (category != null) {
            repository.getMonthlySpendingForCategory(category.id, start, end)
        } else {
            repository.getMonthlySpending(start, end)
        }
        
        // Fill in missing months with 0
        val filledData = mutableListOf<MonthlySpending>()
        for (month in 1..12) {
            val monthStr = "%04d-%02d".format(year, month)
            val existing = rawData.find { it.month == monthStr }
            if (existing != null) {
                filledData.add(existing)
            } else {
                filledData.add(MonthlySpending(monthStr, 0L))
            }
        }
        _monthlyTrends.value = filledData
    }
    
    class Factory(private val repository: ExpenseRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return AnalyticsViewModel(repository) as T
        }
    }
}
