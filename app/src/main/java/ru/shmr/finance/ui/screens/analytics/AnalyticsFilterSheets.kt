package ru.shmr.finance.ui.screens.analytics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import ru.shmr.finance.R

private val PeriodDatesFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

@Composable
internal fun TypeFilterSheet(
    selected: TypeFilter,
    onSelected: (TypeFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    var current by remember { mutableStateOf(selected) }
    FilterSheet(onDismiss) {
        SheetTitle(stringResource(R.string.filter_type))
        TypeFilter.entries.forEach { type ->
            TypeFilterRow(type, current == type) { current = type }
            SheetDivider()
        }
        SheetButton(stringResource(R.string.action_done)) {
            onSelected(current)
            onDismiss()
        }
    }
}

@Composable
private fun TypeFilterRow(type: TypeFilter, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(type.label(), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        SelectionMark(selected = selected, filled = true)
    }
}

@Composable
internal fun TypeFilter.label(): String = when (this) {
    TypeFilter.EXPENSES -> stringResource(R.string.type_expenses)
    TypeFilter.INCOME -> stringResource(R.string.type_income)
    TypeFilter.ALL -> stringResource(R.string.type_all)
}

@Composable
internal fun PeriodPreset.label(): String = when (this) {
    PeriodPreset.CUSTOM -> stringResource(R.string.period_custom)
    PeriodPreset.WEEK -> stringResource(R.string.period_week)
    PeriodPreset.MONTH -> stringResource(R.string.period_month)
    PeriodPreset.QUARTER -> stringResource(R.string.period_quarter)
    PeriodPreset.YEAR -> stringResource(R.string.period_year)
}

internal fun formatPeriod(start: LocalDate, end: LocalDate): String =
    "${start.format(PeriodDatesFormatter)} – ${end.format(PeriodDatesFormatter)}"

@Composable
internal fun PeriodFilterSheet(
    filters: AnalyticsFilters,
    onPresetSelected: (PeriodPreset) -> Unit,
    onCustomRequested: () -> Unit,
    onDismiss: () -> Unit,
) {
    FilterSheet(onDismiss) {
        SheetTitle(stringResource(R.string.filter_period))
        CustomPeriodRow(filters, onCustomRequested)
        SheetDivider()
        listOf(PeriodPreset.WEEK, PeriodPreset.MONTH, PeriodPreset.QUARTER, PeriodPreset.YEAR)
            .forEach { preset ->
                PresetRow(preset, filters.preset == preset) {
                    onPresetSelected(preset)
                    onDismiss()
                }
                SheetDivider()
            }
    }
}

@Composable
private fun CustomPeriodRow(filters: AnalyticsFilters, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                stringResource(R.string.period_custom),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                formatPeriod(filters.startDate, filters.endDate),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SelectionMark(selected = filters.preset == PeriodPreset.CUSTOM, filled = false)
    }
}

@Composable
private fun PresetRow(preset: PeriodPreset, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            preset.label(),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        SelectionMark(selected = selected, filled = false)
    }
}
