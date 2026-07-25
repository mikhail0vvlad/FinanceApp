package ru.shmr.finance.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.shmr.finance.core.result.AppResult
import ru.shmr.finance.core.state.UiState
import ru.shmr.finance.domain.model.AppError
import ru.shmr.finance.domain.model.MoneyTotals
import ru.shmr.finance.domain.repository.AccountsRepository
import ru.shmr.finance.domain.repository.CategoriesRepository
import ru.shmr.finance.domain.repository.TransactionsRepository

abstract class TransactionListViewModel(
    private val isIncome: Boolean,
    private val accountsRepository: AccountsRepository,
    private val categoriesRepository: CategoriesRepository,
    private val transactionsRepository: TransactionsRepository,
) : ViewModel() {

    private val today = LocalDate.now()
    private val refreshError = MutableStateFlow<AppError?>(null)
    private val initialLoadFinished = MutableStateFlow(false)
    private var refreshJob: Job? = null

    val state = combine(
        accountsRepository.observeAccounts(),
        transactionsRepository.observeTransactionsForPeriod(today, today),
        refreshError,
        initialLoadFinished,
    ) { accounts, transactions, error, loaded ->
        val filtered = transactions
            .filter { it.category.isIncome == isIncome }
            .sortedByDescending { it.dateTime }
        when {
            !loaded && accounts.isEmpty() && filtered.isEmpty() -> UiState.Loading
            filtered.isNotEmpty() -> {
                val total = MoneyTotals.of(filtered.map { it.amount })
                UiState.Content(
                    ListScreenData(
                        total = total.formatted(),
                        items = filtered.map { it.toListItem() },
                    ),
                )
            }
            error != null && accounts.isEmpty() -> UiState.Error(error)
            else -> UiState.Empty
        }
    }
        .flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UiState.Loading,
        )

    init {
        refresh()
    }

    fun retry() = refresh()

    private fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            refreshError.value = null
            val accountsResult = accountsRepository.getAccounts()
            val categoriesResult = categoriesRepository.getCategories()
            val accounts = when (accountsResult) {
                is AppResult.Success -> accountsResult.data
                is AppResult.Failure -> {
                    refreshError.value = accountsResult.error
                    emptyList()
                }
            }
            if (categoriesResult is AppResult.Failure && refreshError.value == null) {
                refreshError.value = categoriesResult.error
            }
            accounts.filter { it.id > 0 }.forEach { account ->
                val result = transactionsRepository.refreshTransactionsForPeriod(
                    accountId = account.id,
                    startDate = today,
                    endDate = today,
                )
                if (result is AppResult.Failure && refreshError.value == null) {
                    refreshError.value = result.error
                }
            }
            initialLoadFinished.value = true
        }
    }
}
