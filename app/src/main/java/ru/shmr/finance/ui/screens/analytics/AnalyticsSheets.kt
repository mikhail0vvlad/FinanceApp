package ru.shmr.finance.ui.screens.analytics

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import ru.shmr.finance.R
import ru.shmr.finance.domain.model.Account
import ru.shmr.finance.domain.model.Category
import ru.shmr.finance.ui.components.LeadContent
import ru.shmr.finance.ui.components.ListItemModel
import ru.shmr.finance.ui.components.ListItemRow

private val PeriodDatesFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

@Composable
private fun SheetTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
    )
}

@Composable
private fun SelectionMark(selected: Boolean, filled: Boolean) {
    if (filled) {
        Icon(
            imageVector = if (selected) Icons.Filled.CheckCircle else Icons.Outlined.Circle,
            contentDescription = null,
            tint = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline
            },
        )
    } else if (selected) {
        Icon(
            imageVector = Icons.Filled.Check,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun SheetButton(text: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .height(52.dp),
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TypeFilterSheet(
    selected: TypeFilter,
    onSelected: (TypeFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    var current by remember { mutableStateOf(selected) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        SheetTitle(stringResource(R.string.filter_type))
        TypeFilter.entries.forEach { type ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { current = type }
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = type.label(),
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                SelectionMark(selected = current == type, filled = true)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
        SheetButton(stringResource(R.string.action_done)) {
            onSelected(current)
            onDismiss()
        }
    }
}

@Composable
fun TypeFilter.label(): String = when (this) {
    TypeFilter.EXPENSES -> stringResource(R.string.type_expenses)
    TypeFilter.INCOME -> stringResource(R.string.type_income)
    TypeFilter.ALL -> stringResource(R.string.type_all)
}

@Composable
fun PeriodPreset.label(): String = when (this) {
    PeriodPreset.CUSTOM -> stringResource(R.string.period_custom)
    PeriodPreset.WEEK -> stringResource(R.string.period_week)
    PeriodPreset.MONTH -> stringResource(R.string.period_month)
    PeriodPreset.QUARTER -> stringResource(R.string.period_quarter)
    PeriodPreset.YEAR -> stringResource(R.string.period_year)
}

fun formatPeriod(start: LocalDate, end: LocalDate): String =
    "${start.format(PeriodDatesFormatter)} – ${end.format(PeriodDatesFormatter)}"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodFilterSheet(
    filters: AnalyticsFilters,
    onPresetSelected: (PeriodPreset) -> Unit,
    onCustomRequested: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        SheetTitle(stringResource(R.string.filter_period))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCustomRequested() }
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.period_custom),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    text = formatPeriod(filters.startDate, filters.endDate),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            SelectionMark(selected = filters.preset == PeriodPreset.CUSTOM, filled = false)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        listOf(PeriodPreset.WEEK, PeriodPreset.MONTH, PeriodPreset.QUARTER, PeriodPreset.YEAR)
            .forEach { preset ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onPresetSelected(preset)
                            onDismiss()
                        }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = preset.label(),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    SelectionMark(selected = filters.preset == preset, filled = false)
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
    }
}

private val CustomPeriodLocale: Locale = Locale("ru")
private val CustomPeriodFieldFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM yyyy", CustomPeriodLocale)

private val CustomPeriodMonthFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("LLLL yyyy", CustomPeriodLocale)
private val WeekdayLabels = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

private fun formatFieldDate(date: LocalDate?): String =
    date?.format(CustomPeriodFieldFormatter) ?: ""

@Composable
private fun DateField(text: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .heightIn(min = 44.dp)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(8.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun RangeCalendar(
    start: LocalDate?,
    end: LocalDate?,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    var visibleMonth by remember { mutableStateOf(YearMonth.from(start ?: LocalDate.now())) }

    Column(modifier.padding(horizontal = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { visibleMonth = visibleMonth.minusMonths(1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = null)
            }
            Text(
                text = visibleMonth.format(CustomPeriodMonthFormatter)
                    .replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { visibleMonth = visibleMonth.plusMonths(1) }) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
            }
        }

        Row(Modifier.fillMaxWidth()) {
            WeekdayLabels.forEach { label ->
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        Spacer(Modifier.height(8.dp))

        val firstDay = visibleMonth.atDay(1)
        val leadingBlanks = firstDay.dayOfWeek.value - 1 // Monday = 0 leading
        val daysInMonth = visibleMonth.lengthOfMonth()
        val weeks = (leadingBlanks + daysInMonth + 6) / 7
        for (week in 0 until weeks) {
            Row(Modifier.fillMaxWidth()) {
                for (dow in 0 until 7) {
                    val dayNumber = week * 7 + dow - leadingBlanks + 1
                    if (dayNumber in 1..daysInMonth) {
                        DayCell(
                            date = visibleMonth.atDay(dayNumber),
                            start = start,
                            end = end,
                            onClick = onDayClick,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(
    date: LocalDate,
    start: LocalDate?,
    end: LocalDate?,
    onClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val hasRange = start != null && end != null && start != end
    val inRange = start != null && end != null &&
        !date.isBefore(start) && !date.isAfter(end)
    val isStart = date == start
    val isEnd = date == end
    val isEndpoint = isStart || isEnd
    val bandColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)

    Box(
        modifier = modifier.height(44.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (inRange && hasRange) {
            when {
                isStart -> Box(
                    Modifier.align(Alignment.CenterEnd).fillMaxHeight().fillMaxWidth(0.5f)
                        .background(bandColor),
                )
                isEnd -> Box(
                    Modifier.align(Alignment.CenterStart).fillMaxHeight().fillMaxWidth(0.5f)
                        .background(bandColor),
                )
                else -> Box(Modifier.matchParentSize().background(bandColor))
            }
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .then(
                    if (isEndpoint) Modifier.background(MaterialTheme.colorScheme.primary)
                    else Modifier,
                )
                .clickable { onClick(date) },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                color = if (isEndpoint) {
                    MaterialTheme.colorScheme.onPrimary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomPeriodSheet(
    initialStart: LocalDate,
    initialEnd: LocalDate,
    onConfirm: (LocalDate, LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    var rangeStart by remember { mutableStateOf<LocalDate?>(initialStart) }
    var rangeEnd by remember { mutableStateOf<LocalDate?>(initialEnd) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        SheetTitle(stringResource(R.string.custom_period_title))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DateField(text = formatFieldDate(rangeStart), modifier = Modifier.weight(1f))
            Text(
                text = "—",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            DateField(text = formatFieldDate(rangeEnd), modifier = Modifier.weight(1f))
        }

        RangeCalendar(
            start = rangeStart,
            end = rangeEnd,
            onDayClick = { day ->
                val s = rangeStart
                when {
                    s == null || rangeEnd != null -> {
                        rangeStart = day
                        rangeEnd = null
                    }
                    day.isBefore(s) -> {
                        rangeStart = day
                        rangeEnd = s
                    }
                    else -> rangeEnd = day
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = {
                    val s = rangeStart
                    val e = rangeEnd
                    if (s != null && e != null) {
                        onConfirm(s, e)
                    }
                    onDismiss()
                },
                shape = RoundedCornerShape(24.dp),
            ) {
                Text(stringResource(R.string.action_apply))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticlesFilterSheet(
    categories: List<Category>,
    selectedIds: Set<Int>?,
    onApply: (Set<Int>?) -> Unit,
    onDismiss: () -> Unit,
) {
    var checkedIds by remember {
        mutableStateOf(selectedIds ?: categories.map { it.id }.toSet())
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        SheetTitle(stringResource(R.string.filter_articles))
        LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
            items(categories, key = { it.id }) { category ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            checkedIds = if (category.id in checkedIds) {
                                checkedIds - category.id
                            } else {
                                checkedIds + category.id
                            }
                        }
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ListItemRow(
                        item = ListItemModel(
                            id = category.id.toString(),
                            lead = LeadContent.Emoji(category.emoji),
                            title = category.name,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                    Checkbox(
                        checked = category.id in checkedIds,
                        onCheckedChange = null,
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        SheetButton(stringResource(R.string.action_apply)) {
            onApply(if (checkedIds.size == categories.size) null else checkedIds)
            onDismiss()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountFilterSheet(
    accounts: List<Account>,
    selectedAccountId: Int?,
    onSelected: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        SheetTitle(stringResource(R.string.filter_account))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    onSelected(null)
                    onDismiss()
                }
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ListItemRow(
                item = ListItemModel(
                    id = "all",
                    lead = LeadContent.Emoji("💳"),
                    title = stringResource(R.string.all_accounts),
                ),
                modifier = Modifier.weight(1f),
            )
            SelectionMark(selected = selectedAccountId == null, filled = false)
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        accounts.forEach { account ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onSelected(account.id)
                        onDismiss()
                    }
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ListItemRow(
                    item = ListItemModel(
                        id = account.id.toString(),
                        lead = LeadContent.Emoji("🏦"),
                        title = account.name,
                    ),
                    modifier = Modifier.weight(1f),
                )
                SelectionMark(selected = selectedAccountId == account.id, filled = false)
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailSheet(
    data: AnalyticsData,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.analytics_detail),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            DonutChart(
                shares = data.shares,
                caption = stringResource(R.string.analytics_total_for_period),
                amount = data.total.formatted(),
                modifier = Modifier.padding(vertical = 16.dp),
            )
            LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
                itemsIndexed(data.shares, key = { _, share -> share.category.id }) { index, share ->
                    val color = chartColor(index)
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 10.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            LegendDot(color)
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = share.category.name,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = share.amount.formatted(),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                            )
                            Text(
                                text = " (${share.percent}%)",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        LinearProgressIndicator(
                            progress = { share.fraction },
                            color = color,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LegendDot(color: Color) {
    androidx.compose.foundation.Canvas(modifier = Modifier.width(10.dp).height(10.dp)) {
        drawCircle(color = color)
    }
}
