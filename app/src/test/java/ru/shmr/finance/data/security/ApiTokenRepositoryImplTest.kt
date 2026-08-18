package ru.shmr.finance.data.security

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.shmr.finance.core.result.AppResult
import ru.shmr.finance.domain.model.AppError
import ru.shmr.finance.domain.model.ValidationIssue

class ApiTokenRepositoryImplTest {

    @Test
    fun `stored token is decrypted and exposed only through currentToken`() {
        val repository = EncryptedApiTokenRepository(
            storage = FakeStorage("encrypted:stored"),
            cipher = FakeCipher(),
            initialToken = "debug",
        )

        assertEquals("stored", repository.currentToken())
        assertTrue(repository.hasToken.value)
    }

    @Test
    fun `saving trims encrypts and announces token change`() = runTest {
        val storage = FakeStorage()
        var changes = 0
        val repository = EncryptedApiTokenRepository(
            storage = storage,
            cipher = FakeCipher(),
            initialToken = "",
            onTokenChanged = { changes++ },
        )

        assertTrue(repository.setToken("  secret  ") is AppResult.Success)
        assertEquals("encrypted:secret", storage.value)
        assertEquals("secret", repository.currentToken())
        assertTrue(repository.hasToken.value)
        assertEquals(1, changes)
    }

    @Test
    fun `blank token is rejected without overwriting configured token`() = runTest {
        val repository = EncryptedApiTokenRepository(
            storage = FakeStorage(),
            cipher = FakeCipher(),
            initialToken = "debug",
        )

        assertEquals(
            AppResult.Failure(AppError.Validation(ValidationIssue.API_TOKEN_REQUIRED)),
            repository.setToken("   "),
        )
        assertEquals("debug", repository.currentToken())
    }

    @Test
    fun `storage failure does not expose unsaved token`() = runTest {
        val repository = EncryptedApiTokenRepository(
            storage = FakeStorage(writeSucceeds = false),
            cipher = FakeCipher(),
            initialToken = "",
        )

        assertEquals(AppResult.Failure(AppError.Storage), repository.setToken("secret"))
        assertEquals("", repository.currentToken())
        assertFalse(repository.hasToken.value)
    }

    private class FakeStorage(
        var value: String? = null,
        private val writeSucceeds: Boolean = true,
    ) : ApiTokenStorage {
        override fun read(): String? = value

        override fun write(value: String): Boolean {
            if (writeSucceeds) this.value = value
            return writeSucceeds
        }
    }

    private class FakeCipher : PinCipher {
        override fun encrypt(plaintext: String): String = "encrypted:$plaintext"

        override fun decrypt(encoded: String): String? =
            encoded.removePrefix("encrypted:").takeIf { encoded.startsWith("encrypted:") }

        override fun reset() = Unit
    }
}
