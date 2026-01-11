package com.saikumar.expensetracker.data.db

import androidx.room.*
import com.saikumar.expensetracker.data.entity.MerchantPattern
import kotlinx.coroutines.flow.Flow

@Dao
interface MerchantPatternDao {
    @Query("SELECT * FROM merchant_patterns ORDER BY keyword ASC")
    fun getAllPatterns(): Flow<List<MerchantPattern>>

    @Query("SELECT * FROM merchant_patterns WHERE isUserDefined = 1")
    fun getUserDefinedPatterns(): Flow<List<MerchantPattern>>

    @Query("SELECT * FROM merchant_patterns WHERE keyword = :keyword LIMIT 1")
    suspend fun getPatternByKeyword(keyword: String): MerchantPattern?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPattern(pattern: MerchantPattern): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPatterns(patterns: List<MerchantPattern>)

    @Delete
    suspend fun deletePattern(pattern: MerchantPattern)

    @Query("DELETE FROM merchant_patterns WHERE isUserDefined = 0")
    suspend fun deleteSystemPatterns()

    @Query("SELECT COUNT(*) FROM merchant_patterns")
    suspend fun getCount(): Int
}
