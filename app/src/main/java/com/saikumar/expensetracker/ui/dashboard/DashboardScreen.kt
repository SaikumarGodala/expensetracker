package com.saikumar.expensetracker.ui.dashboard

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.ui.viewinterop.AndroidView
import com.saikumar.expensetracker.ui.theme.*
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
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
fun DashboardScreen(viewModel: DashboardViewModel, onNavigateToAdd: () -> Unit, onCategoryClick: (CategoryType, Long, Long) -> Unit) {
    val uiState by viewModel.uiState.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    var editingTransaction by remember { mutableStateOf<TransactionWithCategory?>(null) }
    var showDateRangePicker by remember { mutableStateOf(false) }

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
            }
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
    
    Scaffold(
        floatingActionButton = {
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = 32.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Search FAB (Left Edge)
                FloatingActionButton(
                    onClick = { showSearchDialog = true },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                ) {
                    Icon(Icons.Default.Search, contentDescription = "Search")
                }
                // Add Transaction FAB (Right Edge)
                FloatingActionButton(
                    onClick = onNavigateToAdd,
                    containerColor = MaterialTheme.colorScheme.primary
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add")
                }
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    CycleSelector(uiState, onPrevious = { viewModel.previousCycle() }, onNext = { viewModel.nextCycle() }, onAdjust = { showDateRangePicker = true })
                    
                    // Account Filter Dropdown
                    AccountFilterDropdown(
                        accounts = uiState.detectedAccounts,
                        selectedAccounts = uiState.selectedAccounts,
                        transactions = uiState.transactions,
                        onToggle = { viewModel.toggleAccountFilter(it) },
                        onClearAll = { viewModel.clearAccountFilter() }
                    )
                    
                    HeroBalanceCard(uiState)
                }
            }
            item { SummaryRowList(uiState) { type -> 
                uiState.cycleRange?.let { range ->
                    val startMillis = range.startDate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    val endMillis = range.endDate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                    onCategoryClick(type, startMillis, endMillis)
                }
            } }
            // Removed spending breakdown chart - already have category breakdown in Overview
            
            // STATEMENTS SECTION (Collapsible)
            if (uiState.statements.isNotEmpty()) {
                item {
                    var statementsExpanded by remember { mutableStateOf(false) }
                    
                    Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { statementsExpanded = !statementsExpanded }
                                .padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Statements (${uiState.statements.size})",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(
                                if (statementsExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                                contentDescription = if (statementsExpanded) "Collapse" else "Expand"
                            )
                        }
                        
                        if (statementsExpanded) {
                            uiState.statements.forEach { stmt ->
                                TransactionItem(
                                    item = stmt,
                                    linkType = uiState.transactionLinks[stmt.transaction.id]?.type,
                                    onClick = { editingTransaction = stmt }
                                )
                            }
                        }
                    }
                }
            }
            
            val groupedTransactions = uiState.transactions.groupBy { timestampToLocalDate(it.transaction.timestamp) }
            groupedTransactions.forEach { (date, transactions) ->
                stickyHeader { DateHeader(date) }
                items(transactions, key = { it.transaction.id }) { transaction ->
                    val linkDetail = uiState.transactionLinks[transaction.transaction.id]
                    TransactionItem(transaction, linkType = linkDetail?.type, onClick = { editingTransaction = transaction })
                }
            }
        }
    }
}

@Composable
fun CycleSelector(state: DashboardUiState, onPrevious: () -> Unit, onNext: () -> Unit, onAdjust: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) { Icon(Icons.Default.KeyboardArrowLeft, null, tint = MaterialTheme.colorScheme.onBackground) }
        Text(
            text = state.cycleRange?.endDate?.format(DateTimeFormatter.ofPattern("MMMM yyyy")) ?: "...", 
            modifier = Modifier.clickable { onAdjust() }, 
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        IconButton(onClick = onNext) { Icon(Icons.Default.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onBackground) }
    }
}

@Composable
fun HeroBalanceCard(state: DashboardUiState) {
    var showBreakdown by remember { mutableStateOf(false) }
    
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable { showBreakdown = !showBreakdown }
        ) {
            Text(
                "Remaining Money", 
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                if (showBreakdown) Icons.Default.KeyboardArrowUp else Icons.Default.Info,
                contentDescription = if (showBreakdown) "Hide breakdown" else "Show breakdown",
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )
        }
        Text(
            "₹${String.format(Locale.getDefault(), "%,.0f", state.extraMoney)}", 
            style = MaterialTheme.typography.displayMedium, 
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )
        
        // Expandable breakdown
        if (showBreakdown) {
            Spacer(modifier = Modifier.height(8.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    BreakdownRow("Income", state.totalIncome, IncomeGreen, isAddition = true)
                    BreakdownRow("Fixed Expenses", state.totalFixedExpenses, ExpenseRed, isAddition = false)
                    BreakdownRow("Variable Expenses", state.totalVariableExpenses, PendingOrange, isAddition = false)
                    BreakdownRow("Investments", state.totalInvestments, TransferBlue, isAddition = false)
                    BreakdownRow("Vehicle", state.totalVehicleExpenses, VehiclePurple, isAddition = false)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Remaining", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "₹${String.format(Locale.getDefault(), "%,.0f", state.extraMoney)}",
                            fontWeight = FontWeight.Bold,
                            color = if (state.extraMoney >= 0) IncomeGreen else ExpenseRed
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BreakdownRow(label: String, amount: Double, color: Color, isAddition: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            "${if (isAddition) "+" else "−"} $label",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            "₹${String.format(Locale.getDefault(), "%,.0f", amount)}",
            style = MaterialTheme.typography.bodySmall,
            color = color
        )
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
fun DateHeader(date: LocalDate) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(
            date.format(DateTimeFormatter.ofPattern("EEEE, dd MMM")), 
            style = MaterialTheme.typography.titleSmall, 
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
fun AccountFilterDropdown(
    accounts: List<com.saikumar.expensetracker.data.entity.UserAccount>,
    selectedAccounts: Set<String>,
    transactions: List<TransactionWithCategory> = emptyList(), // AUDIT: Added for count display
    onToggle: (String) -> Unit,
    onClearAll: () -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Box(modifier = Modifier.padding(bottom = 8.dp)) {
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.FilterList, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                if (selectedAccounts.isEmpty()) "Filter Accounts" 
                else "${selectedAccounts.size} Account(s) Selected"
            )
        }
        
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth(0.9f)
        ) {
            if (accounts.isEmpty()) {
                DropdownMenuItem(
                    text = { Text("No accounts detected yet", style = MaterialTheme.typography.bodySmall) },
                    onClick = { }
                )
            } else {
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
                    HorizontalDivider()
                }
                
                accounts.forEach { account ->
                    val typeLabel = if (account.accountType == com.saikumar.expensetracker.data.entity.AccountType.CREDIT_CARD) "CC" else "A/c"
                    // Count transactions for this account
                    val txnCount = transactions.count { txn ->
                        txn.transaction.fullSmsBody?.contains(account.accountNumberLast4) == true ||
                        txn.transaction.accountNumberLast4 == account.accountNumberLast4
                    }
                    val label = "${account.bankName} $typeLabel XX${account.accountNumberLast4} ($txnCount)"
                    
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
fun DashboardChart(state: DashboardUiState) {
    val total = state.totalFixedExpenses + state.totalVariableExpenses + state.totalVehicleExpenses + state.totalInvestments
    if (total <= 0) return
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Spending Breakdown", style = MaterialTheme.typography.titleSmall)
            Spacer(modifier = Modifier.height(8.dp))
            AndroidView(
                factory = { context ->
                    PieChart(context).apply { 
                        description.isEnabled = false
                        legend.isEnabled = true
                        setUsePercentValues(true)
                        holeRadius = 40f
                        transparentCircleRadius = 45f
                    }
                },
                modifier = Modifier.fillMaxWidth().height(220.dp),
                update = { chart ->
                    val entries = mutableListOf<PieEntry>()
                    if (state.totalFixedExpenses > 0) entries.add(PieEntry(state.totalFixedExpenses.toFloat(), "Fixed"))
                    if (state.totalVariableExpenses > 0) entries.add(PieEntry(state.totalVariableExpenses.toFloat(), "Variable"))
                    if (state.totalVehicleExpenses > 0) entries.add(PieEntry(state.totalVehicleExpenses.toFloat(), "Vehicle"))
                    if (state.totalInvestments > 0) entries.add(PieEntry(state.totalInvestments.toFloat(), "Invest"))
                    
                    val dataSet = PieDataSet(entries, "").apply { 
                        colors = listOf(
                            ExpenseRed.toArgb(),      // Fixed - Red
                            PendingOrange.toArgb(),   // Variable - Orange
                            VehiclePurple.toArgb(),   // Vehicle - Purple
                            TransferBlue.toArgb()     // Investment - Blue
                        )
                        setDrawValues(true)
                        valueTextSize = 12f
                        valueTextColor = android.graphics.Color.WHITE
                    }
                    chart.data = PieData(dataSet)
                    chart.invalidate()
                }
            )
        }
    }
}

@Composable
fun TransactionItem(item: TransactionWithCategory, linkType: LinkType? = null, onClick: () -> Unit) {
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

    // AUDIT FIX: Flag uncategorized and unverified income transactions for review
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
        isStatement -> Icons.Default.ReceiptLong
        isInvestment -> Icons.Default.TrendingUp  // Investment icon (check before transfer!)
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
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable(onClick = onClick),
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
                    item.transaction.merchantName ?: item.transaction.note ?: item.category.name, 
                    style = MaterialTheme.typography.bodySmall,
                    color = subtitleColor,
                    maxLines = 1
                )
            }
            Text(formatAmount(item.transaction.amountPaisa), color = amountColor, fontWeight = FontWeight.Bold)
        }
    }
}
