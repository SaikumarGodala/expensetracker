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
                if (debitAccountNum != null && creditAccountNum != null && debitAccountNum != creditAccountNum) {
                    if (debitAccountNum in myAccountNumbers && creditAccountNum in myAccountNumbers) {
                        confidence += 25
                        // Redacted log
                        Log.d(TAG, "Account match boost: **** → **** (+25)")
                    }
                }

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
                        // Add to set to prevent re-linking in this same run
                        linkedTxnIds.add(debit.id)
                        linkedTxnIds.add(credit.id)
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
     * Run all pairing algorithms.
     */
    suspend fun runAllPairing(context: Context): PairingResult {
        val selfTransfers = pairSelfTransfers(context)
        val ccPayments = pairStatementToPayments(context)
        return PairingResult(selfTransfers, ccPayments)
    }

    data class PairingResult(
        val selfTransfersPaired: Int,
        val ccPaymentsPaired: Int
    ) {
        val totalPaired: Int get() = selfTransfersPaired + ccPaymentsPaired
    }
}
