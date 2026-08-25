/**
 * DUAL-SCREEN COMPONENT - Media browser on the interactive display.
 * Rendered by the dual home in MEDIA_GRID mode; the other screen describes the focused title.
 */
package com.nendo.argosy.ui.dualscreen.media

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.nendo.argosy.ui.screens.media.MediaItemUi
import com.nendo.argosy.ui.screens.media.components.MediaPosterCard
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme

/**
 * The library the viewer is browsing, as a grid of posters.
 *
 * [columns] is reported back rather than fixed here, because the cursor moves by rows and the only
 * place that knows how many tiles make a row is the layout that measured them.
 */
@Composable
fun DualMediaGrid(
    items: List<MediaItemUi>,
    focusedIndex: Int,
    libraryLabel: String,
    onColumnsChanged: (Int) -> Unit,
    onItemTapped: (Int) -> Unit,
    onItemLongPressed: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val theme = LocalArgosyTheme.current
    val gridState = rememberLazyGridState()

    LaunchedEffect(focusedIndex, items.size) {
        if (items.isNotEmpty()) {
            gridState.animateScrollToItem(focusedIndex.coerceIn(0, items.lastIndex))
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        Text(
            text = libraryLabel,
            style = MaterialTheme.typography.titleMedium,
            color = theme.focusAccent,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Dimens.spacingMd, bottom = Dimens.spacingSm)
        )

        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Nothing in this library yet",
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.textDim
                )
            }
            return@Column
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val tileWidth = Dimens.mediaPosterWidth + Dimens.spacingMd
            val columns = ((maxWidth - Dimens.spacingLg) / tileWidth).toInt().coerceAtLeast(1)
            LaunchedEffect(columns) { onColumnsChanged(columns) }

            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                state = gridState,
                contentPadding = PaddingValues(
                    start = Dimens.spacingLg,
                    end = Dimens.spacingLg,
                    bottom = Dimens.spacingXl
                ),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
            ) {
                itemsIndexed(items, key = { _, item -> item.itemId }) { index, item ->
                    MediaPosterCard(
                        item = item,
                        isFocused = index == focusedIndex,
                        onClick = { onItemTapped(index) },
                        onLongClick = { onItemLongPressed(index) }
                    )
                }
            }
        }
    }
}
