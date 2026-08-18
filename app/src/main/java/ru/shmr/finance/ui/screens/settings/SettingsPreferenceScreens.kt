package ru.shmr.finance.ui.screens.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.SettingsBrightness
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import ru.shmr.finance.R
import ru.shmr.finance.domain.model.AppCurrency
import ru.shmr.finance.domain.model.AppLanguage
import ru.shmr.finance.domain.model.ThemeMode
import ru.shmr.finance.ui.testing.SettingsTestTags

@Composable
internal fun CurrencyScreen(
    selected: AppCurrency,
    onBack: () -> Unit,
    onSelected: (AppCurrency) -> Unit,
) {
    SelectionScreen(stringResource(R.string.settings_currency), onBack) {
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
internal fun LanguageScreen(
    selected: AppLanguage,
    onBack: () -> Unit,
    onSelected: (AppLanguage) -> Unit,
) {
    SelectionScreen(stringResource(R.string.settings_language), onBack) {
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
internal fun ThemeScreen(
    selected: ThemeMode,
    onBack: () -> Unit,
    onSelected: (ThemeMode) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 34.dp)) {
        ScreenTitle(stringResource(R.string.settings_theme), onBack)
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ThemeMode.entries.forEach { mode ->
                ThemeOption(mode, selected == mode) { onSelected(mode) }
            }
        }
    }
}

@Composable
private fun RowScope.ThemeOption(mode: ThemeMode, selected: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier.weight(1f).testTag(SettingsTestTags.themeOption(mode)).semantics {
            this.selected = selected
        }.clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(
            if (selected) 2.dp else 1.dp,
            if (selected) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.outlineVariant,
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(8.dp))
                    .background(themePreviewBrush(mode)),
            )
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(themeIcon(mode), null, Modifier.size(16.dp))
                Text(
                    themeModeLabel(mode),
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }
    }
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

private fun currencyFlag(currency: AppCurrency) = listOf("🇷🇺", "🇺🇸", "🇪🇺", "🇬🇧", "🇨🇳")[currency.ordinal]
private fun languageFlag(language: AppLanguage) = listOf("🇷🇺", "🇬🇧", "🇩🇪", "🇫🇷", "🇪🇸")[language.ordinal]
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
