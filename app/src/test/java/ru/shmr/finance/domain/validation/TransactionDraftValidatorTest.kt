package ru.shmr.finance.domain.validation

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransactionDraftValidatorTest {

    @Test
    fun `valid draft normalizes comma decimal separator and whitespace`() {
        val result = TransactionDraftValidator.validate(
            TransactionDraft(
                accountId = 7,
                categoryId = 42,
                amount = " 1 234,50 ",
                date = LocalDate.of(2026, 7, 24),
                time = LocalTime.of(18, 30),
                comment = "  обед  ",
            ),
        )

        assertTrue(result.isValid)
        assertEquals(BigDecimal("1234.50"), result.normalizedAmount)
        assertEquals("обед", result.normalizedComment)
    }

    @Test
    fun `draft requires account category and positive amount`() {
        val result = TransactionDraftValidator.validate(
            TransactionDraft(
                accountId = null,
                categoryId = null,
                amount = "0",
                date = LocalDate.of(2026, 7, 24),
                time = LocalTime.NOON,
                comment = "",
            ),
        )

        assertFalse(result.isValid)
        assertEquals(
            setOf(
                TransactionField.ACCOUNT,
                TransactionField.CATEGORY,
                TransactionField.AMOUNT,
            ),
            result.errors.keys,
        )
    }

    @Test
    fun `draft rejects malformed and negative amount`() {
        listOf("not money", "-1", "1,2,3").forEach { amount ->
            val result = TransactionDraftValidator.validate(
                TransactionDraft(
                    accountId = 1,
                    categoryId = 2,
                    amount = amount,
                    date = LocalDate.of(2026, 7, 24),
                    time = LocalTime.NOON,
                    comment = null,
                ),
            )

            assertFalse("$amount must be invalid", result.isValid)
            assertTrue(result.errors.containsKey(TransactionField.AMOUNT))
        }
    }

    @Test
    fun `draft rejects date outside picker range`() {
        val result = TransactionDraftValidator.validate(
            TransactionDraft(
                accountId = 1,
                categoryId = 2,
                amount = "10",
                date = LocalDate.of(2101, 1, 1),
                time = LocalTime.NOON,
                comment = null,
            ),
        )

        assertFalse(result.isValid)
        assertTrue(result.errors.containsKey(TransactionField.DATE))
    }

    @Test
    fun `draft rejects time more precise than editor can represent`() {
        val result = TransactionDraftValidator.validate(
            TransactionDraft(
                accountId = 1,
                categoryId = 2,
                amount = "10",
                date = LocalDate.of(2026, 7, 24),
                time = LocalTime.of(18, 30, 1),
                comment = null,
            ),
        )

        assertFalse(result.isValid)
        assertTrue(result.errors.containsKey(TransactionField.TIME))
    }
}
