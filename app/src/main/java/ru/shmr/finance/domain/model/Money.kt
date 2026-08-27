package ru.shmr.finance.domain.model

import java.math.BigDecimal
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

enum class Currency(val code: String, val symbol: String) {
    RUB("RUB", "₽"),
    USD("USD", "$"),
    EUR("EUR", "€"),
    GBP("GBP", "£"),
    CNY("CNY", "¥"),
    ;

    companion object {
        fun fromCode(code: String): Currency = entries.find { it.code == code } ?: RUB
    }
}

data class Money(
    val amount: BigDecimal,
    val currency: Currency = Currency.RUB,
) {
    operator fun plus(other: Money): Money {
        require(currency == other.currency) {
            "Cannot add ${currency.code} and ${other.currency.code} without an exchange rate"
        }
        return copy(amount = amount + other.amount)
    }

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

data class MoneyTotals(
    val amounts: List<Money>,
) {
    fun formatted(): String = amounts.joinToString(separator = " · ", transform = Money::formatted)

    companion object {
        fun of(
            values: Iterable<Money>,
            preferredCurrency: Currency = Currency.RUB,
        ): MoneyTotals {
            val totals = values
                .groupBy(Money::currency)
                .mapValues { (currency, amounts) ->
                    amounts.fold(Money.ZERO.copy(currency = currency), Money::plus)
                }
            val order = listOf(preferredCurrency, Currency.RUB, Currency.USD, Currency.EUR)
                .distinct()
                .withIndex()
                .associate { (index, currency) -> currency to index }
            return MoneyTotals(
                totals.values.sortedWith(
                    compareBy<Money> { order[it.currency] ?: Int.MAX_VALUE }
                        .thenBy { it.currency.code },
                ),
            )
        }
    }
}
