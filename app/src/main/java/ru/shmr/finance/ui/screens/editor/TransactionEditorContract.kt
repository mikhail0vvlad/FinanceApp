package ru.shmr.finance.ui.screens.editor

import androidx.compose.runtime.Immutable
import java.time.LocalDate
import java.time.LocalTime
import ru.shmr.finance.domain.model.Account
import ru.shmr.finance.domain.model.Category
import ru.shmr.finance.domain.validation.TransactionField

@Immutable
data class TransactionEditorTarget(
    val isIncome: Boolean,
    val localId: String? = null,
)

@Immutable
data class TransactionEditorState(
    val isIncome: Boolean,
    val editingLocalId: String?,
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val accounts: List<Account> = emptyList(),
    val categories: List<Category> = emptyList(),
    val accountId: Int? = null,
    val categoryId: Int? = null,
    val amount: String = "",
    val date: LocalDate = LocalDate.now(),
    val time: LocalTime = LocalTime.now().withSecond(0).withNano(0),
    val comment: String = "",
    val errors: Map<TransactionField, String> = emptyMap(),
    val activePicker: TransactionEditorPicker? = null,
    val pendingDate: LocalDate? = null,
    val pendingTime: LocalTime? = null,
    val pickerError: String? = null,
)

enum class TransactionEditorPicker {
    CATEGORY,
    ACCOUNT,
    DATE,
    TIME,
}

sealed interface TransactionEditorAction {
    data class AccountSelected(val id: Int) : TransactionEditorAction
    data class CategorySelected(val id: Int) : TransactionEditorAction
    data class AmountChanged(val value: String) : TransactionEditorAction
    data class CommentChanged(val value: String) : TransactionEditorAction
    data class OpenPicker(val picker: TransactionEditorPicker) : TransactionEditorAction
    data class PickerDateChanged(val value: LocalDate?) : TransactionEditorAction
    data class PickerTimeChanged(val value: LocalTime) : TransactionEditorAction
    data object ApplyPicker : TransactionEditorAction
    data object DismissPicker : TransactionEditorAction
    data object Save : TransactionEditorAction
}

sealed interface TransactionEditorEffect {
    data object Saved : TransactionEditorEffect
    data class ShowMessage(val message: String) : TransactionEditorEffect
}

data class TransactionEditorDraftSnapshot(
    val initialized: Boolean = false,
    val accountId: Int? = null,
    val categoryId: Int? = null,
    val amount: String = "",
    val date: String = "",
    val time: String = "",
    val comment: String = "",
    val activePicker: String = "",
    val pendingDate: String = "",
    val pendingTime: String = "",
) {
    fun restore(isIncome: Boolean, editingLocalId: String?): TransactionEditorState {
        val fallback = TransactionEditorState(
            isIncome = isIncome,
            editingLocalId = editingLocalId,
        )
        if (!initialized) return fallback
        return fallback.copy(
            accountId = accountId,
            categoryId = categoryId,
            amount = amount,
            date = date.toLocalDateOrNull() ?: fallback.date,
            time = time.toLocalTimeOrNull() ?: fallback.time,
            comment = comment,
            activePicker = activePicker
                .takeIf(String::isNotEmpty)
                ?.let { runCatching { TransactionEditorPicker.valueOf(it) }.getOrNull() },
            pendingDate = pendingDate.toLocalDateOrNull(),
            pendingTime = pendingTime.toLocalTimeOrNull(),
        )
    }

    companion object {
        fun from(state: TransactionEditorState) = TransactionEditorDraftSnapshot(
            initialized = !state.isLoading,
            accountId = state.accountId,
            categoryId = state.categoryId,
            amount = state.amount,
            date = state.date.toString(),
            time = state.time.toString(),
            comment = state.comment,
            activePicker = state.activePicker?.name.orEmpty(),
            pendingDate = state.pendingDate?.toString().orEmpty(),
            pendingTime = state.pendingTime?.toString().orEmpty(),
        )
    }
}

private fun String.toLocalDateOrNull(): LocalDate? =
    takeIf(String::isNotEmpty)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

private fun String.toLocalTimeOrNull(): LocalTime? =
    takeIf(String::isNotEmpty)?.let { runCatching { LocalTime.parse(it) }.getOrNull() }
