package com.saikumar.expensetracker.data.repository

import com.saikumar.expensetracker.data.db.*
import com.saikumar.expensetracker.data.entity.*
import com.saikumar.expensetracker.domain.DuplicateDetector
import com.saikumar.expensetracker.domain.MerchantManager
import com.saikumar.expensetracker.data.db.TransactionSummary
import kotlinx.coroutines.flow.Flow

class ExpenseRepository(
    private val categoryDao: CategoryDao,
    val transactionDao: TransactionDao,  // Made public for salary day detection
    private val accountDao: AccountDao? = null,
    private val merchantPatternDao: MerchantPatternDao? = null,
    private val merchantMemoryDao: MerchantMemoryDao? = null,
    val transactionLinkDao: TransactionLinkDao,  // P1 Fix #4
    val pendingTransactionDao: PendingTransactionDao // CRITICAL FIX 3
) {
    private val duplicateDetector = DuplicateDetector(transactionDao)
    private val merchantManager = merchantMemoryDao?.let { MerchantManager(it) }


    val allEnabledCategories: Flow<List<Category>> = categoryDao.getAllEnabledCategories()
    val allCategories: Flow<List<Category>> = categoryDao.getAllCategories()
    val allTransactionLinks: Flow<List<TransactionLink>> = transactionLinkDao.getAllLinks()

    /**
     * Get transaction links within a specific time period
     * Performance optimization: Filters links to match transaction period
     */
    fun getTransactionLinksInPeriod(startTimestamp: Long, endTimestamp: Long): Flow<List<TransactionLink>> {
        return transactionLinkDao.getLinksInPeriod(startTimestamp, endTimestamp)
    }

    /**
     * Get transactions within a time range.
     * @param startTimestamp UTC epoch millis for range start
     * @param endTimestamp UTC epoch millis for range end
     */
    fun getTransactionsInPeriod(startTimestamp: Long, endTimestamp: Long): Flow<List<TransactionWithCategory>> {
        return transactionDao.getTransactionsInPeriod(startTimestamp, endTimestamp)
    }

    fun getStatementsInPeriod(startTimestamp: Long, endTimestamp: Long): Flow<List<TransactionWithCategory>> {
        return transactionDao.getStatementsInPeriod(startTimestamp, endTimestamp)
    }

    fun getTransactionsPaged(startTimestamp: Long, endTimestamp: Long, limit: Int, offset: Int): Flow<List<TransactionWithCategory>> {
        return transactionDao.getTransactionsPaged(startTimestamp, endTimestamp, limit, offset)
    }

    fun getTransactionSummary(startTimestamp: Long, endTimestamp: Long): Flow<TransactionSummary> {
        return transactionDao.getTransactionSummary(startTimestamp, endTimestamp)
    }

    fun searchTransactions(query: String, limit: Int = 500): Flow<List<TransactionWithCategory>> {
        return transactionDao.searchTransactions(query, limit)
    }

    /**
     * Insert a transaction with idempotent duplicate handling.
     * 
     * CRITICAL FIX 2: Returns -1 if the insert was ignored due to a conflict
     * (e.g., duplicate smsHash). This is expected behavior, not an error.
     * 
     * @return Row ID of inserted transaction, or -1 if ignored due to conflict
     */
    suspend fun insertTransaction(transaction: Transaction): Long {
        val rowId = transactionDao.insertTransaction(transaction)
        if (rowId == -1L) {
            android.util.Log.d("ExpenseRepository", 
                "INSERT_IGNORED: Duplicate smsHash detected - transaction was silently ignored (hash=${transaction.smsHash})")
        }
        return rowId
    }

    suspend fun updateTransaction(transaction: Transaction) {
        transactionDao.updateTransaction(transaction)
    }

    suspend fun deleteTransaction(transaction: Transaction) {
        transactionDao.softDelete(transaction.id, System.currentTimeMillis())
    }

    /**
     * Check if a transaction with this SMS hash already exists.
     * @param smsHash SHA-256 hash prefix of the SMS body
     * @return true if duplicate exists
     */
    suspend fun transactionExistsBySmsHash(smsHash: String): Boolean {
        return transactionDao.existsBySmsHash(smsHash)
    }

    /**
     * BUG FIX 1: Check if duplicate exists by reference number + amount.
     * @param refNo Transaction reference number (UPI/NEFT/IMPS ref)
     * @param amountPaisa Amount in paisa
     * @return true if duplicate exists
     */
    suspend fun transactionExistsByReferenceAndAmount(refNo: String?, amountPaisa: Long): Boolean {
        return transactionDao.existsByReferenceAndAmount(refNo, amountPaisa)
    }

    /**
     * BUG FIX 1: Find potential duplicates by amount and timestamp window.
     * @param amountPaisa Exact amount in paisa
     * @param timestampStart Start of time window
     * @param timestampEnd End of time window
     * @return List of potential duplicate transactions
     */
    suspend fun findPotentialDuplicates(
        amountPaisa: Long,
        timestampStart: Long,
        timestampEnd: Long
    ): List<Transaction> {
        return transactionDao.findPotentialDuplicates(amountPaisa, timestampStart, timestampEnd)
    }

    suspend fun isDuplicateTransaction(
        smsHash: String,
        referenceNo: String?,
        amountPaisa: Long,
        timestamp: Long,
        merchantName: String? = null,
        accountNumberLast4: String? = null
    ): DuplicateCheckResult {
        val result = duplicateDetector.check(
            smsHash, referenceNo, amountPaisa, timestamp, merchantName, accountNumberLast4
        )
        // Map domain result to repository result to preserve API compatibility
        return DuplicateCheckResult(
            isDuplicate = result.isDuplicate,
            tier = result.tier?.let { DuplicateTier.valueOf(it.name) },
            confidence = result.confidence,
            reason = result.reason,
            matchedTransactionId = result.matchedTransactionId
        )
    }

    /**
     * Result of duplicate detection check
     */
    data class DuplicateCheckResult(
        val isDuplicate: Boolean,
        val tier: DuplicateTier?,
        val confidence: Double,
        val reason: String,
        val matchedTransactionId: Long? = null
    )

    /**
     * Which tier of duplicate detection caught the match
     */
    enum class DuplicateTier {
        EXACT_HASH,       // Tier 1: Byte-for-byte identical SMS
        REFERENCE_MATCH,  // Tier 2: Same reference number + amount
        FUZZY_MATCH       // Tier 3: Amount + time + context
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

    // ========== MERCHANT MEMORY OPERATIONS (Soft Learning) ==========
    
    suspend fun getLearnedMerchantCategory(normalizedMerchant: String): MerchantMemory? {
        return merchantManager?.getLearnedCategory(normalizedMerchant)
    }
    
    suspend fun recordMerchantOccurrence(
        merchantName: String,
        categoryId: Long,
        transactionType: String?,
        timestamp: Long
    ) {
        merchantManager?.recordOccurrence(merchantName, categoryId, transactionType, timestamp)
    }

    suspend fun userConfirmMerchantMapping(
        merchantName: String,
        categoryId: Long,
        transactionType: String?,
        timestamp: Long
    ) {
        merchantManager?.confirmMapping(merchantName, categoryId, transactionType, timestamp)
    }
    suspend fun insertTransactionLinks(links: List<TransactionLink>) {
        transactionLinkDao.insertLinks(links)
    }
    
    val allLinksWithDetails: Flow<List<LinkWithDetails>> =
        transactionLinkDao.getAllLinksWithDetails()
        
    suspend fun deleteLink(linkId: Long) {
        transactionLinkDao.deleteLinkById(linkId)
    }

    // P1 Fix: Optimized similarity search
    suspend fun getTransactionsByMerchant(merchantName: String): List<Transaction> {
        return transactionDao.getTransactionsByMerchant(merchantName)
    }

    suspend fun getTransactionsBySnippetPattern(pattern: String): List<Transaction> {
        return transactionDao.getTransactionsBySnippetPattern(pattern)
    }

    /**
     * Find transactions similar to the given transaction for batch categorization.
     * Centralized logic to avoid duplication in ViewModels.
     *
     * Priority:
     * 1. Match by merchant name (highest confidence)
     * 2. Match by UPI ID (medium confidence, fallback when merchant missing)
     * 3. Empty list (no matches)
     */
    suspend fun findSimilarTransactions(transaction: Transaction): com.saikumar.expensetracker.sms.SimilarityResult {
        val merchantName = transaction.merchantName
        val upiId = transaction.upiId

        // Priority 1: Match by merchant name
        if (!merchantName.isNullOrBlank()) {
            val matches = transactionDao.getTransactionsByMerchant(merchantName)
                .filter { it.id != transaction.id }
            return com.saikumar.expensetracker.sms.SimilarityResult(
                matchedTransactions = matches,
                matchType = "MERCHANT_NAME",
                confidence = 0.9f
            )
        }

        // Priority 2: Match by UPI ID (fallback)
        if (!upiId.isNullOrBlank()) {
            val matches = transactionDao.getTransactionsByUpiId(upiId)
                .filter { it.id != transaction.id }
            return com.saikumar.expensetracker.sms.SimilarityResult(
                matchedTransactions = matches,
                matchType = "UPI_ID",
                confidence = 0.85f
            )
        }

        // No matches
        return com.saikumar.expensetracker.sms.SimilarityResult(
            matchedTransactions = emptyList(),
            matchType = "NO_MATCH",
            confidence = 0.0f
        )
    }

    // ========== PENDING TRANSACTION OPERATIONS (CRITICAL FIX 3) ==========
    
    val allPendingTransactions: Flow<List<PendingTransaction>> = pendingTransactionDao.getAllPending()
    
    suspend fun insertPendingTransaction(pending: PendingTransaction): Long {
        return pendingTransactionDao.insert(pending)
    }
    
    suspend fun updatePendingTransaction(pending: PendingTransaction) {
        pendingTransactionDao.update(pending)
    }
    
    suspend fun getPendingTransactionByHash(hash: String): PendingTransaction? {
        return pendingTransactionDao.getByHash(hash)
    }
    
    suspend fun markPendingAsProcessed(id: Long, realizedId: Long) {
        pendingTransactionDao.markAsProcessed(id, realizedId)
    }
    
    suspend fun cancelPendingTransaction(id: Long) {
        pendingTransactionDao.cancel(id)
    }

    /**
     * Retroactively update transactions for new Transfer Circle members to P2P.
     */
    suspend fun updateTransactionsToP2P(merchantNames: List<String>) {
        val p2pCategory = categoryDao.getCategoryByName("P2P Transfers")
        if (p2pCategory != null) {
            transactionDao.updateTransactionsForMerchants(
                merchantNames,
                p2pCategory.id,
                com.saikumar.expensetracker.data.entity.TransactionType.TRANSFER
            )
        }
    }

    // ========== ANALYTICS OPERATIONS ==========

    suspend fun getCategorySpending(start: Long, end: Long): List<CategorySpending> {
        return transactionDao.getCategorySpending(start, end)
    }

    suspend fun getMonthlySpending(start: Long, end: Long): List<MonthlySpending> {
        return transactionDao.getMonthlySpending(start, end)
    }

    suspend fun getMonthlySpendingForCategory(categoryId: Long, start: Long, end: Long): List<MonthlySpending> {
        return transactionDao.getMonthlySpendingForCategory(categoryId, start, end)
    }

    suspend fun getYearlySpending(): List<YearlySpending> {
        return transactionDao.getYearlySpending()
    }

    suspend fun getYearlySpendingForCategory(categoryId: Long): List<YearlySpending> {
        return transactionDao.getYearlySpendingForCategory(categoryId)
    }

    suspend fun seedCategories() {
        CategorySeeder.seedDefaultsIfNeeded(categoryDao)
    }
}
