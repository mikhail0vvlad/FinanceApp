package ru.shmr.finance.ui.screens.accounts

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.shmr.finance.R
import ru.shmr.finance.core.result.AppResult
import ru.shmr.finance.core.state.UiState
import ru.shmr.finance.di.ServiceLocator
import ru.shmr.finance.domain.model.Money
import ru.shmr.finance.domain.repository.AccountsRepository
import ru.shmr.finance.ui.screens.ListScreen
import ru.shmr.finance.ui.screens.ListScreenData
import ru.shmr.finance.ui.screens.toListItem

class AccountsViewModel(
    private val accountsRepository: AccountsRepository = ServiceLocator.accountsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow<UiState<ListScreenData>>(UiState.Loading)
    val state = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    fun retry() = load()

    private fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            _state.value = UiState.Loading
            when (val result = accountsRepository.getAccounts()) {
                is AppResult.Failure -> _state.value = UiState.Error(result.error)
                is AppResult.Success -> {
                    val accounts = result.data
                    _state.value = if (accounts.isEmpty()) {
                        UiState.Empty
                    } else {
                        withContext(Dispatchers.Default) {
                            val total = accounts
                                .fold(Money.ZERO.copy(currency = accounts.first().balance.currency)) { acc, account ->
                                    acc + account.balance
                                }
                            UiState.Content(
                                ListScreenData(
                                    total = total.formatted(),
                                    items = accounts.map { it.toListItem() },
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AccountsScreen(
    modifier: Modifier = Modifier,
    viewModel: AccountsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ListScreen(
        state = state,
        caption = stringResource(R.string.balance_total),
        onRetry = viewModel::retry,
        modifier = modifier,
    )
}
