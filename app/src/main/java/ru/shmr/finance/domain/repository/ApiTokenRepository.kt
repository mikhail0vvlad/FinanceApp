package ru.shmr.finance.domain.repository

import kotlinx.coroutines.flow.StateFlow
import ru.shmr.finance.core.result.AppResult

interface ApiTokenRepository {
    val hasToken: StateFlow<Boolean>

    fun currentToken(): String

    suspend fun setToken(token: String): AppResult<Unit>
}
