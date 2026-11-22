package com.nendo.argosy.ui.screens.home

import android.graphics.Bitmap
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.em
import coil.compose.AsyncImage
import com.nendo.argosy.ui.common.ExpandingClipShape
import com.nendo.argosy.ui.common.rememberFileImageModel
import com.nendo.argosy.ui.components.GameTitle
import com.nendo.argosy.ui.components.GameVideoPlayer
import com.nendo.argosy.ui.screens.home.delegates.VideoPreviewPhase
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.reducedMotion
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.util.clickableNoFocus
import kotlinx.coroutines.delay

@Composable
internal fun HomeColumnsTakeover(
    uiState: HomeUiState,
    columnBounds: () -> Rect?,
    progress: () -> Float,
    onReady: (String) -> Unit,
    onError: (String) -> Unit,
    onBeginTakeover: (String) -> Unit,
    onShowInfo: (String) -> Unit,
    onFinishRestore: () -> Unit,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onCoverLoadFailed: (Long, String) -> Unit,
    onCoverLoaded: (Long, Bitmap) -> Unit,
    modifier: Modifier = Modifier
) {
    val videoClip = uiState.videoPreviewClip ?: return
    val phase = uiState.videoPreviewPhase
    if (phase == VideoPreviewPhase.IDLE) return
    val bounds = columnBounds() ?: return
    val reduced = reducedMotion()
    val expandMs = if (reduced) 0 else Motion.durationMedium
    val beginTakeover by rememberUpdatedState(onBeginTakeover)
    val showInfo by rememberUpdatedState(onShowInfo)
    val finishRestore by rememberUpdatedState(onFinishRestore)
    LaunchedEffect(phase, videoClip.key, reduced) {
        when (phase) {
            VideoPreviewPhase.PLAYING -> {
                if (!reduced) delay(ComponentDefaults.HomeColumns.takeoverBeatMs.toLong())
                beginTakeover(videoClip.key)
            }
            VideoPreviewPhase.EXPANDED -> {
                delay((expandMs + ComponentDefaults.HomeColumns.takeoverInfoDelayMs).toLong())
                showInfo(videoClip.key)
            }
            VideoPreviewPhase.RESTORING -> {
                delay(expandMs.toLong())
                finishRestore()
            }
            VideoPreviewPhase.IDLE, VideoPreviewPhase.LOADING, VideoPreviewPhase.INFO -> Unit
        }
    }
    val videoAlpha by animateFloatAsState(
        targetValue = if (phase == VideoPreviewPhase.PLAYING || phase.isExpanded) 1f else 0f,
        animationSpec = if (reduced) snap<Float>() else tween<Float>(Motion.durationMedium, easing = Motion.argosyEase),
        label = "takeover-video"
    )
    val infoShown by animateFloatAsState(
        targetValue = if (phase == VideoPreviewPhase.INFO) 1f else 0f,
        animationSpec = if (reduced) snap<Float>() else tween<Float>(Motion.durationMedium, easing = Motion.argosyEase),
        label = "takeover-info"
    )
    var origin by remember { mutableStateOf(Offset.Zero) }
    val radiusPx = with(LocalDensity.current) { Dimens.radiusPanel.toPx() }
    BoxWithConstraints(modifier.fillMaxSize().onGloballyPositioned { origin = it.positionInRoot() }) {
        val surfaceWidth = maxWidth
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    shape = ExpandingClipShape(bounds.translate(-origin), progress(), radiusPx)
                    clip = true
                    alpha = videoAlpha
                }
        ) {
            GameVideoPlayer(
                clip = videoClip,
                muted = uiState.muteVideoPreview,
                onReady = { onReady(videoClip.key) },
                onError = { onError(videoClip.key) },
                modifier = Modifier.fillMaxSize()
            )
            Box(Modifier.matchParentSize().clickableNoFocus(onClick = onTap, onLongClick = onLongPress))
        }
        val column = uiState.columns.focusedColumn
        val item = uiState.columns.focusedItem
        val game = (item as? HomeRowItem.Game)?.game
        if (column != null && item != null && game != null && infoShown > 0f) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(ComponentDefaults.HomeColumns.takeoverScrimHeightRatio)
                    .graphicsLayer { alpha = infoShown }
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color.Transparent,
                                Color.Black.copy(alpha = ComponentDefaults.HomeColumns.bottomScrimAlpha)
                            )
                        )
                    )
            )
            TakeoverInfo(
                column = column,
                item = item,
                game = game,
                friends = uiState.friendsFor(game),
                shown = { infoShown },
                surfaceWidth = surfaceWidth,
                onCoverLoadFailed = onCoverLoadFailed,
                onCoverLoaded = onCoverLoaded,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(Dimens.spacingXxl)
            )
        }
    }
}

@Composable
private fun TakeoverInfo(
    column: HomeColumnUi,
    item: HomeRowItem,
    game: HomeGameUi,
    friends: List<com.nendo.argosy.data.social.FriendActivity>,
    shown: () -> Float,
    surfaceWidth: Dp,
    onCoverLoadFailed: (Long, String) -> Unit,
    onCoverLoaded: (Long, Bitmap) -> Unit,
    modifier: Modifier = Modifier
) {
    val slide = Dimens.spacingXxl
    val secondary = Color.White.copy(alpha = ComponentDefaults.HomeColumns.secondaryTextAlpha)
    val logoPath = game.logoPath
    val dataLine = listOfNotNull(
        game.platformDisplayName.takeIf { it.isNotBlank() },
        homeColumnFact(column, item, friends)
    ).joinToString(BAND_FACT_SEPARATOR)
    Column(
        modifier = modifier.graphicsLayer { alpha = shown() },
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        Box(Modifier.graphicsLayer { translationX = -(1f - shown()) * slide.toPx() }) {
            if (logoPath != null) {
                AsyncImage(
                    model = rememberFileImageModel(logoPath),
                    contentDescription = game.title,
                    contentScale = ContentScale.Fit,
                    alignment = Alignment.BottomStart,
                    modifier = Modifier.width(surfaceWidth * ComponentDefaults.HomeColumns.takeoverLogoWidthRatio)
                )
            } else {
                ColumnCover(
                    item = item,
                    onCoverLoadFailed = onCoverLoadFailed,
                    onCoverLoaded = onCoverLoaded,
                    modifier = Modifier.width(surfaceWidth * ComponentDefaults.HomeColumns.takeoverCoverWidthRatio)
                )
            }
        }
        Text(
            text = homeColumnTitle(column),
            style = MaterialTheme.typography.labelSmall.copy(
                letterSpacing = ComponentDefaults.HomeColumns.kickerTrackingRatio.em
            ),
            color = LocalArgosyTheme.current.focusAccent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (logoPath == null) {
            GameTitle(
                title = game.title,
                titleStyle = MaterialTheme.typography.titleLarge,
                titleColor = Color.White,
                seriesScale = HOME_TITLE_SERIES_SCALE,
                adaptiveSize = true
            )
        }
        if (dataLine.isNotEmpty()) {
            Text(
                text = dataLine,
                style = MaterialTheme.typography.bodyMedium,
                color = secondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
