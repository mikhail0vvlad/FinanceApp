package ru.shmr.finance.data.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class AccountDto(
    val id: Int,
    val userId: Int,
    val name: String,
    val balance: String,
    val currency: String,
    val createdAt: String,
    val updatedAt: String,
    val emoji: String = "💵",
)

@Serializable
data class AccountBriefDto(
    val id: Int,
    val name: String,
    val balance: String,
    val currency: String,
    val emoji: String = "💵",
)

@Serializable
data class CategoryDto(
    val id: Int,
    val name: String,
    val emoji: String,
    val isIncome: Boolean,
)

@Serializable
data class TransactionResponseDto(
    val id: Int,
    val account: AccountBriefDto,
    val category: CategoryDto,
    val amount: String,
    val transactionDate: String,
    val comment: String? = null,
    val createdAt: String,
    val updatedAt: String,
)

@Serializable
data class AccountCreateRequestDto(
    val name: String,
    val emoji: String,
    val balance: String,
    val currency: String,
)

@Serializable
data class AccountUpdateRequestDto(
    val name: String,
    val emoji: String?,
    val balance: String,
    val currency: String,
)

@Serializable
data class TransactionRequestDto(
    val accountId: Int,
    val categoryId: Int,
    val amount: String,
    val transactionDate: String,
    val comment: String?,
)

@Serializable
data class TransactionDto(
    val id: Int,
    val accountId: Int,
    val categoryId: Int,
    val amount: String,
    val transactionDate: String,
    val comment: String? = null,
    val createdAt: String,
    val updatedAt: String,
)
