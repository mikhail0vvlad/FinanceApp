package ru.shmr.finance.data.local

import java.time.LocalDateTime
import ru.shmr.finance.data.local.entity.AccountEntity
import ru.shmr.finance.data.local.entity.CategoryEntity
import ru.shmr.finance.data.local.entity.TransactionEntity
import ru.shmr.finance.data.local.entity.TransactionRecord
import ru.shmr.finance.data.sync.PendingAccount
import ru.shmr.finance.data.sync.PendingTransaction
import ru.shmr.finance.data.sync.SyncAction
import ru.shmr.finance.domain.model.Account
import ru.shmr.finance.domain.model.Category
import ru.shmr.finance.domain.model.Money
import ru.shmr.finance.domain.model.Transaction

internal fun AccountEntity.toDomain() = Account(
    id = id,
    name = name,
    balance = Money.parse(balance, currency),
    emoji = emoji,
    isPending = syncAction != SyncAction.NONE,
    syncFailed = syncFailure != null,
)

internal fun CategoryEntity.toDomain() = Category(
    id = id,
    name = name,
    emoji = emoji,
    isIncome = isIncome,
)

internal fun TransactionRecord.toDomainOrNull(): Transaction? {
    val account = account ?: return null
    val category = category ?: return null
    val row = transaction
    return Transaction(
        id = row.serverId ?: row.localId.hashCode(),
        accountId = account.id,
        accountName = account.name,
        category = category.toDomain(),
        comment = row.comment,
        amount = Money.parse(row.amount, account.currency),
        dateTime = LocalDateTime.parse(row.transactionDate),
        localId = row.localId,
        serverId = row.serverId,
        isPending = row.syncAction != SyncAction.NONE,
        syncFailed = row.syncFailure != null,
    )
}

internal fun AccountEntity.toPending() = PendingAccount(
    id = id,
    name = name,
    emoji = emoji,
    balance = syncBalance,
    currency = currency,
    action = syncAction,
    revision = revision,
    failure = syncFailure,
)

internal fun PendingAccount.toEntity() = AccountEntity(
    id = id,
    name = name,
    emoji = emoji,
    balance = balance,
    syncBalance = balance,
    currency = currency,
    syncAction = action,
    revision = revision,
    syncFailure = failure,
)

internal fun TransactionEntity.toPending() = PendingTransaction(
    localId = localId,
    serverId = serverId,
    accountId = accountId,
    categoryId = categoryId,
    amount = amount,
    transactionDate = LocalDateTime.parse(transactionDate),
    comment = comment,
    action = syncAction,
    revision = revision,
    failure = syncFailure,
)
