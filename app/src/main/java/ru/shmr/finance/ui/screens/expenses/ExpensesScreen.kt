package ru.shmr.finance.ui.screens.expenses

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.time.LocalDate
import kotlinx.coroutines.flow.StateFlow
import ru.shmr.finance.R
import ru.shmr.finance.di.ServiceLocator
import ru.shmr.finance.domain.repository.AccountsRepository
import ru.shmr.finance.domain.repository.CategoriesRepository
import ru.shmr.finance.domain.repository.TransactionsRepository
import ru.shmr.finance.ui.screens.ListScreen
import ru.shmr.finance.ui.screens.TransactionListViewModel

class ExpensesViewModel(
    accountsRepository: AccountsRepository,
    categoriesRepository: CategoriesRepository,
    transactionsRepository: TransactionsRepository,
    selectedDate: StateFlow<LocalDate>,
) : TransactionListViewModel(
    isIncome = false,
    accountsRepository = accountsRepository,
    categoriesRepository = categoriesRepository,
    transactionsRepository = transactionsRepository,
    selectedDate = selectedDate,
)

@Composable
fun ExpensesScreen(
    selectedDate: StateFlow<LocalDate>,
    modifier: Modifier = Modifier,
    onTransactionClick: (String) -> Unit = {},
    viewModel: ExpensesViewModel = viewModel(
        factory = remember {
            viewModelFactory {
                initializer {
                    ExpensesViewModel(
                        accountsRepository = ServiceLocator.accountsRepository,
                        categoriesRepository = ServiceLocator.categoriesRepository,
                        transactionsRepository = ServiceLocator.transactionsRepository,
                        selectedDate = selectedDate,
                    )
                }
            }
        },
    ),
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
