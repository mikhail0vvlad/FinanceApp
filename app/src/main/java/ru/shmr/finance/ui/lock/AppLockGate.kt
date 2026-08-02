package ru.shmr.finance.ui.lock

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.shmr.finance.R
import ru.shmr.finance.data.security.BiometricAuthenticator
import ru.shmr.finance.di.ServiceLocator
import ru.shmr.finance.ui.screens.settings.security.findFragmentActivity

/**
 * Заблокировано ли приложение прямо сейчас.
 *
 * Оверлей гейта лежит в окне Activity и физически не может накрыть `ModalBottomSheet`, который
 * рисуется в собственном окне поверх Activity. Поэтому листы обязаны сами убираться из
 * композиции на время блокировки — иначе они остаются видимыми и кликабельными поверх экрана
 * ввода ПИН-кода.
 */
val LocalAppLocked = compositionLocalOf { false }

/**
 * Экран блокировки поверх приложения.
 *
 * [content] намеренно остаётся в композиции: после успешной проверки пользователь видит тот
 * же экран и то же состояние навигации, с которого ушёл. Оверлей полностью непрозрачен и
 * перехватывает касания, а `BackHandler` съедает системную кнопку «Назад», поэтому обойти
 * его нельзя.
 */
@Composable
fun AppLockGate(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    val authenticator = remember(context) { BiometricAuthenticator(context) }
    val factory = remember {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                AppLockViewModel(ServiceLocator.securityRepository) as T
        }
    }
    val viewModel: AppLockViewModel = viewModel(factory = factory)
    val state by viewModel.state.collectAsStateWithLifecycle()

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, viewModel) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> viewModel.onMovedToBackground()
                Lifecycle.Event.ON_START -> viewModel.onMovedToForeground()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val promptTitle = stringResource(R.string.lock_prompt_title)
    val promptSubtitle = stringResource(R.string.lock_prompt_subtitle)
    val promptNegative = stringResource(R.string.security_biometrics_prompt_negative)

    // Диалог поднимается по состоянию, а не по событию: запрос переживает то, что композиция
    // подписывается позже блокировки, и не теряется при пересоздании Activity.
    LaunchedEffect(state.isBiometricPromptPending, activity) {
        if (!state.isBiometricPromptPending) return@LaunchedEffect
        val host = activity ?: return@LaunchedEffect
        viewModel.onAction(
            AppLockAction.BiometricFinished(
                authenticator.authenticate(
                    activity = host,
                    title = promptTitle,
                    subtitle = promptSubtitle,
                    negativeButton = promptNegative,
                ),
            ),
        )
    }

    val locked = state.phase != LockPhase.UNLOCKED

    Box(modifier = Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalAppLocked provides locked) {
            content()
        }

        if (locked) {
            BackHandler(enabled = true) {
                // Экран блокировки — тупик по построению: «Назад» его не закрывает.
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    // Гасит касания по скрытому под оверлеем приложению: непрозрачный фон
                    // сам по себе события не перехватывает.
                    .pointerInput(Unit) {
                        awaitPointerEventScope {
                            while (true) {
                                awaitPointerEvent().changes.forEach { it.consume() }
                            }
                        }
                    },
            ) {
                // PENDING — настройки безопасности ещё читаются с диска. Показываем тот же
                // экран без клавиатуры, чтобы не мигнуть содержимым защищённого приложения.
                if (state.phase == LockPhase.LOCKED) {
                    AppLockScreen(
                        state = state,
                        onDigit = { viewModel.onAction(AppLockAction.Digit(it)) },
                        onBackspace = { viewModel.onAction(AppLockAction.Backspace) },
                        onBiometricRequested = {
                            viewModel.onAction(AppLockAction.BiometricRequested)
                        },
                    )
                } else {
                    AppLockPlaceholder()
                }
            }
        }
    }
}
