package com.saikumar.expensetracker.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Sms

@Composable
fun PermissionRationaleDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Sms, contentDescription = null) },
        title = { Text("SMS Permission Required") },
        text = { 
            Text("Expense Tracker needs access to your SMS messages to automatically detect and categorize your financial transactions (UPI, Credit Card, Bank).\n\nYour data is processed locally on your device and is never uploaded to any server.") 
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Allow")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Not Now")
            }
        }
    )
}
