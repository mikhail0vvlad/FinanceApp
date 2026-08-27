package ru.shmr.finance.domain.model

data class Category(
    val id: Int,
    val name: String,
    val emoji: String,
    val isIncome: Boolean,
)
