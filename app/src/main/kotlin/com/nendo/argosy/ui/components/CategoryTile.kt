package com.nendo.argosy.ui.components

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalUiScale
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.util.clickableNoFocus
import com.nendo.argosy.ui.util.pressScale

/** Two-line storage category tile: icon + title left, primary stat + chevron right, secondary stat below. */
@Composable
fun CategoryTile(
    title: String,
    icon: ImageVector,
    primaryStat: String,
    secondaryStat: String,
    isFocused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isWorking: Boolean = false
) {
    val theme = LocalArgosyTheme.current
    val s = LocalUiScale.current.scale
    val interaction = remember { MutableInteractionSource() }
    val shape = RoundedCornerShape(Dimens.radiusControl)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .pressScale(interaction)
            .argosyFocusIndicators(
                focused = isFocused,
                indicators = FocusIndicators(fill = true, ring = true),
                tint = theme.focusAccent,
                shape = shape
            )
            .clip(shape)
            .heightIn(min = (ComponentDefaults.CategoryTile.minHeight * s).dp)
            .clickableNoFocus(interactionSource = interaction, onClick = onClick)
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm),
        contentAlignment = Alignment.CenterStart
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = theme.textPrimary,
                modifier = Modifier.size(Dimens.iconMd)
            )
            Spacer(modifier = Modifier.width(Dimens.spacingMd))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = theme.textPrimary,
                    maxLines = 1
                )
                Text(
                    text = secondaryStat,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textDim,
                    maxLines = 1
                )
            }
            Spacer(modifier = Modifier.width(Dimens.spacingMd))
            Text(
                text = primaryStat,
                style = MaterialTheme.typography.bodyMedium,
                color = theme.textDim,
                maxLines = 1
            )
            Spacer(modifier = Modifier.width(Dimens.spacingXs))
            if (isWorking) {
                CircularProgressIndicator(
                    modifier = Modifier.size(Dimens.iconSm),
                    strokeWidth = Dimens.borderMedium,
                    color = theme.textMute
                )
            } else {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = theme.textMute,
                    modifier = Modifier.size(Dimens.iconSm)
                )
            }
        }
    }
}
