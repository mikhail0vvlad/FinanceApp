package ru.shmr.finance.ui.screens.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.shmr.finance.R
import ru.shmr.finance.domain.model.Account
import ru.shmr.finance.domain.model.Category

private data class PickerItem(
    val id: Int,
    val emoji: String,
    val title: String,
    val subtitle: String? = null,
)

@Composable
internal fun CategoryPickerSheet(
    categories: List<Category>,
    selectedId: Int?,
    onSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    SelectionPickerSheet(
        stringResource(R.string.editor_category),
        categories.map { PickerItem(it.id, it.emoji, it.name) },
        selectedId,
        onSelected,
        onDismiss,
    )
}

@Composable
internal fun AccountPickerSheet(
    accounts: List<Account>,
    selectedId: Int?,
    onSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    SelectionPickerSheet(
        stringResource(R.string.editor_account),
        accounts.map { PickerItem(it.id, it.emoji, it.name, it.balance.formatted()) },
        selectedId,
        onSelected,
        onDismiss,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SelectionPickerSheet(
    title: String,
    items: List<PickerItem>,
    selectedId: Int?,
    onSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLowest,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
            items(items, key = { it.id }) { item ->
                PickerItemRow(item, item.id == selectedId) { onSelected(item.id) }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun PickerItemRow(item: PickerItem, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Surface(
            Modifier.size(40.dp),
            shape = CircleShape,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) { Box(contentAlignment = Alignment.Center) { Text(item.emoji, fontSize = 20.sp) } }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                item.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            item.subtitle?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (selected) {
            Icon(Icons.Filled.Check, stringResource(R.string.cd_selected), tint = MaterialTheme.colorScheme.primary)
        }
    }
}
