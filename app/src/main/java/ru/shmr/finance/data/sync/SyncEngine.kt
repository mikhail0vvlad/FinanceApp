package ru.shmr.finance.data.sync

import java.time.LocalDateTime
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

enum class SyncAction {
    NONE,
    CREATE,
    UPDATE,
    ;

    fun afterLocalEdit(): SyncAction = when (this) {
        CREATE -> CREATE
        NONE, UPDATE -> UPDATE
    }
}

enum class SyncFailure {
    ITEM_PERMANENT,
}

data class PendingAccount(
    val id: Int,
    val name: String,
    val emoji: String,
    val balance: String,
    val currency: String,
    val action: SyncAction,
    val revision: Long,
    val failure: SyncFailure?,
)

data class PendingTransaction(
    val localId: String,
    val serverId: Int?,
    val accountId: Int,
    val categoryId: Int,
    val amount: String,
    val transactionDate: LocalDateTime,
    val comment: String?,
    val action: SyncAction,
    val revision: Long,
    val failure: SyncFailure?,
)

sealed interface SyncCallResult<out T> {
    data class Success<T>(val value: T) : SyncCallResult<T>
    data object RetryableFailure : SyncCallResult<Nothing>

    /** Stops the whole sync run: no further remote calls are useful (e.g. auth is broken). */
    data object GlobalFatalFailure : SyncCallResult<Nothing>

    /** Only this item is broken; independent pending items must still be processed. */
    data object ItemPermanentFailure : SyncCallResult<Nothing>
}

enum class SyncOutcome {
    SUCCESS,
    PARTIAL_FAILURE,
    RETRY,
    FAILURE,
}

interface SyncQueue {
    suspend fun pendingAccounts(): List<PendingAccount>
    suspend fun pendingTransactions(): List<PendingTransaction>
    suspend fun replaceCreatedAccount(sent: PendingAccount, remote: PendingAccount)
    suspend fun markAccountSynced(sent: PendingAccount, remote: PendingAccount)
    suspend fun replaceCreatedTransaction(sent: PendingTransaction, remote: PendingTransaction)
    suspend fun markTransactionSynced(sent: PendingTransaction, remote: PendingTransaction)
    suspend fun markAccountFailed(sent: PendingAccount)
    suspend fun markTransactionFailed(sent: PendingTransaction)
}

interface SyncRemoteGateway {
    suspend fun createAccount(account: PendingAccount): SyncCallResult<PendingAccount>
    suspend fun updateAccount(account: PendingAccount): SyncCallResult<PendingAccount>
    suspend fun createTransaction(transaction: PendingTransaction): SyncCallResult<PendingTransaction>
    suspend fun updateTransaction(transaction: PendingTransaction): SyncCallResult<PendingTransaction>
}

internal class SyncEngine(
    private val queue: SyncQueue,
    private val remote: SyncRemoteGateway,
) {

    suspend fun sync(): SyncOutcome {
        var hadItemFailure = false
        val blockedAccountIds = mutableSetOf<Int>()

        for (account in queue.pendingAccounts().sortedBy { it.id }) {
            if (account.failure != null) {
                hadItemFailure = true
                if (account.action == SyncAction.CREATE) blockedAccountIds += account.id
                continue
            }
            val result = when (account.action) {
                SyncAction.CREATE -> remote.createAccount(account)
                SyncAction.UPDATE -> remote.updateAccount(account)
                SyncAction.NONE -> continue
            }
            when (result) {
                is SyncCallResult.Success -> when (account.action) {
                    SyncAction.CREATE -> queue.replaceCreatedAccount(account, result.value)
                    SyncAction.UPDATE -> queue.markAccountSynced(account, result.value)
                    SyncAction.NONE -> Unit
                }
                SyncCallResult.RetryableFailure -> return SyncOutcome.RETRY
                SyncCallResult.GlobalFatalFailure -> return SyncOutcome.FAILURE
                SyncCallResult.ItemPermanentFailure -> {
                    hadItemFailure = true
                    queue.markAccountFailed(account)
                    if (account.action == SyncAction.CREATE) blockedAccountIds += account.id
                }
            }
        }

        for (transaction in queue.pendingTransactions().sortedBy { it.transactionDate }) {
            if (transaction.failure != null) {
                hadItemFailure = true
                continue
            }
            if (transaction.accountId in blockedAccountIds) {
                hadItemFailure = true
                continue
            }
            val result = when (transaction.action) {
                SyncAction.CREATE -> remote.createTransaction(transaction)
                SyncAction.UPDATE -> remote.updateTransaction(transaction)
                SyncAction.NONE -> continue
            }
            when (result) {
                is SyncCallResult.Success -> when (transaction.action) {
                    SyncAction.CREATE -> queue.replaceCreatedTransaction(transaction, result.value)
                    SyncAction.UPDATE -> queue.markTransactionSynced(transaction, result.value)
                    SyncAction.NONE -> Unit
                }
                SyncCallResult.RetryableFailure -> return SyncOutcome.RETRY
                SyncCallResult.GlobalFatalFailure -> return SyncOutcome.FAILURE
                SyncCallResult.ItemPermanentFailure -> {
                    hadItemFailure = true
                    queue.markTransactionFailed(transaction)
                }
            }
        }
        return if (hadItemFailure) SyncOutcome.PARTIAL_FAILURE else SyncOutcome.SUCCESS
    }
}

/** Serializes every entry into the full sync pipeline inside this app process. */
internal class SyncCoordinator {
    private val mutex = Mutex()

    suspend fun run(block: suspend () -> SyncOutcome): SyncOutcome = mutex.withLock { block() }
}
