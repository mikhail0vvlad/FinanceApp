package ru.shmr.finance.ui.lock

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.shmr.finance.domain.repository.SecurityRepository

class AppLockViewModel(
    private val securityRepository: SecurityRepository,
    private val clock: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val _state = MutableStateFlow(AppLockState())
    val state: StateFlow<AppLockState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            securityRepository.state.collect { security ->
                onAction(AppLockAction.SecurityStateChanged(security))
            }
        }
    }

    fun onAction(action: AppLockAction) {
        apply(reduceAppLock(_state.value, action))
    }

    fun onMovedToBackground() = onAction(AppLockAction.MovedToBackground(clock()))

    fun onMovedToForeground() = onAction(AppLockAction.MovedToForeground(clock()))

    private fun apply(step: AppLockStep) {
        _state.value = step.state
        when (val command = step.command) {
            is AppLockCommand.VerifyPin -> viewModelScope.launch {
                onAction(AppLockAction.PinChecked(securityRepository.verifyPin(command.pin)))
            }

            AppLockCommand.DisableBiometrics -> viewModelScope.launch {
                securityRepository.setBiometricsEnabled(false)
            }

            AppLockCommand.ResetBrokenCredential -> viewModelScope.launch {
                securityRepository.clearPin()
            }

            null -> Unit
        }
    }
}
