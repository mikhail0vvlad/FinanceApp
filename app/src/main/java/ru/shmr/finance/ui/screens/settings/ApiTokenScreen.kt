package ru.shmr.finance.ui.screens.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import ru.shmr.finance.R
import ru.shmr.finance.di.ServiceLocator
import ru.shmr.finance.ui.components.message

@Composable
internal fun ApiTokenScreen(
    onBack: () -> Unit,
    viewModel: ApiTokenViewModel = viewModel(
        factory = remember {
            viewModelFactory {
                initializer { ApiTokenViewModel(repository = ServiceLocator.apiTokenRepository) }
            }
        },
    ),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
        ScreenTitle(stringResource(R.string.settings_api_token), onBack)
        Text(
            stringResource(R.string.settings_api_token_explanation),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        OutlinedTextField(
            value = state.input,
            onValueChange = viewModel::onTokenChanged,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            enabled = !state.isSaving,
            singleLine = true,
            label = { Text(stringResource(R.string.settings_api_token_input)) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Done,
            ),
            isError = state.error != null,
            supportingText = { ApiTokenSupportingText(state) },
        )
        Button(
            onClick = viewModel::save,
            enabled = !state.isSaving,
            modifier = Modifier.align(Alignment.End).padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Text(stringResource(R.string.cd_save))
        }
    }
}

@Composable
private fun ApiTokenSupportingText(state: ApiTokenState) {
    when {
        state.error != null -> Text(state.error.message())
        state.saved -> Text(stringResource(R.string.settings_api_token_saved))
        state.isConfigured -> Text(stringResource(R.string.settings_api_token_replace_hint))
    }
}
