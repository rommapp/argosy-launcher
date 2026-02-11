package com.nendo.argosy.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import java.io.File
import com.nendo.argosy.data.preferences.BoxArtBorderStyle
import com.nendo.argosy.data.preferences.BoxArtInnerEffect
import com.nendo.argosy.data.preferences.BoxArtOuterEffect
import com.nendo.argosy.data.preferences.GlowColorMode
import com.nendo.argosy.data.preferences.SystemIconPosition
import com.nendo.argosy.ui.screens.home.GameDownloadIndicator
import com.nendo.argosy.ui.screens.home.HomeGameUi
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalBoxArtStyle
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.ui.theme.Motion

@Composable
fun GameCard(
    game: HomeGameUi,
    isFocused: Boolean,
    modifier: Modifier = Modifier,
    focusScale: Float = Motion.scaleFocused,
    scaleFromBottom: Boolean = false,
    downloadIndicator: GameDownloadIndicator = GameDownloadIndicator.NONE,
    showPlatformBadge: Boolean = true,
    coverPathOverride: String? = null,
    onCoverLoadFailed: ((gameId: Long, failedPath: String) -> Unit)? = null,
    onCoverLoaded: ((gameId: Long, coverPath: String) -> Unit)? = null,
    scaleOverride: Float? = null,
    alphaOverride: Float? = null,
    saturationOverride: Float? = null
) {
    val themeConfig = LocalLauncherTheme.current
    val boxArtStyle = LocalBoxArtStyle.current
    val isDarkTheme = isSystemInDarkTheme()
    val effectiveCoverPath = coverPathOverride ?: game.coverPath
    val coverGradientColors = game.gradientColors

    val glowColorMode = boxArtStyle.glowColorMode
    val glowGradientColors: Pair<Color, Color>? = when (glowColorMode) {
        GlowColorMode.AUTO -> {
            if (boxArtStyle.borderStyle == BoxArtBorderStyle.GRADIENT && coverGradientColors != null) {
                coverGradientColors
            } else null
        }
        GlowColorMode.ACCENT -> null
        GlowColorMode.ACCENT_GRADIENT -> {
            val accent = boxArtStyle.accentColor
            val secondary = boxArtStyle.secondaryColor
            if (accent != null && secondary != null) Pair(accent, secondary) else null
        }
        GlowColorMode.COVER -> coverGradientColors
    }

    val scale by animateFloatAsState(
        targetValue = scaleOverride ?: if (isFocused) focusScale else Motion.scaleDefault,
        animationSpec = Motion.focusSpring,
        label = "scale"
    )

    val alpha by animateFloatAsState(
        targetValue = alphaOverride ?: if (isFocused) Motion.alphaFocused else Motion.alphaUnfocused,
        animationSpec = Motion.focusSpring,
        label = "alpha"
    )

    val saturation by animateFloatAsState(
        targetValue = saturationOverride ?: 1f,
        animationSpec = Motion.focusSpring,
        label = "saturation"
    )
    val saturationColorFilter = if (saturation < 1f) {
        ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(saturation) })
    } else null

    val outerEffect = boxArtStyle.outerEffect
    val outerEffectRadius = boxArtStyle.outerEffectThicknessPx
    val showOuterEffect = isFocused && outerEffect != BoxArtOuterEffect.OFF

    val glowColor = boxArtStyle.accentColor ?: themeConfig.focusGlowColor
    val borderColor = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(boxArtStyle.cornerRadiusDp)

    val outerShineTransition = if (outerEffect == BoxArtOuterEffect.SHINE && isFocused) {
        rememberInfiniteTransition(label = "outerShine")
    } else null
    val outerShineOffset by outerShineTransition?.animateFloat(
        initialValue = -0.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "outerShine"
    ) ?: remember { mutableStateOf(0f) }

    BoxWithConstraints(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = if (scaleFromBottom) TransformOrigin(0.5f, 1f) else TransformOrigin.Center
                this.alpha = alpha
                this.clip = false
            }
            .then(
                if (showOuterEffect) {
                    Modifier.drawBehind {
                        drawIntoCanvas { canvas ->
                            val cornerRadius = boxArtStyle.cornerRadiusDp.toPx()
                            val spread = outerEffectRadius
                            when (outerEffect) {
                                BoxArtOuterEffect.GLOW -> {
                                    val glowAlpha = boxArtStyle.glowAlpha
                                    val frameworkPaint = android.graphics.Paint().apply {
                                        maskFilter = android.graphics.BlurMaskFilter(
                                            outerEffectRadius,
                                            android.graphics.BlurMaskFilter.Blur.NORMAL
                                        )
                                        if (glowGradientColors != null) {
                                            shader = android.graphics.LinearGradient(
                                                0f, 0f,
                                                0f, size.height,
                                                glowGradientColors.first.copy(alpha = glowAlpha).toArgb(),
                                                glowGradientColors.second.copy(alpha = glowAlpha).toArgb(),
                                                android.graphics.Shader.TileMode.CLAMP
                                            )
                                        } else {
                                            color = glowColor.copy(alpha = glowAlpha).toArgb()
                                        }
                                    }
                                    canvas.nativeCanvas.drawRoundRect(
                                        -spread, -spread,
                                        size.width + spread, size.height + spread,
                                        cornerRadius + spread, cornerRadius + spread,
                                        frameworkPaint
                                    )
                                }
                                BoxArtOuterEffect.SHADOW -> {
                                    val paint = Paint().apply {
                                        color = Color.Black.copy(alpha = 0.3f)
                                    }
                                    val frameworkPaint = paint.asFrameworkPaint().apply {
                                        maskFilter = android.graphics.BlurMaskFilter(
                                            outerEffectRadius,
                                            android.graphics.BlurMaskFilter.Blur.NORMAL
                                        )
                                    }
                                    canvas.nativeCanvas.drawRoundRect(
                                        -spread, -spread,
                                        size.width + spread, size.height + spread,
                                        cornerRadius + spread, cornerRadius + spread,
                                        frameworkPaint
                                    )
                                }
                                BoxArtOuterEffect.SHINE -> {
                                    val shineWidth = size.width * 0.4f
                                    val shineX = outerShineOffset * (size.width + shineWidth) - shineWidth
                                    val paint = android.graphics.Paint().apply {
                                        maskFilter = android.graphics.BlurMaskFilter(
                                            outerEffectRadius / 2,
                                            android.graphics.BlurMaskFilter.Blur.NORMAL
                                        )
                                        shader = android.graphics.LinearGradient(
                                            shineX, 0f,
                                            shineX + shineWidth, size.height,
                                            intArrayOf(
                                                android.graphics.Color.TRANSPARENT,
                                                android.graphics.Color.argb(150, 255, 255, 255),
                                                android.graphics.Color.TRANSPARENT
                                            ),
                                            floatArrayOf(0f, 0.5f, 1f),
                                            android.graphics.Shader.TileMode.CLAMP
                                        )
                                    }
                                    canvas.nativeCanvas.drawRoundRect(
                                        -spread, -spread,
                                        size.width + spread, size.height + spread,
                                        cornerRadius + spread, cornerRadius + spread,
                                        paint
                                    )
                                }
                                BoxArtOuterEffect.OFF -> {}
                            }
                        }
                    }
                } else Modifier
            )
            .then(
                if (isFocused && boxArtStyle.borderThicknessDp.value > 0f && boxArtStyle.borderStyle == BoxArtBorderStyle.SOLID) {
                    Modifier.border(boxArtStyle.borderThicknessDp, borderColor, shape)
                } else Modifier
            )
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        val density = LocalDensity.current
        val outerCornerRadiusPx = with(density) { boxArtStyle.cornerRadiusDp.toPx() }
        val frameWidthPx = with(density) { boxArtStyle.borderThicknessDp.toPx() }
        val oneDpPx = with(density) { 1.dp.toPx() }
        val useGlassBorder = isFocused && boxArtStyle.borderStyle == BoxArtBorderStyle.GLASS
        val useGradientBorder = isFocused && boxArtStyle.borderStyle == BoxArtBorderStyle.GRADIENT
        val isStub = effectiveCoverPath == null

        val gradientColors = game.gradientColors
        val hasGradientColors = gradientColors != null
        val gradientBorderProgress = if (hasGradientColors && useGradientBorder) 1f else 0f

        val cardWidthDp = this@BoxWithConstraints.maxWidth
        val baseWidthDp = 150.dp
        val baseFontSizeSp = 11f
        val baseHorizontalPaddingDp = 4.dp
        val baseVerticalPaddingDp = 2.dp
        val badgeScale = (cardWidthDp / baseWidthDp).coerceIn(0.5f, 2f)
        val scaledCornerRadius = boxArtStyle.cornerRadiusDp * badgeScale
        val userPadding = boxArtStyle.systemIconPaddingDp
        val borderPadding = boxArtStyle.borderThicknessDp * 1.5f
        val horizontalPadding = (baseHorizontalPaddingDp + userPadding + borderPadding) * badgeScale
        val verticalPadding = (baseVerticalPaddingDp + userPadding / 2 + borderPadding / 2) * badgeScale

        val displayName = if (showPlatformBadge) game.platformSlug.take(8) else ""
        val fontSizePx = with(density) { (baseFontSizeSp * badgeScale).dp.toPx() }
        val estimatedTextWidthPx = displayName.length * fontSizePx * 0.7f
        val badgeWidthPx = with(density) { estimatedTextWidthPx + horizontalPadding.toPx() * 2 }
        val badgeHeightPx = with(density) { fontSizePx + verticalPadding.toPx() * 2 }
        val scaledCornerRadiusPx = with(density) { scaledCornerRadius.toPx() }

        val innerEffect = boxArtStyle.innerEffect
        val innerEffectWidth = boxArtStyle.innerEffectThicknessPx
        val innerRadius = (outerCornerRadiusPx - frameWidthPx).coerceAtLeast(0f)

        val shineTransition = if (innerEffect == BoxArtInnerEffect.SHINE && (useGlassBorder || useGradientBorder)) {
            rememberInfiniteTransition(label = "innerShine")
        } else null
        val sweepOffset by shineTransition?.animateFloat(
            initialValue = -0.5f,
            targetValue = 1.5f,
            animationSpec = infiniteRepeatable(
                animation = tween(2000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "shine"
        ) ?: remember { mutableStateOf(0f) }

        if (effectiveCoverPath != null) {
            val imageData = if (effectiveCoverPath.startsWith("/")) {
                File(effectiveCoverPath)
            } else {
                effectiveCoverPath
            }

            AsyncImage(
                model = imageData,
                contentDescription = game.title,
                contentScale = ContentScale.Crop,
                colorFilter = saturationColorFilter,
                modifier = Modifier.fillMaxSize(),
                onSuccess = {
                    onCoverLoaded?.invoke(game.id, effectiveCoverPath)
                },
                onError = {
                    if (onCoverLoadFailed != null && effectiveCoverPath.startsWith("/")) {
                        onCoverLoadFailed(game.id, effectiveCoverPath)
                    }
                }
            )

            if (useGlassBorder) {
                val glassTintAlpha = boxArtStyle.glassBorderTintAlpha
                val glassColorFilter = if (hasGradientColors) {
                    val tintColor = lerp(Color.White, gradientColors!!.first, (glassTintAlpha * 2).coerceIn(0f, 1f))
                    ColorFilter.lighting(
                        multiply = tintColor,
                        add = Color.Black
                    )
                } else if (glassTintAlpha > 0f) {
                    val tintColor = lerp(Color.White, borderColor, glassTintAlpha)
                    ColorFilter.lighting(
                        multiply = tintColor,
                        add = Color.Black
                    )
                } else {
                    null
                }
                val includeBadge = showPlatformBadge && boxArtStyle.systemIconPosition != SystemIconPosition.OFF
                val combinedShape = GlassCombinedShape(
                    outerCornerRadius = outerCornerRadiusPx,
                    frameWidth = frameWidthPx,
                    badgePosition = if (includeBadge) boxArtStyle.systemIconPosition else SystemIconPosition.OFF,
                    badgeWidth = badgeWidthPx,
                    badgeHeight = badgeHeightPx,
                    badgeCornerRadius = scaledCornerRadiusPx,
                    oneDpPx = oneDpPx
                )
                AsyncImage(
                    model = imageData,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    colorFilter = glassColorFilter,
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(combinedShape)
                        .blur(8.dp)
                )

                val innerEffectShape = InnerEffectShape(
                    outerCornerRadius = outerCornerRadiusPx,
                    frameWidth = frameWidthPx,
                    effectWidth = innerEffectWidth,
                    badgePosition = if (includeBadge) boxArtStyle.systemIconPosition else SystemIconPosition.OFF,
                    badgeWidth = badgeWidthPx,
                    badgeHeight = badgeHeightPx,
                    badgeCornerRadius = scaledCornerRadiusPx,
                    oneDpPx = oneDpPx
                )
                when (innerEffect) {
                    BoxArtInnerEffect.GLASS -> {
                        val glassLayers = listOf(
                            Triple(24.dp, 0.00f to 0.24f, 1.0f),
                            Triple(12.dp, 0.21f to 0.44f, 1.0f),
                            Triple(6.dp, 0.41f to 0.60f, 1.0f),
                            Triple(3.dp, 0.57f to 0.68f, 1.0f),
                            Triple(1.5.dp, 0.65f to 0.76f, 0.85f),
                            Triple(0.8.dp, 0.73f to 0.84f, 0.65f),
                            Triple(0.4.dp, 0.81f to 0.92f, 0.45f),
                            Triple(0.1.dp, 0.89f to 1.00f, 0.3f)
                        )

                        glassLayers.forEach { (blurAmount, range, alpha) ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer { this.alpha = alpha }
                                    .clip(
                                        GlassRingShape(
                                            outerCornerRadius = outerCornerRadiusPx,
                                            frameWidth = frameWidthPx,
                                            innerEffectWidth = innerEffectWidth,
                                            startProgress = range.first,
                                            endProgress = range.second,
                                            badgePosition = if (includeBadge) boxArtStyle.systemIconPosition else SystemIconPosition.OFF,
                                            badgeWidth = badgeWidthPx,
                                            badgeHeight = badgeHeightPx,
                                            badgeCornerRadius = scaledCornerRadiusPx,
                                            oneDpPx = oneDpPx
                                        )
                                    )
                            ) {
                                AsyncImage(
                                    model = imageData,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    colorFilter = glassColorFilter,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .blur(blurAmount)
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(innerEffectShape)
                                .drawBehind {
                                    val depthWidth = frameWidthPx * 1.5f
                                    val depthLayers = 6
                                    val depthStrokeWidth = depthWidth / depthLayers
                                    for (i in 0 until depthLayers) {
                                        val progress = i.toFloat() / depthLayers
                                        val alpha = (0.35f * (1f - progress)).coerceIn(0f, 1f)
                                        val layerInset = frameWidthPx + (depthWidth * progress) + depthStrokeWidth / 2
                                        val layerRadius = (outerCornerRadiusPx - layerInset).coerceAtLeast(0f)
                                        drawRoundRect(
                                            color = Color.Black.copy(alpha = alpha),
                                            topLeft = Offset(layerInset, layerInset),
                                            size = Size(size.width - layerInset * 2, size.height - layerInset * 2),
                                            cornerRadius = CornerRadius(layerRadius),
                                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = depthStrokeWidth)
                                        )
                                    }
                                }
                        )
                    }
                    BoxArtInnerEffect.SHADOW -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(innerEffectShape)
                                .drawBehind {
                                    val layers = 12
                                    val strokeWidth = innerEffectWidth / layers
                                    for (i in 0 until layers) {
                                        val progress = i.toFloat() / layers
                                        val alpha = (0.5f * (1f - progress)).coerceIn(0f, 1f)
                                        val layerInset = frameWidthPx + (innerEffectWidth * progress) + strokeWidth / 2
                                        val layerRadius = (outerCornerRadiusPx - layerInset).coerceAtLeast(0f)
                                        drawRoundRect(
                                            color = Color.Black.copy(alpha = alpha),
                                            topLeft = Offset(layerInset, layerInset),
                                            size = Size(size.width - layerInset * 2, size.height - layerInset * 2),
                                            cornerRadius = CornerRadius(layerRadius),
                                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
                                        )
                                    }
                                }
                        )
                    }
                    BoxArtInnerEffect.GLOW -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(innerEffectShape)
                                .drawBehind {
                                    val layers = 12
                                    val strokeWidth = innerEffectWidth / layers
                                    for (i in 0 until layers) {
                                        val progress = i.toFloat() / layers
                                        val alpha = (0.4f * (1f - progress)).coerceIn(0f, 1f)
                                        val layerInset = frameWidthPx + (innerEffectWidth * progress) + strokeWidth / 2
                                        val layerRadius = (outerCornerRadiusPx - layerInset).coerceAtLeast(0f)
                                        drawRoundRect(
                                            color = Color.White.copy(alpha = alpha),
                                            topLeft = Offset(layerInset, layerInset),
                                            size = Size(size.width - layerInset * 2, size.height - layerInset * 2),
                                            cornerRadius = CornerRadius(layerRadius),
                                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
                                        )
                                    }
                                }
                        )
                    }
                    BoxArtInnerEffect.SHINE -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .drawBehind {
                                    val diagonal = kotlin.math.sqrt(size.width * size.width + size.height * size.height)
                                    val sweepWidth = diagonal * 0.4f
                                    val progress = sweepOffset * (diagonal + sweepWidth) - sweepWidth
                                    val startX = progress * 0.85f
                                    val startY = progress * 0.5f - size.height * 0.2f
                                    val endX = startX + sweepWidth * 0.85f
                                    val endY = startY + sweepWidth * 0.5f
                                    drawRect(
                                        brush = Brush.linearGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                Color.White.copy(alpha = 0.4f),
                                                Color.Transparent
                                            ),
                                            start = Offset(startX, startY),
                                            end = Offset(endX, endY)
                                        )
                                    )
                                }
                        )
                    }
                    BoxArtInnerEffect.OFF -> {}
                }
            }
        } else {
            val useSolidStub = boxArtStyle.borderStyle == BoxArtBorderStyle.SOLID
            val stubBackground = if (useSolidStub) {
                MaterialTheme.colorScheme.primaryContainer
            } else {
                Color.Black.copy(alpha = 0.6f)
            }
            val stubTextColor = if (useSolidStub) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                Color.White.copy(alpha = 0.9f)
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(stubBackground),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = game.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = stubTextColor,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(Dimens.spacingSm)
                )
            }
        }

        if (gradientBorderProgress > 0f && gradientColors != null) {
            val isDark = isSystemInDarkTheme()
            val neutralColor = if (isDark) Color.Black.copy(alpha = 0.5f) else Color.White.copy(alpha = 0.5f)
            val animatedPrimary = lerp(neutralColor, gradientColors.first, gradientBorderProgress)
            val animatedSecondary = lerp(neutralColor, gradientColors.second, gradientBorderProgress)
            val animatedFrameWidth = frameWidthPx * gradientBorderProgress

            val badgePosition = if (showPlatformBadge) boxArtStyle.systemIconPosition else SystemIconPosition.OFF
            val gradientMaskShape = GradientMaskShape(
                outerCornerRadius = outerCornerRadiusPx,
                frameWidth = animatedFrameWidth,
                isStub = false,
                badgePosition = badgePosition,
                badgeWidth = badgeWidthPx,
                badgeHeight = badgeHeightPx,
                badgeCornerRadius = scaledCornerRadiusPx,
                oneDpPx = oneDpPx
            )

            val innerEffectShape = InnerEffectShape(outerCornerRadiusPx, animatedFrameWidth, innerEffectWidth)
            val gradientImageData = effectiveCoverPath?.let { path ->
                if (path.startsWith("/")) File(path) else path
            }
            when (innerEffect) {
                BoxArtInnerEffect.GLASS -> {
                    if (gradientImageData != null) {
                        val glassLayers = listOf(
                            Triple(24.dp, 0.00f to 0.24f, 1.0f),
                            Triple(12.dp, 0.21f to 0.44f, 1.0f),
                            Triple(6.dp, 0.41f to 0.60f, 1.0f),
                            Triple(3.dp, 0.57f to 0.68f, 1.0f),
                            Triple(1.5.dp, 0.65f to 0.76f, 0.85f),
                            Triple(0.8.dp, 0.73f to 0.84f, 0.65f),
                            Triple(0.4.dp, 0.81f to 0.92f, 0.45f),
                            Triple(0.1.dp, 0.89f to 1.00f, 0.3f)
                        )

                        glassLayers.forEach { (blurAmount, range, alpha) ->
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer { this.alpha = alpha }
                                    .clip(
                                        GlassRingShape(
                                            outerCornerRadius = outerCornerRadiusPx,
                                            frameWidth = frameWidthPx,
                                            innerEffectWidth = innerEffectWidth,
                                            startProgress = range.first,
                                            endProgress = range.second,
                                            badgePosition = badgePosition,
                                            badgeWidth = badgeWidthPx,
                                            badgeHeight = badgeHeightPx,
                                            badgeCornerRadius = scaledCornerRadiusPx,
                                            oneDpPx = oneDpPx
                                        )
                                    )
                            ) {
                                AsyncImage(
                                    model = gradientImageData,
                                    contentDescription = null,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .blur(blurAmount)
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(innerEffectShape)
                                .drawBehind {
                                    val depthWidth = frameWidthPx * 1.5f
                                    val depthLayers = 6
                                    val depthStrokeWidth = depthWidth / depthLayers
                                    for (i in 0 until depthLayers) {
                                        val progress = i.toFloat() / depthLayers
                                        val alpha = (0.35f * (1f - progress)).coerceIn(0f, 1f)
                                        val layerInset = frameWidthPx + (depthWidth * progress) + depthStrokeWidth / 2
                                        val layerRadius = (outerCornerRadiusPx - layerInset).coerceAtLeast(0f)
                                        drawRoundRect(
                                            color = Color.Black.copy(alpha = alpha),
                                            topLeft = Offset(layerInset, layerInset),
                                            size = Size(size.width - layerInset * 2, size.height - layerInset * 2),
                                            cornerRadius = CornerRadius(layerRadius),
                                            style = androidx.compose.ui.graphics.drawscope.Stroke(width = depthStrokeWidth)
                                        )
                                    }
                                }
                        )
                    }
                }
                BoxArtInnerEffect.SHADOW -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(innerEffectShape)
                            .drawBehind {
                                val layers = 12
                                val strokeWidth = innerEffectWidth / layers
                                for (i in 0 until layers) {
                                    val progress = i.toFloat() / layers
                                    val alpha = (0.5f * (1f - progress)).coerceIn(0f, 1f)
                                    val layerInset = frameWidthPx + (innerEffectWidth * progress) + strokeWidth / 2
                                    val layerRadius = (outerCornerRadiusPx - layerInset).coerceAtLeast(0f)
                                    drawRoundRect(
                                        color = Color.Black.copy(alpha = alpha),
                                        topLeft = Offset(layerInset, layerInset),
                                        size = Size(size.width - layerInset * 2, size.height - layerInset * 2),
                                        cornerRadius = CornerRadius(layerRadius),
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
                                    )
                                }
                            }
                    )
                }
                BoxArtInnerEffect.GLOW -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(innerEffectShape)
                            .drawBehind {
                                val layers = 12
                                val strokeWidth = innerEffectWidth / layers
                                for (i in 0 until layers) {
                                    val progress = i.toFloat() / layers
                                    val alpha = (0.4f * (1f - progress)).coerceIn(0f, 1f)
                                    val layerInset = frameWidthPx + (innerEffectWidth * progress) + strokeWidth / 2
                                    val layerRadius = (outerCornerRadiusPx - layerInset).coerceAtLeast(0f)
                                    drawRoundRect(
                                        color = Color.White.copy(alpha = alpha),
                                        topLeft = Offset(layerInset, layerInset),
                                        size = Size(size.width - layerInset * 2, size.height - layerInset * 2),
                                        cornerRadius = CornerRadius(layerRadius),
                                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
                                    )
                                }
                            }
                    )
                }
                BoxArtInnerEffect.SHINE -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .drawBehind {
                                val diagonal = kotlin.math.sqrt(size.width * size.width + size.height * size.height)
                                val sweepWidth = diagonal * 0.4f
                                val progress = sweepOffset * (diagonal + sweepWidth) - sweepWidth
                                val startX = progress * 0.85f
                                val startY = progress * 0.5f - size.height * 0.2f
                                val endX = startX + sweepWidth * 0.85f
                                val endY = startY + sweepWidth * 0.5f
                                drawRect(
                                    brush = Brush.linearGradient(
                                        colors = listOf(
                                            Color.Transparent,
                                            Color.White.copy(alpha = 0.4f),
                                            Color.Transparent
                                        ),
                                        start = Offset(startX, startY),
                                        end = Offset(endX, endY)
                                    )
                                )
                            }
                    )
                }
                BoxArtInnerEffect.OFF -> {}
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(gradientMaskShape)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(animatedPrimary, animatedSecondary)
                        )
                    )
            )
        }

        if (showPlatformBadge && boxArtStyle.systemIconPosition != SystemIconPosition.OFF) {
            val badgeAlignment = when (boxArtStyle.systemIconPosition) {
                SystemIconPosition.TOP_LEFT -> Alignment.TopStart
                SystemIconPosition.TOP_RIGHT -> Alignment.TopEnd
                else -> Alignment.TopStart
            }

            PlatformBadge(
                platformDisplayName = game.platformSlug,
                cardWidthDp = maxWidth,
                isFocused = isFocused,
                modifier = Modifier.align(badgeAlignment)
            )
        }

        if (downloadIndicator.isActive) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(Dimens.spacingSm)
                    .size(Dimens.iconSm + Dimens.spacingXs)
                    .background(
                        MaterialTheme.colorScheme.secondary.copy(alpha = 0.9f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Downloading",
                    tint = Color.White,
                    modifier = Modifier.size(Dimens.iconXs)
                )
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
        ) {
            if (game.isFavorite || game.isDownloaded) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = Dimens.spacingSm)
                        .padding(bottom = Dimens.spacingXs),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (game.isFavorite) {
                        Box(
                            modifier = Modifier
                                .size(Dimens.iconSm + Dimens.borderMedium)
                                .background(Color.Black.copy(alpha = 0.35f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Favorite,
                                contentDescription = "Favorite",
                                tint = Color.White,
                                modifier = Modifier.size(Dimens.iconXs)
                            )
                        }
                    } else {
                        Box(modifier = Modifier.size(Dimens.iconSm + Dimens.borderMedium))
                    }

                    if (game.isDownloaded) {
                        Box(
                            modifier = Modifier
                                .size(Dimens.iconSm + Dimens.borderMedium)
                                .background(Color.Black.copy(alpha = 0.35f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.CheckCircle,
                                contentDescription = "Downloaded",
                                tint = Color.White,
                                modifier = Modifier.size(Dimens.iconXs)
                            )
                        }
                    } else {
                        Box(modifier = Modifier.size(Dimens.iconSm + Dimens.borderMedium))
                    }
                }
            }

            if (downloadIndicator.isActive) {
                val secondaryColor = MaterialTheme.colorScheme.secondary
                val progressColor = if (downloadIndicator.isPaused || downloadIndicator.isQueued) {
                    secondaryColor.copy(alpha = 0.5f)
                } else {
                    secondaryColor
                }
                val progressBarHeight = Dimens.spacingSm - Dimens.borderMedium

                val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
                val shimmerOffset by infiniteTransition.animateFloat(
                    initialValue = -0.3f,
                    targetValue = 1.8f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1300, easing = LinearEasing),
                        repeatMode = RepeatMode.Restart
                    ),
                    label = "shimmer"
                )

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(progressBarHeight)
                        .background(Color.Gray.copy(alpha = 0.6f))
                        .then(
                            if (downloadIndicator.isDownloading) {
                                Modifier.drawBehind {
                                    val shimmerWidth = size.width * 0.25f
                                    val shimmerX = shimmerOffset * size.width
                                    drawRect(
                                        brush = androidx.compose.ui.graphics.Brush.horizontalGradient(
                                            colors = listOf(
                                                Color.Transparent,
                                                Color.White.copy(alpha = 0.4f),
                                                Color.Transparent
                                            ),
                                            startX = shimmerX - shimmerWidth / 2,
                                            endX = shimmerX + shimmerWidth / 2
                                        )
                                    )
                                }
                            } else Modifier
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(downloadIndicator.progress.coerceIn(0f, 1f))
                            .height(progressBarHeight)
                            .background(progressColor)
                    )
                }
            }
        }
    }
}

@Composable
fun GameCardWithBadge(
    game: HomeGameUi,
    isFocused: Boolean,
    modifier: Modifier = Modifier,
    badge: @Composable (() -> Unit)? = null
) {
    Box(modifier = modifier) {
        GameCard(
            game = game,
            isFocused = isFocused,
            modifier = Modifier.fillMaxSize()
        )

        if (badge != null) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(Dimens.spacingSm)
            ) {
                badge()
            }
        }
    }
}

@Composable
fun GameCardWithNewBadge(
    game: HomeGameUi,
    isFocused: Boolean,
    cardWidth: Dp,
    cardHeight: Dp,
    modifier: Modifier = Modifier,
    focusScale: Float = Motion.scaleFocused,
    scaleFromBottom: Boolean = false,
    downloadIndicator: GameDownloadIndicator = GameDownloadIndicator.NONE,
    showPlatformBadge: Boolean = true,
    coverPathOverride: String? = null,
    onCoverLoadFailed: ((gameId: Long, failedPath: String) -> Unit)? = null,
    onCoverLoaded: ((gameId: Long, coverPath: String) -> Unit)? = null,
    scaleOverride: Float? = null,
    alphaOverride: Float? = null
) {
    val showNewBadge = game.isNew && !downloadIndicator.isActive
    val badgeWidthDp = 44.dp
    val badgeHeightDp = 30.dp
    val badgeOffsetXDp = 8.dp
    val badgeTopOverflow = 20.dp

    val scale by animateFloatAsState(
        targetValue = scaleOverride ?: if (isFocused) focusScale else Motion.scaleDefault,
        animationSpec = Motion.focusSpring,
        label = "wrapperScale"
    )

    val alpha by animateFloatAsState(
        targetValue = alphaOverride ?: if (isFocused) Motion.alphaFocused else Motion.alphaUnfocused,
        animationSpec = Motion.focusSpring,
        label = "wrapperAlpha"
    )

    Layout(
        content = {
            GameCard(
                game = game,
                isFocused = isFocused,
                focusScale = 1f,
                scaleFromBottom = scaleFromBottom,
                downloadIndicator = downloadIndicator,
                showPlatformBadge = showPlatformBadge,
                coverPathOverride = coverPathOverride,
                onCoverLoadFailed = onCoverLoadFailed,
                onCoverLoaded = onCoverLoaded,
                scaleOverride = 1f,
                alphaOverride = 1f,
                modifier = Modifier
            )
            if (showNewBadge) {
                NewBadge(
                    width = badgeWidthDp,
                    height = badgeHeightDp,
                    rotation = 15f
                )
            }
        },
        modifier = modifier.graphicsLayer {
            scaleX = scale
            scaleY = scale
            transformOrigin = if (scaleFromBottom) TransformOrigin(0.5f, 1f) else TransformOrigin.Center
            this.alpha = alpha
        }
    ) { measurables, constraints ->
        val cardWidthPx = cardWidth.toPx().toInt()
        val cardHeightPx = cardHeight.toPx().toInt()
        val cardConstraints = Constraints.fixed(cardWidthPx, cardHeightPx)

        val cardPlaceable = measurables[0].measure(cardConstraints)
        val badgePlaceable = if (showNewBadge && measurables.size > 1) {
            measurables[1].measure(Constraints())
        } else null

        val badgeOffsetX = badgeOffsetXDp.roundToPx()
        val topOverflow = if (showNewBadge) badgeTopOverflow.roundToPx() else 0
        val rightOverflow = if (showNewBadge) badgeOffsetX else 0

        val layoutWidth = cardPlaceable.width + rightOverflow
        val layoutHeight = cardPlaceable.height + topOverflow

        layout(layoutWidth, layoutHeight) {
            cardPlaceable.placeRelative(0, topOverflow)
            badgePlaceable?.placeRelative(
                x = cardPlaceable.width - badgePlaceable.width + badgeOffsetX,
                y = topOverflow - (badgePlaceable.height / 2)
            )
        }
    }
}

@Composable
fun SourceBadge(
    isLocal: Boolean,
    isSynced: Boolean
) {
    val (text, color) = when {
        isLocal && !isSynced -> "F" to LocalLauncherTheme.current.semanticColors.warning
        isSynced -> "C" to MaterialTheme.colorScheme.primary
        else -> return
    }

    Box(
        modifier = Modifier
            .background(
                color = color,
                shape = RoundedCornerShape(Dimens.radiusSm)
            )
            .padding(horizontal = Dimens.spacingSm - Dimens.borderMedium, vertical = Dimens.borderMedium)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White
        )
    }
}

private class GlassFrameShape(
    private val outerCornerRadius: Float,
    private val frameWidth: Float
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val outerPath = Path().apply {
            addRoundRect(
                RoundRect(
                    left = 0f,
                    top = 0f,
                    right = size.width,
                    bottom = size.height,
                    cornerRadius = CornerRadius(outerCornerRadius)
                )
            )
        }
        val innerRadius = (outerCornerRadius - frameWidth).coerceAtLeast(0f)
        val innerPath = Path().apply {
            addRoundRect(
                RoundRect(
                    left = frameWidth,
                    top = frameWidth,
                    right = size.width - frameWidth,
                    bottom = size.height - frameWidth,
                    cornerRadius = CornerRadius(innerRadius)
                )
            )
        }
        val framePath = Path().apply {
            op(outerPath, innerPath, PathOperation.Difference)
        }
        return Outline.Generic(framePath)
    }
}

private class GlassCombinedShape(
    private val outerCornerRadius: Float,
    private val frameWidth: Float,
    private val badgePosition: SystemIconPosition,
    private val badgeWidth: Float,
    private val badgeHeight: Float,
    private val badgeCornerRadius: Float,
    private val oneDpPx: Float
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val outerPath = Path().apply {
            addRoundRect(
                RoundRect(0f, 0f, size.width, size.height, CornerRadius(outerCornerRadius))
            )
        }
        val innerRadius = (outerCornerRadius - frameWidth).coerceAtLeast(0f)
        val innerPath = Path().apply {
            addRoundRect(
                RoundRect(
                    frameWidth, frameWidth,
                    size.width - frameWidth, size.height - frameWidth,
                    CornerRadius(innerRadius)
                )
            )
        }
        val framePath = Path().apply {
            op(outerPath, innerPath, PathOperation.Difference)
        }

        if (badgePosition == SystemIconPosition.OFF) {
            return Outline.Generic(framePath)
        }

        val badgePath = createBadgePath(size)
        val combinedPath = Path().apply {
            op(framePath, badgePath, PathOperation.Union)
        }
        return Outline.Generic(combinedPath)
    }

    private fun createBadgePath(size: Size): Path {
        val path = Path()
        when (badgePosition) {
            SystemIconPosition.TOP_LEFT -> {
                path.addRoundRect(
                    RoundRect(
                        left = 0f,
                        top = 0f,
                        right = badgeWidth,
                        bottom = badgeHeight,
                        topLeftCornerRadius = CornerRadius(badgeCornerRadius),
                        bottomRightCornerRadius = CornerRadius(badgeCornerRadius)
                    )
                )
                val rightEarX = badgeWidth - oneDpPx
                val rightEarY = frameWidth - oneDpPx
                val rightEar = Path().apply {
                    moveTo(rightEarX, rightEarY)
                    lineTo(rightEarX + badgeCornerRadius, rightEarY)
                    arcTo(
                        Rect(
                            rightEarX,
                            rightEarY,
                            rightEarX + badgeCornerRadius * 2,
                            rightEarY + badgeCornerRadius * 2
                        ),
                        270f, -90f, false
                    )
                    close()
                }
                path.op(path, rightEar, PathOperation.Union)
                val bottomEarX = frameWidth - oneDpPx
                val bottomEarY = badgeHeight - oneDpPx
                val bottomEar = Path().apply {
                    moveTo(bottomEarX, bottomEarY)
                    lineTo(bottomEarX + badgeCornerRadius, bottomEarY)
                    arcTo(
                        Rect(
                            bottomEarX,
                            bottomEarY,
                            bottomEarX + badgeCornerRadius * 2,
                            bottomEarY + badgeCornerRadius * 2
                        ),
                        270f, -90f, false
                    )
                    close()
                }
                path.op(path, bottomEar, PathOperation.Union)
            }
            SystemIconPosition.TOP_RIGHT -> {
                val badgeLeft = size.width - badgeWidth
                path.addRoundRect(
                    RoundRect(
                        left = badgeLeft,
                        top = 0f,
                        right = size.width,
                        bottom = badgeHeight,
                        topRightCornerRadius = CornerRadius(badgeCornerRadius),
                        bottomLeftCornerRadius = CornerRadius(badgeCornerRadius)
                    )
                )
                val leftEarX = badgeLeft + oneDpPx
                val leftEarY = frameWidth - oneDpPx
                val leftEar = Path().apply {
                    moveTo(leftEarX, leftEarY)
                    lineTo(leftEarX - badgeCornerRadius, leftEarY)
                    arcTo(
                        Rect(
                            leftEarX - badgeCornerRadius * 2,
                            leftEarY,
                            leftEarX,
                            leftEarY + badgeCornerRadius * 2
                        ),
                        270f, 90f, false
                    )
                    close()
                }
                path.op(path, leftEar, PathOperation.Union)
                val bottomEarX = size.width - frameWidth + oneDpPx
                val bottomEarY = badgeHeight - oneDpPx
                val bottomEar = Path().apply {
                    moveTo(bottomEarX, bottomEarY)
                    lineTo(bottomEarX - badgeCornerRadius, bottomEarY)
                    arcTo(
                        Rect(
                            bottomEarX - badgeCornerRadius * 2,
                            bottomEarY,
                            bottomEarX,
                            bottomEarY + badgeCornerRadius * 2
                        ),
                        270f, 90f, false
                    )
                    close()
                }
                path.op(path, bottomEar, PathOperation.Union)
            }
            else -> {}
        }
        return path
    }
}

private class InnerEffectShape(
    private val outerCornerRadius: Float,
    private val frameWidth: Float,
    private val effectWidth: Float,
    private val badgePosition: SystemIconPosition = SystemIconPosition.OFF,
    private val badgeWidth: Float = 0f,
    private val badgeHeight: Float = 0f,
    private val badgeCornerRadius: Float = 0f,
    private val oneDpPx: Float = 0f
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val outerRadius = (outerCornerRadius - frameWidth).coerceAtLeast(0f)
        val outerPath = Path().apply {
            addRoundRect(
                RoundRect(
                    left = frameWidth,
                    top = frameWidth,
                    right = size.width - frameWidth,
                    bottom = size.height - frameWidth,
                    cornerRadius = CornerRadius(outerRadius)
                )
            )
        }
        val innerInset = frameWidth + effectWidth
        val innerRadius = (outerCornerRadius - innerInset).coerceAtLeast(0f)
        val innerPath = Path().apply {
            addRoundRect(
                RoundRect(
                    left = innerInset,
                    top = innerInset,
                    right = size.width - innerInset,
                    bottom = size.height - innerInset,
                    cornerRadius = CornerRadius(innerRadius)
                )
            )
        }
        var effectPath = Path().apply {
            op(outerPath, innerPath, PathOperation.Difference)
        }

        if (badgePosition != SystemIconPosition.OFF) {
            val badgePath = createBadgePath(size)
            effectPath = Path().apply {
                op(effectPath, badgePath, PathOperation.Difference)
            }
        }

        return Outline.Generic(effectPath)
    }

    private fun createBadgePath(size: Size): Path {
        val path = Path()
        when (badgePosition) {
            SystemIconPosition.TOP_LEFT -> {
                path.addRoundRect(
                    RoundRect(
                        left = 0f,
                        top = 0f,
                        right = badgeWidth,
                        bottom = badgeHeight,
                        topLeftCornerRadius = CornerRadius(badgeCornerRadius),
                        bottomRightCornerRadius = CornerRadius(badgeCornerRadius)
                    )
                )
                val rightEarX = badgeWidth - oneDpPx
                val rightEarY = frameWidth - oneDpPx
                val rightEar = Path().apply {
                    moveTo(rightEarX, rightEarY)
                    lineTo(rightEarX + badgeCornerRadius, rightEarY)
                    arcTo(
                        Rect(
                            rightEarX,
                            rightEarY,
                            rightEarX + badgeCornerRadius * 2,
                            rightEarY + badgeCornerRadius * 2
                        ),
                        270f, -90f, false
                    )
                    close()
                }
                path.op(path, rightEar, PathOperation.Union)
                val bottomEarX = frameWidth - oneDpPx
                val bottomEarY = badgeHeight - oneDpPx
                val bottomEar = Path().apply {
                    moveTo(bottomEarX, bottomEarY)
                    lineTo(bottomEarX + badgeCornerRadius, bottomEarY)
                    arcTo(
                        Rect(
                            bottomEarX,
                            bottomEarY,
                            bottomEarX + badgeCornerRadius * 2,
                            bottomEarY + badgeCornerRadius * 2
                        ),
                        270f, -90f, false
                    )
                    close()
                }
                path.op(path, bottomEar, PathOperation.Union)
            }
            SystemIconPosition.TOP_RIGHT -> {
                val badgeLeft = size.width - badgeWidth
                path.addRoundRect(
                    RoundRect(
                        left = badgeLeft,
                        top = 0f,
                        right = size.width,
                        bottom = badgeHeight,
                        topRightCornerRadius = CornerRadius(badgeCornerRadius),
                        bottomLeftCornerRadius = CornerRadius(badgeCornerRadius)
                    )
                )
                val leftEarX = badgeLeft + oneDpPx
                val leftEarY = frameWidth - oneDpPx
                val leftEar = Path().apply {
                    moveTo(leftEarX, leftEarY)
                    lineTo(leftEarX - badgeCornerRadius, leftEarY)
                    arcTo(
                        Rect(
                            leftEarX - badgeCornerRadius * 2,
                            leftEarY,
                            leftEarX,
                            leftEarY + badgeCornerRadius * 2
                        ),
                        270f, 90f, false
                    )
                    close()
                }
                path.op(path, leftEar, PathOperation.Union)
                val bottomEarX = size.width - frameWidth + oneDpPx
                val bottomEarY = badgeHeight - oneDpPx
                val bottomEar = Path().apply {
                    moveTo(bottomEarX, bottomEarY)
                    lineTo(bottomEarX - badgeCornerRadius, bottomEarY)
                    arcTo(
                        Rect(
                            bottomEarX - badgeCornerRadius * 2,
                            bottomEarY,
                            bottomEarX,
                            bottomEarY + badgeCornerRadius * 2
                        ),
                        270f, 90f, false
                    )
                    close()
                }
                path.op(path, bottomEar, PathOperation.Union)
            }
            else -> {}
        }
        return path
    }
}

private class GlassRingShape(
    private val outerCornerRadius: Float,
    private val frameWidth: Float,
    private val innerEffectWidth: Float,
    private val startProgress: Float,
    private val endProgress: Float,
    private val badgePosition: SystemIconPosition = SystemIconPosition.OFF,
    private val badgeWidth: Float = 0f,
    private val badgeHeight: Float = 0f,
    private val badgeCornerRadius: Float = 0f,
    private val oneDpPx: Float = 0f
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val outerInset = frameWidth + (innerEffectWidth * startProgress)
        val outerRadius = (outerCornerRadius - outerInset).coerceAtLeast(0f)
        val outerPath = Path().apply {
            addRoundRect(
                RoundRect(
                    left = outerInset,
                    top = outerInset,
                    right = size.width - outerInset,
                    bottom = size.height - outerInset,
                    cornerRadius = CornerRadius(outerRadius)
                )
            )
        }

        val innerInset = frameWidth + (innerEffectWidth * endProgress)
        val innerRadius = (outerCornerRadius - innerInset).coerceAtLeast(0f)
        val innerPath = Path().apply {
            addRoundRect(
                RoundRect(
                    left = innerInset,
                    top = innerInset,
                    right = size.width - innerInset,
                    bottom = size.height - innerInset,
                    cornerRadius = CornerRadius(innerRadius)
                )
            )
        }

        var ringPath = Path().apply {
            op(outerPath, innerPath, PathOperation.Difference)
        }

        if (badgePosition != SystemIconPosition.OFF) {
            val badgePath = createBadgePath(size)
            ringPath = Path().apply {
                op(ringPath, badgePath, PathOperation.Difference)
            }
        }

        return Outline.Generic(ringPath)
    }

    private fun createBadgePath(size: Size): Path {
        val path = Path()
        when (badgePosition) {
            SystemIconPosition.TOP_LEFT -> {
                path.addRoundRect(
                    RoundRect(
                        left = 0f,
                        top = 0f,
                        right = badgeWidth,
                        bottom = badgeHeight,
                        topLeftCornerRadius = CornerRadius(badgeCornerRadius),
                        bottomRightCornerRadius = CornerRadius(badgeCornerRadius)
                    )
                )
                val rightEarX = badgeWidth - oneDpPx
                val rightEarY = frameWidth - oneDpPx
                val rightEar = Path().apply {
                    moveTo(rightEarX, rightEarY)
                    lineTo(rightEarX + badgeCornerRadius, rightEarY)
                    arcTo(
                        Rect(rightEarX, rightEarY, rightEarX + badgeCornerRadius * 2, rightEarY + badgeCornerRadius * 2),
                        270f, -90f, false
                    )
                    close()
                }
                path.op(path, rightEar, PathOperation.Union)
                val bottomEarX = frameWidth - oneDpPx
                val bottomEarY = badgeHeight - oneDpPx
                val bottomEar = Path().apply {
                    moveTo(bottomEarX, bottomEarY)
                    lineTo(bottomEarX + badgeCornerRadius, bottomEarY)
                    arcTo(
                        Rect(bottomEarX, bottomEarY, bottomEarX + badgeCornerRadius * 2, bottomEarY + badgeCornerRadius * 2),
                        270f, -90f, false
                    )
                    close()
                }
                path.op(path, bottomEar, PathOperation.Union)
            }
            SystemIconPosition.TOP_RIGHT -> {
                val badgeLeft = size.width - badgeWidth
                path.addRoundRect(
                    RoundRect(
                        left = badgeLeft,
                        top = 0f,
                        right = size.width,
                        bottom = badgeHeight,
                        topRightCornerRadius = CornerRadius(badgeCornerRadius),
                        bottomLeftCornerRadius = CornerRadius(badgeCornerRadius)
                    )
                )
                val leftEarX = badgeLeft + oneDpPx
                val leftEarY = frameWidth - oneDpPx
                val leftEar = Path().apply {
                    moveTo(leftEarX, leftEarY)
                    lineTo(leftEarX - badgeCornerRadius, leftEarY)
                    arcTo(
                        Rect(leftEarX - badgeCornerRadius * 2, leftEarY, leftEarX, leftEarY + badgeCornerRadius * 2),
                        270f, 90f, false
                    )
                    close()
                }
                path.op(path, leftEar, PathOperation.Union)
                val bottomEarX = size.width - frameWidth + oneDpPx
                val bottomEarY = badgeHeight - oneDpPx
                val bottomEar = Path().apply {
                    moveTo(bottomEarX, bottomEarY)
                    lineTo(bottomEarX - badgeCornerRadius, bottomEarY)
                    arcTo(
                        Rect(bottomEarX - badgeCornerRadius * 2, bottomEarY, bottomEarX, bottomEarY + badgeCornerRadius * 2),
                        270f, 90f, false
                    )
                    close()
                }
                path.op(path, bottomEar, PathOperation.Union)
            }
            else -> {}
        }
        return path
    }
}

private class GradientMaskShape(
    private val outerCornerRadius: Float,
    private val frameWidth: Float,
    private val isStub: Boolean,
    private val badgePosition: SystemIconPosition,
    private val badgeWidth: Float,
    private val badgeHeight: Float,
    private val badgeCornerRadius: Float,
    private val oneDpPx: Float
) : Shape {
    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density
    ): Outline {
        val path = Path()

        if (isStub) {
            path.addRoundRect(
                RoundRect(
                    left = 0f,
                    top = 0f,
                    right = size.width,
                    bottom = size.height,
                    cornerRadius = CornerRadius(outerCornerRadius)
                )
            )
        } else {
            val outerPath = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = 0f,
                        top = 0f,
                        right = size.width,
                        bottom = size.height,
                        cornerRadius = CornerRadius(outerCornerRadius)
                    )
                )
            }
            val innerRadius = (outerCornerRadius - frameWidth).coerceAtLeast(0f)
            val innerPath = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = frameWidth,
                        top = frameWidth,
                        right = size.width - frameWidth,
                        bottom = size.height - frameWidth,
                        cornerRadius = CornerRadius(innerRadius)
                    )
                )
            }
            path.op(outerPath, innerPath, PathOperation.Difference)
        }

        if (badgePosition != SystemIconPosition.OFF) {
            val badgePath = createBadgePath(size)
            path.op(path, badgePath, PathOperation.Union)
        }

        return Outline.Generic(path)
    }

    private fun createBadgePath(size: Size): Path {
        val path = Path()
        val borderOffsetPx = frameWidth

        when (badgePosition) {
            SystemIconPosition.TOP_LEFT -> {
                path.addRoundRect(
                    RoundRect(
                        left = 0f,
                        top = 0f,
                        right = badgeWidth,
                        bottom = badgeHeight,
                        topLeftCornerRadius = CornerRadius(badgeCornerRadius),
                        bottomRightCornerRadius = CornerRadius(badgeCornerRadius)
                    )
                )
                val rightEarX = badgeWidth - oneDpPx
                val rightEarY = borderOffsetPx - oneDpPx
                val rightEar = Path().apply {
                    moveTo(rightEarX, rightEarY)
                    lineTo(rightEarX + badgeCornerRadius, rightEarY)
                    arcTo(
                        Rect(rightEarX, rightEarY, rightEarX + badgeCornerRadius * 2, rightEarY + badgeCornerRadius * 2),
                        270f, -90f, false
                    )
                    close()
                }
                path.op(path, rightEar, PathOperation.Union)

                val bottomEarX = borderOffsetPx - oneDpPx
                val bottomEarY = badgeHeight - oneDpPx
                val bottomEar = Path().apply {
                    moveTo(bottomEarX, bottomEarY)
                    lineTo(bottomEarX + badgeCornerRadius, bottomEarY)
                    arcTo(
                        Rect(bottomEarX, bottomEarY, bottomEarX + badgeCornerRadius * 2, bottomEarY + badgeCornerRadius * 2),
                        270f, -90f, false
                    )
                    close()
                }
                path.op(path, bottomEar, PathOperation.Union)
            }
            SystemIconPosition.TOP_RIGHT -> {
                val badgeLeft = size.width - badgeWidth
                path.addRoundRect(
                    RoundRect(
                        left = badgeLeft,
                        top = 0f,
                        right = size.width,
                        bottom = badgeHeight,
                        topRightCornerRadius = CornerRadius(badgeCornerRadius),
                        bottomLeftCornerRadius = CornerRadius(badgeCornerRadius)
                    )
                )
                val leftEarX = badgeLeft + oneDpPx
                val leftEarY = borderOffsetPx - oneDpPx
                val leftEar = Path().apply {
                    moveTo(leftEarX, leftEarY)
                    lineTo(leftEarX - badgeCornerRadius, leftEarY)
                    arcTo(
                        Rect(leftEarX - badgeCornerRadius * 2, leftEarY, leftEarX, leftEarY + badgeCornerRadius * 2),
                        270f, 90f, false
                    )
                    close()
                }
                path.op(path, leftEar, PathOperation.Union)

                val bottomEarX = size.width - borderOffsetPx + oneDpPx
                val bottomEarY = badgeHeight - oneDpPx
                val bottomEar = Path().apply {
                    moveTo(bottomEarX, bottomEarY)
                    lineTo(bottomEarX - badgeCornerRadius, bottomEarY)
                    arcTo(
                        Rect(bottomEarX - badgeCornerRadius * 2, bottomEarY, bottomEarX, bottomEarY + badgeCornerRadius * 2),
                        270f, 90f, false
                    )
                    close()
                }
                path.op(path, bottomEar, PathOperation.Union)
            }
            else -> {}
        }
        return path
    }
}
