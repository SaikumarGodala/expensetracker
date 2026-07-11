package com.saikumar.expensetracker.ui.settings

import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

import androidx.compose.foundation.layout.ExperimentalLayoutApi

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AdvancedSettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Advanced Settings") },
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

            // (Theme & palette moved to the main Settings screen's "Appearance" section)

            // Debug Mode Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Debug Mode", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Enable detailed classification logs",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                        Switch(
                            checked = viewModel.debugMode.collectAsState().value,
                            onCheckedChange = { viewModel.setDebugMode(it) }
                        )
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

                    // Log All SMS Button
                    OutlinedButton(
                        onClick = {
                            val app = context.applicationContext as? com.saikumar.expensetracker.ExpenseTrackerApplication
                            if (app == null) {
                                android.widget.Toast.makeText(context, "❌ Unable to access application scope", android.widget.Toast.LENGTH_SHORT).show()
                                return@OutlinedButton
                            }
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
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Export SMS Logs (Debug)")
                    }
                    Text(
                        "Exports SMS locally for debugging. No data leaves your device.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.padding(top = 8.dp)
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

            // Category Preferences Backup Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("Category Preferences Backup", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Your learned category preferences are automatically backed up when you edit transactions. This ensures they survive app updates.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )

                    // Load backup info on screen composition
                    LaunchedEffect(Unit) {
                        viewModel.loadBackupInfo(context)
                    }

                    val backupInfo by viewModel.backupInfo.collectAsState()
                    val scope = rememberCoroutineScope()

                    // Display backup status
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surface,
                        shape = MaterialTheme.shapes.small
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            if (backupInfo != null) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "Last Backup:",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        formatBackupDate(backupInfo!!.timestamp),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "Preferences Saved:",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        "${backupInfo!!.count}",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            } else {
                                Text(
                                    "No backup found",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }

                    // Manual backup button
                    OutlinedButton(
                        onClick = {
                            scope.launch {
                                viewModel.backupMerchantMemory(context)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Backup Now")
                    }

                    Text(
                        "💡 Tip: Backups happen automatically when you edit transactions. Use this button for manual backups.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

/**
 * Format backup timestamp for display
 */
private fun formatBackupDate(timestamp: Long): String {
    val instant = java.time.Instant.ofEpochMilli(timestamp)
    val zonedDateTime = instant.atZone(java.time.ZoneId.systemDefault())

    val now = java.time.LocalDateTime.now()
    val backupDateTime = zonedDateTime.toLocalDateTime()

    val daysDiff = java.time.temporal.ChronoUnit.DAYS.between(backupDateTime.toLocalDate(), now.toLocalDate())

    return when {
        daysDiff == 0L -> {
            val formatter = java.time.format.DateTimeFormatter.ofPattern("'Today at' HH:mm")
            backupDateTime.format(formatter)
        }
        daysDiff == 1L -> {
            val formatter = java.time.format.DateTimeFormatter.ofPattern("'Yesterday at' HH:mm")
            backupDateTime.format(formatter)
        }
        daysDiff < 7 -> {
            val formatter = java.time.format.DateTimeFormatter.ofPattern("EEEE 'at' HH:mm")
            backupDateTime.format(formatter)
        }
        else -> {
            val formatter = java.time.format.DateTimeFormatter.ofPattern("dd MMM yyyy")
            backupDateTime.format(formatter)
        }
    }
}
