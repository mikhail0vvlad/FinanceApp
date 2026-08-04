package ru.shmr.finance.ui.screens.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock
import java.time.LocalDate
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.shmr.finance.core.dispatchers.DefaultDispatcherProvider
import ru.shmr.finance.core.dispatchers.DispatcherProvider
import ru.shmr.finance.core.result.AppResult
import ru.shmr.finance.core.state.UiState
import ru.shmr.finance.domain.model.Account
import ru.shmr.finance.domain.model.AppError
import ru.shmr.finance.domain.model.Money
import ru.shmr.finance.domain.model.Transaction
import ru.shmr.finance.domain.repository.AccountsRepository
import ru.shmr.finance.domain.repository.CategoriesRepository
import ru.shmr.finance.domain.repository.TransactionsRepository

class AnalyticsViewModel(
    startWithIncome: Boolean,
    private val accountsRepository: AccountsRepository,
    private val categoriesRepository: CategoriesRepository,
    private val transactionsRepository: TransactionsRepository,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider,
    private val clock: Clock = Clock.systemDefaultZone(),
) : ViewModel() {

    private val _state = MutableStateFlow(
        AnalyticsState(
            filters = AnalyticsFilters.default(
                if (startWithIncome) TypeFilter.INCOME else TypeFilter.EXPENSES,
                today = LocalDate.now(clock),
            ),
        ),
    )
    val state = _state.asStateFlow()

    private val _effects = MutableSharedFlow<AnalyticsEffect>(extraBufferCapacity = 1)
    val effects = _effects.asSharedFlow()

    private var loadJob: Job? = null

    init {
        load()
    }

    fun onAction(action: AnalyticsAction) {
        when (action) {
            is AnalyticsAction.SelectType ->
                applyFilters { copy(type = action.type, selectedCategoryIds = null) }
            is AnalyticsAction.SelectPreset -> applyFilters {
                val (start, end) = AnalyticsFilters.periodFor(action.preset, LocalDate.now(clock))
                copy(preset = action.preset, startDate = start, endDate = end)
            }
            is AnalyticsAction.SelectCustomPeriod -> applyFilters {
                copy(preset = PeriodPreset.CUSTOM, startDate = action.start, endDate = action.end)
            }
            is AnalyticsAction.SelectCategories ->
                applyFilters { copy(selectedCategoryIds = action.ids?.takeIf { it.isNotEmpty() }) }
            is AnalyticsAction.SelectAccount ->
                applyFilters { copy(selectedAccountId = action.accountId) }
            AnalyticsAction.Retry -> load()
        }
    }

    private fun applyFilters(transform: AnalyticsFilters.() -> AnalyticsFilters) {
        val updated = _state.value.filters.transform()
        if (updated != _state.value.filters) {
            _state.update { it.copy(filters = updated) }
            load()
        }
    }

    private fun load() {
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            val hadContent = _state.value.ui is UiState.Content
            if (!hadContent) _state.update { it.copy(ui = UiState.Loading) }
            val filters = _state.value.filters

            if (_state.value.accounts.isEmpty()) {
                when (val result = accountsRepository.getAccounts()) {
                    is AppResult.Failure -> return@launch fail(result.error, hadContent)
                    is AppResult.Success -> _state.update { it.copy(accounts = result.data) }
                }
            }
            if (_state.value.categories.isEmpty()) {
                when (val result = categoriesRepository.getCategories()) {
                    is AppResult.Failure -> return@launch fail(result.error, hadContent)
                    is AppResult.Success -> _state.update { it.copy(categories = result.data) }
                }
            }

            val accountsToQuery = _state.value.accounts
                .filter { filters.selectedAccountId == null || it.id == filters.selectedAccountId }
            if (accountsToQuery.isEmpty()) {
                _state.update { it.copy(ui = UiState.Empty) }
                return@launch
            }

            // Room остаётся источником истины: сеть обновляется best-effort и только
            // для серверных счетов (id > 0) — несинхронизированные локальные счета
            // никогда не уходят в API.
            val refreshError = refreshServerAccounts(accountsToQuery, filters)

            val accountIds = accountsToQuery.mapTo(mutableSetOf()) { it.id }
            val transactions = withContext(dispatchers.default) {
                transactionsRepository
                    .observeTransactionsForPeriod(filters.startDate, filters.endDate)
                    .first()
                    .filter { it.accountId in accountIds }
            }

            if (transactions.isEmpty()) {
                if (refreshError != null) {
                    _state.update { it.copy(ui = UiState.Error(refreshError)) }
                } else {
                    _state.update { it.copy(ui = UiState.Empty) }
                }
                return@launch
            }

            if (refreshError != null) {
                _effects.emit(AnalyticsEffect.ShowError(refreshError))
            }

            val ui = withContext(dispatchers.default) {
                buildContent(transactions, filters, accountsToQuery.first())
            }
            _state.update { it.copy(ui = ui) }
        }
    }

    // Ошибка при перезагрузке (контент уже есть) — Snackbar, экран не сбрасываем.
    private suspend fun fail(error: AppError, hadContent: Boolean) {
        if (hadContent) {
            _effects.emit(AnalyticsEffect.ShowError(error))
        } else {
            _state.update { it.copy(ui = UiState.Error(error)) }
        }
    }

    private suspend fun refreshServerAccounts(
        accounts: List<Account>,
        filters: AnalyticsFilters,
    ): AppError? = coroutineScope {
        var firstFailure: AppError? = null
        accounts.filter { it.id > 0 }
            .chunked(MAX_PARALLEL_REFRESHES)
            .forEach { batch ->
                batch.map { account ->
                    async {
                        transactionsRepository.refreshTransactionsForPeriod(
                            accountId = account.id,
                            startDate = filters.startDate,
                            endDate = filters.endDate,
                        )
                    }
                }
                    .awaitAll()
                    .filterIsInstance<AppResult.Failure>()
                    .firstOrNull()
                    ?.error
                    ?.let { if (firstFailure == null) firstFailure = it }
            }
        firstFailure
    }

    private fun buildContent(
        transactions: List<Transaction>,
        filters: AnalyticsFilters,
        anyAccount: Account,
    ): UiState<AnalyticsData> {
        val matching = transactions
            .filter { filters.type.matches(it.category.isIncome) }
            .filter { filters.selectedCategoryIds == null || it.category.id in filters.selectedCategoryIds }
            .sortedByDescending { it.dateTime }

        if (matching.isEmpty()) return UiState.Empty

        val currency = if (filters.selectedAccountId != null) {
            anyAccount.balance.currency
        } else {
            matching.first().amount.currency
        }
        val filtered = matching.filter { it.amount.currency == currency }
        val total = filtered.fold(Money.ZERO.copy(currency = currency)) { acc, tx -> acc + tx.amount }

        val shares = filtered
            .groupBy { it.category }
            .map { (category, txs) ->
                val amount = txs.fold(Money.ZERO.copy(currency = currency)) { acc, tx -> acc + tx.amount }
                val fraction = if (total.amount.signum() == 0) {
                    0f
                } else {
                    amount.amount.divide(total.amount, 4, RoundingMode.HALF_UP).toFloat()
                }
                CategoryShare(
                    category = category,
                    amount = amount,
                    fraction = fraction,
                    percent = BigDecimal((fraction * 100).toDouble())
                        .setScale(0, RoundingMode.HALF_UP).toInt(),
                )
            }
            .sortedByDescending { it.amount.amount }

        return UiState.Content(AnalyticsData(total = total, shares = shares, transactions = filtered))
    }

    private companion object {
        const val MAX_PARALLEL_REFRESHES = 4
    }
}
