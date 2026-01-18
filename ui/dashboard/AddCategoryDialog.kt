package com.saikumar.expensetracker.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.saikumar.expensetracker.data.entity.CategoryType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCategoryDialog(
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
