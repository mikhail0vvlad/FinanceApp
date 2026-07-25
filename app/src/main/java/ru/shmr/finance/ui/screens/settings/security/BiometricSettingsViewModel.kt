package ru.shmr.finance.ui.screens.settings.security

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.shmr.finance.domain.model.BiometricAvailability
import ru.shmr.finance.domain.model.BiometricOutcome
import ru.shmr.finance.domain.repository.SecurityRepository

@Immutable
data class BiometricSettingsState(
    val availability: BiometricAvailability = BiometricAvailability.UNSUPPORTED,
    val isPinSet: Boolean = false,
    val isEnabled: Boolean = false,
    val error: BiometricSettingsError? = null,
) {
    /**
     * Тумблер живой, только если датчик действительно готов и есть запасной ПИН-код:
     * иначе включение биометрии не даёт входа в приложение, а отбирает его.
     */
    val canToggle: Boolean
        get() = isPinSet && availability == BiometricAvailability.AVAILABLE
}

enum class BiometricSettingsError {
    LOCKOUT,
    LOCKOUT_PERMANENT,
    UNAVAILABLE,
    FAILED,
}

sealed interface BiometricSettingsEffect {
    /** Включение требует настоящей проверки: показать системный диалог. */
    data object RequestPrompt : BiometricSettingsEffect
}

class BiometricSettingsViewModel(
    private val securityRepository: SecurityRepository,
    private val availabilityProvider: () -> BiometricAvailability,
) : ViewModel() {

    private val _state = MutableStateFlow(
        BiometricSettingsState(availability = availabilityProvider()),
    )
    val state: StateFlow<BiometricSettingsState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<BiometricSettingsEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<BiometricSettingsEffect> = _effects.asSharedFlow()

    init {
        viewModelScope.launch {
            securityRepository.state.collect { security ->
                _state.value = _state.value.copy(
                    isPinSet = security.isPinSet,
                    isEnabled = security.isBiometricsEnabled,
                )
            }
        }
    }

    /** Пересчитывает доступность датчика: пользователь мог зарегистрировать отпечаток. */
    fun refreshAvailability() {
        _state.value = _state.value.copy(availability = availabilityProvider())
    }

    fun onToggle(enabled: Boolean) {
        if (!_state.value.canToggle) return
        _state.value = _state.value.copy(error = null)
        if (enabled) {
            _effects.tryEmit(BiometricSettingsEffect.RequestPrompt)
        } else {
            viewModelScope.launch { securityRepository.setBiometricsEnabled(false) }
        }
    }

    fun onPromptResult(outcome: BiometricOutcome) {
        when (outcome) {
            BiometricOutcome.Success -> viewModelScope.launch {
                securityRepository.setBiometricsEnabled(true)
            }

            BiometricOutcome.Cancelled -> Unit
            BiometricOutcome.Lockout -> setError(BiometricSettingsError.LOCKOUT)
            BiometricOutcome.LockoutPermanent -> setError(BiometricSettingsError.LOCKOUT_PERMANENT)
            BiometricOutcome.Unavailable -> {
                _state.value = _state.value.copy(
                    availability = BiometricAvailability.HARDWARE_UNAVAILABLE,
                    error = BiometricSettingsError.UNAVAILABLE,
                )
            }

            is BiometricOutcome.Failed -> setError(BiometricSettingsError.FAILED)
        }
    }

    private fun setError(error: BiometricSettingsError) {
        _state.value = _state.value.copy(error = error)
    }
}
