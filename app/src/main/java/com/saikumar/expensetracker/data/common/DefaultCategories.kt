package com.saikumar.expensetracker.data.common

import com.saikumar.expensetracker.data.entity.CategoryType
import com.saikumar.expensetracker.core.AppConstants

data class DefaultCategoryDef(
    val name: String,
    val type: CategoryType,
    val isDefault: Boolean = true
)

object DefaultCategories {
    val ALL_CATEGORIES = listOf(
        // Income
        DefaultCategoryDef(AppConstants.Categories.SALARY, CategoryType.INCOME),
        DefaultCategoryDef("Freelance / Other", CategoryType.INCOME),
        DefaultCategoryDef("Refund", CategoryType.INCOME),
        DefaultCategoryDef("Interest", CategoryType.INCOME),
        DefaultCategoryDef("Dividend", CategoryType.INCOME),
        DefaultCategoryDef("Rental Income", CategoryType.INCOME),
        DefaultCategoryDef("Bonus", CategoryType.INCOME),
        DefaultCategoryDef(AppConstants.Categories.OTHER_INCOME, CategoryType.INCOME),
        DefaultCategoryDef("Investment Redemption", CategoryType.INCOME),
        DefaultCategoryDef(AppConstants.Categories.CASHBACK, CategoryType.INCOME),
        DefaultCategoryDef("Gifts", CategoryType.INCOME),
        DefaultCategoryDef("Business Income", CategoryType.INCOME),
        DefaultCategoryDef("Unverified Income", CategoryType.INCOME),

        // Fixed Expenses (Needs)
        DefaultCategoryDef("Rent", CategoryType.FIXED_EXPENSE),
        DefaultCategoryDef("Housing", CategoryType.FIXED_EXPENSE),
        DefaultCategoryDef("Utilities", CategoryType.FIXED_EXPENSE),
        DefaultCategoryDef("Insurance", CategoryType.FIXED_EXPENSE),
        DefaultCategoryDef("Subscriptions", CategoryType.FIXED_EXPENSE),
        DefaultCategoryDef("Mobile + WiFi", CategoryType.FIXED_EXPENSE),
        DefaultCategoryDef("Loan EMI", CategoryType.FIXED_EXPENSE),
        DefaultCategoryDef("Education / Fees", CategoryType.FIXED_EXPENSE),
        DefaultCategoryDef("Home Maintenance", CategoryType.FIXED_EXPENSE),
        DefaultCategoryDef("Domestic Help", CategoryType.FIXED_EXPENSE),
        
        // Variable Expenses (Lifestyle)
        DefaultCategoryDef("Groceries", CategoryType.VARIABLE_EXPENSE),
        DefaultCategoryDef("Dining Out", CategoryType.VARIABLE_EXPENSE),
        DefaultCategoryDef("Food Delivery", CategoryType.VARIABLE_EXPENSE),
        DefaultCategoryDef("Shopping", CategoryType.VARIABLE_EXPENSE),
        DefaultCategoryDef("Entertainment", CategoryType.VARIABLE_EXPENSE),
        DefaultCategoryDef("Travel", CategoryType.VARIABLE_EXPENSE),
        DefaultCategoryDef("Transportation", CategoryType.VARIABLE_EXPENSE),
        DefaultCategoryDef("Cab & Taxi", CategoryType.VARIABLE_EXPENSE),
        DefaultCategoryDef("Medical", CategoryType.VARIABLE_EXPENSE),
        DefaultCategoryDef("Clothing", CategoryType.VARIABLE_EXPENSE),
        DefaultCategoryDef("Furniture", CategoryType.VARIABLE_EXPENSE),
        DefaultCategoryDef("Electronics", CategoryType.VARIABLE_EXPENSE),
        DefaultCategoryDef("Personal Care", CategoryType.VARIABLE_EXPENSE),
        DefaultCategoryDef("Gym & Fitness", CategoryType.VARIABLE_EXPENSE),
        DefaultCategoryDef("Gifts & Donations", CategoryType.VARIABLE_EXPENSE),
        DefaultCategoryDef("Books & Learning", CategoryType.VARIABLE_EXPENSE),
        DefaultCategoryDef("Pet Care", CategoryType.VARIABLE_EXPENSE),
        DefaultCategoryDef("Services", CategoryType.VARIABLE_EXPENSE),
        DefaultCategoryDef("Offline Merchant", CategoryType.VARIABLE_EXPENSE),
        DefaultCategoryDef("Unknown Expense", CategoryType.VARIABLE_EXPENSE),
        DefaultCategoryDef("Cash Withdrawal", CategoryType.VARIABLE_EXPENSE),
        DefaultCategoryDef(AppConstants.Categories.MISCELLANEOUS, CategoryType.VARIABLE_EXPENSE),
        DefaultCategoryDef(AppConstants.Categories.UNCATEGORIZED, CategoryType.VARIABLE_EXPENSE),

        // Investment
        DefaultCategoryDef(AppConstants.Categories.MUTUAL_FUNDS, CategoryType.INVESTMENT),
        DefaultCategoryDef("Stocks", CategoryType.INVESTMENT),
        DefaultCategoryDef("Gold", CategoryType.INVESTMENT),
        DefaultCategoryDef(AppConstants.Categories.RECURRING_DEPOSITS, CategoryType.INVESTMENT),
        DefaultCategoryDef("Fixed Deposits", CategoryType.INVESTMENT),
        DefaultCategoryDef("Provident Fund", CategoryType.INVESTMENT),
        DefaultCategoryDef("PPF / EPF", CategoryType.INVESTMENT),
        DefaultCategoryDef("NPS", CategoryType.INVESTMENT),
        DefaultCategoryDef("Crypto", CategoryType.INVESTMENT),
        DefaultCategoryDef("Chits", CategoryType.INVESTMENT),
        
        // Vehicle
        DefaultCategoryDef("Fuel", CategoryType.VEHICLE),
        DefaultCategoryDef("Vehicle Maintenance", CategoryType.VEHICLE),
        DefaultCategoryDef("Parking & Tolls", CategoryType.VEHICLE),

        // Liability Payments
        DefaultCategoryDef(AppConstants.Categories.CREDIT_BILL_PAYMENTS, CategoryType.LIABILITY),
        DefaultCategoryDef("Loan Repayment", CategoryType.LIABILITY),

        // Transfer
        DefaultCategoryDef(AppConstants.Categories.P2P_TRANSFERS, CategoryType.TRANSFER),
        DefaultCategoryDef(AppConstants.Categories.SELF_TRANSFER, CategoryType.TRANSFER),

        // System
        DefaultCategoryDef("Spam", CategoryType.IGNORE),
        DefaultCategoryDef("Failed/Declined", CategoryType.IGNORE),
        DefaultCategoryDef("Credit Card Statement", CategoryType.STATEMENT)
    )
}
