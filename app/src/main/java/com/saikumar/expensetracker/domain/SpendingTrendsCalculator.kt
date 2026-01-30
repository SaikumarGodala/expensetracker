package com.saikumar.expensetracker.domain

import com.saikumar.expensetracker.data.db.TransactionDao
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId

class SpendingTrendsCalculator(
    private val transactionDao: TransactionDao
) {

    /**
     * Calculate typical monthly spend for a category based on the last [monthsBack] months.
     * Excluding the current month to avoid partial data skimming the average.
     */
    suspend fun calculateTypicalSpend(categoryId: Long, monthsBack: Int = 3): Long {
        val now = LocalDate.now()
        // We want the previous N complete months from 1st of (Current - N) to End of (Current - 1)
        val endOfLastMonth = now.withDayOfMonth(1).minusDays(1)
        val startOfWindow = now.withDayOfMonth(1).minusMonths(monthsBack.toLong())

        val startMillis = startOfWindow.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endMillis = endOfLastMonth.atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        // Get all transactions for this category in the window
        // Use existing getTransactionsInPeriod and filter, or add specific query.
        // Let's use getTransactionsInPeriod which returns Flow<List<TransactionWithCategory>>
        val allTransactions = transactionDao.getTransactionsInPeriod(startMillis, endMillis).first()
        
        val categoryTransactions = allTransactions.filter { it.transaction.categoryId == categoryId }
        
        if (categoryTransactions.isEmpty()) return 0L

        // Group by Month (YYYY-MM)
        val monthlySums = categoryTransactions.groupBy { 
            val date = java.time.Instant.ofEpochMilli(it.transaction.timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
            "${date.year}-${date.monthValue}"
        }.mapValues { (_, txns) -> 
            txns.sumOf { it.transaction.amountPaisa }
        }

        // Calculate Average
        val totalSum = monthlySums.values.sum()
        val averagePaisa = if (monthsBack > 0) totalSum / monthsBack else 0L

        // Round to nearest ₹100 for cleaner "Ghost Budget" numbers
        return roundToNearestHundred(averagePaisa)
    }

    private fun roundToNearestHundred(paisa: Long): Long {
        val rupees = paisa / 100.0
        val roundedRupees = kotlin.math.round(rupees / 100) * 100
        return (roundedRupees * 100).toLong()
    }
}
