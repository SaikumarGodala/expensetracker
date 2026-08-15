package com.saikumar.expensetracker.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.saikumar.expensetracker.data.db.CategorySpending
import com.saikumar.expensetracker.data.db.MonthlySpending
import com.saikumar.expensetracker.data.db.YearlySpending
import com.saikumar.expensetracker.data.entity.Category
import com.saikumar.expensetracker.data.repository.ExpenseRepository
import com.saikumar.expensetracker.ui.components.BarChart
import com.saikumar.expensetracker.ui.components.LineChart
import com.saikumar.expensetracker.ui.components.PieChart
import com.saikumar.expensetracker.ui.components.getCategoryColor
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    repository: ExpenseRepository,
    onCategoryClick: (Category, Long, Long) -> Unit,
    onNavigateToTransferCircle: () -> Unit = {}
) {
    val viewModel: AnalyticsViewModel = viewModel(
        factory = AnalyticsViewModel.Factory(repository)
    )
    val state by viewModel.uiState.collectAsState()
    
    // Calculate Summary Metrics
    val totalSpend = state.categorySpending.sumOf { it.totalAmount }

    // Calculate actual number of months with data
    val currentYear = java.time.LocalDate.now().year
    val currentMonth = java.time.LocalDate.now().monthValue
    val monthsWithData = state.monthlyTrends.count { it.totalAmount > 0 }
    val monthsElapsed = when {
        state.selectedYear < currentYear -> 12
        state.selectedYear == currentYear -> currentMonth
        else -> 0
    }

    // Use actual months with data for more accurate average
    val avgMonthlySpend = if (monthsWithData > 0 && totalSpend > 0) {
        totalSpend / monthsWithData
    } else 0L

    val topCategory = state.categorySpending.maxByOrNull { it.totalAmount }

    // No top bar: the screen title is provided by InsightsHubScreen, which hosts
    // this content behind its "Over Time" segmented tab. Insets are zeroed for the
    // same reason - the hub already consumed the status-bar inset.
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0.dp)
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // YEAR SELECTOR (Conditional)
            // Always show in Overview mode, only show in Trends mode if there's multi-year data
            val shouldShowYearSelector = state.viewMode == AnalyticsViewMode.OVERVIEW ||
                                        (state.viewMode == AnalyticsViewMode.TRENDS && state.yearlySpending.size > 1)

            if (shouldShowYearSelector) {
                YearSelector(
                    selectedYear = state.selectedYear,
                    onPrevious = { viewModel.setYear(state.selectedYear - 1) },
                    onNext = { viewModel.setYear(state.selectedYear + 1) }
                )
                Spacer(modifier = Modifier.height(16.dp))
            } else {
                // Add minimal top spacing when year selector is hidden
                Spacer(modifier = Modifier.height(8.dp))
            }

            // SUMMARY & HERO
            if (!state.isLoading) {
                // Growth calculation
                val growthPercent = if (state.previousYearTotal > 0) {
                    ((totalSpend - state.previousYearTotal).toDouble() / state.previousYearTotal.toDouble()) * 100
                } else null

                // Hero Card (Total Spent) - hero radius tier, matching Home's balance card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    shape = RoundedCornerShape(com.saikumar.expensetracker.ui.theme.Dimens.RadiusHero)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Total Spent in ${state.selectedYear}",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = formatCurrency(totalSpend),
                            style = MaterialTheme.typography.displayMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        
                        if (growthPercent != null) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val icon = if (growthPercent >= 0) "↑" else "↓"
                                    val color = MaterialTheme.colorScheme.onSecondaryContainer
                                    Text(
                                        text = "$icon ${"%.1f".format(kotlin.math.abs(growthPercent))}% vs last year",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = color
                                    )
                                }
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Secondary Stats Row (Avg + Highest)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Highest Month Logic
                    val maxMonthEntry = state.monthlyTrends.maxByOrNull { it.totalAmount }
                    val maxMonthName = if (maxMonthEntry != null) {
                        val m = java.time.YearMonth.parse(maxMonthEntry.month)
                        m.month.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH)
                    } else "-"
                    
                    SecondaryStatCard(
                        label = "Highest Month",
                        value = if (maxMonthEntry != null) formatCurrencyCompact(maxMonthEntry.totalAmount) else "-",
                        subtitle = maxMonthName,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.secondary
                    )
                    
                    // Avg Logic - show meaningful info based on data availability
                    val avgLabel = when {
                        monthsWithData == 0 -> "Monthly Avg"
                        monthsWithData == 1 -> "Total (1 month)"
                        else -> "Monthly Avg"
                    }
                    val displayAvg = if (monthsWithData == 0) "-" else formatCurrencyCompact(avgMonthlySpend)
                    val subtitle = when {
                        monthsWithData == 0 -> null
                        monthsWithData == 1 -> null
                        else -> "$monthsWithData months"
                    }

                    SecondaryStatCard(
                        label = avgLabel,
                        value = displayAvg,
                        subtitle = subtitle,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))

                // Smart Insight Card (Actionable)
                // Smart Insight Card (Actionable)
                // Smart Insight Card (Actionable)
                 if (state.viewMode == AnalyticsViewMode.OVERVIEW && topCategory != null && totalSpend > 0) {
                     val topCategoryObj = state.allCategories.find { it.name == topCategory.categoryName }
                     val percent = (topCategory.totalAmount.toDouble() / totalSpend.toDouble()) * 100
                     
                     // Detect if the top category is "bad" (needs action)
                     val isNeedsAttention = topCategory.categoryName.lowercase(Locale.ROOT) in listOf("uncategorized", "miscellaneous", "general", "other")
                     val cardColor = if (isNeedsAttention) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                     val contentColor = if (isNeedsAttention) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface
                     val icon = if (isNeedsAttention) Icons.Default.Warning else Icons.Default.Star
                     val iconTint = if (isNeedsAttention) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                     
                     Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clickable {
                                if (topCategoryObj != null) {
                                    val start = java.time.LocalDate.of(state.selectedYear, 1, 1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                                    val end = java.time.LocalDate.of(state.selectedYear, 12, 31).atTime(23, 59, 59).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                                    onCategoryClick(topCategoryObj, start, end)
                                }
                            },
                        colors = CardDefaults.cardColors(containerColor = cardColor),
                        shape = RoundedCornerShape(com.saikumar.expensetracker.ui.theme.Dimens.RadiusCard)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(iconTint.copy(alpha = 0.1f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(icon, contentDescription = null, tint = iconTint)
                                }
                                Spacer(modifier = Modifier.width(16.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        if (isNeedsAttention) "Needs Attention" else "Insight",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = iconTint
                                    )
                                    Text(
                                        "${topCategory.categoryName} is %.1f%% of your spending".format(percent),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = contentColor
                                    )
                                }
                            }
                            
                            if (isNeedsAttention) {
                                // The useful version: the bucket grouped BY COUNTERPARTY, so
                                // it's obvious who the money went to and one tap categorizes
                                // the whole group (learned for future payments too).
                                LaunchedEffect(state.selectedYear) { viewModel.loadAttentionGroups() }
                                val groups by viewModel.attentionGroups.collectAsState()
                                var expanded by remember { mutableStateOf(false) }
                                var viewingGroup by remember { mutableStateOf<String?>(null) }
                                var pickingFor by remember { mutableStateOf<String?>(null) }

                                // STEP 1: transaction list for the tapped counterparty
                                if (viewingGroup != null) {
                                    val groupTxns by viewModel.groupTransactions.collectAsState()
                                    val dateFmt = remember { java.text.SimpleDateFormat("d MMM yy", Locale.getDefault()) }
                                    AlertDialog(
                                        onDismissRequest = { viewingGroup = null },
                                        title = { Text(viewingGroup!!, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                                        text = {
                                            androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                                                items(groupTxns.size) { i ->
                                                    val t = groupTxns[i]
                                                    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                                                        Row(
                                                            modifier = Modifier.fillMaxWidth(),
                                                            horizontalArrangement = Arrangement.SpaceBetween
                                                        ) {
                                                            Text(
                                                                dateFmt.format(java.util.Date(t.transaction.timestamp)),
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                            Text(
                                                                "₹${java.text.NumberFormat.getIntegerInstance().format(t.transaction.amountPaisa / 100)}",
                                                                style = MaterialTheme.typography.bodyMedium,
                                                                fontWeight = FontWeight.SemiBold
                                                            )
                                                        }
                                                        val snippet = (t.transaction.fullSmsBody ?: t.transaction.smsSnippet ?: "")
                                                            .replace('\n', ' ').take(80)
                                                        if (snippet.isNotBlank()) {
                                                            Text(
                                                                snippet,
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                                                maxLines = 1,
                                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                                            )
                                                        }
                                                    }
                                                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                                                }
                                            }
                                        },
                                        confirmButton = {
                                            Button(onClick = {
                                                pickingFor = viewingGroup
                                                viewingGroup = null
                                            }) { Text("Categorize All (${groupTxns.size})") }
                                        },
                                        dismissButton = {
                                            TextButton(onClick = { viewingGroup = null }) { Text("Close") }
                                        }
                                    )
                                }

                                // STEP 2: category picker
                                if (pickingFor != null) {
                                    val groupOrder = listOf(
                                        com.saikumar.expensetracker.data.entity.CategoryType.VARIABLE_EXPENSE to "🛒 Variable Expenses",
                                        com.saikumar.expensetracker.data.entity.CategoryType.FIXED_EXPENSE to "🏠 Fixed Expenses",
                                        com.saikumar.expensetracker.data.entity.CategoryType.VEHICLE to "🚗 Vehicle",
                                        com.saikumar.expensetracker.data.entity.CategoryType.INCOME to "📈 Income",
                                        com.saikumar.expensetracker.data.entity.CategoryType.INVESTMENT to "📊 Investments",
                                        com.saikumar.expensetracker.data.entity.CategoryType.LIABILITY to "💳 CC Bill Payment",
                                        com.saikumar.expensetracker.data.entity.CategoryType.TRANSFER to "↔️ Transfer"
                                    )
                                    com.saikumar.expensetracker.ui.common.CategoryPickerDialog(
                                        categories = state.allCategories,
                                        selectedCategory = topCategoryObj ?: state.allCategories.first(),
                                        groupOrder = groupOrder,
                                        onPick = { cat ->
                                            viewModel.bulkCategorize(pickingFor!!, cat)
                                            pickingFor = null
                                        },
                                        onDismiss = { pickingFor = null }
                                    )
                                }

                                if (groups.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    HorizontalDivider(color = contentColor.copy(alpha = 0.15f))
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        "Tap a name to categorize all its payments:",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = contentColor.copy(alpha = 0.7f)
                                    )
                                    val shown = if (expanded) groups.take(25) else groups.take(6)
                                    shown.forEach { g ->
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    viewModel.loadGroupTransactions(g.name)
                                                    viewingGroup = g.name
                                                }
                                                .padding(vertical = 8.dp)
                                        ) {
                                            Text(
                                                g.name,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                                color = contentColor,
                                                maxLines = 1,
                                                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                "${g.count} × · ₹${java.text.NumberFormat.getIntegerInstance().format(g.totalPaisa / 100)}",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = contentColor.copy(alpha = 0.8f)
                                            )
                                            Icon(
                                                Icons.Default.KeyboardArrowRight,
                                                contentDescription = null,
                                                tint = contentColor.copy(alpha = 0.5f),
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    if (groups.size > 6) {
                                        TextButton(onClick = { expanded = !expanded }) {
                                            Text(
                                                if (expanded) "Show less" else "Show all ${groups.size}",
                                                color = iconTint
                                            )
                                        }
                                    }
                                }

                                Spacer(modifier = Modifier.height(4.dp))
                                TextButton(
                                    onClick = onNavigateToTransferCircle,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("People you transfer with often? Review Transfer Circle", color = iconTint)
                                }
                            }
                        }
                    }
                }

            }

            Spacer(modifier = Modifier.height(24.dp))

            // VIEW MODE CHIPS
            // Deliberately styled as compact FilterChips, NOT a second full-width
            // segmented row: the hub's "This Cycle / Over Time" toggle sits directly
            // above this screen, and two identical pill rows stacked read as one
            // broken control. Chips also carry clearer labels than Overview/Trends.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AnalyticsViewMode.values().forEach { mode ->
                    val selected = state.viewMode == mode
                    FilterChip(
                        selected = selected,
                        onClick = { viewModel.setViewMode(mode) },
                        label = {
                            Text(
                                when (mode) {
                                    AnalyticsViewMode.OVERVIEW -> "Category Breakdown"
                                    AnalyticsViewMode.TRENDS -> "Monthly Trend"
                                }
                            )
                        },
                        leadingIcon = if (selected) {
                            {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(FilterChipDefaults.IconSize)
                                )
                            }
                        } else null
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // PROMINENT CATEGORY FILTER (Trends Mode Only)
            if (state.viewMode == AnalyticsViewMode.TRENDS) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 16.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Filter by Category",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = state.selectedCategory?.name ?: "Showing all categories",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        CategorySelector(
                            categories = state.allCategories,
                            selectedCategory = state.selectedCategory,
                            onCategorySelected = { viewModel.setSelectedCategory(it) }
                        )
                    }
                }
            }

            // MAIN CHART CONTENT
            if (state.isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp), 
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 32.dp),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(com.saikumar.expensetracker.ui.theme.Dimens.RadiusCard)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Header for Chart Section
                        Text(
                            text = when (state.viewMode) {
                                AnalyticsViewMode.OVERVIEW -> "Category Breakdown"
                                AnalyticsViewMode.TRENDS -> "Monthly Trends"
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                        // Chart Render
                        when (state.viewMode) {
                            AnalyticsViewMode.OVERVIEW -> {
                                PieChart(
                                    data = state.categorySpending,
                                    modifier = Modifier.fillMaxWidth().height(300.dp),
                                    onCategoryClick = { spending ->
                                        // Find full Category object
                                        val category = state.allCategories.find { it.name == spending.categoryName }
                                        if (category != null) {
                                            val start = java.time.LocalDate.of(state.selectedYear, 1, 1).atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                                            val end = java.time.LocalDate.of(state.selectedYear, 12, 31).atTime(23, 59, 59).atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
                                            onCategoryClick(category, start, end)
                                        }
                                    }
                                )
                            }
                            AnalyticsViewMode.TRENDS -> {
                                val currentMonVal = java.time.LocalDate.now().monthValue
                                val isCurYear = state.selectedYear == currentYear
                                
                                // Filter data: remove future months if current year
                                val activeData = if (isCurYear) {
                                    state.monthlyTrends.filter { 
                                        try {
                                            val m = java.time.YearMonth.parse(it.month).monthValue
                                            m <= currentMonVal
                                        } catch(e: Exception) {
                                            android.util.Log.w("AnalyticsScreen", "Failed to parse month: ${it.month}", e)
                                            true // Include unparseable entries
                                        }
                                    }.sortedBy { it.month }
                                } else {
                                     state.monthlyTrends.sortedBy { it.month }
                                }
                                
                                val nonZeroCount = activeData.count { it.totalAmount > 0 }
                                
                                if (nonZeroCount < 2) {
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(200.dp),
                                        verticalArrangement = Arrangement.Center,
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Icon(
                                            Icons.Default.DateRange, 
                                            contentDescription = null, 
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                                            modifier = Modifier.size(48.dp)
                                        )
                                        Spacer(modifier = Modifier.height(16.dp))
                                        Text(
                                            "Not enough monthly data yet.",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                } else {
                                    // Trends Insight
                                    val trendMsg = remember(activeData) { 
                                        generateTrendInsight(activeData, state.selectedCategory) 
                                    }
                                    
                                    val categoryName = state.selectedCategory?.name ?: "Overall"
                                    val tColor = if (state.selectedCategory != null) getCategoryColor(categoryName) else MaterialTheme.colorScheme.tertiary
                                    
                                    // Insight Card
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 16.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                                        shape = RoundedCornerShape(com.saikumar.expensetracker.ui.theme.Dimens.RadiusCard)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                             Icon(
                                                Icons.Default.ShoppingCart, // Or a generic 'Trend' icon if available
                                                contentDescription = null,
                                                tint = tColor,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text(
                                                text = trendMsg,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                    }

                                    LineChart(
                                        data = activeData,
                                        modifier = Modifier.fillMaxWidth().height(260.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun YearSelector(
    selectedYear: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    // Styled to mirror Home's month selector pill exactly (same container, pill shape,
    // chevrons inside, titleMedium bold) - previously this was a differently-colored,
    // differently-shaped, larger-typography variant of the same control.
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = RoundedCornerShape(percent = com.saikumar.expensetracker.ui.theme.Dimens.RadiusPill),
            color = MaterialTheme.colorScheme.surfaceContainer
        ) {
            Row(
                modifier = Modifier.padding(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onPrevious, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.KeyboardArrowLeft, "Previous Year", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
                }
                Text(
                    text = selectedYear.toString(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 20.dp)
                )
                IconButton(onClick = onNext, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.KeyboardArrowRight, "Next Year", modifier = Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurface)
                }
            }
        }
    }
}

@Composable
fun SecondaryStatCard(
    label: String,
    value: String,
    subtitle: String?,
    modifier: Modifier = Modifier,
    color: Color
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        // Explicit shape (was relying on the M3 default) so this matches the app-wide
        // RadiusCard tier instead of whatever Material's implicit default happened to be.
        shape = RoundedCornerShape(com.saikumar.expensetracker.ui.theme.Dimens.RadiusCard)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = color
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            }
        }
    }
}

// Helper for compact currency (avoid duplication if possible, but localized here is safer)
private fun formatCurrencyCompact(paisa: Long): String {
    val amount = paisa / 100.0
    return when {
        amount >= 10000000 -> "₹%.1fCr".format(amount / 10000000)
        amount >= 100000 -> "₹%.1fL".format(amount / 100000)
        amount >= 1000 -> "₹%.1fK".format(amount / 1000)
        else -> "₹%.0f".format(amount)
    }
}

private fun generateTrendInsight(trends: List<MonthlySpending>, category: Category?): String {
    val activeMonths = trends.filter { it.totalAmount > 0 }.sortedBy { it.month }
    if (activeMonths.size < 2) return "Not enough data to analyze trends."

    val lastMonth = activeMonths.last()
    val prevMonth = activeMonths[activeMonths.size - 2]
    
    // 1. Check for sharp changes (last 2 active months)
    val change = lastMonth.totalAmount - prevMonth.totalAmount
    val percentChange = if (prevMonth.totalAmount > 0) (change.toDouble() / prevMonth.totalAmount) * 100 else 0.0
    
    val catName = category?.name ?: "Spending"
    
    if (kotlin.math.abs(percentChange) > 20) {
        val direction = if (percentChange > 0) "jumped" else "dropped"
        return "$catName $direction by ${"%.0f".format(kotlin.math.abs(percentChange))}% in ${monthName(lastMonth.month)} vs ${monthName(prevMonth.month)}."
    }
    
    // 2. Check for consistency (Expense consistency)
    val values = activeMonths.map { it.totalAmount }
    val avg = values.average()
    val maxDev = values.maxOf { kotlin.math.abs(it - avg) }
    if (avg > 0 && (maxDev / avg) < 0.15) {
         return "Your $catName expenses are very consistent month-to-month."
    }

    // 3. Fallback: Highest month
    val curMax = activeMonths.maxByOrNull { it.totalAmount }
    return if (curMax != null) {
        "Highest $catName spending was in ${monthName(curMax.month)}."
    } else {
        "Spending habits are varying."
    }
}

private fun monthName(yyyymm: String): String {
    return try {
        val m = java.time.YearMonth.parse(yyyymm)
        m.month.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH)
    } catch (e: Exception) {
        android.util.Log.w("AnalyticsScreen", "Failed to parse month: $yyyymm", e)
        yyyymm
    }
}

@Composable
fun SummaryStatCard(
    title: String,
    value: String,
    subtitle: String? = null,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    showToggleIcon: Boolean = false
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(color.copy(alpha = 0.1f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = color)
                }
                
                if (showToggleIcon) {
                    Icon(
                        Icons.Default.Refresh, 
                        contentDescription = "Tap to toggle",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            if (subtitle != null) {
                 Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun CategorySelector(
    categories: List<Category>,
    selectedCategory: Category?,
    onCategorySelected: (Category?) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    
    Box {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(8.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.height(32.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selectedCategory?.name ?: "All Categories",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Spacer(modifier = Modifier.width(4.dp))
                Icon(
                    Icons.Default.ArrowDropDown, 
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
        
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            DropdownMenuItem(
                text = { Text("All Categories") },
                onClick = { 
                    onCategorySelected(null)
                    expanded = false 
                }
            )
            categories.forEach { category ->
                DropdownMenuItem(
                    text = { Text(category.name) },
                    onClick = { 
                        onCategorySelected(category)
                        expanded = false 
                    }
                )
            }
        }
    }
}

private fun formatCurrency(paisa: Long): String {
    val amount = paisa / 100.0
    return when {
        amount >= 10000000 -> "₹%.1fCr".format(amount / 10000000)
        amount >= 100000 -> "₹%.1fL".format(amount / 100000)
        amount >= 1000 -> "₹%.1fK".format(amount / 1000)
        else -> "₹%.0f".format(amount)
    }
}
