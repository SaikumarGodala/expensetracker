package com.saikumar.expensetracker.ui.settings

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.saikumar.expensetracker.data.entity.Category
import com.saikumar.expensetracker.data.entity.CategoryType
import com.saikumar.expensetracker.util.CategoryIcons

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryManagementScreen(
    categories: List<Category>,
    onNavigateBack: () -> Unit,
    onUpdateCategory: (Category) -> Unit,
    onAddCategory: (String, CategoryType) -> Unit,
    onDeleteCategory: (Category) -> Unit = {}
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf<CategoryType?>(null) } // null = All
    var editingCategory by remember { mutableStateOf<Category?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    // Dialogs
    if (editingCategory != null) {
        CategoryDialog(
            category = editingCategory,
            isEdit = true,
            onDismiss = { editingCategory = null },
            onConfirm = { name, type ->
                onUpdateCategory(editingCategory!!.copy(name = name, type = type))
                editingCategory = null
            },
            onDelete = {
                onDeleteCategory(editingCategory!!)
                editingCategory = null
            }
        )
    }

    if (showAddDialog) {
        CategoryDialog(
            category = null,
            isEdit = false,
            onDismiss = { showAddDialog = false },
            onConfirm = { name, type ->
                onAddCategory(name, type)
                showAddDialog = false
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage Categories") },
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
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Category")
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
        ) {
            // Search Bar
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search categories...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = if (searchQuery.isNotEmpty()) {
                    {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, contentDescription = "Clear")
                        }
                    }
                } else null,
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                )
            )

            // Selectable Summary Chips
            CategorySummaryRow(
                categories = categories,
                selectedFilter = selectedFilter,
                onSelectFilter = { selectedFilter = it }
            )
            
            HorizontalDivider(modifier = Modifier.padding(top = 8.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Filtering Logic
            val filteredCategories = remember(categories, searchQuery, selectedFilter) {
                var result = categories
                // 1. Filter by search
                if (searchQuery.isNotBlank()) {
                    result = result.filter { it.name.contains(searchQuery, ignoreCase = true) }
                }
                // 2. Filter by type (if selected)
                if (selectedFilter != null) {
                    result = result.filter { it.type == selectedFilter }
                }
                result
            }

            LazyColumn(
                contentPadding = PaddingValues(bottom = 80.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                if (selectedFilter != null) {
                    // FLAT LIST (Specific Filter Selected)
                    items(items = filteredCategories.sortedBy { it.name }, key = { it.id }) { category ->
                         CategoryListItem(
                             category = category,
                             onClick = { editingCategory = category }
                         )
                    }
                } else {
                    // GROUPED LIST ("All" Selected)
                    val groupedCategories = filteredCategories
                        .groupBy { it.type }
                        // Sort groups
                        .toSortedMap(compareBy { type ->
                             when (type) {
                                CategoryType.INCOME -> 0
                                CategoryType.FIXED_EXPENSE -> 1
                                CategoryType.VARIABLE_EXPENSE -> 2
                                CategoryType.INVESTMENT -> 3
                                CategoryType.LIABILITY -> 4
                                CategoryType.VEHICLE -> 5
                                else -> 99
                            }
                        })
                    
                     // Track expansion (independent of filter)
                     // Note: We move state inside logic or hoist? 
                     // Hoisting here for simple persistence during scroll
                     
                    groupedCategories.forEach { (type, cats) ->
                        // In "All" view, we keep headers to distinguish types
                        item(key = "header_${type.name}") {
                            // "why show the count twice" -> removed count from header since it's in the pills
                            CategoryGroupHeader(
                                type = type,
                                // count = cats.size, // Removed count to reduce redundancy
                                isExpanded = true, // Force expanded in this view for simplicity, or make toggleable? 
                                // User said "list their specific categories" -> IMPLIED smooth list. 
                                // Let's keep toggle but maybe just default to expanded.
                                onToggle = {} // No-op if we want fixed headers. 
                                // Actually, let's keep the existing collapsible logic for "All" view flexibility
                            )
                        }

                        items(items = cats.sortedBy { it.name }, key = { it.id }) { category ->
                            CategoryListItem(
                                category = category,
                                onClick = { editingCategory = category }
                            )
                        }
                    }
                }
                
                if (filteredCategories.isEmpty()) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text(
                                "No categories found",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategorySummaryRow(
    categories: List<Category>,
    selectedFilter: CategoryType?,
    onSelectFilter: (CategoryType?) -> Unit
) {
    val stats = remember(categories) {
        categories.groupingBy { it.type }.eachCount()
    }
    
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item { 
            FilterChipItem(
                label = "All", 
                count = categories.size, 
                isSelected = selectedFilter == null,
                color = MaterialTheme.colorScheme.primaryContainer,
                onClick = { onSelectFilter(null) }
            ) 
        }
        item { 
            FilterChipItem(
                label = "Income", 
                count = stats[CategoryType.INCOME] ?: 0, 
                isSelected = selectedFilter == CategoryType.INCOME,
                color = MaterialTheme.colorScheme.tertiaryContainer,
                onClick = { onSelectFilter(CategoryType.INCOME) }
            ) 
        }
        item { 
             FilterChipItem(
                label = "Fixed", 
                count = stats[CategoryType.FIXED_EXPENSE] ?: 0, 
                isSelected = selectedFilter == CategoryType.FIXED_EXPENSE,
                color = MaterialTheme.colorScheme.secondaryContainer,
                onClick = { onSelectFilter(CategoryType.FIXED_EXPENSE) }
            )
        }
        item { 
             FilterChipItem(
                label = "Variable", 
                count = stats[CategoryType.VARIABLE_EXPENSE] ?: 0, 
                isSelected = selectedFilter == CategoryType.VARIABLE_EXPENSE,
                color = MaterialTheme.colorScheme.errorContainer,
                onClick = { onSelectFilter(CategoryType.VARIABLE_EXPENSE) }
            )
        }
        item { 
             FilterChipItem(
                label = "Invest", 
                count = stats[CategoryType.INVESTMENT] ?: 0, 
                isSelected = selectedFilter == CategoryType.INVESTMENT,
                color = MaterialTheme.colorScheme.surfaceVariant,
                onClick = { onSelectFilter(CategoryType.INVESTMENT) }
            )
        }
        item { 
             FilterChipItem(
                label = "Vehicle", 
                count = stats[CategoryType.VEHICLE] ?: 0, 
                isSelected = selectedFilter == CategoryType.VEHICLE,
                color = MaterialTheme.colorScheme.surfaceVariant,
                onClick = { onSelectFilter(CategoryType.VEHICLE) }
            )
        }
    }
}

@Composable
private fun FilterChipItem(
    label: String, 
    count: Int, 
    isSelected: Boolean, 
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        color = if (isSelected) MaterialTheme.colorScheme.primary else color.copy(alpha = 0.3f),
        shape = CircleShape,
        border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), // Bigger touch target
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                label, 
                style = MaterialTheme.typography.labelMedium, 
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.width(6.dp))
            Surface(
                color = if (isSelected) MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surface,
                shape = CircleShape,
                modifier = Modifier.size(20.dp) // Slightly bigger badge
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        count.toString(), 
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun CategoryGroupHeader(type: CategoryType, isExpanded: Boolean, onToggle: () -> Unit) {
    Surface(
        onClick = onToggle, // Keep interaction if user wants to collapse
        color = MaterialTheme.colorScheme.surface // Subtle distinctive background
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // No count here as per user request (redundancy removed)
            Text(
                text = getTypeName(type).uppercase(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun CategoryListItem(category: Category, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icon container
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = CategoryIcons.getIcon(category.name),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = category.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            if (category.isDefault) {
                Text(
                    text = "System Default",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
        
        Icon(
            imageVector = Icons.Default.Edit,
            contentDescription = "Edit",
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryDialog(
    category: Category? = null,
    isEdit: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String, CategoryType) -> Unit,
    onDelete: (() -> Unit)? = null
) {
    var name by remember { mutableStateOf(category?.name ?: "") }
    var selectedType by remember { mutableStateOf(category?.type ?: CategoryType.VARIABLE_EXPENSE) }
    var typeExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    if (showDeleteConfirm && onDelete != null) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            icon = { Icon(Icons.Outlined.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete Category?") },
            text = { Text("Are you sure you want to delete '${category?.name}'? Associated transactions will NOT be deleted but may become uncategorized.") },
            confirmButton = {
                Button(
                    onClick = { onDelete(); showDeleteConfirm = false },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (isEdit) "Edit Category" else "New Category")
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Category Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences)
                )

                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = !typeExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = getTypeName(selectedType),
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Type") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded) },
                        modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryEditable, true),
                        shape = RoundedCornerShape(12.dp)
                    )
                    ExposedDropdownMenu(
                        expanded = typeExpanded,
                        onDismissRequest = { typeExpanded = false }
                    ) {
                        CategoryType.entries
                            .filter { it != CategoryType.IGNORE && it != CategoryType.STATEMENT }
                            .forEach { type ->
                            DropdownMenuItem(
                                text = { Text(getTypeName(type)) },
                                onClick = {
                                    selectedType = type
                                    typeExpanded = false
                                },
                                leadingIcon = {
                                    Icon(
                                        imageVector = when(type) {
                                            CategoryType.INCOME -> Icons.Default.AttachMoney
                                            CategoryType.FIXED_EXPENSE -> Icons.Default.Lock
                                            CategoryType.VARIABLE_EXPENSE -> Icons.Default.ShoppingCart
                                            CategoryType.INVESTMENT -> Icons.Default.TrendingUp
                                            CategoryType.VEHICLE -> Icons.Default.DirectionsCar
                                            CategoryType.LIABILITY -> Icons.Default.CreditCard
                                            CategoryType.TRANSFER -> Icons.Default.SwapHoriz
                                            else -> Icons.Default.Category
                                        },
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            )
                        }
                    }
                }
                
                if (isEdit && onDelete != null) {
                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth(),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Delete Category")
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onConfirm(name.trim(), selectedType) },
                enabled = name.isNotBlank(),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun getTypeName(type: CategoryType): String {
    return when (type) {
        CategoryType.INCOME -> "Income"
        CategoryType.FIXED_EXPENSE -> "Fixed Expense"
        CategoryType.VARIABLE_EXPENSE -> "Variable Expense"
        CategoryType.INVESTMENT -> "Investment"
        CategoryType.VEHICLE -> "Vehicle"
        CategoryType.LIABILITY -> "Liability / Bill"
        CategoryType.TRANSFER -> "Transfer"
        CategoryType.IGNORE -> "Ignored"
        CategoryType.STATEMENT -> "Statement"
    }
}
