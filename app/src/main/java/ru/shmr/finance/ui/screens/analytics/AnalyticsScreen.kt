package ru.shmr.finance.ui.screens.analytics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ru.shmr.finance.R
import ru.shmr.finance.core.state.UiState
import ru.shmr.finance.ui.components.EmptyState
import ru.shmr.finance.ui.components.ErrorState
import ru.shmr.finance.ui.components.ListItemRow
import ru.shmr.finance.ui.components.LoadingState
import ru.shmr.finance.ui.components.plainMessage
import ru.shmr.finance.ui.screens.toListItem
import ru.shmr.finance.ui.theme.LeadBadgeOutline

private enum class AnalyticsSheet { TYPE, PERIOD, ARTICLES, ACCOUNT, DETAIL }

@Composable
fun AnalyticsScreen(
    startWithIncome: Boolean,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: AnalyticsViewModel = viewModel(
        factory = viewModelFactory {
            initializer { AnalyticsViewModel(startWithIncome) }
        },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val retryLabel = stringResource(R.string.action_retry)

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is AnalyticsEffect.ShowError -> {
                    val result = snackbarHostState.showSnackbar(
                        message = effect.error.plainMessage(context),
                        actionLabel = retryLabel,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.onAction(AnalyticsAction.Retry)
                    }
                }
            }
        }
    }

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        AnalyticsContent(
            state = state,
            onAction = viewModel::onAction,
            onBack = onBack,
            modifier = Modifier.padding(padding),
        )
    }
}

@Composable
private fun AnalyticsContent(
    state: AnalyticsState,
    onAction: (AnalyticsAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var openSheet by rememberSaveable { mutableStateOf<AnalyticsSheet?>(null) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }

    Column(modifier.fillMaxSize()) {
        AnalyticsTopBar(onBack)

        when (val ui = state.ui) {
            UiState.Loading -> Column(Modifier.fillMaxSize()) {
                FilterRows(state) { openSheet = it }
                LoadingState(Modifier.weight(1f))
            }

            UiState.Empty -> Column(Modifier.fillMaxSize()) {
                FilterRows(state) { openSheet = it }
                EmptyState(Modifier.weight(1f))
            }

            is UiState.Error -> Column(Modifier.fillMaxSize()) {
                FilterRows(state) { openSheet = it }
                ErrorState(ui.error, { onAction(AnalyticsAction.Retry) }, Modifier.weight(1f))
            }

            is UiState.Content -> LazyColumn(Modifier.fillMaxSize()) {
                item(key = "chart") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        DonutChart(
                            shares = ui.data.shares,
                            caption = stringResource(R.string.analytics_total_for_period),
                            amount = ui.data.total.formatted(),
                            modifier = Modifier.clickable { openSheet = AnalyticsSheet.DETAIL },
                        )
                        ChartLegend(ui.data.shares)
                    }
                }
                item(key = "filters") { FilterRows(state) { openSheet = it } }
                item(key = "header") {
                    Text(
                        text = stringResource(R.string.analytics_transactions),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
                    )
                }
                items(ui.data.transactions, key = { it.id }) { transaction ->
                    ListItemRow(
                        transaction.toListItem().copy(
                            subtitle = transaction.comment ?: transaction.accountName,
                        ),
                    )
                }
            }
        }
    }

    AnalyticsSheets(
        state = state,
        openSheet = openSheet,
        onAction = onAction,
        onCloseSheet = { openSheet = null },
        onCustomRequested = {
            openSheet = null
            showDatePicker = true
        },
    )

    if (showDatePicker) {
        CustomPeriodSheet(
            initialStart = state.filters.startDate,
            initialEnd = state.filters.endDate,
            onConfirm = { start, end -> onAction(AnalyticsAction.SelectCustomPeriod(start, end)) },
            onDismiss = { showDatePicker = false },
        )
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
            selected = state.filters.type,
            onSelected = { onAction(AnalyticsAction.SelectType(it)) },
            onDismiss = onCloseSheet,
        )

        AnalyticsSheet.PERIOD -> PeriodFilterSheet(
            filters = state.filters,
            onPresetSelected = { onAction(AnalyticsAction.SelectPreset(it)) },
            onCustomRequested = onCustomRequested,
            onDismiss = onCloseSheet,
        )

        AnalyticsSheet.ARTICLES -> ArticlesFilterSheet(
            categories = state.categories.filter {
                when (state.filters.type) {
                    TypeFilter.EXPENSES -> !it.isIncome
                    TypeFilter.INCOME -> it.isIncome
                    TypeFilter.ALL -> true
                }
            },
            selectedIds = state.filters.selectedCategoryIds,
            onApply = { onAction(AnalyticsAction.SelectCategories(it)) },
            onDismiss = onCloseSheet,
        )

        AnalyticsSheet.ACCOUNT -> AccountFilterSheet(
            accounts = state.accounts,
            selectedAccountId = state.filters.selectedAccountId,
            onSelected = { onAction(AnalyticsAction.SelectAccount(it)) },
            onDismiss = onCloseSheet,
        )

        AnalyticsSheet.DETAIL -> (state.ui as? UiState.Content)?.let { content ->
            DetailSheet(data = content.data, onDismiss = onCloseSheet)
        }

        null -> Unit
    }
}

@Composable
private fun AnalyticsTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = stringResource(R.string.cd_back),
            )
        }
        Text(
            text = stringResource(R.string.analytics_title),
            fontSize = 24.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun ChartLegend(shares: List<CategoryShare>) {
    Row(
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        shares.take(3).forEachIndexed { index, share ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                LegendDot(chartColor(index))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = share.category.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun FilterRows(
    state: AnalyticsState,
    onOpenSheet: (AnalyticsSheet) -> Unit,
) {
    val filters = state.filters
    val articlesChip = when (val ids = filters.selectedCategoryIds) {
        null -> stringResource(R.string.all_articles)
        else -> state.categories.filter { it.id in ids }.joinToString { it.name }
            .ifEmpty { stringResource(R.string.all_articles) }
    }
    val accountChip = state.accounts.find { it.id == filters.selectedAccountId }?.name
        ?: stringResource(R.string.all_accounts)

    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        FilterRow(
            icon = rememberVectorPainter(Icons.AutoMirrored.Outlined.List),
            label = stringResource(R.string.filter_type),
            chipText = filters.type.label(),
            onClick = { onOpenSheet(AnalyticsSheet.TYPE) },
        )
        FilterRow(
            icon = painterResource(R.drawable.ic_calendar_month),
            label = stringResource(R.string.filter_period),
            chipText = formatPeriod(filters.startDate, filters.endDate),
            onClick = { onOpenSheet(AnalyticsSheet.PERIOD) },
        )
        FilterRow(
            icon = rememberVectorPainter(Icons.Outlined.Sell),
            label = stringResource(R.string.filter_articles),
            chipText = articlesChip,
            onClick = { onOpenSheet(AnalyticsSheet.ARTICLES) },
        )
        FilterRow(
            icon = rememberVectorPainter(Icons.Outlined.CreditCard),
            label = stringResource(R.string.filter_account),
            chipText = accountChip,
            onClick = { onOpenSheet(AnalyticsSheet.ACCOUNT) },
        )
    }
}

@Composable
private fun FilterRow(
    icon: Painter,
    label: String,
    chipText: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .border(1.dp, LeadBadgeOutline, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                painter = icon,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurface,
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp),
        )
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Text(
                text = chipText,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .widthIn(max = 220.dp)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
