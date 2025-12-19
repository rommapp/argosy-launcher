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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material3.Icon
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import com.nendo.argosy.ui.theme.LocalLauncherTheme
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
    downloadIndicator: GameDownloadIndicator = GameDownloadIndicator.NONE
) {
    val themeConfig = LocalLauncherTheme.current
    val boxArtStyle = LocalBoxArtStyle.current

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
        targetValue = if (isFocused) boxArtStyle.glowAlpha else Motion.glowAlphaUnfocused,
        animationSpec = Motion.focusSpring,
        label = "glow"
    )

    val glowColor = themeConfig.focusGlowColor
    val borderColor = MaterialTheme.colorScheme.primary
    val shape = RoundedCornerShape(boxArtStyle.cornerRadiusDp)

    val saturationMatrix = ColorMatrix().apply {
        setToSaturation(saturation)
    }

    val glowRadius = 16f

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
                            val cornerRadius = boxArtStyle.cornerRadiusDp.toPx()
                            canvas.nativeCanvas.drawRoundRect(
                                -spread,
                                -spread,
                                size.width + spread,
                                size.height + spread,
                                cornerRadius + spread,
                                cornerRadius + spread,
                                frameworkPaint
                            )
                        }
                    }
                } else Modifier
            )
            .then(
                if (isFocused && boxArtStyle.borderThicknessDp.value > 0f) {
                    Modifier.border(boxArtStyle.borderThicknessDp, borderColor, shape)
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

        if (boxArtStyle.systemIconPosition != SystemIconPosition.OFF) {
            val badgeAlignment = when (boxArtStyle.systemIconPosition) {
                SystemIconPosition.TOP_LEFT -> Alignment.TopStart
                SystemIconPosition.TOP_RIGHT -> Alignment.TopEnd
                else -> Alignment.TopStart
            }
            PlatformBadge(
                platformId = game.platformId,
                cardWidthDp = maxWidth,
                modifier = Modifier.align(badgeAlignment)
            )
        }

        if (downloadIndicator.isActive) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(Dimens.spacingSm)
                    .size(22.dp)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.9f),
                        CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = "Downloading",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
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

            if (downloadIndicator.isActive) {
                val primaryColor = MaterialTheme.colorScheme.primary
                val progressColor = if (downloadIndicator.isPaused || downloadIndicator.isQueued) {
                    primaryColor.copy(alpha = 0.5f)
                } else {
                    primaryColor
                }
                val progressBarHeight = 6.dp

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
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White
        )
    }
}
