package ru.shmr.finance.domain.model

data class Category(
    val id: String,
    val name: String,
    val emoji: String,
    val isIncome: Boolean,
)
