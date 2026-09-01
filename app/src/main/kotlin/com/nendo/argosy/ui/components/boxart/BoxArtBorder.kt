package com.nendo.argosy.ui.components.boxart

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nendo.argosy.data.preferences.BoxArtBorderStyle
import com.nendo.argosy.data.preferences.BoxArtInnerEffect
import com.nendo.argosy.data.preferences.SystemIconPosition
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalBoxArtStyle
import com.nendo.argosy.ui.theme.generated.ComponentDefaults

/**
 * The GLASS and GRADIENT border styles, drawn over artwork of any kind.
 *
 * `boxArtFrame` covers only the SOLID style, because SOLID is a plain stroke and the other two
 * are masks cut around the platform badge. That is why these lived inside `GameCard` and why
 * every other cover surface silently had no border. They take an image model rather than a file
 * path so a remote poster and a local cover both work.
 */

@Composable
internal fun rememberShineSweep(active: Boolean): State<Float> {
    val transition = if (active) rememberInfiniteTransition(label = "innerShine") else null
    return transition?.animateFloat(
        initialValue = -0.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "shine"
    ) ?: remember { mutableStateOf(0f) }
}

/**
 * Geometry for a surface with no platform badge to cut around, which is every cover that is not
 * a game card.
 */
internal fun unbadgedGeometry(
    outerCornerRadiusPx: Float,
    frameWidthPx: Float,
    oneDpPx: Float,
    innerEffect: BoxArtInnerEffect,
    innerEffectWidth: Float
): BoxArtGeometry = BoxArtGeometry(
    outerCornerRadiusPx = outerCornerRadiusPx,
    frameWidthPx = frameWidthPx,
    oneDpPx = oneDpPx,
    badgeWidthPx = 0f,
    badgeHeightPx = 0f,
    scaledCornerRadiusPx = 0f,
    innerEffect = innerEffect,
    innerEffectWidth = innerEffectWidth,
    effectiveBadgePosition = SystemIconPosition.OFF
)

internal fun glassColorFilterFor(
    gradientColors: Pair<Color, Color>?,
    borderColor: Color,
    glassBorderTintAlpha: Float
): ColorFilter? = when {
    gradientColors != null -> {
        val tint = lerp(Color.White, gradientColors.first, (glassBorderTintAlpha * 2).coerceIn(0f, 1f))
        ColorFilter.lighting(multiply = tint, add = Color.Black)
    }
    glassBorderTintAlpha > 0f -> {
        val tint = lerp(Color.White, borderColor, glassBorderTintAlpha)
        ColorFilter.lighting(multiply = tint, add = Color.Black)
    }
    else -> null
}

/**
 * The focused border for a cover with no platform badge to cut around, which is every surface
 * except the game card. Draws nothing for SOLID, which `boxArtFrame` already strokes, and
 * nothing while unfocused, matching when a game card shows its own.
 */
@Composable
internal fun UnbadgedBoxArtBorder(
    imageModel: Any?,
    gradientColors: Pair<Color, Color>?,
    isFocused: Boolean
) {
    val boxArtStyle = LocalBoxArtStyle.current
    if (!isFocused || boxArtStyle.borderThicknessDp.value <= 0f) return
    if (boxArtStyle.borderStyle != BoxArtBorderStyle.GLASS &&
        boxArtStyle.borderStyle != BoxArtBorderStyle.GRADIENT
    ) {
        return
    }

    val density = LocalDensity.current
    val geometry = unbadgedGeometry(
        outerCornerRadiusPx = with(density) { boxArtStyle.cornerRadiusDp.toPx() },
        frameWidthPx = with(density) { boxArtStyle.borderThicknessDp.toPx() },
        oneDpPx = with(density) { Dimens.borderThin.toPx() },
        innerEffect = boxArtStyle.innerEffect,
        innerEffectWidth = boxArtStyle.innerEffectThicknessPx
    )
    val sweepOffset by rememberShineSweep(boxArtStyle.innerEffect == BoxArtInnerEffect.SHINE)

    when (boxArtStyle.borderStyle) {
        BoxArtBorderStyle.GLASS -> GlassBorderOverlay(
            imageModel = imageModel,
            geometry = geometry,
            glassColorFilter = glassColorFilterFor(
                gradientColors = gradientColors,
                borderColor = MaterialTheme.colorScheme.primary,
                glassBorderTintAlpha = boxArtStyle.glassBorderTintAlpha
            ),
            sweepOffset = sweepOffset
        )
        BoxArtBorderStyle.GRADIENT -> if (gradientColors != null) {
            GradientBorderOverlay(
                imageModel = imageModel,
                gradientColors = gradientColors,
                gradientBorderProgress = 1f,
                geometry = geometry,
                sweepOffset = sweepOffset
            )
        }
        else -> {}
    }
}

@Composable
internal fun GlassBorderOverlay(
    imageModel: Any?,
    geometry: BoxArtGeometry,
    glassColorFilter: ColorFilter?,
    sweepOffset: Float
) {
    val combinedShape = GlassCombinedShape(
        outerCornerRadius = geometry.outerCornerRadiusPx,
        frameWidth = geometry.frameWidthPx,
        badgePosition = geometry.effectiveBadgePosition,
        badgeWidth = geometry.badgeWidthPx,
        badgeHeight = geometry.badgeHeightPx,
        badgeCornerRadius = geometry.scaledCornerRadiusPx,
        oneDpPx = geometry.oneDpPx
    )
    AsyncImage(
        model = imageModel,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        colorFilter = glassColorFilter,
        modifier = Modifier
            .fillMaxSize()
            .clip(combinedShape)
            .blur(GLASS_BLUR)
    )

    val innerEffectShape = InnerEffectShape(
        outerCornerRadius = geometry.outerCornerRadiusPx,
        frameWidth = geometry.frameWidthPx,
        effectWidth = geometry.innerEffectWidth,
        badgePosition = geometry.effectiveBadgePosition,
        badgeWidth = geometry.badgeWidthPx,
        badgeHeight = geometry.badgeHeightPx,
        badgeCornerRadius = geometry.scaledCornerRadiusPx,
        oneDpPx = geometry.oneDpPx
    )
    InnerEffect(geometry, innerEffectShape, imageModel, glassColorFilter, sweepOffset)
}

@Composable
internal fun GradientBorderOverlay(
    imageModel: Any?,
    gradientColors: Pair<Color, Color>,
    gradientBorderProgress: Float,
    geometry: BoxArtGeometry,
    sweepOffset: Float
) {
    val neutralColor = if (isSystemInDarkTheme()) {
        Color.Black.copy(alpha = 0.5f)
    } else {
        Color.White.copy(alpha = 0.5f)
    }
    val animatedPrimary = lerp(neutralColor, gradientColors.first, gradientBorderProgress)
    val animatedSecondary = lerp(neutralColor, gradientColors.second, gradientBorderProgress)
    val animatedFrameWidth = geometry.frameWidthPx * gradientBorderProgress

    val gradientMaskShape = GradientMaskShape(
        outerCornerRadius = geometry.outerCornerRadiusPx,
        frameWidth = animatedFrameWidth,
        isStub = false,
        badgePosition = geometry.effectiveBadgePosition,
        badgeWidth = geometry.badgeWidthPx,
        badgeHeight = geometry.badgeHeightPx,
        badgeCornerRadius = geometry.scaledCornerRadiusPx,
        oneDpPx = geometry.oneDpPx
    )

    val innerEffectShape = InnerEffectShape(
        outerCornerRadius = geometry.outerCornerRadiusPx,
        frameWidth = animatedFrameWidth,
        effectWidth = geometry.innerEffectWidth
    )
    InnerEffect(geometry, innerEffectShape, imageModel, glassColorFilter = null, sweepOffset)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(gradientMaskShape)
            .background(Brush.verticalGradient(listOf(animatedPrimary, animatedSecondary)))
    )
}

@Composable
private fun InnerEffect(
    geometry: BoxArtGeometry,
    innerEffectShape: InnerEffectShape,
    imageModel: Any?,
    glassColorFilter: ColorFilter?,
    sweepOffset: Float
) {
    when (geometry.innerEffect) {
        BoxArtInnerEffect.GLASS -> if (imageModel != null) {
            GlassInnerEffect(imageModel, glassColorFilter, innerEffectShape, geometry)
        }
        BoxArtInnerEffect.SHADOW -> StrokeInnerEffect(innerEffectShape, geometry, Color.Black, 0.5f)
        BoxArtInnerEffect.GLOW -> StrokeInnerEffect(innerEffectShape, geometry, Color.White, 0.4f)
        BoxArtInnerEffect.SHINE -> ShineInnerEffect(sweepOffset)
        BoxArtInnerEffect.OFF -> {}
    }
}

@Composable
private fun GlassInnerEffect(
    imageModel: Any?,
    glassColorFilter: ColorFilter?,
    innerEffectShape: InnerEffectShape,
    geometry: BoxArtGeometry
) {
    GLASS_RING_LAYERS.forEach { layer ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { this.alpha = layer.alpha }
                .clip(
                    GlassRingShape(
                        outerCornerRadius = geometry.outerCornerRadiusPx,
                        frameWidth = geometry.frameWidthPx,
                        innerEffectWidth = geometry.innerEffectWidth,
                        startProgress = layer.startProgress,
                        endProgress = layer.endProgress,
                        badgePosition = geometry.effectiveBadgePosition,
                        badgeWidth = geometry.badgeWidthPx,
                        badgeHeight = geometry.badgeHeightPx,
                        badgeCornerRadius = geometry.scaledCornerRadiusPx,
                        oneDpPx = geometry.oneDpPx
                    )
                )
        ) {
            AsyncImage(
                model = imageModel,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                colorFilter = glassColorFilter,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(layer.blur)
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(innerEffectShape)
            .drawBehind {
                val depthWidth = geometry.frameWidthPx * 1.5f
                val depthStrokeWidth = depthWidth / DEPTH_LAYERS
                for (i in 0 until DEPTH_LAYERS) {
                    val progress = i.toFloat() / DEPTH_LAYERS
                    val alpha = (0.35f * (1f - progress)).coerceIn(0f, 1f)
                    val layerInset = geometry.frameWidthPx + (depthWidth * progress) + depthStrokeWidth / 2
                    val layerRadius = (geometry.outerCornerRadiusPx - layerInset).coerceAtLeast(0f)
                    drawRoundRect(
                        color = Color.Black.copy(alpha = alpha),
                        topLeft = Offset(layerInset, layerInset),
                        size = Size(size.width - layerInset * 2, size.height - layerInset * 2),
                        cornerRadius = CornerRadius(layerRadius),
                        style = Stroke(width = depthStrokeWidth)
                    )
                }
            }
    )
}

@Composable
private fun StrokeInnerEffect(
    innerEffectShape: InnerEffectShape,
    geometry: BoxArtGeometry,
    color: Color,
    baseAlpha: Float
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(innerEffectShape)
            .drawBehind {
                val strokeWidth = geometry.innerEffectWidth / STROKE_LAYERS
                for (i in 0 until STROKE_LAYERS) {
                    val progress = i.toFloat() / STROKE_LAYERS
                    val alpha = (baseAlpha * (1f - progress)).coerceIn(0f, 1f)
                    val layerInset =
                        geometry.frameWidthPx + (geometry.innerEffectWidth * progress) + strokeWidth / 2
                    val layerRadius = (geometry.outerCornerRadiusPx - layerInset).coerceAtLeast(0f)
                    drawRoundRect(
                        color = color.copy(alpha = alpha),
                        topLeft = Offset(layerInset, layerInset),
                        size = Size(size.width - layerInset * 2, size.height - layerInset * 2),
                        cornerRadius = CornerRadius(layerRadius),
                        style = Stroke(width = strokeWidth)
                    )
                }
            }
    )
}

@Composable
private fun ShineInnerEffect(sweepOffset: Float) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .drawBehind {
                val diagonal = kotlin.math.sqrt(size.width * size.width + size.height * size.height)
                val sweepWidth = diagonal * 0.4f
                val progress = sweepOffset * (diagonal + sweepWidth) - sweepWidth
                val startX = progress * 0.85f
                val startY = progress * 0.5f - size.height * 0.2f
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.4f),
                            Color.Transparent
                        ),
                        start = Offset(startX, startY),
                        end = Offset(startX + sweepWidth * 0.85f, startY + sweepWidth * 0.5f)
                    )
                )
            }
    )
}

private val GLASS_BLUR = ComponentDefaults.BoxArtBorder.glassBlurDp.dp
private const val DEPTH_LAYERS = 6
private const val STROKE_LAYERS = 12

private data class GlassRingLayer(
    val blur: Dp,
    val startProgress: Float,
    val endProgress: Float,
    val alpha: Float
)

private val GLASS_RING_ALPHAS = listOf(1.0f, 1.0f, 1.0f, 1.0f, 0.85f, 0.65f, 0.45f, 0.3f)

private val GLASS_RING_LAYERS: List<GlassRingLayer> = listOf(
    0.00f to 0.24f,
    0.21f to 0.44f,
    0.41f to 0.60f,
    0.57f to 0.68f,
    0.65f to 0.76f,
    0.73f to 0.84f,
    0.81f to 0.92f,
    0.89f to 1.00f
).mapIndexed { index, range ->
    GlassRingLayer(
        blur = ComponentDefaults.BoxArtBorder.glassRingBlursDp[index].dp,
        startProgress = range.first,
        endProgress = range.second,
        alpha = GLASS_RING_ALPHAS[index]
    )
}
