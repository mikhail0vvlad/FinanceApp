package ru.shmr.finance.ui.screens.accounts

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

class AccountsViewModel : ListScreenViewModel({
    ListScreenData(
        total = MockData.accountsTotal.formatted(),
        items = MockData.accounts.map { it.toListItem() },
    )
})

@Composable
fun AccountsScreen(
    modifier: Modifier = Modifier,
    viewModel: AccountsViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    ListScreen(state, stringResource(R.string.balance_total), modifier)
}
