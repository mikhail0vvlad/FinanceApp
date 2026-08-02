package ru.shmr.finance.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.shmr.finance.data.local.entity.AccountEntity
import ru.shmr.finance.data.local.entity.CategoryEntity
import ru.shmr.finance.data.sync.PendingAccount
import ru.shmr.finance.data.sync.PendingTransaction
import ru.shmr.finance.data.sync.SyncAction
import ru.shmr.finance.data.sync.SyncCallResult
import ru.shmr.finance.data.sync.SyncEngine
import ru.shmr.finance.data.sync.SyncOutcome
import ru.shmr.finance.data.sync.SyncRemoteGateway
import ru.shmr.finance.domain.model.AccountDraft
import ru.shmr.finance.domain.model.Currency
import ru.shmr.finance.domain.validation.TransactionDraft
import ru.shmr.finance.domain.validation.TransactionDraftValidator

@RunWith(AndroidJUnit4::class)
class LocalFinanceDataSourceTest {

    private lateinit var database: FinanceDatabase
    private lateinit var local: LocalFinanceDataSource

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, FinanceDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        local = LocalFinanceDataSource(database)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun offlineTransactionIsStoredAsPendingAndUpdatesDisplayedBalance() = runTest {
        seedAccountAndCategory()
        val draft = TransactionDraft(
            accountId = 1,
            categoryId = 10,
            amount = "125,50",
            date = LocalDate.of(2026, 7, 24),
            time = LocalTime.of(14, 0),
            comment = "  офлайн  ",
        )
        val validation = TransactionDraftValidator.validate(draft)

        val saved = local.saveTransaction(
            draft = draft,
            normalizedAmount = requireNotNull(validation.normalizedAmount),
            normalizedComment = validation.normalizedComment,
            existingLocalId = null,
        )

        assertTrue(saved.isPending)
        assertEquals("офлайн", saved.comment)
        assertEquals(LocalDateTime.of(draft.date, draft.time), saved.dateTime)
        assertEquals(
            LocalDateTime.of(draft.date, draft.time),
            local.pendingTransactions().single().transactionDate,
        )
        assertEquals(SyncAction.CREATE, local.pendingTransactions().single().action)
        assertEquals(BigDecimal("874.50"), local.getAccounts().single().balance.amount)
        assertNotNull(local.getTransaction(saved.localId))

        local.upsertRemoteAccounts(
            listOf(
                AccountEntity(
                    id = 1,
                    name = "Основной",
                    emoji = "💳",
                    balance = "1000.00",
                    syncBalance = "1000.00",
                    currency = "RUB",
                ),
            ),
        )
        assertEquals(
            BigDecimal("874.50"),
            local.getAccounts().single().balance.amount,
        )
    }

    @Test
    fun createdAccountRemapAlsoReassignsPendingTransactions() = runTest {
        local.upsertCategories(
            listOf(CategoryEntity(10, "Транспорт", "🚌", isIncome = false)),
        )
        val localAccount = local.saveAccount(
            AccountDraft(
                name = "Офлайн",
                emoji = "💳",
                balance = BigDecimal("1000"),
                currency = Currency.RUB,
            ),
        )
        val draft = TransactionDraft(
            accountId = localAccount.id,
            categoryId = 10,
            amount = "10",
            date = LocalDate.of(2026, 7, 24),
            time = LocalTime.NOON,
            comment = null,
        )
        local.saveTransaction(
            draft,
            normalizedAmount = BigDecimal.TEN,
            normalizedComment = null,
            existingLocalId = null,
        )

        local.replaceCreatedAccount(
            localId = localAccount.id,
            remote = PendingAccount(
                id = 77,
                name = "Офлайн",
                emoji = "💳",
                balance = "1000",
                currency = "RUB",
                action = SyncAction.NONE,
            ),
        )

        assertEquals(77, local.pendingTransactions().single().accountId)
        assertEquals(77, local.getAccounts().single().id)
        assertEquals(BigDecimal("990"), local.getAccounts().single().balance.amount)
    }

    @Test
    fun accountWithoutTransactionsCanChangeCurrency() = runTest {
        seedAccountAndCategory()

        local.saveAccount(
            AccountDraft(
                id = 1,
                name = "Основной",
                emoji = "💳",
                balance = BigDecimal("1000"),
                currency = Currency.USD,
            ),
        )

        assertEquals(Currency.USD, local.getAccounts().single().balance.currency)
    }

    @Test
    fun accountWithTransactionsCannotChangeCurrency() = runTest {
        seedAccountAndCategory()
        local.saveTransaction(
            draft = TransactionDraft(
                accountId = 1,
                categoryId = 10,
                amount = "10",
                date = LocalDate.of(2026, 7, 24),
                time = LocalTime.NOON,
                comment = null,
            ),
            normalizedAmount = BigDecimal.TEN,
            normalizedComment = null,
            existingLocalId = null,
        )

        val result = runCatching {
            local.saveAccount(
                AccountDraft(
                    id = 1,
                    name = "Основной",
                    emoji = "💳",
                    balance = BigDecimal("990"),
                    currency = Currency.USD,
                ),
            )
        }

        assertTrue(result.isFailure)
        assertEquals(Currency.RUB, local.getAccounts().single().balance.currency)
    }

    @Test
    fun nameOnlyEditPreservesPendingSyncBalance() = runTest {
        seedAccountAndCategory()
        saveExpense(accountId = 1, amount = "100.00")

        val displayed = local.getAccounts().single()
        assertEquals(BigDecimal("900.00"), displayed.balance.amount)

        local.saveAccount(
            AccountDraft(
                id = 1,
                name = "Обновлённое имя",
                emoji = "💳",
                balance = displayed.balance.amount,
                currency = Currency.RUB,
            ),
        )

        assertEquals(BigDecimal("900.00"), local.getAccounts().single().balance.amount)
        assertEquals(
            BigDecimal("1000.00"),
            BigDecimal(local.pendingAccounts().single().balance),
        )
    }

    @Test
    fun manualBalanceEditWhilePendingExpenseExistsAppliesAsDelta() = runTest {
        seedAccountAndCategory()
        saveExpense(accountId = 1, amount = "100.00")

        local.saveAccount(
            AccountDraft(
                id = 1,
                name = "Основной",
                emoji = "💳",
                balance = BigDecimal("1200.00"),
                currency = Currency.RUB,
            ),
        )

        assertEquals(BigDecimal("1200.00"), local.getAccounts().single().balance.amount)
        val pendingBaseBalance = BigDecimal(local.pendingAccounts().single().balance)
        assertEquals(BigDecimal("1300.00"), pendingBaseBalance)
        // Replaying the pending expense on top of the base sent to the server must
        // land exactly on the balance the user set.
        assertEquals(BigDecimal("1200.00"), pendingBaseBalance - BigDecimal("100.00"))
    }

    @Test
    fun manualBalanceEditWhilePendingIncomeExistsAppliesAsDelta() = runTest {
        seedAccountAndCategory()
        local.upsertCategories(
            listOf(CategoryEntity(20, "Зарплата", "💰", isIncome = true)),
        )
        local.saveTransaction(
            draft = TransactionDraft(
                accountId = 1,
                categoryId = 20,
                amount = "100",
                date = LocalDate.of(2026, 7, 24),
                time = LocalTime.NOON,
                comment = null,
            ),
            normalizedAmount = BigDecimal("100.00"),
            normalizedComment = null,
            existingLocalId = null,
        )
        assertEquals(BigDecimal("1100.00"), local.getAccounts().single().balance.amount)

        local.saveAccount(
            AccountDraft(
                id = 1,
                name = "Основной",
                emoji = "💳",
                balance = BigDecimal("1300.00"),
                currency = Currency.RUB,
            ),
        )

        val pendingBaseBalance = BigDecimal(local.pendingAccounts().single().balance)
        assertEquals(BigDecimal("1200.00"), pendingBaseBalance)
        assertEquals(BigDecimal("1300.00"), pendingBaseBalance + BigDecimal("100.00"))
    }

    @Test
    fun newOfflineAccountNameEditKeepsCreateBalanceAtInitialAmount() = runTest {
        local.upsertCategories(
            listOf(CategoryEntity(10, "Транспорт", "🚌", isIncome = false)),
        )
        val created = local.saveAccount(
            AccountDraft(
                name = "Офлайн",
                emoji = "💳",
                balance = BigDecimal("1000"),
                currency = Currency.RUB,
            ),
        )
        saveExpense(accountId = created.id, amount = "100")

        val displayed = local.getAccounts().single()
        assertEquals(BigDecimal("900"), displayed.balance.amount)

        local.saveAccount(
            AccountDraft(
                id = created.id,
                name = "Офлайн 2",
                emoji = "💳",
                balance = displayed.balance.amount,
                currency = Currency.RUB,
            ),
        )

        val pending = local.pendingAccounts().single()
        assertEquals(SyncAction.CREATE, pending.action)
        assertEquals(BigDecimal("1000"), BigDecimal(pending.balance))
    }

    @Test
    fun accountWithoutPendingTransactionsSendsEditedBalanceDirectly() = runTest {
        seedAccountAndCategory()

        local.saveAccount(
            AccountDraft(
                id = 1,
                name = "Основной",
                emoji = "💳",
                balance = BigDecimal("1200.00"),
                currency = Currency.RUB,
            ),
        )

        assertEquals(BigDecimal("1200.00"), local.getAccounts().single().balance.amount)
        assertEquals(
            BigDecimal("1200.00"),
            BigDecimal(local.pendingAccounts().single().balance),
        )
    }

    @Test
    fun movingTransactionBetweenAccountsKeepsEachAccountsSyncBalanceIndependent() = runTest {
        local.upsertRemoteAccounts(
            listOf(
                AccountEntity(
                    id = 1,
                    name = "A",
                    emoji = "💳",
                    balance = "1000.00",
                    syncBalance = "1000.00",
                    currency = "RUB",
                ),
                AccountEntity(
                    id = 2,
                    name = "B",
                    emoji = "💳",
                    balance = "500.00",
                    syncBalance = "500.00",
                    currency = "RUB",
                ),
            ),
        )
        local.upsertCategories(
            listOf(CategoryEntity(10, "Транспорт", "🚌", isIncome = false)),
        )

        val saved = local.saveTransaction(
            draft = TransactionDraft(
                accountId = 1,
                categoryId = 10,
                amount = "100.00",
                date = LocalDate.of(2026, 7, 24),
                time = LocalTime.NOON,
                comment = null,
            ),
            normalizedAmount = BigDecimal("100.00"),
            normalizedComment = null,
            existingLocalId = null,
        )
        // Move the same expense from account 1 to account 2.
        local.saveTransaction(
            draft = TransactionDraft(
                accountId = 2,
                categoryId = 10,
                amount = "100.00",
                date = LocalDate.of(2026, 7, 24),
                time = LocalTime.NOON,
                comment = null,
            ),
            normalizedAmount = BigDecimal("100.00"),
            normalizedComment = null,
            existingLocalId = saved.localId,
        )

        val accountsById = local.getAccounts().associateBy { it.id }
        assertEquals(BigDecimal("1000.00"), accountsById.getValue(1).balance.amount)
        assertEquals(BigDecimal("400.00"), accountsById.getValue(2).balance.amount)

        // Account 1 no longer has a pending diff; a name-only edit must send its
        // displayed balance unchanged.
        local.saveAccount(
            AccountDraft(
                id = 1,
                name = "A2",
                emoji = "💳",
                balance = BigDecimal("1000.00"),
                currency = Currency.RUB,
            ),
        )
        assertEquals(
            BigDecimal("1000.00"),
            BigDecimal(local.pendingAccounts().single { it.id == 1 }.balance),
        )

        // Account 2 carries the moved-in expense as a pending diff; a name-only edit
        // must keep sending the pre-expense base (500.00), not the displayed 400.00.
        local.saveAccount(
            AccountDraft(
                id = 2,
                name = "B2",
                emoji = "💳",
                balance = BigDecimal("400.00"),
                currency = Currency.RUB,
            ),
        )
        assertEquals(
            BigDecimal("500.00"),
            BigDecimal(local.pendingAccounts().single { it.id == 2 }.balance),
        )
    }

    @Test
    fun syncEngineReplaysAccountUpdateThenTransactionAndMatchesDisplayedBalance() = runTest {
        seedAccountAndCategory()
        saveExpense(accountId = 1, amount = "100.00")
        local.saveAccount(
            AccountDraft(
                id = 1,
                name = "Основной",
                emoji = "💳",
                balance = BigDecimal("1200.00"),
                currency = Currency.RUB,
            ),
        )

        val remote = ServerBalanceGateway(expenseCategoryIds = setOf(10))
        val outcome = SyncEngine(local, remote).sync()

        assertEquals(SyncOutcome.SUCCESS, outcome)
        assertEquals(BigDecimal("1200.00"), remote.serverBalances.getValue(1))
        assertEquals(BigDecimal("1200.00"), local.getAccounts().single().balance.amount)
    }

    private class ServerBalanceGateway(
        private val expenseCategoryIds: Set<Int>,
    ) : SyncRemoteGateway {
        val serverBalances = mutableMapOf<Int, BigDecimal>()
        private var nextServerId = 1

        override suspend fun createAccount(
            account: PendingAccount,
        ): SyncCallResult<PendingAccount> {
            serverBalances[account.id] = BigDecimal(account.balance)
            return SyncCallResult.Success(account.copy(action = SyncAction.NONE))
        }

        override suspend fun updateAccount(
            account: PendingAccount,
        ): SyncCallResult<PendingAccount> {
            serverBalances[account.id] = BigDecimal(account.balance)
            return SyncCallResult.Success(account.copy(action = SyncAction.NONE))
        }

        override suspend fun createTransaction(
            transaction: PendingTransaction,
        ): SyncCallResult<PendingTransaction> {
            applyDelta(transaction)
            return SyncCallResult.Success(
                transaction.copy(serverId = nextServerId++, action = SyncAction.NONE),
            )
        }

        override suspend fun updateTransaction(
            transaction: PendingTransaction,
        ): SyncCallResult<PendingTransaction> =
            SyncCallResult.Success(transaction.copy(action = SyncAction.NONE))

        private fun applyDelta(transaction: PendingTransaction) {
            val signed = if (transaction.categoryId in expenseCategoryIds) {
                BigDecimal(transaction.amount).negate()
            } else {
                BigDecimal(transaction.amount)
            }
            serverBalances[transaction.accountId] =
                (serverBalances[transaction.accountId] ?: BigDecimal.ZERO) + signed
        }
    }

    private suspend fun saveExpense(accountId: Int, amount: String) {
        local.saveTransaction(
            draft = TransactionDraft(
                accountId = accountId,
                categoryId = 10,
                amount = amount,
                date = LocalDate.of(2026, 7, 24),
                time = LocalTime.NOON,
                comment = null,
            ),
            normalizedAmount = BigDecimal(amount),
            normalizedComment = null,
            existingLocalId = null,
        )
    }

    private suspend fun seedAccountAndCategory() {
        local.upsertRemoteAccounts(
            listOf(
                AccountEntity(
                    id = 1,
                    name = "Основной",
                    emoji = "💳",
                    balance = "1000.00",
                    syncBalance = "1000.00",
                    currency = "RUB",
                ),
            ),
        )
        local.upsertCategories(
            listOf(CategoryEntity(10, "Транспорт", "🚌", isIncome = false)),
        )
    }
}
