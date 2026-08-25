package com.nendo.argosy.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import kotlin.math.sin

private const val TWO_PI = (2.0 * Math.PI).toFloat()

/**
 * The download badge on its own, for artwork the water fill cannot be drawn over.
 *
 * A 3D box is a perspective render rather than a rectangle, so clipping a rising waterline to it
 * would cut across the shape. The badge still says what the fill would have: how far along, or that
 * it is waiting.
 */
@Composable
fun DownloadProgressBadge(
    progress: Float,
    badgeSize: Dp,
    modifier: Modifier = Modifier,
    paused: Boolean = false
) {
    Box(
        modifier = modifier
            .size(badgeSize)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), CircleShape),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            strokeWidth = Dimens.borderMedium,
            modifier = Modifier.size(badgeSize)
        )
        if (paused) {
            Icon(
                imageVector = Icons.Default.Pause,
                contentDescription = "Paused",
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(badgeSize / 2)
            )
        } else {
            Text(
                text = "${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

/**
 * A cover filling up as its download proceeds.
 *
 * [paused] stills it: the surface goes flat and the badge shows a pause mark instead of a
 * percentage. Motion is what says "this is moving", so a paused transfer that kept rippling would
 * claim progress it is not making, while a cover with no fill at all would lose the fact that a
 * part-finished copy is waiting.
 */
@Composable
fun DownloadProgressCover(
    imageData: Any,
    progress: Float,
    badgeSize: Dp,
    modifier: Modifier = Modifier,
    paused: Boolean = false
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 2000, easing = LinearEasing),
        label = "water_level"
    )
    val transition = rememberInfiniteTransition(label = "water")

    // Primary wave: ~1.5 cycles across the width, 3s period
    val phase1 by transition.animateFloat(
        initialValue = 0f,
        targetValue = TWO_PI,
        animationSpec = infiniteRepeatable(
            animation = tween(3000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase1"
    )
    // Secondary wave: ~2.3 cycles, slower drift in the opposite direction
    val phase2 by transition.animateFloat(
        initialValue = TWO_PI,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(4700, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase2"
    )
    // Primary amplitude: swells and recedes over 2.2s
    val ampScale1 by transition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "amp1"
    )
    // Secondary amplitude: slower, offset cycle (3.1s) so they rarely peak together
    val ampScale2 by transition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(3100, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "amp2"
    )

    val grayscaleMatrix = ColorMatrix().apply { setToSaturation(0f) }
    val freq1 = 1.5f * TWO_PI
    val freq2 = 2.3f * TWO_PI

    fun wavePath(
        width: Float,
        height: Float,
        waterY: Float,
        startY: Float,
        endY: Float
    ): Path {
        val baseAmp = if (paused) 0f else ComponentDefaults.DownloadCover.waveAmplitudeDp.toFloat()
        val amp1 = baseAmp * ampScale1
        val amp2 = baseAmp * 0.4f * ampScale2
        return Path().apply {
            moveTo(0f, startY)
            lineTo(0f, waterY)
            val steps = (width / 2f).toInt().coerceAtLeast(1)
            for (i in 0..steps) {
                val x = width * i / steps
                val t = x / width
                val y = waterY +
                    amp1 * sin(phase1 + t * freq1) +
                    amp2 * sin(phase2 + t * freq2)
                lineTo(x, y)
            }
            lineTo(width, endY)
            close()
        }
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        AsyncImage(
            model = imageData,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    val waterY = size.height * (1f - animatedProgress)
                    val path = wavePath(size.width, size.height, waterY, size.height, size.height)
                    clipPath(path) { this@drawWithContent.drawContent() }
                }
        )
        AsyncImage(
            model = imageData,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            colorFilter = ColorFilter.colorMatrix(grayscaleMatrix),
            modifier = Modifier
                .fillMaxSize()
                .drawWithContent {
                    val waterY = size.height * (1f - animatedProgress)
                    val path = wavePath(size.width, size.height, waterY, 0f, 0f)
                    clipPath(path) { this@drawWithContent.drawContent() }
                }
        )
        Box(
            modifier = Modifier
                .size(badgeSize)
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                    CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            if (paused) {
                Icon(
                    imageVector = Icons.Default.Pause,
                    contentDescription = "Paused",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(badgeSize / 2)
                )
            } else {
                Text(
                    text = "${(progress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}
