package com.saikumar.expensetracker.util

import androidx.compose.ui.graphics.Color

/**
 * Stable visual color identity per category.
 *
 * Categories are the primary thing users scan a transaction list for, so each category
 * gets a distinct, consistent hue used on the row's icon avatar and category chip.
 * Colors are mid-saturation Material tones chosen to read well both as a solid tint
 * (icon/chip text) and as a translucent container (alpha ~0.14) in light AND dark themes.
 *
 * Known default categories get curated colors grouped by domain (food = warm hues,
 * transport = blues, investments = teals...). User-created categories fall back to a
 * deterministic pick from a 14-color palette based on the category name, so the same
 * category always renders the same color across sessions and screens.
 */
object CategoryColors {

    private val curated = mapOf(
        // Income
        "Salary" to Color(0xFF43A047),           // Green
        "Freelance / Other" to Color(0xFF00897B),
        "Interest" to Color(0xFF00ACC1),
        "Dividend" to Color(0xFF7CB342),
        "Rental Income" to Color(0xFF558B2F),
        "Bonus" to Color(0xFF9CCC65),
        "Other Income" to Color(0xFF66BB6A),
        "Investment Redemption" to Color(0xFF26A69A),
        "Refund" to Color(0xFF26A69A),
        "Cashback" to Color(0xFFF9A825),         // Gold
        "Gifts" to Color(0xFFEC407A),
        "Business Income" to Color(0xFF2E7D32),
        "Unverified Income" to Color(0xFFEF6C00), // Attention orange

        // Fixed expenses
        "Rent" to Color(0xFF5C6BC0),             // Indigo
        "Housing" to Color(0xFF7986CB),
        "Utilities" to Color(0xFFF57F17),        // Deep yellow
        "Insurance" to Color(0xFF00838F),
        "Subscriptions" to Color(0xFF7E57C2),    // Purple
        "Mobile + WiFi" to Color(0xFF0288D1),    // Light blue
        "Loan EMI" to Color(0xFF8D6E63),         // Brown
        "Education / Fees" to Color(0xFF3949AB),
        "Home Maintenance" to Color(0xFF6D4C41),
        "Domestic Help" to Color(0xFF78909C),

        // Variable expenses
        "Groceries" to Color(0xFF2E7D32),        // Deep green
        "Dining Out" to Color(0xFFE64A19),       // Deep orange
        "Food Delivery" to Color(0xFFEF6C00),    // Orange
        "Shopping" to Color(0xFFD81B60),         // Pink
        "Entertainment" to Color(0xFF8E24AA),    // Purple
        "Travel" to Color(0xFF00ACC1),           // Cyan
        "Transportation" to Color(0xFF1976D2),   // Blue
        "Cab & Taxi" to Color(0xFFFBC02D),       // Yellow
        "Medical" to Color(0xFFE53935),          // Red
        "Clothing" to Color(0xFFBA68C8),
        "Furniture" to Color(0xFF795548),
        "Electronics" to Color(0xFF546E7A),      // Blue grey
        "Personal Care" to Color(0xFFF06292),
        "Gym & Fitness" to Color(0xFF7CB342),    // Light green
        "Gifts & Donations" to Color(0xFFEC407A),
        "Books & Learning" to Color(0xFF5E35B1),
        "Pet Care" to Color(0xFFFF8F00),
        "Services" to Color(0xFF00897B),
        "Offline Merchant" to Color(0xFF6D4C41),
        "Unknown Expense" to Color(0xFF9E9E9E),
        "Cash Withdrawal" to Color(0xFF455A64),
        "Miscellaneous" to Color(0xFF757575),    // Neutral grey
        "Uncategorized" to Color(0xFF9E9E9E),

        // Investments
        "Mutual Funds" to Color(0xFF00695C),     // Teal
        "Stocks" to Color(0xFF1565C0),
        "Gold" to Color(0xFFF9A825),
        "Recurring Deposits" to Color(0xFF00838F),
        "Fixed Deposits" to Color(0xFF283593),
        "Provident Fund" to Color(0xFF4527A0),
        "PPF / EPF" to Color(0xFF4527A0),
        "NPS" to Color(0xFF6A1B9A),
        "Crypto" to Color(0xFFF57C00),
        "Chits" to Color(0xFF5D4037),

        // Vehicle
        "Fuel" to Color(0xFFFB8C00),
        "Vehicle Maintenance" to Color(0xFF6D4C41),
        "Parking & Tolls" to Color(0xFF607D8B),

        // Liability / transfer / system
        "Credit Bill Payments" to Color(0xFF6A1B9A), // Deep purple
        "Loan Repayment" to Color(0xFF4E342E),
        "P2P Transfers" to Color(0xFF5C6BC0),
        "Self Transfer" to Color(0xFF78909C),        // Neutral blue-grey
        "Credit Card Statement" to Color(0xFF455A64),
        "Spam" to Color(0xFF9E9E9E),
        "Failed/Declined" to Color(0xFF9E9E9E)
    )

    // Fallback palette for user-created categories - distinct hues, stable via name hash
    private val fallbackPalette = listOf(
        Color(0xFFE53935), // Red
        Color(0xFFD81B60), // Pink
        Color(0xFF8E24AA), // Purple
        Color(0xFF5E35B1), // Deep purple
        Color(0xFF3949AB), // Indigo
        Color(0xFF1E88E5), // Blue
        Color(0xFF00ACC1), // Cyan
        Color(0xFF00897B), // Teal
        Color(0xFF43A047), // Green
        Color(0xFF7CB342), // Light green
        Color(0xFFF9A825), // Amber
        Color(0xFFFB8C00), // Orange
        Color(0xFFE64A19), // Deep orange
        Color(0xFF6D4C41)  // Brown
    )

    fun getColor(categoryName: String): Color {
        curated[categoryName]?.let { return it }
        val index = Math.floorMod(categoryName.hashCode(), fallbackPalette.size)
        return fallbackPalette[index]
    }
}
