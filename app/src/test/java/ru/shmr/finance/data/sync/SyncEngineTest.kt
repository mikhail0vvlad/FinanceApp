package ru.shmr.finance.data.sync

import java.time.LocalDateTime
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SyncEngineTest {

    @Test
    fun `created account is remapped before dependent transaction is sent`() = runTest {
        val queue = FakeSyncQueue(
            accounts = mutableListOf(account(id = -1, action = SyncAction.CREATE)),
            transactions = mutableListOf(
                transaction(
                    localId = "local-transaction",
                    serverId = null,
                    accountId = -1,
                    action = SyncAction.CREATE,
                ),
            ),
        )
        val remote = FakeSyncRemote()
        val engine = SyncEngine(queue, remote)

        assertEquals(SyncOutcome.SUCCESS, engine.sync())

        assertEquals(101, remote.createdTransactions.single().accountId)
        assertTrue(queue.accounts.single().id == 101)
        assertEquals(SyncAction.NONE, queue.accounts.single().action)
        assertEquals(501, queue.transactions.single().serverId)
        assertEquals(SyncAction.NONE, queue.transactions.single().action)
    }

    @Test
    fun `retryable failure keeps pending change`() = runTest {
        val pending = transaction(
            localId = "pending",
            serverId = null,
            accountId = 1,
            action = SyncAction.CREATE,
        )
        val queue = FakeSyncQueue(transactions = mutableListOf(pending))
        val remote = FakeSyncRemote(
            transactionCreateResult = { SyncCallResult.RetryableFailure },
        )

        val outcome = SyncEngine(queue, remote).sync()

        assertEquals(SyncOutcome.RETRY, outcome)
        assertEquals(SyncAction.CREATE, queue.transactions.single().action)
        assertFalse(queue.transactions.single().serverId != null)
    }

    @Test
    fun `item-permanent failure on one transaction does not block an independent transaction`() = runTest {
        val queue = FakeSyncQueue(
            transactions = mutableListOf(
                transaction(localId = "broken", serverId = null, accountId = 1, action = SyncAction.CREATE),
                transaction(localId = "healthy", serverId = null, accountId = 1, action = SyncAction.CREATE),
            ),
        )
        val remote = FakeSyncRemote(
            transactionCreateResult = { tx ->
                if (tx.localId == "broken") {
                    SyncCallResult.ItemPermanentFailure
                } else {
                    SyncCallResult.Success(tx.copy(serverId = 501, action = SyncAction.NONE))
                }
            },
        )

        val outcome = SyncEngine(queue, remote).sync()

        assertEquals(SyncOutcome.FAILURE, outcome)
        val broken = queue.transactions.single { it.localId == "broken" }
        assertEquals(SyncAction.CREATE, broken.action)
        assertEquals(null, broken.serverId)
        val healthy = queue.transactions.single { it.localId == "healthy" }
        assertEquals(SyncAction.NONE, healthy.action)
        assertEquals(501, healthy.serverId)
    }

    @Test
    fun `global fatal failure stops sync before any further remote calls`() = runTest {
        val queue = FakeSyncQueue(
            accounts = mutableListOf(account(id = -1, action = SyncAction.CREATE)),
            transactions = mutableListOf(
                transaction(localId = "dependent", serverId = null, accountId = -1, action = SyncAction.CREATE),
            ),
        )
        val remote = FakeSyncRemote(
            accountCreateResult = { SyncCallResult.GlobalFatalFailure },
        )

        val outcome = SyncEngine(queue, remote).sync()

        assertEquals(SyncOutcome.FAILURE, outcome)
        assertTrue(remote.attemptedTransactionLocalIds.isEmpty())
        assertEquals(SyncAction.CREATE, queue.accounts.single().action)
        assertEquals(SyncAction.CREATE, queue.transactions.single().action)
    }

    @Test
    fun `item-permanent account create failure blocks only its dependent transaction`() = runTest {
        val queue = FakeSyncQueue(
            accounts = mutableListOf(account(id = -1, action = SyncAction.CREATE)),
            transactions = mutableListOf(
                transaction(localId = "blocked", serverId = null, accountId = -1, action = SyncAction.CREATE),
                transaction(localId = "other", serverId = null, accountId = 5, action = SyncAction.CREATE),
            ),
        )
        val remote = FakeSyncRemote(
            accountCreateResult = { SyncCallResult.ItemPermanentFailure },
        )

        val outcome = SyncEngine(queue, remote).sync()

        assertEquals(SyncOutcome.FAILURE, outcome)
        assertEquals(listOf("other"), remote.attemptedTransactionLocalIds)
        val blocked = queue.transactions.single { it.localId == "blocked" }
        assertEquals(SyncAction.CREATE, blocked.action)
        assertEquals(null, blocked.serverId)
        val other = queue.transactions.single { it.localId == "other" }
        assertEquals(SyncAction.NONE, other.action)
        assertEquals(501, other.serverId)
        assertEquals(SyncAction.CREATE, queue.accounts.single().action)
    }

    @Test
    fun `permanent failure in one run does not starve an independent item in the next run`() = runTest {
        val queue = FakeSyncQueue(
            transactions = mutableListOf(
                transaction(localId = "always-broken", serverId = null, accountId = 1, action = SyncAction.CREATE),
            ),
        )
        val remote = FakeSyncRemote(
            transactionCreateResult = { tx ->
                if (tx.localId == "always-broken") {
                    SyncCallResult.ItemPermanentFailure
                } else {
                    SyncCallResult.Success(tx.copy(serverId = 501, action = SyncAction.NONE))
                }
            },
        )
        val engine = SyncEngine(queue, remote)

        assertEquals(SyncOutcome.FAILURE, engine.sync())

        queue.transactions += transaction(
            localId = "new-in-next-run",
            serverId = null,
            accountId = 1,
            action = SyncAction.CREATE,
        )

        assertEquals(SyncOutcome.FAILURE, engine.sync())
        val stillBroken = queue.transactions.single { it.localId == "always-broken" }
        assertEquals(SyncAction.CREATE, stillBroken.action)
        val newItem = queue.transactions.single { it.localId == "new-in-next-run" }
        assertEquals(SyncAction.NONE, newItem.action)
        assertEquals(501, newItem.serverId)
    }

    @Test
    fun `remote transaction update clears pending action`() = runTest {
        val queue = FakeSyncQueue(
            transactions = mutableListOf(
                transaction(
                    localId = "remote-9",
                    serverId = 9,
                    accountId = 1,
                    action = SyncAction.UPDATE,
                ),
            ),
        )

        val outcome = SyncEngine(queue, FakeSyncRemote()).sync()

        assertEquals(SyncOutcome.SUCCESS, outcome)
        assertEquals(SyncAction.NONE, queue.transactions.single().action)
        assertEquals(9, queue.transactions.single().serverId)
    }

    private fun account(id: Int, action: SyncAction) = PendingAccount(
        id = id,
        name = "Основной",
        emoji = "💳",
        balance = "1000.00",
        currency = "RUB",
        action = action,
    )

    private fun transaction(
        localId: String,
        serverId: Int?,
        accountId: Int,
        action: SyncAction,
    ) = PendingTransaction(
        localId = localId,
        serverId = serverId,
        accountId = accountId,
        categoryId = 3,
        amount = "125.50",
        transactionDate = LocalDateTime.of(2026, 7, 24, 18, 30),
        comment = null,
        action = action,
    )
}

private class FakeSyncQueue(
    val accounts: MutableList<PendingAccount> = mutableListOf(),
    val transactions: MutableList<PendingTransaction> = mutableListOf(),
) : SyncQueue {

    override suspend fun pendingAccounts(): List<PendingAccount> =
        accounts.filter { it.action != SyncAction.NONE }

    override suspend fun pendingTransactions(): List<PendingTransaction> =
        transactions.filter { it.action != SyncAction.NONE }

    override suspend fun replaceCreatedAccount(localId: Int, remote: PendingAccount) {
        val index = accounts.indexOfFirst { it.id == localId }
        accounts[index] = remote.copy(action = SyncAction.NONE)
        transactions.replaceAll { transaction ->
            if (transaction.accountId == localId) {
                transaction.copy(accountId = remote.id)
            } else {
                transaction
            }
        }
    }

    override suspend fun markAccountSynced(remote: PendingAccount) {
        val index = accounts.indexOfFirst { it.id == remote.id }
        accounts[index] = remote.copy(action = SyncAction.NONE)
    }

    override suspend fun replaceCreatedTransaction(
        localId: String,
        remote: PendingTransaction,
    ) {
        val index = transactions.indexOfFirst { it.localId == localId }
        transactions[index] = remote.copy(localId = localId, action = SyncAction.NONE)
    }

    override suspend fun markTransactionSynced(remote: PendingTransaction) {
        val index = transactions.indexOfFirst { it.serverId == remote.serverId }
        transactions[index] = remote.copy(
            localId = transactions[index].localId,
            action = SyncAction.NONE,
        )
    }
}

private class FakeSyncRemote(
    private val accountCreateResult: (PendingAccount) -> SyncCallResult<PendingAccount> =
        { account -> SyncCallResult.Success(account.copy(id = 101, action = SyncAction.NONE)) },
    private val transactionCreateResult: (PendingTransaction) -> SyncCallResult<PendingTransaction> =
        { tx -> SyncCallResult.Success(tx.copy(serverId = 501, action = SyncAction.NONE)) },
) : SyncRemoteGateway {

    val createdTransactions = mutableListOf<PendingTransaction>()
    val attemptedTransactionLocalIds = mutableListOf<String>()

    override suspend fun createAccount(account: PendingAccount): SyncCallResult<PendingAccount> =
        accountCreateResult(account)

    override suspend fun updateAccount(account: PendingAccount): SyncCallResult<PendingAccount> =
        SyncCallResult.Success(account.copy(action = SyncAction.NONE))

    override suspend fun createTransaction(
        transaction: PendingTransaction,
    ): SyncCallResult<PendingTransaction> {
        attemptedTransactionLocalIds += transaction.localId
        val result = transactionCreateResult(transaction)
        if (result is SyncCallResult.Success) createdTransactions += transaction
        return result
    }

    override suspend fun updateTransaction(
        transaction: PendingTransaction,
    ): SyncCallResult<PendingTransaction> =
        SyncCallResult.Success(transaction.copy(action = SyncAction.NONE))
}
