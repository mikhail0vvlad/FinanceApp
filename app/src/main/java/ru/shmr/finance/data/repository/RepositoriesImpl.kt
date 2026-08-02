package ru.shmr.finance.data.repository

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
import ru.shmr.finance.domain.model.ValidationIssue
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

    private val refreshGate = SuccessfulRefreshGate<Unit>()

    override fun observeAccounts(): Flow<List<Account>> = local.observeAccounts()

    override suspend fun getAccounts(): AppResult<List<Account>> {
        val refresh = refreshGate.run(Unit, reuseRecentSuccess = true, ::loadRemoteAccounts)
        val cached = local.getAccounts()
        return if (cached.isNotEmpty() || refresh is AppResult.Success) {
            AppResult.Success(cached)
        } else {
            refresh as AppResult.Failure
        }
    }

    override suspend fun refreshAccounts(): AppResult<Unit> =
        refreshGate.run(Unit, reuseRecentSuccess = false, ::loadRemoteAccounts)

    private suspend fun loadRemoteAccounts(): AppResult<Unit> = safeApiCall {
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
            return AppResult.Failure(
                AppError.Validation(ValidationIssue.ACCOUNT_CURRENCY_LOCKED),
            )
        }
        if (draft.name.isBlank()) {
            return AppResult.Failure(AppError.Validation(ValidationIssue.ACCOUNT_NAME_REQUIRED))
        }
        if (draft.balance.signum() < 0) {
            return AppResult.Failure(AppError.Validation(ValidationIssue.BALANCE_NEGATIVE))
        }
        return localResult {
            local.saveAccount(draft).also { syncScheduler.enqueueOneTime() }
        }
    }
}

class CategoriesRepositoryImpl(
    private val api: FinanceApi,
    private val local: LocalFinanceDataSource,
) : CategoriesRepository {

    private val refreshGate = SuccessfulRefreshGate<Unit>()

    override fun observeCategories(): Flow<List<Category>> = local.observeCategories()

    override suspend fun getCategories(): AppResult<List<Category>> {
        val refresh = refreshGate.run(Unit, reuseRecentSuccess = true, ::loadRemoteCategories)
        val cached = local.getCategories()
        return if (cached.isNotEmpty() || refresh is AppResult.Success) {
            AppResult.Success(cached)
        } else {
            refresh as AppResult.Failure
        }
    }

    override suspend fun refreshCategories(): AppResult<Unit> =
        refreshGate.run(Unit, reuseRecentSuccess = false, ::loadRemoteCategories)

    private suspend fun loadRemoteCategories(): AppResult<Unit> = safeApiCall {
        local.upsertCategories(api.getCategories().map { it.toEntity() })
    }
}

class TransactionsRepositoryImpl(
    private val api: FinanceApi,
    private val local: LocalFinanceDataSource,
    private val syncScheduler: SyncScheduler,
) : TransactionsRepository {

    private val dateFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    private val refreshGate = SuccessfulRefreshGate<TransactionRefreshKey>()

    override fun observeTransactionsForPeriod(
        startDate: LocalDate,
        endDate: LocalDate,
    ): Flow<List<Transaction>> = local.observeTransactions(startDate, endDate)

    override suspend fun refreshTransactionsForPeriod(
        accountId: Int,
        startDate: LocalDate,
        endDate: LocalDate,
    ): AppResult<Unit> = refreshGate.run(
        key = TransactionRefreshKey(accountId, startDate, endDate),
        reuseRecentSuccess = true,
    ) {
        safeApiCall {
            val remote = api.getTransactionsForPeriod(
                accountId = accountId,
                startDate = startDate.format(dateFormatter),
                endDate = endDate.format(dateFormatter),
            ).map { it.toEntity() }
            local.replaceRemoteTransactions(accountId, startDate, endDate, remote)
        }
    }

    override suspend fun getTransaction(localId: String): Transaction? =
        local.getTransaction(localId)

    override suspend fun saveTransaction(
        draft: TransactionDraft,
        existingLocalId: String?,
    ): AppResult<Transaction> {
        val validation = TransactionDraftValidator.validate(draft)
        if (!validation.isValid) {
            return AppResult.Failure(AppError.Validation(ValidationIssue.TRANSACTION_INVALID))
        }
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

private data class TransactionRefreshKey(
    val accountId: Int,
    val startDate: LocalDate,
    val endDate: LocalDate,
)

private class SuccessfulRefreshGate<K : Any>(
    private val freshnessNanos: Long = 2_000_000_000,
    private val nanoTime: () -> Long = System::nanoTime,
) {
    private val locks = ConcurrentHashMap<K, Mutex>()
    private val lastSuccess = ConcurrentHashMap<K, Long>()

    suspend fun run(
        key: K,
        reuseRecentSuccess: Boolean,
        block: suspend () -> AppResult<Unit>,
    ): AppResult<Unit> = locks.computeIfAbsent(key) { Mutex() }.withLock {
        val now = nanoTime()
        val recent = lastSuccess[key]?.let { now - it < freshnessNanos } == true
        if (reuseRecentSuccess && recent) return@withLock AppResult.Success(Unit)

        block().also { result ->
            if (result is AppResult.Success) lastSuccess[key] = nanoTime()
        }
    }
}

private suspend inline fun <T> localResult(block: () -> T): AppResult<T> = try {
    AppResult.Success(block())
} catch (error: CancellationException) {
    throw error
} catch (_: Exception) {
    AppResult.Failure(AppError.Storage)
}
