package ru.shmr.finance.domain.model

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

class MoneyTest {

    @Test
    fun `integer amount groups thousands with spaces`() {
        assertEquals("1 000 ₽", Money(BigDecimal("1000.00")).formatted())
    }

    @Test
    fun `fractional amount uses comma and drops trailing zeros`() {
        assertEquals("1 234 567,89 $", Money(BigDecimal("1234567.89"), Currency.USD).formatted())
    }

    @Test
    fun `zero is formatted without fraction`() {
        assertEquals("0 ₽", Money(BigDecimal.ZERO).formatted())
    }

    @Test
    fun `negative keeps sign before grouped digits`() {
        assertEquals("-1 500,5 ₽", Money(BigDecimal("-1500.50")).formatted())
    }
}
