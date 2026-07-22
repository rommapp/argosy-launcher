package com.nendo.argosy.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.components.FooterHints
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.ModalInputEffect
import com.nendo.argosy.ui.primitives.ArgosyToggle
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import com.nendo.argosy.ui.theme.LocalLauncherTheme
import com.nendo.argosy.ui.util.clickableNoFocus

@Composable
fun BgmPlaylistManagerScreen(
    onAddMusic: () -> Unit,
    onDismiss: () -> Unit,
    viewModel: BgmPlaylistManagerViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val currentOnAddMusic by rememberUpdatedState(onAddMusic)
    val currentOnDismiss by rememberUpdatedState(onDismiss)

    val inputHandler = remember(viewModel) {
        object : InputHandler {
            override fun onUp(): InputResult {
                val st = viewModel.uiState.value
                if (!st.isReordering) {
                    viewModel.moveFocus(-1)
                    return InputResult.HANDLED
                }
                return if (viewModel.moveFocusedRow(-1)) InputResult.HANDLED else InputResult.handled(SoundType.BOUNDARY)
            }

            override fun onDown(): InputResult {
                val st = viewModel.uiState.value
                if (!st.isReordering) {
                    viewModel.moveFocus(1)
                    return InputResult.HANDLED
                }
                return if (viewModel.moveFocusedRow(1)) InputResult.HANDLED else InputResult.handled(SoundType.BOUNDARY)
            }

            override fun onLeft(): InputResult = InputResult.handled(SoundType.SILENT)
            override fun onRight(): InputResult = InputResult.handled(SoundType.SILENT)

            override fun onConfirm(): InputResult {
                val st = viewModel.uiState.value
                if (st.isReordering) {
                    viewModel.commitReorder()
                    return InputResult.HANDLED
                }
                if (st.focusedEntry == null) return InputResult.handled(SoundType.SILENT)
                viewModel.beginReorder()
                return InputResult.HANDLED
            }

            override fun onBack(): InputResult {
                val st = viewModel.uiState.value
                if (st.isReordering) viewModel.cancelReorder() else currentOnDismiss()
                return InputResult.HANDLED
            }

            override fun onSecondaryAction(): InputResult {
                val st = viewModel.uiState.value
                if (st.isReordering || st.isEmpty) return InputResult.handled(SoundType.SILENT)
                viewModel.removeFocused()
                return InputResult.HANDLED
            }

            override fun onContextMenu(): InputResult {
                val st = viewModel.uiState.value
                if (st.isReordering) return InputResult.handled(SoundType.SILENT)
                currentOnAddMusic()
                return InputResult.HANDLED
            }

            override fun onPrevSection(): InputResult = InputResult.handled(SoundType.SILENT)
            override fun onNextSection(): InputResult = InputResult.handled(SoundType.SILENT)
            override fun onPrevTrigger(): InputResult = InputResult.handled(SoundType.SILENT)
            override fun onNextTrigger(): InputResult = InputResult.handled(SoundType.SILENT)
            override fun onMenu(): InputResult = InputResult.handled(SoundType.SILENT)
            override fun onSelect(): InputResult = InputResult.handled(SoundType.SILENT)
        }
    }

    ModalInputEffect(active = true, handler = inputHandler)

    val listState = rememberLazyListState()

    LaunchedEffect(uiState.focusedIndex, uiState.folderSources.size, uiState.entries.size) {
        if (uiState.isEmpty || uiState.focusedIndex !in 0 until uiState.focusCount) return@LaunchedEffect
        val sourceCount = uiState.folderSources.size
        val lazyIndex = when {
            sourceCount == 0 -> uiState.focusedIndex
            uiState.focusedIndex < sourceCount -> 1 + uiState.focusedIndex
            else -> 2 + sourceCount + (uiState.focusedIndex - sourceCount)
        }
        val viewportHeight = listState.layoutInfo.viewportEndOffset
        val visibleItems = listState.layoutInfo.visibleItemsInfo
        val avgItemHeight = if (visibleItems.isNotEmpty()) {
            visibleItems.sumOf { it.size } / visibleItems.size
        } else 64
        val targetOffset = (viewportHeight / 2) - (avgItemHeight / 2)
        listState.animateScrollToItem(lazyIndex, -targetOffset)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickableNoFocus {}
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            BgmPlaylistHeader(
                onBack = onDismiss,
                onAddMusic = onAddMusic
            )

            if (uiState.isEmpty) {
                BgmPlaylistEmptyState()
            } else {
                val sourceCount = uiState.folderSources.size
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(
                        start = Dimens.spacingLg,
                        end = Dimens.spacingLg,
                        top = Dimens.spacingSm,
                        bottom = Dimens.footerHeight
                    ),
                    verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
                ) {
                    if (sourceCount > 0) {
                        item(key = "sources-header") {
                            BgmPlaylistGroupHeader("Synced Folders")
                        }
                        itemsIndexed(uiState.folderSources, key = { _, row -> "source-${row.id}" }) { index, row ->
                            BgmFolderSourceRow(
                                row = row,
                                isFocused = uiState.focusedIndex == index,
                                onClick = { viewModel.setFocusIndex(index) },
                                onRemove = { viewModel.removeSource(index) }
                            )
                        }
                        item(key = "tracks-header") {
                            BgmPlaylistGroupHeader("Tracks")
                        }
                    }
                    itemsIndexed(uiState.entries, key = { _, row -> "track-${row.id}" }) { index, row ->
                        val focusIndex = sourceCount + index
                        BgmPlaylistEntryRow(
                            row = row,
                            position = index + 1,
                            isFocused = uiState.focusedIndex == focusIndex,
                            isBeingMoved = uiState.isReordering && uiState.focusedIndex == focusIndex,
                            canMoveUp = index > 0,
                            canMoveDown = index < uiState.entries.lastIndex,
                            onClick = { viewModel.setFocusIndex(focusIndex) },
                            onMoveUp = { viewModel.moveTrack(index, -1) },
                            onMoveDown = { viewModel.moveTrack(index, 1) },
                            onRemove = { viewModel.toggleOrRemoveTrack(index) },
                            onSetEnabled = { viewModel.setTrackEnabled(index, it) }
                        )
                    }
                }
            }
        }

        val hints = when {
            uiState.isReordering -> listOf(
                InputButton.DPAD_VERTICAL to "Move",
                InputButton.A to "Done",
                InputButton.B to "Cancel"
            )
            uiState.isEmpty -> listOf(
                InputButton.X to "Add Music"
            )
            else -> buildList {
                val focusedEntry = uiState.focusedEntry
                if (focusedEntry != null) add(InputButton.A to "Move")
                add(InputButton.X to "Add Music")
                val trackVerb = when {
                    focusedEntry == null -> "Remove"
                    !focusedEntry.isFolderCovered -> "Remove"
                    focusedEntry.enabled -> "Disable"
                    else -> "Enable"
                }
                add(InputButton.Y to trackVerb)
            }
        }

        FooterHints(
            hints = hints,
            onHintClick = { button ->
                when (button) {
                    InputButton.A -> inputHandler.onConfirm()
                    InputButton.B -> inputHandler.onBack()
                    InputButton.X -> inputHandler.onContextMenu()
                    InputButton.Y -> inputHandler.onSecondaryAction()
                    else -> {}
                }
            }
        )
    }
}

@Composable
private fun BgmPlaylistHeader(
    onBack: () -> Unit,
    onAddMusic: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.width(Dimens.spacingSm))

        Text(
            text = "Music Playlist",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )

        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(Dimens.radiusMd))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                .clickableNoFocus(onClick = onAddMusic)
                .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(Dimens.iconSm)
            )
            Spacer(modifier = Modifier.width(Dimens.spacingXs))
            Text(
                text = "Add",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun BgmPlaylistGroupHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = Dimens.spacingSm, vertical = Dimens.spacingXs)
    )
}

@Composable
private fun BgmFolderSourceRow(
    row: BgmFolderSourceUi,
    isFocused: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val focusAccent = LocalArgosyTheme.current.focusAccent
    val focusedContentColor = lerp(focusAccent, Color.White, 0.45f)
    val warningColor = LocalLauncherTheme.current.semanticColors.warning

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(
                if (isFocused) focusAccent.copy(alpha = 0.15f).compositeOver(MaterialTheme.colorScheme.surface)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            )
            .clickableNoFocus(onClick = onClick)
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Outlined.Folder,
            contentDescription = null,
            tint = if (isFocused) focusedContentColor else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(Dimens.iconMd)
        )

        Spacer(modifier = Modifier.width(Dimens.spacingMd))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = row.displayName,
                style = MaterialTheme.typography.bodyLarge,
                color = if (isFocused) focusedContentColor else MaterialTheme.colorScheme.onSurface
            )
            if (row.isMissing) {
                Text(
                    text = "Folder missing",
                    style = MaterialTheme.typography.bodySmall,
                    color = warningColor
                )
            } else {
                val countLabel = if (row.trackCount == 1) "1 track" else "${row.trackCount} tracks"
                val disabledSuffix = if (row.disabledCount > 0) " (${row.disabledCount} disabled)" else ""
                Text(
                    text = countLabel + disabledSuffix,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isFocused) focusedContentColor.copy(alpha = 0.7f)
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        IconButton(onClick = onRemove) {
            Icon(
                Icons.Default.Close,
                contentDescription = "Remove folder",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun BgmPlaylistEntryRow(
    row: BgmPlaylistRowUi,
    position: Int,
    isFocused: Boolean,
    isBeingMoved: Boolean,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onClick: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    onSetEnabled: (Boolean) -> Unit
) {
    val focusAccent = LocalArgosyTheme.current.focusAccent
    val focusedContentColor = lerp(focusAccent, Color.White, 0.45f)
    val warningColor = LocalLauncherTheme.current.semanticColors.warning
    val disabledAlpha = 0.38f
    val contentAlpha = if (row.enabled) 1f else disabledAlpha

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(
                when {
                    isBeingMoved -> focusAccent.copy(alpha = 0.3f).compositeOver(MaterialTheme.colorScheme.surface)
                    isFocused -> focusAccent.copy(alpha = 0.15f).compositeOver(MaterialTheme.colorScheme.surface)
                    else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                }
            )
            .clickableNoFocus(onClick = onClick)
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            BgmTrackThumbnail(
                filePath = row.filePath,
                coverPath = row.coverPath,
                contentAlpha = contentAlpha,
                size = Dimens.iconXl
            )
            if (isBeingMoved) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(Dimens.radiusSm))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.DragHandle,
                        contentDescription = "Moving",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(Dimens.iconMd)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.width(Dimens.spacingMd))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "$position. ${row.displayName}",
                style = MaterialTheme.typography.bodyLarge,
                color = (if (isFocused) focusedContentColor else MaterialTheme.colorScheme.onSurface)
                    .copy(alpha = contentAlpha)
            )
            when {
                row.isMissing -> Text(
                    text = "File missing - skipped during playback",
                    style = MaterialTheme.typography.bodySmall,
                    color = warningColor
                )
                !row.enabled -> Text(
                    text = "Disabled - excluded from playback",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isFocused) focusedContentColor.copy(alpha = 0.7f)
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                row.sourceFolderName != null -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Outlined.Folder,
                        contentDescription = null,
                        tint = if (isFocused) focusedContentColor.copy(alpha = 0.7f)
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(Dimens.iconXs)
                    )
                    Spacer(modifier = Modifier.width(Dimens.spacingXs))
                    Text(
                        text = row.sourceFolderName,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isFocused) focusedContentColor.copy(alpha = 0.7f)
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        IconButton(onClick = onMoveUp, enabled = canMoveUp) {
            Icon(
                Icons.Default.KeyboardArrowUp,
                contentDescription = "Move up",
                tint = if (canMoveUp) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            )
        }
        IconButton(onClick = onMoveDown, enabled = canMoveDown) {
            Icon(
                Icons.Default.KeyboardArrowDown,
                contentDescription = "Move down",
                tint = if (canMoveDown) MaterialTheme.colorScheme.onSurfaceVariant
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
            )
        }
        if (row.isFolderCovered) {
            Spacer(modifier = Modifier.width(Dimens.spacingSm))
            ArgosyToggle(
                checked = row.enabled,
                onToggle = onSetEnabled,
                focused = isFocused
            )
            Spacer(modifier = Modifier.width(Dimens.spacingXs))
        } else {
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Remove",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun BgmPlaylistEmptyState() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Outlined.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(Dimens.iconXl)
            )
            Spacer(modifier = Modifier.height(Dimens.spacingMd))
            Text(
                text = "No music - add from the music browser or local files",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
