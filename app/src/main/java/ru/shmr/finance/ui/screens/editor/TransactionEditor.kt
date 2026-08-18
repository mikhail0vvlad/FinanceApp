package ru.shmr.finance.ui.screens.editor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter
import ru.shmr.finance.R
import ru.shmr.finance.domain.validation.TransactionField

private val EditorTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun TransactionEditorScreen(
    state: TransactionEditorState,
    onAction: (TransactionEditorAction) -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val amountFocusRequester = remember { FocusRequester() }
    val leaveAmountInput = {
        focusManager.clearFocus()
        keyboardController?.hide()
        Unit
    }
    LaunchedEffect(state.editingLocalId, state.isLoading) {
        if (state.editingLocalId == null && !state.isLoading) {
            withFrameNanos { }
            amountFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }
    EditorLayout(
        state = state,
        onAction = onAction,
        snackbarHostState = snackbarHostState,
        amountFocusRequester = amountFocusRequester,
        leaveAmountInput = leaveAmountInput,
    )
    TransactionEditorPickers(state, onAction)
}

@Composable
private fun EditorLayout(
    state: TransactionEditorState,
    onAction: (TransactionEditorAction) -> Unit,
    snackbarHostState: SnackbarHostState,
    amountFocusRequester: FocusRequester,
    leaveAmountInput: () -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxWidth().fillMaxHeight(0.96f).imePadding(),
    ) {
        EditorForm(state, onAction, amountFocusRequester, leaveAmountInput)
        ConfirmButton(
            loading = state.isSaving || state.isLoading,
            enabled = !state.isSaving && !state.isLoading,
            onClick = {
                leaveAmountInput()
                onAction(TransactionEditorAction.Save)
            },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 24.dp, bottom = 4.dp),
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 88.dp),
        )
    }
}

@Composable
private fun EditorForm(
    state: TransactionEditorState,
    onAction: (TransactionEditorAction) -> Unit,
    amountFocusRequester: FocusRequester,
    leaveAmountInput: () -> Unit,
) {
    val account = state.accounts.find { it.id == state.accountId }
    val category = state.categories.find { it.id == state.categoryId }
    val locale = LocalConfiguration.current.locales[0]
    val dateFormatter = remember(locale) { DateTimeFormatter.ofPattern("d MMMM", locale) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(bottom = 96.dp),
    ) {
        HeroAmount(
            amount = state.amount,
            currencySymbol = account?.balance?.currency?.symbol.orEmpty(),
            error = state.errors[TransactionField.AMOUNT]?.localized(),
            enabled = !state.isSaving && !state.isLoading,
            focusRequester = amountFocusRequester,
            onAmountChanged = { onAction(TransactionEditorAction.AmountChanged(it)) },
            onDone = { onAction(TransactionEditorAction.Save) },
        )
        val rows = listOf(
            EditorRow(Icons.Outlined.Sell, R.string.editor_category, category?.name.orEmpty(), TransactionField.CATEGORY, TransactionEditorPicker.CATEGORY),
            EditorRow(Icons.Outlined.CalendarMonth, R.string.editor_date, state.date.format(dateFormatter), TransactionField.DATE, TransactionEditorPicker.DATE),
            EditorRow(Icons.Outlined.Schedule, R.string.editor_time, state.time.format(EditorTimeFormatter), TransactionField.TIME, TransactionEditorPicker.TIME),
            EditorRow(Icons.Outlined.AccountBalanceWallet, R.string.editor_account, account?.name.orEmpty(), TransactionField.ACCOUNT, TransactionEditorPicker.ACCOUNT),
        )
        rows.forEach { row ->
            EditorParameterRow(
                icon = row.icon,
                label = stringResource(row.labelRes),
                value = row.value,
                error = state.errors[row.field]?.localized(),
                enabled = !state.isSaving,
                onClick = {
                    leaveAmountInput()
                    onAction(TransactionEditorAction.OpenPicker(row.picker))
                },
            )
        }
        CommentField(state, onAction)
    }
}

@Composable
private fun CommentField(state: TransactionEditorState, onAction: (TransactionEditorAction) -> Unit) {
    OutlinedTextField(
        value = state.comment,
        onValueChange = { onAction(TransactionEditorAction.CommentChanged(it)) },
        enabled = !state.isSaving,
        label = { Text(stringResource(R.string.editor_comment)) },
        leadingIcon = { Icon(Icons.Outlined.ChatBubbleOutline, null) },
        minLines = 2,
        maxLines = 4,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp),
    )
}

@Composable
private fun TransactionEditorPickers(
    state: TransactionEditorState,
    onAction: (TransactionEditorAction) -> Unit,
) {
    when (state.activePicker) {
        TransactionEditorPicker.CATEGORY -> CategoryPickerSheet(
            state.categories,
            state.categoryId,
            { onAction(TransactionEditorAction.CategorySelected(it)) },
            { onAction(TransactionEditorAction.DismissPicker) },
        )
        TransactionEditorPicker.ACCOUNT -> AccountPickerSheet(
            state.accounts,
            state.accountId,
            { onAction(TransactionEditorAction.AccountSelected(it)) },
            { onAction(TransactionEditorAction.DismissPicker) },
        )
        TransactionEditorPicker.DATE -> DatePickerOverlay(
            state.pendingDate,
            state.pickerError?.localized(),
            { onAction(TransactionEditorAction.PickerDateChanged(it)) },
            { onAction(TransactionEditorAction.DismissPicker) },
            { onAction(TransactionEditorAction.ApplyPicker) },
        )
        TransactionEditorPicker.TIME -> TimePickerSheet(
            state.pendingTime ?: state.time,
            state.pickerError?.localized(),
            { onAction(TransactionEditorAction.PickerTimeChanged(it)) },
            { onAction(TransactionEditorAction.DismissPicker) },
            { onAction(TransactionEditorAction.ApplyPicker) },
        )
        null -> Unit
    }
}

private data class EditorRow(
    val icon: ImageVector,
    val labelRes: Int,
    val value: String,
    val field: TransactionField,
    val picker: TransactionEditorPicker,
)
