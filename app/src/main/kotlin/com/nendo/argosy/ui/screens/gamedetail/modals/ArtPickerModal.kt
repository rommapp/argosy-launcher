package com.nendo.argosy.ui.screens.gamedetail.modals

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.nendo.argosy.R
import com.nendo.argosy.data.model.ArtSlot
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.Modal
import com.nendo.argosy.ui.components.ModalSearchField
import com.nendo.argosy.ui.components.animateScrollToItemCentered
import com.nendo.argosy.ui.primitives.FocusIndicators
import com.nendo.argosy.ui.primitives.argosyFocusIndicators
import com.nendo.argosy.ui.screens.gamedetail.ArtCandidate
import com.nendo.argosy.ui.screens.gamedetail.ArtPickerConfig
import com.nendo.argosy.ui.screens.gamedetail.PICKABLE_ART_SLOTS
import com.nendo.argosy.ui.screens.gamedetail.components.OptionItem
import com.nendo.argosy.ui.screens.gamedetail.pickerConfig
import com.nendo.argosy.ui.screens.gamedetail.stepped
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.generated.ColorTokens
import com.nendo.argosy.ui.theme.generated.ComponentDefaults
import com.nendo.argosy.ui.theme.generated.DimensionTokens
import com.nendo.argosy.ui.util.clickableNoFocus
import kotlin.math.ceil

@Composable
fun ArtPickerModal(
    gameTitle: String,
    slot: ArtSlot,
    candidates: List<ArtCandidate>,
    focusIndex: Int,
    isLoading: Boolean,
    errorMessage: String?,
    canSearch: Boolean,
    query: String,
    searchFocused: Boolean,
    keyboardOpen: Boolean,
    onQueryChange: (String) -> Unit,
    onOpenKeyboard: () -> Unit,
    onCloseKeyboard: () -> Unit,
    onSearch: () -> Unit,
    onChooseFile: () -> Unit,
    onSelect: (ArtCandidate) -> Unit,
    onSelectSlot: (ArtSlot) -> Unit,
    onDismiss: () -> Unit
) {
    val config = slot.pickerConfig
    val gridState = rememberLazyGridState()

    LaunchedEffect(focusIndex, candidates.size) {
        if (candidates.isNotEmpty()) {
            gridState.animateScrollToItemCentered(focusIndex.coerceIn(0, candidates.lastIndex))
        }
    }

    val slotHint = stringResource(R.string.gamedetail_art_picker_footer_slot)
    val searchHint = stringResource(R.string.gamedetail_art_picker_footer_search)
    val fileHint = stringResource(R.string.gamedetail_art_picker_footer_from_file)

    Modal(
        title = stringResource(R.string.gamedetail_artwork_title),
        subtitle = gameTitle,
        baseWidth = DimensionTokens.Layout.modalWidthXl.dp,
        onDismiss = onDismiss,
        footerHints = listOfNotNull(
            InputButton.LB_RB to slotHint,
            (InputButton.X to searchHint).takeIf { canSearch },
            InputButton.Y to fileHint
        ),
        onFooterHintClick = { button ->
            when (button) {
                InputButton.X -> onSearch()
                InputButton.Y -> onChooseFile()
                InputButton.LB_RB -> onSelectSlot(slot.stepped(1))
                else -> {}
            }
        }
    ) {
        ArtSlotTabs(
            selected = slot,
            onSelect = onSelectSlot,
            modifier = Modifier.padding(bottom = Dimens.spacingSm)
        )
        if (canSearch) {
            ModalSearchField(
                query = query,
                onQueryChange = onQueryChange,
                keyboardOpen = keyboardOpen,
                onOpenKeyboard = onOpenKeyboard,
                onCloseKeyboard = onCloseKeyboard,
                focused = searchFocused,
                placeholder = stringResource(R.string.gamedetail_art_picker_search_placeholder),
                onSearch = onSearch,
                modifier = Modifier.padding(bottom = Dimens.spacingSm)
            )
        }
        when {
            isLoading -> LoadingState()
            candidates.isEmpty() -> ChooseFileState(
                message = errorMessage ?: if (canSearch) {
                    stringResource(R.string.gamedetail_art_picker_empty)
                } else {
                    stringResource(R.string.gamedetail_art_picker_search_unavailable)
                },
                isFocused = !searchFocused,
                onChooseFile = onChooseFile
            )
            else -> {
                errorMessage?.let { message ->
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = Dimens.spacingSm)
                    )
                }
                LazyVerticalGrid(
                    columns = GridCells.Fixed(config.columns),
                    state = gridState,
                    horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
                    verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    itemsIndexed(candidates, key = { _, candidate -> candidate.source }) { index, candidate ->
                        ArtTile(
                            candidate = candidate,
                            slot = slot,
                            config = config,
                            isFocused = !searchFocused && index == focusIndex,
                            onClick = { onSelect(candidate) }
                        )
                    }
                }
            }
        }
    }
}

@get:StringRes
private val ArtSlot.tabLabelRes: Int
    get() = when (this) {
        ArtSlot.COVER -> R.string.gamedetail_artwork_row_cover
        ArtSlot.BACKGROUND -> R.string.gamedetail_artwork_row_background
        ArtSlot.LOGO -> R.string.gamedetail_artwork_row_logo
        ArtSlot.BOX_3D -> R.string.gamedetail_artwork_row_box_3d
        ArtSlot.BOX_SPINE, ArtSlot.BOX_BACK -> error("$this has no artwork picker tab")
    }

@get:StringRes
private val ArtSlot.revertLabelRes: Int
    get() = when (this) {
        ArtSlot.COVER -> R.string.gamedetail_artwork_row_revert_cover
        ArtSlot.BACKGROUND -> R.string.gamedetail_artwork_row_revert_background
        ArtSlot.LOGO -> R.string.gamedetail_artwork_row_revert_logo
        ArtSlot.BOX_3D -> R.string.gamedetail_artwork_row_revert_box_3d
        ArtSlot.BOX_SPINE, ArtSlot.BOX_BACK -> error("$this has no artwork picker tab")
    }

@Composable
private fun ArtSlotTabs(
    selected: ArtSlot,
    onSelect: (ArtSlot) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        PICKABLE_ART_SLOTS.forEach { slot ->
            val isSelected = slot == selected
            val shape = RoundedCornerShape(Dimens.radiusLg)
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .weight(1f)
                    .clip(shape)
                    .background(
                        if (isSelected) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)
                        }
                    )
                    .clickableNoFocus { onSelect(slot) }
                    .padding(vertical = Dimens.spacingSm)
            ) {
                Text(
                    text = stringResource(slot.tabLabelRes),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
        }
    }
}

@get:StringRes
val ArtSlot.fileBrowserTitleRes: Int
    get() = when (this) {
        ArtSlot.COVER -> R.string.gamedetail_art_picker_file_browser_title_cover
        ArtSlot.BACKGROUND -> R.string.gamedetail_art_picker_file_browser_title_background
        ArtSlot.LOGO -> R.string.gamedetail_art_picker_file_browser_title_logo
        ArtSlot.BOX_3D -> R.string.gamedetail_art_picker_file_browser_title_box_3d
        ArtSlot.BOX_SPINE, ArtSlot.BOX_BACK -> error("$this has no artwork picker tab")
    }

@Composable
private fun ArtTile(
    candidate: ArtCandidate,
    slot: ArtSlot,
    config: ArtPickerConfig,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(Dimens.radiusMd)
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(config.tileAspectRatio)
                .clip(shape)
                .then(
                    if (config.checkeredBackdrop) {
                        Modifier.checkerboard(ComponentDefaults.ArtPicker.checkerCellDp.dp)
                    } else {
                        Modifier.background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
                    }
                )
                .argosyFocusIndicators(
                    focused = isFocused,
                    indicators = FocusIndicators.Ring,
                    shape = shape
                )
                .clickableNoFocus(onClick = onClick)
        ) {
            if (candidate.isRevert) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(Dimens.spacingXs),
                    modifier = Modifier.align(Alignment.Center).padding(Dimens.spacingSm)
                ) {
                    Icon(
                        imageVector = Icons.Default.Restore,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(Dimens.iconMd)
                    )
                    Text(
                        text = stringResource(slot.revertLabelRes),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                AsyncImage(
                    model = candidate.thumbUrl ?: candidate.source,
                    contentDescription = null,
                    contentScale = if (config.cropsToTile) ContentScale.Crop else ContentScale.Fit,
                    modifier = Modifier.matchParentSize()
                )
            }
        }
        val origin = candidate.originRes?.let { stringResource(it) }
        val dimensions = candidate.dimensionLabel
        val caption = when {
            origin != null && dimensions != null ->
                stringResource(R.string.gamedetail_art_picker_caption, origin, dimensions)
            else -> origin ?: dimensions
        }
        caption?.let { label ->
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

private fun Modifier.checkerboard(cell: Dp): Modifier = drawBehind {
    val cellPx = cell.toPx()
    drawRect(ColorTokens.Domain.ArtChecker.lightTile)
    val columns = ceil(size.width / cellPx).toInt()
    val rows = ceil(size.height / cellPx).toInt()
    for (row in 0 until rows) {
        for (column in 0 until columns) {
            if ((row + column).mod(2) == 1) {
                drawRect(
                    color = ColorTokens.Domain.ArtChecker.darkTile,
                    topLeft = Offset(column * cellPx, row * cellPx),
                    size = Size(cellPx, cellPx)
                )
            }
        }
    }
}

@Composable
private fun LoadingState() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(ComponentDefaults.ArtPicker.stateHeightDp.dp),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun ChooseFileState(message: String, isFocused: Boolean, onChooseFile: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(
                horizontal = Dimens.spacingLg,
                vertical = Dimens.spacingMd
            )
        )
        OptionItem(
            icon = Icons.Default.FolderOpen,
            label = stringResource(R.string.gamedetail_art_picker_choose_file),
            isFocused = isFocused,
            onClick = onChooseFile
        )
    }
}
