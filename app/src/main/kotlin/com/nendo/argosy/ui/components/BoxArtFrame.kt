package com.nendo.argosy.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.nendo.argosy.data.preferences.BoxArtBorderStyle
import com.nendo.argosy.data.preferences.BoxArtOuterEffect
import com.nendo.argosy.data.preferences.GlowColorMode
import com.nendo.argosy.ui.theme.LocalBoxArtStyle
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.generated.ComponentDefaults

/**
 * The single owner of how a piece of cover art reacts to focus.
 *
 * Lift, fade, glow, border and corner rounding are all user settings, so every surface that draws
 * cover art has to read the same ones or the app has several box-art styles wearing one name. Cover
 * art is cover art whether it is a game, a film or a series: the artwork differs, the frame does not.
 *
 * [artworkGradient] is the pair sampled from the artwork itself, used by the border and glow modes
 * that follow the cover. Pass null when nothing has been sampled and those modes fall back to the
 * theme accent rather than drawing nothing.
 */
@Composable
fun Modifier.boxArtFrame(
    isFocused: Boolean,
    focusScale: Float = ComponentDefaults.Focus.scaleFocused,
    scalePivotY: Float = 0.5f,
    scaleOverride: Float? = null,
    alphaOverride: Float? = null,
    artworkGradient: Pair<Color, Color>? = null,
    background: Brush? = null,
    drawBorder: Boolean = true,
    shapeOverride: Shape? = null
): Modifier {
    val boxArtStyle = LocalBoxArtStyle.current
    val themeConfig = LocalLauncherTheme.current

    val glowGradientColors: Pair<Color, Color>? = when (boxArtStyle.glowColorMode) {
        GlowColorMode.AUTO -> when (boxArtStyle.borderStyle) {
            BoxArtBorderStyle.GRADIENT, BoxArtBorderStyle.GLASS -> artworkGradient
            else -> null
        }
        GlowColorMode.ACCENT -> null
        GlowColorMode.ACCENT_GRADIENT -> {
            val accent = boxArtStyle.accentColor
            val secondary = boxArtStyle.secondaryColor
            if (accent != null && secondary != null) Pair(accent, secondary) else null
        }
        GlowColorMode.COVER -> artworkGradient
    }

    val scale by animateFloatAsState(
        targetValue = scaleOverride ?: if (isFocused) focusScale else ComponentDefaults.Focus.scaleDefault,
        animationSpec = Motion.focusSpring,
        label = "boxArtScale"
    )
    val alpha by animateFloatAsState(
        targetValue = alphaOverride
            ?: if (isFocused) ComponentDefaults.Focus.alphaFocused else ComponentDefaults.Focus.alphaUnfocused,
        animationSpec = Motion.focusSpring,
        label = "boxArtAlpha"
    )

    val outerEffect = boxArtStyle.outerEffect
    val outerEffectRadius = boxArtStyle.outerEffectThicknessPx
    val showOuterEffect = isFocused && outerEffect != BoxArtOuterEffect.OFF
    val glowColor = boxArtStyle.accentColor ?: themeConfig.focusGlowColor
    val shape = shapeOverride ?: RoundedCornerShape(boxArtStyle.cornerRadiusDp)

    val shineTransition = if (outerEffect == BoxArtOuterEffect.SHINE && isFocused) {
        rememberInfiniteTransition(label = "boxArtShine")
    } else null
    val shineOffset by shineTransition?.animateFloat(
        initialValue = -0.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "boxArtShine"
    ) ?: remember { mutableStateOf(0f) }

    val borderColor = MaterialTheme.colorScheme.primary
    val showBorder = drawBorder &&
        isFocused &&
        boxArtStyle.borderThicknessDp.value > 0f &&
        boxArtStyle.borderStyle == BoxArtBorderStyle.SOLID

    return this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
            transformOrigin = TransformOrigin(0.5f, scalePivotY)
            this.alpha = alpha
            this.clip = false
        }
        .then(
            if (showOuterEffect) {
                Modifier.drawBehind {
                    drawIntoCanvas { canvas ->
                        val cornerRadius = if (shapeOverride != null) {
                            size.minDimension / 2f
                        } else {
                            boxArtStyle.cornerRadiusDp.toPx()
                        }
                        val spread = outerEffectRadius
                        when (outerEffect) {
                            BoxArtOuterEffect.GLOW -> {
                                val glowAlpha = boxArtStyle.glowAlpha
                                val paint = android.graphics.Paint().apply {
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
                                    paint
                                )
                            }
                            BoxArtOuterEffect.SHADOW -> {
                                val paint = Paint().apply { color = Color.Black.copy(alpha = 0.3f) }
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
                                val shineX = shineOffset * (size.width + shineWidth) - shineWidth
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
        .then(if (showBorder) Modifier.border(boxArtStyle.borderThicknessDp, borderColor, shape) else Modifier)
        .clip(shape)
        .then(
            if (background != null) {
                Modifier.background(background)
            } else {
                Modifier.background(SolidColor(MaterialTheme.colorScheme.surfaceVariant))
            }
        )
}
