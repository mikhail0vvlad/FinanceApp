package ru.shmr.finance.ui.screens.analytics

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Checkbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ru.shmr.finance.R
import ru.shmr.finance.domain.model.Account
import ru.shmr.finance.domain.model.Category
import ru.shmr.finance.ui.components.LeadContent
import ru.shmr.finance.ui.components.ListItemModel
import ru.shmr.finance.ui.components.ListItemRow

@Composable
internal fun ArticlesFilterSheet(
    categories: List<Category>,
    selectedIds: Set<Int>?,
    onApply: (Set<Int>?) -> Unit,
    onDismiss: () -> Unit,
) {
    var checkedIds by remember { mutableStateOf(selectedIds ?: categories.map { it.id }.toSet()) }
    FilterSheet(onDismiss) {
        SheetTitle(stringResource(R.string.filter_articles))
        LazyColumn(modifier = Modifier.weight(1f, fill = false)) {
            items(categories, key = { it.id }) { category ->
                CategorySelectionRow(category, category.id in checkedIds) {
                    checkedIds = checkedIds.toggle(category.id)
                }
                SheetDivider()
            }
        }
        SheetButton(stringResource(R.string.action_apply)) {
            onApply(if (checkedIds.size == categories.size) null else checkedIds)
            onDismiss()
        }
    }
}

@Composable
private fun CategorySelectionRow(category: Category, checked: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ListItemRow(
            item = ListItemModel(
                id = category.id.toString(),
                lead = LeadContent.Emoji(category.emoji),
                title = category.name,
            ),
            modifier = Modifier.weight(1f),
        )
        Checkbox(checked = checked, onCheckedChange = null)
    }
}

@Composable
internal fun AccountFilterSheet(
    accounts: List<Account>,
    selectedAccountId: Int?,
    onSelected: (Int?) -> Unit,
    onDismiss: () -> Unit,
) {
    FilterSheet(onDismiss) {
        SheetTitle(stringResource(R.string.filter_account))
        AccountSelectionRow("all", "💳", stringResource(R.string.all_accounts), selectedAccountId == null) {
            onSelected(null)
            onDismiss()
        }
        SheetDivider()
        accounts.forEach { account ->
            AccountSelectionRow(
                account.id.toString(),
                "🏦",
                account.name,
                selectedAccountId == account.id,
            ) {
                onSelected(account.id)
                onDismiss()
            }
            SheetDivider()
        }
    }
}

@Composable
private fun AccountSelectionRow(
    id: String,
    emoji: String,
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ListItemRow(
            item = ListItemModel(id = id, lead = LeadContent.Emoji(emoji), title = title),
            modifier = Modifier.weight(1f),
        )
        SelectionMark(selected = selected, filled = false)
    }
}

private fun Set<Int>.toggle(id: Int): Set<Int> = if (id in this) this - id else this + id
