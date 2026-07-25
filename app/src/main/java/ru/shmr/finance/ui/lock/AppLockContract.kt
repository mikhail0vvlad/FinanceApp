package ru.shmr.finance.ui.lock

import androidx.compose.runtime.Immutable
import ru.shmr.finance.domain.model.BiometricOutcome
import ru.shmr.finance.domain.model.PIN_LENGTH
import ru.shmr.finance.domain.model.PinStorageError
import ru.shmr.finance.domain.model.PinVerification
import ru.shmr.finance.domain.model.SecurityState

/**
 * Пауза, в течение которой возврат из фона не требует повторного ввода. Покрывает
 * переключение на камеру, шторку уведомлений или выбор файла и не даёт экрану блокировки
 * появляться после каждого моргания.
 */
const val LOCK_GRACE_PERIOD_MILLIS: Long = 30_000L

enum class LockPhase {
    /** Настройки безопасности ещё не прочитаны: показывать нечего. */
    PENDING,
    LOCKED,
    UNLOCKED,
}

sealed interface AppLockError {
    data object WrongPin : AppLockError
    data object BiometricLockout : AppLockError
    data object BiometricLockoutPermanent : AppLockError
    data object BiometricUnavailable : AppLockError

    /** Верификатор нечитаем: защита снята принудительно, ПИН-код нужно задать заново. */
    data object CredentialReset : AppLockError
    data class BiometricFailed(val message: String) : AppLockError
}

@Immutable
data class AppLockState(
    val phase: LockPhase = LockPhase.PENDING,
    val isPinSet: Boolean = false,
    val isBiometricsEnabled: Boolean = false,
    val entry: String = "",
    val error: AppLockError? = null,
    val isVerifying: Boolean = false,
    val backgroundedAtMillis: Long? = null,
    /**
     * Запрос показать системный диалог живёт в состоянии, а не в одноразовом событии:
     * блокировка на холодном старте наступает раньше, чем композиция успевает подписаться,
     * и `SharedFlow` без replay такое событие теряет.
     */
    val isBiometricPromptPending: Boolean = false,
) {
    val isLocked: Boolean get() = phase == LockPhase.LOCKED
}

sealed interface AppLockAction {
    data class SecurityStateChanged(val security: SecurityState) : AppLockAction
    data class MovedToBackground(val nowMillis: Long) : AppLockAction
    data class MovedToForeground(val nowMillis: Long) : AppLockAction
    data class Digit(val value: Char) : AppLockAction
    data object Backspace : AppLockAction
    data class PinChecked(val result: PinVerification) : AppLockAction

    /** Пользователь сам попросил показать биометрию кнопкой на экране блокировки. */
    data object BiometricRequested : AppLockAction
    data class BiometricFinished(val outcome: BiometricOutcome) : AppLockAction
}

sealed interface AppLockCommand {
    data class VerifyPin(val pin: String) : AppLockCommand
    data object DisableBiometrics : AppLockCommand

    /** Снять защиту, которую невозможно проверить, чтобы не запереть пользователя навсегда. */
    data object ResetBrokenCredential : AppLockCommand
}

data class AppLockStep(
    val state: AppLockState,
    val command: AppLockCommand? = null,
)

fun reduceAppLock(state: AppLockState, action: AppLockAction): AppLockStep = when (action) {
    is AppLockAction.SecurityStateChanged -> applySecurityState(state, action.security)
    is AppLockAction.MovedToBackground -> AppLockStep(applyBackground(state, action.nowMillis))
    is AppLockAction.MovedToForeground -> applyForeground(state, action.nowMillis)
    is AppLockAction.Digit -> appendDigit(state, action.value)
    AppLockAction.Backspace -> AppLockStep(removeDigit(state))
    is AppLockAction.PinChecked -> applyPinCheck(state, action.result)
    AppLockAction.BiometricRequested -> requestBiometrics(state)
    is AppLockAction.BiometricFinished -> applyBiometricOutcome(state, action.outcome)
}

private fun applySecurityState(state: AppLockState, security: SecurityState): AppLockStep {
    // Без заданного ПИН-кода приложение не блокируется вообще — это условие «Чата 8».
    if (!security.isPinSet) {
        return AppLockStep(
            state.copy(
                phase = LockPhase.UNLOCKED,
                isPinSet = false,
                isBiometricsEnabled = false,
                entry = "",
                isVerifying = false,
                backgroundedAtMillis = null,
                isBiometricPromptPending = false,
            ),
        )
    }

    val updated = state.copy(
        isPinSet = true,
        isBiometricsEnabled = security.isBiometricsEnabled,
    )
    // Холодный старт с уже настроенным ПИН-кодом: запираемся до первой проверки.
    return if (state.phase == LockPhase.PENDING) {
        lock(updated)
    } else {
        AppLockStep(updated)
    }
}

private fun applyBackground(state: AppLockState, nowMillis: Long): AppLockState =
    if (state.isPinSet && state.phase == LockPhase.UNLOCKED) {
        state.copy(backgroundedAtMillis = nowMillis)
    } else {
        state
    }

private fun applyForeground(state: AppLockState, nowMillis: Long): AppLockStep {
    val backgroundedAt = state.backgroundedAtMillis ?: return AppLockStep(state)
    val cleared = state.copy(backgroundedAtMillis = null)
    if (!state.isPinSet || state.phase != LockPhase.UNLOCKED) return AppLockStep(cleared)

    val awayMillis = nowMillis - backgroundedAt
    return if (awayMillis >= LOCK_GRACE_PERIOD_MILLIS) lock(cleared) else AppLockStep(cleared)
}

private fun lock(state: AppLockState): AppLockStep = AppLockStep(
    state.copy(
        phase = LockPhase.LOCKED,
        entry = "",
        error = null,
        isVerifying = false,
        backgroundedAtMillis = null,
        isBiometricPromptPending = state.isBiometricsEnabled,
    ),
)

private fun appendDigit(state: AppLockState, value: Char): AppLockStep {
    if (!state.isLocked || state.isVerifying) return AppLockStep(state)
    if (!value.isDigit() || state.entry.length >= PIN_LENGTH) return AppLockStep(state)

    val entry = state.entry + value
    return if (entry.length < PIN_LENGTH) {
        AppLockStep(state.copy(entry = entry, error = null))
    } else {
        AppLockStep(
            state.copy(entry = entry, error = null, isVerifying = true),
            AppLockCommand.VerifyPin(entry),
        )
    }
}

private fun removeDigit(state: AppLockState): AppLockState {
    if (!state.isLocked || state.isVerifying) return state
    return state.copy(entry = state.entry.dropLast(1), error = null)
}

private fun applyPinCheck(state: AppLockState, result: PinVerification): AppLockStep =
    when (result) {
        PinVerification.Match -> AppLockStep(
            state.copy(
                phase = LockPhase.UNLOCKED,
                entry = "",
                error = null,
                isVerifying = false,
                backgroundedAtMillis = null,
                isBiometricPromptPending = false,
            ),
        )

        PinVerification.Mismatch -> AppLockStep(
            state.copy(entry = "", error = AppLockError.WrongPin, isVerifying = false),
        )

        is PinVerification.Unavailable -> when (result.error) {
            // Проверить ПИН-код невозможно ни одной попыткой: держать пользователя на
            // экране блокировки бессмысленно, поэтому защиту снимаем и сообщаем об этом.
            PinStorageError.CREDENTIAL_UNREADABLE -> AppLockStep(
                state.copy(
                    phase = LockPhase.UNLOCKED,
                    isPinSet = false,
                    isBiometricsEnabled = false,
                    entry = "",
                    error = AppLockError.CredentialReset,
                    isVerifying = false,
                    isBiometricPromptPending = false,
                ),
                AppLockCommand.ResetBrokenCredential,
            )

            PinStorageError.DEVICE_STORAGE_UNAVAILABLE -> AppLockStep(
                state.copy(entry = "", error = AppLockError.WrongPin, isVerifying = false),
            )
        }
    }

private fun requestBiometrics(state: AppLockState): AppLockStep =
    if (state.isLocked && state.isBiometricsEnabled && !state.isVerifying) {
        AppLockStep(state.copy(error = null, isBiometricPromptPending = true))
    } else {
        AppLockStep(state)
    }

/** Любой исход снимает запрос: иначе диалог показывался бы бесконечно. */
private fun applyBiometricOutcome(
    state: AppLockState,
    outcome: BiometricOutcome,
): AppLockStep {
    val settled = state.copy(isBiometricPromptPending = false)
    return when (outcome) {
        BiometricOutcome.Success -> AppLockStep(
            settled.copy(
                phase = LockPhase.UNLOCKED,
                entry = "",
                error = null,
                isVerifying = false,
                backgroundedAtMillis = null,
            ),
        )

        // Отмена — это осознанный выбор ввести ПИН-код, а не ошибка.
        BiometricOutcome.Cancelled -> AppLockStep(settled.copy(error = null))

        BiometricOutcome.Lockout -> AppLockStep(settled.copy(error = AppLockError.BiometricLockout))

        BiometricOutcome.LockoutPermanent -> AppLockStep(
            settled.copy(error = AppLockError.BiometricLockoutPermanent),
        )

        // Датчик исчез или сломан: выключаем биометрию, чтобы не показывать мёртвую кнопку.
        BiometricOutcome.Unavailable -> AppLockStep(
            settled.copy(isBiometricsEnabled = false, error = AppLockError.BiometricUnavailable),
            AppLockCommand.DisableBiometrics,
        )

        is BiometricOutcome.Failed -> AppLockStep(
            settled.copy(error = AppLockError.BiometricFailed(outcome.message)),
        )
    }
}
