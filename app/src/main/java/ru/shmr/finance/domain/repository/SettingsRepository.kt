package ru.shmr.finance.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.shmr.finance.domain.model.AppCurrency
import ru.shmr.finance.domain.model.AppLanguage
import ru.shmr.finance.domain.model.AppSettings
import ru.shmr.finance.domain.model.ThemeMode

interface SettingsRepository {
    val settings: Flow<AppSettings>

    suspend fun setThemeMode(mode: ThemeMode)

    suspend fun setLanguage(language: AppLanguage)

    suspend fun setCurrency(currency: AppCurrency)

    suspend fun setPinEnabled(enabled: Boolean)

    suspend fun setBiometricsEnabled(enabled: Boolean)
}
