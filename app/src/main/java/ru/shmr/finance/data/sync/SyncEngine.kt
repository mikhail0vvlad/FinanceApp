package ru.shmr.finance.data.sync

import java.time.LocalDateTime

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

data class PendingAccount(
    val id: Int,
    val name: String,
    val emoji: String,
    val balance: String,
    val currency: String,
    val action: SyncAction,
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
    RETRY,
    FAILURE,
}

interface SyncQueue {
    suspend fun pendingAccounts(): List<PendingAccount>
    suspend fun pendingTransactions(): List<PendingTransaction>
    suspend fun replaceCreatedAccount(localId: Int, remote: PendingAccount)
    suspend fun markAccountSynced(remote: PendingAccount)
    suspend fun replaceCreatedTransaction(localId: String, remote: PendingTransaction)
    suspend fun markTransactionSynced(remote: PendingTransaction)
}

interface SyncRemoteGateway {
    suspend fun createAccount(account: PendingAccount): SyncCallResult<PendingAccount>
    suspend fun updateAccount(account: PendingAccount): SyncCallResult<PendingAccount>
    suspend fun createTransaction(transaction: PendingTransaction): SyncCallResult<PendingTransaction>
    suspend fun updateTransaction(transaction: PendingTransaction): SyncCallResult<PendingTransaction>
}

class SyncEngine(
    private val queue: SyncQueue,
    private val remote: SyncRemoteGateway,
) {

    suspend fun sync(): SyncOutcome {
        var hadItemFailure = false
        val blockedAccountIds = mutableSetOf<Int>()

        for (account in queue.pendingAccounts().sortedBy { it.id }) {
            val result = when (account.action) {
                SyncAction.CREATE -> remote.createAccount(account)
                SyncAction.UPDATE -> remote.updateAccount(account)
                SyncAction.NONE -> continue
            }
            when (result) {
                is SyncCallResult.Success -> when (account.action) {
                    SyncAction.CREATE -> queue.replaceCreatedAccount(account.id, result.value)
                    SyncAction.UPDATE -> queue.markAccountSynced(result.value)
                    SyncAction.NONE -> Unit
                }
                SyncCallResult.RetryableFailure -> return SyncOutcome.RETRY
                SyncCallResult.GlobalFatalFailure -> return SyncOutcome.FAILURE
                SyncCallResult.ItemPermanentFailure -> {
                    hadItemFailure = true
                    if (account.action == SyncAction.CREATE) blockedAccountIds += account.id
                }
            }
        }

        for (transaction in queue.pendingTransactions().sortedBy { it.transactionDate }) {
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
                    SyncAction.CREATE -> queue.replaceCreatedTransaction(
                        transaction.localId,
                        result.value,
                    )
                    SyncAction.UPDATE -> queue.markTransactionSynced(result.value)
                    SyncAction.NONE -> Unit
                }
                SyncCallResult.RetryableFailure -> return SyncOutcome.RETRY
                SyncCallResult.GlobalFatalFailure -> return SyncOutcome.FAILURE
                SyncCallResult.ItemPermanentFailure -> hadItemFailure = true
            }
        }
        return if (hadItemFailure) SyncOutcome.FAILURE else SyncOutcome.SUCCESS
    }
}
