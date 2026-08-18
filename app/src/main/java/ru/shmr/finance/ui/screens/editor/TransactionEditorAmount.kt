package ru.shmr.finance.ui.screens.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.shmr.finance.R

@Composable
internal fun HeroAmount(
    amount: String,
    currencySymbol: String,
    error: String?,
    enabled: Boolean,
    focusRequester: FocusRequester,
    onAmountChanged: (String) -> Unit,
    onDone: () -> Unit,
    label: String? = null,
) {
    val amountStyle = heroAmountStyle()
    Column(
        modifier = Modifier.fillMaxWidth().heightIn(min = 150.dp)
            .padding(horizontal = 24.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        if (label != null) HeroAmountLabel(label)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            AmountInput(amount, enabled, amountStyle, focusRequester, onAmountChanged, onDone)
            if (currencySymbol.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Text(currencySymbol, style = amountStyle)
            }
        }
        HorizontalDivider(
            modifier = Modifier.padding(top = 4.dp).width(220.dp),
            color = if (error == null) MaterialTheme.colorScheme.outlineVariant
            else MaterialTheme.colorScheme.error,
        )
        if (error != null) {
            Text(
                error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun heroAmountStyle() = TextStyle(
    color = MaterialTheme.colorScheme.onSurface,
    fontSize = 48.sp,
    lineHeight = 56.sp,
    fontWeight = FontWeight.Bold,
    textAlign = TextAlign.End,
)

@Composable
private fun HeroAmountLabel(label: String) {
    Text(
        label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Medium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

@Composable
private fun AmountInput(
    amount: String,
    enabled: Boolean,
    style: TextStyle,
    focusRequester: FocusRequester,
    onAmountChanged: (String) -> Unit,
    onDone: () -> Unit,
) {
    BasicTextField(
        value = amount,
        onValueChange = onAmountChanged,
        enabled = enabled,
        singleLine = true,
        textStyle = style,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        keyboardOptions = KeyboardOptions(
            keyboardType = KeyboardType.Decimal,
            imeAction = ImeAction.Done,
        ),
        keyboardActions = KeyboardActions(onDone = { onDone() }),
        decorationBox = { inner ->
            Box(contentAlignment = Alignment.CenterEnd) {
                if (amount.isBlank()) Text("0", style = style)
                inner()
            }
        },
        modifier = Modifier.width((amount.length.coerceAtLeast(1) * 32).coerceIn(48, 230).dp)
            .focusRequester(focusRequester),
    )
}

@Composable
internal fun ConfirmButton(
    loading: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    FloatingActionButton(
        onClick = { if (enabled) onClick() },
        modifier = modifier.size(58.dp),
        shape = CircleShape,
        containerColor = MaterialTheme.colorScheme.onSurface,
        contentColor = MaterialTheme.colorScheme.surface,
    ) {
        if (loading) {
            CircularProgressIndicator(
                Modifier.size(24.dp),
                color = MaterialTheme.colorScheme.surface,
                strokeWidth = 2.dp,
            )
        } else {
            Icon(Icons.Filled.Check, stringResource(R.string.cd_save), Modifier.size(30.dp))
        }
    }
}
