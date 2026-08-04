package ru.shmr.finance.data.security

import android.content.Context
import android.annotation.SuppressLint
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import ru.shmr.finance.core.dispatchers.DefaultDispatcherProvider
import ru.shmr.finance.core.dispatchers.DispatcherProvider
import ru.shmr.finance.core.result.AppResult
import ru.shmr.finance.domain.model.AppError
import ru.shmr.finance.domain.model.ValidationIssue
import ru.shmr.finance.domain.repository.ApiTokenRepository

internal interface ApiTokenStorage {
    fun read(): String?
    fun write(value: String): Boolean
}

internal class SharedPreferencesApiTokenStorage(context: Context) : ApiTokenStorage {
    private val preferences = context.getSharedPreferences(FILE_NAME, Context.MODE_PRIVATE)

    override fun read(): String? = preferences.getString(TOKEN_KEY, null)

    @SuppressLint("UseKtx")
    override fun write(value: String): Boolean =
        preferences.edit().putString(TOKEN_KEY, value).commit()

    private companion object {
        const val FILE_NAME = "api_credentials"
        const val TOKEN_KEY = "encrypted_api_token"
    }
}

internal class EncryptedApiTokenRepository(
    private val storage: ApiTokenStorage,
    private val cipher: PinCipher,
    initialToken: String,
    private val onTokenChanged: () -> Unit = {},
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider,
) : ApiTokenRepository {

    constructor(
        context: Context,
        initialToken: String,
        onTokenChanged: () -> Unit = {},
        dispatchers: DispatcherProvider = DefaultDispatcherProvider,
    ) : this(
        storage = SharedPreferencesApiTokenStorage(context),
        cipher = KeystoreCipher(keyAlias = API_TOKEN_KEY_ALIAS),
        initialToken = initialToken,
        onTokenChanged = onTokenChanged,
        dispatchers = dispatchers,
    )

    private val token = AtomicReference(
        storage.read()?.let(cipher::decrypt).orEmpty().ifBlank { initialToken.trim() },
    )
    private val _hasToken = MutableStateFlow(token.get().isNotEmpty())
    override val hasToken: StateFlow<Boolean> = _hasToken

    override fun currentToken(): String = token.get()

    override suspend fun setToken(token: String): AppResult<Unit> {
        val normalized = token.trim()
        if (normalized.isEmpty()) {
            return AppResult.Failure(AppError.Validation(ValidationIssue.API_TOKEN_REQUIRED))
        }
        return withContext(dispatchers.io) {
            val encrypted = cipher.encrypt(normalized)
                ?: return@withContext AppResult.Failure(AppError.Storage)
            if (!storage.write(encrypted)) {
                return@withContext AppResult.Failure(AppError.Storage)
            }
            this@EncryptedApiTokenRepository.token.set(normalized)
            _hasToken.value = true
            onTokenChanged()
            AppResult.Success(Unit)
        }
    }

    private companion object {
        const val API_TOKEN_KEY_ALIAS = "ru.shmr.finance.api_token"
    }
}
