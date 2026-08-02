package ru.shmr.finance.ui.screens.analytics

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.shmr.finance.core.result.AppResult
import ru.shmr.finance.core.state.UiState
import ru.shmr.finance.domain.model.Account
import ru.shmr.finance.domain.model.AccountDraft
import ru.shmr.finance.domain.model.AppError
import ru.shmr.finance.domain.model.Category
import ru.shmr.finance.domain.model.Currency
import ru.shmr.finance.domain.model.Money
import ru.shmr.finance.domain.model.Transaction
import ru.shmr.finance.domain.repository.AccountsRepository
import ru.shmr.finance.domain.repository.CategoriesRepository
import ru.shmr.finance.domain.repository.TransactionsRepository
import ru.shmr.finance.domain.validation.TransactionDraft

@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsViewModelTest {

    @Test
    fun `cached data from other account survives when one account refresh fails`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val tx1 = transaction(localId = "tx1", accountId = ACCOUNT_1.id, amount = BigDecimal("100"))
            val transactions = FakeTransactionsRepository(
                initialCache = listOf(tx1),
                refreshFailures = mapOf(ACCOUNT_2.id to AppError.NoInternet),
            )
            val viewModel = createViewModel(
                accounts = listOf(ACCOUNT_1, ACCOUNT_2),
                transactionsRepository = transactions,
                computeDispatcher = dispatcher,
            )
            advanceUntilIdle()

            val ui = viewModel.state.value.ui
            assertTrue(ui is UiState.Content)
            val data = (ui as UiState.Content).data
            assertEquals(listOf("tx1"), data.transactions.map { it.localId })
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `local account transactions are included and never trigger network refresh`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val localTx = transaction(localId = "local-tx", accountId = LOCAL_ACCOUNT.id, amount = BigDecimal("50"))
            val transactions = FakeTransactionsRepository(initialCache = listOf(localTx))
            val viewModel = createViewModel(
                accounts = listOf(LOCAL_ACCOUNT),
                transactionsRepository = transactions,
                computeDispatcher = dispatcher,
            )
            advanceUntilIdle()

            val ui = viewModel.state.value.ui
            assertTrue(ui is UiState.Content)
            assertEquals(listOf("local-tx"), (ui as UiState.Content).data.transactions.map { it.localId })
            assertTrue(transactions.refreshedAccountIds.isEmpty())
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `empty cache and failed refresh surfaces Error`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val transactions = FakeTransactionsRepository(
                initialCache = emptyList(),
                refreshFailures = mapOf(ACCOUNT_1.id to AppError.NoInternet),
            )
            val viewModel = createViewModel(
                accounts = listOf(ACCOUNT_1),
                transactionsRepository = transactions,
                computeDispatcher = dispatcher,
            )
            advanceUntilIdle()

            assertEquals(UiState.Error(AppError.NoInternet), viewModel.state.value.ui)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `successful sync with no transactions shows Empty`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val transactions = FakeTransactionsRepository(initialCache = emptyList())
            val viewModel = createViewModel(
                accounts = listOf(ACCOUNT_1),
                transactionsRepository = transactions,
                computeDispatcher = dispatcher,
            )
            advanceUntilIdle()

            assertEquals(UiState.Empty, viewModel.state.value.ui)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `existing content is kept and ShowError emitted when refresh later fails`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val tx1 = transaction(localId = "tx1", accountId = ACCOUNT_1.id, amount = BigDecimal("100"))
            val transactions = FakeTransactionsRepository(initialCache = listOf(tx1))
            val viewModel = createViewModel(
                accounts = listOf(ACCOUNT_1),
                transactionsRepository = transactions,
                computeDispatcher = dispatcher,
            )
            advanceUntilIdle()
            assertTrue(viewModel.state.value.ui is UiState.Content)

            transactions.refreshFailures = mapOf(ACCOUNT_1.id to AppError.NoInternet)
            val effect = async(start = CoroutineStart.UNDISPATCHED) { viewModel.effects.first() }
            viewModel.onAction(AnalyticsAction.Retry)
            advanceUntilIdle()

            assertEquals(AnalyticsEffect.ShowError(AppError.NoInternet), effect.await())
            assertTrue(viewModel.state.value.ui is UiState.Content)
            assertEquals(
                listOf("tx1"),
                ((viewModel.state.value.ui) as UiState.Content).data.transactions.map { it.localId },
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `selectedAccountId excludes other accounts data`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val tx1 = transaction(localId = "tx1", accountId = ACCOUNT_1.id, amount = BigDecimal("100"))
            val tx2 = transaction(localId = "tx2", accountId = ACCOUNT_2.id, amount = BigDecimal("200"))
            val transactions = FakeTransactionsRepository(initialCache = listOf(tx1, tx2))
            val viewModel = createViewModel(
                accounts = listOf(ACCOUNT_1, ACCOUNT_2),
                transactionsRepository = transactions,
                computeDispatcher = dispatcher,
            )
            advanceUntilIdle()

            viewModel.onAction(AnalyticsAction.SelectAccount(ACCOUNT_1.id))
            advanceUntilIdle()

            val data = (viewModel.state.value.ui as UiState.Content).data
            assertEquals(listOf("tx1"), data.transactions.map { it.localId })
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `different currencies are not summed together`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val txRub = transaction(
                localId = "tx-rub",
                accountId = ACCOUNT_1.id,
                amount = BigDecimal("100"),
                currency = Currency.RUB,
                dateTime = LocalDateTime.of(2026, 7, 27, 12, 0),
            )
            val txUsd = transaction(
                localId = "tx-usd",
                accountId = ACCOUNT_1.id,
                amount = BigDecimal("10"),
                currency = Currency.USD,
                dateTime = LocalDateTime.of(2026, 7, 20, 12, 0),
            )
            val transactions = FakeTransactionsRepository(initialCache = listOf(txRub, txUsd))
            val viewModel = createViewModel(
                accounts = listOf(ACCOUNT_1),
                transactionsRepository = transactions,
                computeDispatcher = dispatcher,
            )
            advanceUntilIdle()

            val data = (viewModel.state.value.ui as UiState.Content).data
            assertEquals(Currency.RUB, data.total.currency)
            assertEquals(0, BigDecimal("100").compareTo(data.total.amount))
            assertEquals(listOf("tx-rub"), data.transactions.map { it.localId })
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `transactions outside the selected period are excluded and refresh receives the selected period`() =
        runTest {
            val dispatcher = StandardTestDispatcher(testScheduler)
            Dispatchers.setMain(dispatcher)
            try {
                val today = LocalDate.of(2026, 7, 27)
                val inRangeTx = transaction(
                    localId = "in-range",
                    accountId = ACCOUNT_1.id,
                    amount = BigDecimal("100"),
                    dateTime = today.atTime(12, 0),
                )
                val outOfRangeTx = transaction(
                    localId = "out-of-range",
                    accountId = ACCOUNT_1.id,
                    amount = BigDecimal("999"),
                    dateTime = today.minusMonths(2).atTime(12, 0),
                )
                val transactions = FakeTransactionsRepository(
                    initialCache = listOf(inRangeTx, outOfRangeTx),
                )
                val viewModel = createViewModel(
                    accounts = listOf(ACCOUNT_1),
                    transactionsRepository = transactions,
                    computeDispatcher = dispatcher,
                )
                advanceUntilIdle()

                val data = (viewModel.state.value.ui as UiState.Content).data
                assertEquals(listOf("in-range"), data.transactions.map { it.localId })

                val expectedStart = today.withDayOfMonth(1)
                assertEquals(
                    listOf(Triple(ACCOUNT_1.id, expectedStart, today)),
                    transactions.refreshedPeriods,
                )
            } finally {
                Dispatchers.resetMain()
            }
        }

    private fun createViewModel(
        accounts: List<Account>,
        categories: List<Category> = listOf(EXPENSE_CATEGORY, INCOME_CATEGORY),
        transactionsRepository: FakeTransactionsRepository,
        computeDispatcher: CoroutineDispatcher,
        startWithIncome: Boolean = false,
    ) = AnalyticsViewModel(
        startWithIncome = startWithIncome,
        accountsRepository = FakeAccountsRepository(accounts),
        categoriesRepository = FakeCategoriesRepository(categories),
        transactionsRepository = transactionsRepository,
        computeDispatcher = computeDispatcher,
        clock = Clock.fixed(Instant.parse("2026-07-27T12:00:00Z"), ZoneOffset.UTC),
    )

    private fun transaction(
        localId: String,
        accountId: Int,
        amount: BigDecimal,
        category: Category = EXPENSE_CATEGORY,
        currency: Currency = Currency.RUB,
        dateTime: LocalDateTime = LocalDateTime.of(2026, 7, 24, 12, 0),
    ) = Transaction(
        id = localId.hashCode(),
        accountId = accountId,
        accountName = "Account $accountId",
        category = category,
        comment = null,
        amount = Money(amount, currency),
        dateTime = dateTime,
        localId = localId,
        serverId = null,
    )

    private class FakeAccountsRepository(
        private val accounts: List<Account>,
    ) : AccountsRepository {
        override fun observeAccounts(): Flow<List<Account>> = flowOf(accounts)
        override suspend fun getAccounts(): AppResult<List<Account>> = AppResult.Success(accounts)
        override suspend fun refreshAccounts(): AppResult<Unit> = AppResult.Success(Unit)
        override suspend fun hasTransactions(accountId: Int): Boolean = false
        override suspend fun saveAccount(draft: AccountDraft): AppResult<Account> = error("Not used")
    }

    private class FakeCategoriesRepository(
        private val categories: List<Category>,
    ) : CategoriesRepository {
        override fun observeCategories(): Flow<List<Category>> = flowOf(categories)
        override suspend fun getCategories(): AppResult<List<Category>> = AppResult.Success(categories)
        override suspend fun refreshCategories(): AppResult<Unit> = AppResult.Success(Unit)
    }

    private class FakeTransactionsRepository(
        initialCache: List<Transaction> = emptyList(),
        var refreshFailures: Map<Int, AppError> = emptyMap(),
    ) : TransactionsRepository {
        private val cache = MutableStateFlow(initialCache)
        val refreshedAccountIds = mutableListOf<Int>()
        val refreshedPeriods = mutableListOf<Triple<Int, LocalDate, LocalDate>>()

        override fun observeTransactionsForPeriod(
            startDate: LocalDate,
            endDate: LocalDate,
        ): Flow<List<Transaction>> = cache.map { list ->
            list.filter { tx ->
                val date = tx.dateTime.toLocalDate()
                !date.isBefore(startDate) && !date.isAfter(endDate)
            }
        }

        override suspend fun refreshTransactionsForPeriod(
            accountId: Int,
            startDate: LocalDate,
            endDate: LocalDate,
        ): AppResult<Unit> {
            check(accountId > 0) { "Must never refresh a negative (local) account id: $accountId" }
            refreshedAccountIds += accountId
            refreshedPeriods += Triple(accountId, startDate, endDate)
            val failure = refreshFailures[accountId]
            return if (failure != null) AppResult.Failure(failure) else AppResult.Success(Unit)
        }

        override suspend fun getTransaction(localId: String): Transaction? =
            cache.value.find { it.localId == localId }

        override suspend fun saveTransaction(
            draft: TransactionDraft,
            existingLocalId: String?,
        ): AppResult<Transaction> = error("Not used")
    }

    private companion object {
        val ACCOUNT_1 = Account(id = 1, name = "Основной", balance = Money(BigDecimal("1000"), Currency.RUB))
        val ACCOUNT_2 = Account(id = 2, name = "Резерв", balance = Money(BigDecimal("500"), Currency.RUB))
        val LOCAL_ACCOUNT = Account(
            id = -1,
            name = "Черновик",
            balance = Money(BigDecimal("0"), Currency.RUB),
            isPending = true,
        )
        val EXPENSE_CATEGORY = Category(id = 10, name = "Продукты", emoji = "🛒", isIncome = false)
        val INCOME_CATEGORY = Category(id = 11, name = "Зарплата", emoji = "💰", isIncome = true)
    }
}
