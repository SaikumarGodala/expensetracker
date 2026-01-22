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
import com.saikumar.expensetracker.util.MerchantNormalizer
import com.saikumar.expensetracker.data.entity.MerchantAlias
import com.saikumar.expensetracker.data.db.TransactionPairCandidate

data class ParsedTransaction(
    val sender: String,
    val body: String,
    val timestamp: Long,
    val senderType: SenderClassifier.SenderType,
    val transactionType: TransactionType,
    val amount: Double?,
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

object SmsProcessor {
    private const val TAG = "SmsProcessor"

    fun process(
        sender: String, 
        body: String, 
        timestamp: Long,
        rules: List<CategorizationRule> = emptyList(),
        categoryMap: Map<Long, String> = emptyMap(),
        forceSenderType: SenderClassifier.SenderType? = null,
        salaryCompanyNames: Set<String> = emptySet() // For salary company name matching
    ): ParsedTransaction? {
        val senderType = forceSenderType ?: SenderClassifier.classify(sender)
        if (senderType == SenderClassifier.SenderType.EXCLUDED) return null

        val extraction = TransactionExtractor.extract(body, senderType)
        var counterparty = CounterpartyExtractor.extract(body, extraction.type)
        
        // Override for Statement to prevent garbage merchant extraction (e.g. "ar")
        if (extraction.type == TransactionType.STATEMENT) {
            counterparty = CounterpartyExtractor.Counterparty("Credit Card Statement", null, CounterpartyExtractor.CounterpartyType.MERCHANT)
        }

        val classificationTrace = mutableListOf<String>()
        val category = CategoryMapper.categorize(
            counterparty = counterparty, 
            transactionType = extraction.type, 
            rules = rules, 
            categoryMap = categoryMap, 
            messageBody = body, 
            salaryCompanyNames = salaryCompanyNames,
            trace = classificationTrace
        )

        return ParsedTransaction(
            sender = sender,
            body = body,
            timestamp = timestamp,
            senderType = senderType,
            transactionType = extraction.type,
            amount = extraction.amount,
            counterparty = counterparty,
            category = category,
            isDebit = extraction.isDebit,
            classificationTrace = classificationTrace
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
        var categories = db.categoryDao().getAllEnabledCategories().first()
        
        // AUTO-SEED: If no categories exist (fresh install), seed them now
        if (categories.isEmpty()) {
            Log.w(TAG, "No categories found - seeding defaults...")
            val categoriesToSeed = listOf(
                Category(name = "Salary", type = CategoryType.INCOME, isDefault = true),
                Category(name = "Other Income", type = CategoryType.INCOME, isDefault = true),
                Category(name = "Unverified Income", type = CategoryType.INCOME, isDefault = true), // AUDIT: Needs review
                Category(name = "Self Transfer", type = CategoryType.INCOME, isDefault = true), // Inter-bank transfers
                Category(name = "Refund", type = CategoryType.INCOME, isDefault = true),
                Category(name = "Interest", type = CategoryType.INCOME, isDefault = true),
                Category(name = "Housing", type = CategoryType.FIXED_EXPENSE, isDefault = true),
                Category(name = "Utilities", type = CategoryType.FIXED_EXPENSE, isDefault = true),
                Category(name = "Insurance", type = CategoryType.FIXED_EXPENSE, isDefault = true),
                Category(name = "Credit Bill Payments", type = CategoryType.FIXED_EXPENSE, isDefault = true),
                Category(name = "Groceries", type = CategoryType.VARIABLE_EXPENSE, isDefault = true),
                Category(name = "Dining Out", type = CategoryType.VARIABLE_EXPENSE, isDefault = true),
                Category(name = "Entertainment", type = CategoryType.VARIABLE_EXPENSE, isDefault = true),
                Category(name = "Travel", type = CategoryType.VARIABLE_EXPENSE, isDefault = true),
                Category(name = "Cab & Taxi", type = CategoryType.VARIABLE_EXPENSE, isDefault = true),
                Category(name = "Food Delivery", type = CategoryType.VARIABLE_EXPENSE, isDefault = true),
                Category(name = "Medical", type = CategoryType.VARIABLE_EXPENSE, isDefault = true),
                Category(name = "Shopping", type = CategoryType.VARIABLE_EXPENSE, isDefault = true),
                Category(name = "Miscellaneous", type = CategoryType.VARIABLE_EXPENSE, isDefault = true),
                Category(name = "Unknown Expense", type = CategoryType.VARIABLE_EXPENSE, isDefault = true),
                Category(name = "Uncategorized", type = CategoryType.VARIABLE_EXPENSE, isDefault = true),
                Category(name = "P2P Transfers", type = CategoryType.VARIABLE_EXPENSE, isDefault = true),
                Category(name = "Mutual Funds", type = CategoryType.INVESTMENT, isDefault = true),
                Category(name = "Fuel", type = CategoryType.VEHICLE, isDefault = true)
            )
            db.categoryDao().insertCategories(categoriesToSeed)
            categories = db.categoryDao().getAllEnabledCategories().first()
            Log.d(TAG, "Seeded ${categories.size} categories")
        }
        
        if (categories.isEmpty()) {
            Log.e(TAG, "Scan Inbox Aborted: Category seeding failed!")
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
                        RawInputCapture(body, "SMS_SCAN", timestamp, sender, 0)
                    )
                    
                    try {
                        val smsHash = generateHash(body)
                        
                        // Skip if already processed
                        if (db.transactionDao().existsBySmsHash(smsHash)) {
                            skipped++
                            continue
                        }

                        // Fetch salary company names for initial parse
                        val salaryCompanyNames = app.preferencesManager.getSalaryCompanyNamesSync()
                        
                        // 1. PARSE (Extract fields first)
                        // We parse first so the Gate has full context (Amount, inferred Type)
                        // If parsing fails (e.g. excluded sender), we skip immediately.
                        var parsed = process(sender, body, timestamp, rules, categoryNameMap, salaryCompanyNames = salaryCompanyNames)
                        if (parsed == null) {
                             // Excluded sender or extraction failure
                             continue
                        }
                        
                        // Update log with parsed amount
                        ClassificationDebugLogger.updateLogAmount(
                            logId = logId,
                            amountPaisa = ((parsed.amount ?: 0.0) * 100).toLong()
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
                        
                        // Handle EPF/NPS separately
                        if (parsed.senderType == SenderClassifier.SenderType.PENSION ||
                            parsed.senderType == SenderClassifier.SenderType.INVESTMENT) {
                            handleRetirement(db, parsed, smsHash)
                            ClassificationDebugLogger.finalizeLog(logId, FinalDecision(
                                transactionType = "RETIREMENT", categoryId = 0, categoryName = "RETIREMENT",
                                finalConfidence = 1.0, requiresUserConfirmation = false
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
                        
                        // TRANSFER CIRCLE CHECK
                        // Trusted contacts -> P2P Transfers (TRANSFER)
                        // Untrusted/Unknown -> Miscellaneous (EXPENSE)
                        var isUntrustedP2P = false
                        if (parsed.category == "P2P Transfers" || parsed.counterparty.type == CounterpartyExtractor.CounterpartyType.PERSON) {
                            val recipientName = parsed.counterparty.name
                            val isTrusted = if (!recipientName.isNullOrBlank()) {
                                db.transferCircleDao().isInCircle(recipientName)
                            } else false
                            
                            if (!isTrusted) {
                                categoryId = categoryMap["Miscellaneous"]?.id ?: categoryId
                                isUntrustedP2P = true
                                Log.d(TAG, "Untrusted P2P (${recipientName ?: "Unknown"}) -> Treated as EXPENSE (Miscellaneous)")
                            } else {
                                categoryId = categoryMap["P2P Transfers"]?.id ?: categoryId
                                Log.d(TAG, "Trusted Transfer Circle ($recipientName) -> P2P TRANSFER")
                            }
                        }
                        
                        // RECURRENCE CHECK FOR SALARY
                        if (parsed.category == "Other Income" && parsed.transactionType == TransactionType.INCOME) {
                             if (checkSalaryRecurrence(db, parsed.counterparty.name, parsed.timestamp, parsed.amount)) {
                                 categoryId = categoryMap["Salary"]?.id ?: categoryId
                                 Log.d(TAG, "Upgraded ${parsed.counterparty.name} to Salary due to recurrence")
                             }
                        }
                        
                        // Map transaction type
                        // Untrusted P2P → EXPENSE, CC Bill → LIABILITY_PAYMENT (but NOT for statements), else normal mapping
                        var txnType = when {
                            isUntrustedP2P -> TransactionType.EXPENSE
                            // FIX #5: STATEMENT must be preserved even if category is "Credit Bill Payments"
                            // A statement is informational, not an actual payment
                            parsed.transactionType == TransactionType.STATEMENT -> TransactionType.STATEMENT
                            parsed.category == "Credit Bill Payments" -> TransactionType.LIABILITY_PAYMENT
                            else -> when (parsed.transactionType) {
                                TransactionType.INCOME -> TransactionType.INCOME
                                TransactionType.EXPENSE -> TransactionType.EXPENSE
                                TransactionType.TRANSFER -> TransactionType.TRANSFER
                                TransactionType.LIABILITY_PAYMENT -> TransactionType.LIABILITY_PAYMENT
                                TransactionType.PENSION -> TransactionType.PENSION
                                TransactionType.INVESTMENT_CONTRIBUTION -> TransactionType.INVESTMENT_CONTRIBUTION
                                TransactionType.STATEMENT -> TransactionType.STATEMENT
                                TransactionType.UNKNOWN -> TransactionType.UNKNOWN
                                else -> parsed.transactionType // Pass through CASHBACK, REFUND, PENDING, IGNORE, INVESTMENT_OUTFLOW
                            }
                        }
                        
                        // ====== FIX #1: CRITICAL INVARIANT ======
                        // CREDIT direction (isDebit == false) MUST NOT result in EXPENSE
                        // This is a fundamental accounting invariant: money coming IN cannot be an expense
                        if (parsed.isDebit == false && txnType == TransactionType.EXPENSE) {
                            Log.w(TAG, "INVARIANT_FIX: CREDIT direction forced INCOME over EXPENSE for ${parsed.counterparty.name}")
                            txnType = TransactionType.INCOME
                        }
                        
                        // ====== FIX #2: Cashback VPA Override ======
                        // VPAs containing "cashback", "reward", etc. should be CASHBACK type, not regular P2P
                        val upiId = parsed.counterparty.upiId?.lowercase() ?: ""
                        val cashbackPatterns = listOf("cashback", "reward", "promo", "bhimcashback")
                        if (cashbackPatterns.any { upiId.contains(it) } && parsed.isDebit == false) {
                            Log.d(TAG, "CASHBACK_DETECTED: VPA $upiId identified as cashback")
                            txnType = TransactionType.CASHBACK
                        }
                        
                        // ====== FIX #3: P2P/PERSON → TRANSFER INVARIANT ======
                        // If category is P2P Transfers OR entity is PERSON (above threshold), type MUST be TRANSFER
                        // P2P transfers are neither income nor expense - they're neutral transfers
                        val isP2pOrPerson = !isUntrustedP2P && (
                            parsed.category == "P2P Transfers" ||
                            parsed.counterparty.type == CounterpartyExtractor.CounterpartyType.PERSON
                        )
                        if (isP2pOrPerson && txnType != TransactionType.CASHBACK && txnType != TransactionType.TRANSFER) {
                            Log.d(TAG, "P2P_INVARIANT: Forcing TRANSFER for P2P/PERSON entity (was ${txnType}, entity=${parsed.counterparty.type})")
                            txnType = TransactionType.TRANSFER
                        }
                        
                        val rawMerchant = parsed.counterparty.name
                        val normalizedMerchant = MerchantNormalizer.normalize(rawMerchant)
                        
                        if (!rawMerchant.isNullOrBlank() && !normalizedMerchant.isNullOrBlank() && rawMerchant != normalizedMerchant) {
                            try {
                                db.merchantAliasDao().insert(MerchantAlias(rawMerchant, normalizedMerchant))
                            } catch (e: Exception) { Log.w(TAG, "Alias insert failed: ${e.message}") }
                        }

                        // Extract account number from SMS for self-transfer detection
                        val accountPattern = Regex("""(?:A/c|Acct|Account)\s+[*X]*(\d{4})""", RegexOption.IGNORE_CASE)
                        val cardPattern = Regex("""(?:Card|Credit Card)\s+(?:ending\s+)?(?:[*X]*\s*)?(\d{4})""", RegexOption.IGNORE_CASE)
                        val extractedAccountNum = accountPattern.find(body)?.groupValues?.get(1)
                            ?: cardPattern.find(body)?.groupValues?.get(1)

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
                            accountNumberLast4 = extractedAccountNum,
                            isExpenseEligible = txnType == TransactionType.EXPENSE,
                            entityType = when (parsed.counterparty.type) {
                                CounterpartyExtractor.CounterpartyType.PERSON -> EntityType.PERSON
                                CounterpartyExtractor.CounterpartyType.MERCHANT -> EntityType.BUSINESS
                                else -> EntityType.UNKNOWN
                            },
                            confidenceScore = CategoryMapper.calculateConfidence(parsed.category)
                        )
                        
                        val rowId = db.transactionDao().insertTransaction(transaction)
                        if (rowId > 0) {
                            inserted++
                            
                            // ACCOUNT DISCOVERY: Detect and save bank accounts from SMS
                            try {
                                AccountDiscoveryManager.scanAndDiscover(body, sender, db.userAccountDao())
                            } catch (e: Exception) {
                                Log.w(TAG, "Account discovery failed: ${e.message}")
                            }
                            
                            // Fetch Rules
                            val activeRules = db.categorizationRuleDao().getAllActiveRules().first() // Collect Flow
                            
                            // Auto-discover account holder name from NEFT salary deposits FIRST
                            if (body.contains("NEFT", ignoreCase = true)) {
                                val holderName = CounterpartyExtractor.extractAccountHolderName(body)
                                if (holderName != null) {
                                    // Target specific account if we extracted it from this SMS
                                    if (extractedAccountNum != null) {
                                        Log.d(TAG, "Updating holder name for account $extractedAccountNum to '$holderName'")
                                        try {
                                            AccountDiscoveryManager.updateHolderName(db.userAccountDao(), extractedAccountNum, holderName)
                                        } catch (e: Exception) {
                                             Log.w(TAG, "Failed to update holder name for $extractedAccountNum: ${e.message}")
                                        }
                                    } else {
                                        // Fallback: Update accounts without holder name
                                        val accountsToUpdate = db.userAccountDao().getAllAccounts()
                                            .filter { it.accountHolderName == null }
                                            .map { it.accountNumberLast4 }
                                        
                                        for (accountNum in accountsToUpdate) {
                                            try {
                                                AccountDiscoveryManager.updateHolderName(db.userAccountDao(), accountNum, holderName)
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
                            
                            // Fetch user-configured salary company names
                            val salaryCompanyNames = app.preferencesManager.getSalaryCompanyNamesSync()

                            CategoryMapper.categorize(
                                counterparty = parsed.counterparty,
                                transactionType = parsed.transactionType,
                                rules = activeRules,
                                userAccounts = userAccounts, // Pass discovered accounts
                                categoryMap = categoryNameMap,
                                messageBody = body, // For NEFT self-transfer detection
                                salarySources = salarySources, // For automatic salary categorization
                                salaryCompanyNames = salaryCompanyNames // For company name matching
                            )
                            ClassificationDebugLogger.finalizeLog(logId, FinalDecision(
                                transactionType = txnType.name,
                                categoryId = categoryId,
                                categoryName = parsed.category,
                                finalConfidence = 1.0,
                                requiresUserConfirmation = false,
                                entityType = parsed.counterparty.type.name,
                                isExpenseEligible = txnType == TransactionType.EXPENSE,
                                whyNotOtherCategories = parsed.classificationTrace
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
        
        // Run self-transfer pairing after scan
        pairSelfTransfers(context)
        pairStatementToPayments(context)
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
            
            // Skip if already processed
            val smsHash = generateHash(body)
            if (db.transactionDao().existsBySmsHash(smsHash)) return@withContext

            // Fetch salary company names
            val salaryCompanyNames = app.preferencesManager.getSalaryCompanyNamesSync()
            
            val parsed = process(sender, body, timestamp, salaryCompanyNames = salaryCompanyNames)
            if (parsed == null) {
                // Excluded
                return@withContext
            }

            // Update log amount
            ClassificationDebugLogger.updateLogAmount(
                logId = logId,
                amountPaisa = ((parsed.amount ?: 0.0) * 100).toLong()
            )
            
            // Log Parsed
             ClassificationDebugLogger.logParsedFields(logId, ParsedFields(
                merchantName = parsed.counterparty.name,
                upiId = null,
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
                    finalConfidence = 1.0, requiresUserConfirmation = false,
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
            
            // TRANSFER CIRCLE CHECK
            var isUntrustedP2P = false
            if (parsed.category == "P2P Transfers" || parsed.counterparty.type == CounterpartyExtractor.CounterpartyType.PERSON) {
                val recipientName = parsed.counterparty.name
                val isTrusted = if (!recipientName.isNullOrBlank()) {
                    db.transferCircleDao().isInCircle(recipientName)
                } else false
                
                if (!isTrusted) {
                    categoryId = categoryMap["Miscellaneous"]?.id ?: categoryId
                    isUntrustedP2P = true
                } else {
                    categoryId = categoryMap["P2P Transfers"]?.id ?: categoryId
                }
            }
            
            // RECURRENCE CHECK FOR SALARY
            if (parsed.category == "Other Income" && parsed.transactionType == TransactionType.INCOME) {
                 if (checkSalaryRecurrence(db, parsed.counterparty.name, parsed.timestamp, parsed.amount)) {
                     categoryId = categoryMap["Salary"]?.id ?: categoryId
                 }
            } 
            
            // Map transaction type - Untrusted P2P becomes EXPENSE
            var txnType = if (isUntrustedP2P) {
                TransactionType.EXPENSE
            } else {
                when {
                     // 1. STATEMENTS (Highest Priority - never become expenses)
                    parsed.transactionType == TransactionType.STATEMENT -> TransactionType.STATEMENT
                    
                    // 2. CREDIT BILL PAYMENTS (Distinct from Statements)
                    parsed.category == "Credit Bill Payments" -> TransactionType.LIABILITY_PAYMENT

                    // 2.1 CASHBACK
                    parsed.category == "Cashback" || parsed.category == "Cashback / Rewards" -> TransactionType.CASHBACK
                    
                    // 3. Standard Mapping
                    else -> when (parsed.transactionType) {
                        TransactionType.INCOME -> TransactionType.INCOME
                        TransactionType.EXPENSE -> TransactionType.EXPENSE
                        TransactionType.TRANSFER -> TransactionType.TRANSFER
                        TransactionType.LIABILITY_PAYMENT -> TransactionType.LIABILITY_PAYMENT
                        TransactionType.PENSION -> TransactionType.PENSION
                        TransactionType.INVESTMENT_CONTRIBUTION -> TransactionType.INVESTMENT_CONTRIBUTION
                        TransactionType.STATEMENT -> TransactionType.STATEMENT
                        TransactionType.UNKNOWN -> TransactionType.UNKNOWN
                        else -> parsed.transactionType // Pass through CASHBACK, REFUND, PENDING, IGNORE, INVESTMENT_OUTFLOW
                    }
                }
            }
            
            // ====== FIX #1: CRITICAL INVARIANT ======
            // CREDIT direction (isDebit == false) MUST NOT result in EXPENSE
            if (parsed.isDebit == false && txnType == TransactionType.EXPENSE) {
                Log.w(TAG, "INVARIANT_FIX: CREDIT direction forced INCOME over EXPENSE for ${parsed.counterparty.name}")
                txnType = TransactionType.INCOME
            }
            
            // ====== FIX #2: Cashback VPA/Category Override ======
            val upiId = parsed.counterparty.upiId?.lowercase() ?: ""
            val cashbackPatterns = listOf("cashback", "reward", "promo", "bhimcashback")
            if ((parsed.category == "Cashback" || cashbackPatterns.any { upiId.contains(it) }) && parsed.isDebit == false) {
                Log.d(TAG, "CASHBACK_DETECTED: Identified as cashback (Category: ${parsed.category}, VPA: $upiId)")
                txnType = TransactionType.CASHBACK
            }
            
            // ====== FIX #3: P2P/PERSON → TRANSFER INVARIANT ======
            // If category is P2P Transfers OR entity is PERSON (above threshold), type MUST be TRANSFER
            // P2P transfers are neither income nor expense - they're neutral transfers
            val isP2pOrPerson = !isUntrustedP2P && (
                parsed.category == "P2P Transfers" ||
                parsed.counterparty.type == CounterpartyExtractor.CounterpartyType.PERSON
            )
            if (isP2pOrPerson && txnType != TransactionType.CASHBACK && txnType != TransactionType.TRANSFER) {
                Log.d(TAG, "P2P_INVARIANT: Forcing TRANSFER for P2P/PERSON entity (was ${txnType}, entity=${parsed.counterparty.type})")
                txnType = TransactionType.TRANSFER
            }
            
            val rawMerchant = parsed.counterparty.name
            val normalizedMerchant = MerchantNormalizer.normalize(rawMerchant)
            
            if (!rawMerchant.isNullOrBlank() && !normalizedMerchant.isNullOrBlank() && rawMerchant != normalizedMerchant) {
                 try {
                     db.merchantAliasDao().insert(MerchantAlias(rawMerchant, normalizedMerchant))
                 } catch (e: Exception) { Log.w(TAG, "Alias insert failed: ${e.message}") }
            }
            
            // Extract account number from SMS for self-transfer detection
            val accountPattern = Regex("""(?:A/c|Acct|Account)\s+[*X]*(\d{4})""", RegexOption.IGNORE_CASE)
            val cardPattern = Regex("""(?:Card|Credit Card)\s+(?:ending\s+)?(?:[*X]*\s*)?(\d{4})""", RegexOption.IGNORE_CASE)
            val extractedAccountNum = accountPattern.find(body)?.groupValues?.get(1)
                ?: cardPattern.find(body)?.groupValues?.get(1)
            
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
                accountNumberLast4 = extractedAccountNum,
                // ECONOMIC ACTIVITY INVARIANT: Only actual Expenses count.
                // Transfers, Income, Liability Payments are NOT expenses.
                isExpenseEligible = txnType == TransactionType.EXPENSE,
                entityType = when (parsed.counterparty.type) {
                    CounterpartyExtractor.CounterpartyType.PERSON -> EntityType.PERSON
                    CounterpartyExtractor.CounterpartyType.MERCHANT -> EntityType.BUSINESS
                    else -> EntityType.UNKNOWN
                },
                confidenceScore = CategoryMapper.calculateConfidence(parsed.category)
            )
            
            db.transactionDao().insertTransaction(transaction)
            
            // ACCOUNT DISCOVERY: Detect and save bank accounts from incoming SMS
            try {
                AccountDiscoveryManager.scanAndDiscover(body, sender, db.userAccountDao())
            } catch (e: Exception) {
                Log.w(TAG, "Account discovery failed: ${e.message}")
            }
            
            // Send notification for new transaction
            val finalCategoryName = categories.find { it.id == categoryId }?.name ?: parsed.category
            com.saikumar.expensetracker.util.TransactionNotificationHelper.showTransactionNotification(
                context = context,
                amountPaise = transaction.amountPaisa,
                merchantName = transaction.merchantName,
                categoryName = finalCategoryName,
                isDebit = parsed.isDebit ?: true
            )
            
            ClassificationDebugLogger.finalizeLog(logId, FinalDecision(
                transactionType = txnType.name,
                categoryId = categoryId,
                categoryName = parsed.category,
                finalConfidence = 1.0,
                requiresUserConfirmation = false,
                reasoning = "Inserted successfully. CategoryID: $categoryId",
                whyNotOtherCategories = parsed.classificationTrace
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
                RawInputCapture(txn.fullSmsBody, "RECLASSIFY", txn.timestamp, "UNKNOWN", 0)
            )
            
            try {
                // Fetch salary company names for reclassification
                val salaryCompanyNames = app.preferencesManager.getSalaryCompanyNamesSync()
                
                // FORCE RE-PROCESS
                val parsed = process(
                    sender = "UNKNOWN", // Sender lost, but forced type handles it
                    body = txn.fullSmsBody, 
                    timestamp = txn.timestamp, 
                    rules = rules, 
                    categoryMap = categoryNameMap,
                    forceSenderType = inferredSenderType,
                    salaryCompanyNames = salaryCompanyNames
                )
                
                if (parsed != null) {
                    // Update log with parsed amount
                    ClassificationDebugLogger.updateLogAmount(
                        logId = logId,
                        amountPaisa = ((parsed.amount ?: 0.0) * 100).toLong()
                    )

                    // Log Parsed Fields (Crucial for MerchantSanitization debug)
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
                    
                    // Update Transaction object
                    // Note: We do NOT change amount or timestamp, only Metadata
                    
                    var newCategoryId = categoryMap[parsed.category]?.id 
                        ?: categoryMap["Uncategorized"]?.id 
                        ?: categories.first().id
                    

                    // RECURRENCE CHECK FOR SALARY
                    if (parsed.category == "Other Income" && parsed.transactionType == TransactionType.INCOME) {
                         if (checkSalaryRecurrence(db, parsed.counterparty.name, parsed.timestamp, parsed.amount)) {
                             newCategoryId = categoryMap["Salary"]?.id ?: newCategoryId
                         }
                    }

                    // SMALL P2P AS MERCHANT EXPENSE check
                    val rawMerchant = parsed.counterparty.name
                    val normalizedMerchant = MerchantNormalizer.normalize(rawMerchant)
                    val finalMerchant = normalizedMerchant ?: rawMerchant ?: txn.merchantName
                    
                    // SMALL P2P AS MERCHANT EXPENSE check
                    var isSmallP2pExpense = false
                    if (parsed.category == "P2P Transfers") {
                        val threshold = app.preferencesManager.getSmallP2pThresholdSync()
                        val amountPaise = ((parsed.amount ?: 0.0) * 100).toLong()
                        if (threshold > 0 && amountPaise < threshold) {
                            newCategoryId = categoryMap["Miscellaneous"]?.id ?: newCategoryId
                            isSmallP2pExpense = true
                        }
                    }

                    // Recalculate Transaction Type (CRITICAL FIX: Ensure Type matches Category)
                    var newTxnType = if (isSmallP2pExpense) {
                        TransactionType.EXPENSE
                    } else {
                        when {
                            // STATEMENT must be preserved
                            parsed.transactionType == TransactionType.STATEMENT -> TransactionType.STATEMENT
                            // Credit Bill Payments -> LIABILITY_PAYMENT
                            parsed.category == "Credit Bill Payments" -> TransactionType.LIABILITY_PAYMENT
                            // Cashback -> CASHBACK
                            parsed.category == "Cashback" || parsed.category == "Cashback / Rewards" -> TransactionType.CASHBACK
                            else -> when (parsed.transactionType) {
                                TransactionType.INCOME -> TransactionType.INCOME
                                TransactionType.EXPENSE -> TransactionType.EXPENSE
                                TransactionType.TRANSFER -> TransactionType.TRANSFER
                                TransactionType.LIABILITY_PAYMENT -> TransactionType.LIABILITY_PAYMENT
                                TransactionType.PENSION -> TransactionType.PENSION
                                TransactionType.INVESTMENT_CONTRIBUTION -> TransactionType.INVESTMENT_CONTRIBUTION
                                TransactionType.STATEMENT -> TransactionType.STATEMENT
                                TransactionType.UNKNOWN -> TransactionType.UNKNOWN
                                else -> parsed.transactionType // Pass through CASHBACK, REFUND, PENDING, IGNORE, INVESTMENT_OUTFLOW
                            }
                        }
                    }

                    // Apply Invariants (Copied from processAndInsert)
                    if (parsed.isDebit == false && newTxnType == TransactionType.EXPENSE) {
                        newTxnType = TransactionType.INCOME
                    }
                    val upiId = parsed.counterparty.upiId?.lowercase() ?: ""
                    val cashbackPatterns = listOf("cashback", "reward", "promo", "bhimcashback")
                    if ((parsed.category == "Cashback" || cashbackPatterns.any { upiId.contains(it) }) && parsed.isDebit == false) {
                        newTxnType = TransactionType.CASHBACK
                    }
                    if (!isSmallP2pExpense && parsed.category == "P2P Transfers" && newTxnType != TransactionType.CASHBACK && newTxnType != TransactionType.TRANSFER) {
                        newTxnType = TransactionType.TRANSFER
                    }

                    // Recalculate Expense Eligibility
                    // CRITICAL INVARIANT: Only EXPENSE type is eligible. Liability Payments are NOT.
                    val isExpenseEligible = newTxnType == TransactionType.EXPENSE


                    
                    if (!rawMerchant.isNullOrBlank() && !normalizedMerchant.isNullOrBlank() && rawMerchant != normalizedMerchant) {
                         try {
                             db.merchantAliasDao().insert(MerchantAlias(rawMerchant, normalizedMerchant))
                         } catch (e: Exception) { Log.w(TAG, "Alias insert failed: ${e.message}") }
                    }

                    if (txn.categoryId != newCategoryId || 
                        txn.merchantName != finalMerchant ||
                        txn.transactionType != newTxnType ||
                        txn.isExpenseEligible != isExpenseEligible) {
                        
                        val newTxn = txn.copy(
                            categoryId = newCategoryId,
                            merchantName = finalMerchant,
                            transactionType = newTxnType,
                            isExpenseEligible = isExpenseEligible
                        )
                        db.transactionDao().updateTransaction(newTxn)
                        updatedCount++
                        
                        ClassificationDebugLogger.finalizeLog(logId, FinalDecision(
                            transactionType = newTxnType.name,
                            categoryId = newCategoryId, 
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
        
        Log.d(TAG, "Reclassify Complete. Updated $updatedCount transactions.")
        ClassificationDebugLogger.endBatchSession(context)
        
        // Run self-transfer pairing after reclassify
        pairSelfTransfers(context)
        pairStatementToPayments(context)
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
        val txn = db.transactionDao().getById(transactionId) ?: return 0
        
        // Collect all affected transactions for undo logging
        val affectedTxns = mutableListOf(txn)
        if (applyToSimilar && txn.merchantName != null) {
            affectedTxns.addAll(db.transactionDao().getByMerchantName(txn.merchantName))
        }
        
        // Create undo data before making changes
        val undoData = org.json.JSONObject().apply {
            put("txnIds", org.json.JSONArray(affectedTxns.map { it.id }))
            put("oldCategoryIds", org.json.JSONArray(affectedTxns.map { it.categoryId }))
            put("newCategoryId", categoryId)
        }
        
        // Get category name for description
        val categories = db.categoryDao().getAllEnabledCategories().first()
        val categoryName = categories.find { it.id == categoryId }?.name ?: "Unknown"
        
        // Log undo entry
        val undoLog = com.saikumar.expensetracker.data.entity.UndoLog(
            actionType = com.saikumar.expensetracker.data.entity.UndoLog.ACTION_BATCH_CATEGORIZE,
            undoData = undoData.toString(),
            description = "Changed ${affectedTxns.size} transactions to '$categoryName'",
            affectedCount = affectedTxns.size
        )
        db.undoLogDao().insert(undoLog)
        
        // Now apply the actual updates
        val updated = if (transactionType != null) {
            txn.copy(categoryId = categoryId, transactionType = transactionType, confidenceScore = 100) // User confirmed = 100
        } else {
            txn.copy(categoryId = categoryId, confidenceScore = 100)
        }
        db.transactionDao().updateTransaction(updated)
        
        if (applyToSimilar) {
            // Enhanced matching: Try merchantName first, then smsSnippet pattern
            val allSimilar = mutableListOf<Transaction>()
            
            if (!txn.merchantName.isNullOrBlank()) {
                allSimilar.addAll(db.transactionDao().getByMerchantName(txn.merchantName))
            }
            
            // Also match by UPI ID pattern if present in smsSnippet (for P2P transactions)
            val smsContent = txn.smsSnippet ?: ""
            val upiIdRegex = Regex("([a-zA-Z0-9._-]+@[a-zA-Z]+)")
            val upiMatch = upiIdRegex.find(smsContent)?.value?.lowercase()
            if (upiMatch != null && !upiMatch.contains("gmail") && !upiMatch.contains("yahoo")) {
                val byPattern = db.transactionDao().getTransactionsBySnippetPattern(upiMatch)
                byPattern.forEach { tx ->
                    if (allSimilar.none { it.id == tx.id }) {
                        allSimilar.add(tx)
                    }
                }
            }
            
            if (allSimilar.isNotEmpty()) {
                val similarTxns = allSimilar.map { 
                     if (transactionType != null) {
                        it.copy(categoryId = categoryId, transactionType = transactionType, confidenceScore = 100)
                    } else {
                        it.copy(categoryId = categoryId, confidenceScore = 100)
                    }
                }
                db.transactionDao().updateTransactions(similarTxns)
                Log.d(TAG, "Updated ${similarTxns.size} similar transactions (merchant: ${txn.merchantName}, upi: $upiMatch)")
            }
        }
        
        return affectedTxns.size
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
            
            for (i in 0 until txnIds.length()) {
                val txnId = txnIds.getLong(i)
                val oldCategoryId = oldCategoryIds.getLong(i)
                
                val txn = db.transactionDao().getById(txnId)
                if (txn != null) {
                    db.transactionDao().updateTransaction(
                        txn.copy(categoryId = oldCategoryId)
                    )
                }
            }
            
            // Mark as undone
            db.undoLogDao().markAsUndone(undoLog.id)
            
            Log.d(TAG, "UNDO: Restored ${undoLog.affectedCount} transactions")
            return undoLog.description
            
        } catch (e: Exception) {
            Log.e(TAG, "UNDO failed: ${e.message}")
            return null
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
    
    /**
     * Auto-pair self-transfers: Detect when ₹X leaves Account A and ₹X arrives in Account B on same day.
     * Creates TransactionLink records with LinkType.SELF_TRANSFER.
     */
    suspend fun pairSelfTransfers(context: Context) = withContext(Dispatchers.IO) {
        val app = context.applicationContext as ExpenseTrackerApplication
        val db = app.database
        val linkDao = db.transactionLinkDao()
        
        Log.d(TAG, "=== STARTING SELF-TRANSFER PAIRING ===")
        
        // Get all transactions sorted by date (using new POJO with sender info)
        val allTxns = db.transactionDao().getAllTransactionsSync()
        Log.d(TAG, "Total transactions to analyze: ${allTxns.size}")
        
        // Get all detected user accounts for validation
        val userAccounts = db.userAccountDao().getAllAccounts()
        val myAccountNumbers = userAccounts.map { it.accountNumberLast4 }.toSet()
        Log.d(TAG, "Detected user accounts: ${myAccountNumbers.size} - $myAccountNumbers")
        
        val oneDayMillis = 24 * 60 * 60 * 1000L
        
        // Define types
        val debitTypes = listOf(
            TransactionType.EXPENSE, 
            TransactionType.TRANSFER,
            TransactionType.INVESTMENT_OUTFLOW, 
            TransactionType.INVESTMENT_CONTRIBUTION,
            TransactionType.LIABILITY_PAYMENT
        )
        
        val creditTypes = listOf(
            TransactionType.INCOME, 
            TransactionType.REFUND, 
            TransactionType.CASHBACK,
            TransactionType.TRANSFER // Needed for incoming transfers (e.g. self-transfer or investment payout)
        )
        
        // Find debits (money going out)
        val debits = allTxns.filter { it.transactionType in debitTypes }
        
        // Find credits (money coming in)
        // Note: Sometimes incoming transfers are marked as INCOME or TRANSFER depending on logic
        // We include TRANSFER in credits ONLY if it's not a debit (logic overlap requires care)
        // For simplicity, we assume credits are INCOME/REFUND/CASHBACK for now.
        val credits = allTxns.filter { it.transactionType in creditTypes }
        
        Log.d(TAG, "Debits to match: ${debits.size}, Credits available: ${credits.size}")
        var pairsCreated = 0
        
        for (debit in debits) {
            // Skip if already linked
            if (linkDao.isTransactionLinked(debit.id)) continue
            
            val debitAmount = debit.amountPaisa
            val debitTimestamp = debit.timestamp
            val debitSender = debit.smsSender
            
            for (credit in credits) {
                // Skip if already linked
                if (linkDao.isTransactionLinked(credit.id)) continue
                
                // Skip if same transaction (should never happen as types differ, but safety first)
                if (credit.id == debit.id) continue
                
                // Must be exact same amount
                if (credit.amountPaisa != debitAmount) continue
                
                // Must be within 48 hours (same day or next day)
                val timeDiff = kotlin.math.abs(credit.timestamp - debitTimestamp)
                if (timeDiff > 2 * oneDayMillis) continue
                
                // Must be from different SMS senders (different banks)
                // If sender is null (manual txn), we can't reliably pair by sender diff, so skip or assume compatible
                val creditSender = credit.smsSender
                if (debitSender != null && creditSender != null && debitSender == creditSender) continue
                
                // Calculate confidence score
                var confidence = 0
                
                // Exact amount match: +40
                confidence += 40
                
                // Same day (within 24h): +30, next day: +20
                confidence += if (timeDiff <= oneDayMillis) 30 else 20
                
                // Different account (different SMS sender): +30
                if (debitSender != null && creditSender != null && debitSender != creditSender) {
                    confidence += 30
                }
                
                // Both transactions have DIFFERENT stored account numbers: +25
                val debitAccountNum = debit.accountNumberLast4
                val creditAccountNum = credit.accountNumberLast4
                if (debitAccountNum != null && creditAccountNum != null && debitAccountNum != creditAccountNum) {
                    // Additional validation: both accounts should be known user accounts
                    if (debitAccountNum in myAccountNumbers && creditAccountNum in myAccountNumbers) {
                        confidence += 25
                        Log.d(TAG, "Account match boost: $debitAccountNum → $creditAccountNum (+25)")
                    }
                }
                
                // High confidence (>=80) - create link
                if (confidence >= 80) {
                    try {
                        val link = TransactionLink(
                            primaryTxnId = debit.id,
                            secondaryTxnId = credit.id,
                            linkType = LinkType.SELF_TRANSFER,
                            confidenceScore = confidence,
                            createdBy = LinkSource.AUTO
                        )
                        linkDao.insertLink(link)
                        pairsCreated++
                        Log.d(TAG, "PAIRED: ₹${debitAmount/100.0} | ${debitSender} → ${creditSender} | Acc: $debitAccountNum → $creditAccountNum | Confidence: $confidence%")
                        
                        // Break inner loop to move to next debit
                        break
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to create link: ${e.message}")
                    }
                }
            }
        }
        
        Log.d(TAG, "=== SELF-TRANSFER PAIRING COMPLETE: $pairsCreated pairs created ===")
    }
    
    /**
     * Link credit card STATEMENT notifications with actual bill payments (LIABILITY_PAYMENT).
     * 
     * Matching logic:
     * - Find all STATEMENT transactions
     * - For each, look for LIABILITY_PAYMENT within ±7 days
     * - Match if: same account last4 digits OR amount within 5%
     * - Create TransactionLink with linkType = CC_PAYMENT
     */
    private suspend fun pairStatementToPayments(context: Context) {
        val app = context.applicationContext as ExpenseTrackerApplication
        val db = app.database
        val linkDao = db.transactionLinkDao()
        
        Log.d(TAG, "=== STATEMENT-TO-PAYMENT PAIRING START ===")
        
        // Get all transactions
        val allTxns = db.transactionDao().getAllTransactionsSync()
        
        val statements = allTxns.filter { it.transactionType == TransactionType.STATEMENT }
        val payments = allTxns.filter { it.transactionType == TransactionType.LIABILITY_PAYMENT }
        
        Log.d(TAG, "Statements: ${statements.size}, Payments: ${payments.size}")
        
        val sevenDaysMillis = 7L * 24 * 60 * 60 * 1000
        var pairsCreated = 0
        
        for (statement in statements) {
            // Skip if already linked
            if (linkDao.isTransactionLinked(statement.id)) continue
            
            val statementAmount = statement.amountPaisa
            val statementTime = statement.timestamp
            val statementAccount = statement.accountNumberLast4
            
            for (payment in payments) {
                // Skip if already linked
                if (linkDao.isTransactionLinked(payment.id)) continue
                
                // Skip if same transaction
                if (payment.id == statement.id) continue
                
                // Must be within ±7 days (payment should be around statement date)
                val timeDiff = kotlin.math.abs(payment.timestamp - statementTime)
                if (timeDiff > sevenDaysMillis) continue
                
                // Calculate confidence
                var confidence = 0
                
                // Same account number: +40
                val paymentAccount = payment.accountNumberLast4
                if (statementAccount != null && paymentAccount != null && statementAccount == paymentAccount) {
                    confidence += 40
                }
                
                // Amount within 5%: +30, exact match: +40
                val amountDiff = kotlin.math.abs(payment.amountPaisa - statementAmount).toDouble() / statementAmount.coerceAtLeast(1)
                when {
                    payment.amountPaisa == statementAmount -> confidence += 40
                    amountDiff <= 0.05 -> confidence += 30
                    amountDiff <= 0.10 -> confidence += 20
                    else -> continue // Too different in amount
                }
                
                // Time proximity: same week +20
                val threeDaysMillis = 3L * 24 * 60 * 60 * 1000
                confidence += if (timeDiff <= threeDaysMillis) 20 else 10
                
                // Minimum confidence for CC_PAYMENT link
                if (confidence >= 60) {
                    try {
                        val link = TransactionLink(
                            primaryTxnId = statement.id,
                            secondaryTxnId = payment.id,
                            linkType = LinkType.CC_PAYMENT,
                            confidenceScore = confidence,
                            createdBy = LinkSource.AUTO
                        )
                        linkDao.insertLink(link)
                        pairsCreated++
                        Log.d(TAG, "CC_PAYMENT LINKED: Statement ₹${statementAmount/100.0} → Payment ₹${payment.amountPaisa/100.0} | Acc: $statementAccount | Confidence: $confidence%")
                        
                        // Only one payment per statement
                        break
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to create CC_PAYMENT link: ${e.message}")
                    }
                }
            }
        }
        
        Log.d(TAG, "=== STATEMENT-TO-PAYMENT PAIRING COMPLETE: $pairsCreated pairs created ===")
    }
}
