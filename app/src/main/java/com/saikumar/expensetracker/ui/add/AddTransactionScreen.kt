package com.saikumar.expensetracker.ui.add

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.saikumar.expensetracker.data.entity.Category
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.ui.graphics.Color
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private const val MAX_AMOUNT_RUPEES = 10_00_00_000.0  // ₹10 crore max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddTransactionScreen(viewModel: AddTransactionViewModel, onNavigateBack: () -> Unit) {
    val categories by viewModel.categories.collectAsState()
    val saveResult by viewModel.saveResult.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var amount by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf<Category?>(null) }
    var note by remember { mutableStateOf("") }
    var timestamp by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var expanded by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var amountError by remember { mutableStateOf<String?>(null) }
    var manualClassification by remember { mutableStateOf<String?>(null) }
    val datePickerState = rememberDatePickerState(initialSelectedDateMillis = timestamp)

    // Handle save result
    LaunchedEffect(saveResult) {
        when (saveResult) {
            is SaveResult.Success -> {
                viewModel.resetSaveResult()
                onNavigateBack()
            }
            is SaveResult.Error -> {
                snackbarHostState.showSnackbar(
                    message = (saveResult as SaveResult.Error).message,
                    duration = SnackbarDuration.Short
                )
                viewModel.resetSaveResult()
            }
            else -> {}
        }
    }
    
    // Validate amount input
    fun validateAmount(input: String): Pair<Long?, String?> {
        if (input.isBlank()) return null to null
        
        val parsed = input.toDoubleOrNull()
        if (parsed == null) return null to "Invalid number"
        if (parsed <= 0) return null to "Amount must be positive"
        if (parsed > MAX_AMOUNT_RUPEES) return null to "Amount too large (max ₹10 crore)"
        
        // Check decimal places (max 2)
        val decimalPart = input.substringAfter('.', "")
        if (decimalPart.length > 2) return null to "Max 2 decimal places"
        
        // Convert to paisa
        val paisa = (parsed * 100).toLong()
        return paisa to null
    }
    
    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text("New Transaction") },
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
                .padding(16.dp)
                .fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            OutlinedTextField(
                value = amount,
                onValueChange = { newValue ->
                    // Only allow valid decimal input
                    val filtered = newValue.filter { it.isDigit() || it == '.' }
                    // Prevent multiple decimal points
                    if (filtered.count { it == '.' } <= 1) {
                        amount = filtered
                        val (_, error) = validateAmount(filtered)
                        amountError = error
                    }
                },
                label = { Text("Amount (₹)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
                textStyle = MaterialTheme.typography.headlineMedium,
                isError = amountError != null,
                supportingText = {
                    amountError?.let {
                        Text(it, color = MaterialTheme.colorScheme.error)
                    }
                }
            )

            if (showDatePicker) {
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(onClick = {
                            datePickerState.selectedDateMillis?.let { millis ->
                                // Combine selected date with current time
                                val selectedDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
                                val currentTime = LocalDateTime.now()
                                timestamp = selectedDate.atTime(currentTime.hour, currentTime.minute, currentTime.second)
                                    .atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
                            }
                            showDatePicker = false
                        }) { Text("OK") }
                    },
                    dismissButton = {
                        TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
                    }
                ) {
                    DatePicker(state = datePickerState)
                }
            }

            val displayDate = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalDate()
            OutlinedTextField(
                value = displayDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy")),
                onValueChange = {},
                readOnly = true,
                label = { Text("Date") },
                trailingIcon = {
                    IconButton(onClick = { showDatePicker = true }) {
                        Icon(Icons.Default.DateRange, contentDescription = "Select Date")
                    }
                },
                modifier = Modifier.fillMaxWidth().clickable { showDatePicker = true }
            )

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = selectedCategory?.name ?: "Select Category",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Category") },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors(),
                    modifier = Modifier.fillMaxWidth().menuAnchor(type = MenuAnchorType.PrimaryEditable, enabled = true)
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    categories.forEach { category ->
                        DropdownMenuItem(
                            text = { Text(category.name) },
                            onClick = {
                                selectedCategory = category
                                expanded = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = note,
                onValueChange = { note = it },
                label = { Text("Note (Optional)") },
                modifier = Modifier.fillMaxWidth()
            )
            
            var showAdvanced by remember { mutableStateOf(false) }

            TextButton(
                onClick = { showAdvanced = !showAdvanced },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (showAdvanced) "Hide Advanced Options" else "Show Advanced Options")
            }

            AnimatedVisibility(visible = showAdvanced) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Manual Classification - allows user to override category-based type
                    Text(
                        text = "Classification (Optional):",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = manualClassification == "INCOME",
                            onClick = { manualClassification = if (manualClassification == "INCOME") null else "INCOME" },
                            label = { Text("Income", style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFC8E6C9),
                                selectedLabelColor = Color(0xFF2E7D32)
                            )
                        )
                        FilterChip(
                            selected = manualClassification == "EXPENSE",
                            onClick = { manualClassification = if (manualClassification == "EXPENSE") null else "EXPENSE" },
                            label = { Text("Expense", style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFFFCDD2),
                                selectedLabelColor = Color(0xFFC62828)
                            )
                        )
                        FilterChip(
                            selected = manualClassification == "NEUTRAL",
                            onClick = { manualClassification = if (manualClassification == "NEUTRAL") null else "NEUTRAL" },
                            label = { Text("Transfer", style = MaterialTheme.typography.labelSmall) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFFE1BEE7),
                                selectedLabelColor = Color(0xFF6A1B9A)
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            val (validatedPaisa, _) = validateAmount(amount)
            val isValid = validatedPaisa != null && selectedCategory != null && amountError == null
            
            Button(
                onClick = {
                    validatedPaisa?.let { paisa ->
                        selectedCategory?.let { category ->
                            viewModel.saveTransaction(paisa, category.id, timestamp, note, manualClassification)
                            // Navigation handled by LaunchedEffect on SaveResult.Success
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = isValid
            ) {
                Text("Save", modifier = Modifier.padding(vertical = 8.dp))
            }
        }
    }
}
