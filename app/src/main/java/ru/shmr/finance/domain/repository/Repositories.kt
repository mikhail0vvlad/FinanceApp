package ru.shmr.finance.domain.repository

import java.time.LocalDate
import ru.shmr.finance.core.result.AppResult
import ru.shmr.finance.domain.model.Account
import ru.shmr.finance.domain.model.Category
import ru.shmr.finance.domain.model.Transaction

interface AccountsRepository {
    suspend fun getAccounts(): AppResult<List<Account>>
}

interface CategoriesRepository {
    suspend fun getCategories(): AppResult<List<Category>>
}

interface TransactionsRepository {
    suspend fun getTransactionsForPeriod(
        accountId: Int,
        startDate: LocalDate,
        endDate: LocalDate,
    ): AppResult<List<Transaction>>
}
