package ru.shmr.finance.ui.lock

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.shmr.finance.R
import ru.shmr.finance.ui.components.PinDots
import ru.shmr.finance.ui.components.PinKeypad

@Composable
fun AppLockScreen(
    state: AppLockState,
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    onBiometricRequested: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 48.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Lock,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(34.dp),
                )
            }
            Text(
                text = stringResource(R.string.lock_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 20.dp),
            )
            Text(
                text = stringResource(R.string.lock_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 32.dp, vertical = 6.dp),
            )
            PinDots(filled = state.entry.length, isError = state.error != null)
            Text(
                text = lockErrorText(state.error),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
            )

            Spacer(modifier = Modifier.weight(1f))

            if (state.isBiometricsEnabled) {
                TextButton(
                    onClick = onBiometricRequested,
                    enabled = !state.isVerifying,
                    modifier = Modifier.padding(bottom = 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Fingerprint,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = stringResource(R.string.lock_use_biometrics),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            PinKeypad(
                onDigit = onDigit,
                onBackspace = onBackspace,
                enabled = !state.isVerifying,
            )
        }
    }
}

/**
 * Заглушка на время чтения настроек безопасности с диска: содержимое приложения не должно
 * мелькнуть до того, как станет известно, нужна ли блокировка.
 */
@Composable
fun AppLockPlaceholder() {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.surface,
    ) {}
}

@Composable
private fun lockErrorText(error: AppLockError?): String = when (error) {
    null -> ""
    AppLockError.WrongPin -> stringResource(R.string.lock_error_wrong_pin)
    AppLockError.BiometricLockout -> stringResource(R.string.security_biometrics_lockout)
    AppLockError.BiometricLockoutPermanent ->
        stringResource(R.string.security_biometrics_lockout_permanent)
    AppLockError.BiometricUnavailable -> stringResource(R.string.security_biometrics_unavailable)
    AppLockError.CredentialReset -> stringResource(R.string.lock_error_credential_reset)
    // Текст приходит от системы и уже локализован — показываем как есть.
    is AppLockError.BiometricFailed -> error.message
}
