package ru.shmr.finance.ui.screens.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import ru.shmr.finance.R
import ru.shmr.finance.domain.model.Account
import ru.shmr.finance.domain.model.Category
import ru.shmr.finance.domain.validation.TransactionDraftValidator
import ru.shmr.finance.domain.validation.TransactionField
import ru.shmr.finance.domain.validation.TransactionValidationError

private val EditorTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionEditorScreen(
    state: TransactionEditorState,
    onAction: (TransactionEditorAction) -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val amountFocusRequester = remember { FocusRequester() }
    val selectedCategory = state.categories.find { it.id == state.categoryId }
    val selectedAccount = state.accounts.find { it.id == state.accountId }
    val locale = LocalConfiguration.current.locales[0]
    val dateFormatter = remember(locale) { DateTimeFormatter.ofPattern("d MMMM", locale) }

    LaunchedEffect(state.editingLocalId, state.isLoading) {
        if (state.editingLocalId == null && !state.isLoading) {
            withFrameNanos { }
            amountFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    val leaveAmountInput = {
        focusManager.clearFocus()
        keyboardController?.hide()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .fillMaxHeight(0.96f)
            .imePadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 96.dp),
        ) {
            HeroAmount(
                amount = state.amount,
                currencySymbol = selectedAccount?.balance?.currency?.symbol.orEmpty(),
                error = state.errors[TransactionField.AMOUNT]?.localized(),
                enabled = !state.isSaving && !state.isLoading,
                focusRequester = amountFocusRequester,
                onAmountChanged = { onAction(TransactionEditorAction.AmountChanged(it)) },
                onDone = { onAction(TransactionEditorAction.Save) },
            )

            EditorParameterRow(
                icon = Icons.Outlined.Sell,
                label = stringResource(R.string.editor_category),
                value = selectedCategory?.name.orEmpty(),
                error = state.errors[TransactionField.CATEGORY]?.localized(),
                enabled = !state.isSaving,
                onClick = {
                    leaveAmountInput()
                    onAction(
                        TransactionEditorAction.OpenPicker(
                            TransactionEditorPicker.CATEGORY,
                        ),
                    )
                },
            )
            EditorParameterRow(
                icon = Icons.Outlined.CalendarMonth,
                label = stringResource(R.string.editor_date),
                value = state.date.format(dateFormatter),
                error = state.errors[TransactionField.DATE]?.localized(),
                enabled = !state.isSaving,
                onClick = {
                    leaveAmountInput()
                    onAction(
                        TransactionEditorAction.OpenPicker(
                            TransactionEditorPicker.DATE,
                        ),
                    )
                },
            )
            EditorParameterRow(
                icon = Icons.Outlined.Schedule,
                label = stringResource(R.string.editor_time),
                value = state.time.format(EditorTimeFormatter),
                error = state.errors[TransactionField.TIME]?.localized(),
                enabled = !state.isSaving,
                onClick = {
                    leaveAmountInput()
                    onAction(
                        TransactionEditorAction.OpenPicker(
                            TransactionEditorPicker.TIME,
                        ),
                    )
                },
            )
            EditorParameterRow(
                icon = Icons.Outlined.AccountBalanceWallet,
                label = stringResource(R.string.editor_account),
                value = selectedAccount?.name.orEmpty(),
                error = state.errors[TransactionField.ACCOUNT]?.localized(),
                enabled = !state.isSaving,
                onClick = {
                    leaveAmountInput()
                    onAction(
                        TransactionEditorAction.OpenPicker(
                            TransactionEditorPicker.ACCOUNT,
                        ),
                    )
                },
            )

            OutlinedTextField(
                value = state.comment,
                onValueChange = { onAction(TransactionEditorAction.CommentChanged(it)) },
                enabled = !state.isSaving,
                label = { Text(stringResource(R.string.editor_comment)) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.ChatBubbleOutline,
                        contentDescription = null,
                    )
                },
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
            )
        }

        ConfirmButton(
            loading = state.isSaving || state.isLoading,
            enabled = !state.isSaving && !state.isLoading,
            onClick = {
                leaveAmountInput()
                onAction(TransactionEditorAction.Save)
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 24.dp, bottom = 4.dp),
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 88.dp),
        )
    }

    when (state.activePicker) {
        TransactionEditorPicker.CATEGORY -> CategoryPickerSheet(
            categories = state.categories,
            selectedId = state.categoryId,
            onSelected = {
                onAction(TransactionEditorAction.CategorySelected(it))
            },
            onDismiss = { onAction(TransactionEditorAction.DismissPicker) },
        )
        TransactionEditorPicker.ACCOUNT -> AccountPickerSheet(
            accounts = state.accounts,
            selectedId = state.accountId,
            onSelected = {
                onAction(TransactionEditorAction.AccountSelected(it))
            },
            onDismiss = { onAction(TransactionEditorAction.DismissPicker) },
        )
        TransactionEditorPicker.DATE -> DatePickerOverlay(
            value = state.pendingDate,
            error = state.pickerError?.localized(),
            onValueChanged = {
                onAction(TransactionEditorAction.PickerDateChanged(it))
            },
            onCancel = { onAction(TransactionEditorAction.DismissPicker) },
            onApply = { onAction(TransactionEditorAction.ApplyPicker) },
        )
        TransactionEditorPicker.TIME -> TimePickerSheet(
            value = state.pendingTime ?: state.time,
            error = state.pickerError?.localized(),
            onValueChanged = {
                onAction(TransactionEditorAction.PickerTimeChanged(it))
            },
            onCancel = { onAction(TransactionEditorAction.DismissPicker) },
            onApply = { onAction(TransactionEditorAction.ApplyPicker) },
        )
        null -> Unit
    }
}

@Composable
internal fun HeroAmount(
    amount: String,
    currencySymbol: String,
    error: String?,
    enabled: Boolean,
    focusRequester: FocusRequester,
    onAmountChanged: (String) -> Unit,
    onDone: () -> Unit,
    label: String? = null,
) {
    val amountWidth = (amount.length.coerceAtLeast(1) * 32)
        .coerceIn(48, 230)
        .dp
    val amountStyle = TextStyle(
        color = MaterialTheme.colorScheme.onSurface,
        fontSize = 48.sp,
        lineHeight = 56.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.End,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 150.dp)
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (label != null) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            BasicTextField(
                value = amount,
                onValueChange = onAmountChanged,
                enabled = enabled,
                singleLine = true,
                textStyle = amountStyle,
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(onDone = { onDone() }),
                decorationBox = { innerTextField ->
                    Box(contentAlignment = Alignment.CenterEnd) {
                        if (amount.isBlank()) {
                            Text(
                                text = "0",
                                style = amountStyle,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        innerTextField()
                    }
                },
                modifier = Modifier
                    .width(amountWidth)
                    .focusRequester(focusRequester),
            )
            if (currencySymbol.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Text(text = currencySymbol, style = amountStyle)
            }
        }
        HorizontalDivider(
            modifier = Modifier
                .padding(top = 4.dp)
                .width(220.dp),
            color = if (error == null) {
                MaterialTheme.colorScheme.outlineVariant
            } else {
                MaterialTheme.colorScheme.error
            },
        )
        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
internal fun EditorParameterRow(
    icon: ImageVector,
    label: String,
    value: String,
    enabled: Boolean,
    onClick: () -> Unit,
    error: String? = null,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .clickable(enabled = enabled, onClick = onClick)
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainer,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(22.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                border = BorderStroke(
                    1.dp,
                    if (error == null) {
                        MaterialTheme.colorScheme.outlineVariant
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                ),
            ) {
                Text(
                    text = value.ifBlank { stringResource(R.string.action_select) },
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .widthIn(max = 190.dp)
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                )
            }
        }
        if (error != null) {
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 76.dp, end = 20.dp, bottom = 6.dp),
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
internal fun ConfirmButton(
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FloatingActionButton(
        onClick = { if (enabled) onClick() },
        modifier = modifier.size(58.dp),
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.onSurface,
        contentColor = MaterialTheme.colorScheme.surface,
    ) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.size(24.dp),
                color = MaterialTheme.colorScheme.surface,
                strokeWidth = 2.dp,
            )
        } else {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = stringResource(R.string.cd_save),
                modifier = Modifier.size(30.dp),
            )
        }
    }
}

private data class PickerItem(
    val id: Int,
    val emoji: String,
    val title: String,
    val subtitle: String? = null,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryPickerSheet(
    categories: List<Category>,
    selectedId: Int?,
    onSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    SelectionPickerSheet(
        title = stringResource(R.string.editor_category),
        items = categories.map { PickerItem(it.id, it.emoji, it.name) },
        selectedId = selectedId,
        onSelected = onSelected,
        onDismiss = onDismiss,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AccountPickerSheet(
    accounts: List<Account>,
    selectedId: Int?,
    onSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    SelectionPickerSheet(
        title = stringResource(R.string.editor_account),
        items = accounts.map {
            PickerItem(
                id = it.id,
                emoji = it.emoji,
                title = it.name,
                subtitle = it.balance.formatted(),
            )
        },
        selectedId = selectedId,
        onSelected = onSelected,
        onDismiss = onDismiss,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionPickerSheet(
    title: String,
    items: List<PickerItem>,
    selectedId: Int?,
    onSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 480.dp),
        ) {
            items(items, key = { it.id }) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelected(item.id) }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Surface(
                        modifier = Modifier.size(40.dp),
                        shape = CircleShape,
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant,
                        ),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Text(text = item.emoji, fontSize = 20.sp)
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = item.title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        item.subtitle?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (item.id == selectedId) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = stringResource(R.string.cd_selected),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DatePickerOverlay(
    value: LocalDate?,
    error: String?,
    onValueChanged: (LocalDate?) -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit,
) {
    val selectedDate = value ?: LocalDate.now()
    val locale = LocalConfiguration.current.locales[0]
    val dateFormatter = remember(locale) { DateTimeFormatter.ofPattern("d MMMM", locale) }
    val selectedMillis = selectedDate
        .atStartOfDay(ZoneOffset.UTC)
        .toInstant()
        .toEpochMilli()
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = selectedMillis,
        initialDisplayedMonthMillis = selectedMillis,
        yearRange = TransactionDraftValidator.supportedDateRange.start.year..
            TransactionDraftValidator.supportedDateRange.endInclusive.year,
    )

    LaunchedEffect(pickerState.selectedDateMillis) {
        onValueChanged(
            pickerState.selectedDateMillis?.let {
                Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()
            },
        )
    }

    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
        ),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier.fillMaxSize(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding(),
            ) {
                IconButton(
                    onClick = onCancel,
                    modifier = Modifier.padding(start = 8.dp, top = 4.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = stringResource(R.string.cd_clear_date),
                    )
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.editor_choose_date),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = value?.format(dateFormatter).orEmpty(),
                            style = MaterialTheme.typography.headlineMedium,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                DatePicker(
                    state = pickerState,
                    title = null,
                    headline = null,
                    showModeToggle = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                )
                if (error != null) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                }
                PickerActions(
                    onCancel = onCancel,
                    onApply = onApply,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TimePickerSheet(
    value: LocalTime,
    error: String?,
    onValueChanged: (LocalTime) -> Unit,
    onCancel: () -> Unit,
    onApply: () -> Unit,
) {
    val pickerState = rememberTimePickerState(
        initialHour = value.hour,
        initialMinute = value.minute,
        is24Hour = true,
    )

    LaunchedEffect(pickerState.hour, pickerState.minute) {
        onValueChanged(LocalTime.of(pickerState.hour, pickerState.minute))
    }

    ModalBottomSheet(
        onDismissRequest = onCancel,
        modifier = Modifier.imePadding(),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.editor_enter_time),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TimeInput(
                state = pickerState,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 16.dp),
            )
            if (error != null) {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            PickerActions(
                onCancel = onCancel,
                onApply = onApply,
                modifier = Modifier.padding(top = 8.dp, bottom = 40.dp),
            )
        }
    }
}

@Composable
private fun PickerActions(
    onCancel: () -> Unit,
    onApply: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onCancel) {
            Text(stringResource(R.string.action_cancel))
        }
        TextButton(onClick = onApply) {
            Text(stringResource(R.string.action_apply))
        }
    }
}

@Composable
private fun TransactionValidationError.localized(): String = stringResource(
    when (this) {
        TransactionValidationError.ACCOUNT_REQUIRED -> R.string.editor_error_account_required
        TransactionValidationError.CATEGORY_REQUIRED -> R.string.editor_error_category_required
        TransactionValidationError.AMOUNT_MUST_BE_POSITIVE -> R.string.editor_error_positive_amount
        TransactionValidationError.DATE_TIME_INVALID -> R.string.editor_error_date_time
        TransactionValidationError.DATE_OUT_OF_RANGE -> R.string.editor_error_date_range
        TransactionValidationError.TIME_REQUIRES_MINUTE_PRECISION ->
            R.string.editor_error_time_precision
        TransactionValidationError.DATE_REQUIRED -> R.string.editor_error_date_required
        TransactionValidationError.TIME_REQUIRED -> R.string.editor_error_time_required
    },
)
