package ru.shmr.finance.ui.components

import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.AccessibilityAction
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import ru.shmr.finance.R
import ru.shmr.finance.ui.testing.DatePickerTestTags
import ru.shmr.finance.ui.testing.selectedDateMillis
import ru.shmr.finance.ui.testing.selectDateMillis

/**
 * Single-day picker (not a range): blocks any date after today. Shared by Expenses/Income so the
 * calendar UI and future-date rule live in exactly one place.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SingleDatePickerDialog(
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
    today: LocalDate = LocalDate.now(),
) {
    val todayUtcMillis = today.toUtcMillis()
    val selectableDates = remember(todayUtcMillis) {
        object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean =
                utcTimeMillis <= todayUtcMillis
        }
    }
    val state = rememberDatePickerState(
        initialSelectedDateMillis = selectedDate.toUtcMillis(),
        selectableDates = selectableDates,
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                modifier = Modifier.testTag(DatePickerTestTags.APPLY),
                onClick = {
                    state.selectedDateMillis?.let { onDateSelected(it.toLocalDate()) }
                    onDismiss()
                },
            ) {
                Text(stringResource(R.string.action_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
    ) {
        DatePicker(
            state = state,
            showModeToggle = false,
            modifier = Modifier
                .testTag(DatePickerTestTags.CALENDAR)
                .semantics {
                    selectedDateMillis = state.selectedDateMillis ?: Long.MIN_VALUE
                    selectDateMillis = AccessibilityAction(label = null) { utcTimeMillis ->
                        if (!selectableDates.isSelectableDate(utcTimeMillis)) false else {
                            state.selectedDateMillis = utcTimeMillis
                            true
                        }
                    }
                },
        )
    }
}

private fun LocalDate.toUtcMillis(): Long =
    atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.toLocalDate(): LocalDate =
    Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()
