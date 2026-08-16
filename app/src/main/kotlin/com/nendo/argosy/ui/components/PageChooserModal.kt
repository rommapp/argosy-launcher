package com.nendo.argosy.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nendo.argosy.ui.common.rememberFileImageModel
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.util.clickableNoFocus

private val PREVIEW_SIZE = ComponentDefaults.PageChooser.previewSizeDp.dp
private val ROW_HEIGHT = ComponentDefaults.PageChooser.rowHeightDp.dp
private val MODAL_WIDTH = ComponentDefaults.PageChooser.modalWidthDp.dp

/**
 * Chooses what a curated page shows or plays. One modal serves both because the shape of the
 * question is the same: a list of sources, then a list of things from the chosen source.
 */
@Composable
fun PageChooserModal(
    state: PageChooserState,
    onSelect: (Int) -> Unit,
    onQueryChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    val listState = rememberLazyListState()

    LaunchedEffect(state.focusIndex, state.entries.size) {
        if (state.entries.isEmpty()) return@LaunchedEffect
        listState.animateScrollToItem(state.focusIndex.coerceIn(0, state.entries.lastIndex))
    }

    Modal(
        title = state.title.uppercase(),
        subtitle = state.subtitle,
        baseWidth = MODAL_WIDTH,
        onDismiss = onDismiss,
        footerHints = listOf(
            InputButton.DPAD to "Navigate",
            InputButton.A to "Choose",
            InputButton.B to "Back"
        )
    ) {
        if (state.gameTitle != null && state.gameId == null) {
            ModalSearchField(
                query = state.query,
                onQueryChange = onQueryChange,
                placeholder = "Search games...",
                autoFocus = false,
                modifier = Modifier.padding(bottom = Dimens.spacingSm)
            )
        }

        when {
            state.isLoading -> Text(
                text = "Loading...",
                style = MaterialTheme.typography.bodyMedium,
                color = theme.textDim,
                modifier = Modifier.padding(Dimens.spacingMd)
            )

            state.entries.isEmpty() -> Text(
                text = "Nothing here to choose from.",
                style = MaterialTheme.typography.bodyMedium,
                color = theme.textDim,
                modifier = Modifier.padding(Dimens.spacingMd)
            )

            else -> LazyColumn(
                state = listState,
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(state.entries) { index, entry ->
                    if (entry.isHeader) {
                        Text(
                            text = entry.label.uppercase(),
                            style = MaterialTheme.typography.labelSmall,
                            color = theme.textDim,
                            modifier = Modifier.padding(
                                top = Dimens.spacingSm,
                                bottom = Dimens.spacingXs,
                                start = Dimens.spacingSm
                            )
                        )
                    } else {
                        PageChooserRow(
                            entry = entry,
                            isFocused = index == state.focusIndex,
                            onClick = { onSelect(index) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PageChooserRow(
    entry: PageChooserEntry,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    val shape = RoundedCornerShape(Dimens.radiusControl)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT)
            .clip(shape)
            .background(if (isFocused) theme.focusAccent.copy(alpha = 0.25f) else theme.surfaceRaised)
            .clickableNoFocus(onClick = onClick)
            .padding(horizontal = Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        if (entry.previewPath != null) {
            Box(
                modifier = Modifier
                    .size(PREVIEW_SIZE)
                    .clip(RoundedCornerShape(Dimens.radiusSm))
                    .background(theme.surfaceBase)
            ) {
                AsyncImage(
                    model = rememberFileImageModel(entry.previewPath),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(PREVIEW_SIZE)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.label,
                style = MaterialTheme.typography.bodyLarge,
                color = theme.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (entry.subtitle != null) {
                Text(
                    text = entry.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.textDim,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
