package com.saikumar.expensetracker.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.saikumar.expensetracker.data.entity.CategoryBudget
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetDao {
    @Query("SELECT * FROM category_budgets WHERE categoryId = :categoryId AND month = :month AND year = :year")
    suspend fun getBudgetForCategory(categoryId: Long, month: Int, year: Int): CategoryBudget?

    @Query("SELECT * FROM category_budgets WHERE month = :month AND year = :year")
    fun getBudgetsForMonth(month: Int, year: Int): Flow<List<CategoryBudget>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBudget(budget: CategoryBudget)

    @Query("DELETE FROM category_budgets WHERE id = :id")
    suspend fun deleteBudget(id: Long)
    
    // Get distinct budgeted months for browsing history?
    @Query("SELECT DISTINCT year || '-' || printf('%02d', month) as ym FROM category_budgets ORDER BY ym DESC")
    fun getBudgetedMonths(): Flow<List<String>>
}
