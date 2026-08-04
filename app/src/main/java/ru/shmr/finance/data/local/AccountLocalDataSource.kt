package ru.shmr.finance.data.local

import androidx.room.withTransaction
import java.math.BigDecimal
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import ru.shmr.finance.core.dispatchers.DispatcherProvider
import ru.shmr.finance.data.local.entity.AccountEntity
import ru.shmr.finance.data.sync.SyncAction
import ru.shmr.finance.domain.model.Account
import ru.shmr.finance.domain.model.AccountDraft

/** Owns cached account reads, remote merges, and local account edits. */
internal class AccountLocalDataSource(
    private val database: FinanceDatabase,
    private val dispatchers: DispatcherProvider,
) {
    private val accounts = database.accountDao()
    private val transactions = database.transactionDao()

    fun observeAccounts(): Flow<List<Account>> =
        accounts.observeAll().map { rows -> rows.map(AccountEntity::toDomain) }

    suspend fun getAccounts(): List<Account> = withContext(dispatchers.io) {
        accounts.getAll().map(AccountEntity::toDomain)
    }

    suspend fun hasTransactions(accountId: Int): Boolean = withContext(dispatchers.io) {
        transactions.existsForAccount(accountId)
    }

    suspend fun upsertRemoteAccounts(remote: List<AccountEntity>) = withContext(dispatchers.io) {
        database.withTransaction {
            val pendingIds = accounts.getPending().mapTo(mutableSetOf()) { it.id }
            val pendingTransactionAccounts = transactions.pendingAccountIds().toSet()
            val cachedById = accounts.getAll().associateBy { it.id }
            val mergeable = remote.filterNot { it.id in pendingIds }.map { row ->
                mergeRemoteAccount(row, cachedById[row.id], row.id in pendingTransactionAccounts)
            }
            accounts.upsertAll(mergeable)
        }
    }

    suspend fun saveAccount(draft: AccountDraft): Account = withContext(dispatchers.io) {
        database.withTransaction {
            val existing = draft.id?.let { accounts.getById(it) }
            checkCurrencyChangeAllowed(existing, draft)
            val entity = draft.toEntity(existing, existing?.id ?: nextLocalAccountId())
            accounts.upsert(entity)
            entity.toDomain()
        }
    }

    private fun mergeRemoteAccount(
        remote: AccountEntity,
        cached: AccountEntity?,
        hasPendingTransactions: Boolean,
    ): AccountEntity = remote.copy(
        balance = if (hasPendingTransactions) cached?.balance ?: remote.balance else remote.balance,
        syncBalance = if (hasPendingTransactions) remote.balance else remote.syncBalance,
        transactionSyncCursor = cached?.transactionSyncCursor,
    )

    private suspend fun checkCurrencyChangeAllowed(existing: AccountEntity?, draft: AccountDraft) {
        check(
            existing == null ||
                existing.currency == draft.currency.code ||
                !transactions.existsForAccount(existing.id),
        ) { "Account currency cannot change while transaction history exists" }
    }

    private fun AccountDraft.toEntity(existing: AccountEntity?, id: Int) = AccountEntity(
        id = id,
        name = name.trim(),
        emoji = emoji.trim().ifEmpty { "💳" },
        balance = balance.toPlainString(),
        syncBalance = resolveSyncBalance(
            existingBalance = existing?.balance?.let(::BigDecimal) ?: BigDecimal.ZERO,
            existingSyncBalance = existing?.syncBalance?.let(::BigDecimal) ?: BigDecimal.ZERO,
            newBalance = balance,
        ).toPlainString(),
        currency = currency.code,
        syncAction = existing?.syncAction?.afterLocalEdit() ?: SyncAction.CREATE,
        revision = (existing?.revision ?: 0) + 1,
        syncFailure = null,
        transactionSyncCursor = existing?.transactionSyncCursor,
    )

    private suspend fun nextLocalAccountId(): Int =
        (accounts.lowestId()?.coerceAtMost(0) ?: 0) - 1
}
