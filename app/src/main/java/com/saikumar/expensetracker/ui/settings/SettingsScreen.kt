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
import com.saikumar.expensetracker.ui.theme.Dimens
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import android.util.Log
import android.content.Intent
import android.os.Process
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
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
            // Matches Home/Insights' 16dp screen margin and section rhythm - was 20dp on
            // both axes, the one screen out of step with the rest of the app.
            .padding(Dimens.ScreenPadding),
        verticalArrangement = Arrangement.spacedBy(Dimens.SectionSpacing)
    ) {
        Text(
            "Settings",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )

        // ============= QUICK ACTIONS SECTION =============
        var showReclassifyConfirmation by remember { mutableStateOf(false) }
        var runHealthCheck by remember { mutableStateOf(false) }
        var healthReport by remember {
            mutableStateOf<List<com.saikumar.expensetracker.domain.BalanceAuditor.AccountReport>?>(null)
        }
        var isExporting by remember { mutableStateOf(false) }
        var exportResult by remember {
            mutableStateOf<com.saikumar.expensetracker.util.TransactionExporter.ExportResult?>(null)
        }

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
            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
            SettingsItem(
                icon = Icons.Default.CheckCircle,
                title = "Ledger Health Check",
                subtitle = "Verify ledger against bank-reported balances",
                onClick = { runHealthCheck = true }
            )
            HorizontalDivider(modifier = Modifier.padding(start = 56.dp))
            SettingsItem(
                icon = Icons.Default.Download,
                title = if (isExporting) "Exporting…" else "Export Transactions",
                subtitle = "Save every transaction as a CSV in Downloads",
                onClick = {
                    if (isExporting) return@SettingsItem
                    isExporting = true
                    scope.launch {
                        try {
                            val result = com.saikumar.expensetracker.util.TransactionExporter.exportAll(context)
                            exportResult = result
                        } catch (e: Exception) {
                            Log.e("SettingsScreen", "Transaction export failed", e)
                            viewModel.snackbarController.showError(
                                "Export failed: ${e.message}",
                                actionLabel = null
                            )
                        } finally {
                            isExporting = false
                        }
                    }
                }
            )
        }

        // Offer the share sheet only after a successful write, so the user picks where
        // (if anywhere) the file goes - the export itself never leaves the device.
        exportResult?.let { result ->
            AlertDialog(
                onDismissRequest = { exportResult = null },
                icon = { Icon(Icons.Default.Download, contentDescription = null) },
                title = { Text("Exported ${result.rowCount} transactions") },
                text = { Text("Saved to ${result.displayPath}") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            result.uri?.let { uri ->
                                val send = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/csv"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(send, "Share transactions CSV"))
                            }
                            exportResult = null
                        },
                        enabled = result.uri != null
                    ) {
                        Text("Share")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { exportResult = null }) { Text("Done") }
                }
            )
        }

        // ============= LEDGER HEALTH CHECK =============
        if (runHealthCheck) {
            LaunchedEffect(Unit) {
                healthReport = null
                val app = context.applicationContext as com.saikumar.expensetracker.ExpenseTrackerApplication
                healthReport = try {
                    withContext(Dispatchers.IO) {
                        com.saikumar.expensetracker.domain.BalanceAuditor.audit(app.database)
                    }
                } catch (e: Exception) {
                    Log.e("SettingsScreen", "Balance audit failed", e)
                    emptyList()
                }
            }
            AlertDialog(
                onDismissRequest = { runHealthCheck = false },
                icon = { Icon(Icons.Default.CheckCircle, contentDescription = null) },
                title = { Text("Ledger Health") },
                text = {
                    val report = healthReport
                    if (report == null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            Text("Comparing ledger with bank-reported balances…")
                        }
                    } else if (report.isEmpty()) {
                        Text("No balance data yet. Balances are collected from bank SMS during scans — run a scan first.")
                    } else {
                        val dateFmt = remember { java.text.SimpleDateFormat("d MMM yy", java.util.Locale.getDefault()) }
                        LazyColumn {
                            items(report) { acct ->
                                val pct = if (acct.windowCount > 0) acct.consistentCount * 100 / acct.windowCount else 0
                                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                                    Text(
                                        "A/c ••${acct.accountLast4}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        "${acct.consistentCount} of ${acct.windowCount} intervals match ($pct%)",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (pct >= 90) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.error
                                    )
                                    acct.worstGaps.forEach { w ->
                                        val gapRs = w.gapPaisa / 100.0
                                        Text(
                                            "• ${dateFmt.format(java.util.Date(w.fromTs))} → ${dateFmt.format(java.util.Date(w.toTs))}: " +
                                                (if (gapRs > 0) "₹%,.0f unaccounted credit".format(gapRs)
                                                 else "₹%,.0f unaccounted debit".format(-gapRs)) +
                                                " (${w.txnCount} txns in ledger)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { runHealthCheck = false }) { Text("Close") }
                }
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

        // ============= TRANSACTION RULES =============
        val p2pThreshold by viewModel.smallP2pThresholdPaise.collectAsState()
        var showP2pThresholdDialog by remember { mutableStateOf(false) }
        var tempThreshold by remember { mutableStateOf("") }

        if (showP2pThresholdDialog) {
            AlertDialog(
                onDismissRequest = { showP2pThresholdDialog = false },
                title = { Text("P2P Transfer Threshold") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Payments to people BELOW this amount are counted as spending " +
                            "(chai, splits, small shops). At or above it, they follow the " +
                            "transfer rules (Transfer Circle / P2P). Set 0 to disable.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        OutlinedTextField(
                            value = tempThreshold,
                            onValueChange = { if (it.all { c -> c.isDigit() } && it.length <= 7) tempThreshold = it },
                            label = { Text("Amount in ₹") },
                            singleLine = true,
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                            )
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        tempThreshold.toLongOrNull()?.let {
                            viewModel.setSmallP2pThresholdRupees(it)
                            showP2pThresholdDialog = false
                            android.widget.Toast.makeText(context, "Saved. Reclassify to apply to existing transactions.", android.widget.Toast.LENGTH_LONG).show()
                        }
                    }) { Text("Save") }
                },
                dismissButton = {
                    TextButton(onClick = { showP2pThresholdDialog = false }) { Text("Cancel") }
                }
            )
        }

        SettingsSection(title = "Transaction Rules") {
            SettingsItem(
                icon = Icons.Default.People,
                title = "P2P Transfer Threshold",
                subtitle = if (p2pThreshold > 0)
                    "Below ₹${java.text.NumberFormat.getIntegerInstance().format(p2pThreshold / 100)}, payments to people count as spending"
                else "Off - all person payments follow transfer rules",
                onClick = {
                    tempThreshold = (p2pThreshold / 100).toString()
                    showP2pThresholdDialog = true
                }
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

        // ============= APPEARANCE =============
        // Theme & palette moved out of "Advanced" - they're everyday preferences, not
        // power-user tools.
        val themeMode by viewModel.themeMode.collectAsState()
        val colorPalette by viewModel.colorPalette.collectAsState()
        SettingsSection(title = "Appearance") {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Theme", style = MaterialTheme.typography.bodyLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        listOf(0 to "System", 1 to "Light", 2 to "Dark").forEach { (mode, label) ->
                            FilterChip(
                                selected = themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                                label = { Text(label) },
                                modifier = Modifier.height(32.dp)
                            )
                        }
                    }
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                Text("Color Palette", style = MaterialTheme.typography.bodyLarge)
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("DYNAMIC", "OCEAN", "FOREST", "SUNSET", "SNOW").forEach { palette ->
                        FilterChip(
                            selected = colorPalette == palette,
                            onClick = { viewModel.setColorPalette(palette) },
                            label = {
                                Text(
                                    if (palette == "DYNAMIC") "Default"
                                    else palette.lowercase().replaceFirstChar { it.uppercase() },
                                    style = MaterialTheme.typography.labelMedium
                                )
                            },
                            modifier = Modifier.height(32.dp)
                        )
                    }
                }
            }
        }

        // ============= PREFERENCES SECTION =============
        SettingsSection(title = "Preferences") {
            SettingsItem(
                icon = Icons.Default.Tune,
                title = "Advanced Settings",
                subtitle = "Salary detection, backups, debug tools",
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

                                    // 1. Close existing DB connection
                                    AppDatabase.clearInstance()
                                    
                                    // 2. Delete files safely
                                    val dbFile = context.getDatabasePath("expense_tracker_db")
                                    if (dbFile.exists()) dbFile.delete()
                                    context.getDatabasePath("expense_tracker_db-wal").delete()
                                    context.getDatabasePath("expense_tracker_db-shm").delete()

                                    Log.d("SettingsScreen", "Database files deleted")

                                    // 3. Restart App to ensure fresh state (avoids race conditions with old DB instance)
                                    withContext(Dispatchers.Main) {
                                        android.widget.Toast.makeText(context, "Reset successful. Restarting app...", android.widget.Toast.LENGTH_LONG).show()
                                        
                                        val packageManager = context.packageManager
                                        val intent = packageManager.getLaunchIntentForPackage(context.packageName)
                                        if (intent != null) {
                                            val componentName = intent.component
                                            val mainIntent = Intent.makeRestartActivityTask(componentName)
                                            context.startActivity(mainIntent)
                                            Process.killProcess(Process.myPid())
                                        } else {
                                             // Fallback if intent lookup fails
                                             Runtime.getRuntime().exit(0)
                                        }
                                    }
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
            // 12dp vertical keeps rows on the 4dp grid (was 14dp)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(Dimens.RadiusAvatar))
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
