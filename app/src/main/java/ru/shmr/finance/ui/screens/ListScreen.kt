package ru.shmr.finance.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ru.shmr.finance.core.state.UiState
import ru.shmr.finance.domain.model.Account
import ru.shmr.finance.domain.model.Transaction
import ru.shmr.finance.ui.components.ErrorState
import ru.shmr.finance.ui.components.LeadContent
import ru.shmr.finance.ui.components.ListItemModel
import ru.shmr.finance.ui.components.ListItemRow
import ru.shmr.finance.ui.components.LoadingState
import ru.shmr.finance.ui.components.TotalHeader

data class ListScreenData(
    val total: String,
    val items: List<ListItemModel>,
)

@Composable
fun ListScreen(
    state: UiState<ListScreenData>,
    caption: String,
    modifier: Modifier = Modifier,
) {
    when (state) {
        UiState.Loading -> LoadingState(modifier)
        is UiState.Error -> ErrorState(state.message, modifier)
        is UiState.Content -> Column(modifier.fillMaxSize()) {
            TotalHeader(
                caption = caption,
                amount = state.data.total,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            LazyColumn {
                items(state.data.items, key = { it.id }) { ListItemRow(it) }
            }
        }
    }
}

fun Transaction.toListItem() = ListItemModel(
    id = id,
    lead = LeadContent.Emoji(category.emoji),
    title = category.name,
    subtitle = comment,
    trailingText = amount.formatted(),
)

fun Account.toListItem() = ListItemModel(
    id = id,
    lead = LeadContent.Emoji(emoji),
    title = name,
    trailingText = balance.formatted(),
)
