package ru.shmr.finance.data.repository

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import ru.shmr.finance.core.result.AppResult
import ru.shmr.finance.data.mapper.toDomain
import ru.shmr.finance.data.network.FinanceApi
import ru.shmr.finance.data.network.safeApiCall
import ru.shmr.finance.domain.model.Account
import ru.shmr.finance.domain.model.Category
import ru.shmr.finance.domain.model.Transaction
import ru.shmr.finance.domain.repository.AccountsRepository
import ru.shmr.finance.domain.repository.CategoriesRepository
import ru.shmr.finance.domain.repository.TransactionsRepository

class AccountsRepositoryImpl(private val api: FinanceApi) : AccountsRepository {
    override suspend fun getAccounts(): AppResult<List<Account>> =
        safeApiCall { api.getAccounts().map { it.toDomain() } }
}

class CategoriesRepositoryImpl(private val api: FinanceApi) : CategoriesRepository {
    override suspend fun getCategories(): AppResult<List<Category>> =
        safeApiCall { api.getCategories().map { it.toDomain() } }
}

class TransactionsRepositoryImpl(private val api: FinanceApi) : TransactionsRepository {

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    override suspend fun getTransactionsForPeriod(
        accountId: Int,
        startDate: LocalDate,
        endDate: LocalDate,
    ): AppResult<List<Transaction>> = safeApiCall {
        api.getTransactionsForPeriod(
            accountId = accountId,
            startDate = startDate.format(dateFormatter),
            endDate = endDate.format(dateFormatter),
        ).map { it.toDomain() }
    }
}
