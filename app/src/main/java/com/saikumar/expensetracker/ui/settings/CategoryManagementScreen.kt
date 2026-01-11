package com.saikumar.expensetracker.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
    var editingCategory by remember { mutableStateOf<Category?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }

    if (editingCategory != null) {
        EditCategoryDialog(
            category = editingCategory!!,
            onDismiss = { editingCategory = null },
            onConfirm = { updatedCategory ->
                onUpdateCategory(updatedCategory)
                editingCategory = null
            },
            onDelete = {
                onDeleteCategory(editingCategory!!)
                editingCategory = null
            }
        )
    }

    if (showAddDialog) {
        AddNewCategoryDialog(
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
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Edit, contentDescription = "Add Category")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(categories.sortedBy { it.type.ordinal }) { category ->
                CategoryItem(
                    category = category,
                    onEdit = { editingCategory = category }
                )
            }
        }
    }
}

@Composable
fun CategoryItem(category: Category, onEdit: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onEdit)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                CategoryIcons.getIcon(category.name),
                contentDescription = null,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(category.name, style = MaterialTheme.typography.bodyLarge)
                Text(
                    when (category.type) {
                        CategoryType.INCOME -> "Income"
                        CategoryType.FIXED_EXPENSE -> "Fixed Expense"
                        CategoryType.VARIABLE_EXPENSE -> "Variable Expense"
                        CategoryType.INVESTMENT -> "Investment"
                        CategoryType.VEHICLE -> "Vehicle"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, contentDescription = "Edit")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCategoryDialog(
    category: Category,
    onDismiss: () -> Unit,
    onConfirm: (Category) -> Unit,
    onDelete: () -> Unit = {}
) {
    var name by remember { mutableStateOf(category.name) }
    var selectedType by remember { mutableStateOf(category.type) }
    var typeExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    
    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Category?") },
            text = { Text("Are you sure you want to delete '${category.name}'? Transactions will keep their data but show as Unknown.") },
            confirmButton = {
                TextButton(
                    onClick = { 
                        showDeleteConfirm = false
                        onDelete()
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("Cancel") }
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Category Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = !typeExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = when (selectedType) {
                            CategoryType.INCOME -> "Income"
                            CategoryType.FIXED_EXPENSE -> "Fixed Expense"
                            CategoryType.VARIABLE_EXPENSE -> "Variable Expense"
                            CategoryType.INVESTMENT -> "Investment"
                            CategoryType.VEHICLE -> "Vehicle"
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
                            DropdownMenuItem(
                                text = {
                                    Text(when (type) {
                                        CategoryType.INCOME -> "Income"
                                        CategoryType.FIXED_EXPENSE -> "Fixed Expense"
                                        CategoryType.VARIABLE_EXPENSE -> "Variable Expense"
                                        CategoryType.INVESTMENT -> "Investment"
                                        CategoryType.VEHICLE -> "Vehicle"
                                    })
                                },
                                onClick = {
                                    selectedType = type
                                    typeExpanded = false
                                }
                            )
                        }
                    }
                }
                
                // Delete button
                TextButton(
                    onClick = { showDeleteConfirm = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Delete Category")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { 
                    if (name.isNotBlank()) {
                        onConfirm(category.copy(name = name.trim(), type = selectedType))
                    }
                },
                enabled = name.isNotBlank()
            ) { 
                Text("Save") 
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddNewCategoryDialog(
    onDismiss: () -> Unit,
    onConfirm: (name: String, type: CategoryType) -> Unit
) {
    var categoryName by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(CategoryType.VARIABLE_EXPENSE) }
    var typeExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New Category") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                OutlinedTextField(
                    value = categoryName,
                    onValueChange = { categoryName = it },
                    label = { Text("Category Name") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = !typeExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = when (selectedType) {
                            CategoryType.INCOME -> "Income"
                            CategoryType.FIXED_EXPENSE -> "Fixed Expense"
                            CategoryType.VARIABLE_EXPENSE -> "Variable Expense"
                            CategoryType.INVESTMENT -> "Investment"
                            CategoryType.VEHICLE -> "Vehicle"
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
                            DropdownMenuItem(
                                text = {
                                    Text(when (type) {
                                        CategoryType.INCOME -> "Income"
                                        CategoryType.FIXED_EXPENSE -> "Fixed Expense"
                                        CategoryType.VARIABLE_EXPENSE -> "Variable Expense"
                                        CategoryType.INVESTMENT -> "Investment"
                                        CategoryType.VEHICLE -> "Vehicle"
                                    })
                                },
                                onClick = {
                                    selectedType = type
                                    typeExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { 
                    if (categoryName.isNotBlank()) {
                        onConfirm(categoryName.trim(), selectedType) 
                    }
                },
                enabled = categoryName.isNotBlank()
            ) { 
                Text("Add") 
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
