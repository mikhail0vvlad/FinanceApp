package ru.shmr.finance.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.flow.collect
import ru.shmr.finance.di.ServiceLocator
import ru.shmr.finance.domain.model.AppSettings
import ru.shmr.finance.ui.lock.AppLockGate
import ru.shmr.finance.ui.splash.AnimatedSplash
import ru.shmr.finance.ui.theme.SHMRFinanceTheme

/**
 * Root composable: applies the persisted theme and locale, shows the in-app splash, then gates
 * the app behind the PIN/biometric lock. [onSplashReady] lets the caller drop the system splash
 * screen once this composable is ready to draw its own content.
 */
@Composable
fun AppRoot(onSplashReady: () -> Unit = {}) {
    val persistedSettings by produceState<AppSettings?>(initialValue = null) {
        ServiceLocator.settingsRepository.settings.collect { value = it }
    }
    val settings = persistedSettings ?: AppSettings()
    LaunchedEffect(persistedSettings?.language) {
        val language = persistedSettings?.language ?: return@LaunchedEffect
        val locales = LocaleListCompat.forLanguageTags(language.languageTag)
        if (
            AppCompatDelegate.getApplicationLocales().toLanguageTags() !=
            locales.toLanguageTags()
        ) {
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }
    SHMRFinanceTheme(themeMode = settings.themeMode) {
        var showSplash by remember { mutableStateOf(true) }
        onSplashReady()
        if (showSplash) {
            AnimatedSplash(onFinished = { showSplash = false })
        } else {
            // Гейт снаружи FinanceApp: навигация внутри переживает блокировку и
            // пользователь возвращается на тот же экран.
            AppLockGate { FinanceApp() }
        }
    }
}
