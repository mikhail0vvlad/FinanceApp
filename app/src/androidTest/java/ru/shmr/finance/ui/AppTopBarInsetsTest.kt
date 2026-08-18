package ru.shmr.finance.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import ru.shmr.finance.ui.components.AppTopBar
import ru.shmr.finance.ui.testing.DatePickerTestTags
import ru.shmr.finance.ui.theme.SHMRFinanceTheme

/**
 * Приложение рисуется edge-to-edge, а Scaffold не добавляет верхний inset, когда задан topBar, —
 * отступ под статус-бар обязан делать сам [AppTopBar].
 */
class AppTopBarInsetsTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun dateChipIsDrawnBelowStatusBarInset() {
        val statusBarHeight = 48.dp

        composeRule.setContent {
            SHMRFinanceTheme {
                AppTopBar(
                    date = "4 августа",
                    windowInsets = WindowInsets(top = statusBarHeight),
                )
            }
        }

        val chipTop = composeRule
            .onNodeWithTag(DatePickerTestTags.CHIP)
            .getUnclippedBoundsInRoot()
            .top

        assertTrue(
            "Дата-чип начинается на $chipTop, а статус-бар занимает $statusBarHeight",
            chipTop >= statusBarHeight,
        )
    }
}
