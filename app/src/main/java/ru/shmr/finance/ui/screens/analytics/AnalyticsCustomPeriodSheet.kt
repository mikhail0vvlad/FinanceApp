package ru.shmr.finance.ui.screens.analytics

import android.content.res.Configuration
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import ru.shmr.finance.R

private fun formatFieldDate(millis: Long?, locale: Locale): String = millis?.let {
    val formatter = DateTimeFormatter.ofPattern("d MMM yyyy", locale)
    Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate().format(formatter)
} ?: ""

@Composable
private fun DateField(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.heightIn(min = 44.dp).border(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant,
            shape = RoundedCornerShape(8.dp),
        ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun CustomPeriodSheet(
    initialStart: LocalDate,
    initialEnd: LocalDate,
    onConfirm: (LocalDate, LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    FilterSheet(onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLowest) {
        val baseConfiguration = LocalConfiguration.current
        val appLocale = baseConfiguration.locales[0]
        val localizedConfiguration = remember(baseConfiguration, appLocale) {
            Configuration(baseConfiguration).apply { setLocale(appLocale) }
        }
        CompositionLocalProvider(LocalConfiguration provides localizedConfiguration) {
            val pickerState = rememberDateRangePickerState(
                initialSelectedStartDateMillis = initialStart.toUtcMillis(),
                initialSelectedEndDateMillis = initialEnd.toUtcMillis(),
            )
            SheetTitle(stringResource(R.string.custom_period_title))
            DateRangeFields(
                formatFieldDate(pickerState.selectedStartDateMillis, appLocale),
                formatFieldDate(pickerState.selectedEndDateMillis, appLocale),
            )
            DateRangePicker(
                state = pickerState,
                title = null,
                headline = null,
                showModeToggle = false,
                colors = analyticsDatePickerColors(),
                modifier = Modifier.heightIn(max = 420.dp),
            )
            PeriodActions(
                onDismiss = onDismiss,
                onApply = {
                    pickerState.selectedDatesOrNull()?.let { (start, end) -> onConfirm(start, end) }
                    onDismiss()
                },
            )
        }
    }
}

@Composable
private fun DateRangeFields(start: String, end: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        DateField(start, Modifier.weight(1f))
        Text(
            "—",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DateField(end, Modifier.weight(1f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun analyticsDatePickerColors() = DatePickerDefaults.colors(
    containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    subheadContentColor = MaterialTheme.colorScheme.onSurface,
    weekdayContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
    dayContentColor = MaterialTheme.colorScheme.onSurface,
    selectedDayContainerColor = MaterialTheme.colorScheme.primary,
    selectedDayContentColor = MaterialTheme.colorScheme.onPrimary,
    dayInSelectionRangeContainerColor = MaterialTheme.colorScheme.secondaryContainer,
    dayInSelectionRangeContentColor = MaterialTheme.colorScheme.onSurface,
    dividerColor = Color.Transparent,
)

@Composable
private fun PeriodActions(onDismiss: () -> Unit, onApply: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        Spacer(Modifier.width(8.dp))
        Button(onClick = onApply, shape = RoundedCornerShape(24.dp)) {
            Text(stringResource(R.string.action_apply))
        }
    }
}

private fun LocalDate.toUtcMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

@OptIn(ExperimentalMaterial3Api::class)
private fun androidx.compose.material3.DateRangePickerState.selectedDatesOrNull(): Pair<LocalDate, LocalDate>? {
    val start = selectedStartDateMillis ?: return null
    val end = selectedEndDateMillis ?: return null
    return start.toUtcDate() to end.toUtcDate()
}

private fun Long.toUtcDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
