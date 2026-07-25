package ru.shmr.finance.data.mapper

import java.time.Month
import org.junit.Assert.assertEquals
import org.junit.Test
import ru.shmr.finance.data.network.dto.AccountBriefDto
import ru.shmr.finance.data.network.dto.CategoryDto
import ru.shmr.finance.data.network.dto.TransactionResponseDto

class MapperDateTest {

    private fun dto(date: String) = TransactionResponseDto(
        id = 1,
        account = AccountBriefDto(1, "Acc", "0", "RUB"),
        category = CategoryDto(1, "Cat", "💰", true),
        amount = "100.00",
        transactionDate = date,
        comment = null,
        createdAt = date,
        updatedAt = date,
    )

    @Test
    fun `parses RFC3339 with Z`() {
        val tx = dto("2024-03-15T10:30:00.000Z").toDomain()
        assertEquals(Month.MARCH, tx.dateTime.month)
        assertEquals(15, tx.dateTime.dayOfMonth)
    }

    @Test
    fun `parses value with explicit offset`() {
        val tx = dto("2024-03-15T13:30:00+03:00").toDomain()
        assertEquals(15, tx.dateTime.dayOfMonth)
    }

    @Test
    fun `parses local date-time without offset`() {
        val tx = dto("2024-03-15T10:30:00").toDomain()
        assertEquals(15, tx.dateTime.dayOfMonth)
        assertEquals(10, tx.dateTime.hour)
    }
}
