package com.nendo.argosy.ui.screens.media.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import coil.compose.AsyncImage
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.screens.media.MediaItemUi
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.util.clickableNoFocus

/**
 * Titles worth going to next. A press swaps the screen to one, so every tile here is somewhere the
 * viewer can actually get to; nothing is offered that is not in the library.
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
        contentPadding = PaddingValues(horizontal = Dimens.spacingLg)
    ) {
        itemsIndexed(items = titles, key = { _, title -> title.itemId }) { index, title ->
            MediaSimilarTile(
                title = title,
                isFocused = isSectionFocused && index == focusedIndex,
                onClick = {
                    onSelect(index)
                    onOpen(index)
                }
            )
        }
    }
}

@Composable
private fun MediaSimilarTile(
    title: MediaItemUi,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusMd)
    Column(
        modifier = Modifier
            .width(Dimens.mediaSimilarTileWidth)
            .clickableNoFocus(onClick = onClick)
            .padding(vertical = Dimens.spacingXs),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs)
    ) {
        Box(
            modifier = Modifier
                .width(Dimens.mediaSimilarTileWidth)
                .height(Dimens.mediaSimilarTileHeight)
                .clip(shape)
                .argosyFocusIndicators(
                    focused = isFocused,
                    indicators = FocusIndicators.Tile,
                    shape = shape
                )
                .background(theme.surfaceRaised)
        ) {
            if (title.posterUrl.isNotEmpty()) {
                AsyncImage(
                    model = title.posterUrl,
                    contentDescription = title.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(shape)
                )
            }
        }

        Text(
            text = title.title,
            style = MaterialTheme.typography.labelMedium,
            color = if (isFocused) theme.textPrimary else theme.textDim,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )

        title.year?.let { year ->
            Text(
                text = year.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = theme.textMute,
                maxLines = 1
            )
        }
    }
}
