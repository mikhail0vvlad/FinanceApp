package ru.shmr.finance.data.security

import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.shmr.finance.core.dispatchers.DispatcherProvider
import ru.shmr.finance.data.settings.FakeSettingsRepository
import ru.shmr.finance.domain.model.AppSettings
import ru.shmr.finance.domain.model.PinStorageError
import ru.shmr.finance.domain.model.PinVerification
import ru.shmr.finance.domain.model.SecuritySettings

@OptIn(ExperimentalCoroutinesApi::class)
class SecurityRepositoryImplTest {

    @Test
    fun `pin is stored encrypted and never in clear text`() = runTest {
        val storage = InMemoryCredentialStorage()
        val repository = repository(storage)

        assertNull(repository.setPin("1234"))

        val stored = storage.encrypted.first()
        assertTrue(stored != null && stored.isNotEmpty())
        assertFalse(stored!!.contains("1234"))
    }

    @Test
    fun `correct pin verifies and wrong pin does not`() = runTest {
        val repository = repository()

        repository.setPin("1234")

        assertEquals(PinVerification.Match, repository.verifyPin("1234"))
        assertEquals(PinVerification.Mismatch, repository.verifyPin("4321"))
    }

    @Test
    fun `setting a pin marks security as enabled`() = runTest {
        val repository = repository()

        repository.setPin("1234")

        assertTrue(repository.state.first().isPinSet)
    }

    @Test
    fun `verification without a stored pin reports an unreadable credential`() = runTest {
        val repository = repository()

        assertEquals(
            PinVerification.Unavailable(PinStorageError.CREDENTIAL_UNREADABLE),
            repository.verifyPin("1234"),
        )
    }

    @Test
    fun `an invalidated keystore key surfaces as a device error, not a wrong pin`() = runTest {
        val cipher = FakeCipher()
        val repository = repository(cipher = cipher)
        repository.setPin("1234")

        cipher.isKeyValid = false

        assertEquals(
            PinVerification.Unavailable(PinStorageError.CREDENTIAL_UNREADABLE),
            repository.verifyPin("1234"),
        )
    }

    @Test
    fun `a corrupted verifier surfaces as a device error`() = runTest {
        val storage = InMemoryCredentialStorage()
        val repository = repository(storage)
        repository.setPin("1234")
        storage.write("не запись верификатора")

        assertEquals(
            PinVerification.Unavailable(PinStorageError.CREDENTIAL_UNREADABLE),
            repository.verifyPin("1234"),
        )
    }

    @Test
    fun `unavailable keystore blocks writing a pin`() = runTest {
        val repository = repository(cipher = FakeCipher(canEncrypt = false))

        assertEquals(PinStorageError.DEVICE_STORAGE_UNAVAILABLE, repository.setPin("1234"))
        assertFalse(repository.state.first().isPinSet)
    }

    @Test
    fun `unwritable storage blocks writing a pin`() = runTest {
        val repository = repository(InMemoryCredentialStorage(canWrite = false))

        assertEquals(PinStorageError.DEVICE_STORAGE_UNAVAILABLE, repository.setPin("1234"))
        assertFalse(repository.state.first().isPinSet)
    }

    @Test
    fun `clearing the pin also turns biometrics off`() = runTest {
        val settings = FakeSettingsRepository(
            AppSettings(security = SecuritySettings(isBiometricsEnabled = true)),
        )
        val repository = repository(settingsRepository = settings)
        repository.setPin("1234")

        repository.clearPin()

        val state = repository.state.first()
        assertFalse(state.isPinSet)
        assertFalse(state.isBiometricsEnabled)
        assertFalse(settings.settings.first().security.isBiometricsEnabled)
    }

    @Test
    fun `biometrics stay off while no pin is configured`() = runTest {
        val repository = repository()

        repository.setBiometricsEnabled(true)

        // Флаг записан, но без ПИН-кода биометрия не считается включённой:
        // иначе сломанный датчик отрезал бы единственный вход.
        assertFalse(repository.state.first().isBiometricsEnabled)
    }

    @Test
    fun `biometrics become active once a pin exists`() = runTest {
        val repository = repository()
        repository.setPin("1234")

        repository.setBiometricsEnabled(true)

        assertTrue(repository.state.first().isBiometricsEnabled)
    }

    private fun repository(
        storage: PinCredentialStorage = InMemoryCredentialStorage(),
        cipher: PinCipher = FakeCipher(),
        settingsRepository: FakeSettingsRepository = FakeSettingsRepository(),
    ) = SecurityRepositoryImpl(
        credentialStorage = storage,
        cipher = cipher,
        settingsRepository = settingsRepository,
        dispatchers = TestDispatcherProvider(),
    )
}

@OptIn(ExperimentalCoroutinesApi::class)
private class TestDispatcherProvider(
    dispatcher: CoroutineDispatcher = UnconfinedTestDispatcher(),
) : DispatcherProvider {
    override val io: CoroutineDispatcher = dispatcher
    override val default: CoroutineDispatcher = dispatcher
    override val main: CoroutineDispatcher = dispatcher
}

private class InMemoryCredentialStorage(
    private val canWrite: Boolean = true,
) : PinCredentialStorage {
    private val state = MutableStateFlow<String?>(null)

    override val encrypted: Flow<String?> = state

    override suspend fun write(value: String): Boolean {
        if (!canWrite) return false
        state.value = value
        return true
    }

    override suspend fun clear() {
        state.value = null
    }
}

/**
 * Подставная замена Android Keystore: тот же контракт «шифрую, пока ключ жив, и возвращаю
 * null, когда он потерян», но без TEE, которого нет на JVM.
 */
private class FakeCipher(
    private val canEncrypt: Boolean = true,
) : PinCipher {
    var isKeyValid: Boolean = true

    override fun encrypt(plaintext: String): String? =
        if (canEncrypt) PREFIX + plaintext.reversed() else null

    override fun decrypt(encoded: String): String? = when {
        !isKeyValid -> null
        !encoded.startsWith(PREFIX) -> null
        else -> encoded.removePrefix(PREFIX).reversed()
    }

    override fun reset() {
        isKeyValid = true
    }

    private companion object {
        const val PREFIX = "enc:"
    }
}
