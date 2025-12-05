package com.nendo.argosy.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nendo.argosy.ui.screens.home.HomeGameUi
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.ui.theme.Motion

@Composable
fun GameCard(
    game: HomeGameUi,
    isFocused: Boolean,
    modifier: Modifier = Modifier,
    focusScale: Float = Motion.scaleFocused,
    scaleFromBottom: Boolean = false
) {
    val themeConfig = LocalLauncherTheme.current

    val scale by animateFloatAsState(
        targetValue = if (isFocused) focusScale else Motion.scaleDefault,
        animationSpec = Motion.focusSpring,
        label = "scale"
    )

    val alpha by animateFloatAsState(
        targetValue = if (isFocused) Motion.alphaFocused else Motion.alphaUnfocused,
        animationSpec = Motion.focusSpring,
        label = "alpha"
    )

    val saturation by animateFloatAsState(
        targetValue = if (isFocused) Motion.saturationFocused else Motion.saturationUnfocused,
        animationSpec = Motion.focusSpring,
        label = "saturation"
    )

    val glowAlpha by animateFloatAsState(
        targetValue = if (isFocused) Motion.glowAlphaFocused else Motion.glowAlphaUnfocused,
        animationSpec = Motion.focusSpring,
        label = "glow"
    )

    val glowColor = themeConfig.focusGlowColor
    val borderColor = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(Dimens.radiusMd)

    val saturationMatrix = ColorMatrix().apply {
        setToSaturation(saturation)
    }

    val glowRadius = 16f

    Box(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = if (scaleFromBottom) TransformOrigin(0.5f, 1f) else TransformOrigin.Center
                this.alpha = alpha
                this.clip = false
            }
            .then(
                if (glowAlpha > 0f) {
                    Modifier.drawBehind {
                        drawIntoCanvas { canvas ->
                            val paint = Paint().apply {
                                color = glowColor.copy(alpha = glowAlpha)
                            }
                            val frameworkPaint = paint.asFrameworkPaint().apply {
                                maskFilter = android.graphics.BlurMaskFilter(
                                    glowRadius,
                                    android.graphics.BlurMaskFilter.Blur.NORMAL
                                )
                            }
                            val spread = glowRadius
                            canvas.nativeCanvas.drawRoundRect(
                                -spread,
                                -spread,
                                size.width + spread,
                                size.height + spread,
                                Dimens.radiusLg.toPx() + spread,
                                Dimens.radiusLg.toPx() + spread,
                                frameworkPaint
                            )
                        }
                    }
                } else Modifier
            )
            .then(
                if (isFocused) {
                    Modifier.border(Dimens.borderThick, borderColor, shape)
                } else Modifier
            )
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        if (game.coverPath != null) {
            AsyncImage(
                model = game.coverPath,
                contentDescription = game.title,
                contentScale = ContentScale.Crop,
                colorFilter = ColorFilter.colorMatrix(saturationMatrix),
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        MaterialTheme.colorScheme.primaryContainer
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = game.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(Dimens.spacingSm)
                )
            }
        }

        if (game.isFavorite || game.isDownloaded) {
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(Dimens.spacingSm),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (game.isFavorite) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(Color.Black.copy(alpha = 0.35f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Favorite,
                            contentDescription = "Favorite",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                } else {
                    Box(modifier = Modifier.size(20.dp))
                }

                if (game.isDownloaded) {
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .background(Color.Black.copy(alpha = 0.35f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Downloaded",
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                } else {
                    Box(modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
fun GameCardWithBadge(
    game: HomeGameUi,
    isFocused: Boolean,
    badge: @Composable (() -> Unit)? = null,
    modifier: Modifier = Modifier
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
fun SourceBadge(
    isLocal: Boolean,
    isSynced: Boolean
) {
    val (text, color) = when {
        isLocal && !isSynced -> "F" to MaterialTheme.colorScheme.tertiary
        isSynced -> "C" to MaterialTheme.colorScheme.primary
        else -> return
    }

    Box(
        modifier = Modifier
            .background(
                color = color,
                shape = RoundedCornerShape(Dimens.radiusSm)
            )
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White
        )
    }
}
