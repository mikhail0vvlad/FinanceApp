package ru.shmr.finance.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FinanceDatabaseMigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        FinanceDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    @Throws(IOException::class)
    fun migrate1To2PreservesRowsAndAddsSyncMetadata() {
        helper.createDatabase(TEST_DB, 1).apply {
            execSQL(
                """
                INSERT INTO accounts(id, name, emoji, balance, syncBalance, currency, syncAction)
                VALUES (1, 'Main', 'wallet', '1000', '1000', 'RUB', 'NONE')
                """.trimIndent(),
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            TEST_DB,
            2,
            true,
            FinanceDatabase.MIGRATION_1_2,
        )
        migrated.query(
            "SELECT revision, syncFailure, transactionSyncCursor FROM accounts WHERE id = 1",
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals(0L, cursor.getLong(0))
            assertEquals(true, cursor.isNull(1))
            assertEquals(true, cursor.isNull(2))
        }
        migrated.close()
    }

    private companion object {
        const val TEST_DB = "migration-test"
    }
}
