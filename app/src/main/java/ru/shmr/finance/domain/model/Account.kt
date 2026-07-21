package ru.shmr.finance.domain.model

data class Account(
    val id: String,
    val name: String,
    val balance: Money,
    val emoji: String,
)

data class AccountDetailed(
    val id: String,
    val name: String,
    val balance: Money,
    val currency: Currency,
    val emoji: String,
    val createdAt: String,
    val updatedAt: String,
)
