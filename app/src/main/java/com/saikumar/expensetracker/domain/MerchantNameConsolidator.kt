package com.saikumar.expensetracker.domain

import android.util.Log
import com.saikumar.expensetracker.data.db.AppDatabase

/**
 * Banks truncate payee names to ~15 characters, so one person/merchant fragments into
 * multiple stored names: "Rangineni Mounika" and "Rangineni Mouni", "Parigi Durgaprasad"
 * and "Parigi Durgapra". This splits them across the category breakdown, fragments
 * merchant-memory (a correction on one doesn't apply to the other), and weakens
 * Transfer-Circle matching.
 *
 * This pass canonicalizes each truncated variant to its longest sibling, so everything
 * downstream (display, grouping, memory keying) sees a single consistent name. Runs
 * post-scan/reclassify because it needs the full set of names to find siblings.
 *
 * Deliberately conservative: only merges when one name is a strict character prefix of a
 * longer one AND the shorter is a multi-word name of at least [MIN_LEN] characters (the
 * bank-truncation zone). That merges "Rangineni Mouni" ⊂ "Rangineni Mounika" but leaves
 * genuinely-different names alone ("Godala Sirisha" vs "Godala Saidulu" share only the
 * surname; "Godala Vikas Re" vs "Godala Vikas Vi" diverge, so neither is merged).
 */
object MerchantNameConsolidator {
    private const val TAG = "MerchantNameConsolidator"
    private const val MIN_LEN = 11

    /** True if [short] looks like a bank-truncated prefix of [long]. */
    fun isTruncationOf(short: String, long: String): Boolean {
        val s = short.trim()
        val l = long.trim()
        if (s.length >= l.length) return false
        if (s.length < MIN_LEN) return false
        if (!s.contains(' ')) return false // multi-word only (people / business names)
        return l.startsWith(s, ignoreCase = true)
    }

    suspend fun consolidate(db: AppDatabase): Int {
        val counts = db.transactionDao().getMerchantNameCounts()
        if (counts.size < 2) return 0

        // Longest first: longer names become canonical, shorter truncations fold into them.
        val names = counts.map { it.name }.sortedByDescending { it.length }

        // variant -> canonical
        val canonicalFor = HashMap<String, String>()
        val canonicals = ArrayList<String>()

        for (name in names) {
            val target = canonicals.firstOrNull { isTruncationOf(name, it) }
            if (target != null) {
                canonicalFor[name] = target
            } else {
                canonicals.add(name)
            }
        }

        var renamed = 0
        for ((variant, canonical) in canonicalFor) {
            try {
                val n = db.transactionDao().renameMerchant(variant, canonical)
                if (n > 0) {
                    renamed += n
                    Log.d(TAG, "Consolidated '$variant' -> '$canonical' ($n txns)")
                }
            } catch (e: Exception) {
                Log.w(TAG, "Rename failed for '$variant': ${e.message}")
            }
        }
        if (renamed > 0) Log.i(TAG, "Consolidated ${canonicalFor.size} truncated names ($renamed txns)")
        return renamed
    }
}
