package com.saikumar.expensetracker.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.utils.ColorTemplate
import com.saikumar.expensetracker.data.entity.CategoryType
import com.saikumar.expensetracker.util.CategoryIcons
import com.saikumar.expensetracker.data.db.TransactionWithCategory
import com.saikumar.expensetracker.ui.common.TransactionEditDialog
import com.saikumar.expensetracker.ui.common.CommonDateRangePicker
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Format paisa amount to rupee display string
 */
private fun formatAmount(paisa: Long): String {
    val rupees = paisa / 100.0
    return "₹${String.format(Locale.getDefault(), "%,.0f", rupees)}"
}

/**
 * Convert timestamp to formatted date string
 */
private fun timestampToDateString(timestamp: Long, pattern: String): String {
    return Instant.ofEpochMilli(timestamp)
        .atZone(ZoneId.systemDefault())
        .format(DateTimeFormatter.ofPattern(pattern))
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthlyOverviewScreen(
    viewModel: MonthlyOverviewViewModel,
    onNavigateBack: () -> Unit,
    onCategoryClick: (CategoryType, Long, Long) -> Unit = { _, _, _ -> }
) {
    val uiState by viewModel.uiState.collectAsState()
    val allCategories by viewModel.allCategories.collectAsState(initial = emptyList())
    var editingTransaction by remember { mutableStateOf<TransactionWithCategory?>(null) }
    var showDateRangePicker by remember { mutableStateOf(false) }

    if (showDateRangePicker) {
        val initialStart = uiState.cycleRange?.startDate?.toLocalDate()
        val initialEnd = uiState.cycleRange?.endDate?.toLocalDate()
        
        CommonDateRangePicker(
            initialStartDate = initialStart,
            initialEndDate = initialEnd,
            onDismiss = { showDateRangePicker = false },
            onConfirm = { start, end ->
                viewModel.setCustomRange(start, end)
                showDateRangePicker = false
            }
        )
    }
    
    if (editingTransaction != null) {
        TransactionEditDialog(
            transaction = editingTransaction!!,
            categories = allCategories,
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

    Scaffold(
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Cycle date range with navigation
            item {
                uiState.cycleRange?.let { range ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { viewModel.previousCycle() }) {
                            Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Previous")
                        }
                        
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .weight(1f)
                                .clickable { showDateRangePicker = true }
                                .padding(8.dp)
                        ) {
                            Text(
                                "${range.startDate.format(DateTimeFormatter.ofPattern("dd MMM"))} - ${range.endDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Text("Tap to change date range", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        
                        IconButton(onClick = { viewModel.nextCycle() }) {
                            Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Next")
                        }
                    }
                }
                
                // Account Filter Dropdown
                AccountFilterDropdown(
                    accounts = uiState.detectedAccounts,
                    selectedAccounts = uiState.selectedAccounts,
                    onToggle = { viewModel.toggleAccountFilter(it) },
                    onClearAll = { viewModel.clearAccountFilter() }
                )

                Spacer(modifier = Modifier.height(16.dp))
            }

            // Summary cards - clickable to show transactions
            item {
                val startMillis = uiState.cycleRange?.startDate?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli() ?: 0L
                val endMillis = uiState.cycleRange?.endDate?.atZone(ZoneId.systemDefault())?.toInstant()?.toEpochMilli() ?: 0L
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    SummaryCard(
                        title = "Income",
                        amount = uiState.income,
                        color = Color(0xFF4CAF50),
                        modifier = Modifier.weight(1f),
                        onClick = { onCategoryClick(CategoryType.INCOME, startMillis, endMillis) }
                    )
                    SummaryCard(
                        title = "Expenses",
                        amount = uiState.expenses,
                        color = Color(0xFFF44336),
                        modifier = Modifier.weight(1f),
                        onClick = { onCategoryClick(CategoryType.VARIABLE_EXPENSE, startMillis, endMillis) }
                    )
                    SummaryCard(
                        title = "Invest",
                        amount = uiState.investments,
                        color = Color(0xFF2196F3),
                        modifier = Modifier.weight(1f),
                        onClick = { onCategoryClick(CategoryType.INVESTMENT, startMillis, endMillis) }
                    )
                }
            }

            // Pie chart - Top Categories
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Categories", style = MaterialTheme.typography.titleSmall)
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        // Calculate data for chart and list - show ALL categories
                        val allCategories = uiState.categoryBreakdown.values.flatten()
                            .filter { it.category.type != CategoryType.INCOME }
                            .sortedByDescending { it.total }
                            
                        // Define stable colors to match between chart and list
                        val chartColors = ColorTemplate.MATERIAL_COLORS.toList()
                        
                        AndroidView(
                            factory = { context -> 
                                PieChart(context).apply {
                                    description.isEnabled = false
                                    
                                    // Disable all internal labels to clean up the UI
                                    legend.isEnabled = false
                                    setDrawEntryLabels(false)
                                    setUsePercentValues(true)
                                    
                                    // Styling
                                    holeRadius = 55f
                                    transparentCircleRadius = 60f
                                    setHoleColor(android.graphics.Color.TRANSPARENT)
                                }
                            },
                            modifier = Modifier.fillMaxWidth().height(220.dp), // Reduced height slightly as labels are gone
                            update = { chart ->
                                if (allCategories.isNotEmpty()) {
                                    val entries = allCategories.map { summary ->
                                        PieEntry(summary.total.toFloat(), summary.category.name)
                                    }
                                    
                                    val dataSet = PieDataSet(entries, "").apply {
                                        colors = chartColors
                                        setDrawValues(false) // Hide values on slices
                                        sliceSpace = 2f
                                    }
                                    
                                    chart.data = PieData(dataSet)
                                    chart.invalidate()
                                }
                            }
                        )
                        
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        // Custom Detailed Legend List
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            allCategories.forEachIndexed { index, summary ->
                                val colorInt = chartColors[index % chartColors.size]
                                val composeColor = Color(colorInt)
                                
                                InteractiveCategoryLegendItem(
                                    summary = summary,
                                    color = composeColor,
                                    onTransactionClick = { editingTransaction = it }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SummaryCard(title: String, amount: Double, color: Color, modifier: Modifier = Modifier, onClick: () -> Unit = {}) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f)),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall)
            Text(
                "₹${String.format(Locale.getDefault(), "%,.0f", amount)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
fun InteractiveCategoryLegendItem(
    summary: CategorySummary, 
    color: Color,
    onTransactionClick: (TransactionWithCategory) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    // Main Container
    Surface(
        color = Color.Transparent, // Blend with parent card
        shape = MaterialTheme.shapes.small,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // Main Row (Always Visible)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(vertical = 12.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Color Indicator
                Surface(
                    modifier = Modifier.size(16.dp),
                    color = color,
                    shape = MaterialTheme.shapes.extraSmall
                ) {}
                
                Spacer(modifier = Modifier.width(12.dp))
                
                // Category Name & Transaction Count
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        summary.category.name, 
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        "${summary.transactions.size} transactions",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                // Amount
                Text(
                    "₹${String.format(Locale.getDefault(), "%,.0f", summary.total)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (expanded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                
                Spacer(modifier = Modifier.width(8.dp))
                
                // Expand/Collapse Icon
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            // Expanded Details (Transactions List - ALL, no limit)
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 28.dp, end = 4.dp, bottom = 12.dp)
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, modifier = Modifier.padding(bottom = 8.dp))
                    
                    summary.transactions.forEach { txn ->
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onTransactionClick(txn) }.padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    txn.transaction.merchantName ?: txn.transaction.note ?: "Unknown",
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 1,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    timestampToDateString(txn.transaction.timestamp, "dd MMM"),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            
                            Text(
                                formatAmount(txn.transaction.amountPaisa),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}
