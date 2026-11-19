package com.nendo.argosy.ui.screens.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.nendo.argosy.R
import com.nendo.argosy.ui.components.GameCard
import com.nendo.argosy.ui.components.animateScrollToItemCentered
import com.nendo.argosy.ui.theme.reducedMotion
import com.nendo.argosy.ui.dualscreen.PresentationSlot
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalBoxArtStyle
import com.nendo.argosy.ui.util.clickableNoFocus

private const val FOCUSED_TILE_Z = 1f

private const val RESTING_TILE_Z = 0f

/**
 * What the presentation screen draws while Search is open on the control screen: recently played
 * covers while [isLanding], the game results after. [focusOnTop] is false while focus sits on the
 * control screen.
 */
data class SearchPresentationState(
    val query: String,
    val isSearching: Boolean,
    val isLanding: Boolean,
    val covers: List<SearchResultUi.Game>,
    val columns: Int,
    val spacingDp: Int,
    val focusIndex: Int,
    val focusOnTop: Boolean
) {
    val focusedCover: SearchResultUi.Game?
        get() = if (focusOnTop) covers.getOrNull(focusIndex) else null
}

internal fun SearchUiState.toPresentationState(): SearchPresentationState = SearchPresentationState(
    query = query,
    isSearching = isSearching,
    isLanding = isLanding,
    covers = coverGames,
    columns = presentationColumns,
    spacingDp = gridSpacingDp,
    focusIndex = previewIndex,
    focusOnTop = dualScreen && previewFocused
)

internal fun searchUsesPresentation(presentationShowsHints: Boolean, shownSlot: PresentationSlot): Boolean =
    presentationShowsHints &&
        shownSlot !is PresentationSlot.InGame &&
        shownSlot !is PresentationSlot.MediaPlayback

@Composable
fun SearchPresentation(
    state: SearchPresentationState,
    bottomInset: Dp,
    onTap: (Int) -> Unit
) {
    val captionShown = state.focusOnTop && state.focusedCover != null
    val gridPadding = PaddingValues(
        top = Dimens.headerHeight + Dimens.spacingSm,
        bottom = bottomInset + if (captionShown) Dimens.headerHeightLg else Dimens.spacingXl
    )
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Dimens.spacingXl)
        ) {
            when {
                state.covers.isNotEmpty() -> CoverGrid(state, gridPadding, onTap)
                state.isLanding -> PresentationMessage(
                    pluralStringResource(
                        R.plurals.library_search_presentation_min_query_hint,
                        SearchViewModel.MIN_QUERY_LENGTH,
                        SearchViewModel.MIN_QUERY_LENGTH
                    )
                )
                state.isSearching -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                else -> PresentationMessage(stringResource(R.string.library_search_presentation_no_results, state.query))
            }
        }
        if (state.isLanding && state.covers.isNotEmpty()) {
            Text(
                text = stringResource(R.string.library_search_presentation_heading_recently_played),
                style = MaterialTheme.typography.labelMedium,
                color = LocalArgosyTheme.current.textDim,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = Dimens.spacingXl, top = Dimens.spacingXl)
            )
        }
        if (captionShown) {
            FocusedCaption(
                game = state.focusedCover,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = bottomInset + Dimens.spacingMd)
            )
        }
    }
}

@Composable
private fun CoverGrid(state: SearchPresentationState, contentPadding: PaddingValues, onTap: (Int) -> Unit) {
    val gridState = rememberLazyGridState()
    val reduced = reducedMotion()
    LaunchedEffect(state.focusOnTop, state.focusIndex) {
        if (state.focusOnTop && state.focusIndex in state.covers.indices) {
            gridState.animateScrollToItemCentered(state.focusIndex, reduced)
        }
    }
    val spacing = state.spacingDp.dp
    val aspectRatio = LocalBoxArtStyle.current.aspectRatio
    LazyVerticalGrid(
        columns = GridCells.Fixed(state.columns),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(spacing),
        verticalArrangement = Arrangement.spacedBy(spacing)
    ) {
        itemsIndexed(state.covers, key = { _, game -> game.key }) { index, game ->
            val isFocused = state.focusOnTop && index == state.focusIndex
            GameCard(
                game = game.card,
                isFocused = isFocused,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(aspectRatio)
                    .zIndex(if (isFocused) FOCUSED_TILE_Z else RESTING_TILE_Z)
                    .clickableNoFocus { onTap(index) }
            )
        }
    }
}

@Composable
private fun FocusedCaption(game: SearchResultUi.Game?, modifier: Modifier = Modifier) {
    val theme = LocalArgosyTheme.current
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = game?.title.orEmpty(),
            style = MaterialTheme.typography.headlineSmall,
            color = theme.textPrimary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = game?.card?.platformDisplayName.orEmpty(),
            style = MaterialTheme.typography.titleSmall,
            color = theme.textDim,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PresentationMessage(message: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            color = LocalArgosyTheme.current.textDim
        )
    }
}
