package ru.shmr.finance.data.local

import java.math.BigDecimal
import org.junit.Assert.assertEquals
import org.junit.Test

class AccountSyncBalanceTest {

    @Test
    fun `name only edit preserves pending sync balance`() {
        val result = resolveSyncBalance(
            existingBalance = BigDecimal("900"),
            existingSyncBalance = BigDecimal("1000"),
            newBalance = BigDecimal("900"),
        )

        assertEquals(BigDecimal("1000"), result)
    }

    @Test
    fun `manual balance edit applies as delta on top of pending difference`() {
        val result = resolveSyncBalance(
            existingBalance = BigDecimal("900"),
            existingSyncBalance = BigDecimal("1000"),
            newBalance = BigDecimal("1200"),
        )

        assertEquals(BigDecimal("1300"), result)
    }

    @Test
    fun `without pending difference sync balance follows new balance`() {
        val result = resolveSyncBalance(
            existingBalance = BigDecimal("1000"),
            existingSyncBalance = BigDecimal("1000"),
            newBalance = BigDecimal("1200"),
        )

        assertEquals(BigDecimal("1200"), result)
    }

    @Test
    fun `new account with no existing balance uses new balance directly`() {
        val result = resolveSyncBalance(
            existingBalance = BigDecimal.ZERO,
            existingSyncBalance = BigDecimal.ZERO,
            newBalance = BigDecimal("1000"),
        )

        assertEquals(BigDecimal("1000"), result)
    }
}
