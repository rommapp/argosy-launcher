package com.nendo.argosy.ui.components.resumeplayer

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.IntOffset
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.animateScrollToItemCentered
import com.nendo.argosy.ui.components.frostedSurface
import com.nendo.argosy.ui.components.nowplaying.QueueTrackRow
import com.nendo.argosy.ui.input.ModalInputEffect
import com.nendo.argosy.ui.primitives.PanelSectionHeader
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.reducedMotion
import com.nendo.argosy.ui.util.verticalEdgeFade
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.roundToInt

internal fun resumePanelRect(card: Rect, bottomLimit: Float, gapPx: Float, marginPx: Float): Rect? {
    val top = card.bottom + gapPx
    val bottom = bottomLimit - marginPx
    return if (bottom > top) Rect(card.left, top, card.right, bottom) else null
}

/**
 * The running order dropped from the player card while [ResumePlayerUiState.panelOpen] holds.
 * [cardBounds] and [bottomLimit] are in the coordinates of the full-page box this layer fills;
 * the panel runs from under the card to above [bottomLimit]. The panel takes the input stack.
 */
@Composable
fun ResumePlayerPanelLayer(
    controller: ResumePlayerController,
    state: ResumePlayerUiState,
    cardBounds: Rect?,
    bottomLimit: Float
) {
    val handler = remember(controller) { controller.createPanelInputHandler() }
    ModalInputEffect(active = state.listFocused, handler = handler)
    if (!state.panelOpen || cardBounds == null) return
    val density = LocalDensity.current
    val gap = with(density) { Dimens.spacingXs.toPx() }
    val margin = with(density) { Dimens.spacingMd.toPx() }
    val rect = resumePanelRect(cardBounds, bottomLimit, gap, margin) ?: return
    RunningOrderPanel(
        controller = controller,
        state = state,
        modifier = Modifier
            .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
            .size(with(density) { DpSize(rect.width.toDp(), rect.height.toDp()) })
    )
}

/**
 * Closes the open panel on a tap anywhere outside the player card and the panel, and swallows
 * that tap. Applied to the same full-page box [ResumePlayerPanelLayer] fills.
 */
@Composable
fun Modifier.dismissesResumePanelOutside(
    controller: ResumePlayerController,
    state: ResumePlayerUiState,
    cardBounds: Rect?,
    bottomLimit: Float
): Modifier {
    val density = LocalDensity.current
    val gap = with(density) { Dimens.spacingXs.toPx() }
    val margin = with(density) { Dimens.spacingMd.toPx() }
    val card = cardBounds.takeIf { state.panelOpen }
    return pointerInput(controller, card, bottomLimit, gap, margin) {
        if (card == null) return@pointerInput
        val kept = listOfNotNull(card, resumePanelRect(card, bottomLimit, gap, margin))
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            if (kept.any { it.contains(down.position) }) return@awaitEachGesture
            down.consume()
            controller.dismissPanel()
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
                event.changes.forEach { it.consume() }
            } while (event.changes.any { it.pressed })
        }
    }
}

@Composable
private fun RunningOrderPanel(
    controller: ResumePlayerController,
    state: ResumePlayerUiState,
    modifier: Modifier = Modifier
) {
    val tracks = state.playback.tracks
    val listState = rememberLazyListState()
    val reduced = reducedMotion()
    val anchor = state.listFocus
    LaunchedEffect(anchor, tracks.size) {
        snapshotFlow { listState.layoutInfo.viewportSize.height }
            .collectLatest { if (anchor in tracks.indices) listState.animateScrollToItemCentered(anchor, reduced) }
    }
    Column(
        modifier = modifier
            .frostedSurface(RoundedCornerShape(Dimens.radiusLg))
            .pointerInput(controller) {
                awaitPointerEventScope {
                    while (true) {
                        awaitPointerEvent(PointerEventPass.Initial)
                        controller.onPanelTouched()
                    }
                }
            }
            .padding(Dimens.spacingSm)
    ) {
        PanelSectionHeader(
            title = stringResource(
                R.string.running_player_queue_header,
                state.playback.index + 1,
                state.playback.count
            ),
            modifier = Modifier.padding(horizontal = Dimens.spacingSm, vertical = Dimens.spacingXs)
        )
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalEdgeFade(listState, fadeHeight = Dimens.spacingLg)
        ) {
            itemsIndexed(tracks, key = { position, track -> "$position:${track.id}" }) { position, track ->
                QueueTrackRow(
                    number = position + 1,
                    track = track,
                    isCurrent = position == state.playback.index,
                    isFocused = position == state.listFocus,
                    currentDescription = stringResource(R.string.running_player_queue_playing),
                    onClick = { controller.playRow(position) }
                )
            }
        }
    }
}
