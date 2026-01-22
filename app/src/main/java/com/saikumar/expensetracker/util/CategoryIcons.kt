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
        "Dividend" to Icons.Default.Paid,
        "Rental Income" to Icons.Default.HomeWork,
        "Other Income" to Icons.Default.MoreHoriz,
        "Investment Redemption" to Icons.Default.TrendingDown,
        "Unverified Income" to Icons.Default.Warning,
        "Unknown Income" to Icons.Default.HelpOutline,
        
        // Fixed Expenses
        "Rent" to Icons.Default.House,
        "Housing" to Icons.Default.Home,
        "Utilities" to Icons.Default.Lightbulb,
        "Insurance" to Icons.Default.HealthAndSafety,
        "Subscriptions" to Icons.Default.Apps,
        "Phone & Internet" to Icons.Default.PhoneAndroid,
        "Mobile + WiFi" to Icons.Default.Wifi,
        "Car EMI" to Icons.Default.DirectionsCar,
        "Loan EMI" to Icons.Default.AccountBalance,
        "Education / Fees" to Icons.Default.School,
        "Scheduled / Upcoming Payments" to Icons.Default.Schedule,
        "Credit Bill Payments" to Icons.Default.CreditCard,
        
        // Variable Expenses
        "Groceries" to Icons.Default.ShoppingCart,
        "Dining Out" to Icons.Default.Restaurant,
        "Food Delivery" to Icons.Default.TwoWheeler,
        "SWIGGY" to Icons.Default.TwoWheeler,
        "ZOMATO" to Icons.Default.TwoWheeler,
        "Coffee" to Icons.Default.LocalCafe,
        "Entertainment" to Icons.Default.Movie,
        "Transportation" to Icons.Default.DirectionsBus,
        "Travel & Vacation" to Icons.Default.Flight,
        "Travel" to Icons.Default.Flight,
        "Cab & Taxi" to Icons.Default.LocalTaxi,
        "Uber" to Icons.Default.LocalTaxi,
        "Ola" to Icons.Default.LocalTaxi,
        "Public Transport" to Icons.Default.DirectionsBus,
        "Medical" to Icons.Default.LocalHospital,
        "Shopping" to Icons.Default.ShoppingBag,
        "Clothing" to Icons.Default.Checkroom,
        "Furniture" to Icons.Default.Chair,
        "Electronics" to Icons.Default.Laptop,
        "Personal Care" to Icons.Default.Face,
        "Gym & Fitness" to Icons.Default.FitnessCenter,
        "Grooming" to Icons.Default.Face,
        "Gifts" to Icons.Default.CardGiftcard,
        "Gifts & Donations" to Icons.Default.VolunteerActivism,
        "Books" to Icons.Default.MenuBook,
        "Books & Learning" to Icons.Default.MenuBook,
        "Education" to Icons.Default.School,
        "Pet Care" to Icons.Default.Pets,
        "ATM Withdrawal" to Icons.Default.LocalAtm,
        "Cash Withdrawal" to Icons.Default.LocalAtm,
        "Offline Merchant" to Icons.Default.Store,
        "Miscellaneous" to Icons.Default.MoreHoriz,
        "Wallet Topup" to Icons.Default.AccountBalanceWallet,
        "Unknown Expense" to Icons.Default.Help,
        "Uncategorized" to Icons.Default.Help,
        "P2P Transfers" to Icons.Default.SwapHoriz,
        "Self Transfer" to Icons.Default.SwapHoriz,
        
        // Investments
        "Mutual Funds" to Icons.Default.TrendingUp,
        "Stocks" to Icons.Default.BarChart,
        "Fixed Deposits" to Icons.Default.Lock,
        "Recurring Deposits" to Icons.Default.SaveAlt,
        "PPF / EPF" to Icons.Default.Savings,
        "Gold / Silver" to Icons.Default.Diamond,
        "Gold" to Icons.Default.Diamond,
        "Chits" to Icons.Default.Groups,
        "Cryptocurrency" to Icons.Default.CurrencyBitcoin,
        "Bonds" to Icons.Default.Assessment,
        "Additional Lump-sum" to Icons.Default.Money,
        
        // Vehicle
        "Fuel" to Icons.Default.LocalGasStation,
        "Service" to Icons.Default.Build,
        "Repair" to Icons.Default.BuildCircle,
        "Parking" to Icons.Default.LocalParking,
        "Parking & Tolls" to Icons.Default.LocalParking,
        "Registration / Toll" to Icons.Default.ReceiptLong,
    )
    
    fun getIcon(categoryName: String): ImageVector {
        return iconMap[categoryName] ?: Icons.Default.Help
    }
}
