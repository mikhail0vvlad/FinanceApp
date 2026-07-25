package ru.shmr.finance.ui.screens.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.shmr.finance.core.result.AppResult
import ru.shmr.finance.domain.repository.AccountsRepository
import ru.shmr.finance.domain.repository.CategoriesRepository
import ru.shmr.finance.domain.repository.TransactionsRepository
import ru.shmr.finance.domain.validation.TransactionDraft
import ru.shmr.finance.domain.validation.TransactionDraftValidator
import ru.shmr.finance.domain.validation.TransactionField

class TransactionEditorViewModel(
    private val isIncome: Boolean,
    private val localId: String?,
    private val accountsRepository: AccountsRepository,
    private val categoriesRepository: CategoriesRepository,
    private val transactionsRepository: TransactionsRepository,
    restoredDraft: TransactionEditorDraftSnapshot = TransactionEditorDraftSnapshot(),
) : ViewModel() {

    private val _state = MutableStateFlow(
        restoredDraft.restore(isIncome = isIncome, editingLocalId = localId),
    )
    val state: StateFlow<TransactionEditorState> = _state.asStateFlow()

    private val _effects = MutableSharedFlow<TransactionEditorEffect>(extraBufferCapacity = 1)
    val effects: SharedFlow<TransactionEditorEffect> = _effects.asSharedFlow()

    init {
        viewModelScope.launch {
            combine(
                accountsRepository.observeAccounts(),
                categoriesRepository.observeCategories(),
            ) { accounts, categories ->
                accounts to categories.filter { it.isIncome == isIncome }
            }.collect { (accounts, categories) ->
                _state.update { current ->
                    current.copy(
                        accounts = accounts,
                        categories = categories,
                        accountId = current.accountId ?: accounts.firstOrNull()?.id,
                        categoryId = current.categoryId ?: categories.firstOrNull()?.id,
                    )
                }
            }
        }
        viewModelScope.launch {
            val existing = localId?.let { transactionsRepository.getTransaction(it) }
            _state.update { current ->
                if (restoredDraft.initialized || existing == null) {
                    current.copy(isLoading = false)
                } else {
                    current.copy(
                        isLoading = false,
                        accountId = existing.accountId,
                        categoryId = existing.category.id,
                        amount = existing.amount.amount.stripTrailingZeros().toPlainString(),
                        date = existing.dateTime.toLocalDate(),
                        time = existing.dateTime.toLocalTime().withSecond(0).withNano(0),
                        comment = existing.comment.orEmpty(),
                    )
                }
            }
            accountsRepository.refreshAccounts()
            categoriesRepository.refreshCategories()
        }
    }

    fun onAction(action: TransactionEditorAction) {
        when (action) {
            is TransactionEditorAction.AccountSelected ->
                _state.update {
                    it.copy(
                        accountId = action.id,
                        errors = it.errors - TransactionField.ACCOUNT,
                        activePicker = null,
                    )
                }
            is TransactionEditorAction.CategorySelected ->
                _state.update {
                    it.copy(
                        categoryId = action.id,
                        errors = it.errors - TransactionField.CATEGORY,
                        activePicker = null,
                    )
                }
            is TransactionEditorAction.AmountChanged ->
                _state.update {
                    it.copy(
                        amount = action.value,
                        errors = it.errors - TransactionField.AMOUNT,
                    )
                }
            is TransactionEditorAction.CommentChanged -> _state.update { it.copy(comment = action.value) }
            is TransactionEditorAction.OpenPicker ->
                _state.update {
                    it.copy(
                        activePicker = action.picker,
                        pendingDate = if (action.picker == TransactionEditorPicker.DATE) {
                            it.date
                        } else {
                            null
                        },
                        pendingTime = if (action.picker == TransactionEditorPicker.TIME) {
                            it.time
                        } else {
                            null
                        },
                        pickerError = null,
                    )
                }
            is TransactionEditorAction.PickerDateChanged ->
                _state.update {
                    if (it.activePicker == TransactionEditorPicker.DATE) {
                        it.copy(pendingDate = action.value, pickerError = null)
                    } else {
                        it
                    }
                }
            is TransactionEditorAction.PickerTimeChanged ->
                _state.update {
                    if (it.activePicker == TransactionEditorPicker.TIME) {
                        it.copy(pendingTime = action.value, pickerError = null)
                    } else {
                        it
                    }
                }
            TransactionEditorAction.ApplyPicker -> applyPicker()
            TransactionEditorAction.DismissPicker ->
                _state.update {
                    it.copy(
                        activePicker = null,
                        pendingDate = null,
                        pendingTime = null,
                        pickerError = null,
                    )
                }
            TransactionEditorAction.Save -> save()
        }
    }

    private fun applyPicker() {
        _state.update { current ->
            when (current.activePicker) {
                TransactionEditorPicker.DATE -> {
                    val value = current.pendingDate
                    val error = if (value == null) {
                        "Выберите дату"
                    } else {
                        TransactionDraftValidator.validateDate(value)
                    }
                    if (error != null) {
                        current.copy(pickerError = error)
                    } else {
                        current.copy(
                            date = requireNotNull(value),
                            activePicker = null,
                            pendingDate = null,
                            pickerError = null,
                            errors = current.errors - TransactionField.DATE,
                        )
                    }
                }
                TransactionEditorPicker.TIME -> {
                    val value = current.pendingTime
                    val error = if (value == null) {
                        "Введите время"
                    } else {
                        TransactionDraftValidator.validateTime(value)
                    }
                    if (error != null) {
                        current.copy(pickerError = error)
                    } else {
                        current.copy(
                            time = requireNotNull(value),
                            activePicker = null,
                            pendingTime = null,
                            pickerError = null,
                            errors = current.errors - TransactionField.TIME,
                        )
                    }
                }
                TransactionEditorPicker.CATEGORY,
                TransactionEditorPicker.ACCOUNT,
                null,
                -> current
            }
        }
    }

    private fun save() {
        if (_state.value.isSaving) return
        val current = _state.value
        val draft = TransactionDraft(
            accountId = current.accountId,
            categoryId = current.categoryId,
            amount = current.amount,
            date = current.date,
            time = current.time,
            comment = current.comment,
        )
        val validation = TransactionDraftValidator.validate(draft)
        if (!validation.isValid) {
            _state.update { it.copy(errors = validation.errors) }
            return
        }
        _state.update { it.copy(isSaving = true, activePicker = null) }
        viewModelScope.launch {
            when (transactionsRepository.saveTransaction(draft, localId)) {
                is AppResult.Success -> _effects.emit(TransactionEditorEffect.Saved)
                is AppResult.Failure -> {
                    _state.update { it.copy(isSaving = false) }
                    _effects.emit(
                        TransactionEditorEffect.ShowMessage("Не удалось сохранить операцию"),
                    )
                }
            }
        }
    }
}
