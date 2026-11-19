package com.nendo.argosy.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.motionEnter
import com.nendo.argosy.ui.theme.motionExit
import com.nendo.argosy.ui.util.clickableNoFocus

internal const val DRAWER_FOCUS_WASH_ALPHA = 0.15f
private const val NAV_ROW_SELECTED_FILL_ALPHA = 0.5f
private const val NAV_ROW_DISABLED_ALPHA = 0.4f

/**
 * Navigation drawer row: a primary left indicator while focused, the icon, then the label.
 * Setting [labelVisible] to false animates the label and [trailing] out so the row narrows
 * to its icon. A disabled row keeps its focus visuals and dims its content.
 */
@Composable
fun NavDrawerRow(
    icon: ImageVector,
    label: String,
    isFocused: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    labelVisible: Boolean = true,
    trailing: @Composable RowScope.() -> Unit = {}
) {
    val backgroundColor = when {
        isFocused -> LocalArgosyTheme.current.focusAccent.copy(alpha = DRAWER_FOCUS_WASH_ALPHA)
        isSelected -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = NAV_ROW_SELECTED_FILL_ALPHA)
        else -> Color.Transparent
    }

    val baseContentColor = if (isFocused || isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val contentColor = if (enabled) baseContentColor else baseContentColor.copy(alpha = NAV_ROW_DISABLED_ALPHA)

    val indicatorWidth = if (isFocused) Dimens.spacingXs else 0.dp
    val shape = RoundedCornerShape(topEnd = Dimens.radiusMd, bottomEnd = Dimens.radiusMd)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start,
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(backgroundColor)
            .clickableNoFocus(enabled = enabled, onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .width(indicatorWidth)
                .height(Dimens.spacingXxl)
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(modifier = Modifier.width(if (isFocused) (Dimens.spacingLg - Dimens.spacingXs) else Dimens.spacingLg))
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = contentColor
        )
        AnimatedVisibility(
            visible = labelVisible,
            modifier = Modifier.weight(1f),
            enter = motionEnter(
                expandHorizontally(tween(Motion.durationSlide, easing = Motion.argosyEase)) +
                    fadeIn(tween(Motion.durationContent))
            ),
            exit = motionExit(
                shrinkHorizontally(tween(Motion.durationSlide, easing = Motion.argosyEase)) +
                    fadeOut(tween(Motion.durationContent))
            )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Spacer(modifier = Modifier.width(Dimens.spacingMd))
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                trailing()
            }
        }
    }
}
