package com.saikumar.expensetracker.ui.dashboard

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.saikumar.expensetracker.data.db.TransactionWithCategory
import com.saikumar.expensetracker.data.entity.CategoryType
import com.saikumar.expensetracker.ui.common.TransactionEditDialog
import com.saikumar.expensetracker.ui.components.SortOption
import com.saikumar.expensetracker.ui.components.TransactionSortSelector

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FilteredTransactionsScreen(
    viewModel: FilteredTransactionsViewModel,
    categoryType: CategoryType,
    startDate: Long,
    endDate: Long,
    categoryName: String? = null,
    onNavigateBack: () -> Unit
) {
    LaunchedEffect(categoryType, startDate, endDate, categoryName) {
        viewModel.setFilter(categoryType, startDate, endDate, categoryName)
    }

    val transactions by viewModel.transactions.collectAsState()
    val categories by viewModel.categories.collectAsState()
    var editingTransaction by remember { mutableStateOf<TransactionWithCategory?>(null) }
    
    // Sort state
    var sortOption by remember { mutableStateOf(SortOption.DATE_DESC) }
    
    // Apply sorting
    val sortedTransactions = remember(transactions, sortOption) {
        when (sortOption) {
            SortOption.DATE_DESC -> transactions.sortedByDescending { it.transaction.timestamp }
            SortOption.DATE_ASC -> transactions.sortedBy { it.transaction.timestamp }
            SortOption.AMOUNT_DESC -> transactions.sortedByDescending { it.transaction.amountPaisa }
            SortOption.AMOUNT_ASC -> transactions.sortedBy { it.transaction.amountPaisa }
        }
    }
    
    val listState = rememberLazyListState()

    if (editingTransaction != null) {
        TransactionEditDialog(
            transaction = editingTransaction!!,
            categories = categories,
            onDismiss = { editingTransaction = null },
            onConfirm = { categoryId, note, accountType, updateSimilar, manualClassification, newMerchant ->
                viewModel.updateTransactionDetails(
                    editingTransaction!!.transaction,
                    categoryId,
                    note,
                    accountType,
                    updateSimilar,
                    manualClassification,
                    newMerchant
                )
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Text(if (categoryName != null) {
                        categoryName
                    } else {
                        when (categoryType) {
                            CategoryType.INCOME -> "Income"
                            CategoryType.FIXED_EXPENSE -> "Fixed Expenses"
                            CategoryType.VARIABLE_EXPENSE -> "Variable Expenses"
                            CategoryType.INVESTMENT -> "Investments"
                            CategoryType.VEHICLE -> "Vehicle"
                            CategoryType.IGNORE -> "Invalid/Ignore"
                            CategoryType.STATEMENT -> "Statements"
                            CategoryType.LIABILITY -> "CC Bill Payments"
                            CategoryType.TRANSFER -> "Transfers"
                        }
                    })
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        if (transactions.isEmpty()) {
            Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No transactions in this category")
            }
        } else {
            Column(modifier = Modifier.padding(padding).fillMaxSize()) {
                // Sort selector row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "${sortedTransactions.size} transactions",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    TransactionSortSelector(
                        currentSort = sortOption,
                        onSortChange = { sortOption = it }
                    )
                }
                
                // Group transactions by date for better density
                val groupedTransactions = remember(sortedTransactions) {
                    sortedTransactions.groupBy { 
                        java.time.Instant.ofEpochMilli(it.transaction.timestamp)
                            .atZone(java.time.ZoneId.systemDefault())
                            .toLocalDate()
                    }
                }
                
                // Transaction list with scrollbar
                Box(modifier = Modifier.weight(1f)) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(bottom = 16.dp),
                        // Same 8dp row rhythm as the Home feed - this list used 4dp,
                        // making identical rows feel cramped only on this screen
                        verticalArrangement = Arrangement.spacedBy(com.saikumar.expensetracker.ui.theme.Dimens.ItemSpacing)
                    ) {
                        groupedTransactions.forEach { (date, transactions) ->
                            // Date headers styled identically to the Home feed's (plain
                            // uppercase label) - this screen previously used tonal pill
                            // headers, a second date-header style for the same kind of list.
                            stickyHeader {
                                Surface(color = MaterialTheme.colorScheme.background) {
                                    Text(
                                        text = date.format(java.time.format.DateTimeFormatter.ofPattern("EEE, d MMM")).uppercase(),
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 20.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
                                    )
                                }
                            }
                            
                            items(
                                transactions, 
                                key = { it.transaction.id }
                            ) { transaction ->
                                TransactionItem(
                                    transaction,
                                    onClick = { editingTransaction = transaction },
                                    // Horizontal padding comes from TransactionItem itself now
                                    modifier = Modifier.animateItemPlacement()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
