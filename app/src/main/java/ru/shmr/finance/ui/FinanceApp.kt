package ru.shmr.finance.ui

import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import ru.shmr.finance.R
import ru.shmr.finance.di.ServiceLocator
import ru.shmr.finance.domain.model.AppSettings
import ru.shmr.finance.domain.model.SecurityState
import ru.shmr.finance.ui.components.AppTopBar
import ru.shmr.finance.ui.components.OfflineStatusBanner
import ru.shmr.finance.ui.components.SingleDatePickerDialog
import ru.shmr.finance.ui.lock.LocalAppLocked
import ru.shmr.finance.ui.navigation.Destination
import ru.shmr.finance.ui.screens.accounts.AccountsScreen
import ru.shmr.finance.ui.screens.analytics.AnalyticsScreen
import ru.shmr.finance.ui.screens.editor.AccountEditorRoute
import ru.shmr.finance.ui.screens.editor.TransactionEditorRoute
import ru.shmr.finance.ui.screens.editor.TransactionEditorTarget
import ru.shmr.finance.ui.screens.expenses.ExpensesScreen
import ru.shmr.finance.ui.screens.income.IncomeScreen
import ru.shmr.finance.ui.screens.settings.SettingsSheet

private const val TABS_ROUTE = "tabs"
private const val ANALYTICS_ROUTE = "analytics/{income}"
private const val TRANSITION_MILLIS = 300

private fun analyticsRoute(income: Boolean) = "analytics/$income"

/**
 * The tab Scaffold is a separate navigation destination. Analytics owns its own Scaffold, so the
 * outgoing tab does not change its layout during the transition. Editors are modal sheets hosted
 * by the tabs destination and preserve the list beneath their scrim.
 */
@Composable
fun FinanceApp() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = TABS_ROUTE,
        enterTransition = { slideIntoContainer(SlideDirection.Start, tween(TRANSITION_MILLIS)) },
        exitTransition = { slideOutOfContainer(SlideDirection.Start, tween(TRANSITION_MILLIS)) },
        popEnterTransition = { slideIntoContainer(SlideDirection.End, tween(TRANSITION_MILLIS)) },
        popExitTransition = { slideOutOfContainer(SlideDirection.End, tween(TRANSITION_MILLIS)) },
    ) {
        composable(TABS_ROUTE) {
            TabsScreen(
                onAnalysisClick = { income -> navController.navigate(analyticsRoute(income)) },
            )
        }
        composable(
            route = ANALYTICS_ROUTE,
            arguments = listOf(navArgument("income") { type = NavType.BoolType }),
        ) { entry ->
            AnalyticsScreen(
                startWithIncome = entry.arguments?.getBoolean("income") == true,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

@Composable
private fun TabsScreen(
    onAnalysisClick: (income: Boolean) -> Unit,
    viewModel: TabsViewModel = viewModel(
        factory = remember {
            viewModelFactory {
                initializer {
                    TabsViewModel(
                        isOnline = ServiceLocator.networkMonitor.isOnline,
                        settingsRepository = ServiceLocator.settingsRepository,
                        securityRepository = ServiceLocator.securityRepository,
                        categoriesRepository = ServiceLocator.categoriesRepository,
                        apiTokenRepository = ServiceLocator.apiTokenRepository,
                    )
                }
            }
        },
    ),
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val security by viewModel.security.collectAsStateWithLifecycle(initialValue = SecurityState())
    val tokenConfigured by viewModel.tokenConfigured.collectAsStateWithLifecycle(initialValue = false)
    val categoriesState by viewModel.categoriesState.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    var transactionEditorIncome by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var transactionEditorLocalId by rememberSaveable { mutableStateOf<String?>(null) }
    var accountEditorOpen by rememberSaveable { mutableStateOf(false) }
    var accountEditorId by rememberSaveable { mutableStateOf<Int?>(null) }
    var settingsOpen by rememberSaveable { mutableStateOf(false) }
    var datePickerOpen by rememberSaveable { mutableStateOf(false) }
    val transactionEditorTarget = transactionEditorIncome?.let {
        TransactionEditorTarget(isIncome = it, localId = transactionEditorLocalId)
    }
    val openTransactionEditor = { income: Boolean, localId: String? ->
        transactionEditorLocalId = localId
        transactionEditorIncome = income
    }
    val configuration = LocalConfiguration.current
    val locale = configuration.locales[0]
    val dateLabel = if (selectedDate == LocalDate.now()) {
        stringResource(R.string.date_today)
    } else {
        selectedDate.format(DateTimeFormatter.ofPattern("d MMMM", locale))
    }

    Scaffold(
        topBar = {
            AppTopBar(
                date = dateLabel,
                onDateClick = { datePickerOpen = true },
                onAnalysisClick = {
                    val income = currentDestination
                        ?.hierarchy
                        ?.any { it.route == Destination.Income.route } == true
                    onAnalysisClick(income)
                },
                onFilterClick = { settingsOpen = true },
            )
        },
        bottomBar = {
            NavigationBar {
                Destination.entries.forEach { destination ->
                    val selected = currentDestination
                        ?.hierarchy
                        ?.any { it.route == destination.route } == true
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                painter = painterResource(destination.iconRes),
                                contentDescription = stringResource(destination.labelRes),
                            )
                        },
                        label = { Text(stringResource(destination.labelRes)) },
                    )
                }
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    val accountsSelected = currentDestination
                        ?.hierarchy
                        ?.any { it.route == Destination.Accounts.route } == true
                    if (accountsSelected) {
                        accountEditorId = null
                        accountEditorOpen = true
                    } else {
                        val incomeSelected = currentDestination
                            ?.hierarchy
                            ?.any { it.route == Destination.Income.route } == true
                        openTransactionEditor(incomeSelected, null)
                    }
                },
            ) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.cd_add),
                )
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            OfflineStatusBanner(isOnline)
            Box(modifier = Modifier.weight(1f)) {
                NavHost(
                    navController = navController,
                    startDestination = Destination.Expenses.route,
                ) {
                    composable(Destination.Expenses.route) {
                        ExpensesScreen(
                            selectedDate = viewModel.selectedDate,
                            onTransactionClick = {
                                openTransactionEditor(false, it)
                            },
                        )
                    }
                    composable(Destination.Income.route) {
                        IncomeScreen(
                            selectedDate = viewModel.selectedDate,
                            onTransactionClick = {
                                openTransactionEditor(true, it)
                            },
                        )
                    }
                    composable(Destination.Accounts.route) {
                        AccountsScreen(
                            onAccountClick = {
                                accountEditorId = it
                                accountEditorOpen = true
                            },
                        )
                    }
                }
            }
        }
    }

    // Пока приложение заблокировано, ни один лист не должен существовать: он рисуется в своём
    // окне поверх Activity, поэтому оверлей `AppLockGate` его не накрывает. Флаги открытия
    // (`settingsOpen` и т.п.) сохраняются, и после разблокировки лист возвращается сам.
    val appLocked = LocalAppLocked.current

    transactionEditorTarget?.takeIf { !appLocked }?.let { target ->
        TransactionEditorRoute(
            target = target,
            onClose = {
                transactionEditorIncome = null
                transactionEditorLocalId = null
            },
        )
    }

    if (accountEditorOpen && !appLocked) {
        AccountEditorRoute(
            accountId = accountEditorId,
            defaultCurrency = settings.currency.currency,
            onClose = {
                accountEditorOpen = false
                accountEditorId = null
            },
        )
    }

    if (settingsOpen && !appLocked) {
        SettingsSheet(
            settings = settings,
            security = security,
            categoriesState = categoriesState,
            tokenConfigured = tokenConfigured,
            onCurrencySelected = viewModel::setCurrency,
            onThemeModeSelected = viewModel::setThemeMode,
            onLanguageSelected = viewModel::setLanguage,
            onDismiss = { settingsOpen = false },
        )
    }

    if (datePickerOpen && !appLocked) {
        SingleDatePickerDialog(
            selectedDate = selectedDate,
            onDateSelected = viewModel::selectDate,
            onDismiss = { datePickerOpen = false },
        )
    }
}
