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
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.saikumar.expensetracker.ui.dashboard.Quadruple
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

enum class AnalyticsViewMode {
    OVERVIEW,   // Pie chart of categories for selected year
    YEAR_COMP,  // Bar chart comparing years
    TRENDS      // Line chart of monthly trends (Total or Category specific)
}

data class AnalyticsUiState(
    val selectedYear: Int = LocalDate.now().year,
    val viewMode: AnalyticsViewMode = AnalyticsViewMode.OVERVIEW,
    val selectedCategory: Category? = null,
    val comparisonCategory: Category? = null, // For YoY category comparison
    val categorySpending: List<CategorySpending> = emptyList(),
    val yearlySpending: List<YearlySpending> = emptyList(),
    val monthlyTrends: List<MonthlySpending> = emptyList(),
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
    private val _comparisonCategory = MutableStateFlow<Category?>(null)
    
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
    private val dataState = combine(
        _categorySpending,
        _yearlySpending,
        _monthlyTrends,
        allCategories
    ) { catSpending, yearSpending, trends, categories ->
        Quadruple(catSpending, yearSpending, trends, categories)
    }

    val uiState: StateFlow<AnalyticsUiState> = combine(
        inputState,
        dataState,
        _comparisonCategory
    ) { (year, mode, category, loading), (catSpending, yearSpending, trends, categories), compCategory ->
        AnalyticsUiState(
            selectedYear = year,
            viewMode = mode,
            selectedCategory = category,
            comparisonCategory = compCategory,
            categorySpending = catSpending,
            yearlySpending = yearSpending,
            monthlyTrends = trends,
            allCategories = categories,
            isLoading = loading
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AnalyticsUiState())

    init {
        // Initial load
        refreshData()
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
            }
        }
    }

    fun setComparisonCategory(category: Category?) {
        _comparisonCategory.value = category
        if (_viewMode.value == AnalyticsViewMode.YEAR_COMP) {
            viewModelScope.launch {
                fetchYearlySpending()
            }
        }
    }

    private fun refreshData() {
        viewModelScope.launch {
            _isLoading.value = true
            // Always fetch category spending for the summary cards (Total, Avg, Top Category)
            fetchCategorySpending()
            // Always fetch monthly trends for the "Highest Month" toggle
            fetchMonthlyTrends()
            
            // Fetch specific chart data based on mode
            when (_viewMode.value) {
                AnalyticsViewMode.OVERVIEW -> { /* Already fetched above */ }
                AnalyticsViewMode.YEAR_COMP -> fetchYearlySpending()
                AnalyticsViewMode.TRENDS -> { /* Already fetched above */ }
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
        val compCategory = _comparisonCategory.value
        _yearlySpending.value = if (compCategory != null) {
            repository.getYearlySpendingForCategory(compCategory.id)
        } else {
            repository.getYearlySpending()
        }
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
