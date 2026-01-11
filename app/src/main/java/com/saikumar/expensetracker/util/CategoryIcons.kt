package com.saikumar.expensetracker.util

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

object CategoryIcons {
    private val iconMap = mapOf(
        // Income
        "Salary" to Icons.Default.AttachMoney,
        "Freelance / Other" to Icons.Default.Work,
        "Investments / Dividends" to Icons.Default.TrendingUp,
        "Bonus" to Icons.Default.CardGiftcard,
        "Refund" to Icons.Default.Undo,
        "Cashback" to Icons.Default.Payments,
        "Interest" to Icons.Default.Percent,
        "Other Income" to Icons.Default.MoreHoriz,
        "Unknown Income" to Icons.Default.HelpOutline,
        
        // Fixed Expenses
        "Home Rent / EMI" to Icons.Default.Home,
        "Home Expenses" to Icons.Default.Handyman,
        "Insurance (Life + Health + Term)" to Icons.Default.HealthAndSafety,
        "Subscriptions" to Icons.Default.Apps,
        "Mobile + WiFi" to Icons.Default.PhoneAndroid,
        "Car EMI" to Icons.Default.DirectionsCar,
        "Utilities" to Icons.Default.Lightbulb,
        
        // Variable Expenses
        "Groceries" to Icons.Default.ShoppingCart,
        "Food Outside" to Icons.Default.Restaurant,
        "Coffee" to Icons.Default.LocalCafe,
        "Entertainment" to Icons.Default.Movie,
        "Travel" to Icons.Default.AirlineSeatIndividualSuite,
        "Fuel" to Icons.Default.LocalGasStation,
        "Public Transport" to Icons.Default.DirectionsBus,
        "Gifts" to Icons.Default.CardGiftcard,
        "Apparel / Shopping" to Icons.Default.ShoppingBag,
        "Electronics" to Icons.Default.Laptop,
        "Books" to Icons.Default.MenuBook,
        "Medical" to Icons.Default.LocalHospital,
        "Personal Care" to Icons.Default.SelfImprovement,
        "Miscellaneous" to Icons.Default.MoreHoriz,
        "Unknown Expense" to Icons.Default.Help,
        
        // Investments
        "Mutual Funds" to Icons.Default.TrendingUp,
        "Stocks" to Icons.Default.BarChart,
        "Gold / Silver" to Icons.Default.Diamond,
        "Fixed Deposits" to Icons.Default.AccountBalance,
        "Cryptocurrency" to Icons.Default.CurrencyBitcoin,
        "Bonds" to Icons.Default.Assessment,
        "Recurring Deposits" to Icons.Default.SaveAlt,
        "Additional Lump-sum" to Icons.Default.Money,
        
        // Vehicle
        "Service" to Icons.Default.DirectionsCar,
        "Repair" to Icons.Default.BuildCircle,
        "Parking" to Icons.Default.LocalParking,
        "Registration / Toll" to Icons.Default.ReceiptLong,
    )
    
    fun getIcon(categoryName: String): ImageVector {
        return iconMap[categoryName] ?: Icons.Default.Category
    }
}
