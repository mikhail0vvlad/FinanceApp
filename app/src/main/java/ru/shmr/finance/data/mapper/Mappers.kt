package ru.shmr.finance.data.mapper

import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import ru.shmr.finance.data.local.entity.AccountEntity
import ru.shmr.finance.data.local.entity.CategoryEntity
import ru.shmr.finance.data.local.entity.TransactionEntity
import ru.shmr.finance.data.network.dto.AccountDto
import ru.shmr.finance.data.network.dto.CategoryDto
import ru.shmr.finance.data.network.dto.TransactionResponseDto
import ru.shmr.finance.domain.model.Account
import ru.shmr.finance.domain.model.Category
import ru.shmr.finance.domain.model.Money
import ru.shmr.finance.domain.model.Transaction
import ru.shmr.finance.data.sync.SyncAction

fun AccountDto.toDomain() = Account(
    id = id,
    name = name,
    balance = Money.parse(balance, currency),
    emoji = emoji,
)

fun CategoryDto.toDomain() = Category(
    id = id,
    name = name,
    emoji = emoji,
    isIncome = isIncome,
)

fun TransactionResponseDto.toDomain() = Transaction(
    id = id,
    accountId = account.id,
    accountName = account.name,
    category = category.toDomain(),
    comment = comment?.takeIf { it.isNotBlank() },
    amount = Money.parse(amount, account.currency),
    dateTime = parseDateTime(transactionDate),
    localId = "remote-$id",
    serverId = id,
)

fun AccountDto.toEntity() = AccountEntity(
    id = id,
    name = name,
    emoji = emoji,
    balance = balance,
    syncBalance = balance,
    currency = currency,
    syncAction = SyncAction.NONE,
)

fun CategoryDto.toEntity() = CategoryEntity(
    id = id,
    name = name,
    emoji = emoji,
    isIncome = isIncome,
)

fun TransactionResponseDto.toEntity() = TransactionEntity(
    localId = "remote-$id",
    serverId = id,
    accountId = account.id,
    categoryId = category.id,
    amount = amount,
    transactionDate = parseDateTime(transactionDate).toString(),
    comment = comment?.takeIf { it.isNotBlank() },
    syncAction = SyncAction.NONE,
)

// Сервер отдаёт date-time по-разному: с 'Z', со смещением и иногда без него.
fun parseDateTime(raw: String): LocalDateTime =
    runCatching { OffsetDateTime.parse(raw).atZoneSameInstant(ZoneId.systemDefault()).toLocalDateTime() }
        .recoverCatching { Instant.parse(raw).atZone(ZoneId.systemDefault()).toLocalDateTime() }
        .getOrElse { LocalDateTime.parse(raw) }
