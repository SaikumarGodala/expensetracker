package com.saikumar.expensetracker.util

import java.util.Locale

/**
 * Single source of truth for paisa -> display-string formatting.
 * Previously four screens each had their own private `formatAmount` copy
 * (with subtly different decimal behavior).
 */
object CurrencyFormatter {
    fun formatPaisa(paisa: Long, showDecimals: Boolean = false): String {
        val rupees = paisa / 100.0
        val pattern = if (showDecimals) "%,.2f" else "%,.0f"
        return "₹${String.format(Locale.getDefault(), pattern, rupees)}"
    }
}
