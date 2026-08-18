package ru.shmr.finance.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import ru.shmr.finance.data.sync.SyncAction
import ru.shmr.finance.data.sync.SyncFailure

@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val emoji: String,
    val balance: String,
    val syncBalance: String,
    val currency: String,
    val syncAction: SyncAction = SyncAction.NONE,
    val revision: Long = 0,
    val syncFailure: SyncFailure? = null,
    val transactionSyncCursor: String? = null,
)

@Entity(tableName = "categories")
data class CategoryEntity(
    @PrimaryKey val id: Int,
    val name: String,
    val emoji: String,
    val isIncome: Boolean,
)

@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = AccountEntity::class,
            parentColumns = ["id"],
            childColumns = ["accountId"],
            onDelete = ForeignKey.RESTRICT,
        ),
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [
        Index("accountId"),
        Index("categoryId"),
        Index(value = ["serverId"], unique = true),
        Index("transactionDate"),
        Index("syncAction"),
    ],
)
data class TransactionEntity(
    @PrimaryKey val localId: String,
    val serverId: Int?,
    val accountId: Int,
    val categoryId: Int,
    val amount: String,
    val transactionDate: String,
    val comment: String?,
    val syncAction: SyncAction = SyncAction.NONE,
    val revision: Long = 0,
    val syncFailure: SyncFailure? = null,
)

data class TransactionRecord(
    @Embedded val transaction: TransactionEntity,
    @Relation(parentColumn = "accountId", entityColumn = "id")
    val account: AccountEntity?,
    @Relation(parentColumn = "categoryId", entityColumn = "id")
    val category: CategoryEntity?,
)
