package ru.shmr.finance.ui.screens.settings

import androidx.compose.runtime.saveable.SaverScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.shmr.finance.core.state.UiState
import ru.shmr.finance.domain.model.Category

class SettingsSheetStateTest {

    @Test
    fun `open and back keep navigation inside settings sheet`() {
        val opened = reduceSettingsSheet(
            SettingsSheetState(),
            SettingsSheetAction.Open(SettingsPage.ARTICLES),
        )

        assertEquals(SettingsPage.ARTICLES, opened.page)
        assertEquals(
            SettingsPage.MAIN,
            reduceSettingsSheet(opened, SettingsSheetAction.Back).page,
        )
    }

    @Test
    fun `article query survives nested navigation`() {
        val withQuery = reduceSettingsSheet(
            SettingsSheetState(page = SettingsPage.ARTICLES),
            SettingsSheetAction.ArticleQueryChanged(" аренда "),
        )
        val main = reduceSettingsSheet(withQuery, SettingsSheetAction.Back)
        val reopened = reduceSettingsSheet(
            main,
            SettingsSheetAction.Open(SettingsPage.ARTICLES),
        )

        assertEquals(" аренда ", reopened.articleQuery)
    }

    @Test
    fun `cached categories are filtered locally ignoring case`() {
        val source = UiState.Content(
            listOf(
                Category(1, "Аренда квартиры", "🏠", false),
                Category(2, "Зарплата", "💰", true),
            ),
        )

        assertEquals(
            listOf(Category(1, "Аренда квартиры", "🏠", false)),
            (filterCachedCategories(source, "АРЕНДА") as UiState.Content).data,
        )
    }

    @Test
    fun `query with no cached match produces empty state`() {
        val source = UiState.Content(
            listOf(Category(1, "Зарплата", "💰", true)),
        )

        assertSame(UiState.Empty, filterCachedCategories(source, "аренда"))
    }

    @Test
    fun `sheet state round-trips through its saver`() {
        val state = SettingsSheetState(page = SettingsPage.PIN, articleQuery = "аренда")

        val restored = with(SettingsSheetStateSaver) {
            val scope = SaverScope { true }
            restore(scope.save(state)!!)
        }

        assertEquals(state, restored)
    }

    @Test
    fun `saver stores only bundle-friendly primitives`() {
        // Регресс: data class внутри rememberSaveable ронял открытие листа настроек.
        val saved = with(SettingsSheetStateSaver) { SaverScope { true }.save(SettingsSheetState()) }

        assertTrue(saved is List<*> && saved.all { it is String })
    }

    @Test
    fun `loading state is preserved without triggering another source`() {
        assertSame(
            UiState.Loading,
            filterCachedCategories(UiState.Loading, "любой запрос"),
        )
    }
}
