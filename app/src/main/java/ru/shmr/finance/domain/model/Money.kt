package ru.shmr.finance.domain.model

enum class Currency(val symbol: String) {
    RUB("₽"),
    USD("$"),
    EUR("€"),
}

// Суммы храним в целых рублях — для моков копейки не нужны.
data class Money(
    val amount: Long,
    val currency: Currency = Currency.RUB,
) {
    // 323524 -> "323 524 ₽"
    fun formatted(): String {
        val digits = amount.toString()
        val sb = StringBuilder(digits.length + digits.length / 3 + 2)
        for (i in digits.indices) {
            if (i > 0 && digits[i - 1] != '-' && (digits.length - i) % 3 == 0) {
                sb.append(' ')
            }
            sb.append(digits[i])
        }
        return sb.append(' ').append(currency.symbol).toString()
    }
}
