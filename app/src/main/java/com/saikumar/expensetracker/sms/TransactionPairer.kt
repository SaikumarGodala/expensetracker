package com.saikumar.expensetracker.sms

import android.content.Context
import android.util.Log
import com.saikumar.expensetracker.ExpenseTrackerApplication
import com.saikumar.expensetracker.data.db.AppDatabase
import com.saikumar.expensetracker.data.entity.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Handles automatic transaction pairing logic.
 *
 * Responsibilities:
 * - Self-transfer detection: Pairs debit/credit transactions between user's own accounts
 * - CC payment linking: Links credit card statements with payment transactions
 */
object TransactionPairer {
    private const val TAG = "TransactionPairer"

    private val DEBIT_TYPES = listOf(
        TransactionType.EXPENSE,
        TransactionType.TRANSFER,
        TransactionType.INVESTMENT_OUTFLOW,
        TransactionType.INVESTMENT_CONTRIBUTION,
        TransactionType.LIABILITY_PAYMENT
    )

    private val CREDIT_TYPES = listOf(
        TransactionType.INCOME,
        TransactionType.REFUND,
        TransactionType.CASHBACK,
        TransactionType.TRANSFER
    )

    /** A self-transfer leg carries no external counterparty: blank or the user's own name. */
    private fun isSelfLeg(merchantName: String?, userAccounts: List<UserAccount>): Boolean =
        merchantName.isNullOrBlank() ||
            com.saikumar.expensetracker.sms.CategoryMapper.isUserOwnName(merchantName, userAccounts)

    /**
     * FINANCIAL ACCURACY: a confirmed self-transfer is money moving between the user's own
     * accounts - neither income nor expense. Every aggregation in the app (dashboard totals,
     * budget SQL, analytics SQL) filters by transactionType and knows nothing about links,
     * so leaving the legs typed EXPENSE/INCOME inflates BOTH sides by the transferred amount.
     *
     * Retype conservatively: only plain EXPENSE/INCOME legs are flipped to TRANSFER.
     * Deliberate classifications (INVESTMENT_*, LIABILITY_PAYMENT, REFUND, CASHBACK) are left
     * alone - if one of those got linked, the link is informational and the type still carries
     * the correct financial meaning.
     */
    private suspend fun retypeSelfTransferLegs(
        db: AppDatabase,
        myAccountNumbers: Set<String>,
        debitId: Long,
        creditId: Long
    ): Int {
        val debit = db.transactionDao().getById(debitId) ?: return 0
        val credit = db.transactionDao().getById(creditId) ?: return 0

        // GUARD: the pairer's confidence score can reach the link threshold on
        // amount + time-window + different-sender alone, which is enough to *display* the
        // pair as a probable self-transfer but NOT enough to rewrite the ledger (a salary
        // credit and an unrelated same-amount payment a day apart would qualify). Only
        // retype when both legs are provably on the user's own, distinct accounts.
        val debitAcct = debit.accountNumberLast4
        val creditAcct = credit.accountNumberLast4
        val accountsVerified = debitAcct != null && creditAcct != null &&
            debitAcct != creditAcct &&
            debitAcct in myAccountNumbers && creditAcct in myAccountNumbers
        if (!accountsVerified) return 0

        var updated = 0
        for (txn in listOf(debit, credit)) {
            if (txn.transactionType == TransactionType.EXPENSE || txn.transactionType == TransactionType.INCOME) {
                db.transactionDao().updateTransaction(
                    txn.copy(transactionType = TransactionType.TRANSFER, isExpenseEligible = false)
                )
                updated++
            }
        }
        return updated
    }

    /**
     * Auto-pair self-transfers: Detect when ₹X leaves Account A and ₹X arrives in Account B on same day.
     * Creates TransactionLink records with LinkType.SELF_TRANSFER.
     */
    suspend fun pairSelfTransfers(context: Context): Int = withContext(Dispatchers.IO) {
        val app = context.applicationContext as ExpenseTrackerApplication
        val db = app.database
        val linkDao = db.transactionLinkDao()

        Log.d(TAG, "=== STARTING SELF-TRANSFER PAIRING ===")

        val allTxns = db.transactionDao().getAllTransactionsSync()
        Log.d(TAG, "Total transactions to analyze: ${allTxns.size}")

        val userAccounts = db.userAccountDao().getAllAccounts()
        val myAccountNumbers = userAccounts.map { it.accountNumberLast4 }.toSet()

        // REPAIR PASS: reclassifyTransactions() re-derives types from the raw SMS and can flip
        // already-linked self-transfer legs back to EXPENSE/INCOME (it has no link awareness).
        // Since the matching loop below skips already-linked transactions, those legs would
        // never be retyped again - so heal them here every run. Idempotent: legs already
        // TRANSFER (or unverified pairs) are untouched.
        try {
            val existingSelfTransferLinks = linkDao.getLinksByType(LinkType.SELF_TRANSFER)
            var healed = 0
            var purged = 0
            for (link in existingSelfTransferLinks) {
                // Purge AUTO links whose legs are NOT verifiably the user's own two accounts -
                // they were created by the old amount+time-only matching and are speculation,
                // not transfers (user-created links are kept).
                if (link.createdBy == LinkSource.AUTO) {
                    val d = db.transactionDao().getById(link.primaryTxnId)
                    val c = db.transactionDao().getById(link.secondaryTxnId)
                    val verified = d?.accountNumberLast4 != null && c?.accountNumberLast4 != null &&
                        d.accountNumberLast4 != c.accountNumberLast4 &&
                        d.accountNumberLast4 in myAccountNumbers && c.accountNumberLast4 in myAccountNumbers &&
                        // no external counterparty on either leg (see pairing gate)
                        isSelfLeg(d.merchantName, userAccounts) && isSelfLeg(c.merchantName, userAccounts)
                    if (!verified) {
                        linkDao.deleteLinkById(link.id)
                        purged++
                        continue
                    }
                }
                healed += retypeSelfTransferLegs(db, myAccountNumbers, link.primaryTxnId, link.secondaryTxnId)
            }
            if (healed > 0) Log.d(TAG, "Repair pass: retyped $healed previously-linked self-transfer legs to TRANSFER")
            if (purged > 0) Log.i(TAG, "Purged $purged unverified auto self-transfer links")
        } catch (e: Exception) {
            Log.e(TAG, "Self-transfer repair pass failed: ${e.message}")
        }
        if (com.saikumar.expensetracker.BuildConfig.DEBUG) {
            val redactedAccounts = myAccountNumbers.map { "****" } // Redact all for safety
            Log.d(TAG, "Detected user accounts: ${myAccountNumbers.size} - $redactedAccounts")
        }

        val oneDayMillis = 24 * 60 * 60 * 1000L

        val debits = allTxns.filter { it.transactionType in DEBIT_TYPES }
        val credits = allTxns.filter { it.transactionType in CREDIT_TYPES }

        Log.d(TAG, "Debits to match: ${debits.size}, Credits available: ${credits.size}")
        var pairsCreated = 0

        // Bulk fetch all currently linked transaction IDs to avoid O(N*M) DB queries
        val linkedTxnIds = linkDao.getAllLinkedTransactionIds().toMutableSet()

        for (debit in debits) {
            if (debit.id in linkedTxnIds) continue

            val debitAmount = debit.amountPaisa
            val debitTimestamp = debit.timestamp
            val debitSender = debit.smsSender

            for (credit in credits) {
                if (credit.id in linkedTxnIds) continue
                if (credit.id == debit.id) continue
                if (credit.amountPaisa != debitAmount) continue

                val timeDiff = kotlin.math.abs(credit.timestamp - debitTimestamp)
                if (timeDiff > 2 * oneDayMillis) continue

                val creditSender = credit.smsSender
                if (debitSender != null && creditSender != null && debitSender == creditSender) continue

                var confidence = 40 // Exact amount match

                confidence += if (timeDiff <= oneDayMillis) 30 else 20

                if (debitSender != null && creditSender != null && debitSender != creditSender) {
                    confidence += 30
                }

                val debitAccountNum = debit.accountNumberLast4
                val creditAccountNum = credit.accountNumberLast4
                val accountsVerified = debitAccountNum != null && creditAccountNum != null &&
                    debitAccountNum != creditAccountNum &&
                    debitAccountNum in myAccountNumbers && creditAccountNum in myAccountNumbers
                if (accountsVerified) {
                    confidence += 25
                    Log.d(TAG, "Account match boost: **** → **** (+25)")
                }

                // HARD REQUIREMENTS: a self-transfer claim needs (a) BOTH legs provably on
                // the user's own distinct accounts AND (b) NO external counterparty on either
                // leg - each leg's merchant must be blank or the user's own name. Account
                // match alone is NOT enough: a ₹20 shop payment from one of my accounts and
                // an unrelated ₹20 credit into another of my accounts both involve "my
                // accounts", but the shop leg names an external payee - not a self transfer.
                val legsAreSelf = accountsVerified &&
                    isSelfLeg(db.transactionDao().getById(debit.id)?.merchantName, userAccounts) &&
                    isSelfLeg(db.transactionDao().getById(credit.id)?.merchantName, userAccounts)
                if (confidence >= 80 && legsAreSelf) {
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
                        // Add to set to prevent re-linking in this same run
                        linkedTxnIds.add(debit.id)
                        linkedTxnIds.add(credit.id)
                        // Retype EXPENSE/INCOME legs to TRANSFER so aggregations stop
                        // counting this internal move as spending + income (only applies
                        // when both accounts are verified as the user's own - see guard)
                        retypeSelfTransferLegs(db, myAccountNumbers, debit.id, credit.id)
                        if (com.saikumar.expensetracker.BuildConfig.DEBUG) {
                            Log.d(TAG, "PAIRED: ₹${debitAmount / 100.0} | ${debitSender} → ${creditSender} | Confidence: $confidence%")
                        }
                        break
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to create link: ${e.message}")
                    }
                }
            }
        }

        Log.d(TAG, "=== SELF-TRANSFER PAIRING COMPLETE: $pairsCreated pairs created ===")
        pairsCreated
    }

    /**
     * Link credit card STATEMENT notifications with actual bill payments (LIABILITY_PAYMENT).
     */
    suspend fun pairStatementToPayments(context: Context): Int = withContext(Dispatchers.IO) {
        val app = context.applicationContext as ExpenseTrackerApplication
        val db = app.database
        val linkDao = db.transactionLinkDao()

        Log.d(TAG, "=== STATEMENT-TO-PAYMENT PAIRING START ===")

        val allTxns = db.transactionDao().getAllTransactionsSync()

        val statements = allTxns.filter { it.transactionType == TransactionType.STATEMENT }
        val payments = allTxns.filter { it.transactionType == TransactionType.LIABILITY_PAYMENT }

        Log.d(TAG, "Statements: ${statements.size}, Payments: ${payments.size}")

        val sevenDaysMillis = 7L * 24 * 60 * 60 * 1000
        var pairsCreated = 0

        // Bulk fetch all currently linked transaction IDs
        val linkedTxnIds = linkDao.getAllLinkedTransactionIds().toMutableSet()

        for (statement in statements) {
            if (statement.id in linkedTxnIds) continue

            val statementAmount = statement.amountPaisa
            val statementTime = statement.timestamp
            val statementAccount = statement.accountNumberLast4

            for (payment in payments) {
                if (payment.id in linkedTxnIds) continue
                if (payment.id == statement.id) continue

                val timeDiff = kotlin.math.abs(payment.timestamp - statementTime)
                if (timeDiff > sevenDaysMillis) continue

                var confidence = 0

                val paymentAccount = payment.accountNumberLast4
                if (statementAccount != null && paymentAccount != null && statementAccount == paymentAccount) {
                    confidence += 40
                }

                val amountDiff = kotlin.math.abs(payment.amountPaisa - statementAmount).toDouble() / statementAmount.coerceAtLeast(1)
                when {
                    payment.amountPaisa == statementAmount -> confidence += 40
                    amountDiff <= 0.05 -> confidence += 30
                    amountDiff <= 0.10 -> confidence += 20
                    else -> continue
                }

                val threeDaysMillis = 3L * 24 * 60 * 60 * 1000
                confidence += if (timeDiff <= threeDaysMillis) 20 else 10

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
                        linkedTxnIds.add(statement.id)
                        linkedTxnIds.add(payment.id)

                        // Surface the reconciliation on the payment: "Cleared statement of
                        // ₹X" (only if the payment doesn't already carry a bill note).
                        try {
                            val full = db.transactionDao().getById(payment.id)
                            if (full != null && full.note?.contains("statement", ignoreCase = true) != true) {
                                val stmtLabel = "Cleared statement of ₹${String.format(java.util.Locale.getDefault(), "%,.0f", statementAmount / 100.0)}"
                                val newNote = if (full.note.isNullOrBlank()) stmtLabel else "${full.note} | $stmtLabel"
                                db.transactionDao().updateTransaction(full.copy(note = newNote))
                            }
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to annotate cleared statement: ${e.message}")
                        }

                        if (com.saikumar.expensetracker.BuildConfig.DEBUG) {
                            Log.d(TAG, "CC_PAYMENT LINKED: Statement ₹${statementAmount / 100.0} → Payment | Confidence: $confidence%")
                        }
                        break
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to create CC_PAYMENT link: ${e.message}")
                    }
                }
            }
        }

        Log.d(TAG, "=== STATEMENT-TO-PAYMENT PAIRING COMPLETE: $pairsCreated pairs created ===")
        pairsCreated
    }

    /**
     * Annotate every CC_PAYMENT-linked payment with "Cleared statement of ₹X" — covers
     * links from earlier runs (the pairing loop skips already-linked statements, so the
     * inline note only fires for brand-new links).
     */
    suspend fun annotateClearedStatements(context: Context): Int = withContext(Dispatchers.IO) {
        val db = (context.applicationContext as ExpenseTrackerApplication).database
        var annotated = 0
        try {
            for (link in db.transactionLinkDao().getLinksByType(LinkType.CC_PAYMENT)) {
                val statement = db.transactionDao().getById(link.primaryTxnId) ?: continue
                val payment = db.transactionDao().getById(link.secondaryTxnId) ?: continue
                if (payment.note?.contains("statement", ignoreCase = true) == true) continue
                val label = "Cleared statement of ₹${String.format(java.util.Locale.getDefault(), "%,.0f", statement.amountPaisa / 100.0)}"
                val newNote = if (payment.note.isNullOrBlank()) label else "${payment.note} | $label"
                db.transactionDao().updateTransaction(payment.copy(note = newNote))
                annotated++
            }
        } catch (e: Exception) {
            Log.w(TAG, "annotateClearedStatements failed: ${e.message}")
        }
        annotated
    }

    // Payment-app/gateway names that pass the "two alphabetic words" person check but are
    // never a lend-and-return counterparty.
    private val NOT_A_PERSON = setOf(
        "GOOGLE PAY", "AMAZON PAY", "PHONEPE", "PAYTM", "AIRTEL PAYMENTS",
        "OFFLINE MERCHANT", "CRED CLUB", "CREDIT CARD BILL"
    )
    // Any of these WORDS inside the name marks a business, not a person ("Sri Jain JEWEL
    // PARK", "FUND Idfc First"). A same-amount credit near a merchant purchase is a
    // reimbursement - the purchase is still real spending and must stay an EXPENSE.
    private val MERCHANT_WORDS = setOf(
        "JEWEL", "JEWELLERS", "JEWELLERY", "PARK", "FUND", "STORE", "STORES", "MART",
        "SHOP", "TRADERS", "ENTERPRISES", "AGENCIES", "AGENCY", "BAZAR", "BAZAAR",
        "ELECTRONICS", "MOBILES", "MEDICALS", "PHARMACY", "HOSPITAL", "CLINIC", "HOTEL",
        "RESTAURANT", "CAFE", "BAKERY", "SWEETS", "TEXTILES", "SILKS", "COLLECTIONS",
        "MOTORS", "AUTOMOBILES", "FUELS", "PETROLEUM", "SERVICES", "SOLUTIONS", "BANK",
        "FINANCE", "LIMITED", "PRIVATE", "CORPORATION", "COMPANY", "CLEARING"
    )
    private val PERSON_NAME_REGEX = Regex("""^[A-Za-z]+(?:\s+[A-Za-z]+)+$""")

    private const val LOAN_WINDOW_MS = 7L * 24 * 60 * 60 * 1000
    private const val LOAN_MIN_PAISA = 100_000L // >= Rs.1,000; small round-trips are too coincidental

    /**
     * Detect lend-and-return round trips with PEOPLE: ₹X sent to a person and the same ₹X
     * coming back within a week (e.g. ₹80,000 to Byra Tarun Teja on 23-Jun, ₹80,000 back
     * on 24-Jun). Without this the loan inflates expenses and its return inflates income.
     *
     * Deliberately strict:
     *  - person-side categories only: debit in Miscellaneous/P2P Transfers (where the
     *    trust-circle logic files person debits), credit in Other Income/P2P Transfers
     *  - the debit's merchant must look like a person's name (2+ alphabetic words)
     *  - exact amount, >= Rs.1,000, within 7 days, 1:1 closest-in-time
     *  - user-confirmed rows (confidenceScore >= 100) are never touched
     *
     * Both legs are retyped TRANSFER and moved to P2P Transfers, mirroring what adding the
     * person to the Transfer Circle would have done for this pair.
     */
    suspend fun pairLoanReturns(context: Context): Int = withContext(Dispatchers.IO) {
        val db = (context.applicationContext as ExpenseTrackerApplication).database
        val linkDao = db.transactionLinkDao()

        val categories = db.categoryDao().getAllCategoriesSync().associateBy { it.name }
        val miscId = categories[com.saikumar.expensetracker.core.AppConstants.Categories.MISCELLANEOUS]?.id
        val p2pId = categories[com.saikumar.expensetracker.core.AppConstants.Categories.P2P_TRANSFERS]?.id
            ?: return@withContext 0
        val otherIncomeId = categories[com.saikumar.expensetracker.core.AppConstants.Categories.OTHER_INCOME]?.id

        fun looksLikePerson(name: String?): Boolean {
            if (name == null || !PERSON_NAME_REGEX.matches(name.trim())) return false
            val upper = name.trim().uppercase()
            if (upper in NOT_A_PERSON) return false
            return upper.split(Regex("\\s+")).none { it in MERCHANT_WORDS }
        }

        // HEAL PASS: reclassify re-derives types/categories from the raw SMS and knows
        // nothing about links - restore previously-paired legs every run (idempotent).
        var healed = 0
        try {
            for (link in linkDao.getLinksByType(LinkType.LOAN_RETURN)) {
                for (id in listOf(link.primaryTxnId, link.secondaryTxnId)) {
                    val t = db.transactionDao().getById(id) ?: continue
                    if (t.transactionType == TransactionType.EXPENSE || t.transactionType == TransactionType.INCOME) {
                        db.transactionDao().updateTransaction(
                            t.copy(transactionType = TransactionType.TRANSFER, isExpenseEligible = false, categoryId = p2pId)
                        )
                        healed++
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Loan-return heal pass failed: ${e.message}")
        }
        if (healed > 0) Log.d(TAG, "Loan-return heal: retyped $healed legs")

        val outCatIds = setOfNotNull(miscId, p2pId)
        val inCatIds = setOfNotNull(otherIncomeId, p2pId)
        val outs = outCatIds.flatMap { db.transactionDao().getTransactionsByCategoryId(it) }
            .filter {
                it.transactionType == TransactionType.EXPENSE &&
                it.confidenceScore < 100 &&
                it.amountPaisa >= LOAN_MIN_PAISA &&
                looksLikePerson(it.merchantName)
            }
        val ins = inCatIds.flatMap { db.transactionDao().getTransactionsByCategoryId(it) }
            .filter {
                (it.transactionType == TransactionType.INCOME || it.transactionType == TransactionType.TRANSFER) &&
                it.confidenceScore < 100 &&
                it.amountPaisa >= LOAN_MIN_PAISA
            }
        if (outs.isEmpty() || ins.isEmpty()) return@withContext 0

        val linkedTxnIds = linkDao.getAllLinkedTransactionIds().toMutableSet()
        val dateFmt = java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault())
        var pairsCreated = 0

        for (out in outs) {
            if (out.id in linkedTxnIds) continue
            val match = ins
                .filter {
                    it.id !in linkedTxnIds && it.id != out.id &&
                    it.amountPaisa == out.amountPaisa &&
                    kotlin.math.abs(it.timestamp - out.timestamp) <= LOAN_WINDOW_MS
                }
                .minByOrNull { kotlin.math.abs(it.timestamp - out.timestamp) }
                ?: continue

            try {
                linkDao.insertLink(
                    TransactionLink(
                        primaryTxnId = out.id,
                        secondaryTxnId = match.id,
                        linkType = LinkType.LOAN_RETURN,
                        confidenceScore = 85,
                        createdBy = LinkSource.AUTO
                    )
                )
                linkedTxnIds.add(out.id)
                linkedTxnIds.add(match.id)

                val outNote = "Returned on ${dateFmt.format(java.util.Date(match.timestamp))}" +
                    (match.merchantName?.let { " by $it" } ?: "")
                val inNote = "Return of amount sent ${dateFmt.format(java.util.Date(out.timestamp))}" +
                    (out.merchantName?.let { " to $it" } ?: "")
                db.transactionDao().updateTransaction(
                    out.copy(
                        transactionType = TransactionType.TRANSFER,
                        isExpenseEligible = false,
                        categoryId = p2pId,
                        note = if (out.note.isNullOrBlank()) outNote else "${out.note} | $outNote"
                    )
                )
                db.transactionDao().updateTransaction(
                    match.copy(
                        transactionType = TransactionType.TRANSFER,
                        isExpenseEligible = false,
                        categoryId = p2pId,
                        note = if (match.note.isNullOrBlank()) inNote else "${match.note} | $inNote"
                    )
                )
                pairsCreated++
                Log.d(TAG, "LOAN_RETURN paired ₹${out.amountPaisa / 100.0}: '${out.merchantName}' -> back on ${dateFmt.format(java.util.Date(match.timestamp))}")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create LOAN_RETURN link: ${e.message}")
            }
        }

        if (pairsCreated > 0) Log.i(TAG, "Loan-return pairing: $pairsCreated round trips linked")
        pairsCreated
    }

    /**
     * Run all pairing algorithms.
     */
    suspend fun runAllPairing(context: Context): PairingResult {
        val selfTransfers = pairSelfTransfers(context)
        val ccPayments = pairStatementToPayments(context)
        annotateClearedStatements(context)
        val loanReturns = pairLoanReturns(context)
        return PairingResult(selfTransfers, ccPayments, loanReturns)
    }

    data class PairingResult(
        val selfTransfersPaired: Int,
        val ccPaymentsPaired: Int,
        val loanReturnsPaired: Int = 0
    ) {
        val totalPaired: Int get() = selfTransfersPaired + ccPaymentsPaired + loanReturnsPaired
    }
}
