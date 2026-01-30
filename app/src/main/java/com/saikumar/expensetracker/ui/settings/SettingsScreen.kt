package com.saikumar.expensetracker.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import android.util.Log
import com.saikumar.expensetracker.data.db.AppDatabase
import com.saikumar.expensetracker.data.entity.Category
import com.saikumar.expensetracker.data.entity.CategoryType
import com.saikumar.expensetracker.sms.SmsProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.text.font.FontWeight

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    onNavigateToCategories: () -> Unit = {},
    onNavigateToAdvanced: () -> Unit = {},
    onNavigateToTransferCircle: () -> Unit = {}
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    // Observe snackbar messages
    val snackbarMessages by viewModel.snackbarController.messages.collectAsState()
    
    LaunchedEffect(snackbarMessages) {
        snackbarMessages.firstOrNull()?.let { message ->
            scope.launch {
                val result = snackbarHostState.showSnackbar(
                    message = message.message,
                    actionLabel = message.actionLabel,
                    duration = when (message.duration) {
                        com.saikumar.expensetracker.util.SnackbarDuration.Short -> SnackbarDuration.Short
                        com.saikumar.expensetracker.util.SnackbarDuration.Long -> SnackbarDuration.Long
                        com.saikumar.expensetracker.util.SnackbarDuration.Indefinite -> SnackbarDuration.Indefinite
                    }
                )
                if (result == SnackbarResult.ActionPerformed) {
                    message.onAction?.invoke()
                }
                viewModel.snackbarController.dismiss(message.id)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(scrollState)
            .padding(paddingValues)
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // ============= QUICK ACTIONS SECTION =============
        var showReclassifyConfirmation by remember { mutableStateOf(false) }

        if (showReclassifyConfirmation) {
            AlertDialog(
                onDismissRequest = { showReclassifyConfirmation = false },
                icon = { Icon(Icons.Default.Category, contentDescription = null) },
                title = { Text("Reclassify All Transactions?") },
                text = {
                    Text("This will re-analyze all your transactions using the latest categorization rules. This may take a moment for large transaction histories.")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showReclassifyConfirmation = false
                            val app = context.applicationContext as com.saikumar.expensetracker.ExpenseTrackerApplication
                            app.applicationScope.launch(Dispatchers.IO) {
                                withContext(Dispatchers.Main) {
                                    android.widget.Toast.makeText(context, "Reclassifying...", android.widget.Toast.LENGTH_SHORT).show()
                                }
                                try {
                                    SmsProcessor.reclassifyTransactions(context)
                                    withContext(Dispatchers.Main) {
                                        android.widget.Toast.makeText(context, "Reclassification complete", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                } catch (e: Exception) {
                                    Log.e("SettingsScreen", "Reclassify failed", e)
                                    withContext(Dispatchers.Main) {
                                        android.widget.Toast.makeText(context, "Failed: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        }
                    ) {
                        Text("Reclassify")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showReclassifyConfirmation = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        SettingsSection(title = "Quick Actions") {
            SettingsItem(
                icon = Icons.Default.Category,
                title = "Reclassify Transactions",
                subtitle = "Re-analyze all transactions",
                onClick = { showReclassifyConfirmation = true }
            )
            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
            SettingsItem(
                icon = Icons.Default.Label,
                title = "Manage Categories",
                subtitle = "Add, edit, or disable categories",
                onClick = onNavigateToCategories
            )
            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
            SettingsItem(
                icon = Icons.Default.People,
                title = "Transfer Circle",
                subtitle = "Manage trusted P2P contacts",
                onClick = onNavigateToTransferCircle
            )
        }

        // ============= BREACH HISTORY DIALOG =============
        var showBreachHistory by remember { mutableStateOf(false) }
        val breachHistory by viewModel.breachHistory.collectAsState()
        
        if (showBreachHistory) {
            AlertDialog(
                onDismissRequest = { showBreachHistory = false },
                title = { Text("Breach History") },
                text = {
                    if (breachHistory.isEmpty()) {
                        Text("No breaches recorded so far. Good job!")
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)
                        ) {
                            items(breachHistory) { breach ->
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = MaterialTheme.colorScheme.surfaceVariant
                                    )
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "${breach.month} (Stage ${breach.stage})",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Bold
                                            )
                                            Text(
                                                text = java.time.format.DateTimeFormatter.ofPattern("dd MMM").format(
                                                    java.time.Instant.ofEpochMilli(breach.timestamp).atZone(java.time.ZoneId.systemDefault())
                                                ),
                                                style = MaterialTheme.typography.labelSmall
                                            )
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        val formatter = java.text.NumberFormat.getCurrencyInstance(java.util.Locale("en", "IN"))
                                        Text(
                                            "Limit: ${formatter.format(breach.limitAmount/100)} | Spent: ${formatter.format(breach.breachedAmount/100)}",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                        Text(
                                            "Reason: ${breach.reason}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                            modifier = Modifier.padding(top = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showBreachHistory = false }) { Text("Close") }
                }
            )
        }

        // ============= BUDGET ACCOUNTABILITY =============
        val budgetLimit by viewModel.budgetLimitPaise.collectAsState()
        val isAutoBudget by viewModel.isAutoBudgetEnabled.collectAsState()
        var showManualInput by remember { mutableStateOf(false) }
        var tempLimit by remember { mutableStateOf("") }
        
        if (showManualInput) {
             AlertDialog(
                 onDismissRequest = { showManualInput = false },
                 title = { Text("Set Manual Limit") },
                 text = {
                     OutlinedTextField(
                         value = tempLimit,
                         onValueChange = { 
                             if (it.all { c -> c.isDigit() }) tempLimit = it 
                         },
                         label = { Text("Budget in ₹") },
                         singleLine = true
                     )
                 },
                 confirmButton = {
                     TextButton(onClick = {
                         tempLimit.toLongOrNull()?.let { 
                             viewModel.setBudgetLimit(it)
                             showManualInput = false
                         }
                     }) { Text("Save") }
                 },
                 dismissButton = {
                     TextButton(onClick = { showManualInput = false }) { Text("Cancel") }
                 }
             )
        }
        
        SettingsSection(title = "Budget Accountability") {
             Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Auto-Calculate Limit",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    // TODO: Get actual salary from ViewModel if possible, for now phrasing it generically or based on known logic
                    Text(
                        "Based on last credited salary (50%)", 
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = isAutoBudget,
                    onCheckedChange = { viewModel.setAutoBudget(it) }
                )
            }
            
            HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
            
            SettingsItem(
                icon = Icons.Default.AttachMoney,
                title = "Monthly Budget Limit",
                subtitle = "Current: ₹${java.text.NumberFormat.getIntegerInstance().format(budgetLimit / 100)}",
                onClick = {
                    if (!isAutoBudget) {
                        tempLimit = (budgetLimit / 100).toString()
                        showManualInput = true
                    } else {
                         android.widget.Toast.makeText(context, "Disable Auto-Calculate to set manual limit", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            )
            
            HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
            SettingsItem(
                icon = Icons.Default.History,
                title = "View Breach History",
                subtitle = "See past acknowledgments",
                onClick = { showBreachHistory = true }
             )
        }

        // ============= SECURITY SECTION =============
        val appLockEnabled by viewModel.appLockEnabled.collectAsState()
        val appLockPin by viewModel.appLockPin.collectAsState()
        val biometricEnabled by viewModel.biometricEnabled.collectAsState()
        var showPinDialog by remember { mutableStateOf(false) }
        var tempPin by remember { mutableStateOf("") }
        var tempPinConfirm by remember { mutableStateOf("") }
        var isSettingNewPin by remember { mutableStateOf(false) }

        if (showPinDialog) {
            AlertDialog(
                onDismissRequest = { 
                    showPinDialog = false 
                    tempPin = ""
                    tempPinConfirm = ""
                },
                title = { Text(if (isSettingNewPin) "Set New PIN" else "Change PIN") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = tempPin,
                            onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) tempPin = it },
                            label = { Text("Enter 4-digit PIN") },
                            singleLine = true,
                            visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword)
                        )
                        if (tempPin.length == 4) {
                             OutlinedTextField(
                                value = tempPinConfirm,
                                onValueChange = { if (it.length <= 4 && it.all { c -> c.isDigit() }) tempPinConfirm = it },
                                label = { Text("Confirm PIN") },
                                singleLine = true,
                                visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation(),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = androidx.compose.ui.text.input.KeyboardType.NumberPassword)
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (tempPin.length == 4 && tempPin == tempPinConfirm) {
                                viewModel.setAppLockPin(tempPin)
                                viewModel.setAppLockEnabled(true)
                                showPinDialog = false
                                tempPin = ""
                                tempPinConfirm = ""
                            } else {
                                android.widget.Toast.makeText(context, "PINs do not match", android.widget.Toast.LENGTH_SHORT).show()
                            }
                        },
                        enabled = tempPin.length == 4 && tempPinConfirm.length == 4
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPinDialog = false }) { Text("Cancel") }
                }
            )
        }

        SettingsSection(title = "Security") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { 
                        if (appLockEnabled) {
                            viewModel.setAppLockEnabled(false) 
                        } else {
                            if (appLockPin.isEmpty()) {
                                isSettingNewPin = true
                                showPinDialog = true
                            } else {
                                viewModel.setAppLockEnabled(true)
                            }
                        }
                    }
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "App Lock",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        if (appLockEnabled) "Locks on app open. No data leaves device." else "Protect app with biometrics or PIN",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (appLockEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = appLockEnabled,
                    onCheckedChange = { checked ->
                        if (checked) {
                            if (appLockPin.isEmpty()) {
                                isSettingNewPin = true
                                showPinDialog = true
                            } else {
                                viewModel.setAppLockEnabled(true)
                            }
                        } else {
                            viewModel.setAppLockEnabled(false)
                        }
                    }
                )
            }
            
            if (appLockEnabled) {
                HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                
                SettingsItem(
                    icon = Icons.Default.Lock,
                    title = "Change PIN",
                    subtitle = "Update your 4-digit PIN",
                    onClick = {
                        isSettingNewPin = false
                        showPinDialog = true
                    }
                )
                
                HorizontalDivider(modifier = Modifier.padding(start = 16.dp))
                
                 Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Use Biometrics",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "Fingerprint / Face Unlock",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = biometricEnabled,
                        onCheckedChange = { viewModel.setBiometricEnabled(it) }
                    )
                }
            }
        }

        // ============= PREFERENCES SECTION =============
        SettingsSection(title = "Preferences") {
            SettingsItem(
                icon = Icons.Default.Tune,
                title = "Advanced Settings",
                subtitle = "Themes, Debug, ML Data",
                onClick = onNavigateToAdvanced
            )
        }

        // ============= DANGER ZONE =============
        var showResetConfirmation by remember { mutableStateOf(false) }

        if (showResetConfirmation) {
            AlertDialog(
                onDismissRequest = { showResetConfirmation = false },
                icon = { Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                title = { Text("Reset All Data?") },
                text = {
                    Text(
                        "This will permanently delete ALL your transactions, categories, and settings. This action cannot be undone.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showResetConfirmation = false
                            val app = context.applicationContext as com.saikumar.expensetracker.ExpenseTrackerApplication
                            app.applicationScope.launch(Dispatchers.IO) {
                                try {
                                    Log.d("SettingsScreen", "=== DATABASE RESET STARTED ===")

                                    AppDatabase.clearInstance()
                                    app.forceDatabaseReload()

                                    val dbFile = context.getDatabasePath("expense_tracker_db")
                                    dbFile.delete()
                                    context.getDatabasePath("expense_tracker_db-wal").delete()
                                    context.getDatabasePath("expense_tracker_db-shm").delete()

                                    Log.d("SettingsScreen", "Database files deleted")

                                    val newDb = app.database
                                    val finalCount = newDb.categoryDao().getCount()

                                    withContext(Dispatchers.Main) {
                                        android.widget.Toast.makeText(context, "Database Reset. $finalCount categories seeded.", android.widget.Toast.LENGTH_LONG).show()
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
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Delete Everything")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetConfirmation = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        SettingsSection(title = "Danger Zone", isDestructive = true) {
            SettingsItem(
                icon = Icons.Default.DeleteForever,
                title = "Reset Database",
                subtitle = "Requires confirmation",
                isDestructive = true,
                onClick = { showResetConfirmation = true }
            )
        }
        
        Spacer(modifier = Modifier.height(24.dp))
    }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    isDestructive: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
        )
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 1.dp
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun SettingsItem(
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    isDestructive: Boolean = false,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    if (isDestructive) 
                        MaterialTheme.colorScheme.errorContainer 
                    else 
                        MaterialTheme.colorScheme.primaryContainer
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (isDestructive) 
                    MaterialTheme.colorScheme.onErrorContainer 
                else 
                    MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isDestructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(24.dp)
        )
    }
}
