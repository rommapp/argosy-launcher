package com.nendo.argosy.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.nendo.argosy.R
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.domain.model.GridAxis
import com.nendo.argosy.domain.model.GridCell
import com.nendo.argosy.domain.model.HomeTile
import com.nendo.argosy.domain.model.ResolvedGridShape
import com.nendo.argosy.ui.screens.home.GameDownloadIndicator
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.util.clickableNoFocus

/**
 * The custom grid as a whole: the page being shown, the stub that adds another, and the dots that
 * say where in the set you are. Both home surfaces render this, so a page looks and moves the same
 * on a phone as it does on a companion display.
 */
@Composable
fun CustomGridSurface(
    state: CustomGridState,
    contentFor: (HomeTile) -> CustomGridTileContent?,
    columns: GridAxis,
    rows: GridAxis,
    onCellTap: (GridCell) -> Unit,
    onShapeResolved: (ResolvedGridShape) -> Unit,
    onAddPage: () -> Unit,
    onHiddenTilesTap: () -> Unit,
    modifier: Modifier = Modifier,
    onTileLongPress: ((GridCell) -> Unit)? = null,
    onBadgeTap: ((Int) -> Unit)? = null,
    onBandTap: (() -> Unit)? = null,
    onSwipePage: ((Int) -> Unit)? = null,
    onTileDrag: ((GridCell) -> Unit)? = null,
    onTileResize: ((GridCell) -> Unit)? = null,
    onToggleEditMode: (() -> Unit)? = null,
    onCommitEdit: (() -> Unit)? = null,
    onPlaybackPosition: (String, Long) -> Unit = { _, _ -> },
    onTakeAudio: () -> Unit = {},
    onReleaseAudio: () -> Unit = {},
    showEmptySlots: Boolean = true,
    showCursor: Boolean = true,
    downloadIndicatorFor: (Long) -> GameDownloadIndicator = { GameDownloadIndicator.NONE },
    onCoverLoadFailed: ((Long, String) -> Unit)? = null,
    onCoverLoaded: ((Long, android.graphics.Bitmap) -> Unit)? = null,
    onPosterLoaded: ((String, android.graphics.Bitmap) -> Unit)? = null,
    useBoxArt: Boolean = false
) {
    if (state.isScrolling) {
        Column(modifier = modifier) {
            CustomGridScrollCanvas(
                state = state,
                contentFor = contentFor,
                columns = columns,
                rows = rows,
                onCellTap = onCellTap,
                onShapeResolved = onShapeResolved,
                onTileLongPress = onTileLongPress,
                onBadgeTap = onBadgeTap,
                onBandTap = onBandTap,
                onTileDrag = onTileDrag,
                onTileResize = onTileResize,
                onToggleEditMode = onToggleEditMode,
                onCommitEdit = onCommitEdit,
                onPlaybackPosition = onPlaybackPosition,
                onTakeAudio = onTakeAudio,
                onReleaseAudio = onReleaseAudio,
                showEmptySlots = showEmptySlots,
                showCursor = showCursor,
                downloadIndicatorFor = downloadIndicatorFor,
                onCoverLoadFailed = onCoverLoadFailed,
                onCoverLoaded = onCoverLoaded,
                onPosterLoaded = onPosterLoaded,
                useBoxArt = useBoxArt,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )
            CustomGridFooterBand()
        }
        return
    }
    val swipeThresholdPx = with(LocalDensity.current) {
        ComponentDefaults.CustomGrid.swipePageThresholdDp.dp.toPx()
    }
    val swipeModifier = if (onSwipePage == null || state.isEditing) {
        Modifier
    } else {
        Modifier.pointerInput(onSwipePage) {
            var dragged = 0f
            detectHorizontalDragGestures(
                onDragStart = { dragged = 0f },
                onDragEnd = {
                    if (kotlin.math.abs(dragged) >= swipeThresholdPx) {
                        onSwipePage(if (dragged < 0) 1 else -1)
                    }
                }
            ) { _, amount -> dragged += amount }
        }
    }

    Column(modifier = modifier.then(swipeModifier)) {
        AnimatedContent(
            targetState = state.page,
            transitionSpec = {
                val forward = targetState > initialState
                val slide = tween<IntOffset>(
                    durationMillis = Motion.durationSlide,
                    easing = Motion.argosyEase
                )
                slideInHorizontally(slide) { width ->
                    if (forward) width else -width
                } togetherWith slideOutHorizontally(slide) { width ->
                    if (forward) -width else width
                }
            },
            label = "custom-grid-page",
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) { page ->
            if (page >= state.pageCount) {
                CustomGridAddPage(isFocused = true, onClick = onAddPage)
            } else {
                HomeCustomGridPage(
                    tiles = state.tilesOnPage(page),
                    contentFor = contentFor,
                    columns = columns,
                    rows = rows,
                    focusedCell = state.cell,
                    onCellTap = onCellTap,
                    onShapeResolved = onShapeResolved,
                    showEmptyCells = showEmptySlots,
                    showCursor = showCursor,
                    onTileLongPress = onTileLongPress,
                    onTileDrag = onTileDrag,
                    onTileResize = onTileResize,
                    onToggleEditMode = onToggleEditMode,
                    onCommitEdit = onCommitEdit,
                    isResizing = state.editMode == TileEditMode.RESIZE,
                    tilePlayback = state.tilePlayback,
                    engagedTileId = state.engagedTileId,
                    engagedPaused = state.engagedPaused,
                    engagedSeekTicks = state.engagedSeekTicks,
                    engagedIndex = state.engagedIndex,
                    onBadgeTap = onBadgeTap,
                    onBandTap = onBandTap,
                    playbackPositions = state.playbackPositions,
                    onPlaybackPosition = onPlaybackPosition,
                    onTakeAudio = onTakeAudio,
                    onReleaseAudio = onReleaseAudio,
                    editModeLabel = state.editLabelRes?.let { stringResource(it) },
                    overlappedTileIds = state.overlappedTileIds,
                    editingTileId = state.editingTileId,
                    downloadIndicatorFor = downloadIndicatorFor,
                    onCoverLoadFailed = onCoverLoadFailed,
                    onCoverLoaded = onCoverLoaded,
                    onPosterLoaded = onPosterLoaded,
                    useBoxArt = useBoxArt,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        CustomGridFooterBand {
            CustomGridPageDots(pageCount = state.pageCount, currentPage = state.page)
            val hiddenCount = state.hiddenTiles.size
            if (hiddenCount > 0 && !state.isEditing) {
                CustomGridHiddenMarker(count = hiddenCount, onClick = onHiddenTilesTap)
            }
        }
    }
}

@Composable
private fun CustomGridFooterBand(content: @Composable RowScope.() -> Unit = {}) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Dimens.spacingSm),
        contentAlignment = Alignment.Center
    ) {
        CustomGridHiddenMarker(count = 0, onClick = {}, modifier = Modifier.alpha(0f), enabled = false)
        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
            content = content
        )
    }
}

@Composable
private fun CustomGridHiddenMarker(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusSm)
    Row(
        modifier = modifier
            .clip(shape)
            .background(theme.surfaceRaised)
            .clickableNoFocus(enabled = enabled, onClick = onClick)
            .padding(horizontal = Dimens.spacingSm, vertical = Dimens.spacingXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
    ) {
        Icon(
            imageVector = Icons.Outlined.VisibilityOff,
            contentDescription = null,
            tint = theme.textDim,
            modifier = Modifier.size(Dimens.iconSm)
        )
        Text(
            text = pluralStringResource(R.plurals.ui_custom_grid_hidden_count, count, count),
            style = MaterialTheme.typography.labelSmall,
            color = theme.textDim
        )
    }
}
