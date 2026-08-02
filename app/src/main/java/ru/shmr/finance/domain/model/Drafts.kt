package ru.shmr.finance.domain.model

import java.math.BigDecimal

data class AccountDraft(
    val id: Int? = null,
    val name: String,
    val emoji: String,
    val balance: BigDecimal,
    val currency: Currency,
)
