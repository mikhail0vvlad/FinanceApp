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
import ru.shmr.finance.data.sync.SyncAction
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
