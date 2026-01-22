package com.saikumar.expensetracker.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import com.saikumar.expensetracker.data.db.TransactionWithCategory
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
fun SalaryHistoryScreen(viewModel: SalaryHistoryViewModel, onNavigateBack: () -> Unit) {
    val transactions by viewModel.salaryTransactions.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Salary History") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (transactions.isEmpty()) {
            Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("No salary credits detected yet.")
            }
        } else {
            var selectedRange by remember { mutableStateOf("All") }
            var selectedTransaction by remember { mutableStateOf<TransactionWithCategory?>(null) }
            
            if (selectedTransaction != null) {
                val txn = selectedTransaction!!.transaction
                AlertDialog(
                    onDismissRequest = { selectedTransaction = null },
                    title = { Text("Salary Credit Details") },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Date: ${timestampToDateString(txn.timestamp, "dd MMM yyyy, hh:mm a")}")
                            if (!txn.merchantName.isNullOrBlank()) {
                                Text("Sender: ${txn.merchantName}", fontWeight = FontWeight.SemiBold)
                            }
                            HorizontalDivider()
                            Text(
                                text = txn.fullSmsBody ?: "No message details available.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { selectedTransaction = null }) { Text("Close") }
                    }
                )
            }
            
            // Filter transactions based on selected time range
            val filteredTransactions = remember(transactions, selectedRange) {
                val cutoffTime = when (selectedRange) {
                    "3M" -> System.currentTimeMillis() - (90L * 24 * 60 * 60 * 1000)
                    "6M" -> System.currentTimeMillis() - (180L * 24 * 60 * 60 * 1000)
                    "1Y" -> System.currentTimeMillis() - (365L * 24 * 60 * 60 * 1000)
                    else -> 0L
                }
                transactions.filter { it.transaction.timestamp >= cutoffTime }
            }
            
            // Prepare chart data
            val chartEntries = remember(filteredTransactions) {
                filteredTransactions
                    .sortedBy { it.transaction.timestamp }
                    .mapIndexed { index, item ->
                        com.github.mikephil.charting.data.Entry(
                            index.toFloat(),
                            (item.transaction.amountPaisa / 100f)
                        )
                    }
            }
            
            val xAxisLabels = remember(filteredTransactions) {
                filteredTransactions
                    .sortedBy { it.transaction.timestamp }
                    .map { timestampToDateString(it.transaction.timestamp, "MMM yy") }
            }
            
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Time range filter chips
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        listOf("3M", "6M", "1Y", "All").forEach { range ->
                            FilterChip(
                                selected = selectedRange == range,
                                onClick = { selectedRange = range },
                                label = { Text(range) }
                            )
                        }
                    }
                }
                
                // Salary trend chart
                if (chartEntries.isNotEmpty()) {
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            )
                        ) {
                            Column(modifier = Modifier.padding(16.dp)) {
                                Text(
                                    "Salary Trend",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                                com.saikumar.expensetracker.ui.components.LineChartComposable(
                                    entries = chartEntries,
                                    xAxisLabels = xAxisLabels,
                                    lineColor = Color(0xFF4CAF50),
                                    fillColor = Color(0xFF4CAF50)
                                )
                            }
                        }
                    }
                }
                
                // Transaction list
                items(filteredTransactions) { item ->
                    SalaryItem(item, onClick = { selectedTransaction = item })
                }
            }
        }
    }
}

@Composable
fun SalaryItem(item: TransactionWithCategory, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = timestampToDateString(item.transaction.timestamp, "dd MMM yyyy"),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = formatAmount(item.transaction.amountPaisa),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF4CAF50)
                )
            }
            if (!item.transaction.note.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = item.transaction.note ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
