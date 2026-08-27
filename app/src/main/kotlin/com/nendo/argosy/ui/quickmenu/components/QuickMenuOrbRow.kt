package com.nendo.argosy.ui.quickmenu.components

import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import com.nendo.argosy.ui.util.clickableNoFocus
import com.nendo.argosy.ui.util.focusBorder
import com.nendo.argosy.ui.util.focusGlow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.nendo.argosy.R
import com.nendo.argosy.ui.quickmenu.QuickMenuOrb
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.ui.theme.Motion

private val ORB_SIZE = 56.dp  // Static: orb-specific design spec
private val ORB_SIZE_FOCUSED = 64.dp  // Static: orb-specific design spec
private val ORB_SPACING = 24.dp  // Static: orb-specific design spec

@Composable
fun QuickMenuOrbRow(
    selectedOrb: QuickMenuOrb,
    isOrbRowFocused: Boolean,
    onOrbClick: (QuickMenuOrb) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(ORB_SPACING, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        QuickMenuOrb.entries.forEach { orb ->
            Orb(
                orb = orb,
                isSelected = orb == selectedOrb,
                isRowFocused = isOrbRowFocused,
                onClick = { onOrbClick(orb) }
            )
        }
    }
}

@Composable
private fun Orb(
    orb: QuickMenuOrb,
    isSelected: Boolean,
    isRowFocused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isFocused = isSelected && isRowFocused
    val themeConfig = LocalLauncherTheme.current

    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.15f else 1.0f,
        animationSpec = Motion.focusSpring,
        label = "orbScale"
    )

    val iconAlpha by animateFloatAsState(
        targetValue = when {
            isFocused -> 1.0f
            isSelected && !isRowFocused -> 0.9f
            isRowFocused -> 0.7f
            else -> 0.4f
        },
        animationSpec = Motion.focusSpring,
        label = "iconAlpha"
    )

    val labelAlpha by animateFloatAsState(
        targetValue = when {
            isFocused -> 1.0f
            isSelected && !isRowFocused -> 0.8f
            isRowFocused -> 0.7f
            else -> 0.4f
        },
        animationSpec = Motion.focusSpring,
        label = "labelAlpha"
    )

    val glowAlpha by animateFloatAsState(
        targetValue = if (isFocused) 0.4f else 0f,
        animationSpec = Motion.focusSpring,
        label = "glowAlpha"
    )

    val backgroundColor = when {
        isFocused -> MaterialTheme.colorScheme.primary
        isSelected && !isRowFocused -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        isRowFocused -> MaterialTheme.colorScheme.surfaceVariant
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
    }

    val iconTint = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val glowColor = themeConfig.focusGlowColor

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .clickableNoFocus(onClick = onClick)
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
                .focusGlow(glowColor.copy(alpha = glowAlpha))
                .focusBorder(isFocused, MaterialTheme.colorScheme.primary, Dimens.borderMedium, CircleShape)
                .size(ORB_SIZE)
                .background(backgroundColor, CircleShape)
        ) {
            Icon(
                imageVector = orb.icon,
                contentDescription = stringResource(orb.labelRes),
                tint = iconTint.copy(alpha = iconAlpha),
                modifier = Modifier.size(Dimens.iconMd)
            )
        }

        Spacer(modifier = Modifier.height(Dimens.spacingSm))

        Text(
            text = stringResource(orb.labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = labelAlpha)
        )
    }
}

private val QuickMenuOrb.icon: ImageVector
    get() = when (this) {
        QuickMenuOrb.SEARCH -> Icons.Default.Search
        QuickMenuOrb.RANDOM -> Icons.Default.Casino
        QuickMenuOrb.MOST_PLAYED -> Icons.Default.Timer
        QuickMenuOrb.TOP_UNPLAYED -> Icons.Default.Star
        QuickMenuOrb.RECENT -> Icons.Default.History
        QuickMenuOrb.FAVORITES -> Icons.Default.Favorite
    }

@get:StringRes
private val QuickMenuOrb.labelRes: Int
    get() = when (this) {
        QuickMenuOrb.SEARCH -> R.string.ui_quick_menu_orb_search
        QuickMenuOrb.RANDOM -> R.string.ui_quick_menu_orb_random
        QuickMenuOrb.MOST_PLAYED -> R.string.ui_quick_menu_orb_most_played
        QuickMenuOrb.TOP_UNPLAYED -> R.string.ui_quick_menu_orb_top_unplayed
        QuickMenuOrb.RECENT -> R.string.ui_quick_menu_orb_recent
        QuickMenuOrb.FAVORITES -> R.string.ui_quick_menu_orb_favorites
    }
