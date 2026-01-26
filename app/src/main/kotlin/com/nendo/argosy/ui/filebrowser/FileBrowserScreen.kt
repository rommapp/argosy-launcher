package com.nendo.argosy.ui.filebrowser

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.nendo.argosy.ui.util.clickableNoFocus
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nendo.argosy.ui.components.FooterBar
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.theme.Dimens

@Composable
fun FileBrowserScreen(
    mode: FileBrowserMode = FileBrowserMode.FOLDER_SELECTION,
    fileFilter: FileFilter? = null,
    onPathSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    viewModel: FileBrowserViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val inputDispatcher = LocalInputDispatcher.current

    LaunchedEffect(mode, fileFilter) {
        viewModel.setMode(mode)
        viewModel.setFileFilter(fileFilter)
    }

    val inputHandler = remember(onDismiss) {
        FileBrowserInputHandler(viewModel, onDismiss)
    }

    DisposableEffect(inputHandler) {
        inputDispatcher.pushModal(inputHandler)
        onDispose { inputDispatcher.popModal() }
    }

    LaunchedEffect(Unit) {
        viewModel.resultPath.collect { path ->
            onPathSelected(path)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .clickableNoFocus {}
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            FileBrowserHeader(
                title = if (mode == FileBrowserMode.FOLDER_SELECTION) "Select Folder" else "Select File"
            )

            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(Dimens.spacingMd)
            ) {
                VolumePane(
                    volumes = state.volumes,
                    focusedIndex = state.volumeFocusIndex,
                    isFocused = state.focusedPane == FocusedPane.VOLUMES,
                    onVolumeClick = { viewModel.selectVolume(it) },
                    modifier = Modifier.width(Dimens.modalWidth - 190.dp)
                )

                Spacer(modifier = Modifier.width(Dimens.spacingMd))

                Column(modifier = Modifier.weight(1f)) {
                    BreadcrumbBar(
                        path = state.currentPath,
                        volumes = state.volumes
                    )

                    Spacer(modifier = Modifier.height(Dimens.spacingSm))

                    FilePane(
                        entries = state.entries,
                        focusedIndex = state.fileFocusIndex,
                        isFocused = state.focusedPane == FocusedPane.FILES,
                        isLoading = state.isLoading,
                        error = state.error,
                        onEntryClick = { entry ->
                            if (entry.isDirectory) {
                                viewModel.navigate(entry.path)
                            } else if (mode == FileBrowserMode.FILE_SELECTION) {
                                onPathSelected(entry.path)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            FileBrowserFooter(
                mode = mode,
                currentPath = state.currentPath,
                onUseCurrentFolder = { viewModel.selectCurrentDirectory() },
                onNewFolder = { viewModel.showCreateFolderDialog() },
                onCancel = onDismiss
            )
        }

        if (state.showCreateFolderDialog) {
            CreateFolderDialog(
                folderName = state.newFolderName,
                error = state.createFolderError,
                onFolderNameChange = { viewModel.setNewFolderName(it) },
                onConfirm = { viewModel.confirmCreateFolder() },
                onDismiss = { viewModel.dismissCreateFolderDialog() }
            )
        }
    }
}

@Composable
private fun FileBrowserHeader(title: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingMd)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun VolumePane(
    volumes: List<StorageVolume>,
    focusedIndex: Int,
    isFocused: Boolean,
    onVolumeClick: (StorageVolume) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    val scrollAhead = 2

    LaunchedEffect(focusedIndex, isFocused, volumes.size) {
        if (!isFocused || volumes.isEmpty() || focusedIndex !in volumes.indices) return@LaunchedEffect

        val visibleItems = listState.layoutInfo.visibleItemsInfo
        if (visibleItems.isEmpty()) {
            listState.animateScrollToItem(focusedIndex)
            return@LaunchedEffect
        }

        val firstVisible = visibleItems.first().index
        val lastVisible = visibleItems.last().index
        val visibleCount = lastVisible - firstVisible

        when {
            focusedIndex <= firstVisible + scrollAhead -> {
                val newFirst = (focusedIndex - scrollAhead).coerceAtLeast(0)
                listState.animateScrollToItem(newFirst)
            }
            focusedIndex >= lastVisible - scrollAhead -> {
                val newFirst = (focusedIndex - visibleCount + scrollAhead).coerceAtLeast(0)
                listState.animateScrollToItem(newFirst)
            }
        }
    }

    val borderColor = if (isFocused) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxHeight()
            .border(Dimens.borderThin, borderColor, RoundedCornerShape(Dimens.radiusMd))
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(MaterialTheme.colorScheme.surface)
            .padding(Dimens.spacingSm)
    ) {
        itemsIndexed(volumes, key = { _, v -> v.id }) { index, volume ->
            VolumeItem(
                volume = volume,
                isFocused = isFocused && index == focusedIndex,
                onClick = { onVolumeClick(volume) }
            )
        }
    }
}

@Composable
private fun VolumeItem(
    volume: StorageVolume,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val icon = when (volume.type) {
        StorageVolumeType.INTERNAL -> Icons.Default.PhoneAndroid
        StorageVolumeType.SD_CARD -> Icons.Default.SdCard
        StorageVolumeType.USB -> Icons.Default.Usb
        StorageVolumeType.UNKNOWN -> Icons.Default.Storage
    }

    val backgroundColor = if (isFocused) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }

    val contentColor = if (isFocused) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(backgroundColor)
            .clickableNoFocus(onClick = onClick)
            .padding(Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = contentColor,
            modifier = Modifier.size(Dimens.iconMd)
        )
        Spacer(modifier = Modifier.width(Dimens.spacingSm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = volume.displayName,
                style = MaterialTheme.typography.bodyMedium,
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = formatFileSize(volume.availableBytes) + " free",
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun BreadcrumbBar(
    path: String,
    volumes: List<StorageVolume>
) {
    val displayPath = volumes.find { path.startsWith(it.path) }?.let { volume ->
        val relativePath = path.removePrefix(volume.path).trimStart('/')
        if (relativePath.isEmpty()) {
            volume.displayName
        } else {
            "${volume.displayName}/$relativePath"
        }
    } ?: path

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                RoundedCornerShape(Dimens.radiusSm)
            )
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm)
    ) {
        Text(
            text = displayPath,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun FilePane(
    entries: List<FileEntry>,
    focusedIndex: Int,
    isFocused: Boolean,
    isLoading: Boolean,
    error: String?,
    onEntryClick: (FileEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    val scrollAhead = 3

    LaunchedEffect(focusedIndex, isFocused, entries.size) {
        if (!isFocused || entries.isEmpty() || focusedIndex !in entries.indices) return@LaunchedEffect

        val visibleItems = listState.layoutInfo.visibleItemsInfo
        if (visibleItems.isEmpty()) {
            listState.animateScrollToItem(focusedIndex)
            return@LaunchedEffect
        }

        val firstVisible = visibleItems.first().index
        val lastVisible = visibleItems.last().index
        val visibleCount = lastVisible - firstVisible

        when {
            focusedIndex <= firstVisible + scrollAhead -> {
                val newFirst = (focusedIndex - scrollAhead).coerceAtLeast(0)
                listState.animateScrollToItem(newFirst)
            }
            focusedIndex >= lastVisible - scrollAhead -> {
                val newFirst = (focusedIndex - visibleCount + scrollAhead).coerceAtLeast(0)
                listState.animateScrollToItem(newFirst)
            }
        }
    }

    val borderColor = if (isFocused) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .border(Dimens.borderThin, borderColor, RoundedCornerShape(Dimens.radiusMd))
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(MaterialTheme.colorScheme.surface)
    ) {
        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(Dimens.iconLg),
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            error != null -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(Dimens.spacingLg),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(Dimens.iconXl)
                    )
                    Spacer(modifier = Modifier.height(Dimens.spacingSm))
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            entries.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Empty folder",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            else -> {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(
                        start = Dimens.spacingSm,
                        end = Dimens.spacingSm,
                        top = Dimens.spacingSm,
                        bottom = 80.dp
                    )
                ) {
                    itemsIndexed(entries, key = { _, e -> e.path }) { index, entry ->
                        FileItem(
                            entry = entry,
                            isFocused = isFocused && index == focusedIndex,
                            onClick = { onEntryClick(entry) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FileItem(
    entry: FileEntry,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val icon: ImageVector = when {
        entry.isParentLink -> Icons.AutoMirrored.Filled.KeyboardArrowLeft
        entry.isDirectory -> Icons.Default.Folder
        else -> Icons.AutoMirrored.Filled.InsertDriveFile
    }

    val backgroundColor = if (isFocused) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        Color.Transparent
    }

    val contentColor = if (isFocused) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val iconTint = when {
        isFocused -> MaterialTheme.colorScheme.onPrimaryContainer
        entry.isDirectory -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(Dimens.radiusSm))
            .background(backgroundColor)
            .clickableNoFocus(onClick = onClick)
            .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingSm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(Dimens.iconMd)
        )
        Spacer(modifier = Modifier.width(Dimens.spacingMd))
        Text(
            text = entry.name,
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (!entry.isDirectory && !entry.isParentLink) {
            Text(
                text = formatFileSize(entry.size),
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun FileBrowserFooter(
    mode: FileBrowserMode,
    currentPath: String,
    onUseCurrentFolder: () -> Unit,
    onNewFolder: () -> Unit,
    onCancel: () -> Unit
) {
    val selectHint = if (mode == FileBrowserMode.FILE_SELECTION) "Select File" else "Open"
    val hints = buildList {
        add(InputButton.A to selectHint)
        add(InputButton.B to "Back")
        if (mode == FileBrowserMode.FOLDER_SELECTION && currentPath.isNotEmpty()) {
            add(InputButton.Y to "New Folder")
            add(InputButton.X to "Use Current")
        }
    }

    FooterBar(
        hints = hints,
        onHintClick = { button ->
            when (button) {
                InputButton.Y -> onNewFolder()
                InputButton.X -> onUseCurrentFolder()
                InputButton.B -> onCancel()
                else -> {}
            }
        }
    )
}

@Composable
private fun CreateFolderDialog(
    folderName: String,
    error: String?,
    onFolderNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Folder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)) {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = onFolderNameChange,
                    label = { Text("Folder name") },
                    singleLine = true,
                    isError = error != null,
                    modifier = Modifier.fillMaxWidth()
                )
                if (error != null) {
                    Text(
                        text = error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = folderName.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
    return String.format(java.util.Locale.US, "%.1f %s", value, units[digitGroups.coerceIn(0, units.lastIndex)])
}
