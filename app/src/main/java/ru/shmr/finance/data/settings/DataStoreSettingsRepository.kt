package ru.shmr.finance.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import ru.shmr.finance.domain.model.AppCurrency
import ru.shmr.finance.domain.model.AppLanguage
import ru.shmr.finance.domain.model.AppSettings
import ru.shmr.finance.domain.model.SecuritySettings
import ru.shmr.finance.domain.model.ThemeMode
import ru.shmr.finance.domain.repository.SettingsRepository

private const val SETTINGS_DATA_STORE_NAME = "settings"

private val Context.settingsDataStore by preferencesDataStore(name = SETTINGS_DATA_STORE_NAME)

internal object SettingsPreferenceKeys {
    val themeMode = stringPreferencesKey("theme_mode")
    val language = stringPreferencesKey("language")
    val currency = stringPreferencesKey("currency")
    val pinEnabled = booleanPreferencesKey("pin_enabled")
    val biometricsEnabled = booleanPreferencesKey("biometrics_enabled")
}

internal fun Preferences.toAppSettings(): AppSettings = AppSettings(
    themeMode = this[SettingsPreferenceKeys.themeMode]
        ?.let { value -> ThemeMode.entries.find { it.name == value } }
        ?: ThemeMode.SYSTEM,
    language = AppLanguage.fromLanguageTag(
        this[SettingsPreferenceKeys.language].orEmpty(),
    ),
    currency = AppCurrency.fromCode(
        this[SettingsPreferenceKeys.currency].orEmpty(),
    ),
    security = SecuritySettings(
        isPinEnabled = this[SettingsPreferenceKeys.pinEnabled] ?: false,
        isBiometricsEnabled = this[SettingsPreferenceKeys.biometricsEnabled] ?: false,
    ),
)

internal class DataStoreSettingsRepository internal constructor(
    private val dataStore: DataStore<Preferences>,
) : SettingsRepository {

    constructor(context: Context) : this(context.settingsDataStore)

    override val settings: Flow<AppSettings> = dataStore.data
        .catch { error ->
            if (error is IOException) {
                emit(emptyPreferences())
            } else {
                throw error
            }
        }
        .map(Preferences::toAppSettings)

    override suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[SettingsPreferenceKeys.themeMode] = mode.name }
    }

    override suspend fun setLanguage(language: AppLanguage) {
        dataStore.edit { it[SettingsPreferenceKeys.language] = language.languageTag }
    }

    override suspend fun setCurrency(currency: AppCurrency) {
        dataStore.edit { it[SettingsPreferenceKeys.currency] = currency.currency.code }
    }

    override suspend fun setPinEnabled(enabled: Boolean) {
        dataStore.edit { it[SettingsPreferenceKeys.pinEnabled] = enabled }
    }

    override suspend fun setBiometricsEnabled(enabled: Boolean) {
        dataStore.edit { it[SettingsPreferenceKeys.biometricsEnabled] = enabled }
    }
}
