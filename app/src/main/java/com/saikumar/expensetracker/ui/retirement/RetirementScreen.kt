package com.saikumar.expensetracker.ui.retirement

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.saikumar.expensetracker.data.entity.RetirementBalance
import java.text.NumberFormat
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RetirementScreen(
    onBack: () -> Unit,
    viewModel: RetirementViewModel = viewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val formatter = remember { NumberFormat.getCurrencyInstance(Locale.Builder().setLanguage("en").setRegion("IN").build()) }
    var showCombined by remember { mutableStateOf(true) }
    var selectedBalance by remember { mutableStateOf<RetirementBalance?>(null) }
    
    if (selectedBalance != null) {
        val bal = selectedBalance!!
        AlertDialog(
            onDismissRequest = { selectedBalance = null },
            title = { Text(if (bal.type == com.saikumar.expensetracker.data.entity.RetirementType.EPF) "EPF Update" else "NPS Update") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Month: ${bal.month}")
                    if (!bal.sender.isNullOrBlank()) {
                        Text("Sender: ${bal.sender}", fontWeight = FontWeight.SemiBold)
                    }
                    HorizontalDivider()
                    Text(
                        text = bal.smsBody ?: "No message details available.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedBalance = null }) { Text("Close") }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Retirement Balances") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // EPF Card
            item {
                BalanceCard(
                    title = "EPF Balance",
                    balance = state.epfBalance,
                    formatter = formatter,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // NPS Card
            item {
                BalanceCard(
                    title = "NPS Balance",
                    balance = state.npsBalance,
                    formatter = formatter,
                    color = MaterialTheme.colorScheme.secondary
                )
            }

            // Chart view toggle
            if (state.epfHistory.isNotEmpty() || state.npsHistory.isNotEmpty()) {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = showCombined,
                            onClick = { showCombined = true },
                            label = { Text("Combined") }
                        )
                        FilterChip(
                            selected = !showCombined,
                            onClick = { showCombined = false },
                            label = { Text("Separate") }
                        )
                    }
                }

                // Growth Chart
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                "Monthly Contribution Trend",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            if (showCombined) {
                                // Combined chart
                                val combinedData = prepareRetirementChartData(
                                    state.epfHistory,
                                    state.npsHistory
                                )
                                if (combinedData.entries.isNotEmpty()) {
                                    com.saikumar.expensetracker.ui.components.LineChartComposable(
                                        entries = combinedData.entries,
                                        xAxisLabels = combinedData.labels,
                                        lineColor = MaterialTheme.colorScheme.primary,
                                        fillColor = MaterialTheme.colorScheme.primary
                                    )
                                }
                            } else {
                                // Separate EPF chart
                                val epfData = prepareIndividualChartData(state.epfHistory, true)
                                if (epfData.entries.isNotEmpty()) {
                                    Text(
                                        "EPF",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                    com.saikumar.expensetracker.ui.components.LineChartComposable(
                                        entries = epfData.entries,
                                        xAxisLabels = epfData.labels,
                                        lineColor = MaterialTheme.colorScheme.primary,
                                        fillColor = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.fillMaxWidth().height(150.dp)
                                    )
                                }
                                
                                Spacer(modifier = Modifier.height(16.dp))
                                
                                // Separate NPS chart
                                val npsData = prepareIndividualChartData(state.npsHistory, false)
                                if (npsData.entries.isNotEmpty()) {
                                    Text(
                                        "NPS",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.padding(bottom = 4.dp)
                                    )
                                    com.saikumar.expensetracker.ui.components.LineChartComposable(
                                        entries = npsData.entries,
                                        xAxisLabels = npsData.labels,
                                        lineColor = MaterialTheme.colorScheme.secondary,
                                        fillColor = MaterialTheme.colorScheme.secondary,
                                        modifier = Modifier.fillMaxWidth().height(150.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // EPF History
            if (state.epfHistory.isNotEmpty()) {
                item {
                    Text("EPF Contribution History", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                items(state.epfHistory.take(12)) { balance ->
                    ContributionRow(balance, formatter, onClick = { selectedBalance = balance })
                }
            }

            // NPS History
            if (state.npsHistory.isNotEmpty()) {
                item {
                    Text("NPS Contribution History", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                }
                items(state.npsHistory.take(12)) { balance ->
                    ContributionRow(balance, formatter, onClick = { selectedBalance = balance })
                }
            }
        }
    }
}

data class ChartData(
    val entries: List<com.github.mikephil.charting.data.Entry>,
    val labels: List<String>
)

private fun prepareRetirementChartData(
    epfHistory: List<RetirementBalance>,
    npsHistory: List<RetirementBalance>
): ChartData {
    // Combine and sort all balances by month
    val allBalances = (epfHistory + npsHistory)
        .sortedBy { parseMonth(it.month) }
    
    // Group by month and sum contributions
    val monthlyTotals = allBalances.groupBy { it.month }
        .mapValues { (_, balances) -> balances.sumOf { it.contributionPaisa } }
        .toSortedMap(compareBy { parseMonth(it) })
    
    val entries = monthlyTotals.values.mapIndexed { index, total ->
        com.github.mikephil.charting.data.Entry(index.toFloat(), (total / 100f))
    }
    
    val labels = monthlyTotals.keys.map { formatMonthLabel(it) }
    
    return ChartData(entries, labels)
}

private fun prepareIndividualChartData(
    history: List<RetirementBalance>,
    isEpf: Boolean
): ChartData {
    // Group by month and sum contributions
    val monthlyData = history.groupBy { it.month }
        .mapValues { (_, balances) -> 
            balances.sumOf { it.contributionPaisa } / 100f
        }
        .toSortedMap(compareBy { parseMonth(it) })

    val entries = monthlyData.values.mapIndexed { index, value ->
         com.github.mikephil.charting.data.Entry(index.toFloat(), value)
    }
    
    val labels = monthlyData.keys.map { formatMonthLabel(it) }
    
    return ChartData(entries, labels)
}

private fun parseMonth(monthStr: String): YearMonth {
    // Try standard yyyy-MM
    try {
        return YearMonth.parse(monthStr, DateTimeFormatter.ofPattern("yyyy-MM"))
    } catch (e: Exception) {
        // Fallback: Try legacy MMM-yy or MMM-yyyy (e.g. "Oct-23", "Oct-2023")
        try {
            val parts = monthStr.split(Regex("[-/\\s]"))
            if (parts.size >= 2) {
                val m = parts[0].trim()
                var y = parts[1].trim()
                if (y.length == 2) y = "20$y"
                
                // Capitalize first letter of month for parsing (e.g. "oct" -> "Oct")
                val mCap = m.lowercase().replaceFirstChar { it.uppercase() }
                
                val formatter = DateTimeFormatter.ofPattern("MMM-yyyy", Locale.ENGLISH)
                return YearMonth.parse("$mCap-$y", formatter)
            }
        } catch (e2: Exception) {
            // Log or ignore
        }
        return YearMonth.now()
    }
}

private fun formatMonthLabel(monthStr: String): String {
    return try {
        val ym = parseMonth(monthStr)
        ym.format(DateTimeFormatter.ofPattern("MMM yy"))
    } catch (e: Exception) {
        monthStr
    }
}

@Composable
fun BalanceCard(title: String, balance: Long, formatter: NumberFormat, color: Color) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.1f))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(title, fontSize = 14.sp, color = color)
            Text(
                formatter.format(balance / 100.0),
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = color
            )
        }
    }
}

@Composable
fun ContributionRow(balance: RetirementBalance, formatter: NumberFormat, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(formatMonthLabel(balance.month))
        Text(formatter.format(balance.contributionPaisa / 100.0), fontWeight = FontWeight.SemiBold)
    }
}
