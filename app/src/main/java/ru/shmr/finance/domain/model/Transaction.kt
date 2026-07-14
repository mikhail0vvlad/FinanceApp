package ru.shmr.finance.domain.model

data class Transaction(
    val id: String,
    val category: Category,
    val comment: String?,
    val amount: Money,
)

data class TransactionDetailed(
    val id: String,
    val accountId: String,
    val category: Category,
    val comment: String?,
    val amount: Money,
    val transactionDate: String,
    val transactionTime: String,
    val createdAt: String,
    val updatedAt: String,
)
