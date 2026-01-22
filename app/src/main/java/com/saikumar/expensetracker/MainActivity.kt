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
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
import com.saikumar.expensetracker.ui.retirement.RetirementScreen
import com.saikumar.expensetracker.ui.theme.ExpenseTrackerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val app = (application as ExpenseTrackerApplication)
            
            val themeMode by app.preferencesManager.themeMode.collectAsState(initial = 0)
            val colorPalette by app.preferencesManager.colorPalette.collectAsState(initial = "DYNAMIC")
            
            ExpenseTrackerTheme(
                themeMode = themeMode,
                colorPalette = colorPalette
            ) {
                val navController = rememberNavController()
                
                val scope = rememberCoroutineScope()
                
                val smsPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestMultiplePermissions()
                ) { permissionsGranted ->
                    // Auto-scan SMS on first launch if permissions were granted
                    val smsPermissionsGranted = permissionsGranted[Manifest.permission.READ_SMS] == true
                    if (smsPermissionsGranted) {
                        scope.launch {
                            val isFirstLaunch = app.preferencesManager.isFirstLaunch.first()
                            if (isFirstLaunch) {
                                // Trigger auto-scan on first launch
                                withContext(Dispatchers.IO) {
                                    try {
                                        com.saikumar.expensetracker.sms.SmsProcessor.scanInbox(applicationContext)
                                        app.preferencesManager.setFirstLaunchComplete()
                                    } catch (e: Exception) {
                                        Log.e("MainActivity", "First launch auto-scan failed", e)
                                    }
                                }
                            }
                        }
                    }
                }

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
                        
                        // Hide bottom nav on onboarding screen
                        if (currentRoute == "onboarding") return@Scaffold
                        
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
                    // Check onboarding state
                    val hasSeenOnboarding by app.preferencesManager.hasSeenOnboarding.collectAsState(initial = true)
                    val startDestination = if (hasSeenOnboarding) "dashboard" else "onboarding"
                    
                    NavHost(
                        navController = navController,
                        startDestination = startDestination,
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable("onboarding") {
                            com.saikumar.expensetracker.ui.onboarding.OnboardingScreen(
                                onComplete = {
                                    scope.launch {
                                        app.preferencesManager.setOnboardingComplete()
                                        navController.navigate("dashboard") {
                                            popUpTo("onboarding") { inclusive = true }
                                        }
                                    }
                                }
                            )
                        }
                        composable("dashboard") {
                            val viewModel: DashboardViewModel = viewModel(
                                factory = DashboardViewModel.Factory(app.repository, app.preferencesManager, app.database.cycleOverrideDao(), app.database.userAccountDao())
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
                                factory = MonthlyOverviewViewModel.Factory(app.repository, app.preferencesManager, app.database.userAccountDao())
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
                                onNavigateToCategories = { navController.navigate("category_management") },
                                onNavigateToRetirement = { navController.navigate("retirement") },
                                onNavigateToInterestTransactions = {
                                    // Use a very wide date range to show all time
                                    // Filter by "Interest" category
                                    navController.navigate("filtered/${CategoryType.INCOME.name}/0/4102444800000?categoryName=Interest") 
                                },
                                onNavigateToAdvanced = { navController.navigate("advanced_settings") },
                                onNavigateToTransferCircle = { navController.navigate("transfer_circle") }
                            )
                        }
                        composable("transfer_circle") {
                            com.saikumar.expensetracker.ui.transfercircle.TransferCircleScreen(
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                        composable("advanced_settings") {
                            val viewModel: SettingsViewModel = viewModel(
                                factory = SettingsViewModel.Factory(app.preferencesManager)
                            )
                            com.saikumar.expensetracker.ui.settings.AdvancedSettingsScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                        composable("retirement") {
                            RetirementScreen(onBack = { navController.popBackStack() })
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
                            "filtered/{type}/{start}/{end}?categoryName={categoryName}",
                            arguments = listOf(
                                navArgument("type") { type = NavType.StringType },
                                navArgument("start") { type = NavType.LongType },
                                navArgument("end") { type = NavType.LongType },
                                navArgument("categoryName") { 
                                    type = NavType.StringType 
                                    nullable = true
                                    defaultValue = null
                                }
                            )
                        ) { backStackEntry ->
                            val typeStr = backStackEntry.arguments?.getString("type") ?: return@composable
                            val start = backStackEntry.arguments?.getLong("start") ?: return@composable
                            val end = backStackEntry.arguments?.getLong("end") ?: return@composable
                            val categoryName = backStackEntry.arguments?.getString("categoryName")
                            
                            val type = CategoryType.valueOf(typeStr)
                            
                            val viewModel: FilteredTransactionsViewModel = viewModel(
                                factory = FilteredTransactionsViewModel.Factory(app.repository)
                            )
                            FilteredTransactionsScreen(
                                viewModel, 
                                type, 
                                start, 
                                end, 
                                categoryName, 
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
