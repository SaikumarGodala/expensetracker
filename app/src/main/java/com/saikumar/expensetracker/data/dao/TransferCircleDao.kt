package com.saikumar.expensetracker.data.dao

import androidx.room.*
import com.saikumar.expensetracker.data.entity.TransferCircleMember
import kotlinx.coroutines.flow.Flow
import com.saikumar.expensetracker.data.entity.TransferCircleAlias

/**
 * DAO for managing Transfer Circle members
 */
@Dao
interface TransferCircleDao {
    
    @Query("SELECT * FROM transfer_circle_members WHERE isIgnored = 0 ORDER BY recipientName ASC")
    fun getAllMembers(): Flow<List<TransferCircleMember>>
    
    @Query("SELECT * FROM transfer_circle_members WHERE id = :id")
    suspend fun getMemberById(id: Long): TransferCircleMember?
    
    @Query("SELECT * FROM transfer_circle_members WHERE LOWER(recipientName) = LOWER(:name)")
    suspend fun findByName(name: String): TransferCircleMember?
    
    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM transfer_circle_members WHERE isIgnored = 0 AND LOWER(recipientName) = LOWER(:name)
        ) OR EXISTS (
            SELECT 1 FROM transfer_circle_aliases 
            INNER JOIN transfer_circle_members ON transfer_circle_aliases.memberId = transfer_circle_members.id
            WHERE transfer_circle_members.isIgnored = 0 AND LOWER(transfer_circle_aliases.aliasName) = LOWER(:name)
        )
    """)
    suspend fun isInCircle(name: String): Boolean

    @Query("SELECT recipientName FROM transfer_circle_members")
    fun getAllRecipientNames(): Flow<List<String>>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(member: TransferCircleMember): Long
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAliases(aliases: List<TransferCircleAlias>)
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(members: List<TransferCircleMember>)
    
    @Update
    suspend fun update(member: TransferCircleMember)
    
    @Delete
    suspend fun delete(member: TransferCircleMember)
    
    @Query("DELETE FROM transfer_circle_members WHERE id = :id")
    suspend fun deleteById(id: Long)
    
    @Query("SELECT COUNT(*) FROM transfer_circle_members")
    suspend fun getCount(): Int
}
