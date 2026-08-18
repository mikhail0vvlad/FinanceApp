package ru.shmr.finance.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow
import ru.shmr.finance.data.local.entity.AccountEntity
import ru.shmr.finance.data.local.entity.CategoryEntity
import ru.shmr.finance.data.local.entity.TransactionEntity
import ru.shmr.finance.data.local.entity.TransactionRecord

@Dao
interface AccountDao {

    @Query("SELECT * FROM accounts ORDER BY id")
    fun observeAll(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY id")
    suspend fun getAll(): List<AccountEntity>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: Int): AccountEntity?

    @Query("SELECT MIN(id) FROM accounts")
    suspend fun lowestId(): Int?

    @Query("SELECT * FROM accounts WHERE syncAction != 'NONE' ORDER BY id")
    suspend fun getPending(): List<AccountEntity>

    @Upsert
    suspend fun upsert(account: AccountEntity)

    @Upsert
    suspend fun upsertAll(accounts: List<AccountEntity>)

    @Query("DELETE FROM accounts WHERE id = :id")
    suspend fun deleteById(id: Int)
}

@Dao
interface CategoryDao {

    @Query("SELECT * FROM categories ORDER BY name")
    fun observeAll(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY name")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: Int): CategoryEntity?

    @Upsert
    suspend fun upsertAll(categories: List<CategoryEntity>)
}

@Dao
interface TransactionDao {

    @Transaction
    @Query(
        """
        SELECT * FROM transactions
        WHERE transactionDate >= :startInclusive AND transactionDate < :endExclusive
        ORDER BY transactionDate DESC
        """,
    )
    fun observeForPeriod(
        startInclusive: String,
        endExclusive: String,
    ): Flow<List<TransactionRecord>>

    @Transaction
    @Query("SELECT * FROM transactions WHERE localId = :localId")
    suspend fun getRecordByLocalId(localId: String): TransactionRecord?

    @Query("SELECT * FROM transactions WHERE localId = :localId")
    suspend fun getByLocalId(localId: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE serverId = :serverId")
    suspend fun getByServerId(serverId: Int): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE serverId IN (:serverIds)")
    suspend fun getByServerIds(serverIds: List<Int>): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE syncAction != 'NONE' ORDER BY transactionDate")
    suspend fun getPending(): List<TransactionEntity>

    @Query("SELECT DISTINCT accountId FROM transactions WHERE syncAction != 'NONE'")
    suspend fun pendingAccountIds(): List<Int>

    @Query("SELECT EXISTS(SELECT 1 FROM transactions WHERE accountId = :accountId LIMIT 1)")
    suspend fun existsForAccount(accountId: Int): Boolean

    @Upsert
    suspend fun upsert(transaction: TransactionEntity)

    @Upsert
    suspend fun upsertAll(transactions: List<TransactionEntity>)

    @Query(
        """
        DELETE FROM transactions
        WHERE accountId = :accountId
          AND transactionDate >= :startInclusive
          AND transactionDate < :endExclusive
          AND syncAction = 'NONE'
        """,
    )
    suspend fun deleteSyncedForPeriod(
        accountId: Int,
        startInclusive: String,
        endExclusive: String,
    )

    @Query("UPDATE transactions SET accountId = :remoteId WHERE accountId = :localId")
    suspend fun reassignAccount(localId: Int, remoteId: Int)
}
