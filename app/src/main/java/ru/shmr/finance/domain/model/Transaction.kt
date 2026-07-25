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
)
