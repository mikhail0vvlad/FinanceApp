package ru.shmr.finance.data.settings

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.mutablePreferencesOf
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import ru.shmr.finance.domain.model.AppCurrency
import ru.shmr.finance.domain.model.AppLanguage
import ru.shmr.finance.domain.model.ThemeMode

class DataStoreSettingsRepositoryTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `updates persist all regular and security preferences`() = runTest {
        val dataStore = PreferenceDataStoreFactory.create(
            scope = backgroundScope,
            produceFile = {
                File(temporaryFolder.root, "settings.preferences_pb")
            },
        )
        val repository = DataStoreSettingsRepository(dataStore)

        repository.setThemeMode(ThemeMode.DARK)
        repository.setLanguage(AppLanguage.GERMAN)
        repository.setCurrency(AppCurrency.EUR)
        repository.setPinEnabled(true)
        repository.setBiometricsEnabled(true)

        val settings = repository.settings.first()
        assertEquals(ThemeMode.DARK, settings.themeMode)
        assertEquals(AppLanguage.GERMAN, settings.language)
        assertEquals(AppCurrency.EUR, settings.currency)
        assertEquals(true, settings.security.isPinEnabled)
        assertEquals(true, settings.security.isBiometricsEnabled)
    }

    @Test
    fun `unknown stored values map to safe defaults`() {
        val preferences = mutablePreferencesOf(
            SettingsPreferenceKeys.themeMode to "AMOLED",
            SettingsPreferenceKeys.language to "it",
            SettingsPreferenceKeys.currency to "BTC",
        )

        val settings = preferences.toAppSettings()

        assertEquals(ThemeMode.SYSTEM, settings.themeMode)
        assertEquals(AppLanguage.RUSSIAN, settings.language)
        assertEquals(AppCurrency.RUB, settings.currency)
        assertFalse(settings.security.isPinEnabled)
        assertFalse(settings.security.isBiometricsEnabled)
    }
}
