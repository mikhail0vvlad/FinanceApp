package ru.shmr.finance.core.state

import ru.shmr.finance.domain.model.AppError

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Content<T>(val data: T) : UiState<T>
    data object Empty : UiState<Nothing>
    data class Error(val error: AppError) : UiState<Nothing>
}
