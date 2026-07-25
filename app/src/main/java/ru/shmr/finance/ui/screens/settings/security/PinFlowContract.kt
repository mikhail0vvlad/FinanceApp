package ru.shmr.finance.ui.screens.settings.security

import androidx.compose.runtime.Immutable
import ru.shmr.finance.domain.model.PIN_LENGTH
import ru.shmr.finance.domain.model.PinStorageError
import ru.shmr.finance.domain.model.PinVerification

enum class PinFlowMode {
    /** ПИН-кода ещё нет: сразу просим новый. */
    CREATE,

    /** ПИН-код есть: сначала подтверждаем текущий. */
    CHANGE,
}

enum class PinStage {
    CURRENT,
    NEW,
    CONFIRM,
}

enum class PinFlowError {
    WRONG_CURRENT,
    MISMATCH,

    /** Keystore или хранилище недоступны — записать ПИН-код некуда. */
    STORAGE_UNAVAILABLE,

    /** Старый верификатор нечитаем: подтверждать текущий ПИН-код нечем. */
    CREDENTIAL_UNREADABLE,
}

@Immutable
data class PinFlowState(
    val mode: PinFlowMode,
    val stage: PinStage,
    val entry: String = "",
    val newPin: String = "",
    val error: PinFlowError? = null,
    val isBusy: Boolean = false,
    val isFinished: Boolean = false,
) {
    companion object {
        fun initial(mode: PinFlowMode): PinFlowState = PinFlowState(
            mode = mode,
            stage = if (mode == PinFlowMode.CHANGE) PinStage.CURRENT else PinStage.NEW,
        )
    }
}

sealed interface PinFlowAction {
    data class Digit(val value: Char) : PinFlowAction
    data object Backspace : PinFlowAction
    data class CurrentPinChecked(val result: PinVerification) : PinFlowAction
    data class PinPersisted(val error: PinStorageError?) : PinFlowAction
}

/** Побочный эффект, который умеет выполнять только ViewModel: редьюсер остаётся чистым. */
sealed interface PinFlowCommand {
    data class VerifyCurrent(val pin: String) : PinFlowCommand
    data class Persist(val pin: String) : PinFlowCommand
    data object Finish : PinFlowCommand
}

data class PinFlowStep(
    val state: PinFlowState,
    val command: PinFlowCommand? = null,
)

fun reducePinFlow(state: PinFlowState, action: PinFlowAction): PinFlowStep = when (action) {
    is PinFlowAction.Digit -> appendDigit(state, action.value)
    PinFlowAction.Backspace -> PinFlowStep(removeDigit(state))
    is PinFlowAction.CurrentPinChecked -> PinFlowStep(applyCurrentCheck(state, action.result))
    is PinFlowAction.PinPersisted -> applyPersistResult(state, action.error)
}

private fun appendDigit(state: PinFlowState, value: Char): PinFlowStep {
    if (state.isBusy || state.isFinished) return PinFlowStep(state)
    if (!value.isDigit() || state.entry.length >= PIN_LENGTH) return PinFlowStep(state)

    val entry = state.entry + value
    if (entry.length < PIN_LENGTH) {
        return PinFlowStep(state.copy(entry = entry, error = null))
    }

    return when (state.stage) {
        PinStage.CURRENT -> PinFlowStep(
            state.copy(entry = entry, error = null, isBusy = true),
            PinFlowCommand.VerifyCurrent(entry),
        )

        PinStage.NEW -> PinFlowStep(
            state.copy(stage = PinStage.CONFIRM, newPin = entry, entry = "", error = null),
        )

        PinStage.CONFIRM -> if (entry == state.newPin) {
            PinFlowStep(
                state.copy(entry = entry, error = null, isBusy = true),
                PinFlowCommand.Persist(entry),
            )
        } else {
            // Повтор не совпал — начинаем ввод нового ПИН-кода заново, а не только повтор:
            // иначе пользователь будет угадывать, какая из двух попыток была ошибочной.
            PinFlowStep(
                state.copy(
                    stage = PinStage.NEW,
                    entry = "",
                    newPin = "",
                    error = PinFlowError.MISMATCH,
                ),
            )
        }
    }
}

private fun removeDigit(state: PinFlowState): PinFlowState {
    if (state.isBusy || state.isFinished) return state
    return state.copy(entry = state.entry.dropLast(1), error = null)
}

private fun applyCurrentCheck(state: PinFlowState, result: PinVerification): PinFlowState =
    when (result) {
        PinVerification.Match -> state.copy(
            stage = PinStage.NEW,
            entry = "",
            newPin = "",
            error = null,
            isBusy = false,
        )

        PinVerification.Mismatch -> state.copy(
            entry = "",
            error = PinFlowError.WRONG_CURRENT,
            isBusy = false,
        )

        is PinVerification.Unavailable -> when (result.error) {
            // Старый ПИН-код подтвердить нечем, но пользователь уже в настройках и
            // прошёл экран блокировки — даём задать новый вместо тупика.
            PinStorageError.CREDENTIAL_UNREADABLE -> state.copy(
                stage = PinStage.NEW,
                entry = "",
                newPin = "",
                error = PinFlowError.CREDENTIAL_UNREADABLE,
                isBusy = false,
            )

            PinStorageError.DEVICE_STORAGE_UNAVAILABLE -> state.copy(
                entry = "",
                error = PinFlowError.STORAGE_UNAVAILABLE,
                isBusy = false,
            )
        }
    }

private fun applyPersistResult(state: PinFlowState, error: PinStorageError?): PinFlowStep =
    if (error == null) {
        PinFlowStep(
            state.copy(entry = "", newPin = "", isBusy = false, isFinished = true),
            PinFlowCommand.Finish,
        )
    } else {
        PinFlowStep(
            state.copy(
                stage = PinStage.NEW,
                entry = "",
                newPin = "",
                isBusy = false,
                error = when (error) {
                    PinStorageError.DEVICE_STORAGE_UNAVAILABLE -> PinFlowError.STORAGE_UNAVAILABLE
                    PinStorageError.CREDENTIAL_UNREADABLE -> PinFlowError.CREDENTIAL_UNREADABLE
                },
            ),
        )
    }
