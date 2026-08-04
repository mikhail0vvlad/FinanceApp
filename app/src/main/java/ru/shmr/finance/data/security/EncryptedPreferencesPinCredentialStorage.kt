// HW4 explicitly requires this AndroidX API. Keeping the suppress on this dedicated adapter makes
// the deprecated dependency visible and prevents it from leaking into the rest of the app.
@file:Suppress("DEPRECATION")

package ru.shmr.finance.data.security

import android.content.Context
import android.content.SharedPreferences
import android.annotation.SuppressLint
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import ru.shmr.finance.core.dispatchers.DefaultDispatcherProvider
import ru.shmr.finance.core.dispatchers.DispatcherProvider

/**
 * Stores only the encrypted PBKDF2 record in AndroidX [EncryptedSharedPreferences].
 *
 * The extra [PinCipher] layer is intentionally retained: it keeps the existing Keystore-backed
 * credential readable during migration and means neither the PIN nor a directly brute-forceable
 * salt/verifier record appears in the preferences API or its encrypted XML file.
 */
@SuppressLint("UseKtx") // The commit() result is the storage-failure signal required by the repository.
internal class EncryptedPreferencesPinCredentialStorage(
    context: Context,
    private val legacyStorage: PinCredentialStorage,
    private val legacyCipher: PinCipher,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider,
    private val preferenceFileName: String = PREFERENCE_FILE_NAME,
    private val masterKeyAlias: String = MASTER_KEY_ALIAS,
) : PinCredentialStorage {

    private val appContext = context.applicationContext
    private val loadMutex = Mutex()
    private val state = MutableStateFlow<String?>(null)
    private var loaded = false

    override val encrypted: Flow<String?> = flow {
        ensureLoaded()
        emitAll(state)
    }.distinctUntilChanged()

    override suspend fun write(value: String): Boolean {
        ensureLoaded()
        val written = withContext(dispatchers.io) {
            encryptedPreferencesOrNull()
                ?.let { preferences ->
                    runCatching {
                        preferences.edit().putString(VERIFIER_KEY, value).commit()
                    }.getOrDefault(false)
                }
                ?: false
        }
        if (written) state.value = value
        return written
    }

    override suspend fun clear() {
        ensureLoaded()
        withContext(dispatchers.io) {
            encryptedPreferencesOrNull()?.let { preferences ->
                runCatching { preferences.edit().remove(VERIFIER_KEY).commit() }
            }
        }
        legacyStorage.clear()
        state.value = null
    }

    private suspend fun ensureLoaded() {
        loadMutex.withLock {
            if (loaded) return
            state.value = loadCurrentOrMigrateLegacy()
            loaded = true
        }
    }

    private suspend fun loadCurrentOrMigrateLegacy(): String? {
        val preferences = withContext(dispatchers.io) { encryptedPreferencesOrNull() }
            ?: return null
        val current = withContext(dispatchers.io) {
            runCatching { preferences.getString(VERIFIER_KEY, null) }.getOrNull()
        }
        if (current != null) {
            if (isReadableVerifier(current)) return current
            withContext(dispatchers.io) {
                runCatching { preferences.edit().remove(VERIFIER_KEY).commit() }
            }
            legacyStorage.clear()
            legacyCipher.reset()
            return null
        }

        val legacy = legacyStorage.encrypted.first() ?: return null
        val legacyIsReadable = isReadableVerifier(legacy)
        if (!legacyIsReadable) {
            // A lost Keystore key must never turn an upgraded app into an endless lock screen.
            legacyStorage.clear()
            legacyCipher.reset()
            return null
        }

        val migrated = withContext(dispatchers.io) {
            runCatching {
                preferences.edit().putString(VERIFIER_KEY, legacy).commit()
            }.getOrDefault(false)
        }
        if (!migrated) return null

        legacyStorage.clear()
        return legacy
    }

    private suspend fun isReadableVerifier(value: String): Boolean =
        withContext(dispatchers.default) {
            legacyCipher.decrypt(value)
                ?.let(PinVerifierCodec::decode) != null
        }

    /**
     * HW4 names EncryptedSharedPreferences literally. AndroidX deprecated the API in 1.1.0 in
     * favour of platform SharedPreferences plus direct Keystore use, but replacing it would fail
     * the assignment's explicit storage requirement. Keep this suppress local to that boundary.
     */
    private fun encryptedPreferencesOrNull(): SharedPreferences? = runCatching {
        val masterKey = MasterKey.Builder(appContext, masterKeyAlias)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
        EncryptedSharedPreferences.create(
            appContext,
            preferenceFileName,
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )
    }.getOrNull()

    internal companion object {
        const val PREFERENCE_FILE_NAME = "pin_credentials_encrypted"
        const val MASTER_KEY_ALIAS = "ru.shmr.finance.pin.preferences"
        const val VERIFIER_KEY = "pin_pbkdf2_verifier"
    }
}
