package ru.shmr.finance.data.local

import androidx.room.withTransaction
import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.withContext
import ru.shmr.finance.core.dispatchers.DispatcherProvider
import ru.shmr.finance.data.local.entity.TransactionEntity
import ru.shmr.finance.data.local.entity.TransactionRecord
import ru.shmr.finance.data.sync.SyncAction
import ru.shmr.finance.domain.model.Transaction
import ru.shmr.finance.domain.validation.TransactionDraft

/** Owns cached transaction queries, remote period replacement, and local edits. */
internal class TransactionLocalDataSource(
    private val database: FinanceDatabase,
    private val dispatchers: DispatcherProvider,
) {
    private val accounts = database.accountDao()
    private val categories = database.categoryDao()
    private val transactions = database.transactionDao()

    fun observeTransactions(startDate: LocalDate, endDate: LocalDate): Flow<List<Transaction>> =
        transactions.observeForPeriod(
            startInclusive = startDate.atStartOfDay().toString(),
            endExclusive = endDate.plusDays(1).atStartOfDay().toString(),
        ).map { rows -> rows.mapNotNull(TransactionRecord::toDomainOrNull) }

    suspend fun getTransaction(localId: String): Transaction? = withContext(dispatchers.io) {
        transactions.getRecordByLocalId(localId)?.toDomainOrNull()
    }

    suspend fun replaceRemoteTransactions(
        accountId: Int,
        startDate: LocalDate,
        endDate: LocalDate,
        remote: List<TransactionEntity>,
    ) = withContext(dispatchers.io) {
        database.withTransaction {
            val existingByServerId = loadExistingRemoteRows(remote)
            transactions.deleteSyncedForPeriod(
                accountId = accountId,
                startInclusive = startDate.atStartOfDay().toString(),
                endExclusive = endDate.plusDays(1).atStartOfDay().toString(),
            )
            transactions.upsertAll(remote.mapNotNull { mergeRemoteRow(it, existingByServerId) })
        }
    }

    suspend fun saveTransaction(
        draft: TransactionDraft,
        normalizedAmount: BigDecimal,
        normalizedComment: String?,
        existingLocalId: String?,
    ): Transaction = withContext(dispatchers.io) {
        database.withTransaction {
            val account = requireNotNull(accounts.getById(requireNotNull(draft.accountId)))
            val category = requireNotNull(categories.getById(requireNotNull(draft.categoryId)))
            val existing = existingLocalId?.let { transactions.getByLocalId(it) }
            val oldCategory = existing?.let { categories.getById(it.categoryId) }
            val oldAccount = existing?.let { accounts.getById(it.accountId) }
            val updatedAccount = updateAffectedBalances(
                account = account,
                oldAccount = oldAccount,
                oldImpact = existing?.signedImpact(oldCategory?.isIncome == true) ?: BigDecimal.ZERO,
                newImpact = signedAmount(normalizedAmount, category.isIncome),
            )
            val entity = draft.toEntity(existing, normalizedAmount, normalizedComment)
            transactions.upsert(entity)
            TransactionRecord(entity, updatedAccount, category).toDomainOrNull()
                ?: error("Saved transaction relations are missing")
        }
    }

    private suspend fun loadExistingRemoteRows(
        remote: List<TransactionEntity>,
    ): Map<Int, TransactionEntity> = remote.map { requireNotNull(it.serverId) }
        .chunked(SQLITE_QUERY_ID_CHUNK_SIZE)
        .flatMap { transactions.getByServerIds(it) }
        .associateBy { requireNotNull(it.serverId) }

    private fun mergeRemoteRow(
        remote: TransactionEntity,
        existingByServerId: Map<Int, TransactionEntity>,
    ): TransactionEntity? {
        val serverId = requireNotNull(remote.serverId)
        val existing = existingByServerId[serverId]
        if (existing?.syncAction != null && existing.syncAction != SyncAction.NONE) return null
        return remote.copy(
            localId = existing?.localId ?: "remote-$serverId",
            revision = existing?.revision ?: remote.revision,
        )
    }

    private suspend fun updateAffectedBalances(
        account: ru.shmr.finance.data.local.entity.AccountEntity,
        oldAccount: ru.shmr.finance.data.local.entity.AccountEntity?,
        oldImpact: BigDecimal,
        newImpact: BigDecimal,
    ): ru.shmr.finance.data.local.entity.AccountEntity {
        if (oldAccount?.id != null && oldAccount.id != account.id) {
            accounts.upsert(
                oldAccount.copy(
                    balance = (BigDecimal(oldAccount.balance) - oldImpact).toPlainString(),
                ),
            )
        }
        val balance = if (oldAccount?.id == account.id) {
            BigDecimal(account.balance) - oldImpact + newImpact
        } else {
            BigDecimal(account.balance) + newImpact
        }
        return account.copy(balance = balance.toPlainString()).also { accounts.upsert(it) }
    }

    private fun TransactionEntity.signedImpact(isIncome: Boolean): BigDecimal =
        signedAmount(BigDecimal(amount), isIncome)

    private fun TransactionDraft.toEntity(
        existing: TransactionEntity?,
        normalizedAmount: BigDecimal,
        normalizedComment: String?,
    ) = TransactionEntity(
        localId = existing?.localId ?: UUID.randomUUID().toString(),
        serverId = existing?.serverId,
        accountId = requireNotNull(accountId),
        categoryId = requireNotNull(categoryId),
        amount = normalizedAmount.toPlainString(),
        transactionDate = LocalDateTime.of(date, time).toString(),
        comment = normalizedComment,
        syncAction = existing?.syncAction?.afterLocalEdit() ?: SyncAction.CREATE,
        revision = (existing?.revision ?: 0) + 1,
        syncFailure = null,
    )
}

private fun signedAmount(amount: BigDecimal, isIncome: Boolean): BigDecimal =
    if (isIncome) amount else amount.negate()

private const val SQLITE_QUERY_ID_CHUNK_SIZE = 900
