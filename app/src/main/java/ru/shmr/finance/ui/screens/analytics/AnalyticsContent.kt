package ru.shmr.finance.ui.screens.analytics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.shmr.finance.R
import ru.shmr.finance.core.state.UiState
import ru.shmr.finance.ui.components.EmptyState
import ru.shmr.finance.ui.components.ErrorState
import ru.shmr.finance.ui.components.ListItemRow
import ru.shmr.finance.ui.components.LoadingState
import ru.shmr.finance.ui.screens.toListItem

internal enum class AnalyticsSheet { TYPE, PERIOD, CUSTOM_PERIOD, ARTICLES, ACCOUNT, DETAIL }

@Composable
internal fun AnalyticsContent(
    state: AnalyticsState,
    onAction: (AnalyticsAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var openSheet by rememberSaveable { mutableStateOf<AnalyticsSheet?>(null) }
    Column(modifier.fillMaxSize()) {
        AnalyticsTopBar(onBack)
        when (val ui = state.ui) {
            UiState.Loading -> AnalyticsStateContainer(state, { openSheet = it }) {
                LoadingState(Modifier.weight(1f))
            }
            UiState.Empty -> AnalyticsStateContainer(state, { openSheet = it }) {
                EmptyState(Modifier.weight(1f))
            }
            is UiState.Error -> AnalyticsStateContainer(state, { openSheet = it }) {
                ErrorState(ui.error, { onAction(AnalyticsAction.Retry) }, Modifier.weight(1f))
            }
            is UiState.Content -> AnalyticsTransactions(ui.data, state) { openSheet = it }
        }
    }
    AnalyticsSheets(
        state,
        openSheet,
        onAction,
        { openSheet = null },
        { openSheet = AnalyticsSheet.CUSTOM_PERIOD },
    )
}

@Composable
private fun AnalyticsStateContainer(
    state: AnalyticsState,
    onOpenSheet: (AnalyticsSheet) -> Unit,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        FilterRows(state, onOpenSheet)
        content()
    }
}

@Composable
private fun AnalyticsTransactions(
    data: AnalyticsData,
    state: AnalyticsState,
    onOpenSheet: (AnalyticsSheet) -> Unit,
) {
    LazyColumn(Modifier.fillMaxSize()) {
        item(key = "chart") {
            Column(
                Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                DonutChart(
                    shares = data.shares,
                    caption = stringResource(R.string.analytics_total_for_period),
                    amount = data.total.formatted(),
                    modifier = Modifier.clickable { onOpenSheet(AnalyticsSheet.DETAIL) },
                )
                ChartLegend(data.shares)
            }
        }
        item(key = "filters") { FilterRows(state, onOpenSheet) }
        item(key = "header") {
            Text(
                stringResource(R.string.analytics_transactions),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
            )
        }
        items(data.transactions, key = { it.id }) { transaction ->
            ListItemRow(
                transaction.toListItem().copy(
                    subtitle = transaction.comment ?: transaction.accountName,
                ),
            )
        }
    }
}

@Composable
private fun AnalyticsSheets(
    state: AnalyticsState,
    openSheet: AnalyticsSheet?,
    onAction: (AnalyticsAction) -> Unit,
    onCloseSheet: () -> Unit,
    onCustomRequested: () -> Unit,
) {
    when (openSheet) {
        AnalyticsSheet.TYPE -> TypeFilterSheet(
            state.filters.type,
            { onAction(AnalyticsAction.SelectType(it)) },
            onCloseSheet,
        )
        AnalyticsSheet.PERIOD -> PeriodFilterSheet(
            state.filters,
            { onAction(AnalyticsAction.SelectPreset(it)) },
            onCustomRequested,
            onCloseSheet,
        )
        AnalyticsSheet.CUSTOM_PERIOD -> CustomPeriodSheet(
            state.filters.startDate,
            state.filters.endDate,
            { start, end -> onAction(AnalyticsAction.SelectCustomPeriod(start, end)) },
            onCloseSheet,
        )
        AnalyticsSheet.ARTICLES -> ArticlesFilterSheet(
            state.categories.filter { state.filters.type.matches(it.isIncome) },
            state.filters.selectedCategoryIds,
            { onAction(AnalyticsAction.SelectCategories(it)) },
            onCloseSheet,
        )
        AnalyticsSheet.ACCOUNT -> AccountFilterSheet(
            state.accounts,
            state.filters.selectedAccountId,
            { onAction(AnalyticsAction.SelectAccount(it)) },
            onCloseSheet,
        )
        AnalyticsSheet.DETAIL -> (state.ui as? UiState.Content)?.let {
            DetailSheet(it.data, onCloseSheet)
        }
        null -> Unit
    }
}
