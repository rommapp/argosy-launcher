package com.nendo.argosy.ui.util

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.ContentDrawScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import com.nendo.argosy.ui.theme.generated.MotionTokens

/** Fades list content at the edges, but only toward directions that can still scroll. */
fun Modifier.verticalEdgeFade(
    listState: LazyListState,
    fadeHeight: Dp,
    top: Boolean = true,
    bottom: Boolean = true,
): Modifier = composed {
    val topAlpha by animateFloatAsState(
        targetValue = if (top && listState.canScrollBackward) 1f else 0f,
        animationSpec = tween(MotionTokens.Tween.microMs),
        label = "fade-top",
    )
    val bottomAlpha by animateFloatAsState(
        targetValue = if (bottom && listState.canScrollForward) 1f else 0f,
        animationSpec = tween(MotionTokens.Tween.microMs),
        label = "fade-bottom",
    )
    edgeFadeDraw(fadeHeight, { topAlpha }, { bottomAlpha })
}

/** ScrollState variant for plain scrollable columns. */
fun Modifier.verticalEdgeFade(
    gridState: androidx.compose.foundation.lazy.grid.LazyGridState,
    fadeHeight: Dp,
    top: Boolean = true,
    bottom: Boolean = true,
): Modifier = composed {
    val topAlpha by animateFloatAsState(
        targetValue = if (top && gridState.canScrollBackward) 1f else 0f,
        animationSpec = tween(MotionTokens.Tween.microMs),
        label = "grid-fade-top",
    )
    val bottomAlpha by animateFloatAsState(
        targetValue = if (bottom && gridState.canScrollForward) 1f else 0f,
        animationSpec = tween(MotionTokens.Tween.microMs),
        label = "grid-fade-bottom",
    )
    edgeFadeDraw(fadeHeight, { topAlpha }, { bottomAlpha })
}

fun Modifier.verticalEdgeFade(
    scrollState: ScrollState,
    fadeHeight: Dp,
    top: Boolean = true,
    bottom: Boolean = true,
): Modifier = composed {
    val topAlpha by animateFloatAsState(
        targetValue = if (top && scrollState.canScrollBackward) 1f else 0f,
        animationSpec = tween(MotionTokens.Tween.microMs),
        label = "fade-top",
    )
    val bottomAlpha by animateFloatAsState(
        targetValue = if (bottom && scrollState.canScrollForward) 1f else 0f,
        animationSpec = tween(MotionTokens.Tween.microMs),
        label = "fade-bottom",
    )
    edgeFadeDraw(fadeHeight, { topAlpha }, { bottomAlpha })
}

private fun Modifier.edgeFadeDraw(
    fadeHeight: Dp,
    topAlpha: () -> Float,
    bottomAlpha: () -> Float,
): Modifier = graphicsLayer(compositingStrategy = CompositingStrategy.Offscreen)
    .drawWithContent {
        drawContent()
        val fadePx = fadeHeight.toPx().coerceAtMost(size.height / 2f)
        drawEdge(topAlpha(), fadePx, top = true)
        drawEdge(bottomAlpha(), fadePx, top = false)
    }

private fun ContentDrawScope.drawEdge(alpha: Float, fadePx: Float, top: Boolean) {
    if (alpha <= 0f) return
    val solid = Color.Black
    val faded = Color.Black.copy(alpha = 1f - alpha)
    if (top) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(faded, solid),
                startY = 0f,
                endY = fadePx,
            ),
            size = Size(size.width, fadePx),
            blendMode = BlendMode.DstIn,
        )
    } else {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(solid, faded),
                startY = size.height - fadePx,
                endY = size.height,
            ),
            topLeft = Offset(0f, size.height - fadePx),
            size = Size(size.width, fadePx),
            blendMode = BlendMode.DstIn,
        )
    }
}
