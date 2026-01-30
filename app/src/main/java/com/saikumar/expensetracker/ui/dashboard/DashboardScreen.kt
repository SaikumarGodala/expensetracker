package com.saikumar.expensetracker.ui.dashboard

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.ui.zIndex
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.automirrored.outlined.Sort
import androidx.compose.material.icons.filled.FilterList
import com.saikumar.expensetracker.ui.theme.*

import com.saikumar.expensetracker.data.db.TransactionWithCategory
import com.saikumar.expensetracker.data.entity.*
import com.saikumar.expensetracker.util.CategoryIcons
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.saikumar.expensetracker.ui.common.TransactionEditDialog
import java.util.Locale

/**
 * Format paisa amount to rupee display string
 */
private fun formatAmount(paisa: Long): String {
    val rupees = paisa / 100.0
    return "₹${String.format(Locale.getDefault(), "%,.0f", rupees)}"
}

/**
 * Convert timestamp to LocalDate for grouping
 */
private fun timestampToLocalDate(timestamp: Long): LocalDate {
    return Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel,
    onNavigateToAdd: () -> Unit,
    onCategoryClick: (CategoryType, Long, Long) -> Unit,
    onNavigateToSearch: () -> Unit = {},
    onScanInbox: () -> Unit = {},
    onMenuClick: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    var editingTransaction by remember { mutableStateOf<TransactionWithCategory?>(null) }
    var sortOption by remember { mutableStateOf(com.saikumar.expensetracker.ui.components.SortOption.DATE_DESC) }
    var showDateRangePicker by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    
    // Observe snackbar messages from ViewModel
    val snackbarMessages by viewModel.snackbarController.messages.collectAsState()
    
    LaunchedEffect(snackbarMessages) {
        snackbarMessages.firstOrNull()?.let { message ->
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

    if (editingTransaction != null) {
        TransactionEditDialog(
            transaction = editingTransaction!!,
            categories = uiState.categories,
            onDismiss = { editingTransaction = null },
            onConfirm = { categoryId, note, accountType, updateSimilar, manualClassification ->
                viewModel.updateTransactionDetails(editingTransaction!!.transaction, categoryId, note, accountType, updateSimilar, manualClassification)
                editingTransaction = null
            },
            onAddCategory = { name, type ->
                viewModel.addCategory(name, type)
            },
            onDelete = { txn ->
                viewModel.deleteTransaction(txn)
                editingTransaction = null
            },
            onFindSimilar = { viewModel.findSimilarTransactions(editingTransaction!!.transaction) }
        )
    }

    if (showDateRangePicker) {
        val initialStart = uiState.cycleRange?.startDate?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
        val initialEnd = uiState.cycleRange?.endDate?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli()
        val dateRangePickerState = rememberDateRangePickerState(initialSelectedStartDateMillis = initialStart, initialSelectedEndDateMillis = initialEnd)
        
        DatePickerDialog(
            onDismissRequest = { showDateRangePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    if (dateRangePickerState.selectedStartDateMillis != null && dateRangePickerState.selectedEndDateMillis != null) {
                        val start = Instant.ofEpochMilli(dateRangePickerState.selectedStartDateMillis!!).atZone(ZoneId.systemDefault()).toLocalDate()
                        val end = Instant.ofEpochMilli(dateRangePickerState.selectedEndDateMillis!!).atZone(ZoneId.systemDefault()).toLocalDate()
                        viewModel.setCustomCycle(start, end)
                        showDateRangePicker = false
                    }
                }) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = { showDateRangePicker = false }) { Text("Cancel") }
            }
        ) { DateRangePicker(state = dateRangePickerState) }
    }
    
    var showSearchDialog by remember { mutableStateOf(false) }
    
    if (showSearchDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showSearchDialog = false },
            title = { Text("Search Transactions") },
            text = {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Enter search term...") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = { showSearchDialog = false }) {
                    Text("Close")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    viewModel.onSearchQueryChanged("")
                    showSearchDialog = false
                }) {
                    Text("Clear")
                }
            }
        )
    }

    // Use derivedStateOf for efficient sorting - only recomputes when inputs actually change
    val sortedTransactions by remember(uiState.transactions, sortOption) {
        derivedStateOf {
            when (sortOption) {
                com.saikumar.expensetracker.ui.components.SortOption.DATE_DESC -> uiState.transactions.sortedByDescending { it.transaction.timestamp }
                com.saikumar.expensetracker.ui.components.SortOption.DATE_ASC -> uiState.transactions.sortedBy { it.transaction.timestamp }
                com.saikumar.expensetracker.ui.components.SortOption.AMOUNT_DESC -> uiState.transactions.sortedByDescending { it.transaction.amountPaisa }
                com.saikumar.expensetracker.ui.components.SortOption.AMOUNT_ASC -> uiState.transactions.sortedBy { it.transaction.amountPaisa }
            }
        }
    }
    
    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scanState by com.saikumar.expensetracker.util.ScanProgressManager.scanState.collectAsState()
    
    // Only Add FAB, no Search
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onNavigateToAdd,
                containerColor = MaterialTheme.colorScheme.primary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add")
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            
            // Scan Progress Indicator
            if (scanState is com.saikumar.expensetracker.util.ScanState.Scanning) {
                val scanning = scanState as com.saikumar.expensetracker.util.ScanState.Scanning
                LinearProgressIndicator(
                    progress = scanning.progress,
                    modifier = Modifier.fillMaxWidth().height(4.dp),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
            // --- HEADER & FILTER ---
            // Floating Top Bar with Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter) // Fix: Align to top
                    .zIndex(1f) // Fix: Ensure it's on top of LazyColumn
                    .background(MaterialTheme.colorScheme.background) // Fix: Opaque background
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                 // Left: Menu & Title
                 Row(verticalAlignment = Alignment.CenterVertically) {
                     IconButton(onClick = onMenuClick) {
                         Icon(Icons.Filled.Menu, "Menu", tint = MaterialTheme.colorScheme.onSurface)
                     }
                 }
                 
                 // Right: Actions (Synced with Filter & Search)
                 Row(verticalAlignment = Alignment.CenterVertically) {
                     // Filter Icon (replaces big button)
                     AccountFilterDropdown_IconOnly(
                         accounts = uiState.detectedAccounts,
                         selectedAccounts = uiState.selectedAccounts,
                         onToggle = { viewModel.toggleAccountFilter(it) },
                         onClearAll = { viewModel.clearAccountFilter() }
                     )
                     
                     IconButton(onClick = onScanInbox) {
                         Icon(Icons.Default.Sync, "Sync", tint = MaterialTheme.colorScheme.primary)
                     }
                     IconButton(onClick = onNavigateToSearch) {
                         Icon(Icons.Default.Search, "Search", tint = MaterialTheme.colorScheme.primary)
                     }
                 }
            }
            
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(), 
                contentPadding = PaddingValues(bottom = 80.dp, top = 65.dp), // Reduced top padding
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
               
                item {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {

                        
                        // Refined Month Selector
                        Surface(
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.padding(bottom = 16.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                IconButton(
                                    onClick = { viewModel.previousCycle() },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.ChevronLeft, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
                                }
                                
                                TextButton(onClick = { showDateRangePicker = true }) {
                                    Icon(Icons.Default.CalendarMonth, null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        uiState.cycleRange?.endDate?.format(DateTimeFormatter.ofPattern("MMM yyyy")) ?: "...",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                IconButton(
                                    onClick = { viewModel.nextCycle() },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Default.ChevronRight, null, modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                        
                        // Hero Balance Card
                        if (uiState.transactions.isNotEmpty()) {
                            HeroBalanceCard(uiState)
                        }
                    }
                }

                // Summary Chips
                if (uiState.transactions.isNotEmpty()) {
                    item { 
                        Spacer(modifier = Modifier.height(8.dp))
                        SummaryRowList(uiState) { type ->
                        uiState.cycleRange?.let { range ->
                            val startMillis = range.startDate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                            val endMillis = range.endDate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                            onCategoryClick(type, startMillis, endMillis)
                        }
                    } }
                }
                
                // --- RISKS & ACTIONS ---
                
                // STATEMENTS (Info - Collapsible)
                if (uiState.statements.isNotEmpty()) {
                    item {
                        var statementsExpanded by remember { mutableStateOf(false) }
                        
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                        ) {
                            Column(modifier = Modifier.animateContentSize().padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().clickable { statementsExpanded = !statementsExpanded },
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Description, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "Statements Available (${uiState.statements.size})", 
                                            style = MaterialTheme.typography.labelLarge, 
                                            color = MaterialTheme.colorScheme.onSurface,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                    Icon(
                                        if (statementsExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                        contentDescription = "Expand",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                
                                if (statementsExpanded) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    HorizontalDivider(modifier = Modifier.padding(bottom = 8.dp), color = MaterialTheme.colorScheme.outlineVariant)
                                    uiState.statements.forEach { stmt ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth().clickable { editingTransaction = stmt }.padding(vertical = 8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                 Icon(Icons.AutoMirrored.Filled.ReceiptLong, null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                                 Spacer(modifier = Modifier.width(8.dp))
                                                 Text("Credit Card Statement", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                            }
                                            Text(formatAmount(stmt.transaction.amountPaisa), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                
                // SYSTEM HYGIENE (Ignored Items)
                if (uiState.ignoredCount > 0) {
                     item {
                        Surface(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.VisibilityOff, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "${uiState.ignoredCount} items hidden from view",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(onClick = { /* TODO: Navigate to Hidden Items Screen or Filter */ }) {
                                    Text("Review")
                                }
                            }
                        }
                    }
                }
                
                // EMPTY STATE or RECENT ACTIVITY
                if (uiState.transactions.isEmpty()) {
                    // Empty State Card
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 24.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Default.Inbox,
                                    contentDescription = null,
                                    modifier = Modifier.size(64.dp),
                                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    "No Transactions Yet",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Get started by scanning your SMS inbox for bank and UPI transactions, or add transactions manually",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                                    shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            Icons.Default.Info,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            "Supports major banks and UPI apps",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(24.dp))

                                // Prominent Scan Button
                                Button(
                                    onClick = onScanInbox,
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Scan SMS Inbox", style = MaterialTheme.typography.labelLarge)
                                }

                                Spacer(modifier = Modifier.height(12.dp))

                                OutlinedButton(
                                    onClick = onNavigateToAdd,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Add Transaction Manually", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                    }
                } else {
                    // TRANSACTIONS LIST (All transactions in cycle, grouped by date)
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Transactions (${uiState.transactions.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            // Sort toggle button
                            Surface(
                                onClick = {
                                    sortOption = when (sortOption) {
                                        com.saikumar.expensetracker.ui.components.SortOption.DATE_DESC ->
                                            com.saikumar.expensetracker.ui.components.SortOption.DATE_ASC
                                        com.saikumar.expensetracker.ui.components.SortOption.DATE_ASC ->
                                            com.saikumar.expensetracker.ui.components.SortOption.AMOUNT_DESC
                                        com.saikumar.expensetracker.ui.components.SortOption.AMOUNT_DESC ->
                                            com.saikumar.expensetracker.ui.components.SortOption.AMOUNT_ASC
                                        com.saikumar.expensetracker.ui.components.SortOption.AMOUNT_ASC ->
                                            com.saikumar.expensetracker.ui.components.SortOption.DATE_DESC
                                    }
                                },
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        when (sortOption) {
                                            com.saikumar.expensetracker.ui.components.SortOption.DATE_DESC,
                                            com.saikumar.expensetracker.ui.components.SortOption.DATE_ASC -> Icons.Default.CalendarMonth
                                            else -> Icons.Default.AttachMoney
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        when (sortOption) {
                                            com.saikumar.expensetracker.ui.components.SortOption.DATE_ASC,
                                            com.saikumar.expensetracker.ui.components.SortOption.AMOUNT_ASC -> Icons.Default.ArrowUpward
                                            else -> Icons.Default.ArrowDownward
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp),
                                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                                    )
                                }
                            }
                        }
                    }

                    // Group transactions by date
                    val transactionsByDate = sortedTransactions.groupBy {
                        timestampToLocalDate(it.transaction.timestamp)
                    }

                    // Render grouped transactions with date headers
                    transactionsByDate.forEach { (date, transactionsForDate) ->
                        item(key = "header_$date") {
                            // Date Header
                            Text(
                                text = date.format(DateTimeFormatter.ofPattern("EEEE, d MMMM")),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }

                        items(transactionsForDate, key = { it.transaction.id }) { transaction ->
                            val linkDetail = uiState.transactionLinks[transaction.transaction.id]
                            TransactionItem(
                                transaction,
                                linkType = linkDetail?.type,
                                onClick = { editingTransaction = transaction },
                                modifier = Modifier.animateItem()
                            )
                        }
                    }
                }
            }
        }
    }
}

// ... CycleSelector removed in favor of integrated header ...

@Composable
fun HeroBalanceCard(state: DashboardUiState) {
    var showBreakdown by remember { mutableStateOf(false) }
    
    // Determine Color and Context
    val balanceColor = if (state.extraMoney >= 0) IncomeGreen else ExpenseRed
    
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Text(
            "Remaining Money", 
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
        Text(
            "₹${String.format(Locale.getDefault(), "%,.0f", state.extraMoney)}", 
            style = MaterialTheme.typography.displayLarge, // Bigger
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        // Context Line (Larger now)
        Text(
            state.balanceContext,
            style = MaterialTheme.typography.bodyLarge, // Increased size
            color = if (state.extraMoney < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.secondary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 4.dp)
        )

        // Cycle Date Range
        if (state.cycleRange != null) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text(
                    "${state.cycleRange.startDate.format(DateTimeFormatter.ofPattern("d MMM"))} - ${state.cycleRange.endDate.format(DateTimeFormatter.ofPattern("d MMM yyyy"))}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }
    }
}

@Composable
fun SummaryRowList(state: DashboardUiState, onCategoryClick: (CategoryType) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SummaryChip("Income", state.totalIncome, IncomeGreen) { onCategoryClick(CategoryType.INCOME) } }
        item { SummaryChip("Fixed", state.totalFixedExpenses, ExpenseRed) { onCategoryClick(CategoryType.FIXED_EXPENSE) } }
        item { SummaryChip("Variable", state.totalVariableExpenses, PendingOrange) { onCategoryClick(CategoryType.VARIABLE_EXPENSE) } }
        item { SummaryChip("Invest", state.totalInvestments, TransferBlue) { onCategoryClick(CategoryType.INVESTMENT) } }
        item { SummaryChip("Vehicle", state.totalVehicleExpenses, VehiclePurple) { onCategoryClick(CategoryType.VEHICLE) } }
    }
}

@Composable
fun SummaryChip(label: String, amount: Double, color: Color, onClick: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium, color = color.copy(alpha = 0.15f), modifier = Modifier.clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                label, 
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text("₹${String.format(Locale.getDefault(), "%,.0f", amount)}", fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
fun AccountFilterDropdown_IconOnly(
    accounts: List<com.saikumar.expensetracker.data.entity.UserAccount>,
    selectedAccounts: Set<String>,
    onToggle: (String) -> Unit,
    onClearAll: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Box {
        IconButton(onClick = { expanded = true }) {
             val icon = if (selectedAccounts.isNotEmpty()) Icons.Filled.FilterList else Icons.Outlined.FilterList
             val tint = if (selectedAccounts.isNotEmpty()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
             Icon(icon, contentDescription = "Filter Accounts", tint = tint)
        }
        
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
             if (accounts.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No accounts detected yet", style = MaterialTheme.typography.bodySmall) },
                    onClick = { }
                )
            } else {
                 DropdownMenuItem(
                    text = { Text(if (selectedAccounts.isEmpty()) "Filter Accounts" else "${selectedAccounts.size} Selected", fontWeight = FontWeight.Bold) },
                    onClick = { }
                )
                HorizontalDivider()
                
                if (selectedAccounts.isNotEmpty()) {
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Clear All", color = MaterialTheme.colorScheme.error)
                            }
                        },
                        onClick = {
                            onClearAll()
                            expanded = false
                        }
                    )
                }
                
                accounts.forEach { account ->
                    val typeLabel = if (account.accountType == com.saikumar.expensetracker.data.entity.AccountType.CREDIT_CARD) "CC" else "A/c"
                    val label = "${account.bankName} $typeLabel XX${account.accountNumberLast4}"
                    
                    DropdownMenuItem(
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = account.accountNumberLast4 in selectedAccounts,
                                    onCheckedChange = null
                                )
                                Text(label)
                            }
                        },
                        onClick = { onToggle(account.accountNumberLast4) }
                    )
                }
            }
        }
    }
}

@Composable

fun TransactionItem(
    item: TransactionWithCategory, 
    linkType: LinkType? = null, 
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val transactionType = item.transaction.transactionType
    val isSelfTransferLinked = linkType == LinkType.SELF_TRANSFER
    // Trust the LinkType if present, otherwise fall back to TransactionType
    val isTransfer = isSelfTransferLinked || transactionType == TransactionType.TRANSFER
    
    val isIncome = transactionType == TransactionType.INCOME
    val isLiabilityPayment = transactionType == TransactionType.LIABILITY_PAYMENT
    val isRefund = linkType == LinkType.REFUND || transactionType == TransactionType.REFUND
    val isCashback = transactionType == TransactionType.CASHBACK
    
    val isPending = transactionType == TransactionType.PENDING
    val isIgnored = transactionType == TransactionType.IGNORE
    val isStatement = transactionType == TransactionType.STATEMENT // new
    
    val isInvestment = item.category.type == CategoryType.INVESTMENT ||
                       transactionType == TransactionType.INVESTMENT_OUTFLOW ||
                       transactionType == TransactionType.INVESTMENT_CONTRIBUTION

    val isUncategorized = item.category.name == "Uncategorized"
    val isUnverifiedIncome = item.category.name == "Unverified Income"
    val needsReview = isUncategorized || isUnverifiedIncome

    val categoryIcon = CategoryIcons.getIcon(item.category.name)
    
    // Use app's theme state, not just system setting
    val isDarkTheme = LocalIsDarkTheme.current
    
    // Complete TransactionType handling with distinct colors - DARK MODE AWARE
    // IMPORTANT: isInvestment checked BEFORE isTransfer so investments show as blue
    val containerColor = when {
        isIgnored -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        isPending -> if (isDarkTheme) HighlightBackgroundDark else HighlightBackground
        needsReview -> if (isDarkTheme) Color(0xFF664400) else Color(0xFFFFF3CD) // Amber warning
        isStatement -> MaterialTheme.colorScheme.surface
        isInvestment -> if (isDarkTheme) InvestmentBlueDark else InvestmentBlueLight
        isTransfer -> MaterialTheme.colorScheme.surfaceVariant
        isLiabilityPayment -> if (isDarkTheme) LiabilityPurpleDark else MaterialTheme.colorScheme.tertiaryContainer
        isRefund -> if (isDarkTheme) RefundGreenDark else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        isCashback -> if (isDarkTheme) CashbackGoldDark else CashbackGold
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    
    // Amount color - must be visible on containerColor background
    val amountColor = when {
        isIgnored -> Color.Gray.copy(alpha = 0.5f)
        isPending -> if (isDarkTheme) Color.White else PendingText
        needsReview -> if (isDarkTheme) Color(0xFFFFB74D) else Color(0xFFE65100) // Amber/Orange
        isStatement -> MaterialTheme.colorScheme.primary
        isInvestment -> if (isDarkTheme) Color.White else InvestmentBlueText
        isTransfer -> Color.Gray
        isLiabilityPayment -> if (isDarkTheme) Color.White else MaterialTheme.colorScheme.tertiary
        isRefund -> if (isDarkTheme) Color.White else UndoGreen
        isCashback -> if (isDarkTheme) Color.White else CashbackText
        isIncome -> IncomeGreen
        else -> ExpenseRedLight
    }
    
    val icon = when {
        isIgnored -> Icons.Default.VisibilityOff
        isPending -> Icons.Default.Schedule
        needsReview -> Icons.Default.Warning // Warning icon for needs review
        isStatement -> Icons.AutoMirrored.Filled.ReceiptLong
        isInvestment -> Icons.AutoMirrored.Filled.TrendingUp
        isTransfer -> Icons.Default.SwapHoriz
        isLiabilityPayment -> Icons.Default.CreditCard
        isRefund -> Icons.Default.Refresh
        isCashback -> Icons.Default.CardGiftcard
        else -> categoryIcon
    }
    
    val displayName = when {
        isIgnored -> "Ignored"
        isPending -> "Pending"
        needsReview -> "Needs Review ⚠️" // Clear call-to-action
        isStatement -> "CC Statement"
        isSelfTransferLinked -> "Self Transfer"  // Show "Self Transfer" for linked transactions
        isInvestment -> item.category.name  // Show actual category (Mutual Funds, RD, etc.)
        isTransfer -> item.category.name // Show actual category (P2P Transfers, etc.)
        isLiabilityPayment -> "CC Payment"
        isRefund -> "Refund / Reversal"
        isCashback -> "Cashback"
        else -> item.category.name
    }
    
    // Text color for content inside the card - must contrast with containerColor
    val contentColor = when {
        // For custom colored backgrounds, use appropriate contrast
        isIgnored -> MaterialTheme.colorScheme.onSurfaceVariant
        isPending -> if (isDarkTheme) Color.White else Color(0xFF5D4037) // Brown text on amber
        needsReview -> if (isDarkTheme) Color.White else Color(0xFF5D4037) // Brown text on amber
        isStatement -> MaterialTheme.colorScheme.onSurface
        isInvestment -> if (isDarkTheme) Color.White else Color(0xFF0D47A1) // Blue text
        isTransfer -> MaterialTheme.colorScheme.onSurfaceVariant
        isLiabilityPayment -> if (isDarkTheme) Color.White else Color(0xFF4A1259) // Purple text
        isRefund -> if (isDarkTheme) Color.White else Color(0xFF1B5E20) // Green text
        isCashback -> if (isDarkTheme) Color.White else Color(0xFF5D4037) // Brown/gold text
        else -> MaterialTheme.colorScheme.onSurface
    }
    
    val subtitleColor = when {
        isIgnored || isPending || needsReview || isInvestment || isLiabilityPayment || isRefund || isCashback -> 
            contentColor.copy(alpha = 0.7f)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = amountColor)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    displayName, 
                    fontWeight = FontWeight.Bold,
                    color = contentColor
                )
                Text(
                    item.transaction.merchantName ?: item.transaction.upiId ?: item.transaction.note ?: item.category.name,
                    style = MaterialTheme.typography.bodySmall,
                    color = subtitleColor,
                    maxLines = 1
                )
            }
            Text(formatAmount(item.transaction.amountPaisa), color = amountColor, fontWeight = FontWeight.Bold)
        }
    }
}
