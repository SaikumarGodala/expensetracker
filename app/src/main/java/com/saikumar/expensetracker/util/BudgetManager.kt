package com.saikumar.expensetracker.util

import com.saikumar.expensetracker.data.dao.BudgetBreachDao
import com.saikumar.expensetracker.data.db.TransactionDao
import com.saikumar.expensetracker.data.entity.BudgetBreach
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters
import java.time.YearMonth

enum class BudgetStatus {
    SAFE,
    BREACHED_STAGE_1, // Initial 100% breach
    BREACHED_STAGE_2  // >110% breach (Month End)
}

data class BudgetState(
    val status: BudgetStatus,
    val limit: Long,
    val expenses: Long,
    val month: String
)

class BudgetManager(
    private val transactionDao: TransactionDao,
    private val budgetBreachDao: BudgetBreachDao,
    private val preferencesManager: PreferencesManager
) {

    // Threshold for Stage 2 (10% extra)
    private val STAGE_2_THRESHOLD_PERCENT = 1.10
    // Check Stage 2 only after this day of month (e.g., 25th)
    private val STAGE_2_CHECK_DAY = 25

    suspend fun recalculateAutoLimit() {
        if (!preferencesManager.isAutoBudgetEnabled.first()) return
        if (preferencesManager.isManualBudgetOverride.first()) return

        // 1. Get Previous Month
        val now = LocalDate.now()
        val prevMonth = YearMonth.now().minusMonths(1)
        
        val startTs = prevMonth.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endTs = prevMonth.atEndOfMonth().atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        // 2. Get Salary
        val salary = transactionDao.getSalaryForPeriod(startTs, endTs) ?: 0L
        
        // 3. Set Limit (50%)
        // If salary is 0 (no data), default to 0 or keep existing?
        // If 0, verify if we have data. If truly 0 salary, budget is 0.
        if (salary > 0) {
            val limit = salary / 2
            preferencesManager.setBudgetLimit(limit, isManual = false)
        }
    }

    suspend fun checkBudgetStatus(): BudgetState {
        val now = LocalDate.now()
        val currentMonthStr = YearMonth.now().toString()
        
        val startTs = YearMonth.now().atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val endTs = YearMonth.now().atEndOfMonth().atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()

        // 1. Get Current Expenses (Fixed + Variable)
        val expenses = transactionDao.getTotalExpenseForPeriod(startTs, endTs) ?: 0L
        
        // 2. Get Limit
        var limit = preferencesManager.budgetLimitPaise.first()
        
        // If limit is 0 (e.g. first run), try to calc
        if (limit == 0L) {
            recalculateAutoLimit()
            limit = preferencesManager.budgetLimitPaise.first()
            if (limit == 0L) {
                // Determine a safe fallback or infinite?
                // If limit is 0, we shouldn't block user.
                return BudgetState(BudgetStatus.SAFE, 0, expenses, currentMonthStr)
            }
        }

        // 3. Check Breaches
        if (expenses > limit) {
            // Check if Stage 1 already recorded
            val stage1 = budgetBreachDao.getBreachForMonth(currentMonthStr, 1)
            
            if (stage1 == null) {
                return BudgetState(BudgetStatus.BREACHED_STAGE_1, limit, expenses, currentMonthStr)
            }
            
            // Check Stage 2
            // Condition: Expenses > 110% Limit AND (Day >= 25 OR Month Changed if logic supports)
            // AND Stage 2 not recorded
            if (expenses > (limit * STAGE_2_THRESHOLD_PERCENT).toLong()) {
                // Only check if Late in month
                if (now.dayOfMonth >= STAGE_2_CHECK_DAY) {
                    val stage2 = budgetBreachDao.getBreachForMonth(currentMonthStr, 2)
                    if (stage2 == null) {
                        return BudgetState(BudgetStatus.BREACHED_STAGE_2, limit, expenses, currentMonthStr)
                    }
                }
            }
        }
        
        return BudgetState(BudgetStatus.SAFE, limit, expenses, currentMonthStr)
    }

    suspend fun recordBreach(month: String, stage: Int, limit: Long, expenses: Long, reason: String) {
        val breach = BudgetBreach(
            month = month,
            stage = stage,
            limitAmount = limit,
            breachedAmount = expenses,
            reason = reason,
            timestamp = System.currentTimeMillis()
        )
        budgetBreachDao.insert(breach)
    }
}
