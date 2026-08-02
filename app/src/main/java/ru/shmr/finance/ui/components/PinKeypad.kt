package ru.shmr.finance.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.shmr.finance.R
import ru.shmr.finance.domain.model.PIN_LENGTH

/**
 * Точки-индикаторы введённых цифр. Сами цифры на экране не показываются никогда.
 */
@Composable
fun PinDots(
    filled: Int,
    modifier: Modifier = Modifier,
    total: Int = PIN_LENGTH,
    isError: Boolean = false,
) {
    val accent = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val description = stringResource(R.string.cd_pin_progress, filled, total)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clearAndSetSemantics { contentDescription = description }
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        repeat(total) { index ->
            val isFilled = index < filled
            Box(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .size(12.dp)
                    .clip(CircleShape)
                    .border(width = 1.dp, color = accent, shape = CircleShape)
                    .background(if (isFilled) accent else Color.Transparent),
            )
        }
    }
}

/**
 * Цифровая клавиатура по макету: три колонки белых клавиш на тонированной подложке,
 * последний ряд — пусто, «0» и забой.
 */
@Composable
fun PinKeypad(
    onDigit: (Char) -> Unit,
    onBackspace: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        listOf("123", "456", "789").forEach { row ->
            KeypadRow {
                row.forEach { digit ->
                    KeypadKey(
                        modifier = Modifier.weight(1f),
                        enabled = enabled,
                        contentDescription = digit.toString(),
                        onClick = { onDigit(digit) },
                    ) {
                        Text(
                            text = digit.toString(),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Normal,
                        )
                    }
                }
            }
        }
        KeypadRow {
            Spacer(modifier = Modifier.weight(1f))
            KeypadKey(
                modifier = Modifier.weight(1f),
                enabled = enabled,
                contentDescription = "0",
                onClick = { onDigit('0') },
            ) {
                Text(
                    text = "0",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Normal,
                )
            }
            KeypadKey(
                modifier = Modifier.weight(1f),
                enabled = enabled,
                contentDescription = stringResource(R.string.cd_pin_backspace),
                onClick = onBackspace,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Backspace,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
            }
        }
    }
}

@Composable
private fun KeypadRow(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        content()
    }
}

@Composable
private fun KeypadKey(
    modifier: Modifier,
    enabled: Boolean,
    contentDescription: String,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLowest)
            .clickable(enabled = enabled, onClickLabel = contentDescription, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
