package ru.shmr.finance.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Category
import androidx.compose.material.icons.outlined.CurrencyExchange
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.shmr.finance.R
import ru.shmr.finance.domain.model.SecurityState
import ru.shmr.finance.ui.testing.SettingsTestTags

@Composable
internal fun MainSettingsScreen(
    security: SecurityState,
    tokenConfigured: Boolean,
    onOpen: (SettingsPage) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(bottom = 28.dp),
    ) {
        SheetTitle(stringResource(R.string.settings_title))
        SettingsSectionTitle(R.string.settings_wallet)
        SettingsRow(
            Icons.Outlined.CurrencyExchange,
            stringResource(R.string.settings_currency),
            onClick = { onOpen(SettingsPage.CURRENCY) },
        )
        SettingsRow(
            Icons.Outlined.Category,
            stringResource(R.string.settings_articles),
            onClick = { onOpen(SettingsPage.ARTICLES) },
        )
        SettingsSectionTitle(R.string.settings_interface)
        SettingsRow(
            Icons.Outlined.DarkMode,
            stringResource(R.string.settings_theme),
            modifier = Modifier.testTag(SettingsTestTags.THEME_ROW),
            onClick = { onOpen(SettingsPage.THEME) },
        )
        SettingsRow(
            Icons.Outlined.Language,
            stringResource(R.string.settings_language),
            onClick = { onOpen(SettingsPage.LANGUAGE) },
        )
        SettingsSectionTitle(R.string.settings_security)
        SecurityRows(security, tokenConfigured, onOpen)
    }
}

@Composable
private fun SecurityRows(
    security: SecurityState,
    tokenConfigured: Boolean,
    onOpen: (SettingsPage) -> Unit,
) {
    SettingsRow(
        Icons.Outlined.Lock,
        stringResource(R.string.settings_pin),
        value = stringResource(
            if (security.isPinSet) R.string.security_pin_set else R.string.security_pin_not_set,
        ),
        onClick = { onOpen(SettingsPage.PIN) },
    )
    SettingsRow(
        Icons.Outlined.Fingerprint,
        stringResource(R.string.settings_biometrics),
        value = stringResource(
            if (security.isBiometricsEnabled) {
                R.string.security_biometrics_on
            } else {
                R.string.security_biometrics_off
            },
        ),
        onClick = { onOpen(SettingsPage.BIOMETRICS) },
    )
    SettingsRow(
        Icons.Outlined.Key,
        stringResource(R.string.settings_api_token),
        value = stringResource(
            if (tokenConfigured) R.string.settings_api_token_set
            else R.string.settings_api_token_not_set,
        ),
        onClick = { onOpen(SettingsPage.API_TOKEN) },
    )
}
