package com.saikumar.expensetracker.domain

import android.util.Log
import com.saikumar.expensetracker.data.db.AppDatabase
import com.saikumar.expensetracker.data.entity.Category

/**
 * Names credit-card bill payments after the CARD being paid — "ICICI Card ••0006",
 * "HDFC Card ••7725" — instead of the payment app ("CRED") or funding bank.
 *
 * There are two SMS per CRED-style bill payment:
 *   1. the debit from the funding account ("Sent Rs.7825 From HDFC A/C To CRED Club") —
 *      names the app, not the card, and the account number in it is the SOURCE account
 *   2. a card-side confirmation ("Payment received on your ICICI Bank Credit Card XX0006",
 *      "...card ending 7725") — this is the one that identifies the actual card
 *
 * So: extract the bank + card-ending from any payment whose own body carries it, then let
 * the app-side debits (which don't) adopt that name by matching amount within a time
 * window. Runs post-scan/reclassify because the two sides are separate rows.
 */
object CreditCardBillNamer {
    private const val TAG = "CreditCardBillNamer"
    private const val PAIR_WINDOW_MS = 5L * 24 * 60 * 60 * 1000

    // Bank right before "Credit Card XXnnnn" or "Credit Card ending nnnn":
    // "ICICI Bank Credit Card XX0006", "SBI Credit Card ending 2983", "Axis Bank Credit Card XX8887"
    private val BANK_CARD_NUM = Regex(
        """([A-Za-z]{2,})\s+(?:Bank\s+)?Credit\s+Card\s+(?:XX|ending\s+(?:with\s+)?)(\d{4})""",
        RegexOption.IGNORE_CASE
    )
    // "...on your ICICI Bank Credit Card Account 4xxx2008" — the number follows an "Account"/
    // "Acct" word and is masked (leading digit + x's) rather than "XX"/"ending".
    private val BANK_CARD_ACCOUNT = Regex(
        """([A-Za-z]{2,})\s+(?:Bank\s+)?Credit\s+Card\s+Acc(?:oun)?t\.?\s+[\dXx*]*(\d{4})""",
        RegexOption.IGNORE_CASE
    )
    // "card ending 7725", "card ending with 3838" (bank named elsewhere)
    private val ENDING = Regex("""card\s+ending\s+(?:with\s+)?(\d{4})""", RegexOption.IGNORE_CASE)
    // Bank for the bare-ending case: "HDFC Bank Cardmember", "HDFCBANK CARDMEMBER"
    private val BANK_CARDMEMBER = Regex("""([A-Za-z]{2,})\s*BANK\s+CARDMEMBER""", RegexOption.IGNORE_CASE)
    // Bank + "Credit Card" with NO number (SBI BBPS: "credited to your SBI Credit Card.")
    private val BANK_CARD_NONUM = Regex("""([A-Za-z]{2,})\s+Credit\s+Card\b""", RegexOption.IGNORE_CASE)

    // Words that grammatically precede "Credit Card" but aren't a bank ("your card ending")
    private val NOT_A_BANK = setOf("YOUR", "THE", "OUR", "VIA", "ON", "TO", "A", "AN", "MY", "BANK", "CREDIT")

    /** "ICICI Card ••0006" / "SBI Card" from a card-side payment body, or null if none. */
    fun deriveCardName(body: String): String? {
        // 1. Bank + card number in one phrase (most complete)
        BANK_CARD_NUM.find(body)?.let { m ->
            val bank = m.groupValues[1].takeIf { it.uppercase() !in NOT_A_BANK }
            if (bank != null) return format(bank, m.groupValues[2])
        }
        // 1b. Bank + masked card number after "Account"/"Acct" ("Credit Card Account 4xxx2008")
        BANK_CARD_ACCOUNT.find(body)?.let { m ->
            val bank = m.groupValues[1].takeIf { it.uppercase() !in NOT_A_BANK }
            if (bank != null) return format(bank, m.groupValues[2])
        }
        // 2. Bare card ending, bank taken from the "X Bank Cardmember" line (HDFC)
        ENDING.find(body)?.let { m ->
            val bank = BANK_CARDMEMBER.find(body)?.groupValues?.get(1)
            return format(bank, m.groupValues[1])
        }
        // 3. Bank named but no card number available (SBI BBPS payment confirmations)
        BANK_CARD_NONUM.find(body)?.let { m ->
            val bank = m.groupValues[1].takeIf { it.uppercase() !in NOT_A_BANK }
            if (bank != null) return "${formatBank(bank)} Card"
        }
        return null
    }

    private fun formatBank(bank: String): String =
        bank.trim().let { if (it.length <= 5) it.uppercase() else it.replaceFirstChar { c -> c.uppercaseChar() } }

    private fun format(bank: String?, last4: String): String {
        val b = bank?.trim()?.takeIf { it.isNotEmpty() }?.let { formatBank(it) }
        return if (b != null) "$b Card ••$last4" else "Card ••$last4"
    }

    private data class CardSide(val timestamp: Long, val amountPaisa: Long, val cardName: String)

    // Same-card transactions within this window are treated as ONE payment event (a card
    // is normally paid once per statement cycle, ~monthly).
    private const val EVENT_WINDOW_MS = 3L * 24 * 60 * 60 * 1000

    /**
     * True if [debitPaisa] (what actually left the funding account) plausibly settles a bill
     * of [confPaisa] (what the card recorded). CRED and other apps apply a cashback/discount,
     * so the debit is USUALLY a little LESS than the bill — e.g. ₹12,588 paid against a
     * ₹12,595 bill. Allow up to a 3% discount below, plus ₹10 of rounding either way.
     */
    private fun settlesBill(debitPaisa: Long, confPaisa: Long): Boolean =
        debitPaisa in (confPaisa * 97 / 100)..(confPaisa + 1000)

    suspend fun relabel(db: AppDatabase, categoryMap: Map<String, Category>): Int {
        val categoryId = categoryMap["Credit Bill Payments"]?.id ?: return 0

        // Naming SOURCES include soft-deleted rows: the de-dup pass hides the card-side
        // confirmation, but it's still the only thing that knows which card a "To CRED"
        // debit paid, so it must remain available as a name source.
        val allForNaming = db.transactionDao().getTransactionsByCategoryIdWithDeleted(categoryId)
        val cardSides = allForNaming.mapNotNull { txn ->
            deriveCardName(txn.fullSmsBody ?: "")?.let { CardSide(txn.timestamp, txn.amountPaisa, it) }
        }

        // Only non-deleted, non-user-confirmed rows are actually (re)named.
        val txns = db.transactionDao().getTransactionsByCategoryId(categoryId)
            .filter { it.confidenceScore < 100 }

        var changed = 0
        val unnamed = ArrayList<com.saikumar.expensetracker.data.entity.Transaction>()

        for (txn in txns) {
            val cardName = deriveCardName(txn.fullSmsBody ?: "")
            if (cardName != null) {
                if (txn.merchantName != cardName) {
                    db.transactionDao().updateTransaction(txn.copy(merchantName = cardName))
                    changed++
                }
            } else {
                unnamed.add(txn)
            }
        }

        // Pass 2: app-side debits ("To CRED Club", BillDesk, etc.) carry no card number in
        // their body, only the funding account. Adopt the card name from the card-side
        // confirmation that this debit settles — the debit is usually a few rupees LESS than
        // the bill (CRED discount/cashback), so match on settlesBill, not a tight ±. Pick the
        // confirmation with the closest amount, then the closest time.
        val matchedToCard = HashSet<Long>()
        for (txn in unnamed) {
            val best = cardSides
                .filter {
                    settlesBill(txn.amountPaisa, it.amountPaisa) &&
                    kotlin.math.abs(it.timestamp - txn.timestamp) <= PAIR_WINDOW_MS
                }
                .minWithOrNull(
                    compareBy(
                        { kotlin.math.abs(it.amountPaisa - txn.amountPaisa) },
                        { kotlin.math.abs(it.timestamp - txn.timestamp) }
                    )
                )
                ?: continue
            matchedToCard.add(txn.id)
            if (txn.merchantName != best.cardName) {
                db.transactionDao().updateTransaction(txn.copy(merchantName = best.cardName))
                changed++
            }
        }

        // Pass 3: an app-side debit that matched no card confirmation still shows the raw
        // payment-app brand ("CRED"/"CRED Club"). We can't know which card it paid, so show a
        // neutral "Credit Card Bill" rather than a third-party app name.
        for (txn in unnamed) {
            if (txn.id in matchedToCard) continue
            val name = txn.merchantName?.trim()?.uppercase()
            if (name == "CRED" || name == "CRED CLUB") {
                db.transactionDao().updateTransaction(txn.copy(merchantName = "Credit Card Bill"))
                changed++
            }
        }

        if (changed > 0) Log.i(TAG, "Named $changed credit-card bill payments by card")
        return changed
    }

    // A funding-account debit made through a payment app ("Sent Rs.X From HDFC A/C To CRED
    // Club"). This is the real cash outflow and the row we keep for an event.
    private fun isAppDebit(body: String): Boolean {
        val b = body.lowercase()
        return b.contains("to cred") || b.contains("cred club") || b.contains("to billdesk")
    }

    /**
     * Collapse one bill-payment EVENT into a single row. A single payment produces several
     * SMS — one funding-account debit ("Sent Rs.12588 To CRED Club", the discounted amount
     * actually paid) plus one OR MORE card-side confirmations ("PAYMENT OF Rs.12595 RECEIVED",
     * and often a second "Online Payment...credited to your card" the next day). After
     * [relabel] they all carry the same card name, so group by card and cluster by time: any
     * same-card rows within [EVENT_WINDOW_MS] are the same payment (a card is paid ~monthly).
     *
     * Within an event keep the funding-account debit — it is the real cash outflow and its
     * amount is what actually left the account — and soft-delete the confirmations. A card
     * paid directly (confirmation only, no CRED debit) keeps its single row.
     */
    suspend fun dedupe(db: AppDatabase, categoryMap: Map<String, Category>): Int {
        val categoryId = categoryMap["Credit Bill Payments"]?.id ?: return 0
        // Only group rows named after a specific card. "Credit Card Bill" is the generic
        // catch-all for unmatchable debits (different cards) — it must not be clustered.
        val named = db.transactionDao().getTransactionsByCategoryId(categoryId)
            .filter {
                it.confidenceScore < 100 &&
                it.merchantName?.contains("Card") == true &&
                it.merchantName != "Credit Card Bill"
            }

        var removed = 0
        val now = System.currentTimeMillis()
        for ((_, group) in named.groupBy { it.merchantName }) {
            // Build events within a card: two rows are the SAME payment only if they are
            // close in time AND amount-compatible (the discounted debit settles the recorded
            // bill). The amount check is what keeps two genuinely-different payments to the
            // same card — e.g. ₹19,916 and ₹39,113 — as separate rows even within the window.
            val remaining = group.sortedBy { it.timestamp }.toMutableList()
            while (remaining.isNotEmpty()) {
                val event = arrayListOf(remaining.removeAt(0))
                var grew = true
                while (grew) {
                    grew = false
                    val iter = remaining.iterator()
                    while (iter.hasNext()) {
                        val r = iter.next()
                        val belongs = event.any { e ->
                            kotlin.math.abs(e.timestamp - r.timestamp) <= EVENT_WINDOW_MS &&
                            settlesBill(minOf(e.amountPaisa, r.amountPaisa), maxOf(e.amountPaisa, r.amountPaisa))
                        }
                        if (belongs) { event.add(r); iter.remove(); grew = true }
                    }
                }
                if (event.size <= 1) continue
                // Keep the funding-account debit (real cash out, the discounted amount). If
                // there is none, keep the lowest — a paid bill is never more than recorded.
                val keep = event.firstOrNull { isAppDebit(it.fullSmsBody ?: "") }
                    ?: event.minByOrNull { it.amountPaisa }!!
                for (t in event) {
                    if (t.id != keep.id) {
                        db.transactionDao().softDelete(t.id, now)
                        removed++
                    }
                }
            }
        }
        if (removed > 0) Log.i(TAG, "Collapsed $removed duplicate card-bill rows into their payment events")
        return removed
    }
}
