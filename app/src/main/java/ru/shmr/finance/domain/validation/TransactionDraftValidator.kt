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

enum class TransactionValidationError {
    ACCOUNT_REQUIRED,
    CATEGORY_REQUIRED,
    AMOUNT_MUST_BE_POSITIVE,
    DATE_TIME_INVALID,
    DATE_OUT_OF_RANGE,
    TIME_REQUIRES_MINUTE_PRECISION,
    DATE_REQUIRED,
    TIME_REQUIRED,
}

data class TransactionDraftValidation(
    val normalizedAmount: BigDecimal?,
    val normalizedComment: String?,
    val errors: Map<TransactionField, TransactionValidationError>,
) {
    val isValid: Boolean
        get() = errors.isEmpty()
}

object TransactionDraftValidator {

    val supportedDateRange: ClosedRange<LocalDate> =
        LocalDate.of(1900, 1, 1)..LocalDate.of(2100, 12, 31)

    fun validate(draft: TransactionDraft): TransactionDraftValidation {
        val errors = linkedMapOf<TransactionField, TransactionValidationError>()
        if (draft.accountId == null) {
            errors[TransactionField.ACCOUNT] = TransactionValidationError.ACCOUNT_REQUIRED
        }
        if (draft.categoryId == null) {
            errors[TransactionField.CATEGORY] = TransactionValidationError.CATEGORY_REQUIRED
        }

        val normalizedAmount = MoneyInputParser.parse(draft.amount)
            ?.takeIf { it > BigDecimal.ZERO }
        if (normalizedAmount == null) {
            errors[TransactionField.AMOUNT] = TransactionValidationError.AMOUNT_MUST_BE_POSITIVE
        }
        validateDate(draft.date)?.let { errors[TransactionField.DATE] = it }
        validateTime(draft.time)?.let { errors[TransactionField.TIME] = it }
        if (
            TransactionField.DATE !in errors &&
            TransactionField.TIME !in errors &&
            runCatching { LocalDateTime.of(draft.date, draft.time) }.isFailure
        ) {
            errors[TransactionField.DATE] = TransactionValidationError.DATE_TIME_INVALID
        }

        return TransactionDraftValidation(
            normalizedAmount = normalizedAmount,
            normalizedComment = draft.comment?.trim()?.takeIf(String::isNotEmpty),
            errors = errors,
        )
    }

    fun validateDate(date: LocalDate): TransactionValidationError? =
        if (date in supportedDateRange) null else TransactionValidationError.DATE_OUT_OF_RANGE

    fun validateTime(time: LocalTime): TransactionValidationError? =
        if (time.second == 0 && time.nano == 0) {
            null
        } else {
            TransactionValidationError.TIME_REQUIRES_MINUTE_PRECISION
        }
}
