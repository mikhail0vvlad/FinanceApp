package ru.shmr.finance.ui.screens.expenses

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.shmr.finance.R
import ru.shmr.finance.data.mock.MockData
import ru.shmr.finance.ui.screens.ListScreen
import ru.shmr.finance.ui.screens.ListScreenData
import ru.shmr.finance.ui.screens.ListScreenViewModel
import ru.shmr.finance.ui.screens.toListItem

class ExpensesViewModel : ListScreenViewModel({
    ListScreenData(
        total = MockData.expensesTotal.formatted(),
        items = MockData.expenses.map { it.toListItem() },
    )
})

@Composable
fun ExpensesScreen(
    modifier: Modifier = Modifier,
    viewModel: ExpensesViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ListScreen(state, stringResource(R.string.expenses_total), modifier)
}
