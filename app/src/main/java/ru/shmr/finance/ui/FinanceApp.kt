package ru.shmr.finance.ui

import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
import java.util.Locale
import ru.shmr.finance.R
import ru.shmr.finance.ui.components.AppTopBar
import ru.shmr.finance.ui.navigation.Destination
import ru.shmr.finance.ui.screens.accounts.AccountsScreen
import ru.shmr.finance.ui.screens.analytics.AnalyticsScreen
import ru.shmr.finance.ui.screens.expenses.ExpensesScreen
import ru.shmr.finance.ui.screens.income.IncomeScreen

private const val TABS_ROUTE = "tabs"
private const val ANALYTICS_ROUTE = "analytics/{income}"

private fun analyticsRoute(income: Boolean) = "analytics/$income"

private const val TRANSITION_MILLIS = 300

/**
 * Two levels on purpose. The tab Scaffold (top bar, bottom bar, FAB) lives inside the [TABS_ROUTE]
 * destination rather than wrapping the whole graph, so opening Аналитика does not strip the bars
 * out from under the outgoing screen — which used to re-lay it out mid-transition and read as a
 * jump. Аналитика brings its own Scaffold and top bar, so it needs nothing from this level.
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
private fun TabsScreen(onAnalysisClick: (income: Boolean) -> Unit) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val todayLabel = remember {
        LocalDate.now().format(DateTimeFormatter.ofPattern("d MMMM", Locale("ru")))
    }

    Scaffold(
        topBar = {
            AppTopBar(
                date = todayLabel,
                onAnalysisClick = {
                    val income = currentDestination
                        ?.hierarchy
                        ?.any { it.route == Destination.Income.route } == true
                    onAnalysisClick(income)
                },
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
            FloatingActionButton(onClick = { /* добавление операции — следующее ДЗ */ }) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.cd_add),
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Expenses.route,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Destination.Expenses.route) { ExpensesScreen() }
            composable(Destination.Income.route) { IncomeScreen() }
            composable(Destination.Accounts.route) { AccountsScreen() }
        }
    }
}
