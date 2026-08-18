package ru.shmr.finance.ui

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.test.core.app.ApplicationProvider
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import ru.shmr.finance.core.state.UiState
import ru.shmr.finance.data.settings.DataStoreSettingsRepository
import ru.shmr.finance.domain.model.AppSettings
import ru.shmr.finance.domain.model.SecurityState
import ru.shmr.finance.domain.model.ThemeMode
import ru.shmr.finance.ui.screens.settings.SettingsSheet
import ru.shmr.finance.ui.screens.settings.security.PinFlowAction
import ru.shmr.finance.ui.screens.settings.security.PinFlowCommand
import ru.shmr.finance.ui.screens.settings.security.PinFlowMode
import ru.shmr.finance.ui.screens.settings.security.PinFlowScreen
import ru.shmr.finance.ui.screens.settings.security.PinFlowState
import ru.shmr.finance.ui.screens.settings.security.PinFlowError
import ru.shmr.finance.ui.screens.settings.security.PinStage
import ru.shmr.finance.ui.screens.settings.security.reducePinFlow
import ru.shmr.finance.ui.testing.PinTestTags
import ru.shmr.finance.ui.testing.PinProgress
import ru.shmr.finance.ui.testing.SettingsTestTags
import ru.shmr.finance.ui.theme.SHMRFinanceTheme

class BonusSettingsUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun themeScreenSelectsDarkAndPersistsIt() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dataStoreScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val dataStoreFile = context.preferencesDataStoreFile(
            "theme-ui-${UUID.randomUUID()}.preferences_pb",
        )
        val dataStore = PreferenceDataStoreFactory.create(
            scope = dataStoreScope,
            produceFile = { dataStoreFile },
        )
        val repository = DataStoreSettingsRepository(dataStore)
        runBlocking { repository.setThemeMode(ThemeMode.LIGHT) }

        try {
            composeRule.setContent {
                val settings by repository.settings.collectAsState(initial = AppSettings())
                val coroutineScope = rememberCoroutineScope()
                SHMRFinanceTheme(themeMode = settings.themeMode) {
                    SettingsSheet(
                        settings = settings,
                        security = SecurityState(),
                        categoriesState = UiState.Empty,
                        tokenConfigured = false,
                        onCurrencySelected = {},
                        onThemeModeSelected = { mode ->
                            coroutineScope.launch { repository.setThemeMode(mode) }
                        },
                        onLanguageSelected = {},
                        onDismiss = {},
                    )
                }
            }

            composeRule.onNodeWithTag(SettingsTestTags.THEME_ROW).performClick()
            composeRule.onNodeWithTag(SettingsTestTags.themeOption(ThemeMode.LIGHT))
                .assertIsSelected()
            composeRule.onNodeWithTag(SettingsTestTags.themeOption(ThemeMode.DARK)).performClick()

            composeRule.waitUntil(timeoutMillis = 5_000) {
                runBlocking { repository.settings.first().themeMode == ThemeMode.DARK }
            }
            composeRule.onNodeWithTag(SettingsTestTags.themeOption(ThemeMode.DARK))
                .assertIsSelected()
            assertEquals(ThemeMode.DARK, runBlocking { repository.settings.first().themeMode })
        } finally {
            dataStoreScope.cancel()
            dataStoreFile.delete()
        }
    }

    @Test
    fun pinEntryCoversStagesMismatchBackspaceAndConfirmation() {
        val persistedPin = AtomicReference<String?>(null)
        composeRule.setContent {
            var state by remember { mutableStateOf(PinFlowState.initial(PinFlowMode.CREATE)) }
            SHMRFinanceTheme {
                PinFlowScreen(
                    state = state,
                    canRemovePin = false,
                    onDigit = { digit ->
                        val step = reducePinFlow(state, PinFlowAction.Digit(digit))
                        state = step.state
                        (step.command as? PinFlowCommand.Persist)?.let { command ->
                            persistedPin.set(command.pin)
                        }
                    },
                    onBackspace = {
                        state = reducePinFlow(state, PinFlowAction.Backspace).state
                    },
                    onRemovePin = {},
                    onBack = {},
                )
            }
        }

        enterPin("1234")
        composeRule.onNodeWithTag(PinTestTags.stage(PinStage.CONFIRM)).assertIsDisplayed()

        enterPin("1235")
        composeRule.onNodeWithTag(PinTestTags.stage(PinStage.NEW)).assertIsDisplayed()
        composeRule.onNodeWithTag(PinTestTags.error(PinFlowError.MISMATCH)).assertIsDisplayed()

        composeRule.onNodeWithTag(PinTestTags.digit('9')).performClick()
        composeRule.onNodeWithTag(PinTestTags.digit('8')).performClick()
        composeRule.onNodeWithTag(PinTestTags.BACKSPACE).performClick()
        composeRule.onNodeWithTag(PinTestTags.DOTS).assert(
            SemanticsMatcher.expectValue(PinProgress, 1),
        )
        enterPin("876")
        composeRule.onNodeWithTag(PinTestTags.stage(PinStage.CONFIRM)).assertIsDisplayed()
        enterPin("9876")

        composeRule.waitUntil { persistedPin.get() == "9876" }
        assertEquals("9876", persistedPin.get())
    }

    private fun enterPin(pin: String) {
        pin.forEach { digit ->
            composeRule.onNodeWithTag(PinTestTags.digit(digit)).performClick()
        }
    }
}
