package com.saikumar.expensetracker.data.repository

import com.saikumar.expensetracker.data.dao.TransferCircleDao
import com.saikumar.expensetracker.data.entity.TransferCircleMember
import kotlinx.coroutines.flow.Flow

import com.saikumar.expensetracker.data.entity.TransferCircleAlias

/**
 * Repository for managing Transfer Circle members
 */
class TransferCircleRepository(
    private val transferCircleDao: TransferCircleDao
) {
    
    // ...

    /**
     * Get all circle members as a Flow
     */
    fun getAllMembers(): Flow<List<TransferCircleMember>> {
        return transferCircleDao.getAllMembers()
    }
    
    /**
     * Check if a recipient is in the circle
     */
    suspend fun isInCircle(recipientName: String): Boolean {
        return transferCircleDao.isInCircle(recipientName)
    }

    /**
     * Add a new member to the circle with optional aliases
     */
    suspend fun addMember(
        name: String,
        phoneNumber: String? = null,
        isAutoDetected: Boolean = false,
        notes: String? = null,
        aliases: List<String> = emptyList()
    ): Long {
        val member = TransferCircleMember(
            recipientName = name,
            phoneNumber = phoneNumber,
            addedDate = System.currentTimeMillis(),
            isAutoDetected = isAutoDetected,
            notes = notes
        )
        val id = transferCircleDao.insert(member)
        
        if (aliases.isNotEmpty()) {
            // Filter out the canonical name itself if present in aliases
            val validAliases = aliases
                .filter { !it.equals(name, ignoreCase = true) }
                .distinct()
                
            if (validAliases.isNotEmpty()) {
                val aliasEntities = validAliases.map { 
                    TransferCircleAlias(memberId = id, aliasName = it)
                }
                transferCircleDao.insertAliases(aliasEntities)
            }
        }
        return id
    }
    
    /**
     * Update transfer stats for a member
     */
    suspend fun updateMemberStats(name: String, transferCount: Int, totalAmount: Long) {
        val member = transferCircleDao.findByName(name)
        if (member != null) {
            transferCircleDao.update(
                member.copy(
                    totalTransfers = transferCount,
                    totalAmountPaisa = totalAmount
                )
            )
        }
    }
    
    /**
     * Remove a member from the circle
     */
    suspend fun removeMember(id: Long) {
        transferCircleDao.deleteById(id)
    }
    
    /**
     * Get member by ID
     */
    suspend fun getMemberById(id: Long): TransferCircleMember? {
        return transferCircleDao.getMemberById(id)
    }
    
    /**
     * Get member by name
     */
    suspend fun getMemberByName(name: String): TransferCircleMember? {
        return transferCircleDao.findByName(name)
    }
    
    /**
     * Get total count of members
     */
    suspend fun getMemberCount(): Int {
        return transferCircleDao.getCount()
    }
    
    /**
     * Get all recipient names (including ignored ones)
     */
    fun getAllRecipientNames(): Flow<List<String>> {
        return transferCircleDao.getAllRecipientNames()
    }
    
    /**
     * Ignore a member (exclude from suggestions and circle)
     */
    suspend fun ignoreMember(name: String) {
        val existing = transferCircleDao.findByName(name)
        if (existing != null) {
            transferCircleDao.update(existing.copy(isIgnored = true))
        } else {
            val member = TransferCircleMember(
                recipientName = name,
                addedDate = System.currentTimeMillis(),
                isIgnored = true
            )
            transferCircleDao.insert(member)
        }
    }
}
