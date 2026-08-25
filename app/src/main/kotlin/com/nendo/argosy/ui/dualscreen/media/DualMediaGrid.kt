/**
 * DUAL-SCREEN COMPONENT - Media browser on the interactive display.
 * Rendered by the dual home in MEDIA_GRID mode; the other screen describes the focused title.
 */
package com.nendo.argosy.ui.dualscreen.media

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import com.nendo.argosy.ui.screens.media.MediaItemUi
import com.nendo.argosy.ui.screens.media.components.MediaPosterGrid
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme

/**
 * The library the viewer is browsing: the companion's chrome around the shared poster grid.
 */
@Composable
fun DualMediaGrid(
    items: List<MediaItemUi>,
    focusedIndex: Int,
    libraryLabel: String,
    onColumnsChanged: (Int) -> Unit,
    onItemTapped: (Int) -> Unit,
    onItemLongPressed: (Int) -> Unit,
    onPosterLoaded: (String, android.graphics.Bitmap) -> Unit,
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

        MediaPosterGrid(
            items = items,
            focusedIndex = focusedIndex,
            gridState = gridState,
            contentPadding = PaddingValues(
                start = Dimens.spacingLg,
                end = Dimens.spacingLg,
                bottom = Dimens.spacingXl
            ),
            onColumnsChanged = onColumnsChanged,
            onItemClick = onItemTapped,
            onItemLongClick = onItemLongPressed,
            onPosterLoaded = onPosterLoaded
        )
    }
}
