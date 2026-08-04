package ru.shmr.finance.data.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.security.KeyStore
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.shmr.finance.domain.model.AppCurrency
import ru.shmr.finance.domain.model.AppLanguage
import ru.shmr.finance.domain.model.AppSettings
import ru.shmr.finance.domain.model.SecuritySettings
import ru.shmr.finance.domain.model.ThemeMode
import ru.shmr.finance.domain.model.PinVerification
import ru.shmr.finance.domain.repository.SettingsRepository

class EncryptedPreferencesPinCredentialStorageTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()

    @Test
    fun pinVerifierPersistsOnlyInsideEncryptedSharedPreferences() = runBlocking {
        val fixture = StorageFixture(context)
        try {
            val repository = fixture.repository()

            assertNull(repository.setPin("1234"))
            val storedCiphertext = fixture.storage.encrypted.first()
            assertEquals(PinVerification.Match, repository.verifyPin("1234"))

            val reopened = fixture.reopenRepository()
            assertEquals(PinVerification.Match, reopened.verifyPin("1234"))

            val xml = fixture.preferencesXml().readText()
            assertFalse(xml.contains("1234"))
            assertFalse(xml.contains(EncryptedPreferencesPinCredentialStorage.VERIFIER_KEY))
            assertFalse(xml.contains(storedCiphertext.orEmpty()))
        } finally {
            fixture.cleanup()
        }
    }

    @Test
    fun readableDataStoreCredentialMigratesWithoutChangingThePin() = runBlocking {
        val fixture = StorageFixture(context)
        try {
            val encoded = PinVerifierCodec.encode(PinVerifier.create("2468"))
            val legacyCiphertext = fixture.pinCipher.encrypt(encoded)!!
            fixture.legacy.write(legacyCiphertext)

            assertEquals(legacyCiphertext, fixture.storage.encrypted.first())
            assertNull(fixture.legacy.encrypted.first())
            assertEquals(PinVerification.Match, fixture.repository().verifyPin("2468"))
        } finally {
            fixture.cleanup()
        }
    }

    @Test
    fun unreadableLegacyCredentialIsClearedInsteadOfLockingForever() = runBlocking {
        val legacy = MutableCredentialStorage().apply { write("unreadable") }
        val brokenCipher = BrokenCipher()
        val fileName = "pin-recovery-${UUID.randomUUID()}"
        val masterAlias = "ru.shmr.finance.test.master.${UUID.randomUUID()}"
        val storage = EncryptedPreferencesPinCredentialStorage(
            context = context,
            legacyStorage = legacy,
            legacyCipher = brokenCipher,
            preferenceFileName = fileName,
            masterKeyAlias = masterAlias,
        )
        try {
            assertNull(storage.encrypted.first())
            assertNull(legacy.encrypted.first())
            assertTrue(brokenCipher.wasReset)
        } finally {
            context.deleteSharedPreferences(fileName)
            deleteKey(masterAlias)
        }
    }
}

private class StorageFixture(private val context: Context) {
    private val fileName = "pin-test-${UUID.randomUUID()}"
    private val masterAlias = "ru.shmr.finance.test.master.${UUID.randomUUID()}"
    private val pinAlias = "ru.shmr.finance.test.pin.${UUID.randomUUID()}"
    val legacy = MutableCredentialStorage()
    val pinCipher = KeystoreCipher(pinAlias)
    val storage = newStorage()
    private val settings = TestSettingsRepository()

    fun repository() = SecurityRepositoryImpl(storage, pinCipher, settings)

    fun reopenRepository() = SecurityRepositoryImpl(newStorage(), pinCipher, settings)

    fun preferencesXml(): File = File(
        context.applicationInfo.dataDir,
        "shared_prefs/$fileName.xml",
    )

    suspend fun cleanup() {
        storage.clear()
        pinCipher.reset()
        context.deleteSharedPreferences(fileName)
        deleteKey(masterAlias)
        deleteKey(pinAlias)
    }

    private fun newStorage() = EncryptedPreferencesPinCredentialStorage(
        context = context,
        legacyStorage = legacy,
        legacyCipher = pinCipher,
        preferenceFileName = fileName,
        masterKeyAlias = masterAlias,
    )
}

private class MutableCredentialStorage : PinCredentialStorage {
    private val value = MutableStateFlow<String?>(null)
    override val encrypted: Flow<String?> = value

    override suspend fun write(value: String): Boolean {
        this.value.value = value
        return true
    }

    override suspend fun clear() {
        value.value = null
    }
}

private class BrokenCipher : PinCipher {
    var wasReset = false
    override fun encrypt(plaintext: String): String? = null
    override fun decrypt(encoded: String): String? = null
    override fun reset() {
        wasReset = true
    }
}

private class TestSettingsRepository : SettingsRepository {
    private val value = MutableStateFlow(AppSettings())
    override val settings: Flow<AppSettings> = value

    override suspend fun setThemeMode(mode: ThemeMode) = update { copy(themeMode = mode) }
    override suspend fun setLanguage(language: AppLanguage) = update { copy(language = language) }
    override suspend fun setCurrency(currency: AppCurrency) = update { copy(currency = currency) }
    override suspend fun setPinEnabled(enabled: Boolean) = updateSecurity {
        copy(isPinEnabled = enabled)
    }
    override suspend fun setBiometricsEnabled(enabled: Boolean) = updateSecurity {
        copy(isBiometricsEnabled = enabled)
    }

    private fun update(transform: AppSettings.() -> AppSettings) {
        value.value = value.value.transform()
    }

    private fun updateSecurity(transform: SecuritySettings.() -> SecuritySettings) {
        value.value = value.value.copy(security = value.value.security.transform())
    }
}

private fun deleteKey(alias: String) {
    runCatching {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
    }
}
