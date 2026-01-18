package com.saikumar.expensetracker.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.github.mikephil.charting.charts.BarChart
import com.github.mikephil.charting.charts.PieChart
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.*
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.utils.ColorTemplate
import com.saikumar.expensetracker.data.entity.CategoryType
import com.saikumar.expensetracker.util.CategoryIcons
import java.time.Instant
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Monthly Overview") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.previousCycle() }) {
                        Icon(Icons.Default.KeyboardArrowLeft, "Previous")
                    }
                    IconButton(onClick = { viewModel.nextCycle() }) {
                        Icon(Icons.Default.KeyboardArrowRight, "Next")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Cycle date range
            item {
                uiState.cycleRange?.let { range ->
                    Text(
                        "${range.startDate.format(DateTimeFormatter.ofPattern("dd MMM"))} - ${range.endDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
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

            // Pie chart - Top Categories (bigger)
            item {
                Card(modifier = Modifier.fillMaxWidth().height(320.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Top Categories", style = MaterialTheme.typography.titleSmall)
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
                                val allCategories = uiState.categoryBreakdown.values.flatten()
                                    .filter { it.category.type != CategoryType.INCOME }
                                    .sortedByDescending { it.total }
                                    .take(6)
                                
                                if (allCategories.isNotEmpty()) {
                                    val entries = allCategories.map { summary ->
                                        PieEntry(summary.total.toFloat(), summary.category.name.take(10))
                                    }
                                    
                                    val dataSet = PieDataSet(entries, "").apply {
                                        colors = ColorTemplate.MATERIAL_COLORS.toList()
                                        setDrawValues(true)
                                        valueTextSize = 10f
                                        valueTextColor = android.graphics.Color.WHITE
                                    }
                                    
                                    chart.data = PieData(dataSet)
                                    chart.invalidate()
                                }
                            }
                        )
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
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(title, style = MaterialTheme.typography.labelSmall)
            Text(
                "₹${String.format("%,.0f", amount)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            modifier = Modifier.size(12.dp),
            color = color,
            shape = MaterialTheme.shapes.extraSmall
        ) {}
        Spacer(modifier = Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun CategorySummaryCard(summary: CategorySummary) {
    var expanded by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    CategoryIcons.getIcon(summary.category.name),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(summary.category.name, fontWeight = FontWeight.Medium)
                    Text(
                        "${summary.transactions.size} transactions",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Text(
                    "₹${String.format("%,.0f", summary.total)}",
                    fontWeight = FontWeight.Bold
                )
                Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = null
                )
            }
            
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    summary.transactions.take(5).forEach { txn ->
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                timestampToDateString(txn.transaction.timestamp, "dd MMM"),
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                formatAmount(txn.transaction.amountPaisa),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                    if (summary.transactions.size > 5) {
                        Text(
                            "...and ${summary.transactions.size - 5} more",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
