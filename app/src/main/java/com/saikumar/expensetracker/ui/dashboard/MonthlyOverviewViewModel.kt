package com.saikumar.expensetracker.ui.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.saikumar.expensetracker.data.db.TransactionWithCategory
import com.saikumar.expensetracker.data.entity.Category
import com.saikumar.expensetracker.data.entity.CategoryType
import com.saikumar.expensetracker.data.entity.TransactionType
import com.saikumar.expensetracker.data.repository.ExpenseRepository
import com.saikumar.expensetracker.util.CycleRange
import com.saikumar.expensetracker.util.CycleUtils
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import java.time.LocalDate
import java.time.ZoneId

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
    val categoryBreakdown: Map<CategoryType, List<CategorySummary>> = emptyMap()
)

class MonthlyOverviewViewModel(
    private val repository: ExpenseRepository
) : ViewModel() {

    private val _referenceDate = MutableStateFlow(LocalDate.now())

    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<MonthlyOverviewUiState> = _referenceDate.flatMapLatest { refDate ->
        val range = CycleUtils.getCurrentCycleRange(0, refDate)
        
        // Convert range to timestamps for query
        val startTimestamp = range.startDate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endTimestamp = range.endDate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        
        repository.getTransactionsInPeriod(startTimestamp, endTimestamp).map { transactions ->
            // Filter out transfers and liability payments
            val activeTransactions = transactions.filter { 
                it.transaction.transactionType != TransactionType.TRANSFER &&
                it.transaction.transactionType != TransactionType.LIABILITY_PAYMENT
            }
            
            // Calculate totals using TransactionType
            val income = activeTransactions
                .filter { it.transaction.transactionType == TransactionType.INCOME }
                .sumOf { it.transaction.amountPaisa / 100.0 }
            
            val expenseTransactions = activeTransactions.filter { 
                it.transaction.transactionType == TransactionType.EXPENSE
            }
            
            val expenses = expenseTransactions
                .filter { 
                    it.category.type == CategoryType.FIXED_EXPENSE || 
                    it.category.type == CategoryType.VARIABLE_EXPENSE ||
                    it.category.type == CategoryType.VEHICLE
                }
                .sumOf { it.transaction.amountPaisa / 100.0 }
            
            val investments = expenseTransactions
                .filter { it.category.type == CategoryType.INVESTMENT }
                .sumOf { it.transaction.amountPaisa / 100.0 }
            
            // Group by category and calculate per-category totals
            val breakdown = activeTransactions
                .groupBy { it.category }
                .map { (category, txns) ->
                    CategorySummary(
                        category = category,
                        total = txns.sumOf { it.transaction.amountPaisa / 100.0 },
                        transactions = txns.sortedByDescending { it.transaction.timestamp }
                    )
                }
                .sortedByDescending { it.total }
                .groupBy { it.category.type }
            
            MonthlyOverviewUiState(
                cycleRange = range,
                income = income,
                expenses = expenses,
                investments = investments,
                categoryBreakdown = breakdown
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), MonthlyOverviewUiState())

    fun previousCycle() {
        _referenceDate.value = _referenceDate.value.minusMonths(1)
    }

    fun nextCycle() {
        _referenceDate.value = _referenceDate.value.plusMonths(1)
    }

    class Factory(private val repository: ExpenseRepository) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MonthlyOverviewViewModel(repository) as T
        }
    }
}
