package com.nendo.argosy.ui.components.nowplaying

import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import com.nendo.argosy.R
import com.nendo.argosy.ui.audio.AmbientAudioManager
import com.nendo.argosy.ui.audio.AmbientPlaybackState
import com.nendo.argosy.ui.audio.NowPlayingPillPolicy
import com.nendo.argosy.ui.navigation.Screen
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.theme.loopingFloat
import com.nendo.argosy.ui.theme.reducedMotion
import com.nendo.argosy.ui.util.clickableNoFocus
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.ceil

data class NowPlayingPillUi(
    val title: String,
    val artist: String?,
    val playing: Boolean
)

fun nowPlayingPill(playback: AmbientPlaybackState, suppressed: Boolean): NowPlayingPillUi? {
    if (suppressed || !playback.hasQueue) return null
    val title = playback.displayTitle?.takeIf { it.isNotBlank() } ?: return null
    return NowPlayingPillUi(
        title = title,
        artist = playback.displayArtist?.takeIf { it.isNotBlank() },
        playing = playback.isAudible
    )
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface NowPlayingPillEntryPoint {
    fun ambientAudioManager(): AmbientAudioManager
    fun nowPlayingPillPolicy(): NowPlayingPillPolicy
}

private fun pillEntryPoint(context: Context): NowPlayingPillEntryPoint =
    EntryPointAccessors.fromApplication(context.applicationContext, NowPlayingPillEntryPoint::class.java)

@Composable
private fun rememberPillEntryPoint(): NowPlayingPillEntryPoint {
    val context = LocalContext.current
    return remember { pillEntryPoint(context) }
}

/**
 * Runs [onOpen] for every pill tap made on a surface that cannot navigate the control screen
 * itself. Composed once, by the host that owns navigation.
 */
@Composable
fun NowPlayingPillOpenRequests(onOpen: () -> Unit) {
    val policy = rememberPillEntryPoint().nowPlayingPillPolicy()
    val currentOnOpen by rememberUpdatedState(onOpen)
    LaunchedEffect(policy) {
        policy.openRequests.collect { currentOnOpen() }
    }
}

/**
 * The pill tap for the visual screen: it asks the control screen to open Now Playing. Null, which
 * hides the segment, while the control screen already shows Now Playing or a game owns the screen.
 */
@Composable
fun rememberPresentationNowPlayingOpener(primaryRoute: StateFlow<String?>?, inGame: Boolean): (() -> Unit)? {
    val policy = rememberPillEntryPoint().nowPlayingPillPolicy()
    val route = primaryRoute?.collectAsState()?.value
    val open: () -> Unit = remember(policy) { { policy.requestOpen() } }
    return open.takeIf { !inGame && route != Screen.ROUTE_NOW_PLAYING }
}

@Composable
fun NowPlayingPillSegment(
    onOpen: () -> Unit,
    textColor: Color,
    textStyle: TextStyle,
    modifier: Modifier = Modifier
) {
    val entry = rememberPillEntryPoint()
    val playback by entry.ambientAudioManager().playback.collectAsState()
    val suppressed by entry.nowPlayingPillPolicy().suppressed.collectAsState()
    val pill = nowPlayingPill(playback, suppressed) ?: return
    val label = pill.artist?.let { stringResource(R.string.ui_status_bar_now_playing_label, pill.title, it) }
        ?: pill.title
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(Dimens.radiusSm))
            .clickableNoFocus(onClick = onOpen)
            .padding(horizontal = Dimens.spacingXs, vertical = Dimens.borderMedium),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
    ) {
        EqualiserGlyph(playing = pill.playing)
        MarqueeLabel(
            text = label,
            color = textColor,
            style = textStyle,
            modifier = Modifier.widthIn(max = ComponentDefaults.StatusPill.nowPlayingMaxWidthDp.dp)
        )
    }
}

@Composable
private fun EqualiserGlyph(playing: Boolean) {
    val theme = LocalArgosyTheme.current
    val spec = ComponentDefaults.StatusPill
    val color = if (playing) theme.focusAccent else theme.textDim
    val description = stringResource(R.string.ui_status_bar_now_playing_desc)
    val phase: State<Float>? = if (playing) {
        rememberInfiniteTransition(label = "nowPlayingEqualiser").loopingFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(spec.equaliserCycleMs, easing = LinearEasing)),
            label = "nowPlayingEqualiserPhase"
        )
    } else {
        null
    }
    Canvas(
        modifier = Modifier
            .size(Dimens.iconSm)
            .semantics { contentDescription = description }
    ) {
        val count = spec.equaliserBarCount
        val barWidth = size.width * spec.equaliserBarWidthRatio
        val gap = if (count > 1) (size.width - barWidth * count) / (count - 1) else 0f
        val radius = CornerRadius(barWidth / 2f, barWidth / 2f)
        val currentPhase = phase?.value ?: 0f
        for (index in 0 until count) {
            val height = size.height * equaliserBarFraction(index, count, currentPhase, spec.equaliserMinHeightRatio)
            drawRoundRect(
                color = color,
                topLeft = Offset(index * (barWidth + gap), size.height - height),
                size = Size(barWidth, height),
                cornerRadius = radius
            )
        }
    }
}

@Composable
private fun MarqueeLabel(text: String, color: Color, style: TextStyle, modifier: Modifier = Modifier) {
    val spec = ComponentDefaults.StatusPill
    val density = LocalDensity.current
    val rtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val reduced = reducedMotion()
    var overflowPx by remember(text) { mutableFloatStateOf(0f) }
    var visibleWidthPx by remember(text) { mutableIntStateOf(0) }
    var scrolling by remember(text) { mutableStateOf(false) }
    val offset = remember(text) { Animatable(0f) }
    val overflowing = overflowPx > 0f
    LaunchedEffect(text, overflowing, reduced) {
        if (!overflowing || reduced) return@LaunchedEffect
        delay(spec.nowPlayingMarqueeDelayMs.toLong())
        val pxPerSecond = with(density) { spec.nowPlayingMarqueeDpPerSecond.dp.toPx() }
        scrolling = true
        offset.animateTo(
            targetValue = overflowPx,
            animationSpec = tween(marqueeDurationMs(overflowPx, pxPerSecond), easing = LinearEasing)
        )
        offset.snapTo(0f)
        scrolling = false
    }
    Box(modifier = modifier.clipToBounds()) {
        if (scrolling) {
            Text(
                text = text,
                style = style,
                color = color,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .width(with(density) { visibleWidthPx.toDp() })
                    .wrapContentWidth(align = Alignment.Start, unbounded = true)
                    .graphicsLayer { translationX = if (rtl) offset.value else -offset.value }
            )
        } else {
            Text(
                text = text,
                style = style,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                onTextLayout = { layout ->
                    visibleWidthPx = layout.size.width
                    val hidden = ceil(layout.multiParagraph.intrinsics.maxIntrinsicWidth) - layout.size.width
                    overflowPx = if (hidden > 1f) hidden else 0f
                }
            )
        }
    }
}
