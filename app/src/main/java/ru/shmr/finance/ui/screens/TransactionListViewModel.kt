package ru.shmr.finance.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.shmr.finance.core.result.AppResult
import ru.shmr.finance.core.state.UiState
import ru.shmr.finance.domain.model.Money
import ru.shmr.finance.domain.repository.AccountsRepository
import ru.shmr.finance.domain.repository.TransactionsRepository

abstract class TransactionListViewModel(
    private val isIncome: Boolean,
    private val accountsRepository: AccountsRepository,
    private val transactionsRepository: TransactionsRepository,
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

            val accounts = when (val result = accountsRepository.getAccounts()) {
                is AppResult.Failure -> {
                    _state.value = UiState.Error(result.error)
                    return@launch
                }
                is AppResult.Success -> result.data
            }
            val account = accounts.firstOrNull()
            if (account == null) {
                _state.value = UiState.Empty
                return@launch
            }

            val today = LocalDate.now()
            val transactions = when (
                val result = transactionsRepository.getTransactionsForPeriod(account.id, today, today)
            ) {
                is AppResult.Failure -> {
                    _state.value = UiState.Error(result.error)
                    return@launch
                }
                is AppResult.Success -> result.data
            }

            _state.value = withContext(Dispatchers.Default) {
                val filtered = transactions
                    .filter { it.category.isIncome == isIncome }
                    .sortedByDescending { it.dateTime }
                if (filtered.isEmpty()) {
                    UiState.Empty
                } else {
                    val total = filtered.fold(Money.ZERO.copy(currency = account.balance.currency)) { acc, tx ->
                        acc + tx.amount
                    }
                    UiState.Content(
                        ListScreenData(
                            total = total.formatted(),
                            items = filtered.map { it.toListItem() },
                        ),
                    )
                }
            }
        }
    }
}
