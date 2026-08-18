package ru.shmr.finance.data.local

import androidx.room.withTransaction
import java.time.LocalDate
import kotlinx.coroutines.withContext
import ru.shmr.finance.core.dispatchers.DispatcherProvider
import ru.shmr.finance.data.sync.PendingAccount
import ru.shmr.finance.data.sync.PendingTransaction
import ru.shmr.finance.data.sync.SyncAction
import ru.shmr.finance.data.sync.SyncFailure
import ru.shmr.finance.data.sync.SyncQueue

/** Owns revision-aware persistence of the offline synchronization queue. */
internal class RoomSyncQueue(
    private val database: FinanceDatabase,
    private val dispatchers: DispatcherProvider,
) : SyncQueue {
    private val accounts = database.accountDao()
    private val transactions = database.transactionDao()

    override suspend fun pendingAccounts(): List<PendingAccount> = withContext(dispatchers.io) {
        accounts.getPending().map { it.toPending() }
    }

    override suspend fun pendingTransactions(): List<PendingTransaction> =
        withContext(dispatchers.io) { transactions.getPending().map { it.toPending() } }

    override suspend fun replaceCreatedAccount(sent: PendingAccount, remote: PendingAccount) {
        withContext(dispatchers.io) {
            database.withTransaction {
                val local = accounts.getById(sent.id) ?: return@withTransaction
                val replacement = createdAccountReplacement(local, sent, remote)
                accounts.upsert(replacement)
                transactions.reassignAccount(sent.id, remote.id)
                accounts.deleteById(sent.id)
            }
        }
    }

    override suspend fun markAccountSynced(sent: PendingAccount, remote: PendingAccount) {
        withContext(dispatchers.io) {
            database.withTransaction {
                val local = accounts.getById(sent.id) ?: return@withTransaction
                if (local.revision != sent.revision) return@withTransaction
                accounts.upsert(
                    remote.toEntity().copy(
                        balance = local.balance,
                        syncBalance = remote.balance,
                        syncAction = SyncAction.NONE,
                        revision = local.revision,
                        syncFailure = null,
                        transactionSyncCursor = local.transactionSyncCursor,
                    ),
                )
            }
        }
    }

    override suspend fun replaceCreatedTransaction(
        sent: PendingTransaction,
        remote: PendingTransaction,
    ) {
        withContext(dispatchers.io) {
            database.withTransaction {
                val local = transactions.getByLocalId(sent.localId) ?: return@withTransaction
                transactions.upsert(createdTransactionReplacement(local, sent, remote))
            }
        }
    }

    override suspend fun markTransactionSynced(
        sent: PendingTransaction,
        remote: PendingTransaction,
    ) {
        withContext(dispatchers.io) {
            database.withTransaction {
                val local = transactions.getByLocalId(sent.localId) ?: return@withTransaction
                if (local.revision != sent.revision) return@withTransaction
                transactions.upsert(
                    local.copy(
                        serverId = remote.serverId,
                        accountId = remote.accountId,
                        categoryId = remote.categoryId,
                        amount = remote.amount,
                        transactionDate = remote.transactionDate.toString(),
                        comment = remote.comment,
                        syncAction = SyncAction.NONE,
                        syncFailure = null,
                    ),
                )
            }
        }
    }

    override suspend fun markAccountFailed(sent: PendingAccount) {
        withContext(dispatchers.io) {
            val current = accounts.getById(sent.id) ?: return@withContext
            if (current.revision == sent.revision) {
                accounts.upsert(current.copy(syncFailure = SyncFailure.ITEM_PERMANENT))
            }
        }
    }

    override suspend fun markTransactionFailed(sent: PendingTransaction) {
        withContext(dispatchers.io) {
            val current = transactions.getByLocalId(sent.localId) ?: return@withContext
            if (current.revision == sent.revision) {
                transactions.upsert(current.copy(syncFailure = SyncFailure.ITEM_PERMANENT))
            }
        }
    }

    suspend fun transactionSyncStartDate(accountId: Int): LocalDate = withContext(dispatchers.io) {
        accounts.getById(accountId)?.transactionSyncCursor?.let(LocalDate::parse)
            ?.minusDays(SYNC_OVERLAP_DAYS) ?: FULL_HISTORY_START
    }

    suspend fun markTransactionsSyncedThrough(accountId: Int, endDate: LocalDate) {
        withContext(dispatchers.io) {
            val account = accounts.getById(accountId) ?: return@withContext
            accounts.upsert(account.copy(transactionSyncCursor = endDate.toString()))
        }
    }
}

private fun createdAccountReplacement(
    local: ru.shmr.finance.data.local.entity.AccountEntity,
    sent: PendingAccount,
    remote: PendingAccount,
) = if (local.revision == sent.revision) {
    remote.toEntity().copy(
        balance = local.balance,
        syncBalance = remote.balance,
        syncAction = SyncAction.NONE,
        revision = local.revision,
        syncFailure = null,
        transactionSyncCursor = local.transactionSyncCursor,
    )
} else {
    local.copy(id = remote.id, syncAction = SyncAction.UPDATE, syncFailure = null)
}

private fun createdTransactionReplacement(
    local: ru.shmr.finance.data.local.entity.TransactionEntity,
    sent: PendingTransaction,
    remote: PendingTransaction,
) = if (local.revision == sent.revision) {
    local.copy(
        serverId = remote.serverId,
        accountId = remote.accountId,
        categoryId = remote.categoryId,
        amount = remote.amount,
        transactionDate = remote.transactionDate.toString(),
        comment = remote.comment,
        syncAction = SyncAction.NONE,
        syncFailure = null,
    )
} else {
    local.copy(serverId = remote.serverId, syncAction = SyncAction.UPDATE, syncFailure = null)
}

private const val SYNC_OVERLAP_DAYS = 7L
private val FULL_HISTORY_START: LocalDate = LocalDate.of(1970, 1, 1)
