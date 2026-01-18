package com.saikumar.expensetracker.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.saikumar.expensetracker.data.entity.UserAccount

@Dao
interface UserAccountDao {

    @Query("SELECT * FROM user_accounts")
    suspend fun getAllAccounts(): List<UserAccount>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(account: UserAccount)

    @Query("SELECT EXISTS(SELECT 1 FROM user_accounts WHERE accountNumberLast4 = :last4 LIMIT 1)")
    suspend fun isMyAccount(last4: String): Boolean

    @Query("SELECT * FROM user_accounts WHERE accountNumberLast4 = :last4 LIMIT 1")
    suspend fun getAccount(last4: String): UserAccount?

    @Query("DELETE FROM user_accounts")
    suspend fun clearAll()
    
    @Query("UPDATE user_accounts SET accountHolderName = :holderName WHERE accountNumberLast4 = :last4")
    suspend fun updateAccountHolderName(last4: String, holderName: String)
}
