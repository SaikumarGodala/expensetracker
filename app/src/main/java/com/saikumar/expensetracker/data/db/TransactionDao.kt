package com.saikumar.expensetracker.data.db

import androidx.room.*
import com.saikumar.expensetracker.data.entity.Category
import com.saikumar.expensetracker.data.entity.Transaction
import kotlinx.coroutines.flow.Flow

data class TransactionWithCategory(
    @Embedded val transaction: Transaction,
    @Relation(
        parentColumn = "categoryId",
        entityColumn = "id"
    )
    val category: Category
)



data class TransactionSummary(
    val totalIncome: Long,
    val totalExpense: Long, // Fixed + Variable
    val totalInvestment: Long,
    val totalVehicle: Long,
    val totalRefund: Long,
    val totalFixed: Long,
    val totalVariable: Long
)

@Dao
interface TransactionDao {
    @androidx.room.Transaction
    @Query("SELECT * FROM transactions WHERE timestamp >= :startTimestamp AND timestamp <= :endTimestamp AND deletedAt IS NULL AND transactionType != 'STATEMENT' ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    fun getTransactionsPaged(startTimestamp: Long, endTimestamp: Long, limit: Int, offset: Int): Flow<List<TransactionWithCategory>>

    @androidx.room.Transaction
    @Query("SELECT * FROM transactions WHERE timestamp >= :startTimestamp AND timestamp <= :endTimestamp AND deletedAt IS NULL AND transactionType != 'STATEMENT' ORDER BY timestamp ASC LIMIT :limit OFFSET :offset")
    suspend fun getTransactionsPagedSync(startTimestamp: Long, endTimestamp: Long, limit: Int, offset: Int): List<TransactionWithCategory>

    @Query("SELECT COUNT(*) FROM transactions WHERE timestamp >= :startTimestamp AND timestamp <= :endTimestamp AND deletedAt IS NULL AND transactionType != 'STATEMENT'")
    suspend fun getTransactionCount(startTimestamp: Long, endTimestamp: Long): Int

    @androidx.room.Transaction
    @Query("SELECT * FROM transactions WHERE timestamp >= :startTimestamp AND timestamp <= :endTimestamp AND deletedAt IS NULL AND transactionType != 'STATEMENT' ORDER BY timestamp DESC")
    fun getTransactionsInPeriod(startTimestamp: Long, endTimestamp: Long): Flow<List<TransactionWithCategory>>

    @androidx.room.Transaction
    @Query("SELECT * FROM transactions WHERE timestamp >= :startTimestamp AND timestamp <= :endTimestamp AND deletedAt IS NULL AND transactionType = 'STATEMENT' ORDER BY timestamp DESC")
    fun getStatementsInPeriod(startTimestamp: Long, endTimestamp: Long): Flow<List<TransactionWithCategory>>

    @androidx.room.Transaction
    @Query("""
        SELECT t.* FROM transactions t
        INNER JOIN categories c ON t.categoryId = c.id
        WHERE (t.merchantName LIKE '%' || :query || '%'
           OR t.note LIKE '%' || :query || '%'
           OR c.name LIKE '%' || :query || '%')
        AND t.deletedAt IS NULL
        AND t.transactionType != 'STATEMENT'
        ORDER BY t.timestamp DESC
        LIMIT :limit
    """)
    fun searchTransactions(query: String, limit: Int = 500): Flow<List<TransactionWithCategory>>

    @Query("""
        SELECT
            SUM(CASE WHEN t.transactionType IN ('INCOME', 'CASHBACK') THEN t.amountPaisa ELSE 0 END) as totalIncome,
            SUM(CASE WHEN t.transactionType = 'EXPENSE' AND c.type IN ('FIXED_EXPENSE', 'VARIABLE_EXPENSE') THEN t.amountPaisa ELSE 0 END) as totalExpense,
            SUM(CASE WHEN c.type = 'INVESTMENT' THEN t.amountPaisa ELSE 0 END) as totalInvestment,
            SUM(CASE WHEN c.type = 'VEHICLE' THEN t.amountPaisa ELSE 0 END) as totalVehicle,
            SUM(CASE WHEN t.transactionType = 'REFUND' THEN t.amountPaisa ELSE 0 END) as totalRefund,
            SUM(CASE WHEN c.type = 'FIXED_EXPENSE' THEN t.amountPaisa ELSE 0 END) as totalFixed,
            SUM(CASE WHEN c.type = 'VARIABLE_EXPENSE' THEN t.amountPaisa ELSE 0 END) as totalVariable
        FROM transactions t
        LEFT JOIN categories c ON t.categoryId = c.id
        WHERE t.timestamp >= :startTimestamp
        AND t.timestamp <= :endTimestamp
        AND t.deletedAt IS NULL
        AND t.status = 'COMPLETED'
        AND t.transactionType NOT IN ('LIABILITY_PAYMENT', 'TRANSFER', 'PENDING', 'IGNORE', 'STATEMENT')
    """)
    fun getTransactionSummary(startTimestamp: Long, endTimestamp: Long): Flow<TransactionSummary>

    @Query("SELECT * FROM transactions WHERE id = :id AND deletedAt IS NULL")
    suspend fun getById(id: Long): Transaction?

    /**
     * Insert a transaction with idempotent duplicate handling.
     * Uses OnConflictStrategy.IGNORE to prevent duplicate SMS from creating duplicate transactions.
     * Returns -1 if the insert was ignored due to conflict.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTransaction(transaction: Transaction): Long

    @Update
    suspend fun updateTransaction(transaction: Transaction)

    @Query("UPDATE transactions SET deletedAt = :deletedAt WHERE id = :id")
    suspend fun softDelete(id: Long, deletedAt: Long)
    
    @Query("UPDATE transactions SET deletedAt = NULL WHERE id = :id")
    suspend fun restore(id: Long)
    
    @Delete
    suspend fun hardDelete(transaction: Transaction)

    @Query("SELECT EXISTS(SELECT 1 FROM transactions WHERE smsHash = :hash AND deletedAt IS NULL LIMIT 1)")
    suspend fun existsBySmsHash(hash: String): Boolean

    @Query("SELECT smsHash FROM transactions WHERE smsHash IS NOT NULL AND deletedAt IS NULL")
    suspend fun getAllSmsHashes(): List<String>

    /**
     * BUG FIX: Enhanced deduplication - Check for duplicate by reference number + amount.
     */
    @Query("""
        SELECT EXISTS(
            SELECT 1 FROM transactions 
            WHERE referenceNo = :refNo 
            AND amountPaisa = :amountPaisa 
            AND referenceNo IS NOT NULL
            AND deletedAt IS NULL
            LIMIT 1
        )
    """)
    suspend fun existsByReferenceAndAmount(refNo: String?, amountPaisa: Long): Boolean

    /**
     * BUG FIX: Find potential duplicates by amount and timestamp (time window check).
     */
    @Query("""
        SELECT * FROM transactions 
        WHERE amountPaisa = :amountPaisa 
        AND timestamp BETWEEN :timestampStart AND :timestampEnd
        AND deletedAt IS NULL
    """)
    suspend fun findPotentialDuplicates(
        amountPaisa: Long, 
        timestampStart: Long, 
        timestampEnd: Long
    ): List<Transaction>

    @Query("SELECT * FROM transactions WHERE categoryId = :categoryId AND deletedAt IS NULL AND transactionType != 'STATEMENT'")
    suspend fun getTransactionsByCategoryId(categoryId: Long): List<Transaction>

    @Query("SELECT * FROM transactions WHERE merchantName = :merchantName AND deletedAt IS NULL AND transactionType != 'STATEMENT' ORDER BY timestamp DESC")
    suspend fun getTransactionsByMerchant(merchantName: String): List<Transaction>

    @Query("SELECT * FROM transactions WHERE smsSnippet LIKE '%' || :pattern || '%' AND deletedAt IS NULL AND transactionType != 'STATEMENT' ORDER BY timestamp DESC")
    suspend fun getTransactionsBySnippetPattern(pattern: String): List<Transaction>

    @Query("SELECT * FROM transactions WHERE LOWER(merchantName) = LOWER(:merchantName) AND deletedAt IS NULL")
    suspend fun getByMerchantName(merchantName: String): List<Transaction>

    @Query("SELECT * FROM transactions WHERE upiId = :upiId AND deletedAt IS NULL AND transactionType != 'STATEMENT' ORDER BY timestamp DESC")
    suspend fun getTransactionsByUpiId(upiId: String): List<Transaction>

    @Update
    suspend fun updateTransactions(transactions: List<Transaction>)
    
    @androidx.room.Transaction
    @Query("""
        SELECT t.id, t.amountPaisa, t.timestamp, t.transactionType, t.accountNumberLast4, s.sender as smsSender 
        FROM transactions t 
        LEFT JOIN sms_raw s ON t.rawSmsId = s.rawSmsId
        WHERE t.deletedAt IS NULL 
        ORDER BY t.timestamp DESC
    """)
    suspend fun getAllTransactionsSync(): List<TransactionPairCandidate>
    
    @Query("""
        SELECT * FROM transactions 
        WHERE merchantName IS NOT NULL 
        AND deletedAt IS NULL 
        ORDER BY timestamp DESC
    """)
    suspend fun getAllForMlExport(): List<Transaction>
    
    @Query("UPDATE transactions SET categoryId = :categoryId, transactionType = :type WHERE merchantName IN (:merchantNames) AND deletedAt IS NULL")
    suspend fun updateTransactionsForMerchants(
        merchantNames: List<String>, 
        categoryId: Long, 
        type: com.saikumar.expensetracker.data.entity.TransactionType
    )

    @androidx.room.Transaction
    @Query("""
        SELECT t.merchantName, t.categoryId, t.confidenceScore, t.fullSmsBody, s.sender as smsSender, t.timestamp 
        FROM transactions t 
        LEFT JOIN sms_raw s ON t.rawSmsId = s.rawSmsId
        WHERE (t.merchantName IS NOT NULL OR t.fullSmsBody IS NOT NULL)
        AND t.deletedAt IS NULL 
        ORDER BY t.timestamp DESC
    """)
    suspend fun getAllForMlExportWithSender(): List<MlExportCandidate>
    
    @Query("SELECT SUM(t.amountPaisa) FROM transactions t INNER JOIN categories c ON t.categoryId = c.id WHERE c.name = 'Interest' AND t.deletedAt IS NULL")
    suspend fun getTotalInterestPaisa(): Long?

    @Query("""
        SELECT SUM(t.amountPaisa) 
        FROM transactions t 
        INNER JOIN categories c ON t.categoryId = c.id 
        WHERE c.name = 'Salary' 
        AND t.timestamp BETWEEN :start AND :end 
        AND t.deletedAt IS NULL
    """)
    suspend fun getSalaryForPeriod(start: Long, end: Long): Long?

    @Query("""
        SELECT SUM(t.amountPaisa) 
        FROM transactions t 
        INNER JOIN categories c ON t.categoryId = c.id 
        WHERE c.type IN ('FIXED_EXPENSE', 'VARIABLE_EXPENSE') 
        AND t.timestamp BETWEEN :start AND :end 
        AND t.deletedAt IS NULL
    """)
    suspend fun getTotalExpenseForPeriod(start: Long, end: Long): Long?

    @Query("""
        SELECT c.name as categoryName, c.icon as categoryIcon, SUM(t.amountPaisa) as totalAmount
        FROM transactions t
        JOIN categories c ON t.categoryId = c.id
        WHERE t.timestamp BETWEEN :start AND :end
        AND t.deletedAt IS NULL
        AND t.transactionType = 'EXPENSE'
        AND t.status = 'COMPLETED'
        GROUP BY c.id
        ORDER BY totalAmount DESC
    """)
    suspend fun getCategorySpending(start: Long, end: Long): List<CategorySpending>

    @Query("""
        SELECT strftime('%Y-%m', t.timestamp / 1000, 'unixepoch', 'localtime') as month, SUM(t.amountPaisa) as totalAmount
        FROM transactions t
        WHERE t.timestamp BETWEEN :start AND :end
        AND t.deletedAt IS NULL
        AND t.transactionType = 'EXPENSE'
        AND t.status = 'COMPLETED'
        GROUP BY month
        ORDER BY month ASC
    """)
    suspend fun getMonthlySpending(start: Long, end: Long): List<MonthlySpending>

    @Query("""
        SELECT strftime('%Y-%m', t.timestamp / 1000, 'unixepoch', 'localtime') as month, SUM(t.amountPaisa) as totalAmount
        FROM transactions t
        WHERE t.categoryId = :categoryId
        AND t.timestamp BETWEEN :start AND :end
        AND t.deletedAt IS NULL
        AND t.transactionType = 'EXPENSE'
        AND t.status = 'COMPLETED'
        GROUP BY month
        ORDER BY month ASC
    """)
    suspend fun getMonthlySpendingForCategory(categoryId: Long, start: Long, end: Long): List<MonthlySpending>

    @Query("""
        SELECT strftime('%Y', t.timestamp / 1000, 'unixepoch', 'localtime') as year, SUM(t.amountPaisa) as totalAmount
        FROM transactions t
        WHERE t.deletedAt IS NULL
        AND t.transactionType = 'EXPENSE'
        AND t.status = 'COMPLETED'
        GROUP BY year
        ORDER BY year DESC
    """)
    suspend fun getYearlySpending(): List<YearlySpending>

    @Query("""
        SELECT strftime('%Y', t.timestamp / 1000, 'unixepoch', 'localtime') as year, SUM(t.amountPaisa) as totalAmount
        FROM transactions t
        WHERE t.categoryId = :categoryId
        AND t.deletedAt IS NULL
        AND t.transactionType = 'EXPENSE'
        AND t.status = 'COMPLETED'
        GROUP BY year
        ORDER BY year DESC
    """)
    suspend fun getYearlySpendingForCategory(categoryId: Long): List<YearlySpending>
    @androidx.room.Transaction
    @Query("""
        SELECT t.* FROM transactions t
        INNER JOIN categories c ON t.categoryId = c.id
        WHERE (t.merchantName LIKE '%' || :query || '%' 
           OR t.note LIKE '%' || :query || '%' 
           OR c.name LIKE '%' || :query || '%')
        AND t.timestamp >= :startTime AND t.timestamp <= :endTime
        AND t.deletedAt IS NULL
        ORDER BY t.timestamp DESC
    """)
    fun searchTransactionsInRange(query: String, startTime: Long, endTime: Long): Flow<List<TransactionWithCategory>>

    @androidx.room.Transaction
    @Query("""
        SELECT t.* FROM transactions t
        INNER JOIN categories c ON t.categoryId = c.id
        WHERE (t.merchantName LIKE '%' || :query || '%' 
           OR t.note LIKE '%' || :query || '%' 
           OR c.name LIKE '%' || :query || '%')
        AND t.deletedAt IS NULL
        ORDER BY t.timestamp DESC
    """)
    fun searchAllTransactions(query: String): Flow<List<TransactionWithCategory>>
}

data class TransactionPairCandidate(
    val id: Long,
    val amountPaisa: Long,
    val timestamp: Long,
    val transactionType: com.saikumar.expensetracker.data.entity.TransactionType,
    val accountNumberLast4: String?,
    val smsSender: String?
)

data class MlExportCandidate(
    val merchantName: String?,
    val categoryId: Long,
    val confidenceScore: Int,
    val fullSmsBody: String?,
    val smsSender: String?,
    val timestamp: Long
)

data class CategorySpending(
    val categoryName: String,
    val categoryIcon: String,
    val totalAmount: Long
)

data class MonthlySpending(
    val month: String, // Format: YYYY-MM
    val totalAmount: Long
)

data class YearlySpending(
    val year: String, // Format: YYYY
    val totalAmount: Long
)
