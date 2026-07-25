package ru.shmr.finance.data.repository

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import ru.shmr.finance.core.result.AppResult
import ru.shmr.finance.core.result.map
import ru.shmr.finance.data.local.LocalFinanceDataSource
import ru.shmr.finance.data.mapper.toEntity
import ru.shmr.finance.data.network.FinanceApi
import ru.shmr.finance.data.network.safeApiCall
import ru.shmr.finance.data.sync.SyncScheduler
import ru.shmr.finance.domain.model.Account
import ru.shmr.finance.domain.model.AccountDraft
import ru.shmr.finance.domain.model.AppError
import ru.shmr.finance.domain.model.Category
import ru.shmr.finance.domain.model.Transaction
import ru.shmr.finance.domain.repository.AccountsRepository
import ru.shmr.finance.domain.repository.CategoriesRepository
import ru.shmr.finance.domain.repository.TransactionsRepository
import ru.shmr.finance.domain.validation.TransactionDraft
import ru.shmr.finance.domain.validation.TransactionDraftValidator

class AccountsRepositoryImpl(
    private val api: FinanceApi,
    private val local: LocalFinanceDataSource,
    private val syncScheduler: SyncScheduler,
) : AccountsRepository {

    override fun observeAccounts(): Flow<List<Account>> = local.observeAccounts()

    override suspend fun getAccounts(): AppResult<List<Account>> {
        val refresh = refreshAccounts()
        val cached = local.getAccounts()
        return if (cached.isNotEmpty() || refresh is AppResult.Success) {
            AppResult.Success(cached)
        } else {
            refresh as AppResult.Failure
        }
    }

    override suspend fun refreshAccounts(): AppResult<Unit> = safeApiCall {
        local.upsertRemoteAccounts(api.getAccounts().map { it.toEntity() })
    }

    override suspend fun hasTransactions(accountId: Int): Boolean {
        if (local.hasTransactions(accountId)) return true
        if (accountId < 0) return false

        val remote = safeApiCall {
            api.getTransactionsForPeriod(
                accountId = accountId,
                startDate = LocalDate.of(1970, 1, 1).format(DateTimeFormatter.ISO_LOCAL_DATE),
                endDate = LocalDate.now().plusDays(1).format(DateTimeFormatter.ISO_LOCAL_DATE),
            )
        }
        return when (remote) {
            is AppResult.Success -> remote.data.isNotEmpty()
            is AppResult.Failure -> true
        }
    }

    override suspend fun saveAccount(draft: AccountDraft): AppResult<Account> {
        val existing = draft.id?.let { id -> local.getAccounts().find { it.id == id } }
        if (
            existing != null &&
            existing.balance.currency != draft.currency &&
            hasTransactions(existing.id)
        ) {
            return AppResult.Failure(AppError.Unknown)
        }
        return localResult {
            require(draft.name.isNotBlank()) { "Account name is required" }
            require(draft.balance.signum() >= 0) { "Balance cannot be negative" }
            local.saveAccount(draft).also { syncScheduler.enqueueOneTime() }
        }
    }
}

class CategoriesRepositoryImpl(
    private val api: FinanceApi,
    private val local: LocalFinanceDataSource,
) : CategoriesRepository {

    override fun observeCategories(): Flow<List<Category>> = local.observeCategories()

    override suspend fun getCategories(): AppResult<List<Category>> {
        val refresh = refreshCategories()
        val cached = local.getCategories()
        return if (cached.isNotEmpty() || refresh is AppResult.Success) {
            AppResult.Success(cached)
        } else {
            refresh as AppResult.Failure
        }
    }

    override suspend fun refreshCategories(): AppResult<Unit> = safeApiCall {
        local.upsertCategories(api.getCategories().map { it.toEntity() })
    }
}

class TransactionsRepositoryImpl(
    private val api: FinanceApi,
    private val local: LocalFinanceDataSource,
    private val syncScheduler: SyncScheduler,
) : TransactionsRepository {

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE

    override fun observeTransactionsForPeriod(
        startDate: LocalDate,
        endDate: LocalDate,
    ): Flow<List<Transaction>> = local.observeTransactions(startDate, endDate)

    override fun observeTransactionsForPeriod(
        accountId: Int,
        startDate: LocalDate,
        endDate: LocalDate,
    ): Flow<List<Transaction>> = local.observeTransactions(accountId, startDate, endDate)

    override suspend fun getTransactionsForPeriod(
        accountId: Int,
        startDate: LocalDate,
        endDate: LocalDate,
    ): AppResult<List<Transaction>> {
        val refresh = refreshTransactionsForPeriod(accountId, startDate, endDate)
        val cached = local.getTransactions(accountId, startDate, endDate)
        return if (cached.isNotEmpty() || refresh is AppResult.Success) {
            AppResult.Success(cached)
        } else {
            refresh as AppResult.Failure
        }
    }

    override suspend fun refreshTransactionsForPeriod(
        accountId: Int,
        startDate: LocalDate,
        endDate: LocalDate,
    ): AppResult<Unit> = safeApiCall {
        val remote = api.getTransactionsForPeriod(
            accountId = accountId,
            startDate = startDate.format(dateFormatter),
            endDate = endDate.format(dateFormatter),
        ).map { it.toEntity() }
        local.replaceRemoteTransactions(accountId, startDate, endDate, remote)
    }

    override suspend fun getTransaction(localId: String): Transaction? =
        local.getTransaction(localId)

    override suspend fun saveTransaction(
        draft: TransactionDraft,
        existingLocalId: String?,
    ): AppResult<Transaction> {
        val validation = TransactionDraftValidator.validate(draft)
        if (!validation.isValid) return AppResult.Failure(AppError.Unknown)
        return localResult {
            local.saveTransaction(
                draft = draft,
                normalizedAmount = requireNotNull(validation.normalizedAmount),
                normalizedComment = validation.normalizedComment,
                existingLocalId = existingLocalId,
            ).also { syncScheduler.enqueueOneTime() }
        }
    }
}

private suspend inline fun <T> localResult(block: () -> T): AppResult<T> = try {
    AppResult.Success(block())
} catch (error: CancellationException) {
    throw error
} catch (_: Exception) {
    AppResult.Failure(AppError.Unknown)
}
