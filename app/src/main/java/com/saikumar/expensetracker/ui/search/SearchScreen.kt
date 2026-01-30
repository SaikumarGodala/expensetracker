package com.saikumar.expensetracker.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.saikumar.expensetracker.data.db.TransactionWithCategory
import com.saikumar.expensetracker.data.entity.TransactionType
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onNavigateBack: () -> Unit,
    onTransactionClick: (TransactionWithCategory) -> Unit = {}
) {
    val query by viewModel.query.collectAsState()
    val timeFilter by viewModel.timeFilter.collectAsState()
    val sortOption by viewModel.sortOption.collectAsState()
    val searchResults by viewModel.searchResults.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Search") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Search Bar
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 2.dp
            ) {
                TextField(
                    value = query,
                    onValueChange = { viewModel.updateQuery(it) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { 
                        Text(
                            "Search merchant, note, or amount...",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        ) 
                    },
                    leadingIcon = { 
                        Icon(
                            Icons.Default.Search, 
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        ) 
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { viewModel.updateQuery("") }) {
                                Icon(
                                    Icons.Default.Clear, 
                                    contentDescription = "Clear",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent,
                        errorIndicatorColor = Color.Transparent
                    ),
                    textStyle = MaterialTheme.typography.bodyLarge
                )
            }

            // Filters Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Time Filter Chip
                var timeExpanded by remember { mutableStateOf(false) }
                Box {
                    FilterChip(
                        selected = timeFilter != TimeFilter.ALL,
                        onClick = { timeExpanded = true },
                        label = { Text(timeFilter.label) },
                        trailingIcon = { 
                            // Icon(Icons.Default.ArrowDropDown, null) // Optional
                        }
                    )
                    DropdownMenu(
                        expanded = timeExpanded,
                        onDismissRequest = { timeExpanded = false }
                    ) {
                        TimeFilter.values().forEach { filter ->
                            DropdownMenuItem(
                                text = { Text(filter.label) },
                                onClick = {
                                    viewModel.setTimeFilter(filter)
                                    timeExpanded = false
                                }
                            )
                        }
                    }
                }

                // Sort Filter Chip
                var sortExpanded by remember { mutableStateOf(false) }
                Box {
                    FilterChip(
                        selected = true,
                        onClick = { sortExpanded = true },
                        label = { Text(sortOption.label) },
                         trailingIcon = { 
                            // Icon(Icons.Default.ArrowDropDown, null) // Optional
                        }
                    )
                    DropdownMenu(
                        expanded = sortExpanded,
                        onDismissRequest = { sortExpanded = false }
                    ) {
                        SortOption.values().forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    viewModel.setSortOption(option)
                                    sortExpanded = false
                                }
                            )
                        }
                    }
                }
            }

            // Results List or Empty State
            if (searchResults.isEmpty() && query.isNotEmpty()) {
                // Empty state when searching but no results
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.SearchOff,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "No transactions found",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Try a different search term",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            } else if (searchResults.isEmpty()) {
                // Initial state - prompt to search
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Search your transactions",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Search by merchant, note, category, or amount",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            } else {
                // Results count header
                // Results count and Total header
                Row(
                   modifier = Modifier
                       .fillMaxWidth()
                       .padding(bottom = 8.dp),
                   horizontalArrangement = Arrangement.SpaceBetween,
                   verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${searchResults.size} result${if (searchResults.size != 1) "s" else ""}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val total = viewModel.totalAmount.collectAsState().value
                    Text(
                        text = "Total: ₹${String.format(Locale.getDefault(), "%,.0f", total / 100.0)}",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (total == 0L) MaterialTheme.colorScheme.onSurface else if (total > 0) Color(0xFF2E7D32) else Color(0xFFC62828)
                    )
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(searchResults) { transactionWithCategory ->
                        SearchTransactionItem(
                            transactionWithCategory = transactionWithCategory,
                            onClick = { onTransactionClick(transactionWithCategory) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SearchTransactionItem(
    transactionWithCategory: TransactionWithCategory,
    onClick: () -> Unit
) {
    val txn = transactionWithCategory.transaction
    val category = transactionWithCategory.category

    // Determine color based on transaction type
    val amountColor = when (txn.transactionType) {
        TransactionType.INCOME, TransactionType.CASHBACK, TransactionType.REFUND -> Color(0xFF2E7D32) // Green
        TransactionType.EXPENSE -> Color(0xFFC62828) // Red
        TransactionType.TRANSFER, TransactionType.INVESTMENT_CONTRIBUTION, TransactionType.INVESTMENT_OUTFLOW -> Color(0xFF6A1B9A) // Purple
        else -> MaterialTheme.colorScheme.onSurface
    }

    // Determine sign prefix
    val signPrefix = when (txn.transactionType) {
        TransactionType.INCOME, TransactionType.CASHBACK, TransactionType.REFUND -> "+"
        TransactionType.EXPENSE, TransactionType.INVESTMENT_CONTRIBUTION, TransactionType.LIABILITY_PAYMENT -> "-"
        else -> ""
    }

    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                // Merchant/Category name
                Text(
                    text = txn.merchantName ?: category.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Category chip + Date row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Category chip
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = category.name,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Date
                    Text(
                        text = java.time.Instant.ofEpochMilli(txn.timestamp)
                            .atZone(java.time.ZoneId.systemDefault())
                            .format(java.time.format.DateTimeFormatter.ofPattern("MMM dd, yyyy")),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Note if present
                if (!txn.note.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = txn.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Amount with color
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "$signPrefix₹${String.format(Locale.getDefault(), "%,.2f", txn.amountPaisa / 100.0)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = amountColor
                )
            }
        }
    }
}
