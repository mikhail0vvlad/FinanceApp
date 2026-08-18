package ru.shmr.finance.ui.screens.settings

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.saveable.listSaver
import java.util.Locale
import ru.shmr.finance.core.state.UiState
import ru.shmr.finance.domain.model.Category

enum class SettingsPage {
    MAIN,
    CURRENCY,
    ARTICLES,
    THEME,
    LANGUAGE,
    PIN,
    BIOMETRICS,
    API_TOKEN,
}

@Immutable
data class SettingsSheetState(
    val page: SettingsPage = SettingsPage.MAIN,
    val articleQuery: String = "",
)

/**
 * `rememberSaveable` кладёт значение в `Bundle`, а data class туда не помещается и роняет
 * композицию при открытии листа. Сохраняем примитивы и восстанавливаем страницу по имени:
 * неизвестное имя (например, после удаления страницы) откатывается на главный экран.
 */
val SettingsSheetStateSaver = listSaver<SettingsSheetState, String>(
    save = { listOf(it.page.name, it.articleQuery) },
    restore = {
        SettingsSheetState(
            page = SettingsPage.entries.find { page -> page.name == it[0] }
                ?: SettingsPage.MAIN,
            articleQuery = it[1],
        )
    },
)

sealed interface SettingsSheetAction {
    data class Open(val page: SettingsPage) : SettingsSheetAction
    data class ArticleQueryChanged(val value: String) : SettingsSheetAction
    data object Back : SettingsSheetAction
}

fun reduceSettingsSheet(
    state: SettingsSheetState,
    action: SettingsSheetAction,
): SettingsSheetState = when (action) {
    is SettingsSheetAction.Open -> state.copy(page = action.page)
    is SettingsSheetAction.ArticleQueryChanged -> state.copy(articleQuery = action.value)
    SettingsSheetAction.Back -> state.copy(page = SettingsPage.MAIN)
}

fun filterCachedCategories(
    source: UiState<List<Category>>,
    query: String,
): UiState<List<Category>> {
    if (source !is UiState.Content) return source
    val normalizedQuery = query.trim().lowercase(Locale.getDefault())
    if (normalizedQuery.isEmpty()) return source
    val filtered = source.data.filter { category ->
        category.name.lowercase(Locale.getDefault()).contains(normalizedQuery)
    }
    return if (filtered.isEmpty()) UiState.Empty else UiState.Content(filtered)
}
