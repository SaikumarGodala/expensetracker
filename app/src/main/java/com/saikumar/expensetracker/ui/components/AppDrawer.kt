package com.saikumar.expensetracker.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

@Composable
fun AppDrawer(
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    ModalDrawerSheet(modifier = modifier) {
        Spacer(Modifier.height(24.dp))
        
        // Drawer Header
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = "Expense Tracker",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
        
        Spacer(Modifier.height(24.dp))
        
        // Navigation Items
        NavigationDrawerItem(
            label = { Text("Home") },
            icon = { Icon(Icons.Default.Dashboard, null) },
            selected = currentRoute == "dashboard",
            onClick = { onNavigate("dashboard"); onClose() },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )
        
        NavigationDrawerItem(
            label = { Text("Monthly Overview") },
            icon = { Icon(Icons.Default.PieChart, null) }, // Changed icon to distinguish
            selected = currentRoute == "overview",
            onClick = { onNavigate("overview"); onClose() },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )
        
        NavigationDrawerItem(
            label = { Text("Trends & Analytics") },
            icon = { Icon(androidx.compose.material.icons.Icons.Filled.DateRange, null) },
            selected = currentRoute == "analytics",
            onClick = { onNavigate("analytics"); onClose() },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )
        
        Divider(modifier = Modifier.padding(horizontal = 28.dp, vertical = 24.dp))
        
        NavigationDrawerItem(
            label = { Text("Settings") },
            icon = { Icon(Icons.Default.Settings, null) },
            selected = currentRoute == "settings",
            onClick = { onNavigate("settings"); onClose() },
            modifier = Modifier.padding(NavigationDrawerItemDefaults.ItemPadding)
        )
    }
}
