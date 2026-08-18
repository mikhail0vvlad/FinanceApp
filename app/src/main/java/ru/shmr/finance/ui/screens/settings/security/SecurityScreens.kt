package ru.shmr.finance.ui.screens.settings.security

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.shmr.finance.R
import ru.shmr.finance.domain.model.BiometricAvailability
import ru.shmr.finance.ui.components.PinDots
import ru.shmr.finance.ui.components.PinKeypad
import ru.shmr.finance.ui.testing.PinTestTags

@Composable
fun PinFlowScreen(
    state: PinFlowState,
    canRemovePin: Boolean,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onRemovePin: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .testTag(PinTestTags.ROOT)
            .fillMaxWidth()
            .padding(bottom = 24.dp),
    ) {
        SecurityScreenTitle(
            title = stringResource(
                if (state.mode == PinFlowMode.CHANGE) {
                    R.string.security_pin_change_title
                } else {
                    R.string.security_pin_create_title
                },
            ),
            onBack = onBack,
        )
        Text(
            text = stringResource(stageHint(state.stage)),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .testTag(PinTestTags.stage(state.stage))
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
        )
        PinDots(
            filled = state.entry.length,
            isError = state.error != null,
            modifier = Modifier.testTag(PinTestTags.DOTS),
        )
        Text(
            text = state.error?.let { stringResource(errorMessage(it)) }.orEmpty(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.error,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .testTag(PinTestTags.error(state.error))
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 8.dp),
        )
        PinKeypad(
            onDigit = onDigit,
            onBackspace = onBackspace,
            enabled = !state.isBusy,
        )
        if (canRemovePin) {
            TextButton(
                onClick = onRemovePin,
                enabled = !state.isBusy,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(top = 8.dp),
            ) {
                Text(
                    text = stringResource(R.string.security_pin_remove),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
fun BiometricsScreen(
    state: BiometricSettingsState,
    onToggle: (Boolean) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 32.dp),
    ) {
        SecurityScreenTitle(
            title = stringResource(R.string.settings_biometrics),
            onBack = onBack,
        )
        Box(
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = 20.dp)
                .size(88.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Fingerprint,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp),
            )
        }
        Text(
            text = stringResource(R.string.security_biometrics_description),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp, vertical = 4.dp),
        )

        val toggleLabel = stringResource(R.string.security_biometrics_toggle)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 20.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .semantics { contentDescription = toggleLabel }
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = toggleLabel,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(end = 12.dp),
            )
            Switch(
                checked = state.isEnabled,
                onCheckedChange = onToggle,
                enabled = state.canToggle,
            )
        }

        blockingReason(state)?.let { reason ->
            Text(
                text = stringResource(reason),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
        state.error?.let { error ->
            Text(
                text = stringResource(promptErrorMessage(error)),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
        Text(
            text = stringResource(R.string.security_biometrics_footnote),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
    }
}

@Composable
private fun SecurityScreenTitle(title: String, onBack: () -> Unit) {
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

private fun stageHint(stage: PinStage): Int = when (stage) {
    PinStage.CURRENT -> R.string.security_pin_hint_current
    PinStage.NEW -> R.string.security_pin_hint_new
    PinStage.CONFIRM -> R.string.security_pin_hint_confirm
}

private fun errorMessage(error: PinFlowError): Int = when (error) {
    PinFlowError.WRONG_CURRENT -> R.string.security_pin_error_wrong_current
    PinFlowError.MISMATCH -> R.string.security_pin_error_mismatch
    PinFlowError.STORAGE_UNAVAILABLE -> R.string.security_pin_error_storage
    PinFlowError.CREDENTIAL_UNREADABLE -> R.string.security_pin_error_unreadable
}

private fun promptErrorMessage(error: BiometricSettingsError): Int = when (error) {
    BiometricSettingsError.LOCKOUT -> R.string.security_biometrics_lockout
    BiometricSettingsError.LOCKOUT_PERMANENT -> R.string.security_biometrics_lockout_permanent
    BiometricSettingsError.UNAVAILABLE -> R.string.security_biometrics_unavailable
    BiometricSettingsError.FAILED -> R.string.security_biometrics_failed
}

/** Почему тумблер выключен: пользователь должен понимать, что чинить. */
private fun blockingReason(state: BiometricSettingsState): Int? = when {
    !state.isPinSet -> R.string.security_biometrics_requires_pin
    state.availability == BiometricAvailability.AVAILABLE -> null
    state.availability == BiometricAvailability.NO_HARDWARE -> R.string.security_biometrics_no_hardware
    state.availability == BiometricAvailability.NONE_ENROLLED -> R.string.security_biometrics_none_enrolled
    state.availability == BiometricAvailability.HARDWARE_UNAVAILABLE ->
        R.string.security_biometrics_hardware_unavailable
    state.availability == BiometricAvailability.SECURITY_UPDATE_REQUIRED ->
        R.string.security_biometrics_update_required
    else -> R.string.security_biometrics_unsupported
}
