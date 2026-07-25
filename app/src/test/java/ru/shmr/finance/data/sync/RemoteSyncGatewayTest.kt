package ru.shmr.finance.data.sync

import java.time.LocalDateTime
import java.time.OffsetDateTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.shmr.finance.data.network.FinanceApi
import ru.shmr.finance.data.network.dto.AccountCreateRequestDto
import ru.shmr.finance.data.network.dto.AccountDto
import ru.shmr.finance.data.network.dto.AccountUpdateRequestDto
import ru.shmr.finance.data.network.dto.CategoryDto
import ru.shmr.finance.data.network.dto.TransactionDto
import ru.shmr.finance.data.network.dto.TransactionRequestDto
import ru.shmr.finance.data.network.dto.TransactionResponseDto

class RemoteSyncGatewayTest {

    @Test
    fun `pending transaction is sent as RFC3339 with original local date and time`() = runTest {
        val api = CapturingFinanceApi()
        val pending = PendingTransaction(
            localId = "local-1",
            serverId = null,
            accountId = 7,
            categoryId = 42,
            amount = "125.50",
            transactionDate = LocalDateTime.of(2026, 8, 17, 23, 42),
            comment = null,
            action = SyncAction.CREATE,
        )

        val result = RemoteSyncGateway(api).createTransaction(pending)
        val rawDate = requireNotNull(api.createdTransaction).transactionDate
        val parsed = OffsetDateTime.parse(rawDate)

        assertTrue(result is SyncCallResult.Success)
        assertEquals(pending.transactionDate, parsed.toLocalDateTime())
        assertEquals(pending.accountId, api.createdTransaction?.accountId)
        assertEquals(pending.categoryId, api.createdTransaction?.categoryId)
    }

    private class CapturingFinanceApi : FinanceApi {
        var createdTransaction: TransactionRequestDto? = null

        override suspend fun createTransaction(request: TransactionRequestDto): TransactionDto {
            createdTransaction = request
            return TransactionDto(
                id = 99,
                accountId = request.accountId,
                categoryId = request.categoryId,
                amount = request.amount,
                transactionDate = request.transactionDate,
                comment = request.comment,
                createdAt = "2026-08-17T23:42:00Z",
                updatedAt = "2026-08-17T23:42:00Z",
            )
        }

        override suspend fun getAccounts(): List<AccountDto> = error("Not used")

        override suspend fun createAccount(request: AccountCreateRequestDto): AccountDto =
            error("Not used")

        override suspend fun updateAccount(
            id: Int,
            request: AccountUpdateRequestDto,
        ): AccountDto = error("Not used")

        override suspend fun getCategories(): List<CategoryDto> = error("Not used")

        override suspend fun getTransactionsForPeriod(
            accountId: Int,
            startDate: String,
            endDate: String,
        ): List<TransactionResponseDto> = error("Not used")

        override suspend fun updateTransaction(
            id: Int,
            request: TransactionRequestDto,
        ): TransactionResponseDto = error("Not used")
    }
}
