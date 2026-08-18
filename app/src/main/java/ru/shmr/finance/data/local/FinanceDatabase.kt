package ru.shmr.finance.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import ru.shmr.finance.data.local.dao.AccountDao
import ru.shmr.finance.data.local.dao.CategoryDao
import ru.shmr.finance.data.local.dao.TransactionDao
import ru.shmr.finance.data.local.entity.AccountEntity
import ru.shmr.finance.data.local.entity.CategoryEntity
import ru.shmr.finance.data.local.entity.TransactionEntity
import ru.shmr.finance.data.sync.SyncAction
import ru.shmr.finance.data.sync.SyncFailure

@Database(
    entities = [
        AccountEntity::class,
        CategoryEntity::class,
        TransactionEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(FinanceConverters::class)
abstract class FinanceDatabase : RoomDatabase() {
    abstract fun accountDao(): AccountDao
    abstract fun categoryDao(): CategoryDao
    abstract fun transactionDao(): TransactionDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(database: SupportSQLiteDatabase) {
                database.execSQL(
                    "ALTER TABLE accounts ADD COLUMN revision INTEGER NOT NULL DEFAULT 0",
                )
                database.execSQL(
                    "ALTER TABLE accounts ADD COLUMN syncFailure TEXT DEFAULT NULL",
                )
                database.execSQL(
                    "ALTER TABLE accounts ADD COLUMN transactionSyncCursor TEXT DEFAULT NULL",
                )
                database.execSQL(
                    "ALTER TABLE transactions ADD COLUMN revision INTEGER NOT NULL DEFAULT 0",
                )
                database.execSQL(
                    "ALTER TABLE transactions ADD COLUMN syncFailure TEXT DEFAULT NULL",
                )
            }
        }
    }
}

class FinanceConverters {
    @TypeConverter
    fun syncActionToString(value: SyncAction): String = value.name

    @TypeConverter
    fun stringToSyncAction(value: String): SyncAction = SyncAction.valueOf(value)

    @TypeConverter
    fun syncFailureToString(value: SyncFailure?): String? = value?.name

    @TypeConverter
    fun stringToSyncFailure(value: String?): SyncFailure? = value?.let(SyncFailure::valueOf)
}
