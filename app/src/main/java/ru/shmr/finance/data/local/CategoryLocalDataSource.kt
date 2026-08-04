package ru.shmr.finance.data.local

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import ru.shmr.finance.core.dispatchers.DispatcherProvider
import ru.shmr.finance.data.local.entity.CategoryEntity
import ru.shmr.finance.domain.model.Category

/** Owns cached category reads and remote category replacement. */
internal class CategoryLocalDataSource(
    database: FinanceDatabase,
    private val dispatchers: DispatcherProvider,
) {
    private val categories = database.categoryDao()

    fun observeCategories(): Flow<List<Category>> =
        categories.observeAll().map { rows -> rows.map(CategoryEntity::toDomain) }

    suspend fun getCategories(): List<Category> = withContext(dispatchers.io) {
        categories.getAll().map(CategoryEntity::toDomain)
    }

    suspend fun upsertCategories(remote: List<CategoryEntity>) = withContext(dispatchers.io) {
        categories.upsertAll(remote)
    }
}
