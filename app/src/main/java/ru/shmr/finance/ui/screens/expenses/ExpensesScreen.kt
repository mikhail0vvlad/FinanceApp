package ru.shmr.finance.ui.screens.expenses

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.shmr.finance.R
import ru.shmr.finance.di.ServiceLocator
import ru.shmr.finance.ui.screens.ListScreen
import ru.shmr.finance.ui.screens.TransactionListViewModel

class ExpensesViewModel : TransactionListViewModel(
    isIncome = false,
    accountsRepository = ServiceLocator.accountsRepository,
    categoriesRepository = ServiceLocator.categoriesRepository,
    transactionsRepository = ServiceLocator.transactionsRepository,
)

@Composable
fun ExpensesScreen(
    modifier: Modifier = Modifier,
    onTransactionClick: (String) -> Unit = {},
    viewModel: ExpensesViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ListScreen(
        state = state,
        caption = stringResource(R.string.expenses_total),
        onRetry = viewModel::retry,
        onItemClick = onTransactionClick,
        modifier = modifier,
    )
}
