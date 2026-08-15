package com.saikumar.expensetracker.domain

import android.util.Log
import com.saikumar.expensetracker.data.db.AppDatabase
import com.saikumar.expensetracker.data.entity.BalanceSnapshot
import com.saikumar.expensetracker.data.entity.Transaction
import com.saikumar.expensetracker.data.entity.TransactionType

/**
 * Self-audit of the ledger against bank-reported balances.
 *
 * Most transaction SMS end with the truth: "Avl bal INR 1,54,162.75". Between two such
 * snapshots of the same account, the reported balance change MUST equal the sum of the
 * ledger's transactions on that account. Where it doesn't, something was missed, dropped,
 * or double-counted - the exact class of bug that is otherwise invisible until a manual
 * audit. Capture is done at scan time; the audit runs on demand.
 */
object BalanceAuditor {
    private const val TAG = "BalanceAuditor"

    // "Avl bal INR 1,54,162.75" / "Available Balance is Rs 12,345" / "New bal: INR.3,25,425.00"
    // NOT "Avl Lmt/Limit" - a credit card's remaining limit is not an account balance.
    private val BALANCE_REGEX = Regex(
        """(?:avl|available|new|a/c)\s*bal(?:ance)?\s*(?:is)?\s*[:\-]?\s*(?:rs\.?|inr\.?|₹)\s*([\d,]+(?:\.\d{1,2})?)""",
        RegexOption.IGNORE_CASE
    )

    /** Snapshot from an SMS body, or null when it carries no account balance. */
    fun extractSnapshot(body: String, timestamp: Long, smsHash: String): BalanceSnapshot? {
        val m = BALANCE_REGEX.find(body) ?: return null
        val account = com.saikumar.expensetracker.sms.SmsConstants.extractAccountLast4(body) ?: return null
        val paisa = try {
            java.math.BigDecimal(m.groupValues[1].replace(",", ""))
                .multiply(java.math.BigDecimal(100))
                .setScale(0, java.math.RoundingMode.HALF_UP)
                .longValueExact()
        } catch (e: Exception) {
            return null
        }
        if (paisa <= 0) return null
        return BalanceSnapshot(
            accountLast4 = account,
            balancePaisa = paisa,
            timestamp = timestamp,
            smsHash = smsHash
        )
    }

    data class WindowResult(
        val fromTs: Long,
        val toTs: Long,
        val reportedDeltaPaisa: Long,
        val ledgerDeltaPaisa: Long,
        val txnCount: Int
    ) {
        val gapPaisa: Long get() = reportedDeltaPaisa - ledgerDeltaPaisa
        val consistent: Boolean get() = kotlin.math.abs(gapPaisa) <= 100 // ±₹1 rounding
    }

    data class AccountReport(
        val accountLast4: String,
        val snapshotCount: Int,
        val windowCount: Int,
        val consistentCount: Int,
        val worstGaps: List<WindowResult>
    )

    // Windows longer than this carry too many unknowns (missed SMS, other rails) to judge
    private const val MAX_WINDOW_MS = 45L * 24 * 60 * 60 * 1000

    suspend fun audit(db: AppDatabase): List<AccountReport> {
        val reports = ArrayList<AccountReport>()
        for (account in db.balanceSnapshotDao().getAccounts()) {
            val snaps = db.balanceSnapshotDao().getForAccount(account)
            if (snaps.size < 2) continue

            val windows = ArrayList<WindowResult>()
            for (i in 0 until snaps.size - 1) {
                val a = snaps[i]
                val b = snaps[i + 1]
                if (b.timestamp - a.timestamp > MAX_WINDOW_MS) continue
                if (b.timestamp == a.timestamp) continue

                val txns = db.transactionDao().getActiveByAccountAndPeriod(account, a.timestamp, b.timestamp)
                val ledgerDelta = txns.sumOf { signedAmount(it) }
                windows.add(
                    WindowResult(
                        fromTs = a.timestamp,
                        toTs = b.timestamp,
                        reportedDeltaPaisa = b.balancePaisa - a.balancePaisa,
                        ledgerDeltaPaisa = ledgerDelta,
                        txnCount = txns.size
                    )
                )
            }
            if (windows.isEmpty()) continue

            reports.add(
                AccountReport(
                    accountLast4 = account,
                    snapshotCount = snaps.size,
                    windowCount = windows.size,
                    consistentCount = windows.count { it.consistent },
                    worstGaps = windows.filter { !it.consistent }
                        .sortedByDescending { kotlin.math.abs(it.gapPaisa) }
                        .take(3)
                )
            )
        }
        Log.i(TAG, "Balance audit: ${reports.size} accounts, " +
            "${reports.sumOf { it.consistentCount }}/${reports.sumOf { it.windowCount }} windows consistent")
        return reports.sortedByDescending { it.windowCount }
    }

    /** Effect of one ledger transaction on its account's balance, in paisa. */
    private fun signedAmount(txn: Transaction): Long {
        return when (txn.transactionType) {
            TransactionType.INCOME,
            TransactionType.REFUND,
            TransactionType.CASHBACK,
            TransactionType.PENSION -> txn.amountPaisa

            TransactionType.EXPENSE,
            TransactionType.INVESTMENT_OUTFLOW,
            TransactionType.INVESTMENT_CONTRIBUTION,
            TransactionType.LIABILITY_PAYMENT -> -txn.amountPaisa

            // A TRANSFER row can be either leg - read the wording of its own SMS
            TransactionType.TRANSFER -> {
                val b = (txn.fullSmsBody ?: "").lowercase()
                val debitIdx = listOf("sent", "debited", "deducted", "spent", "withdrawn")
                    .mapNotNull { k -> b.indexOf(k).takeIf { it >= 0 } }.minOrNull() ?: Int.MAX_VALUE
                val creditIdx = listOf("credited", "received", "deposited")
                    .mapNotNull { k -> b.indexOf(k).takeIf { it >= 0 } }.minOrNull() ?: Int.MAX_VALUE
                if (debitIdx <= creditIdx) -txn.amountPaisa else txn.amountPaisa
            }

            // Not cash movements on this account
            else -> 0L
        }
    }
}
