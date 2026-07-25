package ru.shmr.finance.domain.validation

import java.math.BigDecimal
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

data class TransactionDraft(
    val accountId: Int?,
    val categoryId: Int?,
    val amount: String,
    val date: LocalDate,
    val time: LocalTime,
    val comment: String?,
)

enum class TransactionField {
    ACCOUNT,
    CATEGORY,
    AMOUNT,
    DATE,
    TIME,
}

data class TransactionDraftValidation(
    val normalizedAmount: BigDecimal?,
    val normalizedComment: String?,
    val errors: Map<TransactionField, String>,
) {
    val isValid: Boolean
        get() = errors.isEmpty()
}

object TransactionDraftValidator {

    val supportedDateRange: ClosedRange<LocalDate> =
        LocalDate.of(1900, 1, 1)..LocalDate.of(2100, 12, 31)

    fun validate(draft: TransactionDraft): TransactionDraftValidation {
        val errors = linkedMapOf<TransactionField, String>()
        if (draft.accountId == null) {
            errors[TransactionField.ACCOUNT] = "Выберите счёт"
        }
        if (draft.categoryId == null) {
            errors[TransactionField.CATEGORY] = "Выберите статью"
        }

        val normalizedAmount = MoneyInputParser.parse(draft.amount)
            ?.takeIf { it > BigDecimal.ZERO }
        if (normalizedAmount == null) {
            errors[TransactionField.AMOUNT] = "Введите сумму больше нуля"
        }
        validateDate(draft.date)?.let { errors[TransactionField.DATE] = it }
        validateTime(draft.time)?.let { errors[TransactionField.TIME] = it }
        if (
            TransactionField.DATE !in errors &&
            TransactionField.TIME !in errors &&
            runCatching { LocalDateTime.of(draft.date, draft.time) }.isFailure
        ) {
            errors[TransactionField.DATE] = "Выберите корректные дату и время"
        }

        return TransactionDraftValidation(
            normalizedAmount = normalizedAmount,
            normalizedComment = draft.comment?.trim()?.takeIf(String::isNotEmpty),
            errors = errors,
        )
    }

    fun validateDate(date: LocalDate): String? =
        if (date in supportedDateRange) null else "Дата вне допустимого диапазона"

    fun validateTime(time: LocalTime): String? =
        if (time.second == 0 && time.nano == 0) {
            null
        } else {
            "Укажите время с точностью до минуты"
        }
}
