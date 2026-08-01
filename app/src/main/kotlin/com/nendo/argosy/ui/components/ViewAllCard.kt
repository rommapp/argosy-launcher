package com.nendo.argosy.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.util.clickableNoFocus
import com.nendo.argosy.ui.util.touchOnly

private const val VIEW_ALL_OUTLINE_SCALE_STIFFNESS = 300f
private const val VIEW_ALL_OUTLINE_BORDER_TWEEN_MS = 200
private const val VIEW_ALL_OUTLINE_FILL_TOP_ALPHA = 0.15f
private const val VIEW_ALL_OUTLINE_FILL_BOTTOM_ALPHA = 0.05f
private const val VIEW_ALL_OUTLINE_MUTED_ALPHA = 0.3f

/**
 * Visual variant of the trailing rail card. OUTLINE_GRID is the launcher's unlabelled grid glyph
 * that grows with the rail's focus scale; ACCENT_COUNT is the companion's fixed tile carrying the
 * number of games left behind.
 */
enum class ViewAllCardStyle { OUTLINE_GRID, ACCENT_COUNT }

@Composable
fun ViewAllCard(
    isFocused: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    style: ViewAllCardStyle = ViewAllCardStyle.OUTLINE_GRID,
    tapMode: CarouselTapMode = CarouselTapMode.CLICK,
    remainingCount: Int = 0,
    focusScale: Float = 1f,
    scaleFromBottom: Boolean = false
) {
    val scale by animateFloatAsState(
        targetValue = if (isFocused) focusScale else 1f,
        animationSpec = spring(stiffness = VIEW_ALL_OUTLINE_SCALE_STIFFNESS),
        label = "viewAllScale"
    )
    val tapModifier = when (tapMode) {
        CarouselTapMode.CLICK -> Modifier.clickableNoFocus(onClick = onClick)
        CarouselTapMode.TOUCH -> Modifier.touchOnly(onClick)
    }
    val scaledModifier = modifier.graphicsLayer {
        scaleX = scale
        scaleY = scale
        transformOrigin = if (scaleFromBottom) TransformOrigin(0.5f, 1f) else TransformOrigin.Center
    }

    when (style) {
        ViewAllCardStyle.OUTLINE_GRID -> OutlineGridViewAllCard(
            isFocused = isFocused,
            tapModifier = tapModifier,
            modifier = scaledModifier
        )
        ViewAllCardStyle.ACCENT_COUNT -> AccentCountViewAllCard(
            isFocused = isFocused,
            remainingCount = remainingCount,
            tapModifier = tapModifier,
            modifier = scaledModifier
        )
    }
}

@Composable
private fun OutlineGridViewAllCard(
    isFocused: Boolean,
    tapModifier: Modifier,
    modifier: Modifier = Modifier
) {
    val onSurfaceColor = MaterialTheme.colorScheme.onSurface
    val borderColor by animateColorAsState(
        targetValue = if (isFocused) {
            onSurfaceColor
        } else {
            onSurfaceColor.copy(alpha = VIEW_ALL_OUTLINE_MUTED_ALPHA)
        },
        animationSpec = tween(VIEW_ALL_OUTLINE_BORDER_TWEEN_MS),
        label = "viewAllBorder"
    )

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        onSurfaceColor.copy(alpha = VIEW_ALL_OUTLINE_FILL_TOP_ALPHA),
                        onSurfaceColor.copy(alpha = VIEW_ALL_OUTLINE_FILL_BOTTOM_ALPHA)
                    )
                ),
                RoundedCornerShape(Dimens.radiusMd)
            )
            .border(Dimens.borderThin, borderColor, RoundedCornerShape(Dimens.radiusMd))
            .then(tapModifier)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(Dimens.radiusLg)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
                modifier = Modifier.padding(bottom = Dimens.radiusLg)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs)) {
                    GridBox()
                    GridBox()
                }
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs)) {
                    GridBox()
                    GridBox()
                }
            }
            Text(
                text = "View All",
                style = MaterialTheme.typography.labelLarge,
                color = onSurfaceColor.copy(alpha = VIEW_ALL_OUTLINE_MUTED_ALPHA),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun GridBox() {
    Box(
        modifier = Modifier
            .size(Dimens.iconMd)
            .background(
                MaterialTheme.colorScheme.onSurface.copy(alpha = VIEW_ALL_OUTLINE_MUTED_ALPHA),
                RoundedCornerShape(Dimens.radiusSm)
            )
    )
}

@Composable
private fun AccentCountViewAllCard(
    isFocused: Boolean,
    remainingCount: Int,
    tapModifier: Modifier,
    modifier: Modifier = Modifier
) {
    val theme = LocalArgosyTheme.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(Dimens.radiusControl))
            .then(
                if (isFocused) {
                    Modifier.border(
                        width = Dimens.borderThick,
                        color = theme.focusAccent,
                        shape = RoundedCornerShape(Dimens.radiusControl)
                    )
                } else {
                    Modifier
                }
            )
            .background(theme.surfaceRaised)
            .then(tapModifier),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Default.GridView,
                contentDescription = null,
                tint = theme.focusAccent,
                modifier = Modifier.size(ComponentDefaults.Carousel.viewAllIconSize.dp)
            )
            Spacer(modifier = Modifier.height(Dimens.spacingXs))
            Text(
                text = "+$remainingCount",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = theme.focusAccent
            )
            Text(
                text = "View All",
                style = MaterialTheme.typography.labelSmall,
                color = theme.textDim
            )
        }
    }
}
