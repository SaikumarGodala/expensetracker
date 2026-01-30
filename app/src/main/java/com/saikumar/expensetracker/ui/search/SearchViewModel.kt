package com.saikumar.expensetracker.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.saikumar.expensetracker.data.db.TransactionWithCategory
import com.saikumar.expensetracker.data.entity.TransactionType
import com.saikumar.expensetracker.data.repository.ExpenseRepository
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

enum class TimeFilter(val label: String) {
    ALL("All Time"),
    THIS_MONTH("This Month"),
    LAST_MONTH("Last Month"),
    THIS_YEAR("This Year")
}

enum class SortOption(val label: String) {
    NEWEST("Newest"),
    OLDEST("Oldest"),
    AMOUNT_HIGH("High Amount"),
    AMOUNT_LOW("Low Amount")
}

class SearchViewModel(
    private val repository: ExpenseRepository
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _timeFilter = MutableStateFlow(TimeFilter.ALL)
    val timeFilter: StateFlow<TimeFilter> = _timeFilter.asStateFlow()

    private val _sortOption = MutableStateFlow(SortOption.NEWEST)
    val sortOption: StateFlow<SortOption> = _sortOption.asStateFlow()

    private val _searchResults = MutableStateFlow<List<TransactionWithCategory>>(emptyList())
    val searchResults: StateFlow<List<TransactionWithCategory>> = _searchResults.asStateFlow()

    private val _totalAmount = MutableStateFlow(0L)
    val totalAmount: StateFlow<Long> = _totalAmount.asStateFlow()

    init {
        combine(_query, _timeFilter, _sortOption) { q, time, sort ->
            Triple(q, time, sort)
        }.flatMapLatest { (q, time, sort) ->
            if (q.isBlank()) {
                flowOf(emptyList())
            } else {
                repository.searchTransactions(q).map { rawList ->
                    var filtered = rawList

                    // Apply Time Filter
                    val zone = ZoneId.systemDefault()
                    val today = LocalDate.now(zone)
                    
                    filtered = when (time) {
                        TimeFilter.ALL -> filtered
                        TimeFilter.THIS_MONTH -> {
                            val start = today.with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay(zone).toInstant().toEpochMilli()
                            filtered.filter { it.transaction.timestamp >= start }
                        }
                        TimeFilter.LAST_MONTH -> {
                            val start = today.minusMonths(1).with(TemporalAdjusters.firstDayOfMonth()).atStartOfDay(zone).toInstant().toEpochMilli()
                            val end = today.minusMonths(1).with(TemporalAdjusters.lastDayOfMonth()).atTime(23, 59, 59).atZone(zone).toInstant().toEpochMilli()
                            filtered.filter { it.transaction.timestamp in start..end }
                        }
                        TimeFilter.THIS_YEAR -> {
                            val start = today.with(TemporalAdjusters.firstDayOfYear()).atStartOfDay(zone).toInstant().toEpochMilli()
                            filtered.filter { it.transaction.timestamp >= start }
                        }
                    }

                    // Apply Sort
                    when (sort) {
                        SortOption.NEWEST -> filtered.sortedByDescending { it.transaction.timestamp }
                        SortOption.OLDEST -> filtered.sortedBy { it.transaction.timestamp }
                        SortOption.AMOUNT_HIGH -> filtered.sortedByDescending { it.transaction.amountPaisa }
                        SortOption.AMOUNT_LOW -> filtered.sortedBy { it.transaction.amountPaisa }
                    }
                }
            }
        }.onEach {
            _searchResults.value = it
            
            // Calculate total amount
            _totalAmount.value = it.sumOf { item ->
                val amount = item.transaction.amountPaisa
                when (item.transaction.transactionType) {
                    TransactionType.INCOME, TransactionType.CASHBACK, TransactionType.REFUND -> amount
                    TransactionType.EXPENSE, TransactionType.INVESTMENT_CONTRIBUTION, TransactionType.LIABILITY_PAYMENT -> -amount
                    else -> 0L // Transfers and others don't affect net total for now
                }
            }
        }.launchIn(viewModelScope)
    }

    fun updateQuery(newQuery: String) {
        _query.value = newQuery
    }

    fun setTimeFilter(filter: TimeFilter) {
        _timeFilter.value = filter
    }

    fun setSortOption(option: SortOption) {
        _sortOption.value = option
    }

    companion object {
        fun Factory(repository: ExpenseRepository): androidx.lifecycle.ViewModelProvider.Factory = object : androidx.lifecycle.ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SearchViewModel(repository) as T
            }
        }
    }
}
