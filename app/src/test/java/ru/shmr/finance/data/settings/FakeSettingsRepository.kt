package ru.shmr.finance.data.settings

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import ru.shmr.finance.domain.model.AppCurrency
import ru.shmr.finance.domain.model.AppLanguage
import ru.shmr.finance.domain.model.AppSettings
import ru.shmr.finance.domain.model.ThemeMode
import ru.shmr.finance.domain.repository.SettingsRepository

internal class FakeSettingsRepository(
    initialSettings: AppSettings = AppSettings(),
) : SettingsRepository {
    private val state = MutableStateFlow(initialSettings)

    override val settings: Flow<AppSettings> = state

    override suspend fun setThemeMode(mode: ThemeMode) {
        state.update { it.copy(themeMode = mode) }
    }

    override suspend fun setLanguage(language: AppLanguage) {
        state.update { it.copy(language = language) }
    }

    override suspend fun setCurrency(currency: AppCurrency) {
        state.update { it.copy(currency = currency) }
    }

    override suspend fun setPinEnabled(enabled: Boolean) {
        state.update {
            it.copy(security = it.security.copy(isPinEnabled = enabled))
        }
    }

    override suspend fun setBiometricsEnabled(enabled: Boolean) {
        state.update {
            it.copy(security = it.security.copy(isBiometricsEnabled = enabled))
        }
    }
}
