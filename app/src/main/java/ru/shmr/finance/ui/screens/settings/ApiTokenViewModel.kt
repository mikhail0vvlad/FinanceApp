package ru.shmr.finance.ui.screens.settings

import androidx.compose.runtime.Immutable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.shmr.finance.core.result.AppResult
import ru.shmr.finance.di.ServiceLocator
import ru.shmr.finance.domain.model.AppError
import ru.shmr.finance.domain.repository.ApiTokenRepository

@Immutable
data class ApiTokenState(
    val input: String = "",
    val isConfigured: Boolean = false,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
    val error: AppError? = null,
)

class ApiTokenViewModel(
    private val repository: ApiTokenRepository = ServiceLocator.apiTokenRepository,
) : ViewModel() {
    private val _state = MutableStateFlow(
        ApiTokenState(isConfigured = repository.hasToken.value),
    )
    val state: StateFlow<ApiTokenState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.hasToken.collect { configured ->
                _state.update { it.copy(isConfigured = configured) }
            }
        }
    }

    fun onTokenChanged(value: String) {
        _state.update { it.copy(input = value, saved = false, error = null) }
    }

    fun save() {
        val current = _state.value
        if (current.isSaving) return
        _state.update { it.copy(isSaving = true, saved = false, error = null) }
        viewModelScope.launch {
            when (val result = repository.setToken(current.input)) {
                is AppResult.Success -> _state.update {
                    it.copy(input = "", isSaving = false, saved = true, error = null)
                }
                is AppResult.Failure -> _state.update {
                    it.copy(isSaving = false, saved = false, error = result.error)
                }
            }
        }
    }
}
