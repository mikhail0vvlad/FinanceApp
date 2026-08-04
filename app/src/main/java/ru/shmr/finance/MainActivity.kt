package ru.shmr.finance

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import ru.shmr.finance.ui.AppRoot

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        var keepSystemSplash = true
        splashScreen.setKeepOnScreenCondition { keepSystemSplash }

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AppRoot(onSplashReady = { keepSystemSplash = false })
        }
    }
}
