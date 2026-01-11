package com.saikumar.expensetracker

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.saikumar.expensetracker.data.entity.CategoryType
import com.saikumar.expensetracker.ui.add.AddTransactionScreen
import com.saikumar.expensetracker.ui.add.AddTransactionViewModel
import com.saikumar.expensetracker.ui.dashboard.*
import com.saikumar.expensetracker.ui.settings.SettingsScreen
import com.saikumar.expensetracker.ui.settings.SettingsViewModel
import com.saikumar.expensetracker.ui.theme.ExpenseTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val app = (application as ExpenseTrackerApplication)
            
            ExpenseTrackerTheme {
                val navController = rememberNavController()
                
                val smsPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { }

                LaunchedEffect(Unit) {
                    smsPermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.RECEIVE_SMS,
                            Manifest.permission.READ_SMS
                        )
                    )
                }

                Scaffold(
                    bottomBar = {
                        val navBackStackEntry by navController.currentBackStackEntryAsState()
                        val currentRoute = navBackStackEntry?.destination?.route
                        
                        NavigationBar {
                            NavigationBarItem(
                                icon = { Icon(Icons.Default.Dashboard, contentDescription = "Dashboard") },
                                label = { Text("Dashboard") },
                                selected = currentRoute == "dashboard",
                                onClick = { 
                                    navController.navigate("dashboard") {
                                        popUpTo("dashboard") { inclusive = true }
                                    }
                                }
                            )
                            NavigationBarItem(
                                icon = { Icon(Icons.Filled.Menu, contentDescription = "Overview") },
                                label = { Text("Overview") },
                                selected = currentRoute == "overview",
                                onClick = { navController.navigate("overview") }
                            )
                            NavigationBarItem(
                                icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                                label = { Text("Settings") },
                                selected = currentRoute == "settings",
                                onClick = { navController.navigate("settings") }
                            )
                        }
                    }
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = "dashboard",
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("dashboard") {
                            val viewModel: DashboardViewModel = viewModel(
                                factory = DashboardViewModel.Factory(app.repository, app.preferencesManager, app.database.cycleOverrideDao())
                            )
                            DashboardScreen(
                                viewModel, 
                                onNavigateToAdd = { navController.navigate("add") },
                                onCategoryClick = { type, start, end -> navController.navigate("filtered/${type.name}/$start/$end") }
                            )
                        }
                        composable("add") {
                            val viewModel: AddTransactionViewModel = viewModel(
                                factory = AddTransactionViewModel.Factory(app.repository)
                            )
                            AddTransactionScreen(viewModel, onNavigateBack = { navController.popBackStack() })
                        }
                        composable("overview") {
                            val viewModel: MonthlyOverviewViewModel = viewModel(
                                factory = MonthlyOverviewViewModel.Factory(app.repository)
                            )
                            MonthlyOverviewScreen(
                                viewModel, 
                                onNavigateBack = { navController.popBackStack() },
                                onCategoryClick = { type, start, end -> navController.navigate("filtered/${type.name}/$start/$end") }
                            )
                        }
                        composable("settings") {
                            val viewModel: SettingsViewModel = viewModel(
                                factory = SettingsViewModel.Factory(app.preferencesManager)
                            )
                            SettingsScreen(
                                viewModel,
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToSalaryHistory = { navController.navigate("salary_history") },
                                onNavigateToCategories = { navController.navigate("category_management") }
                            )
                        }
                        composable("category_management") {
                            val categories by app.repository.allCategories.collectAsState(initial = emptyList())
                            val scope = rememberCoroutineScope()
                            com.saikumar.expensetracker.ui.settings.CategoryManagementScreen(
                                categories = categories,
                                onNavigateBack = { navController.popBackStack() },
                                onUpdateCategory = { category ->
                                    scope.launch {
                                        app.repository.updateCategory(category)
                                    }
                                },
                                onAddCategory = { name, type ->
                                    scope.launch {
                                        app.repository.insertCategory(com.saikumar.expensetracker.data.entity.Category(
                                            name = name,
                                            type = type,
                                            isEnabled = true,
                                            isDefault = false,
                                            icon = name
                                        ))
                                    }
                                },
                                onDeleteCategory = { category ->
                                    scope.launch {
                                        app.repository.deleteCategory(category)
                                    }
                                }
                            )
                        }
                        composable("salary_history") {
                            val viewModel: SalaryHistoryViewModel = viewModel(
                                factory = SalaryHistoryViewModel.Factory(app.repository)
                            )
                            SalaryHistoryScreen(viewModel, onNavigateBack = { navController.popBackStack() })
                        }
                        composable(
                            "filtered/{type}/{start}/{end}",
                            arguments = listOf(
                                navArgument("type") { type = NavType.StringType },
                                navArgument("start") { type = NavType.LongType },
                                navArgument("end") { type = NavType.LongType }
                            )
                        ) { backStackEntry ->
                            val typeStr = backStackEntry.arguments?.getString("type") ?: return@composable
                            val start = backStackEntry.arguments?.getLong("start") ?: return@composable
                            val end = backStackEntry.arguments?.getLong("end") ?: return@composable
                            val type = CategoryType.valueOf(typeStr)
                            
                            val viewModel: FilteredTransactionsViewModel = viewModel(
                                factory = FilteredTransactionsViewModel.Factory(app.repository)
                            )
                            FilteredTransactionsScreen(viewModel, type, start, end, onNavigateBack = { navController.popBackStack() })
                        }
                    }
                }
            }
        }
    }
}
