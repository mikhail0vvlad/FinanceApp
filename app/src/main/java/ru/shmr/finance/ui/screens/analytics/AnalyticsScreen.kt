package ru.shmr.finance.ui.screens.analytics

import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ru.shmr.finance.R
import ru.shmr.finance.di.ServiceLocator
import ru.shmr.finance.ui.components.plainMessage

@Composable
fun AnalyticsScreen(
    startWithIncome: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AnalyticsViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                AnalyticsViewModel(
                    startWithIncome = startWithIncome,
                    accountsRepository = ServiceLocator.accountsRepository,
                    categoriesRepository = ServiceLocator.categoriesRepository,
                    transactionsRepository = ServiceLocator.transactionsRepository,
                )
            }
        },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val retryLabel = stringResource(R.string.action_retry)
    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is AnalyticsEffect.ShowError -> {
                    val result = snackbarHostState.showSnackbar(
                        message = effect.error.plainMessage(context),
                        actionLabel = retryLabel,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.onAction(AnalyticsAction.Retry)
                    }
                }
            }
        }
    }
    Scaffold(modifier = modifier, snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        AnalyticsContent(
            state = state,
            onAction = viewModel::onAction,
            onBack = onBack,
            modifier = Modifier.padding(padding),
        )
    }
}
