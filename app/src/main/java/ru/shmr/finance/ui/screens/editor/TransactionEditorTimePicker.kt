package ru.shmr.finance.ui.screens.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.time.LocalTime
import ru.shmr.finance.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimePickerSheet(
    value: LocalTime,
    error: String?,
    onValueChanged: (LocalTime) -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit,
) {
    val pickerState = rememberTimePickerState(value.hour, value.minute, is24Hour = true)
    LaunchedEffect(pickerState.hour, pickerState.minute) {
        onValueChanged(LocalTime.of(pickerState.hour, pickerState.minute))
    }
    ModalBottomSheet(
        onDismissRequest = onCancel,
        modifier = Modifier.imePadding(),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp)) {
            Text(
                stringResource(R.string.editor_enter_time),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TimeInput(
                pickerState,
                Modifier.align(Alignment.CenterHorizontally).padding(top = 16.dp),
            )
            if (error != null) {
                Text(
                    error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            PickerActions(onCancel, onApply, Modifier.padding(top = 8.dp, bottom = 40.dp))
        }
    }
}

@Composable
internal fun PickerActions(
    onCancel: () -> Unit,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onCancel) { Text(stringResource(R.string.action_cancel)) }
        TextButton(onApply) { Text(stringResource(R.string.action_apply)) }
    }
}
