package ru.shmr.finance.ui.screens.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.math.BigDecimal
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.shmr.finance.core.result.AppResult
import ru.shmr.finance.domain.model.AccountDraft
import ru.shmr.finance.domain.model.Currency
import ru.shmr.finance.domain.repository.AccountsRepository
import ru.shmr.finance.domain.validation.MoneyInputParser

class AccountEditorViewModel(
    private val accountId: Int?,
    private val accountsRepository: AccountsRepository,
    defaultCurrencyProvider: DefaultAccountCurrencyProvider =
        RubDefaultAccountCurrencyProvider,
    private val restoredDraft: AccountEditorDraftSnapshot = AccountEditorDraftSnapshot(),
) : ViewModel() {

    private val defaultCurrency = defaultCurrencyProvider.get()
    private val _state = MutableStateFlow(
        restoredDraft.restore(accountId = accountId, defaultCurrency = defaultCurrency),
    )
    val state: StateFlow<AccountEditorState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<AccountEditorEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<AccountEditorEffect> = _effects.asSharedFlow()

    init {
        if (accountId == null) {
            _state.update { it.copy(isLoading = false) }
            viewModelScope.launch { accountsRepository.refreshAccounts() }
        } else {
            loadAccount(accountId)
        }
    }

    fun onAction(action: AccountEditorAction) {
        when (action) {
            is AccountEditorAction.NameChanged ->
                _state.update {
                    it.copy(
                        name = action.value,
                        errors = it.errors - AccountEditorField.NAME,
                    )
                }
            is AccountEditorAction.EmojiChanged ->
                _state.update { it.copy(emoji = action.value.take(10)) }
            is AccountEditorAction.BalanceChanged ->
                _state.update {
                    it.copy(
                        balance = action.value,
                        errors = it.errors - AccountEditorField.BALANCE,
                    )
                }
            is AccountEditorAction.CurrencySelected -> selectCurrency(action.value)
            AccountEditorAction.OpenCurrencyPicker -> openCurrencyPicker()
            AccountEditorAction.DismissPicker ->
                _state.update { it.copy(activePicker = null) }
            AccountEditorAction.Save -> save()
        }
    }

    private fun loadAccount(id: Int) {
        viewModelScope.launch {
            val accounts = when (val result = accountsRepository.getAccounts()) {
                is AppResult.Success -> result.data
                is AppResult.Failure -> emptyList()
            }
            val account = accounts.find { it.id == id }
            if (account == null) {
                _state.update { it.copy(isLoading = false) }
                _effects.emit(AccountEditorEffect.ShowMessage(AccountEditorMessage.ACCOUNT_NOT_FOUND))
                return@launch
            }
            val hasHistory = accountsRepository.hasTransactions(id)
            _state.update { current ->
                current.copy(
                    isLoading = false,
                    name = if (restoredDraft.initialized) current.name else account.name,
                    emoji = if (restoredDraft.initialized) current.emoji else account.emoji,
                    balance = if (restoredDraft.initialized) {
                        current.balance
                    } else {
                        account.balance.amount.stripTrailingZeros().toPlainString()
                    },
                    currency = if (restoredDraft.initialized) {
                        current.currency
                    } else {
                        account.balance.currency
                    },
                    originalCurrency = account.balance.currency,
                    hasTransactionHistory = hasHistory,
                    activePicker = current.activePicker.takeUnless { hasHistory },
                )
            }
        }
    }

    private fun openCurrencyPicker() {
        val current = _state.value
        if (!current.canChangeCurrency) {
            _state.update {
                it.copy(
                    errors = it.errors +
                        (AccountEditorField.CURRENCY to AccountEditorError.CURRENCY_HAS_HISTORY),
                )
            }
            _effects.tryEmit(
                AccountEditorEffect.ShowMessage(AccountEditorMessage.CURRENCY_HAS_HISTORY),
            )
            return
        }
        _state.update {
            it.copy(
                activePicker = AccountEditorPicker.CURRENCY,
                errors = it.errors - AccountEditorField.CURRENCY,
            )
        }
    }

    private fun selectCurrency(currency: Currency) {
        val current = _state.value
        if (
            !current.canChangeCurrency &&
            current.originalCurrency != null &&
            currency != current.originalCurrency
        ) {
            _state.update {
                it.copy(
                    activePicker = null,
                    errors = it.errors +
                        (AccountEditorField.CURRENCY to AccountEditorError.CURRENCY_HAS_HISTORY),
                )
            }
            _effects.tryEmit(
                AccountEditorEffect.ShowMessage(AccountEditorMessage.CURRENCY_HAS_HISTORY),
            )
            return
        }
        _state.update {
            it.copy(
                currency = currency,
                activePicker = null,
                errors = it.errors - AccountEditorField.CURRENCY,
            )
        }
    }

    private fun save() {
        val current = _state.value
        if (current.isSaving || current.isLoading) return

        val balance = MoneyInputParser.parse(current.balance)
        val errors = buildMap {
            if (current.name.isBlank()) {
                put(AccountEditorField.NAME, AccountEditorError.NAME_REQUIRED)
            }
            if (balance == null || balance < BigDecimal.ZERO) {
                put(AccountEditorField.BALANCE, AccountEditorError.BALANCE_NON_NEGATIVE)
            }
            if (
                current.hasTransactionHistory &&
                current.originalCurrency != null &&
                current.currency != current.originalCurrency
            ) {
                put(AccountEditorField.CURRENCY, AccountEditorError.CURRENCY_HAS_HISTORY)
            }
        }
        if (errors.isNotEmpty()) {
            _state.update { it.copy(errors = errors, activePicker = null) }
            return
        }

        _state.update { it.copy(isSaving = true, activePicker = null, errors = emptyMap()) }
        viewModelScope.launch {
            val result = accountsRepository.saveAccount(
                AccountDraft(
                    id = accountId,
                    name = current.name,
                    emoji = current.emoji,
                    balance = requireNotNull(balance),
                    currency = current.currency,
                ),
            )
            when (result) {
                is AppResult.Success -> _effects.emit(AccountEditorEffect.Saved)
                is AppResult.Failure -> {
                    _state.update { it.copy(isSaving = false) }
                    _effects.emit(AccountEditorEffect.ShowError(result.error))
                }
            }
        }
    }
}
