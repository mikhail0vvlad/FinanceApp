package ru.shmr.finance.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ru.shmr.finance.core.state.UiState
import ru.shmr.finance.domain.model.AppCurrency
import ru.shmr.finance.domain.model.AppLanguage
import ru.shmr.finance.domain.model.AppSettings
import ru.shmr.finance.domain.model.Category
import ru.shmr.finance.domain.model.SecurityState
import ru.shmr.finance.domain.model.ThemeMode
import ru.shmr.finance.ui.screens.settings.security.BiometricsRoute
import ru.shmr.finance.ui.screens.settings.security.PinFlowRoute

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    settings: AppSettings,
    security: SecurityState,
    categoriesState: UiState<List<Category>>,
    tokenConfigured: Boolean,
    onCurrencySelected: (AppCurrency) -> Unit,
    onThemeModeSelected: (ThemeMode) -> Unit,
    onLanguageSelected: (AppLanguage) -> Unit,
    onDismiss: () -> Unit,
) {
    var state by rememberSaveable(stateSaver = SettingsSheetStateSaver) {
        mutableStateOf(SettingsSheetState())
    }
    val dispatch: (SettingsSheetAction) -> Unit = { state = reduceSettingsSheet(state, it) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        scrimColor = Color.Black.copy(alpha = 0.56f),
    ) {
        BackHandler(enabled = state.page != SettingsPage.MAIN) {
            dispatch(SettingsSheetAction.Back)
        }
        SettingsPageContent(
            state = state,
            settings = settings,
            security = security,
            categoriesState = categoriesState,
            tokenConfigured = tokenConfigured,
            dispatch = dispatch,
            onCurrencySelected = onCurrencySelected,
            onThemeModeSelected = onThemeModeSelected,
            onLanguageSelected = onLanguageSelected,
        )
    }
}

@Composable
private fun SettingsPageContent(
    state: SettingsSheetState,
    settings: AppSettings,
    security: SecurityState,
    categoriesState: UiState<List<Category>>,
    tokenConfigured: Boolean,
    dispatch: (SettingsSheetAction) -> Unit,
    onCurrencySelected: (AppCurrency) -> Unit,
    onThemeModeSelected: (ThemeMode) -> Unit,
    onLanguageSelected: (AppLanguage) -> Unit,
) {
    val back = { dispatch(SettingsSheetAction.Back) }
    when (state.page) {
        SettingsPage.MAIN -> MainSettingsScreen(security, tokenConfigured) {
            dispatch(SettingsSheetAction.Open(it))
        }
        SettingsPage.CURRENCY -> CurrencyScreen(settings.currency, back, onCurrencySelected)
        SettingsPage.ARTICLES -> ArticlesScreen(
            query = state.articleQuery,
            categoriesState = filterCachedCategories(categoriesState, state.articleQuery),
            onQueryChanged = { dispatch(SettingsSheetAction.ArticleQueryChanged(it)) },
            onBack = back,
        )
        SettingsPage.THEME -> ThemeScreen(settings.themeMode, back, onThemeModeSelected)
        SettingsPage.LANGUAGE -> LanguageScreen(settings.language, back, onLanguageSelected)
        SettingsPage.PIN -> PinFlowRoute(security.isPinSet, back, back)
        SettingsPage.BIOMETRICS -> BiometricsRoute(onBack = back)
        SettingsPage.API_TOKEN -> ApiTokenScreen(onBack = back)
    }
}
