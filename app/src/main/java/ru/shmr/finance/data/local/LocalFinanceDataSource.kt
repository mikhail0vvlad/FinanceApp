package ru.shmr.finance.data.local

import java.math.BigDecimal
import java.time.LocalDate
import kotlinx.coroutines.flow.Flow
import ru.shmr.finance.core.dispatchers.DefaultDispatcherProvider
import ru.shmr.finance.core.dispatchers.DispatcherProvider
import ru.shmr.finance.data.local.entity.AccountEntity
import ru.shmr.finance.data.local.entity.CategoryEntity
import ru.shmr.finance.data.local.entity.TransactionEntity
import ru.shmr.finance.data.sync.PendingAccount
import ru.shmr.finance.data.sync.PendingTransaction
import ru.shmr.finance.data.sync.SyncQueue
import ru.shmr.finance.domain.model.Account
import ru.shmr.finance.domain.model.AccountDraft
import ru.shmr.finance.domain.model.Category
import ru.shmr.finance.domain.model.Transaction
import ru.shmr.finance.domain.validation.TransactionDraft

/** Room-backed facade that keeps repository access separate from local storage details. */
internal class LocalFinanceDataSource(
    database: FinanceDatabase,
    dispatchers: DispatcherProvider = DefaultDispatcherProvider,
) : SyncQueue {
    private val accountSource = AccountLocalDataSource(database, dispatchers)
    private val categorySource = CategoryLocalDataSource(database, dispatchers)
    private val transactionSource = TransactionLocalDataSource(database, dispatchers)
    private val syncQueue = RoomSyncQueue(database, dispatchers)

    fun observeAccounts(): Flow<List<Account>> = accountSource.observeAccounts()

    fun observeCategories(): Flow<List<Category>> = categorySource.observeCategories()

    fun observeTransactions(startDate: LocalDate, endDate: LocalDate): Flow<List<Transaction>> =
        transactionSource.observeTransactions(startDate, endDate)

    suspend fun getAccounts(): List<Account> = accountSource.getAccounts()

    suspend fun getCategories(): List<Category> = categorySource.getCategories()

    suspend fun getTransaction(localId: String): Transaction? =
        transactionSource.getTransaction(localId)

    suspend fun hasTransactions(accountId: Int): Boolean =
        accountSource.hasTransactions(accountId)

    suspend fun upsertRemoteAccounts(remote: List<AccountEntity>) =
        accountSource.upsertRemoteAccounts(remote)

    suspend fun upsertCategories(remote: List<CategoryEntity>) =
        categorySource.upsertCategories(remote)

    suspend fun replaceRemoteTransactions(
        accountId: Int,
        startDate: LocalDate,
        endDate: LocalDate,
        remote: List<TransactionEntity>,
    ) = transactionSource.replaceRemoteTransactions(accountId, startDate, endDate, remote)

    suspend fun saveAccount(draft: AccountDraft): Account = accountSource.saveAccount(draft)

    suspend fun saveTransaction(
        draft: TransactionDraft,
        normalizedAmount: BigDecimal,
        normalizedComment: String?,
        existingLocalId: String?,
    ): Transaction = transactionSource.saveTransaction(
        draft = draft,
        normalizedAmount = normalizedAmount,
        normalizedComment = normalizedComment,
        existingLocalId = existingLocalId,
    )

    override suspend fun pendingAccounts(): List<PendingAccount> = syncQueue.pendingAccounts()

    override suspend fun pendingTransactions(): List<PendingTransaction> =
        syncQueue.pendingTransactions()

    override suspend fun replaceCreatedAccount(sent: PendingAccount, remote: PendingAccount) =
        syncQueue.replaceCreatedAccount(sent, remote)

    override suspend fun markAccountSynced(sent: PendingAccount, remote: PendingAccount) =
        syncQueue.markAccountSynced(sent, remote)

    override suspend fun replaceCreatedTransaction(
        sent: PendingTransaction,
        remote: PendingTransaction,
    ) = syncQueue.replaceCreatedTransaction(sent, remote)

    override suspend fun markTransactionSynced(
        sent: PendingTransaction,
        remote: PendingTransaction,
    ) = syncQueue.markTransactionSynced(sent, remote)

    override suspend fun markAccountFailed(sent: PendingAccount) =
        syncQueue.markAccountFailed(sent)

    override suspend fun markTransactionFailed(sent: PendingTransaction) =
        syncQueue.markTransactionFailed(sent)

    suspend fun transactionSyncStartDate(accountId: Int): LocalDate =
        syncQueue.transactionSyncStartDate(accountId)

    suspend fun markTransactionsSyncedThrough(accountId: Int, endDate: LocalDate) =
        syncQueue.markTransactionsSyncedThrough(accountId, endDate)
}
