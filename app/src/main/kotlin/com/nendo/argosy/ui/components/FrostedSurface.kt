package com.nendo.argosy.ui.components

import android.os.Build
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextStyle
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.ui.theme.LocalUiScale
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.generated.ColorTokens
import com.nendo.argosy.ui.theme.generated.ComponentDefaults

@Stable
class FrostedBackdrop internal constructor(
    internal val source: GraphicsLayer,
    internal val blurred: GraphicsLayer
) {
    internal var position by mutableStateOf(Offset.Zero)
    internal var ready by mutableStateOf(false)
}

val LocalFrostedBackdrop = staticCompositionLocalOf<FrostedBackdrop?> { null }

@Composable
fun rememberFrostedBackdrop(): FrostedBackdrop {
    val source = rememberGraphicsLayer()
    val blurred = rememberGraphicsLayer()
    val radius = with(LocalDensity.current) { Motion.blurRadiusDrawer.toPx() }
    blurred.renderEffect = remember(radius) { BlurEffect(radius, radius, TileMode.Clamp) }
    return remember(source, blurred) { FrostedBackdrop(source, blurred) }
}

/**
 * Background-only capture, shared by frosted surfaces on the same display.
 */
@Composable
fun Modifier.frostedBackdropSource(backdrop: FrostedBackdrop): Modifier {
    val view = LocalView.current
    DisposableEffect(backdrop) {
        onDispose { backdrop.ready = false }
    }
    return onGloballyPositioned {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        backdrop.position = it.positionInRoot() + Offset(location[0].toFloat(), location[1].toFloat())
    }
        .drawWithContent {
            backdrop.source.record { this@drawWithContent.drawContent() }
            backdrop.blurred.record { drawLayer(backdrop.source) }
            backdrop.ready = true
            drawLayer(backdrop.source)
        }
}

@Composable
fun frostedSurfaceColor(): Color = if (LocalLauncherTheme.current.isDarkTheme) {
    ColorTokens.Domain.FrostedSurface.dark
} else {
    ColorTokens.Domain.FrostedSurface.light
}

val minimumTouchTarget: Dp get() = ComponentDefaults.FrostedSurface.minimumTouchTargetDp.dp

val frostedVisualChromeHeight: Dp
    @Composable get() = ComponentDefaults.FrostedSurface.chromeHeightDp.dp * LocalUiScale.current.scale

val frostedChromeHeight: Dp
    @Composable get() = maxOf(minimumTouchTarget, frostedVisualChromeHeight)

@Composable
internal fun chromeTextStyle(style: TextStyle, baseline: TextStyle, fontSize: TextUnit): TextStyle {
    val scale = fontSize.value / baseline.fontSize.value * LocalUiScale.current.scale
    return style.copy(fontSize = style.fontSize * scale, lineHeight = style.lineHeight * scale)
}

@Composable
fun Modifier.frostedSurface(
    shape: Shape = CircleShape,
    color: Color = frostedSurfaceColor(),
    mask: Painter? = null
): Modifier {
    val backdrop = LocalFrostedBackdrop.current
    val view = LocalView.current
    var position by remember { mutableStateOf(Offset.Zero) }
    val fill = color.copy(alpha = ComponentDefaults.FrostedSurface.fillAlpha)
    val maskPaint = remember { Paint().apply { blendMode = BlendMode.DstIn } }
    return onGloballyPositioned {
        val location = IntArray(2)
        view.getLocationOnScreen(location)
        position = it.positionInRoot() + Offset(location[0].toFloat(), location[1].toFloat())
    }
        .clip(shape)
        .then(if (mask != null) Modifier.graphicsLayer {
            compositingStrategy = CompositingStrategy.Offscreen
        } else Modifier)
        .drawWithContent {
            if (backdrop != null && backdrop.ready && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val offset = backdrop.position - position
                translate(offset.x, offset.y) { drawLayer(backdrop.blurred) }
            }
            drawRect(fill)
            if (mask != null) {
                drawIntoCanvas { canvas ->
                    canvas.saveLayer(Rect(Offset.Zero, size), maskPaint)
                    with(mask) { draw(size, colorFilter = ColorFilter.tint(Color.White)) }
                    canvas.restore()
                }
            }
            drawContent()
        }
}

internal fun contrastingFrostedContent(color: Color): Color {
    val fill = color.copy(alpha = ComponentDefaults.FrostedSurface.fillAlpha)
    val darkContrast = (fill.compositeOver(Color.Black).luminance() + 0.05f) / 0.05f
    val lightContrast = 1.05f / (fill.compositeOver(Color.White).luminance() + 0.05f)
    return if (darkContrast >= lightContrast) Color.Black else Color.White
}
