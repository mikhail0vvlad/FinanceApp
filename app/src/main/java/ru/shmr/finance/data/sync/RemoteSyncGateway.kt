package ru.shmr.finance.data.sync

import java.time.ZoneId
import java.time.format.DateTimeFormatter
import ru.shmr.finance.core.dispatchers.DefaultDispatcherProvider
import ru.shmr.finance.core.dispatchers.DispatcherProvider
import ru.shmr.finance.core.result.AppResult
import ru.shmr.finance.data.mapper.parseDateTime
import ru.shmr.finance.data.network.FinanceApi
import ru.shmr.finance.data.network.dto.AccountCreateRequestDto
import ru.shmr.finance.data.network.dto.AccountUpdateRequestDto
import ru.shmr.finance.data.network.dto.TransactionRequestDto
import ru.shmr.finance.data.network.safeApiCall
import ru.shmr.finance.domain.model.AppError

internal class RemoteSyncGateway(
    private val api: FinanceApi,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider,
) : SyncRemoteGateway {

    override suspend fun createAccount(account: PendingAccount): SyncCallResult<PendingAccount> =
        safeApiCall(dispatchers.io) {
            api.createAccount(
                AccountCreateRequestDto(
                    name = account.name,
                    emoji = account.emoji,
                    balance = account.balance,
                    currency = account.currency,
                ),
            )
        }.toSyncResult { response ->
            account.copy(
                id = response.id,
                name = response.name,
                emoji = response.emoji,
                balance = response.balance,
                currency = response.currency,
                action = SyncAction.NONE,
            )
        }

    override suspend fun updateAccount(account: PendingAccount): SyncCallResult<PendingAccount> =
        safeApiCall(dispatchers.io) {
            api.updateAccount(
                id = account.id,
                request = AccountUpdateRequestDto(
                    name = account.name,
                    emoji = account.emoji,
                    balance = account.balance,
                    currency = account.currency,
                ),
            )
        }.toSyncResult { response ->
            account.copy(
                name = response.name,
                emoji = response.emoji,
                balance = response.balance,
                currency = response.currency,
                action = SyncAction.NONE,
            )
        }

    override suspend fun createTransaction(
        transaction: PendingTransaction,
    ): SyncCallResult<PendingTransaction> = safeApiCall(dispatchers.io) {
        api.createTransaction(transaction.toRequest())
    }.toSyncResult { response ->
        transaction.copy(
            serverId = response.id,
            accountId = response.accountId,
            categoryId = response.categoryId,
            amount = response.amount,
            transactionDate = parseDateTime(response.transactionDate),
            comment = response.comment,
            action = SyncAction.NONE,
        )
    }

    override suspend fun updateTransaction(
        transaction: PendingTransaction,
    ): SyncCallResult<PendingTransaction> {
        val serverId = transaction.serverId ?: return SyncCallResult.ItemPermanentFailure
        return safeApiCall(dispatchers.io) {
            api.updateTransaction(serverId, transaction.toRequest())
        }.toSyncResult { response ->
            transaction.copy(
                accountId = response.account.id,
                categoryId = response.category.id,
                amount = response.amount,
                transactionDate = parseDateTime(response.transactionDate),
                comment = response.comment,
                action = SyncAction.NONE,
            )
        }
    }
}

private fun PendingTransaction.toRequest() = TransactionRequestDto(
    accountId = accountId,
    categoryId = categoryId,
    amount = amount,
    transactionDate = transactionDate
        .atZone(ZoneId.systemDefault())
        .toOffsetDateTime()
        .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME),
    comment = comment,
)

private inline fun <T, R> AppResult<T>.toSyncResult(
    transform: (T) -> R,
): SyncCallResult<R> = when (this) {
    is AppResult.Success -> SyncCallResult.Success(transform(data))
    is AppResult.Failure -> when (error) {
        AppError.NoInternet, is AppError.Server -> SyncCallResult.RetryableFailure
        AppError.Unauthorized -> SyncCallResult.GlobalFatalFailure
        is AppError.Client,
        is AppError.Validation,
        AppError.Storage,
        AppError.Unknown,
        -> SyncCallResult.ItemPermanentFailure
    }
}
