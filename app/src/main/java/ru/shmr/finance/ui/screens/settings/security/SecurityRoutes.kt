package ru.shmr.finance.ui.screens.settings.security

import android.content.Context
import android.content.ContextWrapper
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.shmr.finance.R
import ru.shmr.finance.data.security.BiometricAuthenticator
import ru.shmr.finance.di.ServiceLocator

/**
 * Экраны безопасности живут внутри settings sheet, поэтому у них нет своей записи в
 * back stack и ViewModel нельзя привязать к NavBackStackEntry. Владелец создаётся вручную
 * и очищается вместе с уходом с экрана.
 */
private class SecurityViewModelStoreOwner : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
}

@Composable
private fun rememberSecurityStoreOwner(key: Any?): ViewModelStoreOwner {
    val owner = remember(key) { SecurityViewModelStoreOwner() }
    DisposableEffect(owner) {
        onDispose { owner.viewModelStore.clear() }
    }
    return owner
}

@Composable
fun PinFlowRoute(
    isPinSet: Boolean,
    onBack: () -> Unit,
    onCompleted: () -> Unit,
) {
    // Режим фиксируется на момент открытия экрана. Если считывать `isPinSet` вживую, то
    // запись или удаление ПИН-кода меняет режим, пересоздаёт ViewModel и обрывает сбор
    // эффектов — сигнал о завершении теряется, и пользователь остаётся на клавиатуре.
    val mode = rememberSaveable {
        if (isPinSet) PinFlowMode.CHANGE.name else PinFlowMode.CREATE.name
    }.let(PinFlowMode::valueOf)
    val storeOwner = rememberSecurityStoreOwner(mode)
    val factory = remember(mode) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = PinFlowViewModel(
                mode = mode,
                securityRepository = ServiceLocator.securityRepository,
            ) as T
        }
    }
    val viewModel: PinFlowViewModel = viewModel(
        viewModelStoreOwner = storeOwner,
        factory = factory,
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                PinFlowEffect.Completed -> onCompleted()
            }
        }
    }

    PinFlowScreen(
        state = state,
        canRemovePin = mode == PinFlowMode.CHANGE,
        onDigit = { viewModel.onAction(PinFlowAction.Digit(it)) },
        onBackspace = { viewModel.onAction(PinFlowAction.Backspace) },
        onRemovePin = viewModel::removePin,
        onBack = onBack,
    )
}

@Composable
fun BiometricsRoute(onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.findFragmentActivity() }
    val authenticator = remember(context) { BiometricAuthenticator(context) }
    val storeOwner = rememberSecurityStoreOwner(Unit)
    val factory = remember(authenticator) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                BiometricSettingsViewModel(
                    securityRepository = ServiceLocator.securityRepository,
                    availabilityProvider = authenticator::availability,
                ) as T
        }
    }
    val viewModel: BiometricSettingsViewModel = viewModel(
        viewModelStoreOwner = storeOwner,
        factory = factory,
    )
    val state by viewModel.state.collectAsStateWithLifecycle()

    // Пользователь мог уйти в системные настройки и зарегистрировать отпечаток.
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { viewModel.refreshAvailability() }

    val promptTitle = stringResource(R.string.security_biometrics_prompt_title)
    val promptSubtitle = stringResource(R.string.security_biometrics_prompt_subtitle)
    val promptNegative = stringResource(R.string.security_biometrics_prompt_negative)

    LaunchedEffect(viewModel, activity) {
        viewModel.effects.collect { effect ->
            when (effect) {
                BiometricSettingsEffect.RequestPrompt -> {
                    val host = activity ?: return@collect
                    viewModel.onPromptResult(
                        authenticator.authenticate(
                            activity = host,
                            title = promptTitle,
                            subtitle = promptSubtitle,
                            negativeButton = promptNegative,
                        ),
                    )
                }
            }
        }
    }

    BiometricsScreen(
        state = state,
        onToggle = viewModel::onToggle,
        onBack = onBack,
    )
}

internal fun Context.findFragmentActivity(): FragmentActivity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is FragmentActivity) return current
        current = current.baseContext
    }
    return null
}
