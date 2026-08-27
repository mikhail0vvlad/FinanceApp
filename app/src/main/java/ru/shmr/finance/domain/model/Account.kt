package ru.shmr.finance.domain.model

data class Account(
    val id: Int,
    val name: String,
    val balance: Money,
    val emoji: String = "💰",
    val isPending: Boolean = false,
    val syncFailed: Boolean = false,
)
