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
        "Investment Redemption" to Icons.Default.TrendingDown,
        "Unknown Income" to Icons.Default.HelpOutline,
        
        // Fixed Expenses
        "Housing" to Icons.Default.Home,
        "Utilities" to Icons.Default.Handyman,
        "Insurance" to Icons.Default.HealthAndSafety,
        "Subscriptions" to Icons.Default.Apps,
        "Phone & Internet" to Icons.Default.PhoneAndroid,
        "Car EMI" to Icons.Default.DirectionsCar,
        "Loan EMI" to Icons.Default.AccountBalance,
        "Scheduled / Upcoming Payments" to Icons.Default.Schedule,
        "Credit Bill Payments" to Icons.Default.CreditCard,
        "P2P Transfers" to Icons.Default.SwapHoriz,
        "Grooming" to Icons.Default.Face,
        "Utilities" to Icons.Default.Lightbulb,
        
        // Variable Expenses
        "Groceries" to Icons.Default.ShoppingCart,
        "Dining Out" to Icons.Default.Restaurant,
        "Food Delivery" to Icons.Default.TwoWheeler,
        "SWIGGY" to Icons.Default.TwoWheeler,
        "ZOMATO" to Icons.Default.TwoWheeler,
        "Coffee" to Icons.Default.LocalCafe,
        "Entertainment" to Icons.Default.Movie,
        "Travel & Vacation" to Icons.Default.AirlineSeatIndividualSuite,
        "Travel" to Icons.Default.AirlineSeatIndividualSuite,
        "Cab & Taxi" to Icons.Default.LocalTaxi,
        "Uber" to Icons.Default.LocalTaxi,
        "Ola" to Icons.Default.LocalTaxi,
        "Fuel" to Icons.Default.LocalGasStation,
        "Public Transport" to Icons.Default.DirectionsBus,
        "Gifts" to Icons.Default.CardGiftcard,
        "Shopping" to Icons.Default.ShoppingBag,
        "Electronics" to Icons.Default.Laptop,
        "Books" to Icons.Default.MenuBook,
        "Education" to Icons.Default.School,
        "Medical" to Icons.Default.LocalHospital,
        "Personal Care" to Icons.Default.SelfImprovement,
        "Miscellaneous" to Icons.Default.MoreHoriz,
        "Cash Withdrawal" to Icons.Default.LocalAtm,
        "Wallet Topup" to Icons.Default.AccountBalanceWallet,
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
        "Service" to Icons.Default.Build,
        "Repair" to Icons.Default.BuildCircle,
        "Parking" to Icons.Default.LocalParking,
        "Registration / Toll" to Icons.Default.ReceiptLong,
    )
    
    fun getIcon(categoryName: String): ImageVector {
        return iconMap[categoryName] ?: Icons.Default.Help
    }
}
