package ru.shmr.finance.ui.screens

import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.shmr.finance.core.dispatchers.DispatcherProvider
import ru.shmr.finance.core.result.AppResult
import ru.shmr.finance.core.state.UiState
import ru.shmr.finance.domain.model.Account
import ru.shmr.finance.domain.model.AccountDraft
import ru.shmr.finance.domain.model.Category
import ru.shmr.finance.domain.model.Currency
import ru.shmr.finance.domain.model.Money
import ru.shmr.finance.domain.model.Transaction
import ru.shmr.finance.domain.repository.AccountsRepository
import ru.shmr.finance.domain.repository.CategoriesRepository
import ru.shmr.finance.domain.repository.TransactionsRepository
import ru.shmr.finance.domain.validation.TransactionDraft

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionListViewModelTest {

    private val account = Account(id = 1, name = "Основной", balance = Money(BigDecimal("1000")))
    private val expenseCategory = Category(id = 10, name = "Продукты", emoji = "🛒", isIncome = false)
    private val incomeCategory = Category(id = 11, name = "Зарплата", emoji = "💰", isIncome = true)

    @Test
    fun `list and total reflect the currently selected day only`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val today = LocalDate.of(2026, 8, 5)
            val yesterday = today.minusDays(1)
            val selectedDate = MutableStateFlow(today)
            val transactions = FakeTransactionsRepository(
                byDate = mapOf(
                    today to listOf(
                        transaction("t1", expenseCategory, BigDecimal("100"), today),
                        transaction("t2", incomeCategory, BigDecimal("500"), today),
                    ),
                    yesterday to listOf(
                        transaction("t3", expenseCategory, BigDecimal("40"), yesterday),
                    ),
                ),
            )
            val viewModel = TestTransactionListViewModel(
                isIncome = false,
                accountsRepository = FakeAccountsRepository(listOf(account)),
                categoriesRepository = FakeCategoriesRepository(listOf(expenseCategory, incomeCategory)),
                transactionsRepository = transactions,
                selectedDate = selectedDate,
                dispatchers = TestDispatcherProvider(dispatcher),
            )
            backgroundScope.launch { viewModel.state.collect {} }
            advanceUntilIdle()

            val todayContent = viewModel.state.value as UiState.Content
            assertEquals(listOf("t1"), todayContent.data.items.map { it.id })

            selectedDate.value = yesterday
            advanceUntilIdle()

            val yesterdayContent = viewModel.state.value as UiState.Content
            assertEquals(listOf("t3"), yesterdayContent.data.items.map { it.id })
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `an empty selected day shows the Empty state, not Loading or Error`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val today = LocalDate.of(2026, 8, 5)
            val emptyDay = today.minusDays(10)
            val selectedDate = MutableStateFlow(today)
            val transactions = FakeTransactionsRepository(
                byDate = mapOf(
                    today to listOf(transaction("t1", expenseCategory, BigDecimal("100"), today)),
                    emptyDay to emptyList(),
                ),
            )
            val viewModel = TestTransactionListViewModel(
                isIncome = false,
                accountsRepository = FakeAccountsRepository(listOf(account)),
                categoriesRepository = FakeCategoriesRepository(listOf(expenseCategory)),
                transactionsRepository = transactions,
                selectedDate = selectedDate,
                dispatchers = TestDispatcherProvider(dispatcher),
            )
            backgroundScope.launch { viewModel.state.collect {} }
            advanceUntilIdle()
            assertTrue(viewModel.state.value is UiState.Content)

            selectedDate.value = emptyDay
            advanceUntilIdle()

            assertEquals(UiState.Empty, viewModel.state.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `only refreshes the currently selected day's period, not the whole history`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val today = LocalDate.of(2026, 8, 5)
            val pastDay = today.minusDays(7)
            val selectedDate = MutableStateFlow(today)
            val transactions = FakeTransactionsRepository(byDate = emptyMap())
            val viewModel = TestTransactionListViewModel(
                isIncome = false,
                accountsRepository = FakeAccountsRepository(listOf(account)),
                categoriesRepository = FakeCategoriesRepository(emptyList()),
                transactionsRepository = transactions,
                selectedDate = selectedDate,
                dispatchers = TestDispatcherProvider(dispatcher),
            )
            advanceUntilIdle()

            selectedDate.value = pastDay
            advanceUntilIdle()

            assertEquals(listOf(today, pastDay), transactions.refreshedPeriods.map { it.first })
            assertTrue(transactions.refreshedPeriods.all { it.first == it.second })
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun transaction(
        localId: String,
        category: Category,
        amount: BigDecimal,
        date: LocalDate,
    ) = Transaction(
        id = localId.hashCode(),
        accountId = account.id,
        accountName = account.name,
        category = category,
        comment = null,
        amount = Money(amount, Currency.RUB),
        dateTime = date.atTime(12, 0),
        localId = localId,
        serverId = null,
    )

    private class TestTransactionListViewModel(
        isIncome: Boolean,
        accountsRepository: AccountsRepository,
        categoriesRepository: CategoriesRepository,
        transactionsRepository: TransactionsRepository,
        selectedDate: MutableStateFlow<LocalDate>,
        dispatchers: DispatcherProvider,
    ) : TransactionListViewModel(
        isIncome = isIncome,
        accountsRepository = accountsRepository,
        categoriesRepository = categoriesRepository,
        transactionsRepository = transactionsRepository,
        selectedDate = selectedDate,
        dispatchers = dispatchers,
    )

    private class TestDispatcherProvider(dispatcher: TestDispatcher) : DispatcherProvider {
        override val io: CoroutineDispatcher = dispatcher
        override val default: CoroutineDispatcher = dispatcher
        override val main: CoroutineDispatcher = dispatcher
    }

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
        private val byDate: Map<LocalDate, List<Transaction>>,
    ) : TransactionsRepository {
        val refreshedPeriods = mutableListOf<Pair<LocalDate, LocalDate>>()

        override fun observeTransactionsForPeriod(
            startDate: LocalDate,
            endDate: LocalDate,
        ): Flow<List<Transaction>> = flowOf(byDate[startDate].orEmpty())

        override suspend fun refreshTransactionsForPeriod(
            accountId: Int,
            startDate: LocalDate,
            endDate: LocalDate,
        ): AppResult<Unit> {
            refreshedPeriods += startDate to endDate
            return AppResult.Success(Unit)
        }

        override suspend fun getTransaction(localId: String): Transaction? = null

        override suspend fun saveTransaction(
            draft: TransactionDraft,
            existingLocalId: String?,
        ): AppResult<Transaction> = error("Not used")
    }
}
