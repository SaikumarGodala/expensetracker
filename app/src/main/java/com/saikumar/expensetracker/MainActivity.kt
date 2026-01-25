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
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
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
import com.saikumar.expensetracker.util.BudgetStatus
import com.saikumar.expensetracker.util.BiometricPromptManager
import com.saikumar.expensetracker.ui.components.LockScreen
import androidx.appcompat.app.AppCompatActivity
import com.saikumar.expensetracker.ui.components.BudgetBreachDialog

class MainActivity : AppCompatActivity() {
    private val promptManager by lazy {
        BiometricPromptManager(this)
    }

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
                var isUnlocked by remember { mutableStateOf(false) }
                val biometricResult by promptManager.promptResults.collectAsState(initial = null)
                
                LaunchedEffect(biometricResult) {
                    if (biometricResult is BiometricPromptManager.BiometricResult.AuthenticationSuccess) {
                        isUnlocked = true
                    }
                }
                
                LaunchedEffect(Unit) {
                    promptManager.showBiometricPrompt(
                        title = "Unlock Expense Tracker",
                        description = "Authenticate to access your financial data"
                    )
                }
                
                if (!isUnlocked) {
                    LockScreen(
                        onUnlockClick = {
                            promptManager.showBiometricPrompt(
                                title = "Unlock Expense Tracker",
                                description = "Authenticate to access your financial data"
                            )
                        }
                    )
                } else {
                    val navController = rememberNavController()
                
                val scope = rememberCoroutineScope()
                
                val context = androidx.compose.ui.platform.LocalContext.current
                var showPermissionRationale by remember { mutableStateOf(false) }
                
                // Function to check and request permissions
                val checkAndRequestPermissions = {
                    val hasReceiveSms = androidx.core.content.ContextCompat.checkSelfPermission(
                        context, Manifest.permission.RECEIVE_SMS
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    
                    val hasReadSms = androidx.core.content.ContextCompat.checkSelfPermission(
                        context, Manifest.permission.READ_SMS
                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                    
                    if (!hasReceiveSms || !hasReadSms) {
                        showPermissionRationale = true
                    }
                }

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
                    checkAndRequestPermissions()
                }
                
                if (showPermissionRationale) {
                    com.saikumar.expensetracker.ui.components.PermissionRationaleDialog(
                        onConfirm = {
                            showPermissionRationale = false
                            smsPermissionLauncher.launch(
                                arrayOf(
                                    Manifest.permission.RECEIVE_SMS,
                                    Manifest.permission.READ_SMS
                                )
                            )
                        },
                        onDismiss = {
                            showPermissionRationale = false
                            // Permission denied flow (optional: show snackbar)
                        }
                    )
                }
                
                // Budget Accountability Check
                val budgetManager = app.budgetManager
                var budgetState by remember { mutableStateOf<com.saikumar.expensetracker.util.BudgetState?>(null) }
                var refreshBudgetCheck by remember { mutableIntStateOf(0) }
                
                LaunchedEffect(refreshBudgetCheck) {
                    withContext(Dispatchers.IO) {
                        budgetManager.recalculateAutoLimit()
                        val status = budgetManager.checkBudgetStatus()
                        withContext(Dispatchers.Main) {
                            budgetState = status
                        }
                    }
                }
                
                // Blocking Dialog
                budgetState?.let { state ->
                    if (state.status != BudgetStatus.SAFE) {
                        BudgetBreachDialog(
                            state = state,
                            onSubmit = { reason ->
                                // Optimistically dismiss dialog to prevent loop
                                budgetState = null
                                
                                scope.launch(Dispatchers.IO) {
                                    try {
                                        budgetManager.recordBreach(
                                            month = state.month,
                                            stage = if (state.status == BudgetStatus.BREACHED_STAGE_1) 1 else 2,
                                            limit = state.limit,
                                            expenses = state.expenses,
                                            reason = reason
                                        )
                                        withContext(Dispatchers.Main) {
                                            refreshBudgetCheck++
                                        }
                                    } catch (e: Exception) {
                                        Log.e("MainActivity", "Failed to save breach", e)
                                    }
                                }
                            }
                        )
                    }
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
                            // Analytics Navigation Item
                            NavigationBarItem(
                                icon = { Icon(androidx.compose.material.icons.Icons.Filled.DateRange, contentDescription = "Analytics") },
                                label = { Text("Analytics") },
                                selected = currentRoute == "analytics",
                                onClick = { navController.navigate("analytics") }
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
                        composable(
                            "onboarding",
                            enterTransition = { fadeIn(tween(300)) },
                            exitTransition = { fadeOut(tween(300)) }
                        ) {
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
                        
                        // Top Level Screens (Fade Transitions)
                        composable(
                            "dashboard",
                            enterTransition = { fadeIn(tween(300)) },
                            exitTransition = { fadeOut(tween(300)) }
                        ) {
                            val viewModel: DashboardViewModel = viewModel(
                                factory = DashboardViewModel.Factory(app.repository, app.preferencesManager, app.database.cycleOverrideDao(), app.database.userAccountDao())
                            )
                            DashboardScreen(
                                viewModel, 
                                onNavigateToAdd = { navController.navigate("add") },
                                onCategoryClick = { type, start, end -> navController.navigate("filtered/${type.name}/$start/$end") }
                            )
                        }
                        composable(
                            "analytics",
                            enterTransition = { fadeIn(tween(300)) },
                            exitTransition = { fadeOut(tween(300)) }
                        ) {
                            com.saikumar.expensetracker.ui.analytics.AnalyticsScreen(
                                repository = app.repository,
                                onNavigateBack = { navController.popBackStack() },
                                onCategoryClick = { category, start, end ->
                                    navController.navigate("filtered/${category.type.name}/$start/$end?categoryName=${category.name}")
                                }
                            )
                        }
                        composable(
                            "overview",
                            enterTransition = { fadeIn(tween(300)) },
                            exitTransition = { fadeOut(tween(300)) }
                        ) {
                            val viewModel: MonthlyOverviewViewModel = viewModel(
                                factory = MonthlyOverviewViewModel.Factory(app.repository, app.preferencesManager, app.database.userAccountDao())
                            )
                            MonthlyOverviewScreen(
                                viewModel, 
                                onNavigateBack = { navController.popBackStack() },
                                onCategoryClick = { type, start, end -> navController.navigate("filtered/${type.name}/$start/$end") }
                            )
                        }
                        composable(
                            "settings",
                            enterTransition = { fadeIn(tween(300)) },
                            exitTransition = { fadeOut(tween(300)) }
                        ) {
                            val viewModel: SettingsViewModel = viewModel(
                                factory = SettingsViewModel.Factory(app.preferencesManager, app.database.budgetBreachDao())
                            )
                            SettingsScreen(
                                viewModel,
                                onNavigateBack = { navController.popBackStack() },
                                onNavigateToSalaryHistory = { navController.navigate("salary_history") },
                                onNavigateToCategories = { navController.navigate("category_management") },
                                onNavigateToRetirement = { navController.navigate("retirement") },
                                onNavigateToInterestTransactions = {
                                    navController.navigate("filtered/${CategoryType.INCOME.name}/0/4102444800000?categoryName=Interest") 
                                },
                                onNavigateToAdvanced = { navController.navigate("advanced_settings") },
                                onNavigateToTransferCircle = { navController.navigate("transfer_circle") }
                            )
                        }

                        // Detail Screens (Slide Transitions)
                        composable(
                            "add",
                            enterTransition = { slideInHorizontally { it } + fadeIn() },
                            exitTransition = { slideOutHorizontally { -it } + fadeOut() },
                            popEnterTransition = { slideInHorizontally { -it } + fadeIn() },
                            popExitTransition = { slideOutHorizontally { it } + fadeOut() }
                        ) {
                            val viewModel: AddTransactionViewModel = viewModel(
                                factory = AddTransactionViewModel.Factory(app.repository)
                            )
                            AddTransactionScreen(viewModel, onNavigateBack = { navController.popBackStack() })
                        }
                        composable(
                            "transfer_circle",
                            enterTransition = { slideInHorizontally { it } + fadeIn() },
                            exitTransition = { slideOutHorizontally { -it } + fadeOut() },
                            popEnterTransition = { slideInHorizontally { -it } + fadeIn() },
                            popExitTransition = { slideOutHorizontally { it } + fadeOut() }
                        ) {
                            com.saikumar.expensetracker.ui.transfercircle.TransferCircleScreen(
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                        composable(
                            "advanced_settings",
                            enterTransition = { slideInHorizontally { it } + fadeIn() },
                            exitTransition = { slideOutHorizontally { -it } + fadeOut() },
                            popEnterTransition = { slideInHorizontally { -it } + fadeIn() },
                            popExitTransition = { slideOutHorizontally { it } + fadeOut() }
                        ) {
                            val viewModel: SettingsViewModel = viewModel(
                                factory = SettingsViewModel.Factory(app.preferencesManager, app.database.budgetBreachDao())
                            )
                            com.saikumar.expensetracker.ui.settings.AdvancedSettingsScreen(
                                viewModel = viewModel,
                                onNavigateBack = { navController.popBackStack() }
                            )
                        }
                        composable(
                            "retirement",
                            enterTransition = { slideInHorizontally { it } + fadeIn() },
                            exitTransition = { slideOutHorizontally { -it } + fadeOut() },
                            popEnterTransition = { slideInHorizontally { -it } + fadeIn() },
                            popExitTransition = { slideOutHorizontally { it } + fadeOut() }
                        ) {
                            RetirementScreen(onBack = { navController.popBackStack() })
                        }
                        composable(
                            "category_management",
                            enterTransition = { slideInHorizontally { it } + fadeIn() },
                            exitTransition = { slideOutHorizontally { -it } + fadeOut() },
                            popEnterTransition = { slideInHorizontally { -it } + fadeIn() },
                            popExitTransition = { slideOutHorizontally { it } + fadeOut() }
                        ) {
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
                        composable(
                            "salary_history",
                            enterTransition = { slideInHorizontally { it } + fadeIn() },
                            exitTransition = { slideOutHorizontally { -it } + fadeOut() },
                            popEnterTransition = { slideInHorizontally { -it } + fadeIn() },
                            popExitTransition = { slideOutHorizontally { it } + fadeOut() }
                        ) {
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
                            ),
                            enterTransition = { slideInHorizontally { it } + fadeIn() },
                            exitTransition = { slideOutHorizontally { -it } + fadeOut() },
                            popEnterTransition = { slideInHorizontally { -it } + fadeIn() },
                            popExitTransition = { slideOutHorizontally { it } + fadeOut() }
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


                } // End else
            }
        }
    }
}
