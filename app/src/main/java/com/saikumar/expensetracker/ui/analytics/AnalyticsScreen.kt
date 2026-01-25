package com.saikumar.expensetracker.ui.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
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
import com.saikumar.expensetracker.data.entity.Category
import com.saikumar.expensetracker.data.repository.ExpenseRepository
import com.saikumar.expensetracker.ui.components.BarChart
import com.saikumar.expensetracker.ui.components.LineChart
import com.saikumar.expensetracker.ui.components.PieChart
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    repository: ExpenseRepository,
    onNavigateBack: () -> Unit,
    onCategoryClick: (Category, Long, Long) -> Unit
) {
    val viewModel: AnalyticsViewModel = viewModel(
        factory = AnalyticsViewModel.Factory(repository)
    )
    val state by viewModel.uiState.collectAsState()
    
    // Calculate Summary Metrics
    val totalSpend = state.categorySpending.sumOf { it.totalAmount }
    
    // Calculate actual number of months for averaging
    val currentYear = java.time.LocalDate.now().year
    val currentMonth = java.time.LocalDate.now().monthValue
    val monthsForAverage = when {
        state.selectedYear < currentYear -> 12 // Past year: full 12 months
        state.selectedYear == currentYear -> currentMonth // Current year: months elapsed
        else -> 0 // Future year: no data yet
    }
    val avgMonthlySpend = if (monthsForAverage > 0 && totalSpend > 0) totalSpend / monthsForAverage else 0L
    
    val topCategory = state.categorySpending.maxByOrNull { it.totalAmount }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Financial Insights", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
        ) {
            // YEAR SELECTOR
            YearSelector(
                selectedYear = state.selectedYear,
                onPrevious = { viewModel.setYear(state.selectedYear - 1) },
                onNext = { viewModel.setYear(state.selectedYear + 1) }
            )
            
            Spacer(modifier = Modifier.height(16.dp))

            // SUMMARY CARDS
            if (!state.isLoading) {
                var showMaxMonth by remember { mutableStateOf(false) }
                val maxMonthEntry = state.monthlyTrends.maxByOrNull { it.totalAmount }
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SummaryStatCard(
                        title = "Total Spent",
                        value = formatCurrency(totalSpend),
                        icon = Icons.Default.ShoppingCart,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f)
                    )
                    
                    val avgTitle = if (showMaxMonth) "Highest Month" else "Monthly Avg"
                    val avgValue = if (showMaxMonth && maxMonthEntry != null) {
                         formatCurrency(maxMonthEntry.totalAmount)
                    } else {
                         formatCurrency(avgMonthlySpend)
                    }
                    val avgSubtitle = if (showMaxMonth && maxMonthEntry != null) {
                         // Format 2024-05 -> May
                         val m = java.time.YearMonth.parse(maxMonthEntry.month)
                         m.month.getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH)
                    } else null
                    
                    SummaryStatCard(
                        title = avgTitle,
                        value = avgValue,
                        subtitle = avgSubtitle,
                        icon = Icons.Default.DateRange,
                        color = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showMaxMonth = !showMaxMonth },
                        showToggleIcon = true
                    )
                }
                
                Spacer(modifier = Modifier.height(12.dp))
                
                if (topCategory != null) {
                    val topCategoryObj = state.allCategories.find { it.name == topCategory.categoryName }
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
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.Star, 
                                contentDescription = null, 
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    "Top Spending Category", 
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    topCategory.categoryName, 
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            Spacer(modifier = Modifier.weight(1f))
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    formatCurrency(topCategory.totalAmount),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                                Text(
                                    "Tap to view",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // VIEW MODE TABS
            SingleChoiceSegmentedButtonRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                AnalyticsViewMode.values().forEachIndexed { index, mode ->
                    SegmentedButton(
                        selected = state.viewMode == mode,
                        onClick = { viewModel.setViewMode(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index = index, count = AnalyticsViewMode.values().size),
                        label = {
                            Text(
                                when(mode) {
                                    AnalyticsViewMode.OVERVIEW -> "Overview"
                                    AnalyticsViewMode.YEAR_COMP -> "Comparison"
                                    AnalyticsViewMode.TRENDS -> "Trends"
                                },
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(24.dp))
            
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
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Header for Chart Section
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = when (state.viewMode) {
                                    AnalyticsViewMode.OVERVIEW -> "Category Breakdown"
                                    AnalyticsViewMode.YEAR_COMP -> {
                                        val catName = state.comparisonCategory?.name
                                        if (catName != null) {
                                            "$catName - Yearly Comparison"
                                        } else {
                                            "Total Spending - Yearly Comparison"
                                        }
                                    }
                                    AnalyticsViewMode.TRENDS -> "Spending Trends"
                                },
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.weight(1f)
                            )
                            
                            // Category Filter (For Trends and Year Comparison)
                            if (state.viewMode == AnalyticsViewMode.TRENDS) {
                                CategorySelector(
                                    categories = state.allCategories,
                                    selectedCategory = state.selectedCategory,
                                    onCategorySelected = { viewModel.setSelectedCategory(it) }
                                )
                            }
                            if (state.viewMode == AnalyticsViewMode.YEAR_COMP) {
                                CategorySelector(
                                    categories = state.allCategories,
                                    selectedCategory = state.comparisonCategory,
                                    onCategorySelected = { viewModel.setComparisonCategory(it) }
                                )
                            }
                        }
                        
                        Divider(modifier = Modifier.padding(vertical = 16.dp))

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
                            AnalyticsViewMode.YEAR_COMP -> {
                                Column {
                                    BarChart(
                                        data = state.yearlySpending,
                                        modifier = Modifier.fillMaxWidth().height(280.dp)
                                    )
                                    if (state.yearlySpending.isNotEmpty()) {
                                        val years = state.yearlySpending.map { it.year }
                                        val categoryText = state.comparisonCategory?.name ?: "All Expenses"
                                        Text(
                                            "$categoryText across: ${years.joinToString(", ")}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(top = 8.dp)
                                        )
                                    }
                                }
                            }
                            AnalyticsViewMode.TRENDS -> {
                                LineChart(
                                    data = state.monthlyTrends,
                                    modifier = Modifier.fillMaxWidth().height(300.dp)
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
fun YearSelector(
    selectedYear: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrevious) {
            Icon(Icons.Default.KeyboardArrowLeft, "Previous Year", tint = MaterialTheme.colorScheme.primary)
        }
        
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = selectedYear.toString(),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
        }

        IconButton(onClick = onNext) {
            Icon(Icons.Default.KeyboardArrowRight, "Next Year", tint = MaterialTheme.colorScheme.primary)
        }
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
