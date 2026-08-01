package com.nendo.argosy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
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
import com.nendo.argosy.ui.common.rememberFileImageModel
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.util.clickableNoFocus

data class TilePickerEntry(
    val gameId: Long,
    val title: String,
    val platformName: String,
    val coverPath: String?
)

/**
 * Chooses what goes in an empty cell. Only installed games are offered, because a curated grid is
 * somewhere you reach for something to play rather than something to fetch.
 *
 * Owns no state: the query and the focus index belong to the caller, so the gamepad drives it
 * through the same input handler as everything else on the screen.
 */
@Composable
fun HomeTilePickerModal(
    entries: List<TilePickerEntry>,
    query: String,
    focusIndex: Int,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    val listState = rememberLazyListState()
    FocusedScroll(listState = listState, focusedIndex = focusIndex)

    Modal(
        title = "ADD TO GRID",
        subtitle = if (query.isBlank()) null else "Matching \"$query\"",
        baseWidth = Dimens.modalWidthLg,
        onDismiss = onDismiss
    ) {
        if (entries.isEmpty()) {
            Text(
                text = if (query.isBlank()) {
                    "No installed games to add"
                } else {
                    "Nothing matches that search"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = LocalArgosyTheme.current.textDim,
                modifier = Modifier.padding(Dimens.spacingMd)
            )
            return@Modal
        }
        LazyColumn(
            state = listState,
            verticalArrangement = Arrangement.spacedBy(Dimens.listGap)
        ) {
            itemsIndexed(entries, key = { _, entry -> entry.gameId }) { index, entry ->
                TilePickerRow(
                    entry = entry,
                    isFocused = index == focusIndex,
                    onClick = { onSelect(entry.gameId) }
                )
            }
        }
    }
}

@Composable
private fun TilePickerRow(
    entry: TilePickerEntry,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusControl)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .argosyFocusIndicators(
                focused = isFocused,
                indicators = FocusIndicators.ListRow,
                shape = shape
            )
            .clickableNoFocus(onClick = onClick)
            .padding(Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        Box(
            modifier = Modifier
                .width(Dimens.iconXl)
                .aspectRatio(COVER_ASPECT)
                .clip(RoundedCornerShape(Dimens.radiusSm))
                .background(theme.surfaceRaised)
        ) {
            val cover = rememberFileImageModel(entry.coverPath)
            if (cover != null) {
                AsyncImage(
                    model = cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = entry.title,
                style = MaterialTheme.typography.bodyMedium,
                color = theme.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = entry.platformName,
                style = MaterialTheme.typography.labelSmall,
                color = theme.textDim,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private const val COVER_ASPECT = 0.7f
