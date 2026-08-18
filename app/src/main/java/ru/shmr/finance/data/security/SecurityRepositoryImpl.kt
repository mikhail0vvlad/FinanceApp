package ru.shmr.finance.data.security

import java.security.GeneralSecurityException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import ru.shmr.finance.core.dispatchers.DefaultDispatcherProvider
import ru.shmr.finance.core.dispatchers.DispatcherProvider
import ru.shmr.finance.domain.model.PinStorageError
import ru.shmr.finance.domain.model.PinVerification
import ru.shmr.finance.domain.model.SecurityState
import ru.shmr.finance.domain.repository.SecurityRepository
import ru.shmr.finance.domain.repository.SettingsRepository

/**
 * Склеивает зашифрованный верификатор и обычные настройки.
 *
 * Признак «ПИН-код задан» берётся из хранилища верификатора, а не из
 * [ru.shmr.finance.domain.model.SecuritySettings]: флаг в настройках — только зеркало для
 * экрана настроек, и он не должен решать, показывать ли экран блокировки.
 */
internal class SecurityRepositoryImpl(
    private val credentialStorage: PinCredentialStorage,
    private val cipher: PinCipher,
    private val settingsRepository: SettingsRepository,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider,
) : SecurityRepository {

    override val state: Flow<SecurityState> = combine(
        credentialStorage.encrypted,
        settingsRepository.settings,
    ) { verifier, settings ->
        val isPinSet = verifier != null
        SecurityState(
            isPinSet = isPinSet,
            // Биометрия без запасного ПИН-кода оставила бы пользователя без входа,
            // если датчик сломается, поэтому она подчинена ПИН-коду.
            isBiometricsEnabled = isPinSet && settings.security.isBiometricsEnabled,
        )
    }

    override suspend fun setPin(pin: String): PinStorageError? {
        val encrypted = withContext(dispatchers.default) {
            try {
                cipher.encrypt(PinVerifierCodec.encode(PinVerifier.create(pin)))
            } catch (error: GeneralSecurityException) {
                null
            }
        } ?: return PinStorageError.DEVICE_STORAGE_UNAVAILABLE

        if (!credentialStorage.write(encrypted)) {
            return PinStorageError.DEVICE_STORAGE_UNAVAILABLE
        }
        settingsRepository.setPinEnabled(true)
        return null
    }

    override suspend fun verifyPin(pin: String): PinVerification {
        val encrypted = credentialStorage.encrypted.first()
            ?: return PinVerification.Unavailable(PinStorageError.CREDENTIAL_UNREADABLE)

        return withContext(dispatchers.default) {
            val record = cipher.decrypt(encrypted)?.let(PinVerifierCodec::decode)
                ?: return@withContext PinVerification.Unavailable(
                    PinStorageError.CREDENTIAL_UNREADABLE,
                )
            try {
                if (PinVerifier.matches(record, pin)) {
                    PinVerification.Match
                } else {
                    PinVerification.Mismatch
                }
            } catch (error: GeneralSecurityException) {
                // Запись создана алгоритмом, которого на этом устройстве больше нет:
                // проверить нечем, но и объявлять ПИН неверным нельзя.
                PinVerification.Unavailable(PinStorageError.CREDENTIAL_UNREADABLE)
            }
        }
    }

    override suspend fun clearPin() {
        credentialStorage.clear()
        cipher.reset()
        settingsRepository.setBiometricsEnabled(false)
        settingsRepository.setPinEnabled(false)
    }

    override suspend fun setBiometricsEnabled(enabled: Boolean) {
        settingsRepository.setBiometricsEnabled(enabled)
    }
}
