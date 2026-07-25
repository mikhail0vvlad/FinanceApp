package ru.shmr.finance.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import ru.shmr.finance.data.local.dao.AccountDao
import ru.shmr.finance.data.local.dao.CategoryDao
import ru.shmr.finance.data.local.dao.TransactionDao
import ru.shmr.finance.data.local.entity.AccountEntity
import ru.shmr.finance.data.local.entity.CategoryEntity
import ru.shmr.finance.data.local.entity.TransactionEntity
import ru.shmr.finance.data.sync.SyncAction

@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(FinanceConverters::class)
abstract class FinanceDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao
}

class FinanceConverters {
    @TypeConverter
    fun syncActionToString(value: SyncAction): String = value.name

    @TypeConverter
    fun stringToSyncAction(value: String): SyncAction = SyncAction.valueOf(value)
}
