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
            onDismissRequest = { },
            confirmButton = {
                TextButton(onClick = {
                    if (dateRangePickerState.selectedStartDateMillis != null && dateRangePickerState.selectedEndDateMillis != null) {
                        val start = Instant.ofEpochMilli(dateRangePickerState.selectedStartDateMillis!!).atZone(ZoneId.systemDefault()).toLocalDate()
                        val end = Instant.ofEpochMilli(dateRangePickerState.selectedEndDateMillis!!).atZone(ZoneId.systemDefault()).toLocalDate()
                        viewModel.setCustomCycle(start, end)
                    }
                }) { Text("Apply") }
            }
        ) { DateRangePicker(state = dateRangePickerState) }
    }
    
    Scaffold(
        topBar = { TopAppBar(title = { Text("Dashboard", fontWeight = FontWeight.Bold) }) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onNavigateToAdd, containerColor = MaterialTheme.colorScheme.primary) {
                Icon(Icons.Default.Add, contentDescription = null)
                Text("Add Transaction")
            }
        }
    ) { padding ->
        LazyColumn(modifier = Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                    CycleSelector(uiState, onPrevious = { viewModel.previousCycle() }, onNext = { viewModel.nextCycle() }, onAdjust = { })
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
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    placeholder = { Text("Search transactions") },
                )
            }
            
            // STATEMENTS SECTION
            if (uiState.statements.isNotEmpty()) {
                item {
                    Text(
                        "Statements", 
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), 
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                items(uiState.statements, key = { it.transaction.id }) { transaction ->
                    TransactionItem(transaction, onClick = { editingTransaction = transaction })
                }
                item { Spacer(modifier = Modifier.height(16.dp)) }
                item { HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp)) }
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
        IconButton(onClick = onPrevious) { Icon(Icons.Default.KeyboardArrowLeft, null) }
        // Month name derived from END date (reflects current working month)
        Text(text = state.cycleRange?.endDate?.format(DateTimeFormatter.ofPattern("MMMM yyyy")) ?: "...", modifier = Modifier.clickable { onAdjust() }, fontWeight = FontWeight.SemiBold)
        IconButton(onClick = onNext) { Icon(Icons.Default.KeyboardArrowRight, null) }
    }
}

@Composable
fun HeroBalanceCard(state: DashboardUiState) {
    Column {
        Text("Remaining Money", style = MaterialTheme.typography.labelLarge)
        Text("₹${String.format(Locale.getDefault(), "%,.0f", state.extraMoney)}", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
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
    Surface(shape = MaterialTheme.shapes.medium, color = color.copy(alpha = 0.1f), modifier = Modifier.clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text("₹${String.format(Locale.getDefault(), "%,.0f", amount)}", fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
fun DateHeader(date: LocalDate) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(date.format(DateTimeFormatter.ofPattern("dd MMM yyyy")), fontWeight = FontWeight.Bold)
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

    val categoryIcon = CategoryIcons.getIcon(item.category.name)
    
    // Complete TransactionType handling with distinct colors
    // IMPORTANT: isInvestment checked BEFORE isTransfer so investments show as blue
    val containerColor = when {
        isIgnored -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        isPending -> HighlightBackground  // Amber lightest
        isStatement -> MaterialTheme.colorScheme.surface // Clean/White for Statement
        isInvestment -> InvestmentBlueLight  // Blue tint for ALL Investments (check before transfer!)
        isTransfer -> MaterialTheme.colorScheme.surfaceVariant
        isLiabilityPayment -> MaterialTheme.colorScheme.tertiaryContainer
        isRefund -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
        isCashback -> CashbackGold  // Golden/yellow tint
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    
    val amountColor = when {
        isIgnored -> Color.Gray.copy(alpha = 0.5f)
        isPending -> PendingText  // Amber
        isStatement -> MaterialTheme.colorScheme.primary
        isInvestment -> InvestmentBlueText  // Blue for ALL Investments (check before transfer!)
        isTransfer -> Color.Gray
        isLiabilityPayment -> MaterialTheme.colorScheme.tertiary
        isRefund -> UndoGreen  // Light green (money back)
        isCashback -> CashbackText  // Gold/amber
        isIncome -> IncomeGreen
        else -> ExpenseRedLight
    }
    
    val icon = when {
        isIgnored -> Icons.Default.VisibilityOff
        isPending -> Icons.Default.Schedule
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
        isStatement -> "CC Statement"
        isInvestment -> item.category.name  // Show actual category (Mutual Funds, RD, etc.)
        isTransfer -> item.category.name // Show actual category (P2P Transfers, Self Transfer, etc.)
        isLiabilityPayment -> "CC Payment"
        isRefund -> "Refund / Reversal"
        isCashback -> "Cashback"
        else -> item.category.name
    }
    
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp), // Softer corners
        border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)) // Glassy border
    ) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = amountColor)
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(displayName, fontWeight = FontWeight.Bold)
                Text(
                    item.transaction.merchantName ?: item.transaction.note ?: item.category.name, 
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )
            }
            Text(formatAmount(item.transaction.amountPaisa), color = amountColor, fontWeight = FontWeight.Bold)
        }
    }
}
