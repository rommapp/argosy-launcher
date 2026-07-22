package com.nendo.argosy.ui.screens.gamedetail.modals

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.Modal
import com.nendo.argosy.ui.screens.gamedetail.CoverCandidate
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.util.clickableNoFocus

const val COVER_PICKER_COLUMNS = 3

/**
 * Cover art picker. Renders large tiles so the user can judge image quality, and surfaces
 * each candidate's pixel dimensions when the server reports them.
 */
@Composable
fun CoverPickerModal(
    gameTitle: String,
    covers: List<CoverCandidate>,
    focusIndex: Int,
    isLoading: Boolean,
    errorMessage: String?,
    onSelect: (CoverCandidate) -> Unit,
    onDismiss: () -> Unit
) {
    val gridState = rememberLazyGridState()

    LaunchedEffect(focusIndex, covers.size) {
        if (covers.isEmpty()) return@LaunchedEffect
        val info = gridState.layoutInfo
        val rowHeight = info.visibleItemsInfo.firstOrNull()?.size?.height ?: 0
        val viewportHeight = info.viewportEndOffset - info.viewportStartOffset
        val centeringOffset = if (rowHeight > 0) -((viewportHeight / 2) - (rowHeight / 2)) else 0
        gridState.animateScrollToItem(
            focusIndex.coerceIn(0, covers.lastIndex),
            centeringOffset
        )
    }

    Modal(
        title = "COVER ART",
        subtitle = gameTitle,
        baseWidth = 560.dp,
        onDismiss = onDismiss,
        footerHints = listOf(
            InputButton.DPAD to "Navigate",
            InputButton.A to "Select",
            InputButton.B to "Cancel"
        )
    ) {
        when {
            isLoading -> LoadingState()
            errorMessage != null -> MessageState(errorMessage)
            covers.isEmpty() -> MessageState("No cover art found for this title.")
            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(COVER_PICKER_COLUMNS),
                state = gridState,
                horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
                verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(covers, key = { _, cover -> cover.url }) { index, cover ->
                    CoverTile(
                        cover = cover,
                        isFocused = index == focusIndex,
                        onClick = { onSelect(cover) }
                    )
                }
            }
        }
    }
}

@Composable
private fun CoverTile(
    cover: CoverCandidate,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(COVER_ASPECT)
                .clip(RoundedCornerShape(Dimens.radiusMd))
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                .then(
                    if (isFocused) {
                        Modifier.border(
                            width = 3.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(Dimens.radiusMd)
                        )
                    } else {
                        Modifier
                    }
                )
                .clickableNoFocus(onClick = onClick)
        ) {
            AsyncImage(
                model = cover.thumbUrl ?: cover.url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(COVER_ASPECT)
            )
        }
        cover.dimensionLabel?.let { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (isFocused) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(top = Dimens.spacingXs)
            )
        }
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier.fillMaxWidth().height(160.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun MessageState(message: String) {
    Box(
        modifier = Modifier.fillMaxWidth().height(160.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = Dimens.spacingLg)
        )
    }
}

private const val COVER_ASPECT = 2f / 3f
