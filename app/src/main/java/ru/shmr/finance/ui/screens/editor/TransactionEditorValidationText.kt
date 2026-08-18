package ru.shmr.finance.ui.screens.editor

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import ru.shmr.finance.R
import ru.shmr.finance.domain.validation.TransactionValidationError

@Composable
internal fun TransactionValidationError.localized(): String = stringResource(
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
