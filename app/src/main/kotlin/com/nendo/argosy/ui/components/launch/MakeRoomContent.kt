package com.nendo.argosy.ui.components.launch

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nendo.argosy.R
import com.nendo.argosy.domain.model.LaunchStep
import com.nendo.argosy.domain.model.MakeRoomCandidate
import com.nendo.argosy.domain.model.MakeRoomStage
import com.nendo.argosy.ui.common.rememberFileImageModel
import androidx.compose.animation.Crossfade
import com.nendo.argosy.ui.common.GameArtBackdrop
import com.nendo.argosy.ui.common.rememberResolvedBackgroundPath
import com.nendo.argosy.ui.common.rememberResolvedCoverPath
import com.nendo.argosy.ui.common.rememberTaskSnapshotRequest
import com.nendo.argosy.ui.components.GameTitle
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.LaunchCancelButton
import com.nendo.argosy.ui.components.scrollToItemCentered
import com.nendo.argosy.ui.primitives.ActionButton
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.ui.theme.LocalMotionTier
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.theme.MotionTier
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.util.clickableNoFocus
import com.nendo.argosy.util.formatRelativeTime
import java.time.Instant

/**
 * The full-pool question: the open built-in games as a strip of cards, [focusIndex] marking the
 * one A closes. Once a game is chosen the strip narrows to it while it saves, then shows it closed.
 * [onTapCard] receives the card's index in [LaunchStep.MakeRoom.candidates].
 */
@Composable
fun MakeRoomContent(
    step: LaunchStep.MakeRoom,
    focusIndex: Int,
    onTapCard: (Int) -> Unit,
    onClose: () -> Unit,
    onCancel: () -> Unit
) {
    val reduced = LocalMotionTier.current == MotionTier.Reduced
    val contentMs = if (reduced) 0 else Motion.durationContent
    val choosing = step.stage == MakeRoomStage.CHOOSE
    val deepScrim by animateFloatAsState(
        targetValue = if (choosing) 0f else ComponentDefaults.LaunchOverlay.makeRoomDeepScrimAlpha,
        animationSpec = tween(contentMs, easing = Motion.argosyEase),
        label = "makeRoomDeepScrim"
    )
    val headerAlpha by animateFloatAsState(
        targetValue = if (choosing) 1f else 0f,
        animationSpec = tween(contentMs, easing = Motion.argosyEase),
        label = "makeRoomHeader"
    )
    val backdropGameId = if (choosing) {
        step.candidates.getOrNull(focusIndex)?.gameId
    } else {
        step.candidates.firstOrNull { it.taskId == step.chosenTaskId }?.gameId
    }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Crossfade(
            targetState = backdropGameId,
            animationSpec = tween(contentMs, easing = Motion.argosyEase),
            label = "makeRoomBackdrop",
            modifier = Modifier.fillMaxSize()
        ) { gameId ->
            if (gameId != null) {
                GameArtBackdrop(
                    rememberResolvedBackgroundPath(gameId, null) ?: rememberResolvedCoverPath(gameId, null)
                )
            }
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background.copy(alpha = deepScrim))
        )
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth().padding(vertical = Dimens.spacingLg)
        ) {
            MakeRoomHeader(
                launchingTitle = step.launchingTitle,
                modifier = Modifier.graphicsLayer { alpha = headerAlpha }
            )
            Spacer(modifier = Modifier.height(Dimens.spacingLg))
            MakeRoomStrip(step = step, focusIndex = focusIndex, animate = !reduced, onTapCard = onTapCard)
            Spacer(modifier = Modifier.height(Dimens.spacingLg))
            MakeRoomStatus(stage = step.stage, fadeMs = contentMs, onClose = onClose, onCancel = onCancel)
        }
    }
}

@Composable
private fun MakeRoomHeader(launchingTitle: String?, modifier: Modifier = Modifier) {
    val theme = LocalArgosyTheme.current
    val title = launchingTitle ?: stringResource(R.string.ui_sync_overlay_unknown_game)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(horizontal = Dimens.spacingLg)
    ) {
        Text(
            text = stringResource(R.string.ui_launch_overlay_makeroom_title, title),
            style = MaterialTheme.typography.headlineSmall,
            color = theme.textPrimary,
            textAlign = TextAlign.Center,
            maxLines = ComponentDefaults.LaunchOverlay.titleMaxLines,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(Dimens.spacingSm))
        Text(
            text = stringResource(R.string.ui_launch_overlay_makeroom_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = theme.textDim,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun MakeRoomStrip(
    step: LaunchStep.MakeRoom,
    focusIndex: Int,
    animate: Boolean,
    onTapCard: (Int) -> Unit
) {
    val choosing = step.stage == MakeRoomStage.CHOOSE
    val shown = remember(step.candidates, choosing, step.chosenTaskId) {
        if (choosing) step.candidates else step.candidates.filter { it.taskId == step.chosenTaskId }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(focusIndex, choosing) {
        if (choosing) listState.scrollToItemCentered(focusIndex, animate)
    }
    val slideMs = if (animate) Motion.durationSlide else 0
    LazyRow(
        state = listState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = Dimens.spacingLg),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingLg, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.Top
    ) {
        itemsIndexed(shown, key = { _, candidate -> candidate.taskId }) { index, candidate ->
            MakeRoomCard(
                candidate = candidate,
                focused = choosing && index == focusIndex,
                stage = step.stage,
                animate = animate,
                onClick = { onTapCard(index) },
                modifier = Modifier.animateItem(
                    fadeInSpec = null,
                    placementSpec = tween(slideMs, easing = Motion.argosyEase),
                    fadeOutSpec = tween(slideMs, easing = Motion.argosyEase)
                )
            )
        }
    }
}

@Composable
private fun MakeRoomCard(
    candidate: MakeRoomCandidate,
    focused: Boolean,
    stage: MakeRoomStage,
    animate: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusLg)
    val closed = stage == MakeRoomStage.CLOSED
    val closeMs = if (animate) Motion.durationContent else 0
    val scale by animateFloatAsState(
        targetValue = if (closed) ComponentDefaults.LaunchOverlay.makeRoomClosedScale else 1f,
        animationSpec = tween(closeMs, easing = Motion.argosyEase),
        label = "makeRoomCardScale"
    )
    val alpha by animateFloatAsState(
        targetValue = if (closed) ComponentDefaults.LaunchOverlay.makeRoomClosedAlpha else 1f,
        animationSpec = tween(closeMs, easing = Motion.argosyEase),
        label = "makeRoomCardAlpha"
    )
    val saturation by animateFloatAsState(
        targetValue = if (closed) ComponentDefaults.LaunchOverlay.makeRoomClosedSaturation else 1f,
        animationSpec = tween(closeMs, easing = Motion.argosyEase),
        label = "makeRoomCardSaturation"
    )
    val filter = remember(saturation) { ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(saturation) }) }
    Column(
        modifier = modifier
            .width(ComponentDefaults.LaunchOverlay.makeRoomCardWidthDp.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .clickableNoFocus(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(CARD_ASPECT)
                .argosyFocusIndicators(focused = focused, indicators = CARD_FOCUS, shape = shape)
                .clip(shape)
                .background(theme.surfaceRaised),
            contentAlignment = Alignment.Center
        ) {
            MakeRoomCardArt(candidate = candidate, filter = filter)
        }
        GameTitle(
            title = candidate.title ?: stringResource(R.string.ui_sync_overlay_unknown_game),
            titleStyle = MaterialTheme.typography.titleMedium,
            titleColor = if (focused) theme.textPrimary else theme.textDim
        )
        val savedAt = candidate.lastSavedAtMillis
        if (focused && savedAt != null) {
            val relative = formatRelativeTime(LocalContext.current, Instant.ofEpochMilli(savedAt).toString())
            Text(
                text = stringResource(R.string.ui_launch_overlay_makeroom_saved, relative),
                style = MaterialTheme.typography.labelMedium,
                color = theme.textDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MakeRoomCardArt(candidate: MakeRoomCandidate, filter: ColorFilter) {
    val snapshot = rememberTaskSnapshotRequest(candidate.snapshotPath, candidate.snapshotTakenAtMillis)
    if (snapshot != null) {
        AsyncImage(
            model = snapshot,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            colorFilter = filter,
            modifier = Modifier.fillMaxSize()
        )
        return
    }
    val coverPath = rememberResolvedCoverPath(candidate.gameId, null) ?: return
    AsyncImage(
        model = rememberFileImageModel(coverPath),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        colorFilter = filter,
        modifier = Modifier.fillMaxSize()
    )
}

@Composable
private fun MakeRoomStatus(
    stage: MakeRoomStage,
    fadeMs: Int,
    onClose: () -> Unit,
    onCancel: () -> Unit
) {
    AnimatedContent(
        targetState = stage,
        transitionSpec = { fadeIn(tween(fadeMs)) togetherWith fadeOut(tween(fadeMs)) },
        contentAlignment = Alignment.Center,
        label = "makeRoomStatus"
    ) { shown ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
        ) {
            when (shown) {
                MakeRoomStage.CHOOSE -> {
                    LaunchCancelButton(onClick = onCancel)
                    ActionButton(
                        label = stringResource(R.string.ui_launch_overlay_makeroom_close),
                        onClick = onClose,
                        primary = true,
                        glyph = InputButton.A
                    )
                }
                MakeRoomStage.SAVING -> {
                    CircularProgressIndicator(
                        modifier = Modifier.size(Dimens.iconMd),
                        color = MaterialTheme.colorScheme.primary
                    )
                    StatusText(stringResource(R.string.ui_launch_overlay_makeroom_saving))
                }
                MakeRoomStage.CLOSED -> {
                    Icon(
                        imageVector = Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = LocalLauncherTheme.current.semanticColors.success,
                        modifier = Modifier.size(Dimens.iconMd)
                    )
                    StatusText(stringResource(R.string.ui_launch_overlay_makeroom_closed))
                }
            }
        }
    }
}

@Composable
private fun StatusText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        color = LocalArgosyTheme.current.textPrimary
    )
}

private val CARD_FOCUS = FocusIndicators(halo = true, ring = true)
private const val CARD_ASPECT = 16f / 9f
