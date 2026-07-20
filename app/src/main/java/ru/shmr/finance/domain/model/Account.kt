package ru.shmr.finance.domain.model

data class Account(
    val id: Int,
    val name: String,
    val balance: Money,
)
