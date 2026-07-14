package ru.shmr.finance

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import ru.shmr.finance.ui.FinanceApp
import ru.shmr.finance.ui.splash.AnimatedSplash
import ru.shmr.finance.ui.theme.SHMRFinanceTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        var keepSystemSplash = true
        splashScreen.setKeepOnScreenCondition { keepSystemSplash }

        super.onCreate(savedInstanceState)

        setContent {
            SHMRFinanceTheme {
                var showSplash by remember { mutableStateOf(true) }
                keepSystemSplash = false
                if (showSplash) {
                    AnimatedSplash(onFinished = { showSplash = false })
                } else {
                    FinanceApp()
                }
            }
        }
    }
}
