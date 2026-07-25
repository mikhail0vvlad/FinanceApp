package ru.shmr.finance.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SettingsBrightness
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import ru.shmr.finance.R
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
    onCurrencySelected: (AppCurrency) -> Unit,
    onThemeModeSelected: (ThemeMode) -> Unit,
    onLanguageSelected: (AppLanguage) -> Unit,
    onDismiss: () -> Unit,
) {
    var state by rememberSaveable(stateSaver = SettingsSheetStateSaver) {
        mutableStateOf(SettingsSheetState())
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dispatch: (SettingsSheetAction) -> Unit = { action ->
        state = reduceSettingsSheet(state, action)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        scrimColor = Color.Black.copy(alpha = 0.56f),
    ) {
        // BackHandler живёт внутри контента: лист рисуется в собственном окне со своим
        // OnBackPressedDispatcher, и обработчик снаружи до системной «Назад» не доходит —
        // окно само вызывает onDismissRequest и закрывает лист целиком.
        BackHandler(enabled = state.page != SettingsPage.MAIN) {
            dispatch(SettingsSheetAction.Back)
        }

        when (state.page) {
            SettingsPage.MAIN -> MainSettingsScreen(
                security = security,
                onOpen = { dispatch(SettingsSheetAction.Open(it)) },
            )

            SettingsPage.CURRENCY -> CurrencyScreen(
                selected = settings.currency,
                onBack = { dispatch(SettingsSheetAction.Back) },
                onSelected = onCurrencySelected,
            )

            SettingsPage.ARTICLES -> ArticlesScreen(
                query = state.articleQuery,
                categoriesState = filterCachedCategories(categoriesState, state.articleQuery),
                onQueryChanged = {
                    dispatch(SettingsSheetAction.ArticleQueryChanged(it))
                },
                onBack = { dispatch(SettingsSheetAction.Back) },
            )

            SettingsPage.THEME -> ThemeScreen(
                selected = settings.themeMode,
                onBack = { dispatch(SettingsSheetAction.Back) },
                onSelected = onThemeModeSelected,
            )

            SettingsPage.LANGUAGE -> LanguageScreen(
                selected = settings.language,
                onBack = { dispatch(SettingsSheetAction.Back) },
                onSelected = onLanguageSelected,
            )

            SettingsPage.PIN -> PinFlowRoute(
                isPinSet = security.isPinSet,
                onBack = { dispatch(SettingsSheetAction.Back) },
                onCompleted = { dispatch(SettingsSheetAction.Back) },
            )

            SettingsPage.BIOMETRICS -> BiometricsRoute(
                onBack = { dispatch(SettingsSheetAction.Back) },
            )
        }
    }
}

@Composable
private fun MainSettingsScreen(
    security: SecurityState,
    onOpen: (SettingsPage) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 28.dp),
    ) {
        SheetTitle(stringResource(R.string.settings_title))
        SettingsSectionTitle(R.string.settings_wallet)
        SettingsRow(
            icon = Icons.Outlined.CurrencyExchange,
            title = stringResource(R.string.settings_currency),
            onClick = { onOpen(SettingsPage.CURRENCY) },
        )
        SettingsRow(
            icon = Icons.Outlined.Category,
            title = stringResource(R.string.settings_articles),
            onClick = { onOpen(SettingsPage.ARTICLES) },
        )

        SettingsSectionTitle(R.string.settings_interface)
        SettingsRow(
            icon = Icons.Outlined.DarkMode,
            title = stringResource(R.string.settings_theme),
            onClick = { onOpen(SettingsPage.THEME) },
        )
        SettingsRow(
            icon = Icons.Outlined.Language,
            title = stringResource(R.string.settings_language),
            onClick = { onOpen(SettingsPage.LANGUAGE) },
        )

        SettingsSectionTitle(R.string.settings_security)
        SettingsRow(
            icon = Icons.Outlined.Lock,
            title = stringResource(R.string.settings_pin),
            value = stringResource(
                if (security.isPinSet) R.string.security_pin_set else R.string.security_pin_not_set,
            ),
            onClick = { onOpen(SettingsPage.PIN) },
        )
        SettingsRow(
            icon = Icons.Outlined.Fingerprint,
            title = stringResource(R.string.settings_biometrics),
            value = stringResource(
                if (security.isBiometricsEnabled) {
                    R.string.security_biometrics_on
                } else {
                    R.string.security_biometrics_off
                },
            ),
            onClick = { onOpen(SettingsPage.BIOMETRICS) },
        )
    }
}

@Composable
private fun CurrencyScreen(
    selected: AppCurrency,
    onBack: () -> Unit,
    onSelected: (AppCurrency) -> Unit,
) {
    SelectionScreen(
        title = stringResource(R.string.settings_currency),
        onBack = onBack,
    ) {
        AppCurrency.entries.forEach { currency ->
            SelectionRow(
                leading = currencyFlag(currency),
                title = currencyLabel(currency),
                supporting = currency.currency.code,
                selected = selected == currency,
                onClick = { onSelected(currency) },
            )
        }
    }
}

@Composable
private fun LanguageScreen(
    selected: AppLanguage,
    onBack: () -> Unit,
    onSelected: (AppLanguage) -> Unit,
) {
    SelectionScreen(
        title = stringResource(R.string.settings_language),
        onBack = onBack,
    ) {
        AppLanguage.entries.forEach { language ->
            SelectionRow(
                leading = languageFlag(language),
                title = languageLabel(language),
                selected = selected == language,
                onClick = { onSelected(language) },
            )
        }
    }
}

@Composable
private fun ArticlesScreen(
    query: String,
    categoriesState: UiState<List<Category>>,
    onQueryChanged: (String) -> Unit,
    onBack: () -> Unit,
) {
    val searchDescription = stringResource(R.string.settings_articles_search)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 28.dp),
    ) {
        ScreenTitle(
            title = stringResource(R.string.settings_articles),
            onBack = onBack,
        )
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChanged,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .semantics {
                    contentDescription = searchDescription
                },
            placeholder = { Text(stringResource(R.string.settings_articles_search)) },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                )
            },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Outlined.Close,
                        contentDescription = stringResource(R.string.settings_clear_search),
                        modifier = Modifier.clickable { onQueryChanged("") },
                    )
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(28.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        )
        when (categoriesState) {
            UiState.Loading -> Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }

            UiState.Empty -> EmptyArticles(query.isNotBlank())
            is UiState.Error -> EmptyArticles(false)
            is UiState.Content -> Column(modifier = Modifier.fillMaxWidth()) {
                categoriesState.data.forEach { category ->
                    CategoryRow(category)
                }
            }
        }
    }
}

@Composable
private fun EmptyArticles(isSearchEmpty: Boolean) {
    Text(
        text = stringResource(
            if (isSearchEmpty) {
                R.string.settings_articles_no_results
            } else {
                R.string.settings_articles_empty
            },
        ),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(20.dp),
    )
}

@Composable
private fun CategoryRow(category: Category) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                contentDescription = "${category.name}, ${category.emoji}"
            }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = category.emoji,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.size(36.dp),
        )
        Text(
            text = category.name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        )
    }
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 20.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
private fun ThemeScreen(
    selected: ThemeMode,
    onBack: () -> Unit,
    onSelected: (ThemeMode) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 34.dp),
    ) {
        ScreenTitle(
            title = stringResource(R.string.settings_theme),
            onBack = onBack,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ThemeMode.entries.forEach { mode ->
                val isSelected = selected == mode
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .semantics { this.selected = isSelected }
                        .clickable { onSelected(mode) },
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(
                        width = if (isSelected) 2.dp else 1.dp,
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outlineVariant
                        },
                    ),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(themePreviewBrush(mode)),
                        )
                        Row(
                            modifier = Modifier.padding(top = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = themeIcon(mode),
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = themeModeLabel(mode),
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectionScreen(
    title: String,
    onBack: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 28.dp),
    ) {
        ScreenTitle(title = title, onBack = onBack)
        content()
    }
}

@Composable
private fun ScreenTitle(
    title: String,
    onBack: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 20.dp, top = 4.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
            contentDescription = stringResource(R.string.cd_back),
            modifier = Modifier
                .clip(RoundedCornerShape(24.dp))
                .clickable(onClick = onBack)
                .padding(12.dp),
        )
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
private fun SheetTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
    )
}

@Composable
private fun SettingsSectionTitle(titleRes: Int) {
    Text(
        text = stringResource(titleRes).uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 4.dp),
    )
}

@Composable
private fun SettingsRow(
    icon: ImageVector,
    title: String,
    value: String = "",
    onClick: () -> Unit,
) {
    val semanticsText = if (value.isBlank()) title else "$title, $value"
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = semanticsText }
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .background(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                    shape = RoundedCornerShape(16.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (value.isNotBlank()) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Icon(
            imageVector = Icons.Outlined.ChevronRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SelectionRow(
    leading: String,
    title: String,
    supporting: String = "",
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                this.selected = selected
                contentDescription = if (supporting.isEmpty()) title else "$title, $supporting"
            }
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = leading, style = MaterialTheme.typography.titleMedium)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            if (supporting.isNotEmpty()) {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (selected) {
            Icon(
                imageVector = Icons.Outlined.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 20.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
private fun themeModeLabel(mode: ThemeMode): String = stringResource(
    when (mode) {
        ThemeMode.LIGHT -> R.string.theme_light
        ThemeMode.DARK -> R.string.theme_dark
        ThemeMode.SYSTEM -> R.string.theme_system
    },
)

@Composable
private fun languageLabel(language: AppLanguage): String = stringResource(
    when (language) {
        AppLanguage.RUSSIAN -> R.string.language_russian
        AppLanguage.ENGLISH -> R.string.language_english
        AppLanguage.GERMAN -> R.string.language_german
        AppLanguage.FRENCH -> R.string.language_french
        AppLanguage.SPANISH -> R.string.language_spanish
    },
)

@Composable
private fun currencyLabel(currency: AppCurrency): String = stringResource(
    when (currency) {
        AppCurrency.RUB -> R.string.currency_rub
        AppCurrency.USD -> R.string.currency_usd
        AppCurrency.EUR -> R.string.currency_eur
        AppCurrency.GBP -> R.string.currency_gbp
        AppCurrency.CNY -> R.string.currency_cny
    },
)

private fun currencyFlag(currency: AppCurrency): String = when (currency) {
    AppCurrency.RUB -> "🇷🇺"
    AppCurrency.USD -> "🇺🇸"
    AppCurrency.EUR -> "🇪🇺"
    AppCurrency.GBP -> "🇬🇧"
    AppCurrency.CNY -> "🇨🇳"
}

private fun languageFlag(language: AppLanguage): String = when (language) {
    AppLanguage.RUSSIAN -> "🇷🇺"
    AppLanguage.ENGLISH -> "🇬🇧"
    AppLanguage.GERMAN -> "🇩🇪"
    AppLanguage.FRENCH -> "🇫🇷"
    AppLanguage.SPANISH -> "🇪🇸"
}

private fun themeIcon(mode: ThemeMode): ImageVector = when (mode) {
    ThemeMode.LIGHT -> Icons.Outlined.LightMode
    ThemeMode.DARK -> Icons.Outlined.DarkMode
    ThemeMode.SYSTEM -> Icons.Outlined.SettingsBrightness
}

@Composable
private fun themePreviewBrush(mode: ThemeMode): Brush = when (mode) {
    ThemeMode.LIGHT -> Brush.verticalGradient(listOf(Color.White, Color(0xFFF4F1F8)))
    ThemeMode.DARK -> Brush.verticalGradient(listOf(Color(0xFF1D1B20), Color(0xFF322F35)))
    ThemeMode.SYSTEM -> Brush.verticalGradient(
        listOf(Color.White, Color(0xFFF4F1F8), Color(0xFF1D1B20)),
    )
}
