package ru.shmr.finance.domain.repository

import kotlinx.coroutines.flow.Flow
import ru.shmr.finance.domain.model.PinStorageError
import ru.shmr.finance.domain.model.PinVerification
import ru.shmr.finance.domain.model.SecurityState

interface SecurityRepository {
    val state: Flow<SecurityState>

    /** Сохраняет новый ПИН-код. Возвращает `null`, если запись удалась. */
    suspend fun setPin(pin: String): PinStorageError?

    suspend fun verifyPin(pin: String): PinVerification

    /** Снимает защиту: удаляет верификатор и выключает биометрию. */
    suspend fun clearPin()

    suspend fun setBiometricsEnabled(enabled: Boolean)
}
