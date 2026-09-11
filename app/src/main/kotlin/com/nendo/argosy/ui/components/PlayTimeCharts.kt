package com.nendo.argosy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import com.nendo.argosy.ui.util.pressScale

/**
 * A titled horizontal bar: name and figures on top, fill fraction of [maxValue] below.
 * Focusable and tappable when [onClick] is given; a plain figure row otherwise.
 */
@Composable
fun PlayBarRow(
    name: String,
    valueLabel: String,
    value: Long,
    maxValue: Long,
    modifier: Modifier = Modifier,
    detail: String? = null,
    badge: String? = null,
    isFocused: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusControl)
    val interaction = remember { MutableInteractionSource() }
    val barColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val segments = remember(value, barColor) { listOf(barColor to value) }
    val rowModifier = if (onClick != null) {
        modifier
            .fillMaxWidth()
            .pressScale(interaction)
            .argosyFocusIndicators(
                focused = isFocused,
                indicators = FocusIndicators(fill = true, ring = true),
                tint = theme.focusAccent,
                shape = shape
            )
            .clip(shape)
            .clickableNoFocus(interactionSource = interaction, onClick = onClick)
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm)
    } else {
        modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingXs)
    }
    Column(
        modifier = rowModifier,
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                color = theme.textPrimary,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )
            if (badge != null) {
                Spacer(modifier = Modifier.width(Dimens.spacingSm))
                Text(
                    text = badge,
                    style = MaterialTheme.typography.labelSmall,
                    color = theme.textPrimary,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(RoundedCornerShape(Dimens.radiusPill))
                        .background(theme.surfaceRaised)
                        .padding(horizontal = Dimens.spacingSm, vertical = Dimens.spacingXs)
                )
            }
            Spacer(modifier = Modifier.width(Dimens.spacingMd))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = valueLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.textPrimary,
                    maxLines = 1
                )
                if (detail != null) {
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.bodySmall,
                        color = theme.textDim,
                        maxLines = 1
                    )
                }
            }
        }
        SegmentedMeterBar(
            totalBytes = maxValue,
            segments = segments,
            trackColor = trackColor
        )
    }
}

@Composable
fun PlayStatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val theme = LocalArgosyTheme.current
    Column(modifier = modifier) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleLarge,
            color = theme.textPrimary,
            maxLines = 1
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = theme.textDim,
            maxLines = 1
        )
    }
}
