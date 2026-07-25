package ru.shmr.finance.domain.repository

import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import ru.shmr.finance.core.result.AppResult
import ru.shmr.finance.domain.model.Account
import ru.shmr.finance.domain.model.AccountDraft
import ru.shmr.finance.domain.model.Category
import ru.shmr.finance.domain.model.Transaction
import ru.shmr.finance.domain.validation.TransactionDraft

interface AccountsRepository {
    fun observeAccounts(): Flow<List<Account>>
    suspend fun getAccounts(): AppResult<List<Account>>
    suspend fun refreshAccounts(): AppResult<Unit>
    suspend fun hasTransactions(accountId: Int): Boolean
    suspend fun saveAccount(draft: AccountDraft): AppResult<Account>
}

interface CategoriesRepository {
    fun observeCategories(): Flow<List<Category>>
    suspend fun getCategories(): AppResult<List<Category>>
    suspend fun refreshCategories(): AppResult<Unit>
}

interface TransactionsRepository {
    fun observeTransactionsForPeriod(
        startDate: LocalDate,
        endDate: LocalDate,
    ): Flow<List<Transaction>>

    fun observeTransactionsForPeriod(
        accountId: Int,
        startDate: LocalDate,
        endDate: LocalDate,
    ): Flow<List<Transaction>>

    suspend fun getTransactionsForPeriod(
        accountId: Int,
        startDate: LocalDate,
        endDate: LocalDate,
    ): AppResult<List<Transaction>>

    suspend fun refreshTransactionsForPeriod(
        accountId: Int,
        startDate: LocalDate,
        endDate: LocalDate,
    ): AppResult<Unit>

    suspend fun getTransaction(localId: String): Transaction?

    suspend fun saveTransaction(
        draft: TransactionDraft,
        existingLocalId: String? = null,
    ): AppResult<Transaction>
}
