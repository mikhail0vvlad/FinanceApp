package ru.shmr.finance.domain.model

sealed interface AppError {
    data object NoInternet : AppError
    data object Unauthorized : AppError
    data class Server(val code: Int) : AppError
    data class Client(val code: Int) : AppError
    data class Validation(val issue: ValidationIssue) : AppError
    data object Storage : AppError
    data object Unknown : AppError
}

enum class ValidationIssue {
    ACCOUNT_NAME_REQUIRED,
    BALANCE_NEGATIVE,
    ACCOUNT_CURRENCY_LOCKED,
    TRANSACTION_INVALID,
    API_TOKEN_REQUIRED,
}
