package com.nendo.argosy.ui.screens.media.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.ui.screens.media.MediaItemUi
import com.nendo.argosy.ui.theme.Dimens

/**
 * Titles worth going to next. A press swaps the screen to one, so every tile here is somewhere the
 * viewer can actually get to; nothing is offered that is not in the library.
 *
 * The tiles are the browse grid's own, not a second kind of poster: a film looks the same wherever
 * it is shown.
 */
@Composable
fun MediaSimilarRail(
    titles: List<MediaItemUi>,
    focusedIndex: Int,
    isSectionFocused: Boolean,
    onSelect: (Int) -> Unit,
    onOpen: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    FocusedScroll(listState = listState, focusedIndex = focusedIndex)

    LazyRow(
        state = listState,
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
        contentPadding = PaddingValues(
            start = Dimens.spacingLg,
            end = Dimens.spacingXl,
            top = Dimens.spacingSm,
            bottom = Dimens.spacingSm
        )
    ) {
        itemsIndexed(items = titles, key = { _, title -> title.itemId }) { index, title ->
            MediaPosterCard(
                item = title,
                isFocused = isSectionFocused && index == focusedIndex,
                onClick = {
                    onSelect(index)
                    onOpen(index)
                },
                onLongClick = {
                    onSelect(index)
                    onOpen(index)
                },
                scaleOverride = 1f
            )
        }
    }
}
