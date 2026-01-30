package com.saikumar.expensetracker.sms

import android.content.Context
import android.util.Log
import com.saikumar.expensetracker.ExpenseTrackerApplication
import com.saikumar.expensetracker.data.entity.*
import com.saikumar.expensetracker.core.AppConstants
import com.saikumar.expensetracker.data.db.AppDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import com.saikumar.expensetracker.util.ClassificationDebugLogger
import com.saikumar.expensetracker.data.entity.MerchantAlias
import com.saikumar.expensetracker.data.db.TransactionWithCategory
import com.saikumar.expensetracker.data.db.TransactionPairCandidate
import com.saikumar.expensetracker.sms.InboxScanner
import com.saikumar.expensetracker.sms.DuplicateChecker
import com.saikumar.expensetracker.data.repository.CategorySeeder

data class ParsedTransaction(
    val sender: String,
    val body: String,
    val timestamp: Long,
    val senderType: SenderClassifier.SenderType,
    val transactionType: TransactionType,
    val amountPaisa: Long?,
    val counterparty: CounterpartyExtractor.Counterparty,
    val category: String,
    val isDebit: Boolean?,
    val accountTypeDetected: AccountType = AccountType.UNKNOWN,
    val classificationTrace: List<String> = emptyList()
)

data class SimilarityResult(
    val matchedTransactions: List<Transaction>,
    val matchType: String,
    val confidence: Float
)

sealed class ProcessingResult {
    data class Success(val transaction: ParsedTransaction, val gateDecision: LedgerDecision.Insert) : ProcessingResult()
    data class Dropped(val decision: LedgerDecision.Drop) : ProcessingResult()
    object Ignored : ProcessingResult()
}

object SmsProcessor {
    private const val TAG = "SmsProcessor"

    private lateinit var duplicateDetector: com.saikumar.expensetracker.domain.DuplicateDetector

    private val OPTIONS = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
    private val BALANCE_REGEX = Regex("(?:balance|bal).*?Rs\\.?\\s*([\\d,]+(?:\\.\\d+)?)", OPTIONS)
    private val CONTRIBUTION_REGEX = Regex("(?:contribution|credit).*?Rs\\.?\\s*([\\d,]+(?:\\.\\d+)?)", OPTIONS)
    private val MONTH_REGEX = Regex("(?:for|month).*?(\\w{3})['-]?(\\d{2,4})", OPTIONS)
    
    // ========================================
    // HELPER DATA CLASS FOR DEDUPLICATION
    // ========================================
    
    /**
     * Context object for transaction resolution. Bundles all the data needed
     * to resolve category, transaction type, and build a Transaction object.
     * Used to deduplicate logic across scanInbox, processAndInsert, and reclassifyTransactions.
     */
    data class TransactionResolutionContext(
        val parsed: ParsedTransaction,
        val categoryMap: Map<String, Category>,
        val trustedNames: Set<String>? = null, // Pre-loaded for bulk ops, null for single-message
        val db: AppDatabase,
        val smsHash: String?
    )
    
    /**
     * Result of resolving category and transaction type.
     */
    data class ResolvedTransaction(
        val categoryId: Long,
        val transactionType: TransactionType,
        val isUntrustedP2P: Boolean,
        val merchantResult: SmsConstants.NormalizedMerchant
    )
    
    // ========================================
    // HELPER METHODS TO REDUCE DUPLICATION
    // ========================================

    /**
     * Check if a recipient name is in the transfer circle using fuzzy matching.
     * Handles name variations like "Rajesh K" vs "Rajesh Kumar".
     */
    private suspend fun isRecipientInCircle(
        recipientName: String,
        trustedNames: Set<String>?,
        db: AppDatabase
    ): Boolean {
        if (recipientName.isBlank()) return false

        val recipientLower = recipientName.lowercase()

        // Use pre-loaded set if available (bulk ops), else fall back to DB query (single ops)
        if (trustedNames != null) {
            // Exact match first
            if (trustedNames.contains(recipientLower)) return true

            // Fuzzy match against all trusted names
            for (trustedName in trustedNames) {
                if (areNamesEquivalent(recipientName, trustedName)) {
                    return true
                }
            }
            return false
        } else {
            // Single operation - use DB query
            return db.transferCircleDao().isInCircle(recipientName)
        }
    }

    /**
     * Check if two names are equivalent, handling variations.
     */
    private fun areNamesEquivalent(name1: String, name2: String): Boolean {
        val lower1 = name1.lowercase()
        val lower2 = name2.lowercase()

        if (lower1 == lower2) return true
        if (lower1.contains(lower2) || lower2.contains(lower1)) return true

        val parts1 = lower1.split("\\s+".toRegex()).filter { it.isNotEmpty() }
        val parts2 = lower2.split("\\s+".toRegex()).filter { it.isNotEmpty() }

        if (parts1.isEmpty() || parts2.isEmpty()) return false

        val significantParts1 = parts1.filter { it.length >= 3 }
        val significantParts2 = parts2.filter { it.length >= 3 }

        val commonSignificant = significantParts1.intersect(significantParts2.toSet())
        if (commonSignificant.size >= 2) return true

        val (shorterParts, longerParts) = if (parts1.size <= parts2.size) {
            parts1 to parts2
        } else {
            parts2 to parts1
        }

        val allShorterPartsMatched = shorterParts.all { shortPart ->
            longerParts.any { longPart ->
                shortPart == longPart ||
                (shortPart.length == 1 && longPart.startsWith(shortPart)) ||
                longPart.startsWith(shortPart)
            }
        }

        return allShorterPartsMatched && shorterParts.size >= 2
    }

    /**
     * Resolves category and transaction type based on P2P trust, invariants, etc.
     * This consolidates the duplicated logic from scanInbox, processAndInsert, reclassifyTransactions.
     */
    private suspend fun resolveTransactionDetails(
        ctx: TransactionResolutionContext
    ): ResolvedTransaction {
        val parsed = ctx.parsed
        var categoryId = ctx.categoryMap[parsed.category]?.id
            ?: ctx.categoryMap[AppConstants.Categories.UNCATEGORIZED]?.id
            ?: 1L

        // P2P TRUST CIRCLE LOGIC (IMPROVED with fuzzy name matching)
        // IMPORTANT: Skip this logic for credit card bill payments and other liability payments
        var isUntrustedP2P = false
        val isCreditCardPayment = parsed.category == AppConstants.Categories.CREDIT_BILL_PAYMENTS ||
                                  parsed.category?.contains("Credit Bill", ignoreCase = true) == true ||
                                  parsed.category?.contains("Liability", ignoreCase = true) == true

        if (!isCreditCardPayment &&
            (parsed.category == AppConstants.Categories.P2P_TRANSFERS || parsed.counterparty.type == CounterpartyExtractor.CounterpartyType.PERSON)) {
            val recipientName = parsed.counterparty.name

            val isTrusted = if (!recipientName.isNullOrBlank()) {
                isRecipientInCircle(recipientName, ctx.trustedNames, ctx.db)
            } else false

            val isDebit = parsed.isDebit ?: true

            if (isDebit) {
                if (!isTrusted) {
                    categoryId = ctx.categoryMap[AppConstants.Categories.MISCELLANEOUS]?.id ?: categoryId
                    isUntrustedP2P = true
                } else {
                    categoryId = ctx.categoryMap[AppConstants.Categories.P2P_TRANSFERS]?.id ?: categoryId
                }
            } else {
                if (!isTrusted) {
                    categoryId = ctx.categoryMap[AppConstants.Categories.OTHER_INCOME]?.id ?: categoryId
                } else {
                    categoryId = ctx.categoryMap[AppConstants.Categories.P2P_TRANSFERS]?.id ?: categoryId
                }
            }
        }
        
        // TRANSACTION TYPE RESOLUTION (Consolidated Logic)
        val categoryObj = ctx.categoryMap.values.find { it.id == categoryId } 
                          ?: ctx.categoryMap[parsed.category]
        
        val txnType = com.saikumar.expensetracker.domain.TransactionRuleEngine.resolveTransactionType(
            transaction = null,
            manualClassification = null,
            isSelfTransfer = false,
            category = categoryObj,
            isDebit = parsed.isDebit,
            counterpartyType = parsed.counterparty.type.name,
            isUntrustedP2P = isUntrustedP2P,
            upiId = parsed.counterparty.upiId
        )
        
        // MERCHANT NORMALIZATION
        val merchantResult = SmsConstants.normalizeMerchant(parsed.counterparty.name)
        
        return ResolvedTransaction(
            categoryId = categoryId,
            transactionType = txnType,
            isUntrustedP2P = isUntrustedP2P,
            merchantResult = merchantResult
        )
    }
    
    /**
     * Builds a Transaction entity from parsed data and resolved details.
     */
    private fun buildTransaction(
        parsed: ParsedTransaction,
        resolved: ResolvedTransaction,
        smsHash: String?,
        body: String,
        userAccounts: List<UserAccount> = emptyList() // Added argument
    ): Transaction {
        val extractedAccountNum = SmsConstants.extractAccountLast4(body)
        
        // Find matching account ID
        // TODO: UserAccount uses String PK, Transaction uses Long FK. Schema mismatch.
        // For now, we rely on accountNumberLast4 string match.
        val matchedAccountId: Long? = null
        
        return Transaction(
            amountPaisa = parsed.amountPaisa ?: 0L,
            categoryId = resolved.categoryId,
            timestamp = parsed.timestamp,
            source = TransactionSource.SMS,
            transactionType = resolved.transactionType,
            smsHash = smsHash,
            merchantName = resolved.merchantResult.normalized ?: resolved.merchantResult.raw,
            smsSnippet = parsed.body.take(100),
            fullSmsBody = parsed.body,
            accountNumberLast4 = extractedAccountNum,
            accountId = matchedAccountId, // P1 Logic Fix: Link to actual Account entity
            upiId = parsed.counterparty.upiId, // Store UPI VPA for display and grouping
            isExpenseEligible = resolved.transactionType == TransactionType.EXPENSE,
            entityType = when (parsed.counterparty.type) {
                CounterpartyExtractor.CounterpartyType.PERSON -> EntityType.PERSON
                CounterpartyExtractor.CounterpartyType.MERCHANT -> EntityType.BUSINESS
                else -> EntityType.UNKNOWN
            },
            confidenceScore = CategoryMapper.calculateConfidence(parsed.category)
        )
    }
    
    /**
     * Inserts a merchant alias if normalization produced a different name.
     */
    private suspend fun tryInsertMerchantAlias(
        db: AppDatabase,
        merchantResult: SmsConstants.NormalizedMerchant
    ) {
        if (merchantResult.shouldCreateAlias) {
            try {
                db.merchantAliasDao().insert(MerchantAlias(merchantResult.raw!!, merchantResult.normalized!!))
            } catch (e: Exception) { 
                Log.w(TAG, "Alias insert failed: ${e.message}") 
            }
        }
    }


    fun process(
        sender: String,
        body: String,
        timestamp: Long,
        rules: List<CategorizationRule> = emptyList(),
        categoryMap: Map<Long, String> = emptyMap(),
        forceSenderType: SenderClassifier.SenderType? = null,
        salaryCompanyNames: Set<String> = emptySet(),
        merchantMemories: Map<String, Long> = emptyMap(),
        context: Context? = null,
        userAccounts: List<UserAccount> = emptyList()
    ): ProcessingResult {
        val senderType = forceSenderType ?: SenderClassifier.classify(sender)
        if (senderType == SenderClassifier.SenderType.EXCLUDED) return ProcessingResult.Ignored

        // 0. PRE-GATE: Quick Drop for OTP/Spam (No extraction needed)
        LedgerGate.quickFilter(body)?.let { dropDecision ->
            return ProcessingResult.Dropped(dropDecision)
        }

        // 1. Basic Extraction (Amount, Type)
        val extraction = TransactionExtractor.extract(body, senderType)
        
        // 2. LEDGER GATE: Full Validation (Features, Type-based)
        val gateDecision = LedgerGate.evaluate(body, extraction.type, extraction.amountPaisa, sender)
        
        if (gateDecision is LedgerDecision.Drop) {
            return ProcessingResult.Dropped(gateDecision)
        }
        
        // 3. Continue Processing for Accepted Transactions
        val decision = gateDecision as LedgerDecision.Insert
        
        // Use the type decided by the Gate (it might have corrected it)
        val transactionType = decision.transactionType

        // 4. Counterparty Extraction
        var counterparty: CounterpartyExtractor.Counterparty

        // Skip counterparty extraction for STATEMENT types (they don't have merchants)
        if (transactionType == TransactionType.STATEMENT) {
            counterparty = CounterpartyExtractor.Counterparty(
                name = "Credit Card Statement",
                upiId = null,
                type = CounterpartyExtractor.CounterpartyType.MERCHANT,
                trace = listOf("Skipped for STATEMENT type")
            )
        } else {
            // REGEX MODE (using pattern matching)
            counterparty = CounterpartyExtractor.extract(body, transactionType)
            val currentTrace = counterparty.trace.toMutableList()
            currentTrace.add("Mode: REGEX")
            counterparty = counterparty.copy(trace = currentTrace)
        }
        
        // Log as Silver Label if valid
        if (counterparty.name != null && context != null) {
            // Auto-Training Log
             com.saikumar.expensetracker.util.TrainingDataLogger.logSample(
                context = context,
                smsBody = body,
                merchantName = counterparty.name,
                confidence = 1.0f, // Regex is high confidence rule
                isUserCorrection = false
            )
        }

        // 5. Classification
        val classificationTrace = mutableListOf<String>()
        val category = CategoryMapper.categorize(
            counterparty = counterparty,
            transactionType = transactionType,
            rules = rules,
            categoryMap = categoryMap,
            userAccounts = userAccounts,
            messageBody = body,
            salaryCompanyNames = salaryCompanyNames,
            merchantMemories = merchantMemories,
            trace = classificationTrace
        )

        val parsed = ParsedTransaction(
            sender = sender,
            body = body,
            timestamp = timestamp,
            senderType = senderType,
            transactionType = transactionType,
            amountPaisa = extraction.amountPaisa,
            counterparty = counterparty,
            category = category,
            isDebit = extraction.isDebit,
            accountTypeDetected = decision.accountType, // Use decision's account type
            classificationTrace = classificationTrace
        )
        
        return ProcessingResult.Success(parsed, decision)
    }

    suspend fun checkSalaryRecurrence(
        db: com.saikumar.expensetracker.data.db.AppDatabase,
        merchantName: String?,
        timestamp: Long,
        amountPaisa: Long?,
        history: List<TransactionWithCategory>? = null
    ): Boolean {
        if (merchantName.isNullOrBlank()) return false
        
        try {
            val historyList = if (history != null) {
                history
            } else {
                // Fallback to DB query if no history provided
                // Window: 20 to 60 days
                val oneDayMillis = 24 * 60 * 60 * 1000L
                val endRange = timestamp - (20 * oneDayMillis)
                val startRange = timestamp - (60 * oneDayMillis)
                db.transactionDao().getTransactionsInPeriod(startRange, endRange).first()
            }
            
            // Check for match
            val match = historyList.find { 
                it.transaction.transactionType == TransactionType.INCOME &&
                it.transaction.merchantName.equals(merchantName, ignoreCase = true)
            }
            
            if (match != null) {
                Log.d(TAG, "SALARY RECURRENCE FOUND: '${merchantName}' matches prev txn on ${java.time.Instant.ofEpochMilli(match.transaction.timestamp)}")
                return true
            } else {
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking salary recurrence", e)
            return false
        }
    }

    /**
     * EXTRACTED HELPER: Finalizes processing and inserts transaction.
     * Removes duplication between scanInbox and processAndInsert.
     */
    private suspend fun resolveAndInsert(
        context: Context,
        db: AppDatabase,
        parsed: ParsedTransaction,
        categoryMap: Map<String, Category>,
        trustedNames: Set<String>?,
        smsHash: String,
        body: String,
        salaryHistory: List<TransactionWithCategory>? = null,
        logId: String,
        userAccounts: List<UserAccount> = emptyList()
    ): Transaction? {
        // --- USE HELPER TO RESOLVE CATEGORY & TYPE ---
        val resolutionCtx = TransactionResolutionContext(
            parsed = parsed,
            categoryMap = categoryMap,
            trustedNames = trustedNames,
            db = db,
            smsHash = smsHash
        )
        var resolved = resolveTransactionDetails(resolutionCtx)
        
        // RECURRENCE CHECK FOR SALARY (specific post-processing)
        if (parsed.category == AppConstants.Categories.OTHER_INCOME && parsed.transactionType == TransactionType.INCOME) {
             if (checkSalaryRecurrence(db, parsed.counterparty.name, parsed.timestamp, parsed.amountPaisa, salaryHistory)) {
                 val salaryId = categoryMap[AppConstants.Categories.SALARY]?.id ?: resolved.categoryId
                 resolved = resolved.copy(categoryId = salaryId)
             }
        }
        
        // Insert merchant alias if needed
        tryInsertMerchantAlias(db, resolved.merchantResult)

        // Build transaction using helper
        val transaction = buildTransaction(parsed, resolved, smsHash, body, userAccounts)
        
        val rowId = db.transactionDao().insertTransaction(transaction)
        if (rowId > 0) {
            // ACCOUNT DISCOVERY: Detect and save bank accounts from SMS
            try {
                AccountDiscoveryManager.scanAndDiscover(body, parsed.sender, db.userAccountDao())
            } catch (e: Exception) {
                Log.w(TAG, "Account discovery failed: ${e.message}")
            }
            
            // Auto-discover account holder name for NEFT
            val extractedAccountNum = SmsConstants.extractAccountLast4(body)
            if (body.contains("NEFT", ignoreCase = true)) {
                try {
                    val holderName = CounterpartyExtractor.extractAccountHolderName(body)
                    if (holderName != null && extractedAccountNum != null) {
                        AccountDiscoveryManager.updateHolderName(db.userAccountDao(), extractedAccountNum, holderName)
                    }
                    if (resolved.transactionType == TransactionType.INCOME) {
                        trackNeftSalarySource(db, body, parsed.timestamp)
                    }
                } catch (e: Exception) { }
            }

            // SALARY DAY AUTO-DETECTION: Update cycle start day when salary is credited
            try {
                val category = categoryMap.values.find { it.id == resolved.categoryId }
                if (category?.name == "Salary") {
                    val transactionDate = java.time.Instant.ofEpochMilli(parsed.timestamp)
                        .atZone(java.time.ZoneId.systemDefault())
                        .toLocalDate()
                    val salaryDay = transactionDate.dayOfMonth

                    // Update salary day preference for cycle calculation
                    val app = context.applicationContext as ExpenseTrackerApplication
                    app.preferencesManager.setSalaryDay(salaryDay)
                    Log.d(TAG, "SALARY_DAY_DETECTED: Updated to day $salaryDay based on salary credit on ${transactionDate}")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Salary day detection failed: ${e.message}")
            }
            
            ClassificationDebugLogger.finalizeLog(logId, FinalDecision(
                transactionType = resolved.transactionType.name,
                categoryId = resolved.categoryId,
                categoryName = parsed.category,
                finalConfidence = 1.0,
                requiresUserConfirmation = false,
                entityType = parsed.counterparty.type.name,
                isExpenseEligible = resolved.transactionType == TransactionType.EXPENSE,
                whyNotOtherCategories = parsed.classificationTrace
            ))
            ClassificationDebugLogger.persistLog(context, logId)
            
            // --- ML TRAINING LOGGING ---
            com.saikumar.expensetracker.util.TrainingDataLogger.logSample(
                context = context,
                smsBody = body,
                merchantName = transaction.merchantName, // Use final resolved merchant
                confidence = 1.0f, // Automated extraction (Silver Label) - Rule based is high confidence
                isUserCorrection = false
            )
            
            return transaction.copy(id = rowId)
        }
        return null
    }



    suspend fun scanInbox(context: Context) = withContext(Dispatchers.IO) {
        Log.d(TAG, "Scanning inbox - Optimized Mode")
        ClassificationDebugLogger.startBatchSession()
        
        val app = context.applicationContext as ExpenseTrackerApplication
        val db = app.database

        // Seed Categories
        val categories = CategorySeeder.seedDefaultsIfNeeded(db.categoryDao())
        
        if (categories.isEmpty()) {
            Log.e(TAG, "Scan Inbox Aborted: Category seeding failed!")
            return@withContext
        }
        val categoryMap = categories.associateBy { it.name }
        val categoryNameMap = categories.associate { it.id to it.name }
        
        // Initialize DuplicateDetector
        if (!::duplicateDetector.isInitialized) {
            duplicateDetector = com.saikumar.expensetracker.domain.DuplicateDetector(db.transactionDao())
        }
        
        // --- PERFORMANCE OPTIMIZATION: HOIST DB READS ---
        Log.d(TAG, "Pre-loading data for generic performance...")
        
        // 1. Load Rules Once
        val rules = db.categorizationRuleDao().getAllActiveRules().first()
        
        // 2. Load Preferences Once
        val salaryCompanyNames = app.preferencesManager.getSalaryCompanyNamesSync()
        
        // 3. Load Memories Once
        val memories = db.merchantMemoryDao().getAllConfirmedMemories().associate { it.normalizedMerchant to it.categoryId }
        
        // 4. Load Existing Hashes (CRITICAL for skip performance)
        // Also pre-loading existing salary transactions for N+1 fix
        val existingHashes = db.transactionDao().getAllSmsHashes().toMutableSet()

        // Performance Fix: Only load last 60 days for salary recurrence detection instead of all history
        // Salary is typically monthly/bi-monthly, so 60 days is sufficient for pattern detection
        val sixtyDaysAgo = System.currentTimeMillis() - (60L * 24 * 60 * 60 * 1000)
        val recentHistory = db.transactionDao().getTransactionsInPeriod(sixtyDaysAgo, Long.MAX_VALUE).first()

        // Filter for only income transactions (used for salary recurrence detection)
        val salaryCheckHistory = recentHistory.filter {
            it.transaction.transactionType == TransactionType.INCOME
        }
        
        Log.d(TAG, "Loaded ${existingHashes.size} hashes and ${salaryCheckHistory.size} income records for salary checks.")
        
        // 5. Load Trusted Names for P2P trust checks (avoids N DB calls)
        val trustedNames = db.transferCircleDao().getAllTrustedNamesSync().toSet()

        // 6. Load User Accounts for self-transfer detection
        val userAccounts = db.userAccountDao().getAllAccounts()

        // Note: Categories already loaded at line 528 via CategorySeeder

        var inserted = 0
        var skipped = 0
        var epfNps = 0
        
        try {
            val inboxData = InboxScanner.readInbox(context)
            com.saikumar.expensetracker.util.ScanProgressManager.start(inboxData.totalCount)
            
            var processedCount = 0
            val batchSize = 50 // Notification update frequency
            
            inboxData.messages.forEach { sms ->
                processedCount++
                if (processedCount % batchSize == 0) {
                    com.saikumar.expensetracker.util.ScanProgressManager.update(processedCount)
                }
                
                val sender = sms.sender
                val body = sms.body
                val timestamp = sms.timestamp
                
                // --- FAST DUPLICATE CHECK (Optimization) ---
                // We check against an in-memory set of known hashes (pre-loaded)
                // to avoid expensive re-extraction (Regex/Parsing) and DB calls.
                // This is purely for performance during full scanning.
                val smsHash = DuplicateChecker.generateHash(body)
                if (existingHashes.contains(smsHash)) {
                    skipped++
                    return@forEach
                }
                

                val logId = ClassificationDebugLogger.startLog(
                    RawInputCapture(body, "SMS_SCAN", timestamp, sender, 0)
                )

                try {
                    // Start Processing
                    val result = process(
                        sender = sender,
                        body = body,
                        timestamp = timestamp,
                        rules = rules,
                        categoryMap = categoryNameMap,
                        salaryCompanyNames = salaryCompanyNames,
                        merchantMemories = memories,
                        context = context,
                        userAccounts = userAccounts
                    )
                    
                    // Handle Non-Success cases early
                    if (result is ProcessingResult.Ignored) return@forEach
                    
                    if (result is ProcessingResult.Dropped) {
                        val decision = result.decision
                        ClassificationDebugLogger.logRuleExecution(logId, ClassificationDebugLogger.createRuleExecution(
                            decision.ruleId, decision.reason, "LEDGER_GATE", "REJECTED", 1.0, decision.reason
                        ))
                        return@forEach
                    }
                    
                    // Proceed with Success
                    val success = result as ProcessingResult.Success
                    var parsed = success.transaction
                    
                    ClassificationDebugLogger.updateLogAmount(
                        logId = logId,
                        amountPaisa = parsed.amountPaisa ?: 0L
                    )

                    ClassificationDebugLogger.logRuleExecution(logId, ClassificationDebugLogger.createRuleExecution(
                        "LEDGER_GATE_PASS", "Ledger Gate Acceptance", "LEDGER_GATE", "PASSED", 1.0
                    ))
                    
                    ClassificationDebugLogger.logParsedFields(logId, ParsedFields(
                        merchantName = parsed.counterparty.name,
                        upiId = parsed.counterparty.upiId,
                        neftReference = null,
                        detectedKeywords = TransactionExtractor.detectKeywords(body),
                        accountTypeDetected = if (parsed.isDebit == true) "DEBIT" else "CREDIT",
                        senderInferred = parsed.senderType.name,
                        receiverInferred = null,
                        merchantSanitization = MerchantSanitization(
                            original = "SMS Extraction", 
                            sanitized = parsed.counterparty.name ?: "NULL",
                            strategy = "TEMPLATE_EXTRACTOR",
                            steps = parsed.counterparty.trace
                        )
                    ))
                    
                    // --- LOG EXTRACTION FAILURES ---
                    if (parsed.counterparty.name.isNullOrBlank() && 
                        parsed.transactionType != TransactionType.STATEMENT &&
                        parsed.transactionType != TransactionType.IGNORE) {
                         ClassificationDebugLogger.logExtractionFailure(context, body, sender, timestamp)
                    }
                    
                    // Handle EPF/NPS
                    if (parsed.senderType == SenderClassifier.SenderType.PENSION ||
                        parsed.senderType == SenderClassifier.SenderType.INVESTMENT) {
                        handleRetirement(db, parsed, smsHash)
                        ClassificationDebugLogger.finalizeLog(logId, FinalDecision(
                            transactionType = "RETIREMENT", categoryId = 0, categoryName = "RETIREMENT",
                            finalConfidence = 1.0, requiresUserConfirmation = false
                        ))
                        ClassificationDebugLogger.persistLog(context, logId)
                        epfNps++
                        existingHashes.add(smsHash) // Mark processed
                        return@forEach
                    }
                    
                    if (parsed.amountPaisa == null || parsed.amountPaisa <= 0) return@forEach
                    
                    // --- SMART DUPLICATE CHECK (Post-Extraction) ---
                    val duplicateCheck = duplicateDetector.check(
                        smsHash = smsHash,
                        referenceNo = null, // TODO: Extract reference if possible
                        amountPaisa = parsed.amountPaisa,
                        timestamp = timestamp,
                        merchantName = parsed.counterparty.name,
                        accountNumberLast4 = parsed.accountTypeDetected.name // Placeholder, ideally specific account num
                    )
                    
                    if (duplicateCheck.isDuplicate) {
                        Log.d(TAG, "Smart Duplicate Detected: ${duplicateCheck.reason}")
                        skipped++
                        // Add hash to avoid re-checking strictly
                        existingHashes.add(smsHash)
                        return@forEach
                    }

                    // --- RESOLVE AND INSERT (Optimized) ---
                    val insertedTxn = resolveAndInsert(
                        context = context,
                        db = db,
                        parsed = parsed,
                        categoryMap = categoryMap,
                        trustedNames = trustedNames,
                        smsHash = smsHash,
                        body = body,
                        salaryHistory = salaryCheckHistory, // Pass pre-loaded history
                        logId = logId,
                        userAccounts = userAccounts
                    )
                    
                    if (insertedTxn != null) {
                        inserted++
                        existingHashes.add(smsHash)
                    }
                    
                } catch (e: Exception) {
                    Log.e(TAG, "Error processing scan item", e)
                    ClassificationDebugLogger.logError(logId, "Scan Error: ${e.message}", e)
                    ClassificationDebugLogger.persistLog(context, logId)
                }
            } // End ForEach
            
        } catch (e: SecurityException) {
            ClassificationDebugLogger.endBatchSession(context)
            Log.e(TAG, "Scan failed: Permission denied", e)
            com.saikumar.expensetracker.util.TransactionNotificationHelper.showErrorNotification(
                context, "SMS Permission Denied", "Cannot scan messages."
            )
            throw e
        } catch (e: Exception) {
            ClassificationDebugLogger.endBatchSession(context)
            Log.e(TAG, "Scan failed", e)
             com.saikumar.expensetracker.util.TransactionNotificationHelper.showErrorNotification(
                context, "Scan Failed", "Error: ${e.message}"
            )
            throw e
        } finally {
            com.saikumar.expensetracker.util.ScanProgressManager.finish()
            ClassificationDebugLogger.endBatchSession(context)
            Log.d(TAG, "Scan complete: inserted=$inserted, skipped=$skipped, epf/nps=$epfNps")
        }
        
        // Run self-transfer pairing after scan
        try {
            TransactionPairer.runAllPairing(context)
        } catch (e: Exception) {
            Log.e(TAG, "Pairing failed", e)
        }
    }

    private suspend fun handleRetirement(
        db: com.saikumar.expensetracker.data.db.AppDatabase,
        parsed: ParsedTransaction,
        smsHash: String
    ) {
        // Extract balance from EPF/NPS messages
        val balanceMatch = BALANCE_REGEX.find(parsed.body)
        val contributionMatch = CONTRIBUTION_REGEX.find(parsed.body)
        val monthMatch = MONTH_REGEX.find(parsed.body)
        
        // Log extraction results for debugging
        Log.d(TAG, "EPF/NPS Extraction: BalMatch=${balanceMatch?.value}, ContribMatch=${contributionMatch?.value}")

        val balance = balanceMatch?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull() ?: 0.0
        val contribution = contributionMatch?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull() ?: 0.0
        
        // Abort only if NEITHER balance NOR contribution was found
        if (balance == 0.0 && contribution == 0.0) {
            Log.d(TAG, "EPF/NPS: No value found, skipping.")
            return
        }

        val monthStr = if (monthMatch != null) formatRetirementMonth(monthMatch.groupValues[1], monthMatch.groupValues[2]) else "Unknown"
        
        val type = if (parsed.senderType == SenderClassifier.SenderType.PENSION) 
            RetirementType.EPF else RetirementType.NPS
        
        val retirementBalance = RetirementBalance(
            type = type,
            balancePaisa = (balance * 100).toLong(),
            contributionPaisa = (contribution * 100).toLong(),
            month = monthStr,
            timestamp = parsed.timestamp,
            identifier = smsHash.take(8),
            smsBody = parsed.body,
            sender = parsed.sender
        )
        
        db.retirementDao().upsert(retirementBalance)
    }

    private fun formatRetirementMonth(subMonth: String?, subYear: String?): String {
        if (subMonth == null || subYear == null) return "Unknown"
        val m = subMonth.trim().lowercase().take(3)
        val monthNum = when(m) {
            "jan" -> 1; "feb" -> 2; "mar" -> 3; "apr" -> 4; "may" -> 5; "jun" -> 6
            "jul" -> 7; "aug" -> 8; "sep" -> 9; "oct" -> 10; "nov" -> 11; "dec" -> 12
            else -> return "$subMonth-$subYear"
        }
        val yearFull = if (subYear.length == 2) "20$subYear" else subYear
        return String.format("%s-%02d", yearFull, monthNum)
    }

    suspend fun processAndInsert(context: Context, sender: String, body: String, timestamp: Long) = withContext(Dispatchers.IO) {
        val logId = ClassificationDebugLogger.startLog(
            RawInputCapture(body, "SMS_RECEIVER", timestamp, sender, 0)
        )

        try {
            val app = context.applicationContext as ExpenseTrackerApplication
            val db = app.database
            
            // Check duplicate
            val smsHash = DuplicateChecker.generateHash(body)
            if (DuplicateChecker.isDuplicate(db, smsHash)) return@withContext

            val salaryCompanyNames = app.preferencesManager.getSalaryCompanyNamesSync()

            // Initialize DuplicateDetector
            if (!::duplicateDetector.isInitialized) {
                duplicateDetector = com.saikumar.expensetracker.domain.DuplicateDetector(db.transactionDao())
            }

            // Adaptive Categorization
            val memories = db.merchantMemoryDao().getAllConfirmedMemories().associate { it.normalizedMerchant to it.categoryId }

            // Load User Accounts for self-transfer detection
            val userAccounts = db.userAccountDao().getAllAccounts()

            val result = process(sender, body, timestamp, salaryCompanyNames = salaryCompanyNames, merchantMemories = memories, context = context, userAccounts = userAccounts)
            
            // Post-Extraction Duplicate Check (Tier 2/3)
            if (result is ProcessingResult.Success) {
                 val parsedTxn = result.transaction
                 if (parsedTxn.amountPaisa != null) {
                     val dupeCheck = duplicateDetector.check(
                        smsHash = smsHash,
                        referenceNo = null,
                        amountPaisa = parsedTxn.amountPaisa,
                        timestamp = timestamp,
                        merchantName = parsedTxn.counterparty.name,
                        accountNumberLast4 = SmsConstants.extractAccountLast4(body)
                     )
                     if (dupeCheck.isDuplicate) {
                         Log.d(TAG, "Duplicate ignored (Smart Check): ${dupeCheck.reason}")
                         return@withContext
                     }
                 }
            }
            
            // Handle Non-Sucess
            if (result is ProcessingResult.Ignored) return@withContext
            if (result is ProcessingResult.Dropped) {
                val decision = result.decision
                ClassificationDebugLogger.logRuleExecution(logId, ClassificationDebugLogger.createRuleExecution(
                    decision.ruleId, decision.reason, "LEDGER_GATE", "REJECTED", 1.0, decision.reason
                ))
                 ClassificationDebugLogger.persistLog(context, logId)
                return@withContext
            }

            val success = result as ProcessingResult.Success
            var parsed = success.transaction
            val gateDecision = success.gateDecision

            ClassificationDebugLogger.updateLogAmount(
                logId = logId,
                amountPaisa = parsed.amountPaisa ?: 0L
            )
            
            ClassificationDebugLogger.logRuleExecution(logId, ClassificationDebugLogger.createRuleExecution(
                "LEDGER_GATE_PASS", "Ledger Gate Acceptance", "LEDGER_GATE", "PASSED", 1.0
            ))
            
             ClassificationDebugLogger.logParsedFields(logId, ParsedFields(
                merchantName = parsed.counterparty.name,
                upiId = parsed.counterparty.upiId,
                neftReference = null,
                detectedKeywords = TransactionExtractor.detectKeywords(body),
                accountTypeDetected = if (parsed.isDebit == true) "DEBIT" else "CREDIT",
                senderInferred = parsed.senderType.name,
                receiverInferred = null,
                merchantSanitization = MerchantSanitization(
                    original = "SMS Extraction",
                    sanitized = parsed.counterparty.name ?: "NULL",
                    strategy = "TEMPLATE_EXTRACTOR",
                    steps = parsed.counterparty.trace
                )
            ))
            
            // --- LOG EXTRACTION FAILURES ---
            if (parsed.counterparty.name.isNullOrBlank() && 
                parsed.transactionType != TransactionType.STATEMENT && 
                parsed.transactionType != TransactionType.IGNORE) {
                 ClassificationDebugLogger.logExtractionFailure(context, body, sender, timestamp)
            }
            
            val categories = db.categoryDao().getAllEnabledCategories().first()
            if (categories.isEmpty()) {
                val msg = "No categories found! Cannot insert transaction."
                Log.e(TAG, msg)
                ClassificationDebugLogger.logError(logId, msg, null)
                ClassificationDebugLogger.persistLog(context, logId)
                return@withContext
            }
            val categoryMap = categories.associateBy { it.name }

            // Handle EPF/NPS
            if (parsed.senderType == SenderClassifier.SenderType.PENSION ||
                parsed.senderType == SenderClassifier.SenderType.INVESTMENT) {
                handleRetirement(db, parsed, smsHash)
                ClassificationDebugLogger.finalizeLog(logId, FinalDecision(
                    transactionType = "RETIREMENT", categoryId = 0, categoryName = "RETIREMENT",
                    finalConfidence = 1.0, requiresUserConfirmation = false,
                    reasoning = "Retirement handled"
                ))
                ClassificationDebugLogger.persistLog(context, logId)
                return@withContext
            }
            
            if (parsed.amountPaisa == null || parsed.amountPaisa <= 0) return@withContext
            
            // --- USE HELPER TO RESOLVE CATEGORY & TYPE ---
            // For single transaction, we don't need to pre-load history, let checkSalaryRecurrence do it if needed
            val transaction = resolveAndInsert(
                context = context,
                db = db,
                parsed = parsed,
                categoryMap = categoryMap,
                trustedNames = null, // Will use DB fallback inside resolveAndInsert -> resolveTransactionDetails
                smsHash = smsHash,
                body = body,
                salaryHistory = null, // Will use DB fallback inside resolveAndInsert -> checkSalaryRecurrence
                logId = logId,
                userAccounts = userAccounts
            )
            
            if (transaction != null) {
                val finalCategoryName = categories.find { it.id == transaction.categoryId }?.name ?: parsed.category
                com.saikumar.expensetracker.util.TransactionNotificationHelper.showTransactionNotification(
                    context = context,
                    amountPaise = transaction.amountPaisa,
                    merchantName = transaction.merchantName,
                    categoryName = finalCategoryName,
                    isDebit = parsed.isDebit ?: true
                )
                
                if (transaction.transactionType == TransactionType.EXPENSE) {
                    try {
                        val budgetManager = (context.applicationContext as ExpenseTrackerApplication).budgetManager
                        val status = budgetManager.checkBudgetStatus()
                        
                        if (status.status != com.saikumar.expensetracker.util.BudgetStatus.SAFE) {
                             com.saikumar.expensetracker.util.TransactionNotificationHelper.showBudgetBreachNotification(
                                 context,
                                 status
                             )
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Budget check failed", e)
                    }
                }
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Error in processAndInsert", e)
            ClassificationDebugLogger.logError(logId, "Insert Failed: ${e.message}", e)
            ClassificationDebugLogger.persistLog(context, logId)
            throw e // Re-throw to ensure Worker shows failure if needed
        }
    }

    suspend fun reclassifyTransactions(context: Context) = withContext(Dispatchers.IO) {
        Log.d(TAG, "Reclassifying transactions...")
        ClassificationDebugLogger.startBatchSession()

        val app = context.applicationContext as ExpenseTrackerApplication
        val db = app.database

        val categories = db.categoryDao().getAllEnabledCategories().first()
        if (categories.isEmpty()) return@withContext
        val categoryMap = categories.associateBy { it.name }
        val categoryNameMap = categories.associate { it.id to it.name }

        val rules = db.categorizationRuleDao().getAllActiveRules().first()

        // --- PERFORMANCE OPTIMIZATION: HOIST DB READS ---
        Log.d(TAG, "Pre-loading data for reclassify performance...")
        val salaryCompanyNames = app.preferencesManager.getSalaryCompanyNamesSync()
        val memories = db.merchantMemoryDao().getAllConfirmedMemories().associate { it.normalizedMerchant to it.categoryId }
        val trustedNames = db.transferCircleDao().getAllTrustedNamesSync().toSet()
        val userAccounts = db.userAccountDao().getAllAccounts()
        Log.d(TAG, "Loaded ${trustedNames.size} trusted names, ${memories.size} memories, ${userAccounts.size} accounts")

        // --- PAGINATED PROCESSING TO PREVENT OOM ---
        val batchSize = 500
        val totalCount = db.transactionDao().getTransactionCount(0, Long.MAX_VALUE)
        Log.d(TAG, "Total transactions to re-evaluate: $totalCount (processing in batches of $batchSize)")

        var updatedCount = 0
        var offset = 0

        while (offset < totalCount) {
            val batch = db.transactionDao().getTransactionsPagedSync(0, Long.MAX_VALUE, batchSize, offset)
            if (batch.isEmpty()) break

            Log.d(TAG, "Processing batch: offset=$offset, size=${batch.size}")

            for (item in batch) {
                val txn = item.transaction
                if (txn.fullSmsBody.isNullOrBlank()) continue

                // Infer Sender Type to avoid UNKNOWN rejection
                val inferredSenderType = when (txn.transactionType) {
                    TransactionType.PENSION -> SenderClassifier.SenderType.PENSION
                    TransactionType.INVESTMENT_CONTRIBUTION -> SenderClassifier.SenderType.INVESTMENT
                    else -> SenderClassifier.SenderType.BANK
                }

                val logId = ClassificationDebugLogger.startLog(
                    RawInputCapture(txn.fullSmsBody, "RECLASSIFY", txn.timestamp, "UNKNOWN", 0)
                )

                try {
                    val result = process(
                        sender = "UNKNOWN", // Forced type handles checks
                        body = txn.fullSmsBody,
                        timestamp = txn.timestamp,
                        rules = rules,
                        categoryMap = categoryNameMap,
                        forceSenderType = inferredSenderType,
                        salaryCompanyNames = salaryCompanyNames,
                        merchantMemories = memories,
                        userAccounts = userAccounts
                    )

                    if (result is ProcessingResult.Success) {
                        val parsed = result.transaction
                        // We don't apply Gate Drops during reclassify (assume existing txns are valid-ish)

                        ClassificationDebugLogger.updateLogAmount(
                            logId = logId,
                            amountPaisa = parsed.amountPaisa ?: 0L
                        )

                        ClassificationDebugLogger.logParsedFields(logId, ParsedFields(
                            merchantName = parsed.counterparty.name,
                            upiId = parsed.counterparty.upiId,
                            neftReference = null,
                            detectedKeywords = TransactionExtractor.detectKeywords(txn.fullSmsBody ?: ""),
                            accountTypeDetected = if (parsed.isDebit == true) "DEBIT" else "CREDIT",
                            senderInferred = parsed.senderType.name,
                            receiverInferred = null,
                            merchantSanitization = MerchantSanitization(
                                original = "SMS Extraction",
                                sanitized = parsed.counterparty.name ?: "NULL",
                                strategy = "TEMPLATE_EXTRACTOR",
                                steps = parsed.counterparty.trace
                            )
                        ))

                        // --- USE HELPER TO RESOLVE CATEGORY & TYPE ---
                        val resolutionCtx = TransactionResolutionContext(
                            parsed = parsed,
                            categoryMap = categoryMap,
                            trustedNames = trustedNames,
                            db = db,
                            smsHash = null
                        )
                        var resolved = resolveTransactionDetails(resolutionCtx)

                        // RECURRENCE CHECK FOR SALARY (uses DB fallback, no allTxns needed)
                        var finalCategoryId = resolved.categoryId
                        if (parsed.category == "Other Income" && parsed.transactionType == TransactionType.INCOME) {
                             if (checkSalaryRecurrence(db, parsed.counterparty.name, parsed.timestamp, parsed.amountPaisa, null)) {
                                 finalCategoryId = categoryMap[AppConstants.Categories.SALARY]?.id ?: finalCategoryId
                                 resolved = resolved.copy(categoryId = finalCategoryId)
                             }
                        }

                        // Insert merchant alias if needed
                        tryInsertMerchantAlias(db, resolved.merchantResult)

                        val finalMerchant = resolved.merchantResult.normalized ?: resolved.merchantResult.raw ?: txn.merchantName

                        // INVARIANT: Only EXPENSE type is eligible
                        val isExpenseEligible = resolved.transactionType == TransactionType.EXPENSE

                        if (txn.categoryId != finalCategoryId ||
                            txn.merchantName != finalMerchant ||
                            txn.transactionType != resolved.transactionType ||
                            txn.isExpenseEligible != isExpenseEligible) {

                            val newTxn = txn.copy(
                                categoryId = finalCategoryId,
                                merchantName = finalMerchant,
                                transactionType = resolved.transactionType,
                                isExpenseEligible = isExpenseEligible
                            )
                            db.transactionDao().updateTransaction(newTxn)
                            updatedCount++

                            ClassificationDebugLogger.finalizeLog(logId, FinalDecision(
                                transactionType = resolved.transactionType.name,
                                categoryId = finalCategoryId,
                                categoryName = parsed.category,
                                finalConfidence = 1.0,
                                requiresUserConfirmation = false,
                                entityType = parsed.counterparty.type.name,
                                isExpenseEligible = isExpenseEligible,
                                whyNotOtherCategories = parsed.classificationTrace
                            ))
                        } else {
                             ClassificationDebugLogger.finalizeLog(logId, FinalDecision(
                                transactionType = "UNCHANGED",
                                categoryId = txn.categoryId,
                                categoryName = parsed.category,
                                finalConfidence = 1.0,
                                requiresUserConfirmation = false,
                                entityType = parsed.counterparty.type.name,
                                isExpenseEligible = txn.isExpenseEligible,
                                whyNotOtherCategories = parsed.classificationTrace
                            ))
                        }
                    }
                     ClassificationDebugLogger.persistLog(context, logId)

                } catch (e: Exception) {
                    Log.e(TAG, "Error reclassifying txn ${txn.id}", e)
                     ClassificationDebugLogger.logError(logId, "Reclassify Error", e)
                     ClassificationDebugLogger.persistLog(context, logId)
                }
            }

            offset += batchSize
        }

        Log.d(TAG, "Reclassify Complete. Updated $updatedCount transactions.")
        ClassificationDebugLogger.endBatchSession(context)

        // Run self-transfer pairing after reclassify
        TransactionPairer.runAllPairing(context)
    }

    suspend fun assignCategoryToTransaction(
        context: Context,
        transactionId: Long,
        categoryId: Long,
        applyToSimilar: Boolean = false,
        transactionType: TransactionType? = null
    ): Int {
        val app = context.applicationContext as ExpenseTrackerApplication
        val db = app.database
        val sourceTxn = db.transactionDao().getById(transactionId) ?: return 0
        
        // ===== STEP 1: FIND ALL SIMILAR TRANSACTIONS (INCLUDING SOURCE) =====
        // This must happen BEFORE any updates to avoid race conditions
        val allAffectedTxns = mutableSetOf<Transaction>()
        
        // Always include the source transaction
        allAffectedTxns.add(sourceTxn)
        
        if (applyToSimilar) {
            // Match by merchant name (Indexed, fast)
            if (!sourceTxn.merchantName.isNullOrBlank()) {
                val byMerchant = db.transactionDao().getByMerchantName(sourceTxn.merchantName)
                allAffectedTxns.addAll(byMerchant)
                Log.d(TAG, "Found ${byMerchant.size} transactions with merchant: ${sourceTxn.merchantName}")
            }
        }
        
        // Convert to sorted list for consistent processing
        val affectedTxnsList = allAffectedTxns.toList().sortedBy { it.id }
        Log.d(TAG, "Total affected transactions: ${affectedTxnsList.size} (source + similar)")
        
        // ===== STEP 2: CREATE UNDO LOG WITH COMPLETE DATA =====
        val undoData = org.json.JSONObject().apply {
            put("txnIds", org.json.JSONArray(affectedTxnsList.map { it.id }))
            put("oldCategoryIds", org.json.JSONArray(affectedTxnsList.map { it.categoryId }))
            put("oldTransactionTypes", org.json.JSONArray(affectedTxnsList.map { it.transactionType.name }))
            put("newCategoryId", categoryId)
            put("newTransactionType", transactionType?.name ?: "UNCHANGED")
        }
        
        // Get category name for description
        val categories = db.categoryDao().getAllEnabledCategories().first()
        val categoryName = categories.find { it.id == categoryId }?.name ?: "Unknown"
        
        // Log undo entry
        val undoLog = com.saikumar.expensetracker.data.entity.UndoLog(
            actionType = com.saikumar.expensetracker.data.entity.UndoLog.ACTION_BATCH_CATEGORIZE,
            undoData = undoData.toString(),
            description = "Changed ${affectedTxnsList.size} transactions to '$categoryName'",
            affectedCount = affectedTxnsList.size
        )
        db.undoLogDao().insert(undoLog)
        
        // ===== STEP 3: UPDATE ALL TRANSACTIONS AT ONCE =====
        val updatedTxns = affectedTxnsList.map { txn ->
            if (transactionType != null) {
                txn.copy(
                    categoryId = categoryId, 
                    transactionType = transactionType, 
                    confidenceScore = 100 // User confirmed = 100%
                )
            } else {
                txn.copy(
                    categoryId = categoryId, 
                    confidenceScore = 100
                )
            }
        }
        
        // Batch update all transactions
        db.transactionDao().updateTransactions(updatedTxns)
        
        Log.d(TAG, "Successfully updated ${updatedTxns.size} transactions to category '$categoryName'" +
            if (transactionType != null) " with type $transactionType" else "")
        
        return updatedTxns.size
    }
    
    /**
     * Undo the most recent batch categorization.
     * Returns the description of what was undone, or null if nothing to undo.
     */
    suspend fun performUndo(context: Context): String? {
        val app = context.applicationContext as ExpenseTrackerApplication
        val db = app.database
        
        val undoLog = db.undoLogDao().getLatestUndoable() ?: return null
        
        try {
            val data = org.json.JSONObject(undoLog.undoData)
            val txnIds = data.getJSONArray("txnIds")
            val oldCategoryIds = data.getJSONArray("oldCategoryIds")
            
            // Check if we have transaction types stored (backward compatibility)
            val oldTransactionTypes = if (data.has("oldTransactionTypes")) {
                data.getJSONArray("oldTransactionTypes")
            } else null
            
            val txnsToUpdate = mutableListOf<Transaction>()
            
            for (i in 0 until txnIds.length()) {
                val txnId = txnIds.getLong(i)
                val oldCategoryId = oldCategoryIds.getLong(i)
                
                val txn = db.transactionDao().getById(txnId)
                if (txn != null) {
                    var updatedTxn = txn.copy(categoryId = oldCategoryId)
                    
                    // Restore transaction type if available
                    if (oldTransactionTypes != null) {
                        try {
                            val typeName = oldTransactionTypes.getString(i)
                            val explicitType = TransactionType.valueOf(typeName)
                            updatedTxn = updatedTxn.copy(transactionType = explicitType)
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to restore transaction type for txn $txnId", e)
                        }
                    }
                    
                    txnsToUpdate.add(updatedTxn)
                }
            }
            
            if (txnsToUpdate.isNotEmpty()) {
                db.transactionDao().updateTransactions(txnsToUpdate)
            }
            
            // Mark as undone
            db.undoLogDao().markAsUndone(undoLog.id)
            
            Log.d(TAG, "UNDO: Restored ${undoLog.affectedCount} transactions")
            return undoLog.description
            
        } catch (e: Exception) {
            Log.e(TAG, "UNDO failed: ${e.message}", e)
            return null
        }
    }


    
    /**
     * Track NEFT income sources for salary pattern detection.
     * When the same IFSC + sender pattern appears in 2+ consecutive months,
     * it's marked as a salary source.
     */
    private suspend fun trackNeftSalarySource(
        db: com.saikumar.expensetracker.data.db.AppDatabase,
        body: String,
        timestamp: Long
    ) {
        try {
            val neftSource = CounterpartyExtractor.extractNeftSource(body) ?: return
            val (ifsc, sender) = neftSource
            
            // Convert timestamp to YYYYMM format
            val calendar = java.util.Calendar.getInstance().apply { timeInMillis = timestamp }
            val currentMonth = calendar.get(java.util.Calendar.YEAR) * 100 + (calendar.get(java.util.Calendar.MONTH) + 1)
            
            // Check if we already have this source
            val existing = db.neftSourceDao().findByIfscAndSender(ifsc, sender)
            
            if (existing != null) {
                // Update existing source
                val previousMonth = existing.lastSeenMonth
                val isConsecutive = isConsecutiveMonth(previousMonth, currentMonth)
                
                val newConsecutiveCount = if (isConsecutive) {
                    existing.consecutiveMonths + 1
                } else if (currentMonth == existing.lastSeenMonth) {
                    existing.consecutiveMonths // Same month, no change
                } else {
                    1 // Reset if not consecutive
                }
                
                val shouldMarkSalary = newConsecutiveCount >= 2
                
                if (currentMonth != existing.lastSeenMonth) {
                    db.neftSourceDao().update(
                        existing.copy(
                            lastSeenMonth = currentMonth,
                            occurrenceCount = existing.occurrenceCount + 1,
                            consecutiveMonths = newConsecutiveCount,
                            isSalary = existing.isSalary || shouldMarkSalary,
                            lastSeenAt = timestamp
                        )
                    )
                    
                    if (shouldMarkSalary && !existing.isSalary) {
                        Log.i(TAG, "SALARY DETECTED: $sender from $ifsc (seen $newConsecutiveCount consecutive months)")
                    }
                }
            } else {
                // Insert new source
                db.neftSourceDao().insert(
                    NeftSource(
                        ifscCode = ifsc,
                        senderPattern = sender,
                        firstSeenMonth = currentMonth,
                        lastSeenMonth = currentMonth,
                        occurrenceCount = 1,
                        consecutiveMonths = 1,
                        isSalary = false,
                        lastSeenAt = timestamp
                    )
                )
                Log.d(TAG, "New NEFT source tracked: $sender from $ifsc (month: $currentMonth)")
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to track NEFT salary source: ${e.message}")
        }
    }
    
    /**
     * Check if two months (in YYYYMM format) are consecutive.
     */
    private fun isConsecutiveMonth(previousMonth: Int, currentMonth: Int): Boolean {
        val prevYear = previousMonth / 100
        val prevMonthNum = previousMonth % 100
        val currYear = currentMonth / 100
        val currMonthNum = currentMonth % 100
        
        // Same month is not consecutive
        if (previousMonth == currentMonth) return false
        
        // Check if current is exactly one month after previous
        return if (prevMonthNum == 12) {
            // December -> January transition
            currYear == prevYear + 1 && currMonthNum == 1
        } else {
            currYear == prevYear && currMonthNum == prevMonthNum + 1
        }
    }
    
}
