package com.nendo.argosy.ui.components.nowplaying

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.nendo.argosy.data.preferences.NowPlayingStyle
import com.nendo.argosy.domain.model.MusicArt
import com.nendo.argosy.ui.screens.music.rememberMusicArtModel
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.generated.ColorTokens
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.theme.reducedMotion
import kotlin.math.roundToInt

private const val FULL_TURN = 360f
private const val COAST_DISTANCE_DIVISOR = 2f

private val Defaults = ComponentDefaults.NowPlaying
private val DiscColors = ColorTokens.Domain.NowPlaying

private val CoastEasing = Easing { t -> 1f - (1f - t) * (1f - t) }

private val RainbowStops = listOf(
    DiscColors.rainbowRed,
    DiscColors.rainbowYellow,
    DiscColors.rainbowGreen,
    DiscColors.rainbowCyan,
    DiscColors.rainbowViolet,
    DiscColors.rainbowRed
)

/**
 * The current track's art as a record, a CD or a plain cover. [spinning] is the user's spin
 * setting: with it on the disc turns while [playing] and coasts to a stop on pause; turning it off
 * returns the disc upright the short way round. Reduced motion keeps it upright and still.
 */
@Composable
fun DiscArt(
    art: MusicArt?,
    style: NowPlayingStyle,
    spinning: Boolean,
    playing: Boolean,
    modifier: Modifier = Modifier
) {
    val reduced = reducedMotion()
    val rotation = remember { Animatable(0f) }
    var turning by remember { mutableStateOf(false) }
    val rpm = when (style) {
        NowPlayingStyle.RECORD -> Defaults.recordRpm
        NowPlayingStyle.CD -> Defaults.cdRpm
        NowPlayingStyle.COVER -> 0f
    }
    val spinOn = spinning && style.spins && !reduced
    LaunchedEffect(spinOn, playing, rpm, reduced) {
        when {
            reduced -> {
                turning = false
                rotation.snapTo(0f)
            }
            spinOn && playing -> {
                turning = true
                spinContinuously(rotation, rpm)
            }
            spinOn -> {
                if (turning) coastToStop(rotation, rpm)
                turning = false
            }
            else -> {
                turning = false
                returnUpright(rotation)
            }
        }
    }

    Box(
        modifier = modifier
            .aspectRatio(1f)
            .graphicsLayer { rotationZ = rotation.value },
        contentAlignment = Alignment.Center
    ) {
        when (style) {
            NowPlayingStyle.RECORD -> RecordDisc(art)
            NowPlayingStyle.CD -> CompactDisc(art)
            NowPlayingStyle.COVER -> SquareCover(art)
        }
    }
}

private suspend fun spinContinuously(rotation: Animatable<Float, AnimationVector1D>, rpm: Float) {
    val periodMs = (FULL_TURN / degreesPerMs(rpm)).roundToInt()
    while (true) {
        val start = rotation.value.mod(FULL_TURN)
        rotation.snapTo(start)
        rotation.animateTo(start + FULL_TURN, tween(periodMs, easing = LinearEasing))
    }
}

private suspend fun coastToStop(rotation: Animatable<Float, AnimationVector1D>, rpm: Float) {
    val distance = degreesPerMs(rpm) * Defaults.spinStopMs / COAST_DISTANCE_DIVISOR
    rotation.animateTo(rotation.value + distance, tween(Defaults.spinStopMs, easing = CoastEasing))
    rotation.snapTo(rotation.value.mod(FULL_TURN))
}

private suspend fun returnUpright(rotation: Animatable<Float, AnimationVector1D>) {
    val delta = uprightDelta(rotation.value)
    if (delta != 0f) {
        val durationMs = uprightDurationMs(delta, Defaults.uprightMinMs, Defaults.uprightMaxMs)
        rotation.animateTo(rotation.value + delta, tween(durationMs, easing = EaseOut))
    }
    rotation.snapTo(0f)
}

@Composable
private fun BoxScope.RecordDisc(art: MusicArt?) {
    val theme = LocalArgosyTheme.current
    val grooveWidth = Dimens.borderThin
    val rimWidth = Dimens.borderMedium
    Box(
        modifier = Modifier
            .matchParentSize()
            .clip(CircleShape)
            .background(Brush.radialGradient(listOf(DiscColors.vinyl, DiscColors.vinyl, DiscColors.vinylEdge)))
            .drawBehind {
                val outer = size.minDimension / 2f
                drawCircle(
                    color = DiscColors.rim,
                    radius = outer - rimWidth.toPx() / 2f,
                    style = Stroke(rimWidth.toPx())
                )
                val inner = outer * Defaults.grooveInnerRatio
                val span = outer * Defaults.grooveOuterRatio - inner
                val count = Defaults.grooveCount
                for (ring in 0 until count) {
                    drawCircle(
                        color = DiscColors.groove,
                        radius = inner + span * (ring + 1) / count,
                        style = Stroke(grooveWidth.toPx())
                    )
                }
            }
            .drawWithContent {
                drawContent()
                drawCircle(theme.surfaceBase, radius = size.minDimension * Defaults.recordHoleRatio)
            },
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize(Defaults.recordLabelRatio)
                .clip(CircleShape)
                .background(theme.focusAccent),
            contentAlignment = Alignment.Center
        ) {
            ArtImage(art)
        }
    }
}

@Composable
private fun BoxScope.CompactDisc(art: MusicArt?) {
    val theme = LocalArgosyTheme.current
    val ringWidth = Dimens.borderMedium
    Box(
        modifier = Modifier
            .matchParentSize()
            .clip(CircleShape)
            .background(theme.surfaceRaised),
        contentAlignment = Alignment.Center
    ) {
        ArtImage(art)
        Box(
            modifier = Modifier
                .matchParentSize()
                .drawBehind {
                    val outer = size.minDimension / 2f
                    drawCircle(
                        brush = Brush.sweepGradient(RainbowStops, center),
                        alpha = Defaults.cdRainbowAlpha
                    )
                    drawCircle(
                        color = DiscColors.hubRing,
                        radius = outer * Defaults.cdHubRatio,
                        style = Stroke(ringWidth.toPx())
                    )
                    drawCircle(theme.surfaceBase, radius = outer * Defaults.cdHoleRatio)
                    drawCircle(
                        color = DiscColors.hubRing,
                        radius = outer * Defaults.cdHoleRatio,
                        style = Stroke(ringWidth.toPx())
                    )
                }
        )
    }
}

@Composable
private fun BoxScope.SquareCover(art: MusicArt?) {
    Box(
        modifier = Modifier
            .matchParentSize()
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(LocalArgosyTheme.current.surfaceRaised),
        contentAlignment = Alignment.Center
    ) {
        ArtImage(art)
    }
}

@Composable
private fun ArtImage(art: MusicArt?) {
    val model = rememberMusicArtModel(art)
    if (model == null) {
        Icon(
            imageVector = Icons.Default.MusicNote,
            contentDescription = null,
            tint = LocalArgosyTheme.current.textDim,
            modifier = Modifier.size(Dimens.iconMd)
        )
        return
    }
    AsyncImage(
        model = model,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        modifier = Modifier.fillMaxSize()
    )
}
