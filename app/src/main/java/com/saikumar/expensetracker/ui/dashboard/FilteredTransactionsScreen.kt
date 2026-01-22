package com.saikumar.expensetracker.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.saikumar.expensetracker.data.db.TransactionWithCategory
import com.saikumar.expensetracker.data.entity.CategoryType
import com.saikumar.expensetracker.ui.common.TransactionEditDialog

@OptIn(ExperimentalMaterial3Api::class)
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

    if (editingTransaction != null) {
        TransactionEditDialog(
            transaction = editingTransaction!!,
            categories = categories,
            onDismiss = { editingTransaction = null },
            onConfirm = { categoryId, note, accountType, updateSimilar, manualClassification ->
                viewModel.updateTransactionDetails(
                    editingTransaction!!.transaction,
                    categoryId,
                    note,
                    accountType,
                    manualClassification
                )
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
            Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = androidx.compose.ui.Alignment.Center) {
                Text("No transactions in this category")
            }
        } else {
            LazyColumn(
                modifier = Modifier.padding(padding).fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(transactions, key = { it.transaction.id }) { transaction ->
                    TransactionItem(transaction, onClick = { editingTransaction = transaction })
                }
            }
        }
    }
}
