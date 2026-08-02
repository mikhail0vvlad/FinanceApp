package ru.shmr.finance.ui.screens.editor

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import ru.shmr.finance.di.ServiceLocator

private class TransactionEditorViewModelStoreOwner : ViewModelStoreOwner {
    override val viewModelStore = ViewModelStore()
}

private val TransactionEditorDraftSnapshotSaver = listSaver<TransactionEditorDraftSnapshot, String>(
    save = {
        listOf(
            it.initialized.toString(),
            it.accountId?.toString().orEmpty(),
            it.categoryId?.toString().orEmpty(),
            it.amount,
            it.date,
            it.time,
            it.comment,
            it.activePicker,
            it.pendingDate,
            it.pendingTime,
        )
    },
    restore = {
        TransactionEditorDraftSnapshot(
            initialized = it[0].toBoolean(),
            accountId = it[1].toIntOrNull(),
            categoryId = it[2].toIntOrNull(),
            amount = it[3],
            date = it[4],
            time = it[5],
            comment = it[6],
            activePicker = it[7],
            pendingDate = it[8],
            pendingTime = it[9],
        )
    },
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionEditorRoute(
    target: TransactionEditorTarget,
    onClose: () -> Unit,
) {
    var savedDraft by rememberSaveable(
        target.isIncome,
        target.localId,
        stateSaver = TransactionEditorDraftSnapshotSaver,
    ) {
        mutableStateOf(TransactionEditorDraftSnapshot())
    }
    val restoredDraft = savedDraft
    val factory = remember(target) {
        object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                TransactionEditorViewModel(
                    isIncome = target.isIncome,
                    localId = target.localId,
                    accountsRepository = ServiceLocator.accountsRepository,
                    categoriesRepository = ServiceLocator.categoriesRepository,
                    transactionsRepository = ServiceLocator.transactionsRepository,
                    restoredDraft = restoredDraft,
                ) as T
        }
    }
    val storeOwner = remember(target) { TransactionEditorViewModelStoreOwner() }
    DisposableEffect(storeOwner) {
        onDispose { storeOwner.viewModelStore.clear() }
    }
    val viewModel: TransactionEditorViewModel = viewModel(
        viewModelStoreOwner = storeOwner,
        factory = factory,
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state) {
        if (!state.isLoading) {
            savedDraft = TransactionEditorDraftSnapshot.from(state)
        }
    }
    val isSaving by rememberUpdatedState(state.isSaving)
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
        confirmValueChange = { value ->
            value != SheetValue.Hidden || !isSaving
        },
    )

    LaunchedEffect(viewModel) {
        viewModel.effects.collect { effect ->
            when (effect) {
                TransactionEditorEffect.Saved -> onClose()
                is TransactionEditorEffect.ShowMessage -> scope.launch {
                    snackbarHostState.showSnackbar(effect.message)
                }
            }
        }
    }

    val requestClose = {
        if (!state.isSaving) onClose()
    }
    ModalBottomSheet(
        onDismissRequest = requestClose,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLowest,
        scrimColor = Color.Black.copy(alpha = 0.56f),
    ) {
        TransactionEditorScreen(
            state = state,
            onAction = viewModel::onAction,
            snackbarHostState = snackbarHostState,
        )
    }
}
