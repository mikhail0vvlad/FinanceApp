package ru.shmr.finance.data.local

import androidx.room.withTransaction
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext
import ru.shmr.finance.core.dispatchers.DefaultDispatcherProvider
import ru.shmr.finance.core.dispatchers.DispatcherProvider
import ru.shmr.finance.data.local.entity.AccountEntity
import ru.shmr.finance.data.local.entity.CategoryEntity
import ru.shmr.finance.data.local.entity.TransactionEntity
import ru.shmr.finance.data.local.entity.TransactionRecord
import ru.shmr.finance.data.sync.PendingAccount
import ru.shmr.finance.data.sync.PendingTransaction
import ru.shmr.finance.data.sync.SyncAction
import ru.shmr.finance.data.sync.SyncFailure
import ru.shmr.finance.data.sync.SyncQueue
import ru.shmr.finance.domain.model.Account
import ru.shmr.finance.domain.model.AccountDraft
import ru.shmr.finance.domain.model.Category
import ru.shmr.finance.domain.model.Money
import ru.shmr.finance.domain.model.Transaction
import ru.shmr.finance.domain.validation.TransactionDraft

internal class LocalFinanceDataSource(
    private val database: FinanceDatabase,
    private val dispatchers: DispatcherProvider = DefaultDispatcherProvider,
) : SyncQueue {

    private val accounts = database.accountDao()
    private val categories = database.categoryDao()
    private val transactions = database.transactionDao()

    fun observeAccounts(): Flow<List<Account>> =
        accounts.observeAll().map { rows -> rows.map(AccountEntity::toDomain) }

    fun observeCategories(): Flow<List<Category>> =
        categories.observeAll().map { rows -> rows.map(CategoryEntity::toDomain) }

    fun observeTransactions(
        startDate: LocalDate,
        endDate: LocalDate,
    ): Flow<List<Transaction>> =
        transactions.observeForPeriod(
            startInclusive = startDate.atStartOfDay().toString(),
            endExclusive = endDate.plusDays(1).atStartOfDay().toString(),
        ).map { rows -> rows.mapNotNull(TransactionRecord::toDomainOrNull) }

    suspend fun getAccounts(): List<Account> = withContext(dispatchers.io) {
        accounts.getAll().map(AccountEntity::toDomain)
    }

    suspend fun getCategories(): List<Category> = withContext(dispatchers.io) {
        categories.getAll().map(CategoryEntity::toDomain)
    }

    suspend fun getTransaction(localId: String): Transaction? = withContext(dispatchers.io) {
        transactions.getRecordByLocalId(localId)?.toDomainOrNull()
    }

    suspend fun hasTransactions(accountId: Int): Boolean = withContext(dispatchers.io) {
        transactions.existsForAccount(accountId)
    }

    suspend fun upsertRemoteAccounts(remote: List<AccountEntity>) = withContext(dispatchers.io) {
        database.withTransaction {
            val pendingIds = accounts.getPending().mapTo(mutableSetOf()) { it.id }
            val accountsWithPendingTransactions = transactions.pendingAccountIds().toSet()
            val cachedById = accounts.getAll().associateBy { it.id }
            accounts.upsertAll(
                remote
                    .filterNot { it.id in pendingIds }
                    .map { remoteAccount ->
                        if (remoteAccount.id in accountsWithPendingTransactions) {
                            remoteAccount.copy(
                                balance = cachedById[remoteAccount.id]?.balance
                                    ?: remoteAccount.balance,
                                syncBalance = remoteAccount.balance,
                                transactionSyncCursor = cachedById[remoteAccount.id]
                                    ?.transactionSyncCursor,
                            )
                        } else {
                            remoteAccount.copy(
                                transactionSyncCursor = cachedById[remoteAccount.id]
                                    ?.transactionSyncCursor,
                            )
                        }
                    },
            )
        }
    }

    suspend fun upsertCategories(remote: List<CategoryEntity>) = withContext(dispatchers.io) {
        categories.upsertAll(remote)
    }

    suspend fun replaceRemoteTransactions(
        accountId: Int,
        startDate: LocalDate,
        endDate: LocalDate,
        remote: List<TransactionEntity>,
    ) =
        withContext(dispatchers.io) {
            database.withTransaction {
                val serverIds = remote.map { requireNotNull(it.serverId) }
                val existingByServerId = serverIds
                    .chunked(SQLITE_QUERY_ID_CHUNK_SIZE)
                    .flatMap { transactions.getByServerIds(it) }
                    .associateBy { requireNotNull(it.serverId) }
                transactions.deleteSyncedForPeriod(
                    accountId = accountId,
                    startInclusive = startDate.atStartOfDay().toString(),
                    endExclusive = endDate.plusDays(1).atStartOfDay().toString(),
                )
                val rowsToUpsert = remote.mapNotNull { row ->
                    val serverId = requireNotNull(row.serverId)
                    val existing = existingByServerId[serverId]
                    if (existing?.syncAction == null || existing.syncAction == SyncAction.NONE) {
                        row.copy(
                            localId = existing?.localId ?: "remote-$serverId",
                            revision = existing?.revision ?: row.revision,
                        )
                    } else {
                        null
                    }
                }
                transactions.upsertAll(rowsToUpsert)
            }
        }

    suspend fun saveAccount(draft: AccountDraft): Account = withContext(dispatchers.io) {
        database.withTransaction {
            val existing = draft.id?.let { accounts.getById(it) }
            check(
                existing == null ||
                    existing.currency == draft.currency.code ||
                    !transactions.existsForAccount(existing.id),
            ) {
                "Account currency cannot change while transaction history exists"
            }
            val id = existing?.id ?: nextLocalAccountId()
            val action = existing?.syncAction?.afterLocalEdit() ?: SyncAction.CREATE
            val syncBalance = resolveSyncBalance(
                existingBalance = existing?.balance?.let(::BigDecimal) ?: BigDecimal.ZERO,
                existingSyncBalance = existing?.syncBalance?.let(::BigDecimal) ?: BigDecimal.ZERO,
                newBalance = draft.balance,
            )
            val entity = AccountEntity(
                id = id,
                name = draft.name.trim(),
                emoji = draft.emoji.trim().ifEmpty { "💳" },
                balance = draft.balance.toPlainString(),
                syncBalance = syncBalance.toPlainString(),
                currency = draft.currency.code,
                syncAction = action,
                revision = (existing?.revision ?: 0) + 1,
                syncFailure = null,
                transactionSyncCursor = existing?.transactionSyncCursor,
            )
            accounts.upsert(entity)
            entity.toDomain()
        }
    }

    suspend fun saveTransaction(
        draft: TransactionDraft,
        normalizedAmount: BigDecimal,
        normalizedComment: String?,
        existingLocalId: String?,
    ): Transaction = withContext(dispatchers.io) {
        database.withTransaction {
            val accountId = requireNotNull(draft.accountId)
            val categoryId = requireNotNull(draft.categoryId)
            val account = requireNotNull(accounts.getById(accountId))
            val category = requireNotNull(categories.getById(categoryId))
            val existing = existingLocalId?.let { transactions.getByLocalId(it) }
            val oldCategory = existing?.let { categories.getById(it.categoryId) }
            val oldAccount = existing?.let { accounts.getById(it.accountId) }

            val oldImpact = existing?.let {
                signedAmount(BigDecimal(it.amount), oldCategory?.isIncome == true)
            } ?: BigDecimal.ZERO
            val newImpact = signedAmount(normalizedAmount, category.isIncome)
            val newBalance = if (oldAccount?.id == account.id) {
                BigDecimal(account.balance) - oldImpact + newImpact
            } else {
                oldAccount?.let { previous ->
                    accounts.upsert(
                        previous.copy(
                            balance = (BigDecimal(previous.balance) - oldImpact).toPlainString(),
                        ),
                    )
                }
                BigDecimal(account.balance) + newImpact
            }
            val updatedAccount = account.copy(balance = newBalance.toPlainString())
            accounts.upsert(updatedAccount)

            val entity = TransactionEntity(
                localId = existing?.localId ?: UUID.randomUUID().toString(),
                serverId = existing?.serverId,
                accountId = accountId,
                categoryId = categoryId,
                amount = normalizedAmount.toPlainString(),
                transactionDate = LocalDateTime.of(draft.date, draft.time).toString(),
                comment = normalizedComment,
                syncAction = existing?.syncAction?.afterLocalEdit() ?: SyncAction.CREATE,
                revision = (existing?.revision ?: 0) + 1,
                syncFailure = null,
            )
            transactions.upsert(entity)
            TransactionRecord(entity, updatedAccount, category)
                .toDomainOrNull()
                ?: error("Saved transaction relations are missing")
        }
    }

    private suspend fun nextLocalAccountId(): Int {
        val lowest = accounts.lowestId()?.coerceAtMost(0) ?: 0
        return lowest - 1
    }

    override suspend fun pendingAccounts(): List<PendingAccount> =
        withContext(dispatchers.io) { accounts.getPending().map(AccountEntity::toPending) }

    override suspend fun pendingTransactions(): List<PendingTransaction> =
        withContext(dispatchers.io) { transactions.getPending().map(TransactionEntity::toPending) }

    override suspend fun replaceCreatedAccount(sent: PendingAccount, remote: PendingAccount) {
        withContext(dispatchers.io) {
            database.withTransaction {
                val localAccount = accounts.getById(sent.id) ?: return@withTransaction
                val replacement = if (localAccount.revision == sent.revision) {
                    remote.toEntity().copy(
                        balance = localAccount.balance,
                        syncBalance = remote.balance,
                        syncAction = SyncAction.NONE,
                        revision = localAccount.revision,
                        syncFailure = null,
                        transactionSyncCursor = localAccount.transactionSyncCursor,
                    )
                } else {
                    localAccount.copy(
                        id = remote.id,
                        syncAction = SyncAction.UPDATE,
                        syncFailure = null,
                    )
                }
                accounts.upsert(replacement)
                transactions.reassignAccount(sent.id, remote.id)
                accounts.deleteById(sent.id)
            }
        }
    }

    override suspend fun markAccountSynced(sent: PendingAccount, remote: PendingAccount) {
        withContext(dispatchers.io) {
            database.withTransaction {
                val localAccount = accounts.getById(sent.id) ?: return@withTransaction
                if (localAccount.revision != sent.revision) return@withTransaction
                accounts.upsert(
                    remote.toEntity().copy(
                        balance = localAccount.balance,
                        syncBalance = remote.balance,
                        syncAction = SyncAction.NONE,
                        revision = localAccount.revision,
                        syncFailure = null,
                        transactionSyncCursor = localAccount.transactionSyncCursor,
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
                val existing = transactions.getByLocalId(sent.localId) ?: return@withTransaction
                val replacement = if (existing.revision == sent.revision) {
                    existing.copy(
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
                    existing.copy(
                        serverId = remote.serverId,
                        syncAction = SyncAction.UPDATE,
                        syncFailure = null,
                    )
                }
                transactions.upsert(replacement)
            }
        }
    }

    override suspend fun markTransactionSynced(
        sent: PendingTransaction,
        remote: PendingTransaction,
    ) {
        withContext(dispatchers.io) {
            database.withTransaction {
                val existing = transactions.getByLocalId(sent.localId) ?: return@withTransaction
                if (existing.revision != sent.revision) return@withTransaction
                transactions.upsert(
                    existing.copy(
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
            database.withTransaction {
                val current = accounts.getById(sent.id) ?: return@withTransaction
                if (current.revision == sent.revision) {
                    accounts.upsert(current.copy(syncFailure = SyncFailure.ITEM_PERMANENT))
                }
            }
        }
    }

    override suspend fun markTransactionFailed(sent: PendingTransaction) {
        withContext(dispatchers.io) {
            database.withTransaction {
                val current = transactions.getByLocalId(sent.localId) ?: return@withTransaction
                if (current.revision == sent.revision) {
                    transactions.upsert(current.copy(syncFailure = SyncFailure.ITEM_PERMANENT))
                }
            }
        }
    }

    suspend fun transactionSyncStartDate(accountId: Int): LocalDate = withContext(dispatchers.io) {
        accounts.getById(accountId)
            ?.transactionSyncCursor
            ?.let(LocalDate::parse)
            ?.minusDays(SYNC_OVERLAP_DAYS)
            ?: FULL_HISTORY_START
    }

    suspend fun markTransactionsSyncedThrough(accountId: Int, endDate: LocalDate) {
        withContext(dispatchers.io) {
            database.withTransaction {
                val account = accounts.getById(accountId) ?: return@withTransaction
                accounts.upsert(account.copy(transactionSyncCursor = endDate.toString()))
            }
        }
    }
}

private fun signedAmount(amount: BigDecimal, isIncome: Boolean): BigDecimal =
    if (isIncome) amount else amount.negate()

fun AccountEntity.toDomain() = Account(
    id = id,
    name = name,
    balance = Money.parse(balance, currency),
    emoji = emoji,
    isPending = syncAction != SyncAction.NONE,
    syncFailed = syncFailure != null,
)

fun CategoryEntity.toDomain() = Category(
    id = id,
    name = name,
    emoji = emoji,
    isIncome = isIncome,
)

fun TransactionRecord.toDomainOrNull(): Transaction? {
    val account = account ?: return null
    val category = category ?: return null
    val row = transaction
    return Transaction(
        id = row.serverId ?: row.localId.hashCode(),
        accountId = account.id,
        accountName = account.name,
        category = category.toDomain(),
        comment = row.comment,
        amount = Money.parse(row.amount, account.currency),
        dateTime = LocalDateTime.parse(row.transactionDate),
        localId = row.localId,
        serverId = row.serverId,
        isPending = row.syncAction != SyncAction.NONE,
        syncFailed = row.syncFailure != null,
    )
}

fun AccountEntity.toPending() = PendingAccount(
    id = id,
    name = name,
    emoji = emoji,
    balance = syncBalance,
    currency = currency,
    action = syncAction,
    revision = revision,
    failure = syncFailure,
)

fun PendingAccount.toEntity() = AccountEntity(
    id = id,
    name = name,
    emoji = emoji,
    balance = balance,
    syncBalance = balance,
    currency = currency,
    syncAction = action,
    revision = revision,
    syncFailure = failure,
)

fun TransactionEntity.toPending() = PendingTransaction(
    localId = localId,
    serverId = serverId,
    accountId = accountId,
    categoryId = categoryId,
    amount = amount,
    transactionDate = LocalDateTime.parse(transactionDate),
    comment = comment,
    action = syncAction,
    revision = revision,
    failure = syncFailure,
)

private const val SYNC_OVERLAP_DAYS = 7L
private const val SQLITE_QUERY_ID_CHUNK_SIZE = 900
private val FULL_HISTORY_START: LocalDate = LocalDate.of(1970, 1, 1)
