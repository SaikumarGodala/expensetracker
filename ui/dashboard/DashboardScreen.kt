package com.saikumar.expensetracker.ui.dashboard

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.utils.ColorTemplate
import com.saikumar.expensetracker.data.db.TransactionWithCategory
import com.saikumar.expensetracker.data.entity.*
import com.saikumar.expensetracker.util.CategoryIcons
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Format paisa amount to rupee display string
 */
private fun formatAmount(paisa: Long): String {
    val rupees = paisa / 100.0
    return "₹${String.format("%,.0f", rupees)}"
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
        EditTransactionDialog(
            transaction = editingTransaction!!,
            categories = uiState.categories,
            onDismiss = { editingTransaction = null },
            onConfirm = { categoryId, note, isSelfTransfer, accountType, isIncomeManuallyIncluded, updateSimilar, manualClassification ->
                viewModel.updateTransactionDetails(editingTransaction!!.transaction, categoryId, note, isSelfTransfer, accountType, isIncomeManuallyIncluded, updateSimilar, manualClassification)
                editingTransaction = null
            },
            onAddCategory = { name, type ->
                viewModel.addCategory(name, type)
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
                    CycleSelector(uiState, onPrevious = { viewModel.previousCycle() }, onNext = { viewModel.nextCycle() }, onAdjust = { showDateRangePicker = true })
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
            item { Box(modifier = Modifier.padding(16.dp)) { DashboardChart(uiState) } }
            item {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { viewModel.onSearchQueryChanged(it) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    placeholder = { Text("Search transactions") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true
                )
            }
            val groupedTransactions = uiState.transactions.groupBy { timestampToLocalDate(it.transaction.timestamp) }
            groupedTransactions.forEach { (date, transactions) ->
                stickyHeader { DateHeader(date, 0.0) }
                items(transactions, key = { it.transaction.id }) { transaction ->
                    TransactionItem(transaction, onClick = { editingTransaction = transaction })
                }
            }
        }
    }
}

@Composable
fun CycleSelector(state: DashboardUiState, onPrevious: () -> Unit, onNext: () -> Unit, onAdjust: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrevious) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null) }
        // Month name derived from END date (reflects current working month)
        Text(text = state.cycleRange?.endDate?.format(DateTimeFormatter.ofPattern("MMMM yyyy")) ?: "...", modifier = Modifier.clickable { onAdjust() }, fontWeight = FontWeight.SemiBold)
        IconButton(onClick = onNext) { Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null) }
    }
}

@Composable
fun HeroBalanceCard(state: DashboardUiState) {
    Column {
        Text("Remaining Money", style = MaterialTheme.typography.labelLarge)
        Text("₹${String.format("%,.0f", state.extraMoney)}", style = MaterialTheme.typography.displayMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun SummaryRowList(state: DashboardUiState, onCategoryClick: (CategoryType) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        item { SummaryChip("Income", state.totalIncome, Color(0xFF4CAF50)) { onCategoryClick(CategoryType.INCOME) } }
        item { SummaryChip("Fixed", state.totalFixedExpenses, Color(0xFFF44336)) { onCategoryClick(CategoryType.FIXED_EXPENSE) } }
        item { SummaryChip("Variable", state.totalVariableExpenses, Color(0xFFFF9800)) { onCategoryClick(CategoryType.VARIABLE_EXPENSE) } }
        item { SummaryChip("Invest", state.totalInvestments, Color(0xFF2196F3)) { onCategoryClick(CategoryType.INVESTMENT) } }
        item { SummaryChip("Vehicle", state.totalVehicleExpenses, Color(0xFF9C27B0)) { onCategoryClick(CategoryType.VEHICLE) } }
    }
}

@Composable
fun SummaryChip(label: String, amount: Double, color: Color, onClick: () -> Unit) {
    Surface(shape = MaterialTheme.shapes.medium, color = color.copy(alpha = 0.1f), modifier = Modifier.clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text("₹${String.format("%,.0f", amount)}", fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
fun DateHeader(date: LocalDate, dailyTotal: Double) {
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
        Text(date.format(DateTimeFormatter.ofPattern("dd MMM yyyy")), fontWeight = FontWeight.Bold)
    }
}

@Composable
fun DashboardChart(state: DashboardUiState) {
    val total = state.totalFixedExpenses + state.totalVariableExpenses + state.totalVehicleExpenses + state.totalInvestments
    if (total <= 0) return
    
    Card(modifier = Modifier.fillMaxWidth().height(250.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Spending Breakdown", style = MaterialTheme.typography.titleSmall)
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
                modifier = Modifier.fillMaxSize(),
                update = { chart ->
                    val entries = mutableListOf<PieEntry>()
                    if (state.totalFixedExpenses > 0) entries.add(PieEntry(state.totalFixedExpenses.toFloat(), "Fixed"))
                    if (state.totalVariableExpenses > 0) entries.add(PieEntry(state.totalVariableExpenses.toFloat(), "Variable"))
                    if (state.totalVehicleExpenses > 0) entries.add(PieEntry(state.totalVehicleExpenses.toFloat(), "Vehicle"))
                    if (state.totalInvestments > 0) entries.add(PieEntry(state.totalInvestments.toFloat(), "Invest"))
                    
                    val dataSet = PieDataSet(entries, "").apply { 
                        colors = listOf(
                            android.graphics.Color.parseColor("#F44336"), // Fixed - Red
                            android.graphics.Color.parseColor("#FF9800"), // Variable - Orange
                            android.graphics.Color.parseColor("#9C27B0"), // Vehicle - Purple
                            android.graphics.Color.parseColor("#2196F3")  // Investment - Blue
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
fun TransactionItem(item: TransactionWithCategory, onClick: () -> Unit) {
    val transactionType = item.transaction.transactionType
    val isTransfer = transactionType == TransactionType.TRANSFER
    val isIncome = transactionType == TransactionType.INCOME
    val isLiabilityPayment = transactionType == TransactionType.LIABILITY_PAYMENT
    val categoryIcon = CategoryIcons.getIcon(item.category.name)
    
    val containerColor = when {
        isTransfer -> MaterialTheme.colorScheme.surfaceVariant
        isLiabilityPayment -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }
    
    val amountColor = when {
        isTransfer -> Color.Gray
        isLiabilityPayment -> MaterialTheme.colorScheme.tertiary
        isIncome -> Color(0xFF4CAF50)
        else -> Color(0xFFEF5350)
    }
    
    val icon = when {
        isTransfer -> Icons.Default.SwapHoriz
        isLiabilityPayment -> Icons.Default.CreditCard
        else -> categoryIcon
    }
    
    val displayName = when {
        isTransfer -> "Self Transfer"
        isLiabilityPayment -> "CC Payment"
        else -> item.category.name
    }
    
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable(onClick = onClick), 
        colors = CardDefaults.cardColors(containerColor = containerColor)
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTransactionDialog(
    transaction: TransactionWithCategory, 
    categories: List<Category>, 
    onDismiss: () -> Unit, 
    onConfirm: (Long, String, Boolean, AccountType, Boolean, Boolean, String?) -> Unit,
    onAddCategory: ((String, CategoryType) -> Unit)? = null
) {
    var selectedCategory by remember { mutableStateOf(transaction.category) }
    var note by remember { mutableStateOf(transaction.transaction.note ?: "") }
    var isSelfTransfer by remember { mutableStateOf(transaction.transaction.isSelfTransfer) }
    var isIncomeManuallyIncluded by remember { mutableStateOf(transaction.transaction.isIncomeManuallyIncluded) }
    var accountType by remember { mutableStateOf(transaction.transaction.accountType) }
    var applyToSimilar by remember { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    var manualClassification by remember { mutableStateOf(transaction.transaction.manualClassification) }
    
    val merchantKeyword = transaction.transaction.merchantName
    val isUnknownCategory = selectedCategory.name.contains("Unknown", ignoreCase = true)

    if (showAddCategoryDialog && onAddCategory != null) {
        AddCategoryDialog(
            onDismiss = { showAddCategoryDialog = false },
            onConfirm = { name, type ->
                onAddCategory(name, type)
                showAddCategoryDialog = false
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Transaction Details") },
        text = {
            Box(modifier = Modifier.heightIn(max = 450.dp)) {
                val dialogScrollState = rememberScrollState()
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.verticalScroll(dialogScrollState)
                ) {
                // Amount display - convert paisa to rupees
                Text("Amount: ${formatAmount(transaction.transaction.amountPaisa)}", style = MaterialTheme.typography.titleMedium)
                
                Text("Transaction Type: ${transaction.transaction.transactionType.name}")
                Text("Detected as Salary: ${if (transaction.transaction.isSalaryCredit) "YES" else "NO"}")
                
                // Show merchant if available
                if (merchantKeyword != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Merchant: $merchantKeyword",
                            modifier = Modifier.padding(8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                
                // SMS Context/Snippet display - useful for debugging and trust
                val smsSnippet = transaction.transaction.smsSnippet
                if (!smsSnippet.isNullOrBlank() && transaction.transaction.source == TransactionSource.SMS) {
                    var isExpanded by remember { mutableStateOf(false) }
                    
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth().clickable { isExpanded = !isExpanded }
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "📱 Original Context",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (isExpanded || smsSnippet.length <= 80) smsSnippet else smsSnippet.take(77) + "...",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = if (isExpanded) Int.MAX_VALUE else 2
                            )
                        }
                    }
                }
                
                // Unknown category alert
                if (isUnknownCategory) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "⚠️ This transaction could not be auto-categorized. Please select a category below.",
                            modifier = Modifier.padding(8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                
                // Category dropdown
                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = !categoryExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedCategory.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier.fillMaxWidth().menuAnchor(type = MenuAnchorType.PrimaryEditable, enabled = true)
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { 
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            CategoryIcons.getIcon(category.name), 
                                            contentDescription = null,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(category.name)
                                    }
                                },
                                onClick = {
                                    selectedCategory = category
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }
                
                // Add New Category button
                if (onAddCategory != null) {
                    TextButton(
                        onClick = { showAddCategoryDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add New Category")
                    }
                }
                
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth())
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = isSelfTransfer, onCheckedChange = { isSelfTransfer = it; if (it) manualClassification = "NEUTRAL" })
                    Text("Mark as Self-Transfer")
                }
                
                // Manual Classification Override (only if not self-transfer)
                if (!isSelfTransfer) {
                    Text("Classification Override:", style = MaterialTheme.typography.labelSmall)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = manualClassification == null,
                            onClick = { manualClassification = null },
                            label = { Text("Auto") }
                        )
                        FilterChip(
                            selected = manualClassification == "INCOME",
                            onClick = { manualClassification = "INCOME"; isIncomeManuallyIncluded = true },
                            label = { Text("Income") }
                        )
                        FilterChip(
                            selected = manualClassification == "EXPENSE",
                            onClick = { manualClassification = "EXPENSE"; isIncomeManuallyIncluded = false },
                            label = { Text("Expense") }
                        )
                        FilterChip(
                            selected = manualClassification == "NEUTRAL",
                            onClick = { manualClassification = "NEUTRAL"; isIncomeManuallyIncluded = false },
                            label = { Text("Neutral") }
                        )
                    }
                }
                
                // Apply to similar transactions option - with smart matching
                // Show if: merchant keyword exists AND (category changed OR has matchable pattern)
                val smsContent = transaction.transaction.smsSnippet ?: ""
                
                // Extract UPI ID
                val upiId = if (smsContent.contains("@") && !smsContent.contains("@gmail") && !smsContent.contains("@yahoo")) {
                    val regex = Regex("([a-zA-Z0-9._-]+@[a-zA-Z]+)")
                    regex.find(smsContent)?.value?.lowercase()
                } else null
                
                // Extract NEFT bank code (e.g., DEUTN52025... → DEUT)
                val neftBankCode = if (smsContent.contains("NEFT", ignoreCase = true)) {
                    val neftRefRegex = Regex("(?i)NEFT[\\s-]+(?:Cr[\\s-]+)?([A-Z0-9]+)")
                    val neftRef = neftRefRegex.find(smsContent)?.groupValues?.getOrNull(1)
                    neftRef?.take(4)?.uppercase() // First 4 letters = bank code
                } else null
                
                val hasMatchablePattern = upiId != null || neftBankCode != null
                val matchPattern = upiId ?: neftBankCode ?: merchantKeyword?.uppercase() ?: ""
                val matchType = when {
                    upiId != null -> "UPI ID"
                    neftBankCode != null -> "NEFT Source"
                    merchantKeyword != null -> "Merchant"
                    else -> "Amount + Date"  // Fallback for transactions without clear pattern
                }
                
                // Show Apply to Similar whenever category is changed
                // Even if no pattern is found, user can still choose to apply
                if (selectedCategory.id != transaction.category.id) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    Surface(
                        color = if (applyToSimilar) 
                            MaterialTheme.colorScheme.primaryContainer 
                        else 
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Checkbox(
                                    checked = applyToSimilar, 
                                    onCheckedChange = { applyToSimilar = it }
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "🔄 Apply to similar transactions",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        "Match by $matchType: $matchPattern",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            
                            if (applyToSimilar) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.surface,
                                    shape = MaterialTheme.shapes.extraSmall,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp),
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
                                            "All matching transactions will be updated to \"${selectedCategory.name}\"",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                Text("This transaction will not affect income totals unless marked.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(selectedCategory.id, note, isSelfTransfer, accountType, isIncomeManuallyIncluded, applyToSimilar, manualClassification) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
