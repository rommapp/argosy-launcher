package com.nendo.argosy.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.IntOffset
import com.nendo.argosy.domain.model.GridCell
import com.nendo.argosy.domain.model.HomeTile
import com.nendo.argosy.ui.screens.home.GameDownloadIndicator
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.Motion

/**
 * The custom grid as a whole: the page being shown, the stub that adds another, and the dots that
 * say where in the set you are. Both home surfaces render this, so a page looks and moves the same
 * on a phone as it does on a companion display.
 */
@Composable
fun CustomGridSurface(
    state: CustomGridState,
    contentFor: (HomeTile) -> CustomGridTileContent?,
    laneCount: Int,
    onCellTap: (GridCell) -> Unit,
    onShapeResolved: (Int, Int) -> Unit,
    onAddPage: () -> Unit,
    modifier: Modifier = Modifier,
    onTileLongPress: ((GridCell) -> Unit)? = null,
    downloadIndicatorFor: (Long) -> GameDownloadIndicator = { GameDownloadIndicator.NONE },
    onCoverLoadFailed: ((Long, String) -> Unit)? = null,
    onCoverLoaded: ((Long, android.graphics.Bitmap) -> Unit)? = null
) {
    Column(modifier = modifier) {
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
                    laneCount = laneCount,
                    focusedCell = state.cell,
                    onCellTap = onCellTap,
                    onShapeResolved = onShapeResolved,
                    onTileLongPress = onTileLongPress,
                    editModeLabel = state.editLabel,
                    overlappedTileIds = state.overlappedTileIds,
                    editingTileId = state.editingTileId,
                    downloadIndicatorFor = downloadIndicatorFor,
                    onCoverLoadFailed = onCoverLoadFailed,
                    onCoverLoaded = onCoverLoaded,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        CustomGridPageDots(
            pageCount = state.pageCount,
            currentPage = state.page,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(vertical = Dimens.spacingSm)
        )
    }
}
