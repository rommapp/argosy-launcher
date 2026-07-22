package com.nendo.argosy.ui.filebrowser

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.nendo.argosy.core.storage.StorageVolume
import com.nendo.argosy.core.storage.StorageVolumeType
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
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Usb
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import android.content.res.Configuration
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.components.FooterHints
import com.nendo.argosy.ui.components.FooterSpacer
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.Modal
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.input.ModalInputEffect
import com.nendo.argosy.ui.primitives.ModalActionButton
import androidx.compose.ui.graphics.lerp
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme

@Composable
fun FileBrowserScreen(
    mode: FileBrowserMode = FileBrowserMode.FOLDER_SELECTION,
    title: String? = null,
    fileFilter: FileFilter? = null,
    onPathSelected: (String) -> Unit,
    onDismiss: () -> Unit,
    viewModel: FileBrowserViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsState()
    val inputDispatcher = LocalInputDispatcher.current
    val context = LocalContext.current

    val requestStoragePermission = remember(context) {
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
                context.startActivity(intent)
            }
        }
    }

    LaunchedEffect(mode, fileFilter) {
        viewModel.setMode(mode)
        viewModel.setFileFilter(fileFilter)
    }

    val useCurrentFolder = remember(onPathSelected) {
        {
            val current = viewModel.state.value.currentPath
            if (current.isNotEmpty()) onPathSelected(current)
        }
    }

    val inputHandler = remember(onDismiss, requestStoragePermission, useCurrentFolder) {
        FileBrowserInputHandler(viewModel, onDismiss, requestStoragePermission, useCurrentFolder)
    }

    DisposableEffect(inputHandler) {
        inputDispatcher.pushModal(inputHandler)
        onDispose { inputDispatcher.removeModal(inputHandler) }
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.recheckPermission()
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
                title = title ?: when (mode) {
                    FileBrowserMode.FOLDER_SELECTION -> "Select Folder"
                    FileBrowserMode.FILE_SELECTION -> "Select File"
                    FileBrowserMode.FILE_OR_FOLDER_SELECTION -> "Select File or Folder"
                }
            )

            if (!state.hasPermission) {
                PermissionRequiredPane(
                    onGrant = requestStoragePermission,
                    modifier = Modifier.weight(1f)
                )

                FileBrowserPermissionFooter(
                    onGrant = requestStoragePermission,
                    onCancel = onDismiss
                )
            } else {
                val configuration = LocalConfiguration.current
                val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                val volumePaneFraction = if (isLandscape) 0.20f else 0.35f

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
                        modifier = Modifier.fillMaxWidth(volumePaneFraction)
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
                                } else if (mode == FileBrowserMode.FILE_SELECTION ||
                                           mode == FileBrowserMode.FILE_OR_FOLDER_SELECTION) {
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
                    onOpenFocused = { viewModel.confirmFocusedItem() },
                    onUseCurrentFolder = useCurrentFolder,
                    onNewFolder = { viewModel.showCreateFolderDialog() },
                    onCancel = onDismiss
                )
            }
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
private fun PermissionRequiredPane(
    onGrant: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(Dimens.spacingXl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(Dimens.iconXl)
        )
        Spacer(modifier = Modifier.height(Dimens.spacingMd))
        Text(
            text = "Storage access required",
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(Dimens.spacingSm))
        Text(
            text = "Argosy needs All files access to browse folders, download games, and sync saves. Grant it, then return here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth(0.8f)
        )
        Spacer(modifier = Modifier.height(Dimens.spacingLg))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(Dimens.radiusMd))
                .background(MaterialTheme.colorScheme.primary)
                .clickableNoFocus(onClick = onGrant)
                .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingMd)
        ) {
            Text(
                text = "Grant Access",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
    }
}

@Composable
private fun FileBrowserPermissionFooter(
    onGrant: () -> Unit,
    onCancel: () -> Unit
) {
    FooterHints(
        hints = listOf(
            InputButton.A to "Grant Access",
            InputButton.B to "Back"
        ),
        onHintClick = { button ->
            when (button) {
                InputButton.A -> onGrant()
                InputButton.B -> onCancel()
                else -> {}
            }
        }
    )
    FooterSpacer()
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
        LocalArgosyTheme.current.focusAccent.copy(alpha = 0.15f)
    } else {
        Color.Transparent
    }

    val contentColor = if (isFocused) {
        lerp(LocalArgosyTheme.current.focusAccent, Color.White, 0.45f)
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
        LocalArgosyTheme.current.focusAccent.copy(alpha = 0.15f)
    } else {
        Color.Transparent
    }

    val contentColor = if (isFocused) {
        lerp(LocalArgosyTheme.current.focusAccent, Color.White, 0.45f)
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    val iconTint = when {
        isFocused -> lerp(LocalArgosyTheme.current.focusAccent, Color.White, 0.45f)
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
    onOpenFocused: () -> Unit,
    onUseCurrentFolder: () -> Unit,
    onNewFolder: () -> Unit,
    onCancel: () -> Unit
) {
    val selectHint = when (mode) {
        FileBrowserMode.FILE_SELECTION -> "Select File"
        FileBrowserMode.FILE_OR_FOLDER_SELECTION -> "Select File"
        FileBrowserMode.FOLDER_SELECTION -> "Open"
    }
    val showFolderOptions = mode == FileBrowserMode.FOLDER_SELECTION ||
                            mode == FileBrowserMode.FILE_OR_FOLDER_SELECTION
    val hints = buildList {
        add(InputButton.A to selectHint)
        add(InputButton.B to "Back")
        if (showFolderOptions && currentPath.isNotEmpty()) {
            if (mode == FileBrowserMode.FOLDER_SELECTION) {
                add(InputButton.Y to "New Folder")
            }
            add(InputButton.X to "Use Folder")
        }
    }

    FooterHints(
        hints = hints,
        onHintClick = { button ->
            when (button) {
                InputButton.A -> onOpenFocused()
                InputButton.Y -> onNewFolder()
                InputButton.X -> onUseCurrentFolder()
                InputButton.B -> onCancel()
                else -> {}
            }
        }
    )
    FooterSpacer()
}

private const val FOLDER_ROW_FIELD = 0
private const val FOLDER_ROW_BUTTONS = 1

@Composable
private fun CreateFolderDialog(
    folderName: String,
    error: String?,
    onFolderNameChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val theme = LocalArgosyTheme.current
    var focusRow by remember { mutableIntStateOf(FOLDER_ROW_FIELD) }
    var buttonIndex by remember { mutableIntStateOf(1) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val canCreate = folderName.isNotBlank()
    val currentCanCreate by rememberUpdatedState(canCreate)
    val currentOnConfirm by rememberUpdatedState(onConfirm)
    val currentOnDismiss by rememberUpdatedState(onDismiss)

    LaunchedEffect(focusRow) {
        if (focusRow == FOLDER_ROW_FIELD) focusRequester.requestFocus() else focusManager.clearFocus()
    }

    val inputHandler = remember {
        object : InputHandler {
            override fun onUp(): InputResult {
                focusRow = FOLDER_ROW_FIELD
                return InputResult.HANDLED
            }

            override fun onDown(): InputResult {
                focusRow = FOLDER_ROW_BUTTONS
                return InputResult.HANDLED
            }

            override fun onLeft(): InputResult {
                if (focusRow == FOLDER_ROW_BUTTONS) buttonIndex = 0
                return InputResult.HANDLED
            }

            override fun onRight(): InputResult {
                if (focusRow == FOLDER_ROW_BUTTONS) buttonIndex = 1
                return InputResult.HANDLED
            }

            override fun onConfirm(): InputResult {
                when {
                    focusRow == FOLDER_ROW_FIELD -> focusRow = FOLDER_ROW_BUTTONS
                    buttonIndex == 0 -> currentOnDismiss()
                    currentCanCreate -> currentOnConfirm()
                }
                return InputResult.HANDLED
            }

            override fun onBack(): InputResult {
                currentOnDismiss()
                return InputResult.handled(SoundType.CLOSE_MODAL)
            }

            override fun onMenu(): InputResult = InputResult.HANDLED
            override fun onSecondaryAction(): InputResult = InputResult.HANDLED
            override fun onContextMenu(): InputResult = InputResult.HANDLED
            override fun onPrevSection(): InputResult = InputResult.HANDLED
            override fun onNextSection(): InputResult = InputResult.HANDLED
            override fun onPrevTrigger(): InputResult = InputResult.HANDLED
            override fun onNextTrigger(): InputResult = InputResult.HANDLED
            override fun onSelect(): InputResult = InputResult.HANDLED
            override fun onLeftStickClick(): InputResult = InputResult.HANDLED
            override fun onRightStickClick(): InputResult = InputResult.HANDLED
            override fun onLongConfirm(): InputResult = InputResult.HANDLED
        }
    }
    ModalInputEffect(active = true, handler = inputHandler)

    val fieldShape = RoundedCornerShape(Dimens.radiusMd)
    Modal(title = "New Folder", onDismiss = onDismiss) {
        OutlinedTextField(
            value = folderName,
            onValueChange = onFolderNameChange,
            label = { Text("Folder name") },
            singleLine = true,
            isError = error != null,
            shape = fieldShape,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (canCreate) onConfirm() }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .then(
                    if (focusRow == FOLDER_ROW_FIELD) {
                        Modifier.background(theme.focusAccent.copy(alpha = 0.15f), fieldShape)
                    } else Modifier
                )
        )
        if (error != null) {
            Spacer(modifier = Modifier.height(Dimens.spacingSm))
            Text(
                text = error,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
        Spacer(modifier = Modifier.height(Dimens.spacingLg))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm, Alignment.End)
        ) {
            ModalActionButton(
                label = "Cancel",
                tint = theme.focusAccent,
                restLabelColor = theme.textPrimary,
                focused = focusRow == FOLDER_ROW_BUTTONS && buttonIndex == 0,
                onClick = onDismiss
            )
            ModalActionButton(
                label = "Create",
                tint = theme.focusAccent,
                restLabelColor = theme.textPrimary,
                focused = focusRow == FOLDER_ROW_BUTTONS && buttonIndex == 1,
                onClick = onConfirm,
                enabled = canCreate
            )
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
    val value = bytes / Math.pow(1024.0, digitGroups.toDouble())
    return String.format(java.util.Locale.US, "%.1f %s", value, units[digitGroups.coerceIn(0, units.lastIndex)])
}
