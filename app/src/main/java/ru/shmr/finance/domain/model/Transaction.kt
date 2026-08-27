package ru.shmr.finance.domain.model

import java.time.LocalDateTime

data class Transaction(
    val id: Int,
    val accountId: Int,
    val accountName: String,
    val category: Category,
    val comment: String?,
    val amount: Money,
    val dateTime: LocalDateTime,
    val localId: String = id.toString(),
    val serverId: Int? = id.takeIf { it > 0 },
    val isPending: Boolean = false,
    val syncFailed: Boolean = false,
)
