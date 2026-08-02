package ru.shmr.finance.ui.screens.editor

import java.math.BigDecimal
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.shmr.finance.core.result.AppResult
import ru.shmr.finance.domain.model.Account
import ru.shmr.finance.domain.model.AccountDraft
import ru.shmr.finance.domain.model.AppError
import ru.shmr.finance.domain.model.Currency
import ru.shmr.finance.domain.model.Money
import ru.shmr.finance.domain.repository.AccountsRepository

@OptIn(ExperimentalCoroutinesApi::class)
class AccountEditorViewModelTest {

    @Test
    fun `create uses provided default currency normalizes comma and emits Saved`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = FakeAccountsRepository()
            val viewModel = AccountEditorViewModel(
                accountId = null,
                accountsRepository = repository,
                defaultCurrencyProvider = DefaultAccountCurrencyProvider { Currency.USD },
            )
            advanceUntilIdle()

            assertEquals(Currency.USD, viewModel.state.value.currency)
            viewModel.onAction(AccountEditorAction.NameChanged("Накопления"))
            viewModel.onAction(AccountEditorAction.EmojiChanged("🏦"))
            viewModel.onAction(AccountEditorAction.BalanceChanged("1 250,75"))
            val effect = async(start = CoroutineStart.UNDISPATCHED) {
                viewModel.effects.first()
            }

            viewModel.onAction(AccountEditorAction.Save)
            advanceUntilIdle()

            assertEquals(AccountEditorEffect.Saved, effect.await())
            assertNull(repository.savedDraft?.id)
            assertEquals("Накопления", repository.savedDraft?.name)
            assertEquals("🏦", repository.savedDraft?.emoji)
            assertEquals(BigDecimal("1250.75"), repository.savedDraft?.balance)
            assertEquals(Currency.USD, repository.savedDraft?.currency)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `edit preloads account and saves same id`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = FakeAccountsRepository(accounts = listOf(ACCOUNT))
            val viewModel = AccountEditorViewModel(
                accountId = ACCOUNT.id,
                accountsRepository = repository,
            )
            advanceUntilIdle()

            assertFalse(viewModel.state.value.isLoading)
            assertEquals(ACCOUNT.name, viewModel.state.value.name)
            assertEquals(ACCOUNT.emoji, viewModel.state.value.emoji)
            assertEquals("900.5", viewModel.state.value.balance)
            assertEquals(Currency.EUR, viewModel.state.value.currency)

            viewModel.onAction(AccountEditorAction.NameChanged("Обновлённый"))
            val effect = async(start = CoroutineStart.UNDISPATCHED) {
                viewModel.effects.first()
            }
            viewModel.onAction(AccountEditorAction.Save)
            advanceUntilIdle()

            assertEquals(AccountEditorEffect.Saved, effect.await())
            assertEquals(ACCOUNT.id, repository.savedDraft?.id)
            assertEquals("Обновлённый", repository.savedDraft?.name)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `validation keeps field errors and does not save`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = FakeAccountsRepository()
            val viewModel = AccountEditorViewModel(
                accountId = null,
                accountsRepository = repository,
            )
            advanceUntilIdle()

            viewModel.onAction(AccountEditorAction.NameChanged(" "))
            viewModel.onAction(AccountEditorAction.BalanceChanged("-1"))
            viewModel.onAction(AccountEditorAction.Save)

            assertEquals(0, repository.saveCalls)
            assertTrue(AccountEditorField.NAME in viewModel.state.value.errors)
            assertTrue(AccountEditorField.BALANCE in viewModel.state.value.errors)
            assertFalse(viewModel.state.value.isSaving)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `double save starts one repository write`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val gate = CompletableDeferred<Unit>()
            val repository = FakeAccountsRepository(saveGate = gate)
            val viewModel = AccountEditorViewModel(
                accountId = null,
                accountsRepository = repository,
            )
            advanceUntilIdle()
            viewModel.onAction(AccountEditorAction.NameChanged("Основной"))
            viewModel.onAction(AccountEditorAction.BalanceChanged("100"))

            viewModel.onAction(AccountEditorAction.Save)
            viewModel.onAction(AccountEditorAction.Save)
            runCurrent()

            assertEquals(1, repository.saveCalls)
            assertTrue(viewModel.state.value.isSaving)

            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(1, repository.saveCalls)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `account with history rejects currency change with explanation`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = FakeAccountsRepository(
                accounts = listOf(ACCOUNT),
                hasHistory = true,
            )
            val viewModel = AccountEditorViewModel(
                accountId = ACCOUNT.id,
                accountsRepository = repository,
            )
            advanceUntilIdle()
            val effect = async(start = CoroutineStart.UNDISPATCHED) {
                viewModel.effects.first()
            }

            viewModel.onAction(AccountEditorAction.OpenCurrencyPicker)

            assertEquals(
                AccountEditorEffect.ShowMessage(AccountEditorMessage.CURRENCY_HAS_HISTORY),
                effect.await(),
            )
            assertEquals(Currency.EUR, viewModel.state.value.currency)
            assertEquals(
                AccountEditorError.CURRENCY_HAS_HISTORY,
                viewModel.state.value.errors[AccountEditorField.CURRENCY],
            )
            assertNull(viewModel.state.value.activePicker)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `empty account allows currency change`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = FakeAccountsRepository(accounts = listOf(ACCOUNT))
            val viewModel = AccountEditorViewModel(
                accountId = ACCOUNT.id,
                accountsRepository = repository,
            )
            advanceUntilIdle()

            viewModel.onAction(AccountEditorAction.OpenCurrencyPicker)
            assertEquals(AccountEditorPicker.CURRENCY, viewModel.state.value.activePicker)
            viewModel.onAction(AccountEditorAction.CurrencySelected(Currency.GBP))

            assertEquals(Currency.GBP, viewModel.state.value.currency)
            assertNull(viewModel.state.value.activePicker)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `draft snapshot restores unsaved fields and picker`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val repository = FakeAccountsRepository()
            val restored = AccountEditorDraftSnapshot(
                initialized = true,
                name = "Черновик",
                emoji = "💶",
                balance = "42,5",
                currencyCode = Currency.EUR.code,
                activePicker = AccountEditorPicker.CURRENCY.name,
            )
            val viewModel = AccountEditorViewModel(
                accountId = null,
                accountsRepository = repository,
                restoredDraft = restored,
            )
            advanceUntilIdle()

            assertEquals("Черновик", viewModel.state.value.name)
            assertEquals("💶", viewModel.state.value.emoji)
            assertEquals("42,5", viewModel.state.value.balance)
            assertEquals(Currency.EUR, viewModel.state.value.currency)
            assertEquals(AccountEditorPicker.CURRENCY, viewModel.state.value.activePicker)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private class FakeAccountsRepository(
        accounts: List<Account> = emptyList(),
        private val hasHistory: Boolean = false,
        private val saveGate: CompletableDeferred<Unit>? = null,
    ) : AccountsRepository {
        private val accountFlow = MutableStateFlow(accounts)

        var savedDraft: AccountDraft? = null
            private set
        var saveCalls: Int = 0
            private set

        override fun observeAccounts(): Flow<List<Account>> = accountFlow

        override suspend fun getAccounts(): AppResult<List<Account>> =
            AppResult.Success(accountFlow.value)

        override suspend fun refreshAccounts(): AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun hasTransactions(accountId: Int): Boolean = hasHistory

        override suspend fun saveAccount(draft: AccountDraft): AppResult<Account> {
            saveCalls += 1
            savedDraft = draft
            saveGate?.await()
            return if (draft.name == "fail") {
                AppResult.Failure(AppError.Unknown)
            } else {
                AppResult.Success(
                    Account(
                        id = draft.id ?: -1,
                        name = draft.name,
                        emoji = draft.emoji,
                        balance = Money(draft.balance, draft.currency),
                    ),
                )
            }
        }
    }

    private companion object {
        val ACCOUNT = Account(
            id = 7,
            name = "Евро-счёт",
            emoji = "💶",
            balance = Money(BigDecimal("900.50"), Currency.EUR),
        )
    }
}
