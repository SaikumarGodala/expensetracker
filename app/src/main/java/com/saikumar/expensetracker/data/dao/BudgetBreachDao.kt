package com.saikumar.expensetracker.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.saikumar.expensetracker.data.entity.BudgetBreach
import kotlinx.coroutines.flow.Flow

@Dao
interface BudgetBreachDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(breach: BudgetBreach)

    @Query("SELECT * FROM budget_breaches WHERE month = :month AND stage = :stage ORDER BY timestamp DESC LIMIT 1")
    suspend fun getBreachForMonth(month: String, stage: Int): BudgetBreach?

    @Query("SELECT * FROM budget_breaches ORDER BY timestamp DESC")
    fun getAllBreaches(): Flow<List<BudgetBreach>>
}
