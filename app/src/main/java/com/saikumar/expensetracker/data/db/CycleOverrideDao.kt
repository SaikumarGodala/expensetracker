package com.saikumar.expensetracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.saikumar.expensetracker.data.entity.CycleOverride
import kotlinx.coroutines.flow.Flow

@Dao
interface CycleOverrideDao {
    @Query("SELECT * FROM cycle_overrides WHERE yearMonth = :yearMonth")
    fun getOverride(yearMonth: String): Flow<CycleOverride?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun setOverride(override: CycleOverride)
}
