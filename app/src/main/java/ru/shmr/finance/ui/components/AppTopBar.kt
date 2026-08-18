package ru.shmr.finance.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ru.shmr.finance.R
import ru.shmr.finance.ui.testing.DatePickerTestTags
import ru.shmr.finance.ui.testing.dateChipState

object AppTopBarDefaults {
    /**
     * Приложение работает edge-to-edge, а Scaffold отдаёт слоту topBar область вместе со статус-
     * баром и вырезом. Отступ делает сама панель — как это делает material3 TopAppBar.
     */
    val windowInsets: WindowInsets
        @Composable get() = WindowInsets.safeDrawing
            .only(WindowInsetsSides.Horizontal + WindowInsetsSides.Top)
}

@Composable
fun AppTopBar(
    date: String,
    modifier: Modifier = Modifier,
    isToday: Boolean = false,
    windowInsets: WindowInsets = AppTopBarDefaults.windowInsets,
    onDateClick: () -> Unit = {},
    onAnalysisClick: () -> Unit = {},
    onFilterClick: () -> Unit = {},
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(windowInsets)
            .height(64.dp)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        DateChip(date, isToday = isToday, onClick = onDateClick)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onAnalysisClick) {
                Icon(
                    painter = painterResource(R.drawable.ic_bar_chart),
                    contentDescription = stringResource(R.string.cd_analysis),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
            IconButton(onClick = onFilterClick) {
                Icon(
                    painter = painterResource(R.drawable.ic_tune),
                    contentDescription = stringResource(R.string.cd_settings),
                    tint = MaterialTheme.colorScheme.onSurface,
                )
            }
        }
    }
}

@Composable
private fun DateChip(date: String, isToday: Boolean, onClick: () -> Unit) {
    val pickDateDescription = stringResource(R.string.cd_pick_date)
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
    ) {
        Row(
            modifier = Modifier
                .testTag(DatePickerTestTags.CHIP)
                .clickable(onClick = onClick)
                .semantics {
                    contentDescription = pickDateDescription
                    dateChipState = if (isToday) {
                        DatePickerTestTags.TODAY_STATE
                    } else {
                        DatePickerTestTags.SELECTED_STATE
                    }
                }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_calendar_month),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = date,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
