package com.saikumar.expensetracker.ui.dashboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.saikumar.expensetracker.util.CategoryIcons
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetPlanningSheet(
    recommendations: List<BudgetRecommendation>,
    onDismiss: () -> Unit,
    onSave: (Map<Long, Long>) -> Unit
) {
    // State to hold the drafted budget values
    val budgetStates = remember(recommendations) {
        mutableStateMapOf<Long, String>().apply {
            recommendations.forEach { rec ->
                // Default to existing budget if present, else recommendation (ghost)
                val initialValue = rec.existingBudget ?: rec.recommendedAmount
                if (initialValue > 0) {
                    this[rec.category.id] = (initialValue / 100.0).toInt().toString()
                }
            }
        }
    }
    
    val totalPlan = budgetStates.values.sumOf { it.toLongOrNull() ?: 0L }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            // Header
            Text(
                "Plan Your Spending",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
            
            Text(
                "Review typical limits and tap Save to approve.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp)
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // List of Categories
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 24.dp)
            ) {
                items(recommendations) { rec ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Icon
                        Surface(
                            modifier = Modifier.size(40.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer,
                            shape = androidx.compose.foundation.shape.CircleShape
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = CategoryIcons.getIcon(rec.category.name),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                        }
                        
                        Spacer(modifier = Modifier.width(16.dp))
                        
                        // Name & Recommendation Hint
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                rec.category.name, 
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            if (rec.recommendedAmount > 0) {
                                Text(
                                    "Typical: ₹${String.format(Locale.getDefault(), "%,.0f", rec.recommendedAmount / 100.0)}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        
                        // Input Field
                        OutlinedTextField(
                            value = budgetStates[rec.category.id] ?: "",
                            onValueChange = { newValue ->
                                if (newValue.all { it.isDigit() }) {
                                    budgetStates[rec.category.id] = newValue
                                }
                            },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.width(100.dp),
                            singleLine = true,
                            prefix = { Text("₹") }
                        )
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Footer / Save Button
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text("Total Plan", style = MaterialTheme.typography.labelMedium)
                    Text(
                        "₹${String.format(Locale.getDefault(), "%,.0f", totalPlan.toDouble())}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                Button(
                    onClick = {
                        // Convert Rupee inputs back to Paisa
                        val budgetsToSave = budgetStates.mapValues { (_, value) -> 
                            (value.toLongOrNull() ?: 0L) * 100 
                        }
                        onSave(budgetsToSave)
                    }
                ) {
                    Text("Save Plan")
                }
            }
        }
    }
}
