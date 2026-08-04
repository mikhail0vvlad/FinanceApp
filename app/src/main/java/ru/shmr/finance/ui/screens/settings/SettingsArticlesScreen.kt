package ru.shmr.finance.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import ru.shmr.finance.R
import ru.shmr.finance.core.state.UiState
import ru.shmr.finance.domain.model.Category

@Composable
internal fun ArticlesScreen(
    query: String,
    categoriesState: UiState<List<Category>>,
    onQueryChanged: (String) -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            .padding(bottom = 28.dp),
    ) {
        ScreenTitle(stringResource(R.string.settings_articles), onBack)
        ArticleSearchField(query, onQueryChanged)
        when (categoriesState) {
            UiState.Loading -> Box(
                Modifier.fillMaxWidth().height(160.dp),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }
            UiState.Empty -> EmptyArticles(query.isNotBlank())
            is UiState.Error -> EmptyArticles(false)
            is UiState.Content -> Column(Modifier.fillMaxWidth()) {
                categoriesState.data.forEach { CategoryRow(it) }
            }
        }
    }
}

@Composable
private fun ArticleSearchField(query: String, onQueryChanged: (String) -> Unit) {
    val description = stringResource(R.string.settings_articles_search)
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChanged,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp)
            .semantics { contentDescription = description },
        placeholder = { Text(description) },
        leadingIcon = { Icon(Icons.Outlined.Search, null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                Icon(
                    Icons.Outlined.Close,
                    stringResource(R.string.settings_clear_search),
                    Modifier.clickable { onQueryChanged("") },
                )
            }
        },
        singleLine = true,
        shape = RoundedCornerShape(28.dp),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    )
}

@Composable
private fun EmptyArticles(searchHasNoResults: Boolean) {
    Text(
        stringResource(
            if (searchHasNoResults) R.string.settings_articles_no_results
            else R.string.settings_articles_empty,
        ),
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(20.dp),
    )
}

@Composable
private fun CategoryRow(category: Category) {
    Row(
        modifier = Modifier.fillMaxWidth().semantics {
            contentDescription = "${category.name}, ${category.emoji}"
        }.padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            category.emoji,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.size(36.dp),
        )
        Text(
            category.name,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f).padding(start = 12.dp),
        )
    }
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 20.dp),
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}
