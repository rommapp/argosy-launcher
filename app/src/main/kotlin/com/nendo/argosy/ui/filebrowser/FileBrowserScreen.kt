package com.nendo.argosy.ui.filebrowser

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import com.nendo.argosy.R
import com.nendo.argosy.core.storage.StorageVolume
import com.nendo.argosy.core.storage.StorageVolumeType
import com.nendo.argosy.ui.util.clickableNoFocus
import com.nendo.argosy.util.formatBytes
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CreateNewFolder
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import android.content.res.Configuration
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.common.rememberStorageAccessRequest
import com.nendo.argosy.ui.components.FooterHints
import com.nendo.argosy.ui.components.FooterSpacer
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.components.Modal
import com.nendo.argosy.ui.components.keyboard.ControllerKeyboardSession
import com.nendo.argosy.ui.components.keyboard.ControllerKeyboardTapTarget
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
    val currentOnPathSelected by rememberUpdatedState(onPathSelected)

    val requestStoragePermission = rememberStorageAccessRequest(onResult = { viewModel.recheckPermission() })

    LaunchedEffect(mode, fileFilter) {
        viewModel.setMode(mode)
        viewModel.setFileFilter(fileFilter)
    }

    val inputHandler = remember(onDismiss, requestStoragePermission) {
        FileBrowserInputHandler(viewModel, onDismiss, requestStoragePermission)
    }

    ModalInputEffect(active = true, handler = inputHandler, inputDispatcher = inputDispatcher)

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.recheckPermission()
    }

    LaunchedEffect(Unit) {
        viewModel.resultPath.collect { path ->
            currentOnPathSelected(path)
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
                    FileBrowserMode.FOLDER_SELECTION ->
                        stringResource(R.string.ui_file_browser_title_folder)
                    FileBrowserMode.FILE_SELECTION ->
                        stringResource(R.string.ui_file_browser_title_file)
                    FileBrowserMode.FILE_OR_FOLDER_SELECTION ->
                        stringResource(R.string.ui_file_browser_title_file_or_folder)
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
                            actions = state.actions,
                            entries = state.entries,
                            focusedIndex = state.fileFocusIndex,
                            isFocused = state.focusedPane == FocusedPane.FILES,
                            isLoading = state.isLoading,
                            errorRes = state.errorRes,
                            onActionClick = { viewModel.performAction(it) },
                            onEntryClick = { viewModel.openEntry(it) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                FileBrowserFooter(
                    actions = state.actions,
                    onAction = { viewModel.performAction(it) }
                )
            }
        }

        if (state.showCreateFolderDialog) {
            CreateFolderDialog(
                folderName = state.newFolderName,
                errorRes = state.createFolderErrorRes,
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
            text = stringResource(R.string.ui_file_browser_permission_title),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(Dimens.spacingSm))
        Text(
            text = stringResource(R.string.ui_file_browser_permission_message),
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
                text = stringResource(R.string.ui_file_browser_permission_button),
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
            InputButton.A to stringResource(R.string.ui_file_browser_permission_footer_grant),
            InputButton.B to stringResource(R.string.ui_file_browser_permission_footer_back)
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
                text = formatBytes(volume.availableBytes) + " free",
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
    actions: List<FileBrowserAction>,
    entries: List<FileEntry>,
    focusedIndex: Int,
    isFocused: Boolean,
    isLoading: Boolean,
    @StringRes errorRes: Int?,
    onActionClick: (FileBrowserAction) -> Unit,
    onEntryClick: (FileEntry) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    val scrollAhead = 3
    val focusedEntryIndex = focusedIndex - actions.size

    LaunchedEffect(focusedEntryIndex, isFocused, entries.size) {
        if (!isFocused || entries.isEmpty() || focusedEntryIndex !in entries.indices) return@LaunchedEffect

        val visibleItems = listState.layoutInfo.visibleItemsInfo
        if (visibleItems.isEmpty()) {
            listState.animateScrollToItem(focusedEntryIndex)
            return@LaunchedEffect
        }

        val firstVisible = visibleItems.first().index
        val lastVisible = visibleItems.last().index
        val visibleCount = lastVisible - firstVisible

        when {
            focusedEntryIndex <= firstVisible + scrollAhead -> {
                val newFirst = (focusedEntryIndex - scrollAhead).coerceAtLeast(0)
                listState.animateScrollToItem(newFirst)
            }
            focusedEntryIndex >= lastVisible - scrollAhead -> {
                val newFirst = (focusedEntryIndex - visibleCount + scrollAhead).coerceAtLeast(0)
                listState.animateScrollToItem(newFirst)
            }
        }
    }

    val borderColor = if (isFocused) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(Dimens.borderThin, borderColor, RoundedCornerShape(Dimens.radiusMd))
            .clip(RoundedCornerShape(Dimens.radiusMd))
            .background(MaterialTheme.colorScheme.surface)
    ) {
        if (actions.isNotEmpty()) {
            Column(
                modifier = Modifier.padding(
                    start = Dimens.spacingSm,
                    end = Dimens.spacingSm,
                    top = Dimens.spacingSm
                )
            ) {
                actions.forEachIndexed { index, action ->
                    FileActionItem(
                        action = action,
                        isFocused = isFocused && index == focusedIndex,
                        onClick = { onActionClick(action) }
                    )
                }
            }
        }
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            FilePaneContent(
                entries = entries,
                focusedEntryIndex = if (isFocused) focusedEntryIndex else -1,
                isLoading = isLoading,
                errorRes = errorRes,
                listState = listState,
                onEntryClick = onEntryClick
            )
        }
    }
}

@Composable
private fun FilePaneContent(
    entries: List<FileEntry>,
    focusedEntryIndex: Int,
    isLoading: Boolean,
    @StringRes errorRes: Int?,
    listState: LazyListState,
    onEntryClick: (FileEntry) -> Unit
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
        errorRes != null -> {
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
                    text = stringResource(errorRes),
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
                    text = stringResource(R.string.ui_file_browser_empty_folder),
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
                    bottom = Dimens.footerClearance
                )
            ) {
                itemsIndexed(entries, key = { _, e -> e.path }) { index, entry ->
                    FileItem(
                        entry = entry,
                        isFocused = index == focusedEntryIndex,
                        onClick = { onEntryClick(entry) }
                    )
                }
            }
        }
    }
}

@Composable
private fun FileActionItem(
    action: FileBrowserAction,
    isFocused: Boolean,
    onClick: () -> Unit
) {
    val (icon, labelRes) = when (action) {
        FileBrowserAction.USE_FOLDER -> Icons.Default.Check to R.string.ui_file_browser_action_use_folder
        FileBrowserAction.NEW_FOLDER -> Icons.Default.CreateNewFolder to R.string.ui_file_browser_action_new_folder
    }
    FileBrowserRow(
        icon = icon,
        restIconTint = MaterialTheme.colorScheme.primary,
        label = stringResource(labelRes),
        trailing = null,
        isFocused = isFocused,
        onClick = onClick
    )
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
    FileBrowserRow(
        icon = icon,
        restIconTint = if (entry.isDirectory) {
            MaterialTheme.colorScheme.primary
        } else {
            MaterialTheme.colorScheme.onSurfaceVariant
        },
        label = entry.name,
        trailing = if (!entry.isDirectory && !entry.isParentLink) formatBytes(entry.size) else null,
        isFocused = isFocused,
        onClick = onClick
    )
}

@Composable
private fun FileBrowserRow(
    icon: ImageVector,
    restIconTint: Color,
    label: String,
    trailing: String?,
    isFocused: Boolean,
    onClick: () -> Unit
) {
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

    val iconTint = if (isFocused) contentColor else restIconTint

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
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.bodySmall,
                color = contentColor.copy(alpha = 0.6f)
            )
        }
    }
}

@Composable
private fun FileBrowserFooter(
    actions: List<FileBrowserAction>,
    onAction: (FileBrowserAction) -> Unit
) {
    val newFolderHint = stringResource(R.string.ui_file_browser_footer_new_folder)
    val useFolderHint = stringResource(R.string.ui_file_browser_footer_use_folder)
    val hints = buildList {
        if (FileBrowserAction.NEW_FOLDER in actions) add(InputButton.Y to newFolderHint)
        if (FileBrowserAction.USE_FOLDER in actions) add(InputButton.X to useFolderHint)
    }

    FooterHints(
        hints = hints,
        onHintClick = { button ->
            when (button) {
                InputButton.Y -> onAction(FileBrowserAction.NEW_FOLDER)
                InputButton.X -> onAction(FileBrowserAction.USE_FOLDER)
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
    @StringRes errorRes: Int?,
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
    var keyboardDismissed by remember { mutableStateOf(false) }
    var systemKeyboardActive by remember { mutableStateOf(false) }
    val reopenKeyboard = {
        if (systemKeyboardActive) focusManager.clearFocus()
        focusRow = FOLDER_ROW_FIELD
        keyboardDismissed = false
    }
    val currentReopenKeyboard by rememberUpdatedState(reopenKeyboard)

    LaunchedEffect(focusRow) {
        if (focusRow != FOLDER_ROW_FIELD) {
            keyboardDismissed = false
            focusManager.clearFocus()
        }
    }

    ControllerKeyboardSession(
        open = focusRow == FOLDER_ROW_FIELD && !keyboardDismissed,
        label = stringResource(R.string.ui_file_browser_new_folder_field_label),
        text = folderName,
        onTextChange = onFolderNameChange,
        onSubmit = {
            keyboardDismissed = true
            if (canCreate) onConfirm()
        },
        onClose = { keyboardDismissed = true },
        submitLabel = stringResource(R.string.ui_file_browser_new_folder_keyboard_submit),
        error = errorRes?.let { stringResource(it) },
        canSubmit = canCreate,
        onSystemKeyboard = {
            keyboardDismissed = true
            focusRequester.requestFocus()
        }
    )

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
                    focusRow == FOLDER_ROW_FIELD -> currentReopenKeyboard()
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
    Modal(
        title = stringResource(R.string.ui_file_browser_new_folder_title),
        onDismiss = onDismiss
    ) {
        ControllerKeyboardTapTarget(
            onTap = reopenKeyboard,
            modifier = Modifier.fillMaxWidth()
        ) {
            OutlinedTextField(
                value = folderName,
                onValueChange = onFolderNameChange,
                label = { Text(stringResource(R.string.ui_file_browser_new_folder_field_label)) },
                singleLine = true,
                isError = errorRes != null,
                shape = fieldShape,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (canCreate) onConfirm() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .onFocusChanged {
                        systemKeyboardActive = it.isFocused
                        if (it.isFocused) keyboardDismissed = true
                    }
                    .then(
                        if (focusRow == FOLDER_ROW_FIELD) {
                            Modifier.background(theme.focusAccent.copy(alpha = 0.15f), fieldShape)
                        } else Modifier
                    )
            )
        }
        if (errorRes != null) {
            Spacer(modifier = Modifier.height(Dimens.spacingSm))
            Text(
                text = stringResource(errorRes),
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
                label = stringResource(R.string.ui_file_browser_new_folder_cancel),
                tint = theme.focusAccent,
                restLabelColor = theme.textPrimary,
                focused = focusRow == FOLDER_ROW_BUTTONS && buttonIndex == 0,
                onClick = onDismiss
            )
            ModalActionButton(
                label = stringResource(R.string.ui_file_browser_new_folder_confirm),
                tint = theme.focusAccent,
                restLabelColor = theme.textPrimary,
                focused = focusRow == FOLDER_ROW_BUTTONS && buttonIndex == 1,
                onClick = onConfirm,
                enabled = canCreate
            )
        }
    }
}

