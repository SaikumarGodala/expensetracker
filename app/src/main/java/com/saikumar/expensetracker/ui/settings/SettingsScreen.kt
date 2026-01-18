package com.saikumar.expensetracker.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import android.util.Log
import com.saikumar.expensetracker.data.db.AppDatabase
import com.saikumar.expensetracker.data.entity.Category
import com.saikumar.expensetracker.data.entity.CategoryType
import com.saikumar.expensetracker.sms.SmsProcessor
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(viewModel: SettingsViewModel, onNavigateBack: () -> Unit, onNavigateToSalaryHistory: () -> Unit, onNavigateToLinkManager: () -> Unit, onNavigateToCategories: () -> Unit = {}, onNavigateToRetirement: () -> Unit = {}) {
    val context = LocalContext.current
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

            // App Appearance
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("App Theme", style = MaterialTheme.typography.titleMedium)
                    
                    // Theme Mode
                    val themeMode by viewModel.themeMode.collectAsState()
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = themeMode == 0, onClick = { viewModel.setThemeMode(0) }, label = { Text("System") })
                        FilterChip(selected = themeMode == 1, onClick = { viewModel.setThemeMode(1) }, label = { Text("Light") })
                        FilterChip(selected = themeMode == 2, onClick = { viewModel.setThemeMode(2) }, label = { Text("Dark") })
                    }
                    
                    HorizontalDivider()
                    
                    Text("Color Palette", style = MaterialTheme.typography.titleMedium)
                    val colorPalette by viewModel.colorPalette.collectAsState()
                    
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val palettes = listOf("DYNAMIC", "OCEAN", "FOREST", "SUNSET", "SNOW")
                        palettes.forEach { palette ->
                            val isSelected = colorPalette == palette
                            FilterChip(
                                selected = isSelected,
                                onClick = { viewModel.setColorPalette(palette) },
                                label = { 
                                    if (palette == "DYNAMIC") Text("Default") 
                                    else Text(palette.lowercase().replaceFirstChar { it.uppercase() }) 
                                },
                                leadingIcon = if (isSelected) {
                                    { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                } else if (palette == "SNOW") {
                                    { Text("❄️") }
                                } else null
                            )
                        }
                    }
                }
            }

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
            
            // RAW SMS LOGGING BUTTON - At the top for easy access
            Button(
                onClick = {
                    val app = context.applicationContext as com.saikumar.expensetracker.ExpenseTrackerApplication
                    app.applicationScope.launch(Dispatchers.IO) {
                        withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(context, "📱 Logging ALL existing SMS messages...", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        try {
                            val count = com.saikumar.expensetracker.util.RawSmsLogger.scanAndLogAllSms(context)
                            withContext(Dispatchers.Main) {
                                android.widget.Toast.makeText(context, "✅ Logged $count SMS messages to Downloads/ExpenseTrackerLogs/", android.widget.Toast.LENGTH_LONG).show()
                            }
                        } catch (e: Exception) {
                            Log.e("SettingsScreen", "SMS logging failed", e)
                            withContext(Dispatchers.Main) {
                                android.widget.Toast.makeText(context, "❌ Failed: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                )
            ) {
                Text("📱 LOG ALL EXISTING SMS TO FILES")
            }
            
            // TRANSACTION-ONLY LOGGING BUTTON (Temporarily Disabled - Logic Under Construction)
            /*
            Button(
                onClick = {
                    val app = context.applicationContext as com.saikumar.expensetracker.ExpenseTrackerApplication
                    app.applicationScope.launch(Dispatchers.IO) {
                        try {
                            // Logic disabled
                             withContext(Dispatchers.Main) {
                                android.widget.Toast.makeText(context, "Feature disabled during refactoring", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Log.e("SettingsScreen", "Error", e)
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    contentColor = MaterialTheme.colorScheme.onSecondary
                )
            ) {
                Text("💰 LOG TRANSACTIONS ONLY (FILTERED)")
            }
            */

            HorizontalDivider()

            Button(
                onClick = {
                    // Use application scope for long-running operations that should survive composition changes
                    val app = context.applicationContext as com.saikumar.expensetracker.ExpenseTrackerApplication
                    app.applicationScope.launch(Dispatchers.IO) {
                        withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(context, "Scanning Inbox...", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        try {
                            SmsProcessor.scanInbox(context)
                            withContext(Dispatchers.Main) {
                                android.widget.Toast.makeText(context, "Scan Complete", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Log.e("SettingsScreen", "Scan failed", e)
                            withContext(Dispatchers.Main) {
                                android.widget.Toast.makeText(context, "Scan failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Scan Inbox for Transactions")
            }

            Button(
                onClick = {
                    // Use application scope for long-running operations that should survive composition changes
                    val app = context.applicationContext as com.saikumar.expensetracker.ExpenseTrackerApplication
                    app.applicationScope.launch(Dispatchers.IO) {
                        withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(context, "Reclassifying Transactions...", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        try {
                            SmsProcessor.reclassifyTransactions(context)
                            withContext(Dispatchers.Main) {
                                android.widget.Toast.makeText(context, "Done", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Log.e("SettingsScreen", "Reclassify failed", e)
                            withContext(Dispatchers.Main) {
                                android.widget.Toast.makeText(context, "Reclassify failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
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
                onClick = onNavigateToLinkManager,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer, contentColor = MaterialTheme.colorScheme.onTertiaryContainer)
            ) {
                Text("Manage Transaction Links")
            }

            Button(
                onClick = onNavigateToRetirement,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primaryContainer, contentColor = MaterialTheme.colorScheme.onPrimaryContainer)
            ) {
                Text("View EPF / NPS Balances")
            }

            Button(
                onClick = {
                    val app = context.applicationContext as com.saikumar.expensetracker.ExpenseTrackerApplication
                    app.applicationScope.launch(Dispatchers.IO) {
                        withContext(Dispatchers.Main) {
                            android.widget.Toast.makeText(context, "Repairing Data...", android.widget.Toast.LENGTH_SHORT).show()
                        }
                        try {
                            com.saikumar.expensetracker.util.MigrationHelper.repairData(app)
                            withContext(Dispatchers.Main) {
                                android.widget.Toast.makeText(context, "Data Repair Complete", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        } catch (e: Exception) {
                            Log.e("SettingsScreen", "Repair failed", e)
                            withContext(Dispatchers.Main) {
                                android.widget.Toast.makeText(context, "Repair failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer, contentColor = MaterialTheme.colorScheme.onErrorContainer)
            ) {
                Text("Fix / Repair Data Corruption")
            }

            Button(
                onClick = {
                    // Use application scope for long-running operations
                    val app = context.applicationContext as com.saikumar.expensetracker.ExpenseTrackerApplication
                    app.applicationScope.launch(Dispatchers.IO) {
                        try {
                            Log.d("SettingsScreen", "=== DATABASE RESET STARTED ===")
                            
                            // Use application's stable scope for database operations
                            val db = app.database
                            db.close()
                            Log.d("SettingsScreen", "Database closed")
                            
                            // Delete the database file
                            val dbFile = context.getDatabasePath("expense_tracker_db")
                            val deleted = dbFile.delete()
                            Log.d("SettingsScreen", "Database file deleted: $deleted")
                            
                            // Delete WAL and SHM files if they exist
                            context.getDatabasePath("expense_tracker_db-wal").delete()
                            context.getDatabasePath("expense_tracker_db-shm").delete()
                            
                            // Clear the instance so it gets recreated
                            // Clear the instance so it gets recreated
                            AppDatabase.clearInstance()
                            app.forceDatabaseReload()
                            Log.d("SettingsScreen", "Instance cleared, will be recreated on next access")
                            
                            // FORCE SEEDING: Use DAO for safer insertion
                            val newDb = app.database
                            val categoriesToSeed = listOf(
                                Category(name = "Salary", type = CategoryType.INCOME, isDefault = true),
                                Category(name = "Freelance / Other", type = CategoryType.INCOME, isDefault = true),
                                Category(name = "Refund", type = CategoryType.INCOME, isDefault = true),
                                Category(name = "Cashback", type = CategoryType.INCOME, isDefault = true),
                                Category(name = "Interest", type = CategoryType.INCOME, isDefault = true),
                                Category(name = "Other Income", type = CategoryType.INCOME, isDefault = true),
                                Category(name = "Investment Redemption", type = CategoryType.INCOME, isDefault = true),
                                Category(name = "Housing", type = CategoryType.FIXED_EXPENSE, isDefault = true),
                                Category(name = "Utilities", type = CategoryType.FIXED_EXPENSE, isDefault = true),
                                Category(name = "Insurance", type = CategoryType.FIXED_EXPENSE, isDefault = true),
                                Category(name = "Subscriptions", type = CategoryType.FIXED_EXPENSE, isDefault = true),
                                Category(name = "Mobile + WiFi", type = CategoryType.FIXED_EXPENSE, isDefault = true),
                                Category(name = "Loan EMI", type = CategoryType.FIXED_EXPENSE, isDefault = true),
                                Category(name = "Credit Bill Payments", type = CategoryType.FIXED_EXPENSE, isDefault = true),
                                Category(name = "Groceries", type = CategoryType.VARIABLE_EXPENSE, isDefault = true),
                                Category(name = "Dining Out", type = CategoryType.VARIABLE_EXPENSE, isDefault = true),
                                Category(name = "Entertainment", type = CategoryType.VARIABLE_EXPENSE, isDefault = true),
                                Category(name = "Travel", type = CategoryType.VARIABLE_EXPENSE, isDefault = true),
                                Category(name = "Cab & Taxi", type = CategoryType.VARIABLE_EXPENSE, isDefault = true),
                                Category(name = "Food Delivery", type = CategoryType.VARIABLE_EXPENSE, isDefault = true),
                                Category(name = "Medical", type = CategoryType.VARIABLE_EXPENSE, isDefault = true),
                                Category(name = "Shopping", type = CategoryType.VARIABLE_EXPENSE, isDefault = true),
                                Category(name = "Miscellaneous", type = CategoryType.VARIABLE_EXPENSE, isDefault = true),
                                Category(name = "Unknown Expense", type = CategoryType.VARIABLE_EXPENSE, isDefault = true),
                                Category(name = "Uncategorized", type = CategoryType.VARIABLE_EXPENSE, isDefault = true),
                                Category(name = "Mutual Funds", type = CategoryType.INVESTMENT, isDefault = true),
                                Category(name = "Recurring Deposits", type = CategoryType.INVESTMENT, isDefault = true),
                                Category(name = "Fuel", type = CategoryType.VEHICLE, isDefault = true),
                                Category(name = "Service", type = CategoryType.VEHICLE, isDefault = true),
                                Category(name = "P2P Transfers", type = CategoryType.VARIABLE_EXPENSE, isDefault = true)
                            )
                            
                            newDb.categoryDao().insertCategories(categoriesToSeed)
                            val finalCount = newDb.categoryDao().getCount()
                            Log.d("SettingsScreen", "Categories forcefully re-seeded via DAO. Count: $finalCount")
                            
                            withContext(Dispatchers.Main) {
                                android.widget.Toast.makeText(context, "Database Reset & Seeded ($finalCount categories). Ready to Scan.", android.widget.Toast.LENGTH_LONG).show()
                            }
                            
                            Log.d("SettingsScreen", "=== DATABASE RESET COMPLETED ===")
                        } catch (e: Exception) {
                            Log.e("SettingsScreen", "Reset failed", e)
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
