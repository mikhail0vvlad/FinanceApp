package ru.shmr.finance.data.security

/**
 * Текстовое представление [PinVerifierRecord] — то, что попадает под шифрование Keystore.
 *
 * Байты кодируются шестнадцатеричной строкой, а не Base64: `android.util.Base64` недоступен
 * в JVM-тестах, а `java.util.Base64` появился только в API 26 при `minSdk = 24`.
 */
internal object PinVerifierCodec {

    private const val VERSION = "1"
    private const val SEPARATOR = '|'
    private const val FIELD_COUNT = 5

    fun encode(record: PinVerifierRecord): String = listOf(
        VERSION,
        record.algorithm,
        record.iterations.toString(),
        record.salt.toHex(),
        record.hash.toHex(),
    ).joinToString(SEPARATOR.toString())

    /** @return `null`, если строка повреждена или записана несовместимой версией. */
    fun decode(encoded: String): PinVerifierRecord? {
        val parts = encoded.split(SEPARATOR)
        if (parts.size != FIELD_COUNT || parts[0] != VERSION) return null
        val iterations = parts[2].toIntOrNull() ?: return null
        if (iterations <= 0 || parts[1].isEmpty()) return null
        val salt = parts[3].hexToBytesOrNull() ?: return null
        val hash = parts[4].hexToBytesOrNull() ?: return null
        if (salt.isEmpty() || hash.isEmpty()) return null
        return PinVerifierRecord(
            algorithm = parts[1],
            iterations = iterations,
            salt = salt,
            hash = hash,
        )
    }

    fun ByteArray.toHex(): String {
        val builder = StringBuilder(size * 2)
        forEach { byte ->
            val value = byte.toInt() and 0xFF
            builder.append(HEX_DIGITS[value ushr 4])
            builder.append(HEX_DIGITS[value and 0x0F])
        }
        return builder.toString()
    }

    fun String.hexToBytesOrNull(): ByteArray? {
        if (length % 2 != 0) return null
        val bytes = ByteArray(length / 2)
        for (index in bytes.indices) {
            val high = HEX_DIGITS.indexOf(this[index * 2].lowercaseChar())
            val low = HEX_DIGITS.indexOf(this[index * 2 + 1].lowercaseChar())
            if (high < 0 || low < 0) return null
            bytes[index] = ((high shl 4) or low).toByte()
        }
        return bytes
    }

    private const val HEX_DIGITS = "0123456789abcdef"
}
