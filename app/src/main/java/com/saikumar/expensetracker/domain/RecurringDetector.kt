package com.saikumar.expensetracker.domain

import com.saikumar.expensetracker.data.db.TransactionDao
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * Detects recurring fixed-amount charges — subscriptions and standing bills that hit the
 * same merchant for the same amount, roughly monthly (Netflix ₹199, YouTube ₹299, an EMI,
 * a broadband bill). Used to surface an "active subscriptions" summary and to give these
 * charges a stable, confident categorization instead of letting them drift.
 */
object RecurringDetector {

    data class Recurring(
        val merchant: String,
        val amountPaisa: Long,
        val count: Int,
        val lastTimestamp: Long,
        val categoryName: String
    )

    private const val MIN_OCCURRENCES = 3
    // Consider it "active" if the latest charge was within ~45 days (a monthly cycle + slack)
    private const val ACTIVE_WINDOW_DAYS = 45L

    // Only genuine subscription/standing-bill categories. Without this filter a recurring
    // ₹40,000 transfer to a person (Miscellaneous) or a coincidentally-repeated Paytm/offline
    // amount would show up as a "subscription".
    private val SUBSCRIPTION_CATEGORIES = setOf(
        "Subscriptions", "Mobile + WiFi", "Utilities", "Insurance",
        "Loan EMI", "Entertainment", "Gym & Fitness", "Rent"
    )

    /**
     * Recurring (merchant, amount) groups with [MIN_OCCURRENCES]+ hits whose intervals look
     * roughly monthly. Returns most-recent first.
     */
    suspend fun detect(dao: TransactionDao): List<Recurring> {
        val txns = dao.getTransactionsInPeriod(0L, Long.MAX_VALUE)
            .first()
            .filter {
                it.transaction.transactionType == com.saikumar.expensetracker.data.entity.TransactionType.EXPENSE &&
                !it.transaction.merchantName.isNullOrBlank() &&
                it.category.name in SUBSCRIPTION_CATEGORIES
            }

        return txns
            .groupBy { it.transaction.merchantName!! to it.transaction.amountPaisa }
            .mapNotNull { (key, group) ->
                if (group.size < MIN_OCCURRENCES) return@mapNotNull null
                val times = group.map { it.transaction.timestamp }.sorted()
                if (!looksMonthly(times)) return@mapNotNull null
                Recurring(
                    merchant = key.first,
                    amountPaisa = key.second,
                    count = group.size,
                    lastTimestamp = times.last(),
                    categoryName = group.first().category.name
                )
            }
            .sortedByDescending { it.lastTimestamp }
    }

    /** Active = still charging (latest within the active window). */
    suspend fun detectActive(dao: TransactionDao): List<Recurring> {
        val cutoff = System.currentTimeMillis() - ACTIVE_WINDOW_DAYS * 24 * 60 * 60 * 1000
        return detect(dao).filter { it.lastTimestamp >= cutoff }
    }

    /** Median gap between consecutive charges is within ~20-45 days (monthly-ish). */
    private fun looksMonthly(sortedTimes: List<Long>): Boolean {
        if (sortedTimes.size < MIN_OCCURRENCES) return false
        val gapsDays = sortedTimes.zipWithNext { a, b ->
            ChronoUnit.DAYS.between(
                Instant.ofEpochMilli(a).atZone(ZoneId.systemDefault()).toLocalDate(),
                Instant.ofEpochMilli(b).atZone(ZoneId.systemDefault()).toLocalDate()
            )
        }.sorted()
        if (gapsDays.isEmpty()) return false
        val median = gapsDays[gapsDays.size / 2]
        // 20-45 days covers monthly billing with weekend/holiday drift
        return median in 20..45
    }
}
