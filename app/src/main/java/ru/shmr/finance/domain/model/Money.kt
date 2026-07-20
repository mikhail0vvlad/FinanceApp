package ru.shmr.finance.domain.model

import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

enum class Currency(val code: String, val symbol: String) {
    RUB("RUB", "₽"),
    USD("USD", "$"),
    EUR("EUR", "€"),
    ;

    companion object {
        fun fromCode(code: String): Currency = entries.find { it.code == code } ?: RUB
    }
}

data class Money(
    val amount: BigDecimal,
    val currency: Currency = Currency.RUB,
) {
    operator fun plus(other: Money): Money = copy(amount = amount + other.amount)

    fun formatted(): String = "${amountFormat.format(amount)} ${currency.symbol}"

    companion object {
        val ZERO = Money(BigDecimal.ZERO)

        private val amountFormat = DecimalFormat(
            "#,##0.##",
            DecimalFormatSymbols(Locale.ROOT).apply {
                groupingSeparator = ' '
                decimalSeparator = ','
            },
        )

        fun parse(raw: String, currencyCode: String = "RUB"): Money =
            Money(BigDecimal(raw), Currency.fromCode(currencyCode))
    }
}
