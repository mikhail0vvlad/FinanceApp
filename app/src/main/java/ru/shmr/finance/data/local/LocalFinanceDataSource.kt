package ru.shmr.finance.data.local

import androidx.room.withTransaction
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext
import ru.shmr.finance.data.local.entity.AccountEntity
import ru.shmr.finance.data.local.entity.CategoryEntity
import ru.shmr.finance.data.local.entity.TransactionEntity
import ru.shmr.finance.data.local.entity.TransactionRecord
import ru.shmr.finance.data.sync.PendingAccount
import ru.shmr.finance.data.sync.PendingTransaction
import ru.shmr.finance.data.sync.SyncAction
import ru.shmr.finance.data.sync.SyncQueue
import ru.shmr.finance.domain.model.Account
import ru.shmr.finance.domain.model.AccountDraft
import ru.shmr.finance.domain.model.Category
import ru.shmr.finance.domain.model.Money
import ru.shmr.finance.domain.model.Transaction
import ru.shmr.finance.domain.validation.TransactionDraft

class LocalFinanceDataSource(
    private val database: FinanceDatabase,
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

    fun observeTransactions(
        accountId: Int,
        startDate: LocalDate,
        endDate: LocalDate,
    ): Flow<List<Transaction>> =
        transactions.observeForAccountPeriod(
            accountId = accountId,
            startInclusive = startDate.atStartOfDay().toString(),
            endExclusive = endDate.plusDays(1).atStartOfDay().toString(),
        ).map { rows -> rows.mapNotNull(TransactionRecord::toDomainOrNull) }

    suspend fun getAccounts(): List<Account> = withContext(Dispatchers.IO) {
        accounts.getAll().map(AccountEntity::toDomain)
    }

    suspend fun getCategories(): List<Category> = withContext(Dispatchers.IO) {
        categories.getAll().map(CategoryEntity::toDomain)
    }

    suspend fun getTransactions(
        accountId: Int,
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<Transaction> = withContext(Dispatchers.IO) {
        transactions.getForAccountPeriod(
            accountId = accountId,
            startInclusive = startDate.atStartOfDay().toString(),
            endExclusive = endDate.plusDays(1).atStartOfDay().toString(),
        ).mapNotNull(TransactionRecord::toDomainOrNull)
    }

    suspend fun getTransaction(localId: String): Transaction? = withContext(Dispatchers.IO) {
        transactions.getRecordByLocalId(localId)?.toDomainOrNull()
    }

    suspend fun hasTransactions(accountId: Int): Boolean = withContext(Dispatchers.IO) {
        transactions.existsForAccount(accountId)
    }

    suspend fun upsertRemoteAccounts(remote: List<AccountEntity>) = withContext(Dispatchers.IO) {
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
                            )
                        } else {
                            remoteAccount
                        }
                    },
            )
        }
    }

    suspend fun upsertCategories(remote: List<CategoryEntity>) = withContext(Dispatchers.IO) {
        categories.upsertAll(remote)
    }

    suspend fun replaceRemoteTransactions(
        accountId: Int,
        startDate: LocalDate,
        endDate: LocalDate,
        remote: List<TransactionEntity>,
    ) =
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val existingByServerId = remote.associate { row ->
                    val serverId = requireNotNull(row.serverId)
                    serverId to transactions.getByServerId(serverId)
                }
                transactions.deleteSyncedForPeriod(
                    accountId = accountId,
                    startInclusive = startDate.atStartOfDay().toString(),
                    endExclusive = endDate.plusDays(1).atStartOfDay().toString(),
                )
                remote.forEach { row ->
                    val serverId = requireNotNull(row.serverId)
                    val existing = existingByServerId[serverId]
                    if (existing?.syncAction == null || existing.syncAction == SyncAction.NONE) {
                        transactions.upsert(
                            row.copy(localId = existing?.localId ?: "remote-$serverId"),
                        )
                    }
                }
            }
        }

    suspend fun saveAccount(draft: AccountDraft): Account = withContext(Dispatchers.IO) {
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
            val entity = AccountEntity(
                id = id,
                name = draft.name.trim(),
                emoji = draft.emoji.trim().ifEmpty { "💳" },
                balance = draft.balance.toPlainString(),
                syncBalance = draft.balance.toPlainString(),
                currency = draft.currency.code,
                syncAction = action,
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
    ): Transaction = withContext(Dispatchers.IO) {
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
        withContext(Dispatchers.IO) { accounts.getPending().map(AccountEntity::toPending) }

    override suspend fun pendingTransactions(): List<PendingTransaction> =
        withContext(Dispatchers.IO) { transactions.getPending().map(TransactionEntity::toPending) }

    override suspend fun replaceCreatedAccount(localId: Int, remote: PendingAccount) {
        withContext(Dispatchers.IO) {
            database.withTransaction {
                val localAccount = accounts.getById(localId)
                accounts.upsert(
                    remote.toEntity().copy(
                        balance = localAccount?.balance ?: remote.balance,
                        syncBalance = remote.balance,
                        syncAction = SyncAction.NONE,
                    ),
                )
                transactions.reassignAccount(localId, remote.id)
                accounts.deleteById(localId)
            }
        }
    }

    override suspend fun markAccountSynced(remote: PendingAccount) {
        withContext(Dispatchers.IO) {
            val localAccount = accounts.getById(remote.id)
            accounts.upsert(
                remote.toEntity().copy(
                    balance = localAccount?.balance ?: remote.balance,
                    syncBalance = remote.balance,
                    syncAction = SyncAction.NONE,
                ),
            )
        }
    }

    override suspend fun replaceCreatedTransaction(
        localId: String,
        remote: PendingTransaction,
    ) {
        withContext(Dispatchers.IO) {
            val existing = requireNotNull(transactions.getByLocalId(localId))
            transactions.upsert(
                existing.copy(
                    serverId = remote.serverId,
                    accountId = remote.accountId,
                    categoryId = remote.categoryId,
                    amount = remote.amount,
                    transactionDate = remote.transactionDate.toString(),
                    comment = remote.comment,
                    syncAction = SyncAction.NONE,
                ),
            )
        }
    }

    override suspend fun markTransactionSynced(remote: PendingTransaction) {
        withContext(Dispatchers.IO) {
            val serverId = requireNotNull(remote.serverId)
            val existing = requireNotNull(transactions.getByServerId(serverId))
            transactions.upsert(
                existing.copy(
                    accountId = remote.accountId,
                    categoryId = remote.categoryId,
                    amount = remote.amount,
                    transactionDate = remote.transactionDate.toString(),
                    comment = remote.comment,
                    syncAction = SyncAction.NONE,
                ),
            )
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
    )
}

fun AccountEntity.toPending() = PendingAccount(
    id = id,
    name = name,
    emoji = emoji,
    balance = syncBalance,
    currency = currency,
    action = syncAction,
)

fun PendingAccount.toEntity() = AccountEntity(
    id = id,
    name = name,
    emoji = emoji,
    balance = balance,
    syncBalance = balance,
    currency = currency,
    syncAction = action,
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
)
