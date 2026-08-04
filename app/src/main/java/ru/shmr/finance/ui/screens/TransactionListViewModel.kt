package ru.shmr.finance.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.shmr.finance.core.dispatchers.DefaultDispatcherProvider
import ru.shmr.finance.core.dispatchers.DispatcherProvider
import ru.shmr.finance.core.result.AppResult
import ru.shmr.finance.core.state.UiState
import ru.shmr.finance.domain.model.AppError
import ru.shmr.finance.domain.model.MoneyTotals
import ru.shmr.finance.domain.repository.AccountsRepository
import ru.shmr.finance.domain.repository.CategoriesRepository
import ru.shmr.finance.domain.repository.TransactionsRepository

/**
 * Backs the Expenses/Income tabs: for whichever single day [selectedDate] currently holds, shows
 * the Room cache first and kicks off a best-effort remote refresh for that day. [selectedDate] is
 * owned by the shared tabs ViewModel so Expenses and Income always agree on the visible day.
 */
@OptIn(ExperimentalCoroutinesApi::class)
abstract class TransactionListViewModel(
    private val isIncome: Boolean,
    private val accountsRepository: AccountsRepository,
    private val categoriesRepository: CategoriesRepository,
    private val transactionsRepository: TransactionsRepository,
    private val selectedDate: StateFlow<LocalDate>,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider,
) : ViewModel() {

    private val refreshError = MutableStateFlow<AppError?>(null)
    private val initialLoadFinished = MutableStateFlow(false)
    private var refreshJob: Job? = null

    private val transactionsForSelectedDate = selectedDate.flatMapLatest { date ->
        transactionsRepository.observeTransactionsForPeriod(date, date)
    }

    val state = combine(
        accountsRepository.observeAccounts(),
        transactionsForSelectedDate,
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
        .flowOn(dispatchers.default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = UiState.Loading,
        )

    init {
        viewModelScope.launch {
            selectedDate.collect { date -> refresh(date) }
        }
    }

    fun retry() = refresh(selectedDate.value)

    private fun refresh(date: LocalDate) {
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
                    startDate = date,
                    endDate = date,
                )
                if (result is AppResult.Failure && refreshError.value == null) {
                    refreshError.value = result.error
                }
            }
            initialLoadFinished.value = true
        }
    }
}
