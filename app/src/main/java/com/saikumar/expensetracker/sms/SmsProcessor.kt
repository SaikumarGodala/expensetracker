package com.saikumar.expensetracker.sms

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import android.util.Log
import com.saikumar.expensetracker.ExpenseTrackerApplication
import com.saikumar.expensetracker.data.entity.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import com.saikumar.expensetracker.util.ClassificationDebugLogger
import com.saikumar.expensetracker.data.entity.*
import com.saikumar.expensetracker.util.MerchantNormalizer
import com.saikumar.expensetracker.data.entity.MerchantAlias

data class ParsedTransaction(
    val sender: String,
    val body: String,
    val timestamp: Long,
    val senderType: SenderClassifier.SenderType,
    val transactionType: TransactionExtractor.TransactionType,
    val amount: Double?,
    val counterparty: CounterpartyExtractor.Counterparty,
    val category: String,
    val isDebit: Boolean?,
    val accountTypeDetected: AccountType = AccountType.UNKNOWN
)

data class SimilarityResult(
    val matchedTransactions: List<Transaction>,
    val matchType: String,
    val confidence: Float
)

object SmsProcessor {
    private const val TAG = "SmsProcessor"

    fun process(
        sender: String, 
        body: String, 
        timestamp: Long,
        rules: List<CategorizationRule> = emptyList(),
        categoryMap: Map<Long, String> = emptyMap(),
        forceSenderType: SenderClassifier.SenderType? = null
    ): ParsedTransaction? {
        val senderType = forceSenderType ?: SenderClassifier.classify(sender)
        if (senderType == SenderClassifier.SenderType.EXCLUDED) return null

        val extraction = TransactionExtractor.extract(body, senderType)
        var counterparty = CounterpartyExtractor.extract(body, extraction.type)
        
        // Override for Statement to prevent garbage merchant extraction (e.g. "ar")
        if (extraction.type == TransactionExtractor.TransactionType.STATEMENT) {
            counterparty = CounterpartyExtractor.Counterparty("Credit Card Statement", null, CounterpartyExtractor.CounterpartyType.MERCHANT)
        }

        val category = CategoryMapper.categorize(counterparty, extraction.type, rules, categoryMap, messageBody = body)

        return ParsedTransaction(
            sender = sender,
            body = body,
            timestamp = timestamp,
            senderType = senderType,
            transactionType = extraction.type,
            amount = extraction.amount,
            counterparty = counterparty,
            category = category,
            isDebit = extraction.isDebit
        )
    }

    suspend fun checkSalaryRecurrence(
        db: com.saikumar.expensetracker.data.db.AppDatabase,
        merchantName: String?,
        timestamp: Long,
        amount: Double?
    ): Boolean {
        if (merchantName.isNullOrBlank()) return false
        
        // Window: 20 to 50 days (wider window to catch irregular dates)
        val oneDayMillis = 24 * 60 * 60 * 1000L
        val endRange = timestamp - (20 * oneDayMillis)
        val startRange = timestamp - (60 * oneDayMillis) 
        
        try {
            val history = db.transactionDao().getTransactionsInPeriod(startRange, endRange).first()
            
            // Check for match
            val match = history.find { 
                it.transaction.transactionType == TransactionType.INCOME &&
                it.transaction.merchantName.equals(merchantName, ignoreCase = true)
            }
            
            if (match != null) {
                Log.d(TAG, "SALARY RECURRENCE FOUND: '${merchantName}' matches prev txn on ${java.time.Instant.ofEpochMilli(match.transaction.timestamp)}")
                return true
            } else {
                // Debug log for failure
                Log.d(TAG, "No recurrence for '${merchantName}' in window. History size: ${history.size}")
                return false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error checking salary recurrence", e)
            return false
        }
    }

    private fun generateHash(body: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hash = digest.digest(body.toByteArray())
        return hash.take(16).joinToString("") { "%02x".format(it) }
    }

    suspend fun scanInbox(context: Context) = withContext(Dispatchers.IO) {
        Log.d(TAG, "Scanning inbox...")
        ClassificationDebugLogger.startBatchSession()
        
        val app = context.applicationContext as ExpenseTrackerApplication
        val db = app.database
        val categories = db.categoryDao().getAllEnabledCategories().first()
        if (categories.isEmpty()) {
            Log.e(TAG, "Scan Inbox Aborted: No categories found in database.")
            return@withContext
        }
        val categoryMap = categories.associateBy { it.name }
        val categoryNameMap = categories.associate { it.id to it.name }
        
        // Load Active Rules
        val rules = db.categorizationRuleDao().getAllActiveRules().first()
        Log.d(TAG, "Loaded ${rules.size} active categorization rules")
        
        val cursor = context.contentResolver.query(
            "content://sms/inbox".toUri(),
            arrayOf("address", "body", "date"),
            null, null, "date DESC"
        )
        
        var inserted = 0
        var skipped = 0
        var epfNps = 0
        
        try {
            cursor?.use {
                while (it.moveToNext()) {
                    val sender = it.getString(0) ?: continue
                    val body = it.getString(1) ?: continue
                    val timestamp = it.getLong(2)
                    
                    val logId = ClassificationDebugLogger.startLog(
                        RawInputCapture(body, "SMS_SCAN", timestamp, sender, 0, "UNKNOWN", "UNKNOWN")
                    )
                    
                    try {
                        val smsHash = generateHash(body)
                        
                        // Skip if already processed
                        if (db.transactionDao().existsBySmsHash(smsHash)) {
                            skipped++
                            continue
                        }

                        // 1. PARSE (Extract fields first)
                        // We parse first so the Gate has full context (Amount, inferred Type)
                        // If parsing fails (e.g. excluded sender), we skip immediately.
                        var parsed = process(sender, body, timestamp, rules, categoryNameMap)
                        if (parsed == null) {
                             // Excluded sender or extraction failure
                             continue
                        }
                        
                        // Update log with parsed amount and direction
                        ClassificationDebugLogger.updateRawInput(
                            logId = logId,
                            amountPaisa = ((parsed.amount ?: 0.0) * 100).toLong(),
                            direction = if (parsed.isDebit == true) "DEBIT" else if (parsed.isDebit == false) "CREDIT" else "UNKNOWN",
                            accountType = parsed.accountTypeDetected.name
                        )

                        // 2. LEDGER GATE (Single Authoritative Source of Truth)
                        // Evaluates: Filters, Confirmation Invariants, Economic Reality, Eligibility
                        val gateDecision = LedgerGate.evaluate(body, parsed.transactionType, sender)
                        
                        when (gateDecision) {
                            is LedgerDecision.Drop -> {
                                ClassificationDebugLogger.logRuleExecution(logId, ClassificationDebugLogger.createRuleExecution(
                                    gateDecision.ruleId, gateDecision.reason, "LEDGER_GATE", "REJECTED", 1.0, gateDecision.reason
                                ))
                                continue
                            }
                            is LedgerDecision.Insert -> {
                                ClassificationDebugLogger.logRuleExecution(logId, ClassificationDebugLogger.createRuleExecution(
                                    "LEDGER_GATE_PASS", "Ledger Gate Acceptance", "LEDGER_GATE", "PASSED", 1.0
                                ))
                                
                                // Apply Gate Overrides (e.g. CC Spend correction)
                                if (parsed.transactionType != gateDecision.transactionType) {
                                     ClassificationDebugLogger.logRuleExecution(logId, ClassificationDebugLogger.createRuleExecution(
                                        "GATE_OVERRIDE_TYPE", "Gate Type Correction", "OVERRIDE", "APPLIED", 1.0, gateDecision.reasoning
                                    ))
                                     parsed = parsed.copy(
                                         transactionType = gateDecision.transactionType,
                                         accountTypeDetected = gateDecision.accountType
                                     )
                                }
                            }
                        }
                        
                        // Proceed with Insert logic using the validated 'parsed' object and 'gateDecision' data
                        val isExpenseEligible = (gateDecision as LedgerDecision.Insert).isExpenseEligible

                        // Log Parsed Fields (Partial reconstruction)
                        ClassificationDebugLogger.logParsedFields(logId, ParsedFields(
                            merchantName = parsed.counterparty.name,
                            upiId = parsed.counterparty.upiId,
                            neftReference = null,
                            detectedKeywords = emptyList(),
                            accountTypeDetected = if (parsed.isDebit == true) "DEBIT" else "CREDIT",
                            senderInferred = parsed.senderType.name,
                            receiverInferred = null
                        ))
                        
                        // Handle EPF/NPS separately
                        if (parsed.senderType == SenderClassifier.SenderType.PENSION ||
                            parsed.senderType == SenderClassifier.SenderType.INVESTMENT) {
                            handleRetirement(db, parsed, smsHash)
                            ClassificationDebugLogger.finalizeLog(logId, FinalDecision(
                                transactionType = "RETIREMENT", categoryId = 0, categoryName = "RETIREMENT",
                                confidence = "HIGH", finalConfidence = 1.0, requiresUserConfirmation = false
                            ))
                            ClassificationDebugLogger.persistLog(context, logId)
                            epfNps++
                            continue
                        }
                        
                        // Skip if no amount
                        if (parsed.amount == null || parsed.amount <= 0) {
                             // Log as filtered
                             continue
                        }
                        
                        // Find category ID
                        var categoryId = categoryMap[parsed.category]?.id 
                            ?: categoryMap["Uncategorized"]?.id 
                            ?: categories.first().id
                        
                        // RECURRENCE CHECK FOR SALARY
                        // If defined as "Other Income", check if it's actually Salary (Recurring)
                        if (parsed.category == "Other Income" && parsed.transactionType == TransactionExtractor.TransactionType.INCOME) {
                             if (checkSalaryRecurrence(db, parsed.counterparty.name, parsed.timestamp, parsed.amount)) {
                                 // Found recurrence! Upgrade to Salary
                                 categoryId = categoryMap["Salary"]?.id ?: categoryId
                                 Log.d(TAG, "Upgraded ${parsed.counterparty.name} to Salary due to recurrence")
                             }
                        }
                        
                        // Map transaction type
                        // FORCE LIABILITY_PAYMENT if category determines it (e.g. CRED, Self-Transfer to CC)
                        val txnType = if (parsed.category == "Credit Bill Payments") {
                            TransactionType.LIABILITY_PAYMENT
                        } else {
                            when (parsed.transactionType) {
                                TransactionExtractor.TransactionType.INCOME -> TransactionType.INCOME
                                TransactionExtractor.TransactionType.EXPENSE -> TransactionType.EXPENSE
                                TransactionExtractor.TransactionType.TRANSFER -> TransactionType.TRANSFER
                                TransactionExtractor.TransactionType.LIABILITY -> TransactionType.LIABILITY_PAYMENT
                                TransactionExtractor.TransactionType.PENSION -> TransactionType.PENSION
                                TransactionExtractor.TransactionType.INVESTMENT -> TransactionType.INVESTMENT_CONTRIBUTION
                                TransactionExtractor.TransactionType.STATEMENT -> TransactionType.STATEMENT
                                TransactionExtractor.TransactionType.UNKNOWN -> TransactionType.UNKNOWN
                            }
                        }
                        
                        val rawMerchant = parsed.counterparty.name
                        val normalizedMerchant = MerchantNormalizer.normalize(rawMerchant)
                        
                        if (!rawMerchant.isNullOrBlank() && !normalizedMerchant.isNullOrBlank() && rawMerchant != normalizedMerchant) {
                            try {
                                db.merchantAliasDao().insert(MerchantAlias(rawMerchant, normalizedMerchant))
                            } catch (e: Exception) { Log.w(TAG, "Alias insert failed: ${e.message}") }
                        }

                        val transaction = Transaction(
                            amountPaisa = (parsed.amount * 100).toLong(),
                            categoryId = categoryId,
                            timestamp = parsed.timestamp,
                            source = TransactionSource.SMS,
                            transactionType = txnType,
                            smsHash = smsHash,
                            merchantName = normalizedMerchant ?: rawMerchant,
                            smsSnippet = parsed.body.take(100),
                            fullSmsBody = parsed.body
                        )
                        
                        val rowId = db.transactionDao().insertTransaction(transaction)
                        if (rowId > 0) {
                            inserted++
                            // Fetch Rules
                            val activeRules = db.categorizationRuleDao().getAllActiveRules().first() // Collect Flow
                            
                            // Auto-discover account holder name from NEFT salary deposits FIRST
                            if (body.contains("NEFT", ignoreCase = true)) {
                                val holderName = CounterpartyExtractor.extractAccountHolderName(body)
                                if (holderName != null) {
                                    // Get accounts without holder name
                                    val accountsToUpdate = db.userAccountDao().getAllAccounts()
                                        .filter { it.accountHolderName == null }
                                        .map { it.accountNumberLast4 }
                                    if (accountsToUpdate.isNotEmpty()) {
                                        // Update all accounts without holder name with this discovered name
                                        for (accountNum in accountsToUpdate) {
                                            try {
                                                db.userAccountDao().updateAccountHolderName(accountNum, holderName)
                                            } catch (e: Exception) {
                                                Log.w(TAG, "Failed to update holder name: ${e.message}")
                                            }
                                        }
                                    }
                                }
                                
                                // SALARY PATTERN TRACKING: Track NEFT income sources
                                if (txnType == TransactionType.INCOME) {
                                    trackNeftSalarySource(db, body, parsed.timestamp)
                                }
                            }
                            
                            // NOW fetch userAccounts (with freshly saved holder name)
                            val userAccounts = db.userAccountDao().getAllAccounts()
                            
                            // Fetch known salary sources for automatic salary categorization
                            val salarySources = db.neftSourceDao().getSalarySources()
                                .map { Pair(it.ifscCode, it.senderPattern) }
                                .toSet()

                            CategoryMapper.categorize(
                                counterparty = parsed.counterparty,
                                transactionType = parsed.transactionType,
                                rules = activeRules,
                                userAccounts = userAccounts, // Pass discovered accounts
                                categoryMap = categoryNameMap,
                                messageBody = body, // For NEFT self-transfer detection
                                salarySources = salarySources // For automatic salary categorization
                            )
                            ClassificationDebugLogger.finalizeLog(logId, FinalDecision(
                                transactionType = txnType.name,
                                categoryId = categoryId,
                                categoryName = parsed.category,
                                confidence = "HIGH",
                                finalConfidence = 1.0,
                                requiresUserConfirmation = false,
                                entityType = parsed.counterparty.type.name,
                                isExpenseEligible = isExpenseEligible
                            ))
                            ClassificationDebugLogger.persistLog(context, logId)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing scan item", e)
                        ClassificationDebugLogger.logError(logId, "Scan Error: ${e.message}", e)
                        ClassificationDebugLogger.persistLog(context, logId)
                    }
                }
            }
        } finally {
            ClassificationDebugLogger.endBatchSession(context)
            Log.d(TAG, "Scan complete: inserted=$inserted, skipped=$skipped, epf/nps=$epfNps")
        }
    }

    private suspend fun handleRetirement(
        db: com.saikumar.expensetracker.data.db.AppDatabase,
        parsed: ParsedTransaction,
        smsHash: String
    ) {
        // Extract balance from EPF/NPS messages
        // Use non-greedy match .*? with DOT_MATCHES_ALL to handle newlines
        val options = setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
        val balanceRegex = Regex("(?:balance|bal).*?Rs\\.?\\s*([\\d,]+(?:\\.\\d+)?)", options)
        val contributionRegex = Regex("(?:contribution|credit).*?Rs\\.?\\s*([\\d,]+(?:\\.\\d+)?)", options)
        val monthRegex = Regex("(?:for|month).*?(\\w{3})['-]?(\\d{2,4})", options)
        
        val balanceMatch = balanceRegex.find(parsed.body)
        val contributionMatch = contributionRegex.find(parsed.body)
        val monthMatch = monthRegex.find(parsed.body)
        
        // Log extraction results for debugging
        Log.d(TAG, "EPF/NPS Extraction: BalMatch=${balanceMatch?.value}, ContribMatch=${contributionMatch?.value}")

        val balance = balanceMatch?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull() ?: 0.0
        val contribution = contributionMatch?.groupValues?.get(1)?.replace(",", "")?.toDoubleOrNull() ?: 0.0
        
        // Abort only if NEITHER balance NOR contribution was found
        if (balance == 0.0 && contribution == 0.0) {
            Log.d(TAG, "EPF/NPS: No value found, skipping.")
            return
        }

        val month = if (monthMatch != null) "${monthMatch.groupValues[1]}-${monthMatch.groupValues[2]}" else "Unknown"
        
        val type = if (parsed.senderType == SenderClassifier.SenderType.PENSION) 
            RetirementType.EPF else RetirementType.NPS
        
        val retirementBalance = RetirementBalance(
            type = type,
            balancePaisa = (balance * 100).toLong(),
            contributionPaisa = (contribution * 100).toLong(),
            month = month,
            timestamp = parsed.timestamp,
            identifier = smsHash.take(8)
        )
        
        db.retirementDao().upsert(retirementBalance)
    }

    suspend fun processAndInsert(context: Context, sender: String, body: String, timestamp: Long) = withContext(Dispatchers.IO) {
        val logId = ClassificationDebugLogger.startLog(
            RawInputCapture(body, "SMS_RECEIVER", timestamp, sender, 0, "UNKNOWN", "UNKNOWN")
        )

        try {
            val app = context.applicationContext as ExpenseTrackerApplication
            val db = app.database
            
            // Skip if already processed
            val smsHash = generateHash(body)
            if (db.transactionDao().existsBySmsHash(smsHash)) return@withContext

            val parsed = process(sender, body, timestamp)
            if (parsed == null) {
                // Excluded
                return@withContext
            }
            
            // Log Parsed
             ClassificationDebugLogger.logParsedFields(logId, ParsedFields(
                merchantName = parsed.counterparty.name,
                upiId = null,
                neftReference = null,
                detectedKeywords = emptyList(),
                accountTypeDetected = if (parsed.isDebit == true) "DEBIT" else "CREDIT",
                senderInferred = parsed.senderType.name,
                receiverInferred = null
            ))
            
            val categories = db.categoryDao().getAllEnabledCategories().first()
            if (categories.isEmpty()) {
                val msg = "No categories found! Cannot insert transaction."
                Log.e(TAG, msg)
                ClassificationDebugLogger.logError(logId, msg, null)
                ClassificationDebugLogger.persistLog(context, logId)
                return@withContext
            }
            val categoryMap = categories.associateBy { it.name }

            // Handle EPF/NPS separately
            if (parsed.senderType == SenderClassifier.SenderType.PENSION ||
                parsed.senderType == SenderClassifier.SenderType.INVESTMENT) {
                handleRetirement(db, parsed, smsHash)
                ClassificationDebugLogger.finalizeLog(logId, FinalDecision(
                    transactionType = "RETIREMENT", categoryId = 0, categoryName = "RETIREMENT",
                    confidence = "HIGH", finalConfidence = 1.0, requiresUserConfirmation = false,
                    reasoning = "Retirement handled"
                ))
                ClassificationDebugLogger.persistLog(context, logId)
                return@withContext
            }
            
            // Skip if no amount
            if (parsed.amount == null || parsed.amount <= 0) return@withContext
            
            // Find category ID - Safe fallback
            var categoryId = categoryMap[parsed.category]?.id 
                ?: categoryMap["Uncategorized"]?.id 
                ?: categories.first().id 
            
            // RECURRENCE CHECK FOR SALARY
            if (parsed.category == "Other Income" && parsed.transactionType == TransactionExtractor.TransactionType.INCOME) {
                 if (checkSalaryRecurrence(db, parsed.counterparty.name, parsed.timestamp, parsed.amount)) {
                     // Found recurrence! Upgrade to Salary
                     categoryId = categoryMap["Salary"]?.id ?: categoryId
                 }
            } 
            
            // Map transaction type
            val txnType = when (parsed.transactionType) {
                TransactionExtractor.TransactionType.INCOME -> TransactionType.INCOME
                TransactionExtractor.TransactionType.EXPENSE -> TransactionType.EXPENSE
                TransactionExtractor.TransactionType.TRANSFER -> TransactionType.TRANSFER
                TransactionExtractor.TransactionType.LIABILITY -> TransactionType.LIABILITY_PAYMENT
                TransactionExtractor.TransactionType.PENSION -> TransactionType.PENSION
                TransactionExtractor.TransactionType.INVESTMENT -> TransactionType.INVESTMENT_CONTRIBUTION
                TransactionExtractor.TransactionType.STATEMENT -> TransactionType.STATEMENT
                TransactionExtractor.TransactionType.UNKNOWN -> TransactionType.UNKNOWN
            }
            
            val rawMerchant = parsed.counterparty.name
            val normalizedMerchant = MerchantNormalizer.normalize(rawMerchant)
            
            if (!rawMerchant.isNullOrBlank() && !normalizedMerchant.isNullOrBlank() && rawMerchant != normalizedMerchant) {
                 try {
                     db.merchantAliasDao().insert(MerchantAlias(rawMerchant, normalizedMerchant))
                 } catch (e: Exception) { Log.w(TAG, "Alias insert failed: ${e.message}") }
            }
            
            val transaction = Transaction(
                amountPaisa = (parsed.amount * 100).toLong(),
                categoryId = categoryId,
                timestamp = parsed.timestamp,
                source = TransactionSource.SMS,
                transactionType = txnType,
                smsHash = smsHash,
                merchantName = normalizedMerchant ?: rawMerchant,
                smsSnippet = parsed.body.take(100),
                fullSmsBody = parsed.body,
                // ECONOMIC ACTIVITY INVARIANT: Only actual Expenses count.
                // Transfers, Income, Liability Payments are NOT expenses.
                isExpenseEligible = txnType == TransactionType.EXPENSE
            )
            
            db.transactionDao().insertTransaction(transaction)
            
            ClassificationDebugLogger.finalizeLog(logId, FinalDecision(
                transactionType = txnType.name,
                categoryId = categoryId,
                categoryName = parsed.category,
                confidence = "HIGH",
                finalConfidence = 1.0,
                requiresUserConfirmation = false,
                reasoning = "Inserted successfully. CategoryID: $categoryId"
            ))
            ClassificationDebugLogger.persistLog(context, logId)
            
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
        
        // 1. Load Categories & Rules
        val categories = db.categoryDao().getAllEnabledCategories().first()
        if (categories.isEmpty()) return@withContext
        val categoryMap = categories.associateBy { it.name }
        val categoryNameMap = categories.associate { it.id to it.name } // ID -> Name
        
        val rules = db.categorizationRuleDao().getAllActiveRules().first()
        
        // 2. Load ALL Transactions (using wide time range)
        // Since DAO doesn't have getAll, use getTransactionsInPeriod with wide range
        val allTxnsFlow = db.transactionDao().getTransactionsInPeriod(0, Long.MAX_VALUE)
        // Sort Chronologically (Oldest First) so specifically for recurrence check, 
        // the "history" is already cleaned/updated when satisfying the check for newer txns.
        val allTxnsWithCategory = allTxnsFlow.first().sortedBy { it.transaction.timestamp }
        
        var updatedCount = 0
        Log.d(TAG, "Found ${allTxnsWithCategory.size} transactions to re-evaluate")

        for (item in allTxnsWithCategory) {
            val txn = item.transaction
            if (txn.fullSmsBody.isNullOrBlank()) continue // Skip manual txns
            
            // Infer Sender Type to bypass exclusion/UNKNOWN check
            val inferredSenderType = when (txn.transactionType) {
                TransactionType.PENSION -> SenderClassifier.SenderType.PENSION
                TransactionType.INVESTMENT_CONTRIBUTION -> SenderClassifier.SenderType.INVESTMENT
                else -> SenderClassifier.SenderType.BANK
            }
            
            val logId = ClassificationDebugLogger.startLog(
                RawInputCapture(txn.fullSmsBody, "RECLASSIFY", txn.timestamp, "UNKNOWN", 0, "UNKNOWN", "UNKNOWN")
            )
            
            try {
                // FORCE RE-PROCESS
                val parsed = process(
                    sender = "UNKNOWN", // Sender lost, but forced type handles it
                    body = txn.fullSmsBody, 
                    timestamp = txn.timestamp, 
                    rules = rules, 
                    categoryMap = categoryNameMap,
                    forceSenderType = inferredSenderType
                )
                
                if (parsed != null) {
                    // Update log with parsed amount and direction
                    ClassificationDebugLogger.updateRawInput(
                        logId = logId,
                        amountPaisa = ((parsed.amount ?: 0.0) * 100).toLong(),
                        direction = if (parsed.isDebit == true) "DEBIT" else if (parsed.isDebit == false) "CREDIT" else "UNKNOWN",
                        accountType = parsed.accountTypeDetected.name
                    )
                    
                    // Update Transaction object
                    // Note: We do NOT change amount or timestamp, only Metadata
                    
                    var newCategoryId = categoryMap[parsed.category]?.id 
                        ?: categoryMap["Uncategorized"]?.id 
                        ?: categories.first().id
                    
                    // RECURRENCE CHECK FOR SALARY
                    if (parsed.category == "Other Income" && parsed.transactionType == TransactionExtractor.TransactionType.INCOME) {
                         if (checkSalaryRecurrence(db, parsed.counterparty.name, parsed.timestamp, parsed.amount)) {
                             newCategoryId = categoryMap["Salary"]?.id ?: newCategoryId
                         }
                    }
                    
                    // Only update if changed (optimization)
                    var changed = false
                    if (txn.categoryId != newCategoryId) changed = true
                    val rawMerchant = parsed.counterparty.name
                    val normalizedMerchant = MerchantNormalizer.normalize(rawMerchant)
                    val finalMerchant = normalizedMerchant ?: rawMerchant
                    
                    if (!rawMerchant.isNullOrBlank() && !normalizedMerchant.isNullOrBlank() && rawMerchant != normalizedMerchant) {
                         try {
                             db.merchantAliasDao().insert(MerchantAlias(rawMerchant, normalizedMerchant))
                         } catch (e: Exception) { Log.w(TAG, "Alias insert failed: ${e.message}") }
                    }

                    if (txn.merchantName != finalMerchant) changed = true
                    // if (txn.transactionType != mapType(parsed.transactionType)) changed = true // Risky to change type? Maybe safe.
                    
                    if (changed) {
                        // HISTORICAL INVARIANT: metadata immutability.
                        // We ONLY update Category and Merchant.
                        // We NEVER update Amount, Timestamp, or TransactionType (prevent report drift).
                        val newTxn = txn.copy(
                            categoryId = newCategoryId,
                            merchantName = finalMerchant
                        )
                        db.transactionDao().updateTransaction(newTxn)
                        updatedCount++
                        
                        ClassificationDebugLogger.finalizeLog(logId, FinalDecision(
                            transactionType = "RECLASSIFIED", categoryId = newCategoryId, categoryName = parsed.category,
                            confidence = "HIGH", finalConfidence = 1.0, requiresUserConfirmation = false,
                            entityType = parsed.counterparty.type.name
                        ))
                    } else {
                         ClassificationDebugLogger.finalizeLog(logId, FinalDecision(
                            transactionType = "UNCHANGED", categoryId = txn.categoryId, categoryName = parsed.category,
                            confidence = "HIGH", finalConfidence = 1.0, requiresUserConfirmation = false,
                            entityType = parsed.counterparty.type.name
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
        
        Log.d(TAG, "Reclassify Complete. Updated $updatedCount transactions.")
        ClassificationDebugLogger.endBatchSession(context)
    }

    suspend fun assignCategoryToTransaction(
        context: Context,
        transactionId: Long,
        categoryId: Long,
        applyToSimilar: Boolean = false,
        transactionType: TransactionType? = null
    ) {
        val app = context.applicationContext as ExpenseTrackerApplication
        val db = app.database
        val txn = db.transactionDao().getById(transactionId) ?: return
        val updated = if (transactionType != null) {
            txn.copy(categoryId = categoryId, transactionType = transactionType)
        } else {
            txn.copy(categoryId = categoryId)
        }
        db.transactionDao().updateTransaction(updated)
        if (applyToSimilar && txn.merchantName != null) {
            val allSimilar = db.transactionDao().getByMerchantName(txn.merchantName)
            if (allSimilar.isNotEmpty()) {
                val similarTxns = allSimilar.map { 
                     if (transactionType != null) {
                        it.copy(categoryId = categoryId, transactionType = transactionType)
                    } else {
                        it.copy(categoryId = categoryId)
                    }
                }
                db.transactionDao().updateTransactions(similarTxns)
                Log.d(TAG, "Updated ${similarTxns.size} similar transactions for merchant: ${txn.merchantName}")
            }
        }
    }

    fun findSimilarTransactions(
        sourceTransaction: Transaction,
        allTransactions: List<Transaction>
    ): SimilarityResult {
        val similar = allTransactions.filter {
            it.id != sourceTransaction.id &&
            it.merchantName == sourceTransaction.merchantName &&
            it.merchantName != null
        }
        return SimilarityResult(similar, "MERCHANT_NAME", 0.8f)
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
