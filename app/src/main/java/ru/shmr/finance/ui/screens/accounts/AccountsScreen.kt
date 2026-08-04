package ru.shmr.finance.ui.screens.accounts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.shmr.finance.R
import ru.shmr.finance.core.dispatchers.DefaultDispatcherProvider
import ru.shmr.finance.core.dispatchers.DispatcherProvider
import ru.shmr.finance.core.result.AppResult
import ru.shmr.finance.core.state.UiState
import ru.shmr.finance.di.ServiceLocator
import ru.shmr.finance.domain.model.AppError
import ru.shmr.finance.domain.model.MoneyTotals
import ru.shmr.finance.domain.repository.AccountsRepository
import ru.shmr.finance.ui.screens.ListScreen
import ru.shmr.finance.ui.screens.ListScreenData
import ru.shmr.finance.ui.screens.toListItem

class AccountsViewModel(
    private val accountsRepository: AccountsRepository,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider,
) : ViewModel() {

    private val refreshError = MutableStateFlow<AppError?>(null)
    private val initialLoadFinished = MutableStateFlow(false)
    private var refreshJob: Job? = null

    val state = combine(
        accountsRepository.observeAccounts(),
        refreshError,
        initialLoadFinished,
    ) { accounts, error, loaded ->
        when {
            !loaded && accounts.isEmpty() -> UiState.Loading
            accounts.isNotEmpty() -> {
                val total = MoneyTotals.of(accounts.map { it.balance })
                UiState.Content(
                    ListScreenData(
                        total = total.formatted(),
                        items = accounts.map { it.toListItem() },
                    ),
                )
            }
            error != null -> UiState.Error(error)
            else -> UiState.Empty
        }
    }
        .flowOn(dispatchers.default)
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            UiState.Loading,
        )

    init {
        refresh()
    }

    fun retry() = refresh()

    private fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            refreshError.value = null
            val result = accountsRepository.refreshAccounts()
            if (result is AppResult.Failure) refreshError.value = result.error
            initialLoadFinished.value = true
        }
    }
}

@Composable
fun AccountsScreen(
    modifier: Modifier = Modifier,
    onAccountClick: (Int) -> Unit = {},
    viewModel: AccountsViewModel = viewModel(
        factory = remember {
            viewModelFactory {
                initializer { AccountsViewModel(accountsRepository = ServiceLocator.accountsRepository) }
            }
        },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ListScreen(
        state = state,
        caption = stringResource(R.string.balance_total),
        onRetry = viewModel::retry,
        onItemClick = { onAccountClick(it.toInt()) },
        modifier = modifier,
    )
}
