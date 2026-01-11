package com.saikumar.expensetracker.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.saikumar.expensetracker.data.db.AppDatabase
import com.saikumar.expensetracker.data.entity.Category
import com.saikumar.expensetracker.data.entity.CategoryType
import com.saikumar.expensetracker.sms.SmsProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onNavigateBack: () -> Unit, onNavigateToSalaryHistory: () -> Unit, onNavigateToCategories: () -> Unit = {}) {
    val smsAutoRead by viewModel.smsAutoRead.collectAsState()
    val salaryDay by viewModel.salaryDay.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showSalaryDayPicker by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(16.dp)
                .fillMaxSize()
                .verticalScroll(scrollState),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Cycle info card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Salary Cycle", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Automatically calculated from last working day of each month",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                }
            }

            // Debug Mode Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Debug Mode", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Save transaction classification logs to Downloads for debugging",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                    Switch(
                        checked = viewModel.debugMode.collectAsState().value,
                        onCheckedChange = { viewModel.setDebugMode(it) }
                    )
                }
            }
            
            // Salary Company Names Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Salary Company Names", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Add company names to detect salary credits accurately. Match is case-insensitive.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                    
                    val salaryCompanyNames by viewModel.salaryCompanyNames.collectAsState()
                    var newCompanyName by remember { mutableStateOf("") }
                    
                    // Input field with Add button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = newCompanyName,
                            onValueChange = { newCompanyName = it },
                            placeholder = { Text("e.g., INFY, TCS, WIPRO") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                if (newCompanyName.trim().length >= 3) {
                                    viewModel.addSalaryCompanyName(newCompanyName)
                                    newCompanyName = ""
                                }
                            },
                            enabled = newCompanyName.trim().length >= 3
                        ) {
                            Text("Add")
                        }
                    }
                    
                    // Display chips for existing company names
                    if (salaryCompanyNames.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            salaryCompanyNames.forEach { name ->
                                InputChip(
                                    selected = false,
                                    onClick = { viewModel.removeSalaryCompanyName(name) },
                                    label = { Text(name) },
                                    trailingIcon = {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove $name",
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                )
                            }
                        }
                    }
                }
            }

            HorizontalDivider()

            Button(
                onClick = {
                    scope.launch {
                        android.widget.Toast.makeText(context, "Scanning Inbox...", android.widget.Toast.LENGTH_SHORT).show()
                        SmsProcessor.scanInbox(context)
                        android.widget.Toast.makeText(context, "Scan Complete", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Scan Inbox for Transactions")
            }

            Button(
                onClick = {
                    scope.launch {
                        android.widget.Toast.makeText(context, "Reclassifying Transactions...", android.widget.Toast.LENGTH_SHORT).show()
                        SmsProcessor.reclassifyTransactions(context)
                        android.widget.Toast.makeText(context, "Done", android.widget.Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
            ) {
                Text("Reclassify All Transactions")
            }

            Button(
                onClick = onNavigateToCategories,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer)
            ) {
                Text("Manage Categories")
            }

            Button(
                onClick = onNavigateToSalaryHistory,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondaryContainer, contentColor = MaterialTheme.colorScheme.onSecondaryContainer)
            ) {
                Text("View Salary History")
            }

            Button(
                onClick = {
                    scope.launch(Dispatchers.IO) {
                        try {
                            val db = AppDatabase.getDatabase(context, scope)
                            db.clearAllTables()
                            // Repopulate default categories with improved list
                            val defaultCategories = listOf(
                                // Income Categories
                                Category(name = "Salary", type = CategoryType.INCOME, isDefault = true, icon = "Salary"),
                                Category(name = "Freelance / Other", type = CategoryType.INCOME, isDefault = true, icon = "Freelance / Other"),
                                Category(name = "Investments / Dividends", type = CategoryType.INCOME, isDefault = true, icon = "Investments / Dividends"),
                                Category(name = "Bonus", type = CategoryType.INCOME, isDefault = true, icon = "Bonus"),
                                Category(name = "Refund", type = CategoryType.INCOME, isDefault = true, icon = "Refund"),
                                Category(name = "Cashback", type = CategoryType.INCOME, isDefault = true, icon = "Cashback"),
                                Category(name = "Interest", type = CategoryType.INCOME, isDefault = true, icon = "Interest"),
                                Category(name = "Other Income", type = CategoryType.INCOME, isDefault = true, icon = "Other Income"),
                                Category(name = "Unknown Income", type = CategoryType.INCOME, isDefault = true, icon = "Unknown Income"),
                                
                                // Fixed Expense Categories
                                Category(name = "Home Rent / EMI", type = CategoryType.FIXED_EXPENSE, isDefault = true, icon = "Home Rent / EMI"),
                                Category(name = "Home Expenses", type = CategoryType.FIXED_EXPENSE, isDefault = true, icon = "Home Expenses"),
                                Category(name = "Insurance (Life + Health + Term)", type = CategoryType.FIXED_EXPENSE, isDefault = true, icon = "Insurance (Life + Health + Term)"),
                                Category(name = "Subscriptions", type = CategoryType.FIXED_EXPENSE, isDefault = true, icon = "Subscriptions"),
                                Category(name = "Mobile + WiFi", type = CategoryType.FIXED_EXPENSE, isDefault = true, icon = "Mobile + WiFi"),
                                Category(name = "Car EMI", type = CategoryType.FIXED_EXPENSE, isDefault = true, icon = "Car EMI"),
                                Category(name = "Utilities", type = CategoryType.FIXED_EXPENSE, isDefault = true, icon = "Utilities"),
                                
                                // Variable Expense Categories
                                Category(name = "Groceries", type = CategoryType.VARIABLE_EXPENSE, isDefault = true, icon = "Groceries"),
                                Category(name = "Food Outside", type = CategoryType.VARIABLE_EXPENSE, isDefault = true, icon = "Food Outside"),
                                Category(name = "Coffee", type = CategoryType.VARIABLE_EXPENSE, isDefault = true, icon = "Coffee"),
                                Category(name = "Entertainment", type = CategoryType.VARIABLE_EXPENSE, isDefault = true, icon = "Entertainment"),
                                Category(name = "Travel", type = CategoryType.VARIABLE_EXPENSE, isDefault = true, icon = "Travel"),
                                Category(name = "Fuel", type = CategoryType.VARIABLE_EXPENSE, isDefault = true, icon = "Fuel"),
                                Category(name = "Public Transport", type = CategoryType.VARIABLE_EXPENSE, isDefault = true, icon = "Public Transport"),
                                Category(name = "Gifts", type = CategoryType.VARIABLE_EXPENSE, isDefault = true, icon = "Gifts"),
                                Category(name = "Apparel / Shopping", type = CategoryType.VARIABLE_EXPENSE, isDefault = true, icon = "Apparel / Shopping"),
                                Category(name = "Electronics", type = CategoryType.VARIABLE_EXPENSE, isDefault = true, icon = "Electronics"),
                                Category(name = "Books", type = CategoryType.VARIABLE_EXPENSE, isDefault = true, icon = "Books"),
                                Category(name = "Medical", type = CategoryType.VARIABLE_EXPENSE, isDefault = true, icon = "Medical"),
                                Category(name = "Personal Care", type = CategoryType.VARIABLE_EXPENSE, isDefault = true, icon = "Personal Care"),
                                Category(name = "Miscellaneous", type = CategoryType.VARIABLE_EXPENSE, isDefault = true, icon = "Miscellaneous"),
                                Category(name = "Unknown Expense", type = CategoryType.VARIABLE_EXPENSE, isDefault = true, icon = "Unknown Expense"),
                                
                                // Investment Categories
                                Category(name = "Mutual Funds", type = CategoryType.INVESTMENT, isDefault = true, icon = "Mutual Funds"),
                                Category(name = "Stocks", type = CategoryType.INVESTMENT, isDefault = true, icon = "Stocks"),
                                Category(name = "Gold / Silver", type = CategoryType.INVESTMENT, isDefault = true, icon = "Gold / Silver"),
                                Category(name = "Fixed Deposits", type = CategoryType.INVESTMENT, isDefault = true, icon = "Fixed Deposits"),
                                Category(name = "Cryptocurrency", type = CategoryType.INVESTMENT, isDefault = true, icon = "Cryptocurrency"),
                                Category(name = "Bonds", type = CategoryType.INVESTMENT, isDefault = true, icon = "Bonds"),
                                Category(name = "Recurring Deposits", type = CategoryType.INVESTMENT, isDefault = true, icon = "Recurring Deposits"),
                                Category(name = "Additional Lump-sum", type = CategoryType.INVESTMENT, isDefault = true, icon = "Additional Lump-sum"),
                                
                                // Vehicle Categories
                                Category(name = "Service", type = CategoryType.VEHICLE, isDefault = true, icon = "Service"),
                                Category(name = "Repair", type = CategoryType.VEHICLE, isDefault = true, icon = "Repair"),
                                Category(name = "Parking", type = CategoryType.VEHICLE, isDefault = true, icon = "Parking"),
                                Category(name = "Registration / Toll", type = CategoryType.VEHICLE, isDefault = true, icon = "Registration / Toll")
                            )
                            db.categoryDao().insertCategories(defaultCategories)
                            withContext(Dispatchers.Main) {
                                android.widget.Toast.makeText(context, "Database Reset Complete", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            withContext(Dispatchers.Main) {
                                android.widget.Toast.makeText(context, "Reset failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Reset Database")
            }
        }
    }
}
