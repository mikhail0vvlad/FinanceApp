package ru.shmr.finance.ui.screens.analytics

import androidx.compose.runtime.Immutable
import java.time.DayOfWeek
import java.time.LocalDate
import ru.shmr.finance.domain.model.Category
import ru.shmr.finance.domain.model.Money
import ru.shmr.finance.domain.model.Transaction

enum class TypeFilter { EXPENSES, INCOME, ALL }

enum class PeriodPreset { CUSTOM, WEEK, MONTH, QUARTER, YEAR }

@Immutable
data class AnalyticsFilters(
    val type: TypeFilter,
    val preset: PeriodPreset,
    val startDate: LocalDate,
    val endDate: LocalDate,
    val selectedCategoryIds: Set<Int>?, // null = все статьи
    val selectedAccountId: Int?, // null = все счета
) {
    companion object {
        // по умолчанию: с начала текущего месяца по сегодня
        fun default(type: TypeFilter): AnalyticsFilters {
            val today = LocalDate.now()
            return AnalyticsFilters(
                type = type,
                preset = PeriodPreset.MONTH,
                startDate = today.withDayOfMonth(1),
                endDate = today,
                selectedCategoryIds = null,
                selectedAccountId = null,
            )
        }

        fun periodFor(preset: PeriodPreset, today: LocalDate = LocalDate.now()): Pair<LocalDate, LocalDate> =
            when (preset) {
                PeriodPreset.WEEK -> today.with(DayOfWeek.MONDAY) to today
                PeriodPreset.MONTH -> today.withDayOfMonth(1) to today
                PeriodPreset.QUARTER -> {
                    val quarterStartMonth = ((today.monthValue - 1) / 3) * 3 + 1
                    today.withMonth(quarterStartMonth).withDayOfMonth(1) to today
                }
                PeriodPreset.YEAR -> today.withDayOfYear(1) to today
                PeriodPreset.CUSTOM -> today.withDayOfMonth(1) to today
            }
    }
}

@Immutable
data class CategoryShare(
    val category: Category,
    val amount: Money,
    val fraction: Float, // доля от общей суммы, 0..1
    val percent: Int,
)

@Immutable
data class AnalyticsData(
    val total: Money,
    val shares: List<CategoryShare>,
    val transactions: List<Transaction>,
)
