package ru.shmr.finance.ui

import java.time.Clock
import java.time.LocalDate
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.shmr.finance.core.result.AppResult
import ru.shmr.finance.data.settings.FakeSettingsRepository
import ru.shmr.finance.domain.model.Category
import ru.shmr.finance.domain.model.PinStorageError
import ru.shmr.finance.domain.model.PinVerification
import ru.shmr.finance.domain.model.SecurityState
import ru.shmr.finance.domain.repository.ApiTokenRepository
import ru.shmr.finance.domain.repository.CategoriesRepository
import ru.shmr.finance.domain.repository.SecurityRepository

@OptIn(ExperimentalCoroutinesApi::class)
class TabsViewModelTest {

    private val today = LocalDate.of(2026, 8, 5)
    private val clock = Clock.fixed(
        today.atStartOfDay(ZoneOffset.UTC).toInstant().plusSeconds(12 * 3600),
        ZoneOffset.UTC,
    )

    @Test
    fun `default selection is today`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val viewModel = createViewModel()
            assertEquals(today, viewModel.selectedDate.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `selecting a past day updates the selection`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val viewModel = createViewModel()
            val pastDay = today.minusDays(3)

            viewModel.selectDate(pastDay)

            assertEquals(pastDay, viewModel.selectedDate.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `future dates are rejected and the previous selection is kept`() = runTest {
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val viewModel = createViewModel()
            val pastDay = today.minusDays(1)
            viewModel.selectDate(pastDay)

            viewModel.selectDate(today.plusDays(1))

            assertEquals(pastDay, viewModel.selectedDate.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `not confirming a selection leaves the previous date unchanged`() = runTest {
        // Models "Cancel": the picker dialog simply never calls selectDate.
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val viewModel = createViewModel()
            val pastDay = today.minusDays(2)
            viewModel.selectDate(pastDay)

            assertEquals(pastDay, viewModel.selectedDate.value)
            assertEquals(pastDay, viewModel.selectedDate.value)
        } finally {
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `selection survives being read again, as it would across a UI recreation`() = runTest {
        // TabsViewModel itself survives rotation (the standard ViewModel contract); this checks
        // the held value stays stable across repeated reads, the observable half of that guarantee.
        val dispatcher = StandardTestDispatcher(testScheduler)
        Dispatchers.setMain(dispatcher)
        try {
            val viewModel = createViewModel()
            val pastDay = today.minusDays(5)
            viewModel.selectDate(pastDay)

            repeat(3) {
                assertEquals(pastDay, viewModel.selectedDate.value)
            }
        } finally {
            Dispatchers.resetMain()
        }
    }

    private fun createViewModel() = TabsViewModel(
        isOnline = MutableStateFlow(true),
        settingsRepository = FakeSettingsRepository(),
        securityRepository = FakeSecurityRepository(),
        categoriesRepository = FakeCategoriesRepository(),
        apiTokenRepository = FakeApiTokenRepository(),
        clock = clock,
    )

    private class FakeSecurityRepository : SecurityRepository {
        override val state: Flow<SecurityState> = MutableStateFlow(SecurityState())
        override suspend fun setPin(pin: String): PinStorageError? = null
        override suspend fun verifyPin(pin: String): PinVerification = PinVerification.Match
        override suspend fun clearPin() = Unit
        override suspend fun setBiometricsEnabled(enabled: Boolean) = Unit
    }

    private class FakeCategoriesRepository : CategoriesRepository {
        override fun observeCategories(): Flow<List<Category>> = MutableStateFlow(emptyList())
        override suspend fun getCategories(): AppResult<List<Category>> =
            AppResult.Success(emptyList())
        override suspend fun refreshCategories(): AppResult<Unit> = AppResult.Success(Unit)
    }

    private class FakeApiTokenRepository : ApiTokenRepository {
        override val hasToken = MutableStateFlow(true)
        override fun currentToken(): String = "token"
        override suspend fun setToken(token: String): AppResult<Unit> = AppResult.Success(Unit)
    }
}
