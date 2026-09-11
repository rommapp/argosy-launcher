package com.nendo.argosy.ui.components.playtime

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.util.clickableNoFocus

private const val CARD_FILL_ALPHA = 0.3f
private const val ENGAGED_FILL_ALPHA = 0.5f

@Composable
fun PlayFigureCard(
    isFocused: Boolean,
    onFocus: () -> Unit,
    modifier: Modifier = Modifier,
    isEngaged: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusLg)
    val interaction = remember { MutableInteractionSource() }
    val fill = if (isEngaged) {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = ENGAGED_FILL_ALPHA)
    } else {
        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = CARD_FILL_ALPHA)
    }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .argosyFocusIndicators(
                focused = isFocused,
                indicators = FocusIndicators(ring = true),
                tint = if (isEngaged) MaterialTheme.colorScheme.primary else theme.focusAccent,
                shape = shape
            )
            .clip(shape)
            .background(fill)
            .clickableNoFocus(interactionSource = interaction, onClick = onFocus)
            .padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
        content = content
    )
}

@Composable
fun PlayFigureHeader(
    title: String,
    value: String?,
    subtitle: String? = null
) {
    val theme = LocalArgosyTheme.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = theme.textPrimary,
                maxLines = 1
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textDim,
                    maxLines = 1
                )
            }
        }
        if (value != null) {
            Spacer(modifier = Modifier.width(Dimens.spacingSm))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                color = theme.textPrimary,
                maxLines = 1
            )
        }
    }
}
