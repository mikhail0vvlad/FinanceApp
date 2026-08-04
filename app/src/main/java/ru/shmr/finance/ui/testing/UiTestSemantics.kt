package ru.shmr.finance.ui.testing

import androidx.compose.ui.semantics.SemanticsPropertyKey
import androidx.compose.ui.semantics.SemanticsPropertyReceiver
import androidx.compose.ui.semantics.AccessibilityAction
import ru.shmr.finance.domain.model.ThemeMode
import ru.shmr.finance.ui.screens.settings.security.PinFlowError
import ru.shmr.finance.ui.screens.settings.security.PinStage

object SettingsTestTags {
    const val THEME_ROW = "settings_theme_row"

    fun themeOption(mode: ThemeMode): String = "settings_theme_${mode.name.lowercase()}"
}

object PinTestTags {
    const val ROOT = "pin_flow"
    const val DOTS = "pin_dots"
    const val BACKSPACE = "pin_backspace"

    fun digit(value: Char): String = "pin_digit_$value"
    fun stage(value: PinStage): String = "pin_stage_${value.name.lowercase()}"
    fun error(value: PinFlowError?): String = "pin_error_${value?.name?.lowercase() ?: "none"}"
}

object DatePickerTestTags {
    const val CHIP = "date_chip"
    const val CALENDAR = "date_calendar"
    const val APPLY = "date_apply"
    const val TODAY_STATE = "today"
    const val SELECTED_STATE = "selected_date"
}

val SelectDateMillis =
    SemanticsPropertyKey<AccessibilityAction<(Long) -> Boolean>>("SelectDateMillis")
var SemanticsPropertyReceiver.selectDateMillis by SelectDateMillis

val SelectedDateMillis = SemanticsPropertyKey<Long>("SelectedDateMillis")
var SemanticsPropertyReceiver.selectedDateMillis by SelectedDateMillis

val DateChipState = SemanticsPropertyKey<String>("DateChipState")
var SemanticsPropertyReceiver.dateChipState by DateChipState

val PinProgress = SemanticsPropertyKey<Int>("PinProgress")
var SemanticsPropertyReceiver.pinProgress by PinProgress
