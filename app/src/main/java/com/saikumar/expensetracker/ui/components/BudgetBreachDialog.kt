package com.saikumar.expensetracker.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.saikumar.expensetracker.util.BudgetState
import com.saikumar.expensetracker.util.BudgetStatus
import java.text.NumberFormat
import java.util.Locale

@Composable
fun BudgetBreachDialog(
    state: BudgetState,
    onSubmit: (String) -> Unit
) {
    var reason by remember { mutableStateOf("") }
    val formatter = remember { NumberFormat.getCurrencyInstance(Locale("en", "IN")) }
    
    // Non-dismissible dialog
    Dialog(
        onDismissRequest = { /* No-op: strictly blocking */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header
                Text(
                    text = if (state.status == BudgetStatus.BREACHED_STAGE_2) "Critical Budget Breach!" else "Budget Limit Breached!",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
                
                Text(
                    text = "You have exceeded your monthly budget limit.",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                HorizontalDivider()
                
                // Stats
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Monthly Limit:")
                    Text(formatter.format(state.limit / 100.0), fontWeight = FontWeight.Bold)
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Current Spent:")
                    Text(formatter.format(state.expenses / 100.0), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                }
                
                val excess = state.expenses - state.limit
                if (excess > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Excess Amount:")
                        Text("+${formatter.format(excess / 100.0)}", fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.error)
                    }
                }
                
                Text(
                    text = "Please explain the reason for this breach to unlock the app.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                // Reason Input
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Reason (e.g. Medical Emergency)") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    singleLine = false
                )
                
                // Submit Button
                Button(
                    onClick = { onSubmit(reason) },
                    enabled = reason.trim().length > 3,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Acknowledge & Save")
                }
            }
        }
    }
}
