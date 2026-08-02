package ru.shmr.finance.data.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.GeneralSecurityException
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import ru.shmr.finance.data.security.PinVerifierCodec.hexToBytesOrNull
import ru.shmr.finance.data.security.PinVerifierCodec.toHex

/**
 * Обёртка над Android Keystore: AES-256/GCM, ключ не покидает TEE и не экспортируется.
 *
 * Приложение шифрует этим ключом проверочную запись ПИН-кода, поэтому вытащить файл
 * настроек с устройства и перебрать четыре цифры офлайн нельзя — без ключа запись
 * бесполезна.
 */
internal class KeystoreCipher(
    private val keyAlias: String = DEFAULT_KEY_ALIAS,
) : PinCipher {

    /**
     * @return `null`, если Keystore на устройстве недоступен. Вызывающий код обязан
     * отработать это как ошибку устройства, а не как неверный ПИН-код.
     */
    override fun encrypt(plaintext: String): String? = runCatching {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, loadOrCreateKey())
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        "${cipher.iv.toHex()}$SEPARATOR${ciphertext.toHex()}"
    }.getOrElse { error ->
        if (error is GeneralSecurityException || error is IllegalStateException) null else throw error
    }

    /**
     * @return `null`, если запись повреждена или ключ Keystore стал недействительным
     * (`KeyPermanentlyInvalidatedException`, сброс блокировки экрана, переустановка).
     * Отличить эти случаи друг от друга нельзя, и оба означают одно: ПИН-код придётся
     * задать заново.
     */
    override fun decrypt(encoded: String): String? = runCatching {
        val parts = encoded.split(SEPARATOR)
        if (parts.size != 2) return null
        val iv = parts[0].hexToBytesOrNull() ?: return null
        val ciphertext = parts[1].hexToBytesOrNull() ?: return null
        if (iv.size != GCM_IV_BYTES) return null
        val key = existingKey() ?: return null
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }.getOrElse { error ->
        if (error is GeneralSecurityException || error is IllegalStateException) null else throw error
    }

    override fun reset() {
        runCatching { keyStore()?.deleteEntry(keyAlias) }
    }

    private fun keyStore(): KeyStore? = runCatching {
        KeyStore.getInstance(ANDROID_KEY_STORE).apply { load(null) }
    }.getOrNull()

    private fun existingKey(): SecretKey? =
        keyStore()?.getKey(keyAlias, null) as? SecretKey

    private fun loadOrCreateKey(): SecretKey = existingKey() ?: generateKey()

    private fun generateKey(): SecretKey {
        val generator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEY_STORE,
        )
        generator.init(
            KeyGenParameterSpec.Builder(
                keyAlias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(AES_KEY_BITS)
                // Ключ не привязан к биометрии: иначе выключение или сброс отпечатка
                // отрезало бы и запасной вход по ПИН-коду.
                .setUserAuthenticationRequired(false)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val DEFAULT_KEY_ALIAS = "ru.shmr.finance.pin"
        const val ANDROID_KEY_STORE = "AndroidKeyStore"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val AES_KEY_BITS = 256
        const val GCM_TAG_BITS = 128
        const val GCM_IV_BYTES = 12
        const val SEPARATOR = ':'
    }
}
