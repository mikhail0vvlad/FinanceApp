package ru.shmr.finance.ui.screens.settings.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.shmr.finance.domain.repository.SecurityRepository

sealed interface PinFlowEffect {
    /** ПИН-код записан или удалён — экран можно закрывать. */
    data object Completed : PinFlowEffect
}

class PinFlowViewModel(
    mode: PinFlowMode,
    private val securityRepository: SecurityRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(PinFlowState.initial(mode))
    val state: StateFlow<PinFlowState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<PinFlowEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<PinFlowEffect> = _effects.asSharedFlow()

    fun onAction(action: PinFlowAction) {
        apply(reducePinFlow(_state.value, action))
    }

    /** Снимает защиту целиком: единственный способ выключить блокировку приложения. */
    fun removePin() {
        if (_state.value.isBusy) return
        _state.value = _state.value.copy(isBusy = true)
        viewModelScope.launch {
            securityRepository.clearPin()
            _state.value = _state.value.copy(isBusy = false, isFinished = true)
            _effects.tryEmit(PinFlowEffect.Completed)
        }
    }

    private fun apply(step: PinFlowStep) {
        _state.value = step.state
        when (val command = step.command) {
            is PinFlowCommand.VerifyCurrent -> viewModelScope.launch {
                onAction(
                    PinFlowAction.CurrentPinChecked(securityRepository.verifyPin(command.pin)),
                )
            }

            is PinFlowCommand.Persist -> viewModelScope.launch {
                onAction(PinFlowAction.PinPersisted(securityRepository.setPin(command.pin)))
            }

            PinFlowCommand.Finish -> _effects.tryEmit(PinFlowEffect.Completed)
            null -> Unit
        }
    }
}
