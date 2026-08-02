package ru.shmr.finance.data.security

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map

/**
 * Шифрование проверочной записи ПИН-кода. Вынесено за интерфейс, потому что настоящая
 * реализация живёт в Android Keystore и на JVM недоступна.
 */
internal interface PinCipher {
    /** @return `null`, если шифрование на этом устройстве невозможно. */
    fun encrypt(plaintext: String): String?

    /** @return `null`, если запись повреждена или ключ стал недействительным. */
    fun decrypt(encoded: String): String?

    /** Удаляет ключ: вызывается при снятии защиты. */
    fun reset()
}

/** Хранилище зашифрованной проверочной записи. Открытый ПИН-код сюда не попадает. */
internal interface PinCredentialStorage {
    val encrypted: Flow<String?>

    /** @return `false`, если хранилище устройства недоступно. */
    suspend fun write(value: String): Boolean

    suspend fun clear()
}

private const val SECURITY_DATA_STORE_NAME = "security"

private val Context.securityDataStore by preferencesDataStore(name = SECURITY_DATA_STORE_NAME)

/**
 * Секретный материал держится в отдельном от пользовательских настроек файле, чтобы
 * очистка или миграция настроек не задевала верификатор и наоборот.
 */
internal class DataStorePinCredentialStorage(
    private val dataStore: DataStore<Preferences>,
) : PinCredentialStorage {

    constructor(context: Context) : this(context.securityDataStore)

    override val encrypted: Flow<String?> = dataStore.data
        .catch { error ->
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { it[VERIFIER_KEY] }

    override suspend fun write(value: String): Boolean = try {
        dataStore.edit { it[VERIFIER_KEY] = value }
        true
    } catch (error: IOException) {
        false
    }

    override suspend fun clear() {
        try {
            dataStore.edit { it.remove(VERIFIER_KEY) }
        } catch (error: IOException) {
            // Верификатор останется на диске, но ключ Keystore уже удалён — расшифровать
            // запись всё равно нечем, и защита фактически снята.
        }
    }

    private companion object {
        val VERIFIER_KEY = stringPreferencesKey("pin_verifier")
    }
}
