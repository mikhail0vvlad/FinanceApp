package ru.shmr.finance.ui.screens.editor

import androidx.compose.runtime.Immutable
import ru.shmr.finance.domain.model.Currency
import ru.shmr.finance.domain.model.AppError

private const val DEFAULT_ACCOUNT_EMOJI = "💳"

fun interface DefaultAccountCurrencyProvider {
    fun get(): Currency
}

object RubDefaultAccountCurrencyProvider : DefaultAccountCurrencyProvider {
    override fun get(): Currency = Currency.RUB
}

@Immutable
data class AccountEditorState(
    val accountId: Int?,
    val isLoading: Boolean,
    val isSaving: Boolean = false,
    val name: String = "",
    val emoji: String = DEFAULT_ACCOUNT_EMOJI,
    val balance: String = "",
    val currency: Currency,
    val originalCurrency: Currency? = null,
    val hasTransactionHistory: Boolean = false,
    val errors: Map<AccountEditorField, AccountEditorError> = emptyMap(),
    val activePicker: AccountEditorPicker? = null,
) {
    val canChangeCurrency: Boolean
        get() = !hasTransactionHistory
}

enum class AccountEditorField {
    NAME,
    BALANCE,
    CURRENCY,
}

enum class AccountEditorPicker {
    CURRENCY,
}

enum class AccountEditorError {
    NAME_REQUIRED,
    BALANCE_NON_NEGATIVE,
    CURRENCY_HAS_HISTORY,
}

enum class AccountEditorMessage {
    ACCOUNT_NOT_FOUND,
    CURRENCY_HAS_HISTORY,
}

sealed interface AccountEditorAction {
    data class NameChanged(val value: String) : AccountEditorAction
    data class EmojiChanged(val value: String) : AccountEditorAction
    data class BalanceChanged(val value: String) : AccountEditorAction
    data class CurrencySelected(val value: Currency) : AccountEditorAction
    data object OpenCurrencyPicker : AccountEditorAction
    data object DismissPicker : AccountEditorAction
    data object Save : AccountEditorAction
}

sealed interface AccountEditorEffect {
    data object Saved : AccountEditorEffect
    data class ShowMessage(val message: AccountEditorMessage) : AccountEditorEffect
    data class ShowError(val error: AppError) : AccountEditorEffect
}

data class AccountEditorDraftSnapshot(
    val initialized: Boolean = false,
    val name: String = "",
    val emoji: String = DEFAULT_ACCOUNT_EMOJI,
    val balance: String = "",
    val currencyCode: String = "",
    val activePicker: String = "",
) {
    fun restore(
        accountId: Int?,
        defaultCurrency: Currency,
    ): AccountEditorState = AccountEditorState(
        accountId = accountId,
        isLoading = accountId != null,
        name = name,
        emoji = emoji,
        balance = balance,
        currency = Currency.entries.find { it.code == currencyCode } ?: defaultCurrency,
        activePicker = activePicker
            .takeIf(String::isNotEmpty)
            ?.let { runCatching { AccountEditorPicker.valueOf(it) }.getOrNull() },
    )

    companion object {
        fun from(state: AccountEditorState) = AccountEditorDraftSnapshot(
            initialized = !state.isLoading,
            name = state.name,
            emoji = state.emoji,
            balance = state.balance,
            currencyCode = state.currency.code,
            activePicker = state.activePicker?.name.orEmpty(),
        )
    }
}
