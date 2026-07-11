package com.saikumar.expensetracker.sms

import android.util.Log
import com.saikumar.expensetracker.data.db.AppDatabase
import com.saikumar.expensetracker.data.entity.BillReminder
import com.saikumar.expensetracker.data.entity.Category
import com.saikumar.expensetracker.data.entity.Transaction
import com.saikumar.expensetracker.data.entity.TransactionType
import java.time.LocalDate
import java.time.ZoneId

/**
 * Captures bill-reminder SMS (which LedgerGate correctly rejects as non-transactions)
 * and links them to the eventual payment by exact amount + time window.
 *
 * Why: a payment SMS says WHO was paid ("AIRTEL", or nothing at all) but not WHAT for.
 * The reminder says what for ("your DTH recharge of Rs.599 is due..."). Linking the two
 * disambiguates cases like Airtel = mobile vs broadband vs DTH, and gives otherwise
 * generic payments (BBPS/gateway debits) a concrete biller and category.
 */
object BillReminderManager {
    private const val TAG = "BillReminderManager"

    /** How far back a reminder may precede its payment and still be linked */
    private const val MATCH_WINDOW_BEFORE_MS = 45L * 24 * 60 * 60 * 1000
    /** Reminders occasionally arrive just after autopay fires */
    private const val MATCH_WINDOW_AFTER_MS = 3L * 24 * 60 * 60 * 1000
    /** Grace period for paying after the stated due date */
    private const val DUE_DATE_GRACE_MS = 7L * 24 * 60 * 60 * 1000

    // Wording in the reminder -> category the payment should land in.
    // Ordered: first match wins, so more specific phrases come before generic ones.
    private val CATEGORY_HINTS = listOf(
        "credit card" to "Credit Bill Payments",
        "airtel black" to "Mobile + WiFi",
        "airtel number" to "Mobile + WiFi",
        "power distribution" to "Utilities",
        "indane" to "Utilities",
        "broadband" to "Mobile + WiFi",
        "fibernet" to "Mobile + WiFi",
        "fiber" to "Mobile + WiFi",
        "wifi" to "Mobile + WiFi",
        "landline" to "Mobile + WiFi",
        "dth" to "Subscriptions",
        "d2h" to "Subscriptions",
        "dish tv" to "Subscriptions",
        "tata play" to "Subscriptions",
        "set top box" to "Subscriptions",
        "electricity" to "Utilities",
        "energy bill" to "Utilities",
        "power bill" to "Utilities",
        "gas bill" to "Utilities",
        "lpg" to "Utilities",
        "water bill" to "Utilities",
        "insurance" to "Insurance",
        "premium" to "Insurance",
        "emi" to "Loan EMI",
        "loan" to "Loan EMI",
        "fastag" to "Parking & Tolls",
        "rent" to "Rent",
        "postpaid" to "Mobile + WiFi",
        "prepaid" to "Mobile + WiFi",
        "mobile bill" to "Mobile + WiFi",
        "recharge" to "Mobile + WiFi"
    )

    // Common biller tokens (uppercase, matched with word boundaries) for a display name
    private val KNOWN_BILLERS = listOf(
        "AIRTEL", "JIO", "VODAFONE", "BSNL", "ACT", "HATHWAY", "EXCITEL",
        "TATA PLAY", "TATASKY", "SUN DIRECT", "DISH TV",
        "BESCOM", "TSSPDCL", "TSNPDCL", "APSPDCL", "TNEB", "MSEDCL", "TATA POWER", "ADANI", "TORRENT",
        "SOUTHERN POWER", "INDANE",
        "LIC", "HDFC ERGO", "STAR HEALTH", "ACKO", "ICICI PRU", "MAX LIFE", "BAJAJ ALLIANZ",
        "HDFC", "ICICI", "SBI", "AXIS", "KOTAK", "IDFC",
        "BAJAJ FINANCE", "BAJAJ FINSERV"
    )

    private val AMOUNT_REGEX = Regex("""(?:rs\.?|inr|₹)\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)

    // "done via Credit Card", "using debit card", "paid by credit card" - instrument, not bill
    private val PAYMENT_METHOD_CLAUSE =
        Regex("""(?:done\s+)?(?:via|using|through|by)\s+(?:credit|debit)\s+card""", RegexOption.IGNORE_CASE)

    // "due by 15-Jul-26", "due on 15 Jul 2026", "before 15/07/2026", "due date 15.07.26"
    private val DUE_DATE_TEXT_REGEX = Regex(
        """(?:due(?:\s+date)?|by|before|on)\s*[:\-]?\s*(\d{1,2})[-/.\s]+(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*[-/.\s]+(\d{2,4})""",
        RegexOption.IGNORE_CASE
    )
    private val DUE_DATE_NUM_REGEX = Regex(
        """(?:due(?:\s+date)?|by|before)\s*[:\-]?\s*(\d{1,2})[-/.](\d{1,2})[-/.](\d{2,4})"""
    )

    private val MONTHS = mapOf(
        "jan" to 1, "feb" to 2, "mar" to 3, "apr" to 4, "may" to 5, "jun" to 6,
        "jul" to 7, "aug" to 8, "sep" to 9, "oct" to 10, "nov" to 11, "dec" to 12
    )

    /**
     * Called when LedgerGate drops a message as FILTER_FUTURE. Extracts what we can and
     * stores it; the unique smsHash index makes repeated scans a no-op.
     */
    suspend fun capture(db: AppDatabase, body: String, receivedAt: Long) {
        try {
            val amountPaisa = extractAmountPaisa(body) ?: return
            if (amountPaisa <= 0) return

            val upperBody = body.uppercase()
            val billerName = KNOWN_BILLERS
                .filter { SmsConstants.containsToken(upperBody, it) }
                .maxByOrNull { it.length }
            // "Payment done via Credit Card for Electricity" - "credit card" is the payment
            // INSTRUMENT, not the bill. Strip method clauses so the purpose ("electricity",
            // "lpg") wins; a real card-bill reminder ("due on HDFC Bank Credit Card 3838")
            // has no via/using phrasing and still hits the "credit card" hint.
            val lowerBody = body.lowercase()
                .replace(PAYMENT_METHOD_CLAUSE, " ")
            val categoryHint = CATEGORY_HINTS.firstOrNull { lowerBody.contains(it.first) }?.second

            // Without at least a biller or a category hint the reminder can't tell us
            // anything a bare amount match wouldn't - skip to avoid junk rows.
            if (billerName == null && categoryHint == null) return

            db.billReminderDao().insert(
                BillReminder(
                    smsHash = DuplicateChecker.generateHash(body),
                    billerName = billerName,
                    amountPaisa = amountPaisa,
                    dueDateMillis = parseDueDate(body, receivedAt),
                    categoryHint = categoryHint,
                    smsBody = body,
                    receivedAt = receivedAt
                )
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to capture bill reminder: ${e.message}")
        }
    }

    /**
     * Called for messages from EXCLUDED senders (billers/gateways like Airtel Thanks or
     * Airtel Payments Bank). Their payment CONFIRMATIONS ("we have received a payment of
     * Rs.2361.18 for your Airtel Black ID", "Southern Power ... payment ... is successful")
     * are rightly never inserted as transactions — the bank debit is the real row — but they
     * are the ONLY SMS that says WHAT the debit paid for. Store them like reminders so
     * reconcile() links them to the debit by amount + time and applies biller/category.
     *
     * Guarded to confirmation phrasing so promo spam from the same senders (which also
     * contains amounts and biller names) can't create junk link candidates.
     */
    suspend fun captureConfirmation(db: AppDatabase, body: String, receivedAt: Long) {
        val lower = body.lowercase()
        if (!lower.contains("payment")) return
        val confirmed = lower.contains("successful") ||
            lower.contains("has been processed") ||
            lower.contains("received a payment") ||
            lower.contains("received the payment")
        if (!confirmed) return
        // Not a promise of future MONEY movement. (A bare "will be" is too blunt: Airtel's
        // confirmations end with "receipt will be available to download for 7 days".)
        val futureMoney = listOf(
            "will be debited", "will be deducted", "will be charged", "will be processed",
            "due by", "is due"
        )
        if (futureMoney.any { lower.contains(it) }) return
        capture(db, body, receivedAt)
    }

    /**
     * Find the best unmatched reminder for a payment of [amountPaisa] at [paidAt].
     * Preference: closest due date, else most recently received.
     */
    suspend fun findMatch(db: AppDatabase, amountPaisa: Long, paidAt: Long): BillReminder? {
        val candidates = db.billReminderDao()
            .findCandidates(amountPaisa, paidAt - MATCH_WINDOW_BEFORE_MS, paidAt + MATCH_WINDOW_AFTER_MS)
            .filter { it.dueDateMillis == null || paidAt <= it.dueDateMillis + DUE_DATE_GRACE_MS }
        return candidates.minByOrNull {
            if (it.dueDateMillis != null) kotlin.math.abs(it.dueDateMillis - paidAt)
            else paidAt - it.receivedAt
        }
    }

    /**
     * Apply a matched reminder to a transaction (pure - returns the adjusted copy).
     *
     * Category override rules are deliberately conservative:
     * - Same biller visible in the payment itself -> trust the hint fully (this is the
     *   "Airtel 599 is actually DTH" case).
     * - Payment is category-generic (Uncategorized/Miscellaneous/Offline Merchant) ->
     *   the hint is strictly better than nothing.
     * - Otherwise leave the category alone and only annotate, so an amount coincidence
     *   (a 599 Swiggy order vs a 599 Airtel reminder) can't corrupt a good category.
     */
    fun applyToTransaction(
        txn: Transaction,
        reminder: BillReminder,
        categoryMap: Map<String, Category>
    ): Transaction {
        val haystack = ((txn.merchantName ?: "") + " " + (txn.fullSmsBody ?: "")).uppercase()
        val sameBiller = reminder.billerName != null &&
            SmsConstants.containsToken(haystack, reminder.billerName)
        val isGenericCategory = categoryMap.entries
            .find { it.value.id == txn.categoryId }?.key in setOf(
                "Uncategorized", "Miscellaneous", "Offline Merchant", "Unknown Expense"
            )

        val hintedCategoryId = reminder.categoryHint?.let { categoryMap[it]?.id }
        val newCategoryId = if (hintedCategoryId != null && (sameBiller || isGenericCategory)) {
            hintedCategoryId
        } else txn.categoryId

        val dueText = reminder.dueDateMillis?.let {
            java.time.Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                .format(java.time.format.DateTimeFormatter.ofPattern("d MMM"))
        }
        val billNote = buildString {
            append("Bill: ")
            append(reminder.billerName ?: reminder.categoryHint ?: "reminder")
            if (reminder.categoryHint != null && reminder.billerName != null) {
                append(" (").append(reminder.categoryHint).append(")")
            }
            if (dueText != null) append(", due ").append(dueText)
        }
        val newNote = when {
            txn.note.isNullOrBlank() -> billNote
            txn.note.contains("Bill: ") -> txn.note
            else -> txn.note + " | " + billNote
        }

        return txn.copy(
            categoryId = newCategoryId,
            note = newNote,
            merchantName = txn.merchantName ?: reminder.billerName?.lowercase()
                ?.replaceFirstChar { c -> c.uppercaseChar() }
        )
    }

    /**
     * Post-scan reconciliation. Needed because a full inbox scan processes newest-first:
     * the payment gets inserted BEFORE its (older) reminder is captured, so insert-time
     * matching misses. Walk unmatched reminders and link them to already-inserted
     * payments. Skips user-confirmed transactions (confidenceScore >= 100).
     */
    suspend fun reconcile(db: AppDatabase, categoryMap: Map<String, Category>): Int {
        var linked = 0
        try {
            for (reminder in db.billReminderDao().getUnmatched()) {
                val windowEnd = (reminder.dueDateMillis ?: (reminder.receivedAt + MATCH_WINDOW_BEFORE_MS)) + DUE_DATE_GRACE_MS
                val candidates = db.transactionDao()
                    .findPotentialDuplicates(reminder.amountPaisa, reminder.receivedAt - MATCH_WINDOW_AFTER_MS, windowEnd)
                    .filter {
                        (it.transactionType == TransactionType.EXPENSE ||
                         it.transactionType == TransactionType.LIABILITY_PAYMENT) &&
                        it.confidenceScore < 100 &&
                        it.note?.contains("Bill: ") != true
                    }
                // Closest in time wins. Confirmations arrive minutes after their debit;
                // reminders precede their payment by days - abs() handles both.
                val txn = candidates.minByOrNull { kotlin.math.abs(it.timestamp - reminder.receivedAt) } ?: continue

                val updated = applyToTransaction(txn, reminder, categoryMap)
                if (updated != txn) {
                    db.transactionDao().updateTransaction(updated)
                }
                db.billReminderDao().markMatched(reminder.id, txn.id)
                linked++
            }
            // Reminders that never met a payment within ~3 months are dead weight
            db.billReminderDao().purgeStale(System.currentTimeMillis() - 90L * 24 * 60 * 60 * 1000)
        } catch (e: Exception) {
            Log.w(TAG, "Bill reminder reconcile failed: ${e.message}")
        }
        if (linked > 0) Log.i(TAG, "Linked $linked payments to bill reminders")
        return linked
    }

    /**
     * Re-apply the category recorded in a "Bill: …" note to any payment whose category has
     * since drifted. Needed because reclassify re-derives the category from the raw SMS, which
     * can be wrong in two ways: it reverts a signal-less debit ("INR 21,825 debited from HDFC")
     * to Uncategorized, OR - worse - it lands on a plausible-but-wrong bucket (an "AIRTEL PAYM"
     * electricity swipe categorized Mobile + WiFi by the Airtel merchant map, when the linked
     * confirmation proved it was Utilities). The linked reminder is authoritative for WHAT the
     * bill was, so restore it over any auto-assigned category. User-confirmed rows
     * (confidenceScore >= 100) are never touched.
     */
    suspend fun reapplyBillCategories(db: AppDatabase, categoryMap: Map<String, Category>): Int {
        var fixed = 0
        try {
            for (txn in db.transactionDao().getByNotePattern("Bill: ")) {
                if (txn.confidenceScore >= 100) continue // respect a manual categorization
                val note = txn.note ?: continue
                val after = note.substringAfter("Bill: ", "")
                // Category is inside parens ("HDFC (Credit Bill Payments)…") when a biller
                // was named, otherwise it's the leading text ("Loan EMI").
                val catName = if (after.contains("(")) {
                    after.substringAfter("(").substringBefore(")").trim()
                } else {
                    after.substringBefore(", due").trim()
                }
                val catId = categoryMap[catName]?.id ?: continue
                if (catId != txn.categoryId) {
                    db.transactionDao().updateTransaction(txn.copy(categoryId = catId))
                    fixed++
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "reapplyBillCategories failed: ${e.message}")
        }
        if (fixed > 0) Log.i(TAG, "Re-applied bill category to $fixed payments")
        return fixed
    }

    private fun extractAmountPaisa(body: String): Long? {
        val raw = AMOUNT_REGEX.find(body)?.groupValues?.get(1) ?: return null
        return try {
            java.math.BigDecimal(raw.replace(",", ""))
                .multiply(java.math.BigDecimal(100))
                .setScale(0, java.math.RoundingMode.HALF_UP)
                .longValueExact()
        } catch (e: Exception) {
            null
        }
    }

    private fun parseDueDate(body: String, receivedAt: Long): Long? {
        try {
            DUE_DATE_TEXT_REGEX.find(body)?.let { m ->
                val day = m.groupValues[1].toInt()
                val month = MONTHS[m.groupValues[2].lowercase().take(3)] ?: return@let
                val year = normalizeYear(m.groupValues[3].toInt())
                return toMillis(year, month, day)
            }
            DUE_DATE_NUM_REGEX.find(body)?.let { m ->
                val day = m.groupValues[1].toInt()
                val month = m.groupValues[2].toInt()
                val year = normalizeYear(m.groupValues[3].toInt())
                if (month in 1..12) return toMillis(year, month, day)
            }
        } catch (e: Exception) {
            Log.d(TAG, "Due date parse failed: ${e.message}")
        }
        return null
    }

    private fun normalizeYear(y: Int): Int = if (y < 100) 2000 + y else y

    private fun toMillis(year: Int, month: Int, day: Int): Long? = try {
        LocalDate.of(year, month, day).atTime(23, 59)
            .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    } catch (e: Exception) {
        null
    }
}
