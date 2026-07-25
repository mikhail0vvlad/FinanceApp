package ru.shmr.finance.ui.screens.editor

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.shmr.finance.domain.model.Currency

@Composable
fun AccountEditorScreen(
    state: AccountEditorState,
    onAction: (AccountEditorAction) -> Unit,
    snackbarHostState: SnackbarHostState,
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val balanceFocusRequester = remember { FocusRequester() }

    LaunchedEffect(state.accountId, state.isLoading) {
        if (state.accountId == null && !state.isLoading) {
            withFrameNanos { }
            balanceFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    val leaveInput = {
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
                amount = state.balance,
                currencySymbol = state.currency.symbol,
                error = state.errors[AccountEditorField.BALANCE],
                enabled = !state.isSaving && !state.isLoading,
                focusRequester = balanceFocusRequester,
                onAmountChanged = { onAction(AccountEditorAction.BalanceChanged(it)) },
                onDone = { onAction(AccountEditorAction.Save) },
                label = "Корректировка баланса",
            )

            AccountIdentityFields(
                emoji = state.emoji,
                name = state.name,
                nameError = state.errors[AccountEditorField.NAME],
                enabled = !state.isSaving && !state.isLoading,
                onEmojiChanged = { onAction(AccountEditorAction.EmojiChanged(it)) },
                onNameChanged = { onAction(AccountEditorAction.NameChanged(it)) },
            )

            EditorParameterRow(
                icon = Icons.Outlined.CurrencyExchange,
                label = "Валюта",
                value = state.currency.shortLabel(),
                error = state.errors[AccountEditorField.CURRENCY],
                enabled = !state.isSaving && !state.isLoading,
                onClick = {
                    leaveInput()
                    onAction(AccountEditorAction.OpenCurrencyPicker)
                },
            )
        }

        ConfirmButton(
            loading = state.isSaving || state.isLoading,
            enabled = !state.isSaving && !state.isLoading,
            onClick = {
                leaveInput()
                onAction(AccountEditorAction.Save)
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

    if (state.activePicker == AccountEditorPicker.CURRENCY) {
        CurrencyPickerSheet(
            selected = state.currency,
            onSelected = { onAction(AccountEditorAction.CurrencySelected(it)) },
            onDismiss = { onAction(AccountEditorAction.DismissPicker) },
        )
    }
}

@Composable
private fun AccountIdentityFields(
    emoji: String,
    name: String,
    nameError: String?,
    enabled: Boolean,
    onEmojiChanged: (String) -> Unit,
    onNameChanged: (String) -> Unit,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            OutlinedTextField(
                value = emoji,
                onValueChange = onEmojiChanged,
                enabled = enabled,
                singleLine = true,
                shape = CircleShape,
                textStyle = MaterialTheme.typography.headlineSmall,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                modifier = Modifier.width(72.dp),
            )
            OutlinedTextField(
                value = name,
                onValueChange = onNameChanged,
                enabled = enabled,
                singleLine = true,
                label = { Text("Название счёта") },
                isError = nameError != null,
                supportingText = nameError?.let { message -> { Text(message) } },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done,
                ),
                shape = RoundedCornerShape(20.dp),
                modifier = Modifier.weight(1f),
            )
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CurrencyPickerSheet(
    selected: Currency,
    onSelected: (Currency) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Text(
            text = "Валюта",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 360.dp),
        ) {
            items(Currency.entries, key = Currency::code) { currency ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelected(currency) }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = currency.flagEmoji(), fontSize = 22.sp)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = currency.code,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                        )
                        Text(
                            text = currency.displayName(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (currency == selected) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Выбрано",
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

private fun Currency.displayName(): String = when (this) {
    Currency.RUB -> "Российский рубль"
    Currency.USD -> "Доллар США"
    Currency.EUR -> "Евро"
    Currency.GBP -> "Фунт стерлингов"
    Currency.CNY -> "Китайский юань"
}

private fun Currency.flagEmoji(): String = when (this) {
    Currency.RUB -> "🇷🇺"
    Currency.USD -> "🇺🇸"
    Currency.EUR -> "🇪🇺"
    Currency.GBP -> "🇬🇧"
    Currency.CNY -> "🇨🇳"
}

private fun Currency.shortLabel(): String = when (this) {
    Currency.RUB -> "Руб."
    Currency.USD -> "$"
    Currency.EUR -> "€"
    Currency.GBP -> "£"
    Currency.CNY -> "¥"
}
