package ru.shmr.finance.data.sync

import java.time.LocalDate
import ru.shmr.finance.core.result.AppResult
import ru.shmr.finance.data.local.LocalFinanceDataSource
import ru.shmr.finance.domain.model.AppError
import ru.shmr.finance.domain.repository.AccountsRepository
import ru.shmr.finance.domain.repository.ApiTokenRepository
import ru.shmr.finance.domain.repository.CategoriesRepository
import ru.shmr.finance.domain.repository.TransactionsRepository

/**
 * Runs the full offline-first sync pipeline: pending queue first, then a full accounts/categories
 * refresh, then per-account transactions for the unsynced window. Runs are serialized so a
 * WorkManager-triggered sync and a foreground-triggered sync never race each other.
 */
internal class SyncOrchestrator(
    private val local: LocalFinanceDataSource,
    private val syncEngine: SyncEngine,
    private val apiTokenRepository: ApiTokenRepository,
    private val accountsRepository: AccountsRepository,
    private val categoriesRepository: CategoriesRepository,
    private val transactionsRepository: TransactionsRepository,
    private val coordinator: SyncCoordinator = SyncCoordinator(),
) {

    suspend fun syncAll(): SyncOutcome = coordinator.run {
        if (!apiTokenRepository.hasToken.value) return@run SyncOutcome.FAILURE
        val pendingOutcome = syncEngine.sync()
        when (pendingOutcome) {
            SyncOutcome.RETRY, SyncOutcome.FAILURE -> return@run pendingOutcome
            SyncOutcome.SUCCESS, SyncOutcome.PARTIAL_FAILURE -> Unit
        }

        accountsRepository.refreshAccounts().syncFailureOrNull()?.let { return@run it }
        categoriesRepository.refreshCategories().syncFailureOrNull()?.let { return@run it }
        val accounts = local.getAccounts()
        val end = LocalDate.now().plusDays(1)
        accounts.filter { it.id > 0 }.forEach { account ->
            val start = local.transactionSyncStartDate(account.id)
            transactionsRepository.refreshTransactionsForPeriod(
                accountId = account.id,
                startDate = start,
                endDate = end,
            ).syncFailureOrNull()?.let { return@run it }
            local.markTransactionsSyncedThrough(account.id, end)
        }
        pendingOutcome
    }
}

private fun AppResult<Unit>.syncFailureOrNull(): SyncOutcome? = when (this) {
    is AppResult.Success -> null
    is AppResult.Failure -> when (error) {
        AppError.NoInternet, is AppError.Server -> SyncOutcome.RETRY
        AppError.Unauthorized,
        is AppError.Client,
        is AppError.Validation,
        AppError.Storage,
        AppError.Unknown,
        -> SyncOutcome.FAILURE
    }
}
