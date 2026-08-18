package ru.shmr.finance.data.repository

import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.shmr.finance.data.mapper.toEntity
import ru.shmr.finance.data.network.dto.AccountBriefDto
import ru.shmr.finance.data.network.dto.CategoryDto
import ru.shmr.finance.data.network.dto.TransactionResponseDto

class TransactionServerDateRangeTest {

    @Test
    fun `Istanbul local day fetches both overlapping UTC dates`() {
        val localDay = LocalDate.of(2026, 7, 30)

        val range = serverDateRangeForLocalPeriod(
            startDate = localDay,
            endDate = localDay,
            zoneId = ZoneId.of("Europe/Istanbul"),
        )

        assertEquals(LocalDate.of(2026, 7, 29), range.startDate)
        assertEquals(LocalDate.of(2026, 7, 30), range.endDate)
    }

    @Test
    fun `New York local day includes the following UTC date`() {
        val localDay = LocalDate.of(2026, 7, 30)

        val range = serverDateRangeForLocalPeriod(
            startDate = localDay,
            endDate = localDay,
            zoneId = ZoneId.of("America/New_York"),
        )

        assertEquals(LocalDate.of(2026, 7, 30), range.startDate)
        assertEquals(LocalDate.of(2026, 7, 31), range.endDate)
    }

    @Test
    fun `UTC record is retained only for its mapped local day`() {
        val zone = ZoneId.of("Europe/Istanbul")
        val entity = transaction("2026-07-29T21:28:00Z").toEntity(zone)

        val july29 = listOf(entity).filterForLocalPeriod(
            LocalDate.of(2026, 7, 29),
            LocalDate.of(2026, 7, 29),
        )
        val july30 = listOf(entity).filterForLocalPeriod(
            LocalDate.of(2026, 7, 30),
            LocalDate.of(2026, 7, 30),
        )

        assertTrue(july29.isEmpty())
        assertEquals(listOf(entity), july30)
    }

    private fun transaction(date: String) = TransactionResponseDto(
        id = 1,
        account = AccountBriefDto(1, "Account", "0", "RUB"),
        category = CategoryDto(1, "Income", "money", true),
        amount = "15000.00",
        transactionDate = date,
        comment = null,
        createdAt = date,
        updatedAt = date,
    )
}
