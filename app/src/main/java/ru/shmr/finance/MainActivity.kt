package ru.shmr.finance

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.os.LocaleListCompat
import kotlinx.coroutines.flow.collect
import ru.shmr.finance.di.ServiceLocator
import ru.shmr.finance.domain.model.AppSettings
import ru.shmr.finance.ui.FinanceApp
import ru.shmr.finance.ui.lock.AppLockGate
import ru.shmr.finance.ui.splash.AnimatedSplash
import ru.shmr.finance.ui.theme.SHMRFinanceTheme

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        var keepSystemSplash = true
        splashScreen.setKeepOnScreenCondition { keepSystemSplash }

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
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
                keepSystemSplash = false
                if (showSplash) {
                    AnimatedSplash(onFinished = { showSplash = false })
                } else {
                    // Гейт снаружи FinanceApp: навигация внутри переживает блокировку и
                    // пользователь возвращается на тот же экран.
                    AppLockGate { FinanceApp() }
                }
            }
        }
    }
}
