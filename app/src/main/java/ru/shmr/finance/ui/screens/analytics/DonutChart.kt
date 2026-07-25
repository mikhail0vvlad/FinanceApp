package ru.shmr.finance.ui.screens.analytics

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Палитра секторов диаграммы — в порядке убывания доли категории. */
val ChartPalette = listOf(
    Color(0xFFB69DF8),
    Color(0xFF4DD8E6),
    Color(0xFFF48FB1),
    Color(0xFF81C784),
    Color(0xFFFFB74D),
    Color(0xFF90A4AE),
    Color(0xFFE57373),
    Color(0xFF64B5F6),
    Color(0xFFA1887F),
    Color(0xFFDCE775),
)

fun chartColor(index: Int): Color = ChartPalette[index % ChartPalette.size]

@Composable
fun DonutChart(
    shares: List<CategoryShare>,
    caption: String,
    amount: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.size(240.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidth = 36.dp.toPx()
            val diameter = size.minDimension - strokeWidth
            val topLeft = Offset(
                (size.width - diameter) / 2,
                (size.height - diameter) / 2,
            )
            val arcSize = Size(diameter, diameter)
            var startAngle = -90f
            shares.forEachIndexed { index, share ->
                val sweep = share.fraction * 360f
                drawArc(
                    color = chartColor(index),
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = strokeWidth),
                )
                startAngle += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = caption,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = amount,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
