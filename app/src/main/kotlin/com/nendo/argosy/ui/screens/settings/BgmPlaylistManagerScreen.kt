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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.nendo.argosy.R
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.components.FooterHints
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.animateScrollToItemCentered
import com.nendo.argosy.ui.theme.reducedMotion
import com.nendo.argosy.ui.components.dragReorderContainer
import com.nendo.argosy.ui.components.dragReorderItem
import com.nendo.argosy.ui.components.liftedReorderHints
import com.nendo.argosy.ui.components.rememberDragReorderState
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
            override fun onUp(): InputResult = move(-1)

            override fun onDown(): InputResult = move(1)

            private fun move(delta: Int): InputResult {
                val st = viewModel.uiState.value
                if (!st.isReordering) {
                    viewModel.moveFocus(delta)
                    return InputResult.HANDLED
                }
                return if (viewModel.moveHeld(delta)) InputResult.HANDLED else InputResult.handled(SoundType.BOUNDARY)
            }

            override fun onLeft(): InputResult = InputResult.handled(SoundType.SILENT)
            override fun onRight(): InputResult = InputResult.handled(SoundType.SILENT)

            override fun onConfirm(): InputResult {
                if (viewModel.uiState.value.isReordering) {
                    viewModel.drop()
                    return InputResult.handled(SoundType.SELECT)
                }
                return if (viewModel.toggleFocusedTrack()) InputResult.handled(SoundType.TOGGLE)
                else InputResult.handled(SoundType.SILENT)
            }

            override fun onBack(): InputResult {
                val st = viewModel.uiState.value
                if (st.isReordering) {
                    viewModel.cancel()
                    return InputResult.handled(SoundType.BACK)
                }
                currentOnDismiss()
                return InputResult.HANDLED
            }

            override fun onSecondaryAction(): InputResult {
                val st = viewModel.uiState.value
                when {
                    st.isReordering -> viewModel.drop()
                    st.focusedEntry == null -> return InputResult.handled(SoundType.SILENT)
                    else -> viewModel.lift()
                }
                return InputResult.handled(SoundType.SELECT)
            }

            override fun onContextMenu(): InputResult {
                val st = viewModel.uiState.value
                if (st.isReordering || st.isEmpty) return InputResult.handled(SoundType.SILENT)
                return if (viewModel.removeFocused()) InputResult.HANDLED
                else InputResult.handled(SoundType.SILENT)
            }

            override fun onSelect(): InputResult {
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
        }
    }

    ModalInputEffect(active = true, handler = inputHandler)

    val listState = rememberLazyListState()
    val haptics = LocalHapticFeedback.current
    val trackKeyPrefix = "track-"

    val dragState = rememberDragReorderState(
        listState = listState,
        canDrag = { key -> key is String && key.startsWith(trackKeyPrefix) },
        onLift = { key ->
            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
            (key as String).removePrefix(trackKeyPrefix).toLongOrNull()?.let(viewModel::liftAt)
        },
        onMove = { key, lazyIndex ->
            val trackId = (key as String).removePrefix(trackKeyPrefix).toLongOrNull()
            val sourceCount = uiState.folderSources.size
            val trackOffset = if (sourceCount > 0) sourceCount + 2 else 0
            if (trackId != null) viewModel.moveHeldTo(trackId, lazyIndex - trackOffset)
        },
        onDrop = { viewModel.drop() }
    )

    val reduced = reducedMotion()
    LaunchedEffect(uiState.focusedIndex, uiState.folderSources.size, uiState.entries.size) {
        if (dragState.draggingKey != null) return@LaunchedEffect
        if (uiState.isEmpty || uiState.focusedIndex !in 0 until uiState.focusCount) return@LaunchedEffect
        val sourceCount = uiState.folderSources.size
        val lazyIndex = when {
            sourceCount == 0 -> uiState.focusedIndex
            uiState.focusedIndex < sourceCount -> 1 + uiState.focusedIndex
            else -> 2 + sourceCount + (uiState.focusedIndex - sourceCount)
        }
        listState.animateScrollToItemCentered(lazyIndex, reduced)
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
                    modifier = Modifier.dragReorderContainer(dragState),
                    contentPadding = PaddingValues(
                        start = Dimens.spacingLg,
                        end = Dimens.spacingLg,
                        top = Dimens.spacingSm,
                        bottom = Dimens.footerClearance
                    ),
                    verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
                ) {
                    if (sourceCount > 0) {
                        item(key = "sources-header") {
                            BgmPlaylistGroupHeader(stringResource(R.string.settings_shell_bgm_synced_folders))
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
                            BgmPlaylistGroupHeader(stringResource(R.string.settings_shell_bgm_tracks))
                        }
                    }
                    itemsIndexed(uiState.entries, key = { _, row -> "$trackKeyPrefix${row.id}" }) { index, row ->
                        val focusIndex = sourceCount + index
                        val rowKey = "$trackKeyPrefix${row.id}"
                        BgmPlaylistEntryRow(
                            row = row,
                            position = index + 1,
                            isFocused = uiState.focusedIndex == focusIndex,
                            isBeingMoved = uiState.heldTrackIndex == index,
                            modifier = Modifier
                                .then(
                                    if (dragState.draggingKey == null || dragState.draggingKey == rowKey) {
                                        Modifier
                                    } else {
                                        Modifier.animateItem()
                                    }
                                )
                                .dragReorderItem(dragState, rowKey),
                            onClick = { if (!uiState.isReordering) viewModel.setFocusIndex(focusIndex) },
                            onRemove = { viewModel.removeTrack(index) },
                            onSetEnabled = { viewModel.setTrackEnabled(index, it) }
                        )
                    }
                }
            }
        }

        val hints = when {
            uiState.isReordering -> liftedReorderHints(
                move = stringResource(R.string.settings_shell_bgm_reorder_move),
                cancel = stringResource(R.string.settings_shell_bgm_reorder_cancel)
            )
            uiState.isEmpty -> listOf(
                InputButton.SELECT to stringResource(R.string.settings_shell_bgm_add_music_empty)
            )
            else -> buildList {
                val focusedEntry = uiState.focusedEntry
                if (focusedEntry != null) {
                    add(InputButton.Y to stringResource(R.string.settings_shell_bgm_reorder_hint))
                    val toggleVerb = if (focusedEntry.enabled) {
                        stringResource(R.string.settings_shell_bgm_disable_verb)
                    } else {
                        stringResource(R.string.settings_shell_bgm_enable_verb)
                    }
                    add(InputButton.A to toggleVerb)
                }
                add(InputButton.SELECT to stringResource(R.string.settings_shell_bgm_add_music_hint))
                when {
                    focusedEntry == null -> add(InputButton.X to stringResource(R.string.settings_shell_bgm_remove_verb))
                    !focusedEntry.isFolderCovered -> add(
                        InputButton.X to stringResource(R.string.settings_shell_bgm_remove_verb_uncovered)
                    )
                    else -> Unit
                }
            }
        }

        FooterHints(
            forced = uiState.isReordering,
            hints = hints,
            onHintClick = { button ->
                when (button) {
                    InputButton.A -> inputHandler.onConfirm()
                    InputButton.B -> inputHandler.onBack()
                    InputButton.X -> inputHandler.onContextMenu()
                    InputButton.Y -> inputHandler.onSecondaryAction()
                    InputButton.SELECT -> inputHandler.onSelect()
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
                contentDescription = stringResource(R.string.settings_shell_bgm_back_desc),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.width(Dimens.spacingSm))

        Text(
            text = stringResource(R.string.settings_shell_bgm_title),
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
                text = stringResource(R.string.settings_shell_bgm_add_label),
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
                    text = stringResource(R.string.settings_shell_bgm_folder_missing),
                    style = MaterialTheme.typography.bodySmall,
                    color = warningColor
                )
            } else {
                val countLabel = pluralStringResource(
                    R.plurals.settings_shell_bgm_track_count, row.trackCount, row.trackCount
                )
                val disabledSuffix = if (row.disabledCount > 0) {
                    pluralStringResource(
                        R.plurals.settings_shell_bgm_disabled_suffix_template,
                        row.disabledCount,
                        row.disabledCount
                    )
                } else {
                    ""
                }
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
                contentDescription = stringResource(R.string.settings_shell_bgm_remove_folder_desc),
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
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onSetEnabled: (Boolean) -> Unit
) {
    val focusAccent = LocalArgosyTheme.current.focusAccent
    val focusedContentColor = lerp(focusAccent, Color.White, 0.45f)
    val warningColor = LocalLauncherTheme.current.semanticColors.warning
    val disabledAlpha = 0.38f
    val contentAlpha = if (row.enabled) 1f else disabledAlpha

    Row(
        modifier = modifier
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
                        contentDescription = stringResource(R.string.settings_shell_bgm_moving_desc),
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
                    text = stringResource(R.string.settings_shell_bgm_file_missing),
                    style = MaterialTheme.typography.bodySmall,
                    color = warningColor
                )
                !row.enabled -> Text(
                    text = stringResource(R.string.settings_shell_bgm_disabled_desc),
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

        Spacer(modifier = Modifier.width(Dimens.spacingSm))
        ArgosyToggle(
            checked = row.enabled,
            onToggle = onSetEnabled,
            focused = isFocused
        )
        Spacer(modifier = Modifier.width(Dimens.spacingXs))
        if (!row.isFolderCovered) {
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.settings_shell_bgm_remove_desc),
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
                text = stringResource(R.string.settings_shell_bgm_empty_state),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
