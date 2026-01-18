package com.saikumar.expensetracker.data.db

import androidx.room.*
import com.saikumar.expensetracker.data.entity.CategorizationRule
import com.saikumar.expensetracker.data.entity.PatternType
import kotlinx.coroutines.flow.Flow

/**
 * DAO for categorization rule operations
 */
@Dao
interface CategorizationRuleDao {
    
    @Query("SELECT * FROM categorization_rules WHERE isActive = 1 ORDER BY createdAt DESC")
    fun getAllActiveRules(): Flow<List<CategorizationRule>>
    
    @Query("SELECT * FROM categorization_rules ORDER BY createdAt DESC")
    fun getAllRules(): Flow<List<CategorizationRule>>
    
    @Query("SELECT * FROM categorization_rules WHERE id = :id")
    suspend fun getRuleById(id: Long): CategorizationRule?
    
    @Query("SELECT * FROM categorization_rules WHERE pattern = :pattern AND isActive = 1")
    suspend fun getRuleByPattern(pattern: String): CategorizationRule?
    
    @Query("SELECT * FROM categorization_rules WHERE patternType = :type AND isActive = 1")
    suspend fun getRulesByType(type: PatternType): List<CategorizationRule>
    
    @Query("SELECT * FROM categorization_rules WHERE categoryId = :categoryId AND isActive = 1")
    suspend fun getRulesForCategory(categoryId: Long): List<CategorizationRule>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(rule: CategorizationRule): Long
    
    @Update
    suspend fun update(rule: CategorizationRule)
    
    @Query("UPDATE categorization_rules SET isActive = 0 WHERE id = :id")
    suspend fun softDelete(id: Long)
    
    @Query("UPDATE categorization_rules SET matchCount = matchCount + :count WHERE id = :id")
    suspend fun incrementMatchCount(id: Long, count: Int)
    
    @Delete
    suspend fun delete(rule: CategorizationRule)
    
    @Query("DELETE FROM categorization_rules WHERE id = :id")
    suspend fun deleteById(id: Long)
}
