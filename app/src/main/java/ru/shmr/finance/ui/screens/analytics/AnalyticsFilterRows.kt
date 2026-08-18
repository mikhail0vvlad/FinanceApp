package ru.shmr.finance.ui.screens.analytics

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.List
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Sell
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.shmr.finance.R

@Composable
internal fun AnalyticsTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().height(64.dp).padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, stringResource(R.string.cd_back))
        }
        Text(
            stringResource(R.string.analytics_title),
            fontSize = 24.sp,
            lineHeight = 32.sp,
            fontWeight = FontWeight.Normal,
            modifier = Modifier.padding(start = 4.dp),
        )
    }
}

@Composable
internal fun ChartLegend(shares: List<CategoryShare>) {
    Row(
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        shares.take(3).forEachIndexed { index, share ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                LegendDot(chartColor(index))
                Spacer(Modifier.width(6.dp))
                Text(
                    share.category.name,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
internal fun FilterRows(state: AnalyticsState, onOpenSheet: (AnalyticsSheet) -> Unit) {
    val filters = state.filters
    val allArticles = stringResource(R.string.all_articles)
    val articlesChip = remember(state.categories, filters.selectedCategoryIds, allArticles) {
        val ids = filters.selectedCategoryIds ?: return@remember allArticles
        state.categories.filter { it.id in ids }.joinToString { it.name }.ifEmpty { allArticles }
    }
    val accountChip = state.accounts.find { it.id == filters.selectedAccountId }?.name
        ?: stringResource(R.string.all_accounts)
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        FilterRow(
            rememberVectorPainter(Icons.AutoMirrored.Outlined.List),
            stringResource(R.string.filter_type),
            filters.type.label(),
        ) { onOpenSheet(AnalyticsSheet.TYPE) }
        FilterRow(
            painterResource(R.drawable.ic_calendar_month),
            stringResource(R.string.filter_period),
            formatPeriod(filters.startDate, filters.endDate),
        ) { onOpenSheet(AnalyticsSheet.PERIOD) }
        FilterRow(
            rememberVectorPainter(Icons.Outlined.Sell),
            stringResource(R.string.filter_articles),
            articlesChip,
        ) { onOpenSheet(AnalyticsSheet.ARTICLES) }
        FilterRow(
            rememberVectorPainter(Icons.Outlined.CreditCard),
            stringResource(R.string.filter_account),
            accountChip,
        ) { onOpenSheet(AnalyticsSheet.ACCOUNT) }
    }
}

@Composable
private fun FilterRow(icon: Painter, label: String, chipText: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(32.dp).border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface)
        }
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f).padding(start = 16.dp),
        )
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        ) {
            Text(
                chipText,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 220.dp)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
            )
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
