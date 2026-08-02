package ru.shmr.finance.domain.validation

import java.math.BigDecimal

object MoneyInputParser {

    fun parse(raw: String): BigDecimal? = raw
        .trim()
        .replace(" ", "")
        .replace('\u00A0'.toString(), "")
        .replace(',', '.')
        .toBigDecimalOrNull()
}
