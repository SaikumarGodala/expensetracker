package com.saikumar.expensetracker.ui.common

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.saikumar.expensetracker.data.db.TransactionWithCategory
import com.saikumar.expensetracker.data.entity.AccountType
import com.saikumar.expensetracker.data.entity.Category
import com.saikumar.expensetracker.data.entity.CategoryType
import com.saikumar.expensetracker.data.entity.Transaction
import com.saikumar.expensetracker.data.entity.TransactionSource
import com.saikumar.expensetracker.data.entity.TransactionType
import com.saikumar.expensetracker.ui.dashboard.AddCategoryDialog
import com.saikumar.expensetracker.util.CategoryIcons
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionEditDialog(
    transaction: TransactionWithCategory, 
    categories: List<Category>, 
    onDismiss: () -> Unit, 
    onConfirm: (Long, String, AccountType, Boolean, String?) -> Unit,
    onDelete: (Transaction) -> Unit,
    onAddCategory: ((String, CategoryType) -> Unit)? = null,
    onFindSimilar: (suspend () -> com.saikumar.expensetracker.sms.SimilarityResult)? = null
) {
    var selectedCategory by remember { mutableStateOf(transaction.category) }
    var selectedType by remember { mutableStateOf(transaction.category.type) }
    var note by remember { mutableStateOf(transaction.transaction.note ?: "") }
    // Classification auto-derived from category when saving
    val accountType by remember { mutableStateOf(transaction.transaction.accountType) }
    var applyToSimilar by remember { mutableStateOf(false) }
    
    // Explicit Transfer toggle (Checkbox)
    // Initialize based on current transaction type or category type
    var isTransferChecked by remember { mutableStateOf(
        transaction.transaction.transactionType == TransactionType.TRANSFER || 
        transaction.category.type == CategoryType.TRANSFER
    ) }

    // P2P override: allows user to mark P2P as income/expense instead of neutral transfer
    var p2pOverride by remember { mutableStateOf<String?>(null) } // "INCOME", "EXPENSE", or null (neutral)
    var typeExpanded by remember { mutableStateOf(false) }
    var categoryExpanded by remember { mutableStateOf(false) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    // Classification is auto-derived from category (see deriveClassificationFromCategory)
    
    val merchantKeyword = transaction.transaction.merchantName
    val isUnknownCategory = selectedCategory.name.contains("Unknown", ignoreCase = true)

    if (showAddCategoryDialog && onAddCategory != null) {
        AddCategoryDialog(
            onDismiss = { },
            onConfirm = { name, type ->
                onAddCategory(name, type)
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Transaction Details") },
        text = {
            Box(modifier = Modifier.heightIn(max = 450.dp)) {
                val dialogScrollState = rememberScrollState()
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.verticalScroll(dialogScrollState)
                ) {
                // Amount display - convert paisa to rupees
                Text("Amount: ${formatAmount(transaction.transaction.amountPaisa)}", style = MaterialTheme.typography.titleMedium)
                
                Text("Transaction Type: ${transaction.transaction.transactionType.name}", style = MaterialTheme.typography.bodySmall)
                
                // Warning if TransactionType and CategoryType are misaligned
                val txnType = transaction.transaction.transactionType
                val isIncomeButExpenseCategory = (txnType == TransactionType.INCOME || txnType == TransactionType.CASHBACK) &&
                    (selectedType == CategoryType.FIXED_EXPENSE || selectedType == CategoryType.VARIABLE_EXPENSE || selectedType == CategoryType.VEHICLE)
                val isExpenseButIncomeCategory = (txnType == TransactionType.EXPENSE) && selectedType == CategoryType.INCOME
                
                if (isIncomeButExpenseCategory || isExpenseButIncomeCategory) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "⚠️ Category type doesn't match transaction type. This may cause incorrect totals.",
                            modifier = Modifier.padding(8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                
                // Show merchant if available
                if (merchantKeyword != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "Merchant: $merchantKeyword",
                            modifier = Modifier.padding(8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }
                
                // SMS Context/Snippet display - useful for debugging and trust
                val smsSnippet = transaction.transaction.smsSnippet
                val fullSmsBody = transaction.transaction.fullSmsBody
                if ((!smsSnippet.isNullOrBlank() || !fullSmsBody.isNullOrBlank()) && transaction.transaction.source == TransactionSource.SMS) {
                    var isExpanded by remember { mutableStateOf(false) }
                    
                    // Use full raw message when expanded, fallback to snippet
                    val expandedMessage = fullSmsBody ?: smsSnippet ?: ""
                    val collapsedMessage = smsSnippet ?: fullSmsBody?.take(77)?.plus("...") ?: ""
                    
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth().clickable { isExpanded = !isExpanded }
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isExpanded) "📱 Full Raw Message" else "📱 Message Preview",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                                    modifier = Modifier.size(16.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            // Scrollable text box when expanded - shows full raw message
                            if (isExpanded) {
                                val messageScrollState = rememberScrollState()
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(max = 200.dp)
                                        .verticalScroll(messageScrollState)
                                ) {
                                    Text(
                                        text = expandedMessage,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                Text(
                                    text = if (collapsedMessage.length <= 80) collapsedMessage else collapsedMessage.take(77) + "...",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }
                
                // Unknown category alert
                if (isUnknownCategory) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "⚠️ This transaction could not be auto-categorized. Please select a category below.",
                            modifier = Modifier.padding(8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
                
                // Two-dropdown approach: Type first, then Category
                // 1. Type Dropdown (5 options)
                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = !typeExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = when (selectedType) {
                            CategoryType.INCOME -> "📈 Income"
                            CategoryType.FIXED_EXPENSE -> "🏠 Fixed Expenses"
                            CategoryType.VARIABLE_EXPENSE -> "🛒 Variable Expenses"
                            CategoryType.INVESTMENT -> "📊 Investments"
                            CategoryType.VEHICLE -> "🚗 Vehicle"
                            CategoryType.STATEMENT -> "📄 Statement"
                            CategoryType.LIABILITY -> "💳 CC Bill Payment"
                            CategoryType.TRANSFER -> "↔️ Transfer"
                            CategoryType.IGNORE -> "🚫 Invalid/Ignore"
                        },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier.fillMaxWidth().menuAnchor(type = MenuAnchorType.PrimaryEditable, enabled = true)
                    )
                    ExposedDropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false }
                    ) {
                        CategoryType.entries.forEach { type ->
                            val typeLabel = when (type) {
                                CategoryType.INCOME -> "📈 Income"
                                CategoryType.FIXED_EXPENSE -> "🏠 Fixed Expenses"
                                CategoryType.VARIABLE_EXPENSE -> "🛒 Variable Expenses"
                                CategoryType.INVESTMENT -> "📊 Investments"
                                CategoryType.VEHICLE -> "🚗 Vehicle"
                                CategoryType.STATEMENT -> "📄 Statement"
                                CategoryType.LIABILITY -> "💳 CC Bill Payment"
                                CategoryType.TRANSFER -> "↔️ Transfer"
                                CategoryType.IGNORE -> "🚫 Invalid/Ignore"
                            }
                            DropdownMenuItem(
                                text = { Text(typeLabel) },
                                onClick = {
                                    selectedType = type
                                    typeExpanded = false
                                    // Auto-select first category of new type
                                    val firstOfType = categories.find { it.type == type }
                                    if (firstOfType != null) {
                                        selectedCategory = firstOfType
                                    }
                                }
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 2. Category Dropdown (filtered by selected type)
                val filteredCategories = categories.filter { it.type == selectedType }.sortedBy { it.name }
                
                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = !categoryExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedCategory.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        leadingIcon = { 
                            Icon(
                                CategoryIcons.getIcon(selectedCategory.name), 
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) },
                        colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                        modifier = Modifier.fillMaxWidth().menuAnchor(type = MenuAnchorType.PrimaryEditable, enabled = true)
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        filteredCategories.forEach { category ->
                            DropdownMenuItem(
                                text = { 
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            CategoryIcons.getIcon(category.name), 
                                            contentDescription = null,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(category.name)
                                    }
                                },
                                onClick = {
                                    selectedCategory = category
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }
                
                // Add New Category button
                if (onAddCategory != null) {
                    TextButton(
                        onClick = { },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add New Category")
                    }
                }
                
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth())

                // Mark as Transfer Checkbox
                Surface(
                    color = if (isTransferChecked) MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f) else Color.Transparent,
                    shape = MaterialTheme.shapes.small,
                    modifier = Modifier.fillMaxWidth().clickable { isTransferChecked = !isTransferChecked }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
                    ) {
                        Checkbox(
                            checked = isTransferChecked,
                            onCheckedChange = { isTransferChecked = it }
                        )
                        Column {
                            Text(
                                "Mark as Transfer",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            if (isTransferChecked) {
                                Text(
                                    "Will be excluded from Income/Expense totals (Neutral)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                
                // P2P Transaction Type Override
                // Show when category is P2P Transfers - allows user to mark as income/expense
                val isP2PCategory = selectedCategory.name.contains("P2P", ignoreCase = true) || 
                                     selectedCategory.name.contains("Transfer", ignoreCase = true)
                if (isP2PCategory) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.5f),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                "🔀 How should this P2P transfer be counted?",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                // Transfer (Neutral) option
                                FilterChip(
                                    selected = p2pOverride == null,
                                    onClick = { p2pOverride = null },
                                    label = { Text("Transfer") },
                                    leadingIcon = if (p2pOverride == null) {
                                        { Icon(Icons.Default.Check, contentDescription = null, Modifier.size(16.dp)) }
                                    } else null
                                )
                                // Income option
                                FilterChip(
                                    selected = p2pOverride == "INCOME",
                                    onClick = { p2pOverride = "INCOME" },
                                    label = { Text("Income") },
                                    leadingIcon = if (p2pOverride == "INCOME") {
                                        { Icon(Icons.Default.Check, contentDescription = null, Modifier.size(16.dp)) }
                                    } else null
                                )
                                // Expense option
                                FilterChip(
                                    selected = p2pOverride == "EXPENSE",
                                    onClick = { p2pOverride = "EXPENSE" },
                                    label = { Text("Expense") },
                                    leadingIcon = if (p2pOverride == "EXPENSE") {
                                        { Icon(Icons.Default.Check, contentDescription = null, Modifier.size(16.dp)) }
                                    } else null
                                )
                            }
                            Text(
                                when (p2pOverride) {
                                    "INCOME" -> "Will count towards income totals"
                                    "EXPENSE" -> "Will count towards expense totals"
                                    else -> "Won't affect income or expense totals"
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onTertiaryContainer
                            )
                        }
                    }
                }
                
                // Classification is now auto-derived from category (no manual buttons needed)
                // The derivedClassification is computed when saving based on selectedCategory
                // Apply to similar transactions option - with smart matching
                // Show if: merchant keyword exists AND (category changed OR has matchable pattern)
                val smsContent = transaction.transaction.smsSnippet ?: ""
                
                // Extract UPI ID
                val upiId = if (smsContent.contains("@") && !smsContent.contains("@gmail") && !smsContent.contains("@yahoo")) {
                    val regex = Regex("([a-zA-Z0-9._-]+@[a-zA-Z]+)")
                    regex.find(smsContent)?.value?.lowercase(Locale.getDefault())
                } else null
                
                // Extract NEFT bank code (e.g., DEUTN52025... → DEUT)
                val neftBankCode = if (smsContent.contains("NEFT", ignoreCase = true)) {
                    val neftRefRegex = Regex("(?i)NEFT[\\s-]+(?:Cr[\\s-]+)?([A-Z0-9]+)")
                    val neftRef = neftRefRegex.find(smsContent)?.groupValues?.getOrNull(1)
                    neftRef?.take(4)?.uppercase(Locale.getDefault()) // First 4 letters = bank code
                } else null
                
                val matchPattern = upiId ?: neftBankCode ?: merchantKeyword?.uppercase(Locale.getDefault()) ?: ""
                val matchType = when {
                    upiId != null -> "UPI ID"
                    neftBankCode != null -> "NEFT Source"
                    merchantKeyword != null -> "Merchant"
                    else -> "Amount + Date"  // Fallback for transactions without clear pattern
                }
                
                // Show Apply to Similar whenever category is changed
                // Even if no pattern is found, user can still choose to apply
                if (selectedCategory.id != transaction.category.id) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    
                    var similarTransactions by remember { mutableStateOf<List<Transaction>>(emptyList()) }
                    var isSearching by remember { mutableStateOf(false) }
                    
                    // Fetch similar transactions when checked
                    LaunchedEffect(applyToSimilar) {
                        if (applyToSimilar && onFindSimilar != null) {
                            isSearching = true
                            try {
                                val result = onFindSimilar()
                                // Filter out the current transaction
                                similarTransactions = result.matchedTransactions.filter { it.id != transaction.transaction.id }
                            } catch (e: Exception) {
                                // Ignore error
                            } finally {
                                isSearching = false
                            }
                        } else {
                            similarTransactions = emptyList()
                        }
                    }
                    
                    Surface(
                        color = if (applyToSimilar) 
                            MaterialTheme.colorScheme.primaryContainer 
                        else 
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = MaterialTheme.shapes.small,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Checkbox(
                                    checked = applyToSimilar, 
                                    onCheckedChange = { applyToSimilar = it }
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        "🔄 Apply to similar transactions",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        "Match by $matchType: $matchPattern",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            
                            if (applyToSimilar) {
                                Spacer(modifier = Modifier.height(8.dp))
                                
                                if (isSearching) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Searching...", style = MaterialTheme.typography.bodySmall)
                                    }
                                } else if (similarTransactions.isNotEmpty()) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.surface,
                                        shape = MaterialTheme.shapes.extraSmall,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Column(modifier = Modifier.padding(8.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                modifier = Modifier.padding(bottom = 4.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.Info,
                                                    contentDescription = null,
                                                    modifier = Modifier.size(16.dp),
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Spacer(modifier = Modifier.width(8.dp))
                                                Text(
                                                    "Found ${similarTransactions.size} other transactions:",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                            
                                            // Show up to 5 transactions
                                            similarTransactions.take(5).forEach { similar ->
                                                 Row(
                                                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        text = java.time.Instant.ofEpochMilli(similar.timestamp)
                                                            .atZone(java.time.ZoneId.systemDefault())
                                                            .format(java.time.format.DateTimeFormatter.ofPattern("dd MMM")),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                                    )
                                                    Text(
                                                        text = formatAmount(similar.amountPaisa),
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Medium
                                                    )
                                                }
                                            }
                                            
                                            if (similarTransactions.size > 5) {
                                                Text(
                                                    "...and ${similarTransactions.size - 5} more",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    modifier = Modifier.padding(top = 2.dp),
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text(
                                                "All will be changed to \"${selectedCategory.name}\"",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                            )
                                        }
                                    }
                                } else {
                                     Text(
                                        "No other similar transactions found.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(start = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                
                Text("This transaction will not affect income totals unless marked.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { 
                // Derive classification from category type and name
                // P2P override takes precedence if user explicitly selected income/expense
                // Checkbox "Mark as Transfer" takes highest precedence
                val derivedClassification = if (isTransferChecked) {
                    "TRANSFER" // Force TRANSFER type
                } else {
                    p2pOverride ?: deriveClassificationFromCategory(selectedCategory)
                }
                
                onConfirm(selectedCategory.id, note, accountType, applyToSimilar, derivedClassification) 
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                TextButton(
                    onClick = { onDelete(transaction.transaction) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        }
    )
}

// Local helper for formatting amount - consistent format with decimals
private fun formatAmount(paisa: Long): String {
    val rupees = paisa / 100.0
    return "₹${String.format(Locale.getDefault(), "%,.2f", rupees)}"
}

/**
 * Derives the transaction classification from category type and name.
 * This eliminates the need for manual classification buttons.
 */
private fun deriveClassificationFromCategory(category: Category): String {
    val categoryName = category.name.uppercase()
    
    // Special category names override type
    return when {
        // Transfer categories - use TRANSFER type which is recognized by RuleEngine
        categoryName.contains("TRANSFER") || categoryName.contains("P2P") -> "TRANSFER"
        
        // Liability payment
        categoryName.contains("CREDIT") && categoryName.contains("BILL") -> "LIABILITY_PAYMENT"
        categoryName.contains("CC BILL") || categoryName.contains("CARD PAYMENT") -> "LIABILITY_PAYMENT"
        
        // Refund categories
        categoryName.contains("REFUND") || categoryName.contains("REVERSAL") || categoryName.contains("CASHBACK") -> "REFUND"
        
        // Ignored categories
        categoryName.contains("IGNORED") || categoryName.contains("SPAM") -> "IGNORE"
        
        // Pending categories
        categoryName.contains("PENDING") || categoryName.contains("UPCOMING") || categoryName.contains("SCHEDULED") -> "PENDING"
        
        // Investment based on type - use actual enum value
        category.type == CategoryType.INVESTMENT -> "INVESTMENT_OUTFLOW"
        
        // Income based on type
        category.type == CategoryType.INCOME -> "INCOME"
        
        // All expense types
        category.type == CategoryType.FIXED_EXPENSE || 
        category.type == CategoryType.VARIABLE_EXPENSE || 
        category.type == CategoryType.VEHICLE -> "EXPENSE"
        
        // Transfer type - use TRANSFER which is recognized by RuleEngine
        category.type == CategoryType.TRANSFER -> "TRANSFER"
        
        // Default
        else -> "EXPENSE"
    }
}
