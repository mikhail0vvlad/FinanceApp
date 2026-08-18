package ru.shmr.finance.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import ru.shmr.finance.ui.components.AppTopBar
import ru.shmr.finance.ui.components.SingleDatePickerDialog
import ru.shmr.finance.ui.testing.DatePickerTestTags
import ru.shmr.finance.ui.testing.DateChipState
import ru.shmr.finance.ui.testing.SelectDateMillis
import ru.shmr.finance.ui.testing.SelectedDateMillis
import ru.shmr.finance.ui.theme.SHMRFinanceTheme

class DateSelectionUiTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun todayChipOpensCalendarAndSelectsPastDay() {
        val today = LocalDate.of(2026, 8, 4)
        val pastDay = today.minusDays(1)
        val selectedResult = AtomicReference(today)

        composeRule.setContent {
            var selectedDate by remember { mutableStateOf(today) }
            var calendarOpen by remember { mutableStateOf(false) }
            SHMRFinanceTheme {
                AppTopBar(
                    date = "unused-by-test",
                    isToday = selectedDate == today,
                    onDateClick = { calendarOpen = true },
                )
                if (calendarOpen) {
                    SingleDatePickerDialog(
                        selectedDate = selectedDate,
                        today = today,
                        onDateSelected = { date ->
                            selectedDate = date
                            selectedResult.set(date)
                        },
                        onDismiss = { calendarOpen = false },
                    )
                }
            }
        }

        composeRule.onNodeWithTag(DatePickerTestTags.CHIP).assert(
            SemanticsMatcher.expectValue(
                DateChipState,
                DatePickerTestTags.TODAY_STATE,
            ),
        )
        composeRule.onNodeWithTag(DatePickerTestTags.CHIP).performClick()

        val pastDayMillis = pastDay.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        composeRule.onNodeWithTag(DatePickerTestTags.CALENDAR)
            .performSemanticsAction(SelectDateMillis) { selectDate ->
                assertTrue(selectDate(pastDayMillis))
            }
        composeRule.onNodeWithTag(DatePickerTestTags.CALENDAR).assert(
            SemanticsMatcher.expectValue(SelectedDateMillis, pastDayMillis),
        )
        composeRule.onNodeWithTag(DatePickerTestTags.APPLY).performClick()

        composeRule.waitUntil { selectedResult.get() == pastDay }
        assertEquals(pastDay, selectedResult.get())
        composeRule.onNodeWithTag(DatePickerTestTags.CHIP).assert(
            SemanticsMatcher.expectValue(
                DateChipState,
                DatePickerTestTags.SELECTED_STATE,
            ),
        )
    }
}
