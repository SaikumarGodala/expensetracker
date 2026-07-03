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
private fun formatAmount(paisa: Long): String =
    com.saikumar.expensetracker.util.CurrencyFormatter.formatPaisa(paisa)

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
    onScanInbox: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
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
    
    // NOTE: a local "Search Transactions" AlertDialog used to live here - dead code, its
    // trigger flag was never set to true (search goes through the global SearchScreen).

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
            ExtendedFloatingActionButton(
                onClick = onNavigateToAdd,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add") },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                expanded = !listState.canScrollBackward
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {

            // Scan Progress Indicator
            if (scanState is com.saikumar.expensetracker.util.ScanState.Scanning) {
                val scanning = scanState as com.saikumar.expensetracker.util.ScanState.Scanning
                LinearProgressIndicator(
                    progress = { scanning.progress },
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
                 // Left: Screen title (drawer removed - navigation lives in the bottom bar)
                 Text(
                     "Home",
                     style = MaterialTheme.typography.titleLarge,
                     color = MaterialTheme.colorScheme.onSurface,
                     modifier = Modifier.padding(start = 4.dp)
                 )


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
                contentPadding = PaddingValues(bottom = 96.dp, top = 65.dp), // Reduced top padding
                verticalArrangement = Arrangement.spacedBy(8.dp)
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
                                text = date.format(DateTimeFormatter.ofPattern("EEE, d MMM")).uppercase(),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
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
    val isNegative = state.extraMoney < 0

    // Salary typically arrives at the END of a cycle, so "Remaining Money" spends most of
    // the month as a meaningless scary negative. Until income lands, show what was actually
    // spent instead - a number that IS meaningful mid-cycle.
    val awaitingIncome = state.totalIncome <= 0.0
    val spentSoFar = state.totalExpenses + state.totalVehicleExpenses
    val heroLabel = if (awaitingIncome) "Spent This Cycle" else "Remaining Money"
    val heroValue = if (awaitingIncome) spentSoFar else state.extraMoney

    // Subtle vertical gradient over the primary container so the hero reads as a
    // distinct surface without resorting to hardcoded brand colors (keeps working
    // across all five palettes + dynamic color).
    val gradient = androidx.compose.ui.graphics.Brush.verticalGradient(
        colors = listOf(
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        )
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
        color = Color.Transparent
    ) {
        Column(
            modifier = Modifier
                .background(gradient)
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    heroLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
                if (state.cycleRange != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.08f),
                        shape = androidx.compose.foundation.shape.RoundedCornerShape(50)
                    ) {
                        Text(
                            "${state.cycleRange.startDate.format(DateTimeFormatter.ofPattern("d MMM"))} – ${state.cycleRange.endDate.format(DateTimeFormatter.ofPattern("d MMM"))}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                // Sign before the currency symbol: −₹50,372, not ₹-50,372
                (if (heroValue < 0) "−" else "") +
                    "₹${String.format(Locale.getDefault(), "%,.0f", kotlin.math.abs(heroValue))}",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )

            Spacer(modifier = Modifier.height(6.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                if (!awaitingIncome) {
                    Icon(
                        if (isNegative) Icons.Default.ArrowDownward else Icons.AutoMirrored.Filled.TrendingUp,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (isNegative) MaterialTheme.colorScheme.error else IncomeGreen
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                }
                Text(
                    state.balanceContext,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (!awaitingIncome && isNegative) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f),
                    fontWeight = FontWeight.Medium
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
    Surface(
        onClick = onClick,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(18.dp),
        color = color.copy(alpha = 0.12f)
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).widthIn(min = 80.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(color, androidx.compose.foundation.shape.CircleShape)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "₹${String.format(Locale.getDefault(), "%,.0f", amount)}",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
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

    // Use app's theme state, not just system setting
    val isDarkTheme = LocalIsDarkTheme.current

    // ============ CATEGORY-FIRST DESIGN ============
    // The category is what users scan the list for, so it drives the row's visual identity:
    // - the icon avatar is tinted with the category's stable color (CategoryColors)
    // - the category name renders as a colored chip on the second line
    // Transaction *state* (pending, needs review, refund...) is layered on top as a second
    // chip + container tint for attention states, and money *direction* colors the amount.
    // Curated colors are mid/deep tones tuned for light surfaces; lighten them in dark
    // theme so chips and icons stay readable on dark containers.
    val rawCategoryColor = com.saikumar.expensetracker.util.CategoryColors.getColor(item.category.name)
    val categoryColor = if (isDarkTheme) {
        androidx.compose.ui.graphics.lerp(rawCategoryColor, Color.White, 0.35f)
    } else rawCategoryColor
    val categoryIcon = CategoryIcons.getIcon(item.category.name)

    // Attention/state accent for the secondary chip
    val stateChip: Pair<String, Color>? = when {
        needsReview -> "Needs Review" to (if (isDarkTheme) Color(0xFFFFB74D) else Color(0xFFE65100))
        isPending -> "Pending" to PendingOrange
        isIgnored -> "Ignored" to MaterialTheme.colorScheme.onSurfaceVariant
        isStatement -> "Statement" to MaterialTheme.colorScheme.primary
        isSelfTransferLinked -> "Self Transfer" to MaterialTheme.colorScheme.onSurfaceVariant
        isRefund -> "Refund" to (if (isDarkTheme) Color(0xFF81C784) else UndoGreen)
        isCashback -> "Cashback" to (if (isDarkTheme) Color(0xFFFFD54F) else CashbackText)
        // "Not spend" is the message here; short label so it doesn't truncate.
        // Skipped when the category already says Credit Bill Payments (redundant).
        isLiabilityPayment && item.category.name != com.saikumar.expensetracker.core.AppConstants.Categories.CREDIT_BILL_PAYMENTS ->
            "CC Bill" to (if (isDarkTheme) Color(0xFFB39DDB) else VehiclePurple)
        else -> null
    }

    val containerColor = when {
        isIgnored -> MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.5f)
        needsReview -> if (isDarkTheme) Color(0xFF4A3800) else Color(0xFFFFF3CD)
        isPending -> if (isDarkTheme) HighlightBackgroundDark.copy(alpha = 0.5f) else HighlightBackground
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }

    // Avatar carries category identity except when a state demands attention
    val avatarColor = when {
        needsReview -> if (isDarkTheme) Color(0xFFFFB74D) else Color(0xFFE65100)
        isIgnored -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        else -> categoryColor
    }
    val avatarIcon = when {
        needsReview -> Icons.Default.Warning
        isIgnored -> Icons.Default.VisibilityOff
        isPending -> Icons.Default.Schedule
        isStatement -> Icons.AutoMirrored.Filled.ReceiptLong
        isSelfTransferLinked -> Icons.Default.SwapHoriz
        isRefund -> Icons.Default.Refresh
        isCashback -> Icons.Default.CardGiftcard
        isLiabilityPayment -> Icons.Default.CreditCard
        isInvestment -> Icons.AutoMirrored.Filled.TrendingUp
        else -> categoryIcon
    }

    // Money direction: + in (green), − out (red), unsigned neutral moves (grey).
    // FINANCIAL ACCURACY (display): CC bill payments are NEUTRAL - the card swipes were
    // already recorded as expenses when they happened, and the aggregations correctly
    // exclude LIABILITY_PAYMENT. Showing the bill payment with a red minus made it look
    // like the money was being counted twice.
    val isMoneyIn = isIncome || isRefund || isCashback
    val isNeutral = isIgnored || isStatement || isTransfer || isLiabilityPayment
    val amountColor = when {
        isIgnored -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
        isNeutral -> MaterialTheme.colorScheme.onSurfaceVariant
        // Investments are asset transfers, not losses - blue, not expense-red
        isInvestment -> if (isDarkTheme) Color(0xFF64B5F6) else InvestmentBlueText
        isMoneyIn -> IncomeGreen
        else -> ExpenseRedLight
    }
    val amountPrefix = when {
        isNeutral -> ""
        isMoneyIn -> "+"
        else -> "−"
    }

    val merchantLabel = item.transaction.merchantName
        ?: item.transaction.upiId
        ?: item.transaction.note
        ?: item.category.name
    val timeLabel = Instant.ofEpochMilli(item.transaction.timestamp)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern("h:mm a"))

    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp),
        color = containerColor,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Category-colored avatar (rounded square reads more "category tile" than a circle)
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(avatarColor.copy(alpha = 0.15f), androidx.compose.foundation.shape.RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(avatarIcon, contentDescription = null, tint = avatarColor, modifier = Modifier.size(22.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    merchantLabel,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Category chip - the color-coded identity. Skipped when the title line
                    // is already the category name (no merchant extracted) - repeating the
                    // same text as both title and chip looked broken.
                    if (merchantLabel != item.category.name) {
                        LabelChip(text = item.category.name, color = categoryColor)
                    }
                    // Skip the state chip when it would just repeat the category name
                    // (e.g. category "Cashback" + state "Cashback")
                    stateChip?.takeIf { it.first != item.category.name }?.let { (label, color) ->
                        if (merchantLabel != item.category.name) {
                            Spacer(modifier = Modifier.width(6.dp))
                        }
                        LabelChip(text = label, color = color)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        timeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                amountPrefix + formatAmount(item.transaction.amountPaisa),
                style = MaterialTheme.typography.titleSmall,
                color = amountColor,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

/**
 * Small tinted pill used for category identity and transaction state on list rows.
 */
@Composable
private fun LabelChip(text: String, color: Color) {
    Surface(
        color = color.copy(alpha = 0.14f),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}
