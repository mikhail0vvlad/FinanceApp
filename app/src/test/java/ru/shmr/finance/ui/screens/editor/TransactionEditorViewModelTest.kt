package ru.shmr.finance.ui.screens.editor

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.shmr.finance.core.result.AppResult
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
import ru.shmr.finance.domain.validation.TransactionField

@OptIn(ExperimentalCoroutinesApi::class)
class TransactionEditorViewModelTest {

    @Test
    fun `create saves through repository and emits Saved`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val transactions = FakeTransactionsRepository()
            val viewModel = createViewModel(transactionsRepository = transactions)
            advanceUntilIdle()

            viewModel.onAction(TransactionEditorAction.AmountChanged("125,50"))
            val effect = async(start = CoroutineStart.UNDISPATCHED) {
                viewModel.effects.first()
            }
            viewModel.onAction(TransactionEditorAction.Save)
            advanceUntilIdle()

            assertEquals(TransactionEditorEffect.Saved, effect.await())
            assertEquals(null, transactions.savedLocalId)
            assertEquals("125,50", transactions.savedDraft?.amount)
            assertEquals(ACCOUNT.id, transactions.savedDraft?.accountId)
            assertEquals(EXPENSE_CATEGORY.id, transactions.savedDraft?.categoryId)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `edit loads local transaction and preserves local id on save`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val existing = transaction(
                localId = "local-edit-id",
                amount = BigDecimal("42.75"),
                comment = "До изменения",
            )
            val transactions = FakeTransactionsRepository(existing)
            val viewModel = createViewModel(
                localId = existing.localId,
                transactionsRepository = transactions,
            )
            advanceUntilIdle()

            assertFalse(viewModel.state.value.isLoading)
            assertEquals("42.75", viewModel.state.value.amount)
            assertEquals(existing.dateTime.toLocalDate(), viewModel.state.value.date)
            assertEquals(existing.dateTime.toLocalTime(), viewModel.state.value.time)

            viewModel.onAction(TransactionEditorAction.CommentChanged("После изменения"))
            val effect = async(start = CoroutineStart.UNDISPATCHED) {
                viewModel.effects.first()
            }
            viewModel.onAction(TransactionEditorAction.Save)
            advanceUntilIdle()

            assertEquals(TransactionEditorEffect.Saved, effect.await())
            assertEquals(existing.localId, transactions.savedLocalId)
            assertEquals("После изменения", transactions.savedDraft?.comment)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `invalid amount is attached to hero field and clears while editing`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val viewModel = createViewModel(
                transactionsRepository = FakeTransactionsRepository(),
            )
            advanceUntilIdle()

            viewModel.onAction(TransactionEditorAction.Save)

            assertFalse(viewModel.state.value.isSaving)
            assertTrue(
                viewModel.state.value.errors.containsKey(TransactionField.AMOUNT),
            )

            viewModel.onAction(TransactionEditorAction.AmountChanged("1,25"))

            assertFalse(
                viewModel.state.value.errors.containsKey(TransactionField.AMOUNT),
            )
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `save failure keeps cached editor content and emits snackbar message`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val viewModel = createViewModel(
                transactionsRepository = FakeTransactionsRepository(failOnSave = true),
            )
            advanceUntilIdle()
            viewModel.onAction(TransactionEditorAction.AmountChanged("125,50"))
            val effect = async(start = CoroutineStart.UNDISPATCHED) {
                viewModel.effects.first()
            }

            viewModel.onAction(TransactionEditorAction.Save)
            advanceUntilIdle()

            assertEquals(
                TransactionEditorEffect.ShowMessage("Не удалось сохранить операцию"),
                effect.await(),
            )
            assertFalse(viewModel.state.value.isSaving)
            assertEquals("125,50", viewModel.state.value.amount)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `date picker cancel keeps draft and apply commits staged date`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val viewModel = createViewModel(
                transactionsRepository = FakeTransactionsRepository(),
            )
            advanceUntilIdle()
            val original = viewModel.state.value.date
            val selected = LocalDate.of(2026, 8, 17)

            viewModel.onAction(
                TransactionEditorAction.OpenPicker(TransactionEditorPicker.DATE),
            )
            viewModel.onAction(TransactionEditorAction.PickerDateChanged(selected))
            viewModel.onAction(TransactionEditorAction.DismissPicker)

            assertEquals(original, viewModel.state.value.date)
            assertEquals(null, viewModel.state.value.activePicker)

            viewModel.onAction(
                TransactionEditorAction.OpenPicker(TransactionEditorPicker.DATE),
            )
            viewModel.onAction(TransactionEditorAction.PickerDateChanged(selected))
            viewModel.onAction(TransactionEditorAction.ApplyPicker)

            assertEquals(selected, viewModel.state.value.date)
            assertEquals(null, viewModel.state.value.activePicker)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `time picker cancel keeps draft and apply commits minute precision`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val viewModel = createViewModel(
                transactionsRepository = FakeTransactionsRepository(),
            )
            advanceUntilIdle()
            val original = viewModel.state.value.time
            val selected = LocalTime.of(23, 42)

            viewModel.onAction(
                TransactionEditorAction.OpenPicker(TransactionEditorPicker.TIME),
            )
            viewModel.onAction(TransactionEditorAction.PickerTimeChanged(selected))
            viewModel.onAction(TransactionEditorAction.DismissPicker)

            assertEquals(original, viewModel.state.value.time)

            viewModel.onAction(
                TransactionEditorAction.OpenPicker(TransactionEditorPicker.TIME),
            )
            viewModel.onAction(TransactionEditorAction.PickerTimeChanged(selected))
            viewModel.onAction(TransactionEditorAction.ApplyPicker)

            assertEquals(selected, viewModel.state.value.time)
            assertEquals(null, viewModel.state.value.activePicker)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `picker refuses unsupported date and non minute time`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val viewModel = createViewModel(
                transactionsRepository = FakeTransactionsRepository(),
            )
            advanceUntilIdle()
            val originalDate = viewModel.state.value.date

            viewModel.onAction(
                TransactionEditorAction.OpenPicker(TransactionEditorPicker.DATE),
            )
            viewModel.onAction(
                TransactionEditorAction.PickerDateChanged(LocalDate.of(2101, 1, 1)),
            )
            viewModel.onAction(TransactionEditorAction.ApplyPicker)

            assertEquals(originalDate, viewModel.state.value.date)
            assertEquals(TransactionEditorPicker.DATE, viewModel.state.value.activePicker)
            assertTrue(viewModel.state.value.pickerError != null)

            viewModel.onAction(TransactionEditorAction.DismissPicker)
            viewModel.onAction(
                TransactionEditorAction.OpenPicker(TransactionEditorPicker.TIME),
            )
            viewModel.onAction(
                TransactionEditorAction.PickerTimeChanged(LocalTime.of(18, 30, 1)),
            )
            viewModel.onAction(TransactionEditorAction.ApplyPicker)

            assertEquals(TransactionEditorPicker.TIME, viewModel.state.value.activePicker)
            assertTrue(viewModel.state.value.pickerError != null)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `draft snapshot restores editor and nested picker after recreation`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val first = createViewModel(
                transactionsRepository = FakeTransactionsRepository(),
            )
            advanceUntilIdle()
            first.onAction(TransactionEditorAction.AmountChanged("987,65"))
            first.onAction(TransactionEditorAction.CommentChanged("Черновик"))
            first.onAction(
                TransactionEditorAction.OpenPicker(TransactionEditorPicker.DATE),
            )
            first.onAction(
                TransactionEditorAction.PickerDateChanged(LocalDate.of(2026, 9, 5)),
            )
            val snapshot = TransactionEditorDraftSnapshot.from(first.state.value)

            val restored = createViewModel(
                transactionsRepository = FakeTransactionsRepository(),
                restoredDraft = snapshot,
            )
            advanceUntilIdle()

            assertEquals("987,65", restored.state.value.amount)
            assertEquals("Черновик", restored.state.value.comment)
            assertEquals(TransactionEditorPicker.DATE, restored.state.value.activePicker)
            assertEquals(LocalDate.of(2026, 9, 5), restored.state.value.pendingDate)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `double save starts only one repository write`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        try {
            val gate = CompletableDeferred<Unit>()
            val transactions = FakeTransactionsRepository(saveGate = gate)
            val viewModel = createViewModel(transactionsRepository = transactions)
            advanceUntilIdle()
            viewModel.onAction(TransactionEditorAction.AmountChanged("125,50"))

            viewModel.onAction(TransactionEditorAction.Save)
            viewModel.onAction(TransactionEditorAction.Save)
            runCurrent()

            assertEquals(1, transactions.saveCalls)
            assertTrue(viewModel.state.value.isSaving)

            gate.complete(Unit)
            advanceUntilIdle()
            assertEquals(1, transactions.saveCalls)
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun createViewModel(
        localId: String? = null,
        transactionsRepository: FakeTransactionsRepository,
        restoredDraft: TransactionEditorDraftSnapshot = TransactionEditorDraftSnapshot(),
    ) = TransactionEditorViewModel(
        isIncome = false,
        localId = localId,
        accountsRepository = FakeAccountsRepository(),
        categoriesRepository = FakeCategoriesRepository(),
        transactionsRepository = transactionsRepository,
        restoredDraft = restoredDraft,
    )

    private class FakeAccountsRepository : AccountsRepository {
        private val accounts = MutableStateFlow(listOf(ACCOUNT))

        override fun observeAccounts(): Flow<List<Account>> = accounts

        override suspend fun getAccounts(): AppResult<List<Account>> =
            AppResult.Success(accounts.value)

        override suspend fun refreshAccounts(): AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun hasTransactions(accountId: Int): Boolean = false

        override suspend fun saveAccount(draft: AccountDraft): AppResult<Account> =
            error("Not used")
    }

    private class FakeCategoriesRepository : CategoriesRepository {
        private val categories = MutableStateFlow(listOf(EXPENSE_CATEGORY, INCOME_CATEGORY))

        override fun observeCategories(): Flow<List<Category>> = categories

        override suspend fun getCategories(): AppResult<List<Category>> =
            AppResult.Success(categories.value)

        override suspend fun refreshCategories(): AppResult<Unit> = AppResult.Success(Unit)
    }

    private class FakeTransactionsRepository(
        private val existing: Transaction? = null,
        private val failOnSave: Boolean = false,
        private val saveGate: CompletableDeferred<Unit>? = null,
    ) : TransactionsRepository {
        var savedDraft: TransactionDraft? = null
            private set
        var savedLocalId: String? = null
            private set
        var saveCalls: Int = 0
            private set

        override fun observeTransactionsForPeriod(
            startDate: LocalDate,
            endDate: LocalDate,
        ): Flow<List<Transaction>> = emptyFlow()

        override fun observeTransactionsForPeriod(
            accountId: Int,
            startDate: LocalDate,
            endDate: LocalDate,
        ): Flow<List<Transaction>> = emptyFlow()

        override suspend fun getTransactionsForPeriod(
            accountId: Int,
            startDate: LocalDate,
            endDate: LocalDate,
        ): AppResult<List<Transaction>> = AppResult.Success(emptyList())

        override suspend fun refreshTransactionsForPeriod(
            accountId: Int,
            startDate: LocalDate,
            endDate: LocalDate,
        ): AppResult<Unit> = AppResult.Success(Unit)

        override suspend fun getTransaction(localId: String): Transaction? =
            existing?.takeIf { it.localId == localId }

        override suspend fun saveTransaction(
            draft: TransactionDraft,
            existingLocalId: String?,
        ): AppResult<Transaction> {
            saveCalls += 1
            savedDraft = draft
            savedLocalId = existingLocalId
            saveGate?.await()
            if (failOnSave) return AppResult.Failure(AppError.Unknown)
            return AppResult.Success(
                existing ?: transaction(
                    localId = "new-local-id",
                    amount = BigDecimal("125.50"),
                    comment = draft.comment,
                ),
            )
        }
    }

    private companion object {
        val ACCOUNT = Account(
            id = 7,
            name = "Основной",
            balance = Money(BigDecimal("1000"), Currency.RUB),
        )
        val EXPENSE_CATEGORY = Category(
            id = 42,
            name = "Продукты",
            emoji = "🛒",
            isIncome = false,
        )
        val INCOME_CATEGORY = Category(
            id = 43,
            name = "Зарплата",
            emoji = "💰",
            isIncome = true,
        )

        fun transaction(
            localId: String,
            amount: BigDecimal,
            comment: String?,
        ) = Transaction(
            id = 21,
            accountId = ACCOUNT.id,
            accountName = ACCOUNT.name,
            category = EXPENSE_CATEGORY,
            comment = comment,
            amount = Money(amount, Currency.RUB),
            dateTime = LocalDateTime.of(
                LocalDate.of(2026, 7, 24),
                LocalTime.of(18, 30),
            ),
            localId = localId,
            serverId = 21,
        )
    }
}
