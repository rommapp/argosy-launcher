package com.nendo.argosy.ui.components.nowplaying

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.util.clickableNoFocus

private val TransportFocus = FocusIndicators(fill = true)
private val PlayFocus = FocusIndicators(ring = true)

@Composable
fun PlayPauseButton(
    isPlaying: Boolean,
    playDescription: String,
    pauseDescription: String,
    size: Dp,
    isFocused: Boolean,
    onClick: () -> Unit,
    iconSize: Dp = Dimens.iconMd
) {
    val theme = LocalArgosyTheme.current
    val ringInset = Dimens.borderMedium + Dimens.spacingXs
    Box(
        modifier = Modifier
            .size(size)
            .argosyFocusIndicators(
                focused = isFocused,
                indicators = PlayFocus,
                shape = CircleShape,
                ringColor = theme.focusAccent,
                ringThickness = Dimens.borderMedium
            )
            .clip(CircleShape)
            .clickableNoFocus(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .size(size - ringInset * 2)
                .clip(CircleShape)
                .background(theme.focusAccent),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) pauseDescription else playDescription,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}

@Composable
fun TransportIconButton(
    icon: ImageVector,
    description: String,
    size: Dp,
    isFocused: Boolean,
    onClick: () -> Unit,
    showDot: Boolean = false
) {
    val theme = LocalArgosyTheme.current
    Box(
        modifier = Modifier
            .size(size)
            .argosyFocusIndicators(focused = isFocused, indicators = TransportFocus, shape = CircleShape)
            .clip(CircleShape)
            .clickableNoFocus(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = description,
            tint = if (isFocused || showDot) theme.focusAccent else theme.textPrimary,
            modifier = Modifier.size(Dimens.iconMd)
        )
        if (showDot) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = Dimens.spacingXs)
                    .size(Dimens.spacingXs)
                    .clip(CircleShape)
                    .background(theme.focusAccent)
            )
        }
    }
}
