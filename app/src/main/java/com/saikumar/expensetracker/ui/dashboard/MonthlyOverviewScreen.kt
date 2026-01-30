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
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.draw.clip
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
    onNavigateToSearch: () -> Unit,
    onCategoryClick: (CategoryType, Long, Long) -> Unit = { _, _, _ -> },
    onNavigateToSalaryHistory: () -> Unit = {},
    onNavigateToInterest: () -> Unit = {},
    onNavigateToRetirement: () -> Unit = {},
    onNavigateToNeedsReview: (Long, Long) -> Unit = { _, _ -> }
) {
    val uiState by viewModel.uiState.collectAsState()
    val allCategories by viewModel.allCategories.collectAsState(initial = emptyList())
    var editingTransaction by remember { mutableStateOf<TransactionWithCategory?>(null) }
    var showDateRangePicker by remember { mutableStateOf(false) }
    
    var showBudgetSheet by remember { mutableStateOf(false) }
    var budgetRecommendations by remember { mutableStateOf<List<BudgetRecommendation>>(emptyList()) }
    val scope = rememberCoroutineScope() 
    
    // Search State REMOVED - Using Global Search
    // var searchQuery by remember { mutableStateOf("") }
    // var showSearchDialog by remember { mutableStateOf(false) }
    
    if (showBudgetSheet) {
        BudgetPlanningSheet(
            recommendations = budgetRecommendations,
            onDismiss = { showBudgetSheet = false },
            onSave = { budgets ->
                viewModel.saveBudgets(budgets)
                showBudgetSheet = false
            }
        )
    }

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
            },
            onFindSimilar = { viewModel.findSimilarTransactions(editingTransaction!!.transaction) }
        )
    }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()

    Scaffold(
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Compact Header: [Filter] [Date] [Search]
                item {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Account Filter - TextButton for clarity
                            Box {
                                var filterExpanded by remember { mutableStateOf(false) }
                                
                                TextButton(
                                    onClick = { filterExpanded = true },
                                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurface)
                                ) {
                                    Icon(
                                        Icons.Default.FilterList, 
                                        contentDescription = "Filter",
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        if (uiState.selectedAccounts.isNotEmpty()) "Filter (${uiState.selectedAccounts.size})" else "Filter",
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                }
                                
                                DropdownMenu(
                                    expanded = filterExpanded,
                                    onDismissRequest = { filterExpanded = false }
                                ) {
                                     // ... (Same content as before)
                                     if (uiState.detectedAccounts.isEmpty()) {
                                        DropdownMenuItem(
                                            text = { Text("No accounts detected yet", style = MaterialTheme.typography.bodySmall) },
                                            onClick = { }
                                        )
                                    } else {
                                         DropdownMenuItem(
                                            text = { Text(if (uiState.selectedAccounts.isEmpty()) "Filter Accounts" else "${uiState.selectedAccounts.size} Selected", fontWeight = FontWeight.Bold) },
                                            onClick = { }
                                        )
                                        HorizontalDivider()
                                        
                                        if (uiState.selectedAccounts.isNotEmpty()) {
                                            DropdownMenuItem(
                                                text = {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(Icons.Default.Close, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                                        Spacer(modifier = Modifier.width(8.dp))
                                                        Text("Clear All", color = MaterialTheme.colorScheme.error)
                                                    }
                                                },
                                                onClick = {
                                                    viewModel.clearAccountFilter()
                                                    filterExpanded = false
                                                }
                                            )
                                        }
                                        
                                        uiState.detectedAccounts.forEach { account ->
                                            val typeLabel = if (account.accountType == com.saikumar.expensetracker.data.entity.AccountType.CREDIT_CARD) "CC" else "A/c"
                                            val label = "${account.bankName} $typeLabel XX${account.accountNumberLast4}"
                                            
                                            DropdownMenuItem(
                                                text = {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Checkbox(
                                                            checked = account.accountNumberLast4 in uiState.selectedAccounts,
                                                            onCheckedChange = null
                                                        )
                                                        Text(label)
                                                    }
                                                },
                                                onClick = { viewModel.toggleAccountFilter(account.accountNumberLast4) }
                                            )
                                        }
                                    }
                                }
                            }
                            
                            // Date Navigator (Centered)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(horizontal = 4.dp), // Slightly tighter padding
                                horizontalArrangement = Arrangement.Center
                            ) {
                                IconButton(onClick = { viewModel.previousCycle() }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.ChevronLeft, null)
                                }
                                
                                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { showDateRangePicker = true }) {
                                    Text(
                                        uiState.cycleRange?.endDate?.format(DateTimeFormatter.ofPattern("MMM yyyy")) ?: "Date",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold
                                    )
                                    uiState.cycleRange?.let { range ->
                                        Text(
                                            "${range.startDate.format(DateTimeFormatter.ofPattern("dd"))}-${range.endDate.format(DateTimeFormatter.ofPattern("dd"))}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                IconButton(onClick = { viewModel.nextCycle() }, modifier = Modifier.size(28.dp)) {
                                    Icon(Icons.Default.ChevronRight, null)
                                }
                            }
                            
                            // Search Icon
                            IconButton(onClick = onNavigateToSearch) {
                                Icon(Icons.Default.Search, contentDescription = "Search", tint = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                    }
                }

                // Summary cards - clickable to show transactions (Restored)
                // ... (Summary Cards Logic) ...
                
                // Calculate data for list - show ALL categories
                val allSummaries = uiState.categoryBreakdown.values.flatten()
                    .filter { it.category.type != CategoryType.INCOME }
                
                // Global Search handles filtering now. Here we show full overview context.
                // We do NOT filter by search query here anymore.
                val filteredSummaries = allSummaries
                
                // 4. Turn "Spam" Into an Action (Logic)
                // Filter Uncategorized, Unknown, Spam, and Miscellaneous into "Needs Review"
                val needsReviewList = filteredSummaries.filter { 
                    it.category.name.equals("Uncategorized", ignoreCase = true) || 
                    it.category.name.equals("Unknown Expense", ignoreCase = true) ||
                    it.category.name.equals("Spam", ignoreCase = true) ||
                    it.category.name.equals("Miscellaneous", ignoreCase = true)
                }
                
                // 5. Gentle Sorting Intelligence (No Manual Controls)
                val categorized = filteredSummaries.filterNot { 
                     it.category.name.equals("Uncategorized", ignoreCase = true) || 
                    it.category.name.equals("Unknown Expense", ignoreCase = true) ||
                    it.category.name.equals("Spam", ignoreCase = true) ||
                    it.category.name.equals("Miscellaneous", ignoreCase = true)
                }.sortedWith(compareByDescending<CategorySummary> { summary ->
                    // Priority 1: Over Budget (Red)
                    val budget = summary.budgetStatus?.targetAmountPaisa ?: 0L
                    if (budget > 0 && summary.total > (budget / 100.0)) 2 else 0
                }.thenByDescending { summary ->
                    // Priority 2: Near Budget (Yellow - 80%)
                    val budget = summary.budgetStatus?.targetAmountPaisa ?: 0L
                    if (budget > 0 && summary.total > (budget / 100.0 * 0.8)) 1 else 0
                }.thenByDescending { 
                    // Priority 3: Total Spend Amount
                    it.total 
                })

                // REMOVED: One-Line "State Summary" block (Good control/Spending is higher)


                // Uncategorized / Spam "Needs Review" Card
                if (needsReviewList.isNotEmpty()) {
                    item {
                        val totalUncat = needsReviewList.sumOf { it.total }
                        val countUncat = needsReviewList.sumOf { it.transactions.size }
                        
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { 
                                        uiState.cycleRange?.let { range ->
                                            val start = range.startDate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                                            val end = range.endDate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                                            onNavigateToNeedsReview(start, end)
                                        }
                                    }
                                    .padding(16.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        "Needs Review", 
                                        style = MaterialTheme.typography.titleMedium, 
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "$countUncat items • ₹${String.format(Locale.getDefault(), "%,.0f", totalUncat)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                    // 4. "Spam" listed explicitly if present
                                    if (needsReviewList.any { it.category.name.equals("Spam", ignoreCase = true) }) {
                                        Text(
                                            "Includes potential spam/junk",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.8f)
                                        )
                                    }
                                }
                                Button(
                                    onClick = { 
                                        uiState.cycleRange?.let { range ->
                                            val start = range.startDate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                                            val end = range.endDate.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                                            onNavigateToNeedsReview(start, end)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error, 
                                        contentColor = Color.White
                                    )
                                ) {
                                    Text("Review")
                                }
                            }
                        }
                    }
                }

                // "Plan Spending" Button (Centered & Styled)
                // 2. Contextual CTA Logic
                val hasRealBudgets = categorized.any { it.budgetStatus?.isGhost == false }
                val planLabel = if (hasRealBudgets) "Adjust Plan" else "Set Monthly Plan"

                item {
                     Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        OutlinedButton(
                            onClick = { 
                                scope.launch {
                                    budgetRecommendations = viewModel.loadBudgetRecommendations()
                                    showBudgetSheet = true
                                }
                            },
                            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(planLabel)
                        }
                    }
                }

                // Global Typical Explanation
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            Icons.Default.Info, 
                            contentDescription = null, 
                            modifier = Modifier.size(16.dp), 
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Typical = what you usually spend (last 3 months)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Categorized List
                items(categorized.size) { index ->
                    val summary = categorized[index]

                    // Simple Color generation (hashing name?) or consistent palette
                    // We can reuse the ColorTemplate logic or just use Primary/Secondary varied
                    val color = Color(ColorTemplate.MATERIAL_COLORS[index % ColorTemplate.MATERIAL_COLORS.size])

                    InteractiveCategoryLegendItem(
                        summary = summary,
                        color = color,
                        onTransactionClick = { editingTransaction = it }
                    )
                }

                // Quick Insights Section
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Quick Insights",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }

                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Salary History
                        QuickInsightCard(
                            icon = Icons.Default.Payments,
                            title = "Salary",
                            subtitle = "View history",
                            onClick = onNavigateToSalaryHistory,
                            modifier = Modifier.weight(1f)
                        )
                        // Interest Earned
                        QuickInsightCard(
                            icon = Icons.Default.Savings,
                            title = "Interest",
                            subtitle = "Track earnings",
                            onClick = onNavigateToInterest,
                            modifier = Modifier.weight(1f)
                        )
                        // Retirement
                        QuickInsightCard(
                            icon = Icons.Default.AccountBalance,
                            title = "EPF/NPS",
                            subtitle = "Balances",
                            onClick = onNavigateToRetirement,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                // Bottom padding
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }


        }
    }
}

@Composable
private fun QuickInsightCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SummaryCard(title: String, amount: Double, color: Color, modifier: Modifier = Modifier, subtitle: String? = null, subtitleColor: Color = color.copy(alpha = 0.8f), onClick: () -> Unit = {}) {
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
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = subtitleColor,
                    fontWeight = FontWeight.Medium
                )
            }
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
        color = MaterialTheme.colorScheme.surface,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(12.dp)
        ) {
            // Header Row (Always Visible)
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Icon
                Surface(
                    modifier = Modifier.size(40.dp),
                    color = color.copy(alpha = 0.1f),
                    shape = androidx.compose.foundation.shape.CircleShape
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = CategoryIcons.getIcon(summary.category.name),
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = color
                        )
                    }
                }
                
                Spacer(modifier = Modifier.width(12.dp))
                
                // Content
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            summary.category.name,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "₹${String.format(Locale.getDefault(), "%,.0f", summary.total)}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    
                    // Mini Progress Bar (Always visible if budget exists)
                    summary.budgetStatus?.let { status ->
                        val target = status.targetAmountPaisa / 100.0
                        val spent = summary.total
                        val progress = (spent / target).coerceIn(0.0, 1.0).toFloat()
                        
                        val progressColor = when {
                            spent >= target * 1.0 -> MaterialTheme.colorScheme.error // Crossed typical
                            spent >= target * 0.8 -> Color(0xFFFFC107) // Warning
                            else -> Color(0xFF4CAF50) // Healthy
                        }
                        
                        Spacer(modifier = Modifier.height(6.dp))
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(androidx.compose.foundation.shape.RoundedCornerShape(2.dp)),
                            color = progressColor,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }
                }
                
                 Spacer(modifier = Modifier.width(8.dp))
                 
                 Icon(
                    if (expanded) Icons.Default.ExpandLess else Icons.Default.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                 )
            }
            
            // Expanded Details
            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    // Budget/Typical Insights
                    summary.budgetStatus?.let { status ->
                        val target = status.targetAmountPaisa / 100.0
                        val diff = summary.total - target
                        
                        // 6. Micro-Insight / Positive Reinforcement
                        // Find top merchant
                        val topMerchant = summary.transactions
                            .groupingBy { it.transaction.merchantName ?: it.transaction.upiId ?: "Unknown" }
                            .eachCount()
                            .maxByOrNull { it.value }?.key ?: "Unknown"
                            
                        val isHealthy = diff <= 0
                        val insight = if (isHealthy) "Nice control! Below typical." else "Mostly at $topMerchant"

                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                             Text(
                                "${summary.transactions.size} txns • $insight", // Added Insight
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isHealthy) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(4.dp))
                        
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val statusText = if (status.isGhost) "Typical" else "Plan"
                            Text(
                                "$statusText: ₹${String.format(Locale.getDefault(), "%,.0f", target)}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            
                            // Human readable text
                            val contextText = if (diff > 0) {
                                "₹${String.format(Locale.getDefault(), "%,.0f", diff)} above usual"
                            } else {
                                "₹${String.format(Locale.getDefault(), "%,.0f", -diff)} below usual"
                            }
                            val contextColor = if (diff > 0) MaterialTheme.colorScheme.error else Color(0xFF4CAF50)
                            
                            Text(
                                contextText,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = contextColor
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(12.dp))
                    } ?: run {
                         Text(
                            "${summary.transactions.size} transactions",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                    
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    
                    // Transaction List
                     summary.transactions.take(5).forEach { txn -> 
                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onTransactionClick(txn) }.padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                             Text(
                                txn.transaction.merchantName ?: txn.transaction.upiId ?: txn.transaction.note ?: "Unknown",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                formatAmount(txn.transaction.amountPaisa),
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium
                            )
                        }
                     }
                }
            }
        }
    }
}
