package com.saikumar.expensetracker.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
    // (categoryId, note, accountType, applyToSimilar, manualClassification, newMerchantName)
    onConfirm: (Long, String, AccountType, Boolean, String?, String?) -> Unit,
    onDelete: (Transaction) -> Unit,
    onAddCategory: ((String, CategoryType) -> Unit)? = null,
    onFindSimilar: (suspend () -> com.saikumar.expensetracker.sms.SimilarityResult)? = null
) {
    var selectedCategory by remember { mutableStateOf(transaction.category) }
    // Type is fully derived from the chosen category - no separate Type picker.
    // (The save path already ignored any manually-picked type and derived it via
    // deriveClassificationFromCategory, so the old Type dropdown was pure friction.)
    val selectedType = selectedCategory.type
    var note by remember { mutableStateOf(transaction.transaction.note ?: "") }
    var merchantNameEdit by remember { mutableStateOf(transaction.transaction.merchantName ?: "") }
    // Classification auto-derived from category when saving
    val accountType by remember { mutableStateOf(transaction.transaction.accountType) }
    var applyToSimilar by remember { mutableStateOf(false) }
    
    var categoryExpanded by remember { mutableStateOf(false) }
    var showAddCategoryDialog by remember { mutableStateOf(false) }
    // Classification is auto-derived from category (see deriveClassificationFromCategory)
    
    val merchantKeyword = transaction.transaction.merchantName
    val isUnknownCategory = selectedCategory.name.contains("Unknown", ignoreCase = true)

    if (showAddCategoryDialog && onAddCategory != null) {
        AddCategoryDialog(
            onDismiss = { showAddCategoryDialog = false },
            onConfirm = { name, type ->
                onAddCategory(name, type)
                showAddCategoryDialog = false
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
                // Header: category-colored avatar, amount as the anchor, and the context the
                // old dialog never showed - WHEN it happened and on WHICH account.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val avatarColor = com.saikumar.expensetracker.util.CategoryColors.getColor(selectedCategory.name)
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(avatarColor.copy(alpha = 0.18f))
                    ) {
                        Icon(
                            CategoryIcons.getIcon(selectedCategory.name),
                            contentDescription = null,
                            tint = avatarColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                formatAmount(transaction.transaction.amountPaisa),
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                                shape = MaterialTheme.shapes.small
                            ) {
                                Text(
                                    transaction.transaction.transactionType.name
                                        .lowercase().replace('_', ' ')
                                        .replaceFirstChar { it.uppercaseChar() },
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        val meta = buildString {
                            append(
                                java.time.Instant.ofEpochMilli(transaction.transaction.timestamp)
                                    .atZone(java.time.ZoneId.systemDefault())
                                    .format(java.time.format.DateTimeFormatter.ofPattern("EEE, d MMM yyyy · h:mm a"))
                            )
                            transaction.transaction.accountNumberLast4?.let { append("  ·  A/c ••").append(it) }
                        }
                        Text(
                            meta,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
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
                
                // Editable merchant/counterparty name. For anonymous QR payments (paytmqr…,
                // q…@ybl) the SMS carries no name, so naming it here - and, when there's a VPA,
                // remembering it for every past & future payment to that same QR - is the only
                // way to give it a real identity.
                val vpa = transaction.transaction.upiId
                run {
                    val label = when (transaction.transaction.transactionType) {
                        TransactionType.INCOME, TransactionType.CASHBACK, TransactionType.REFUND -> "From (name)"
                        TransactionType.EXPENSE, TransactionType.LIABILITY_PAYMENT -> "To (name)"
                        else -> "Merchant / name"
                    }
                    OutlinedTextField(
                        value = merchantNameEdit,
                        onValueChange = { merchantNameEdit = it },
                        label = { Text(label) },
                        placeholder = { Text(vpa ?: "e.g. Local Kirana Store") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (!vpa.isNullOrBlank()) {
                        Text(
                            text = "Applies to all payments to $vpa",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp)
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
                
                // QUICK PICKS: one-tap chips for the categories corrections most often land in,
                // so the common case never needs the full dropdown.
                run {
                    val quickNames = listOf(
                        "Groceries", "Dining Out", "Food Delivery", "Shopping",
                        "Entertainment", "Medical", "Fuel", "Transportation",
                        "Subscriptions", "P2P Transfers"
                    )
                    val quickPicks = quickNames.mapNotNull { name -> categories.find { it.name == name } }
                    if (quickPicks.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState())
                        ) {
                            quickPicks.forEach { cat ->
                                FilterChip(
                                    selected = selectedCategory.id == cat.id,
                                    onClick = { selectedCategory = cat },
                                    label = { Text(cat.name, style = MaterialTheme.typography.labelMedium) },
                                    leadingIcon = {
                                        Icon(
                                            CategoryIcons.getIcon(cat.name),
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                )
                            }
                        }
                    }
                }

                // SINGLE grouped category picker. The old two-step (Category Type dropdown ->
                // Category dropdown) forced two decisions where one suffices: the type is a
                // property OF the category, so picking "Groceries" already implies Variable
                // Expense. Categories are listed grouped by type with section headers.
                val groupOrder = listOf(
                    CategoryType.VARIABLE_EXPENSE to "🛒 Variable Expenses",
                    CategoryType.FIXED_EXPENSE to "🏠 Fixed Expenses",
                    CategoryType.VEHICLE to "🚗 Vehicle",
                    CategoryType.INCOME to "📈 Income",
                    CategoryType.INVESTMENT to "📊 Investments",
                    CategoryType.LIABILITY to "💳 CC Bill Payment",
                    CategoryType.TRANSFER to "↔️ Transfer",
                    CategoryType.STATEMENT to "📄 Statement",
                    CategoryType.IGNORE to "🚫 Invalid/Ignore"
                )

                // Clickable field -> full searchable picker (type-to-filter beats scrolling
                // a 60-item dropdown).
                Box {
                    OutlinedTextField(
                        value = selectedCategory.name,
                        onValueChange = {},
                        readOnly = true,
                        enabled = false,
                        label = { Text("Category") },
                        leadingIcon = {
                            Icon(
                                CategoryIcons.getIcon(selectedCategory.name),
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        supportingText = {
                            Text(groupOrder.firstOrNull { it.first == selectedType }?.second ?: selectedType.name)
                        },
                        trailingIcon = { Icon(Icons.Default.ExpandMore, contentDescription = null) },
                        // Disabled only to route taps to the overlay - repaint as enabled
                        colors = OutlinedTextFieldDefaults.colors(
                            disabledTextColor = MaterialTheme.colorScheme.onSurface,
                            disabledBorderColor = MaterialTheme.colorScheme.outline,
                            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledTrailingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            disabledSupportingTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { categoryExpanded = true }
                    )
                }

                if (categoryExpanded) {
                    CategoryPickerDialog(
                        categories = categories,
                        selectedCategory = selectedCategory,
                        groupOrder = groupOrder,
                        onPick = {
                            selectedCategory = it
                            categoryExpanded = false
                        },
                        onDismiss = { categoryExpanded = false }
                    )
                }
                
                // Add New Category button
                if (onAddCategory != null) {
                    TextButton(
                        onClick = { showAddCategoryDialog = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Add New Category")
                    }
                }
                
                OutlinedTextField(value = note, onValueChange = { note = it }, label = { Text("Note") }, modifier = Modifier.fillMaxWidth())

                // How this row counts is fully derived from the category - a subtle helper
                // line states the effect so there's nothing extra to configure.
                Text(
                    when {
                        selectedCategory.type == CategoryType.TRANSFER ->
                            "Counted as a transfer - excluded from income & expense totals"
                        selectedCategory.type == CategoryType.INCOME ->
                            "Counts towards income"
                        selectedCategory.type == CategoryType.INVESTMENT ->
                            "Counts as an investment (not spending)"
                        selectedCategory.type == CategoryType.LIABILITY ->
                            "Card bill payment - not counted as spending"
                        else -> "Counts towards spending"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // Classification is now auto-derived from category (no manual buttons needed)
                // The derivedClassification is computed when saving based on selectedCategory
                // Apply to similar transactions option - with smart matching
                // Determine match pattern based on available transaction data (priority order)
                val storedUpiId = transaction.transaction.upiId

                // Extract NEFT bank code from SMS (e.g., DEUTN52025... → DEUT)
                val smsContent = transaction.transaction.smsSnippet ?: ""
                val neftBankCode = if (smsContent.contains("NEFT", ignoreCase = true)) {
                    val neftRefRegex = Regex("(?i)NEFT[\\s-]+(?:Cr[\\s-]+)?([A-Z0-9]+)")
                    val neftRef = neftRefRegex.find(smsContent)?.groupValues?.getOrNull(1)
                    neftRef?.take(4)?.uppercase(Locale.getDefault()) // First 4 letters = bank code
                } else null

                // Priority: Merchant > UPI ID > NEFT > Nothing
                val matchPattern = merchantKeyword ?: storedUpiId ?: neftBankCode ?: ""
                val matchType = when {
                    merchantKeyword != null -> "Merchant"
                    storedUpiId != null -> "UPI ID"
                    neftBankCode != null -> "NEFT Source"
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
                                android.util.Log.e("TransactionEditDialog", "Failed to find similar transactions", e)
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
                
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                // Classification follows the category. When the category was NOT changed,
                // keep the row's current type untouched - a note/name-only edit must not
                // retype a TRANSFER (e.g. a linked loan-return) into an EXPENSE.
                val derivedClassification = if (selectedCategory.id == transaction.category.id) {
                    transaction.transaction.transactionType.name
                } else {
                    deriveClassificationFromCategory(selectedCategory)
                }
                val cleanedMerchant = merchantNameEdit.trim().ifBlank { null }
                onConfirm(selectedCategory.id, note, accountType, applyToSimilar, derivedClassification, cleanedMerchant)
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

/**
 * Searchable category picker: type a few letters instead of scrolling ~60 entries.
 * Results stay grouped by type; empty query shows the full grouped list.
 */
@Composable
internal fun CategoryPickerDialog(
    categories: List<Category>,
    selectedCategory: Category,
    groupOrder: List<Pair<CategoryType, String>>,
    onPick: (Category) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose Category") },
        text = {
            Column {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search categories…") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(20.dp)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                val anyMatch = categories.any { it.name.contains(query.trim(), ignoreCase = true) }
                androidx.compose.foundation.lazy.LazyColumn(modifier = Modifier.heightIn(max = 380.dp)) {
                    groupOrder.forEach { (type, label) ->
                        val group = categories
                            .filter { it.type == type && (query.isBlank() || it.name.contains(query.trim(), ignoreCase = true)) }
                            .sortedBy { it.name }
                        if (group.isNotEmpty()) {
                            item(key = "hdr_$type") {
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp)
                                )
                            }
                            items(group.size, key = { "cat_${group[it].id}" }) { i ->
                                val category = group[i]
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onPick(category) }
                                        .padding(horizontal = 4.dp, vertical = 10.dp)
                                ) {
                                    Icon(
                                        CategoryIcons.getIcon(category.name),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = com.saikumar.expensetracker.util.CategoryColors.getColor(category.name)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Text(category.name, modifier = Modifier.weight(1f))
                                    if (category.id == selectedCategory.id) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = "Selected",
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                    if (!anyMatch && query.isNotBlank()) {
                        item {
                            Text(
                                "No categories match \"${query.trim()}\"",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

// Local helper for formatting amount - consistent format with decimals
private fun formatAmount(paisa: Long): String =
    com.saikumar.expensetracker.util.CurrencyFormatter.formatPaisa(paisa, showDecimals = true)

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
