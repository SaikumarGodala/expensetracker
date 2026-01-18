package com.saikumar.expensetracker.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.saikumar.expensetracker.data.entity.MerchantAlias

@Dao
interface MerchantAliasDao {
    @Query("SELECT * FROM merchant_aliases WHERE rawName = :rawName")
    suspend fun getAlias(rawName: String): MerchantAlias?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(alias: MerchantAlias)
    
    @Query("SELECT * FROM merchant_aliases")
    suspend fun getAllAliases(): List<MerchantAlias>
}
