package com.nendo.argosy.ui.screens.media.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.nendo.argosy.ui.screens.media.MediaItemUi
import com.nendo.argosy.ui.theme.Dimens

/**
 * One library's posters as a grid, shared by the single-screen media library and the companion's
 * media browser so the two surfaces cannot drift apart.
 *
 * The column count is reported back rather than fixed here, because the cursor moves by rows and
 * the only place that knows how many tiles make a row is the layout that measured them.
 */
@Composable
fun MediaPosterGrid(
    items: List<MediaItemUi>,
    focusedIndex: Int,
    gridState: LazyGridState,
    contentPadding: PaddingValues,
    onColumnsChanged: (Int) -> Unit,
    onItemClick: (Int) -> Unit,
    onItemLongClick: (Int) -> Unit,
    onPosterLoaded: (String, android.graphics.Bitmap) -> Unit,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val tileWidth = Dimens.mediaPosterWidth + Dimens.spacingMd
        val columns = ((maxWidth - Dimens.spacingLg) / tileWidth).toInt().coerceAtLeast(1)
        LaunchedEffect(columns) { onColumnsChanged(columns) }

        LazyVerticalGrid(
            columns = GridCells.Fixed(columns),
            state = gridState,
            contentPadding = contentPadding,
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
        ) {
            itemsIndexed(items, key = { _, item -> item.itemId }) { index, item ->
                MediaPosterCard(
                    item = item,
                    isFocused = index == focusedIndex,
                    onClick = { onItemClick(index) },
                    onLongClick = { onItemLongClick(index) },
                    onPosterLoaded = onPosterLoaded
                )
            }
        }
    }
}
