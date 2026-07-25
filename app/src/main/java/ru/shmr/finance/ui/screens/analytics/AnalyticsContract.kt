package ru.shmr.finance.ui.screens.analytics

import androidx.compose.runtime.Immutable
import java.time.LocalDate
import ru.shmr.finance.core.state.UiState
import ru.shmr.finance.domain.model.Account
import ru.shmr.finance.domain.model.AppError
import ru.shmr.finance.domain.model.Category

@Immutable
data class AnalyticsState(
    val ui: UiState<AnalyticsData> = UiState.Loading,
    val filters: AnalyticsFilters,
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
)

sealed interface AnalyticsAction {
    data class SelectType(val type: TypeFilter) : AnalyticsAction
    data class SelectPreset(val preset: PeriodPreset) : AnalyticsAction
    data class SelectCustomPeriod(val start: LocalDate, val end: LocalDate) : AnalyticsAction
    data class SelectCategories(val ids: Set<Int>?) : AnalyticsAction
    data class SelectAccount(val accountId: Int?) : AnalyticsAction
    data object Retry : AnalyticsAction
}

sealed interface AnalyticsEffect {
    data class ShowError(val error: AppError) : AnalyticsEffect
}
