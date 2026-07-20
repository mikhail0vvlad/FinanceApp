package ru.shmr.finance.domain.model

sealed interface AppError {
    data object NoInternet : AppError
    data object Unauthorized : AppError
    data class Server(val code: Int) : AppError
    data object Unknown : AppError
}
