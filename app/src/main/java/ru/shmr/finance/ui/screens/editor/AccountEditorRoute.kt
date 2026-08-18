package ru.shmr.finance.ui.screens.editor

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import ru.shmr.finance.di.ServiceLocator
import ru.shmr.finance.R
import ru.shmr.finance.ui.components.plainMessage
import ru.shmr.finance.domain.model.Currency

private class AccountEditorViewModelStoreOwner : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
}

private val AccountEditorDraftSnapshotSaver = listSaver<AccountEditorDraftSnapshot, String>(
    save = {
        listOf(
            it.initialized.toString(),
            it.name,
            it.emoji,
            it.balance,
            it.currencyCode,
            it.activePicker,
        )
    },
    restore = {
        AccountEditorDraftSnapshot(
            initialized = it[0].toBoolean(),
            name = it[1],
            emoji = it[2],
            balance = it[3],
            currencyCode = it[4],
            activePicker = it[5],
        )
    },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountEditorRoute(
    accountId: Int?,
    defaultCurrency: Currency = Currency.RUB,
    onClose: () -> Unit,
) {
    var savedDraft by rememberSaveable(
        accountId,
        stateSaver = AccountEditorDraftSnapshotSaver,
    ) {
        mutableStateOf(AccountEditorDraftSnapshot())
    }
    val restoredDraft = savedDraft
    val factory = remember(accountId, defaultCurrency) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                AccountEditorViewModel(
                    accountId = accountId,
                    accountsRepository = ServiceLocator.accountsRepository,
                    defaultCurrencyProvider = DefaultAccountCurrencyProvider {
                        defaultCurrency
                    },
                    restoredDraft = restoredDraft,
                ) as T
        }
    }
    val storeOwner = remember(accountId) { AccountEditorViewModelStoreOwner() }
    DisposableEffect(storeOwner) {
        onDispose { storeOwner.viewModelStore.clear() }
    }
    val viewModel: AccountEditorViewModel = viewModel(
        viewModelStoreOwner = storeOwner,
        factory = factory,
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state) {
        if (!state.isLoading) {
            savedDraft = AccountEditorDraftSnapshot.from(state)
        }
    }

    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val isSaving by rememberUpdatedState(state.isSaving)
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { value ->
            value != SheetValue.Hidden || !isSaving
        },
    )

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                AccountEditorEffect.Saved -> onClose()
                is AccountEditorEffect.ShowMessage -> scope.launch {
                    val message = when (effect.message) {
                        AccountEditorMessage.ACCOUNT_NOT_FOUND -> R.string.editor_account_not_found
                        AccountEditorMessage.CURRENCY_HAS_HISTORY ->
                            R.string.editor_error_currency_history
                    }
                    snackbarHostState.showSnackbar(context.getString(message))
                }
                is AccountEditorEffect.ShowError -> scope.launch {
                    snackbarHostState.showSnackbar(effect.error.plainMessage(context))
                }
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = { if (!state.isSaving) onClose() },
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
        scrimColor = Color.Black.copy(alpha = 0.56f),
    ) {
        AccountEditorScreen(
            state = state,
            onAction = viewModel::onAction,
            snackbarHostState = snackbarHostState,
        )
    }
}
