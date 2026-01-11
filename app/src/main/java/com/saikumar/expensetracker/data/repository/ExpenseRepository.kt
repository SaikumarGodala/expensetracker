package com.saikumar.expensetracker.data.repository

import com.saikumar.expensetracker.data.db.*
import com.saikumar.expensetracker.data.entity.*
import kotlinx.coroutines.flow.Flow

class ExpenseRepository(
    private val categoryDao: CategoryDao,
    private val transactionDao: TransactionDao,
    private val accountDao: AccountDao? = null,
    private val merchantPatternDao: MerchantPatternDao? = null
) {
    val allEnabledCategories: Flow<List<Category>> = categoryDao.getAllEnabledCategories()
    val allCategories: Flow<List<Category>> = categoryDao.getAllCategories()

    /**
     * Get transactions within a time range.
     * @param startTimestamp UTC epoch millis for range start
     * @param endTimestamp UTC epoch millis for range end
     */
    fun getTransactionsInPeriod(startTimestamp: Long, endTimestamp: Long): Flow<List<TransactionWithCategory>> {
        return transactionDao.getTransactionsInPeriod(startTimestamp, endTimestamp)
    }

    suspend fun insertTransaction(transaction: Transaction) {
        transactionDao.insertTransaction(transaction)
    }

    suspend fun updateTransaction(transaction: Transaction) {
        transactionDao.updateTransaction(transaction)
    }

    suspend fun deleteTransaction(transaction: Transaction) {
        transactionDao.deleteTransaction(transaction)
    }

    /**
     * Check if a transaction with this SMS hash already exists.
     * @param smsHash SHA-256 hash prefix of the SMS body
     * @return true if duplicate exists
     */
    suspend fun transactionExistsBySmsHash(smsHash: String): Boolean {
        return transactionDao.existsBySmsHash(smsHash)
    }

    suspend fun updateCategory(category: Category) {
        categoryDao.updateCategory(category)
    }

    suspend fun insertCategories(categories: List<Category>) {
        categoryDao.insertCategories(categories)
    }

    suspend fun insertCategory(category: Category): Long {
        return categoryDao.insertCategory(category)
    }

    suspend fun deleteCategory(category: Category) {
        categoryDao.deleteCategory(category)
    }

    // Account operations
    fun getAllAccounts(): Flow<List<Account>>? = accountDao?.getAllAccounts()
    
    suspend fun getDefaultAccount(): Account? = accountDao?.getDefaultAccount()
    
    suspend fun insertAccount(account: Account): Long? = accountDao?.insertAccount(account)

    // Merchant pattern operations
    fun getAllMerchantPatterns(): Flow<List<MerchantPattern>>? = merchantPatternDao?.getAllPatterns()
    
    suspend fun getMerchantPatternByKeyword(keyword: String): MerchantPattern? {
        return merchantPatternDao?.getPatternByKeyword(keyword)
    }
    
    suspend fun insertMerchantPattern(pattern: MerchantPattern): Long? {
        return merchantPatternDao?.insertPattern(pattern)
    }
    
    suspend fun insertMerchantPatterns(patterns: List<MerchantPattern>) {
        merchantPatternDao?.insertPatterns(patterns)
    }
}
