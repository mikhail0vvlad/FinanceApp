package ru.shmr.finance.data.security

import java.security.NoSuchAlgorithmException
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Проверочный материал для ПИН-кода: соль и результат PBKDF2. Сам ПИН-код никогда не
 * сохраняется и не восстанавливается из этой записи.
 *
 * Четырёхзначный ПИН — это всего 10 000 вариантов, поэтому одна лишь KDF от офлайнового
 * перебора не спасает. Основная защита — шифрование записи ключом Android Keystore
 * (см. [KeystoreCipher]): без доступа к TEE-ключу перебирать нечего. PBKDF2 здесь —
 * второй эшелон на случай, если ключ Keystore всё-таки утечёт.
 */
internal data class PinVerifierRecord(
    val algorithm: String,
    val iterations: Int,
    val salt: ByteArray,
    val hash: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is PinVerifierRecord) return false
        return algorithm == other.algorithm &&
            iterations == other.iterations &&
            salt.contentEquals(other.salt) &&
            hash.contentEquals(other.hash)
    }

    override fun hashCode(): Int {
        var result = algorithm.hashCode()
        result = 31 * result + iterations
        result = 31 * result + salt.contentHashCode()
        result = 31 * result + hash.contentHashCode()
        return result
    }
}

internal object PinVerifier {

    /**
     * PBKDF2WithHmacSHA256 появился только в API 26, поэтому на API 24–25 нужен запасной
     * вариант. Использованный алгоритм пишется в запись, чтобы проверка после обновления
     * системы шла тем же способом, каким создавалась.
     */
    private val PREFERRED_ALGORITHMS = listOf(
        "PBKDF2WithHmacSHA256" to 120_000,
        "PBKDF2WithHmacSHA1" to 150_000,
    )

    private const val SALT_BYTES = 16
    private const val KEY_BITS = 256

    private val secureRandom = SecureRandom()

    /** @throws NoSuchAlgorithmException если устройство не умеет ни один из PBKDF2-вариантов. */
    fun create(pin: String): PinVerifierRecord {
        val (algorithm, iterations) = supportedAlgorithm()
        val salt = ByteArray(SALT_BYTES).also(secureRandom::nextBytes)
        return PinVerifierRecord(
            algorithm = algorithm,
            iterations = iterations,
            salt = salt,
            hash = derive(pin, algorithm, iterations, salt),
        )
    }

    /** Сравнение за постоянное время: длительность не должна зависеть от числа совпавших байт. */
    fun matches(record: PinVerifierRecord, pin: String): Boolean {
        val candidate = derive(pin, record.algorithm, record.iterations, record.salt)
        return constantTimeEquals(record.hash, candidate)
    }

    private fun supportedAlgorithm(): Pair<String, Int> {
        PREFERRED_ALGORITHMS.forEach { candidate ->
            runCatching { SecretKeyFactory.getInstance(candidate.first) }
                .onSuccess { return candidate }
        }
        throw NoSuchAlgorithmException("Устройство не поддерживает ни один вариант PBKDF2")
    }

    private fun derive(
        pin: String,
        algorithm: String,
        iterations: Int,
        salt: ByteArray,
    ): ByteArray {
        val spec = PBEKeySpec(pin.toCharArray(), salt, iterations, KEY_BITS)
        return try {
            SecretKeyFactory.getInstance(algorithm).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun constantTimeEquals(first: ByteArray, second: ByteArray): Boolean {
        if (first.size != second.size) return false
        var difference = 0
        for (index in first.indices) {
            difference = difference or (first[index].toInt() xor second[index].toInt())
        }
        return difference == 0
    }
}
