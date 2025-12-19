package com.nendo.argosy.ui.screens.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.nendo.argosy.ui.components.FooterBar
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.navigation.Screen
import com.nendo.argosy.ui.screens.settings.components.PlatformSettingsModal
import com.nendo.argosy.ui.screens.settings.components.SoundPickerPopup
import com.nendo.argosy.ui.screens.settings.sections.AboutSection
import com.nendo.argosy.ui.screens.settings.sections.BoxArtSection
import com.nendo.argosy.ui.screens.settings.sections.ControlsSection
import com.nendo.argosy.ui.screens.settings.sections.DisplaySection
import com.nendo.argosy.ui.screens.settings.sections.EmulatorsSection
import com.nendo.argosy.ui.screens.settings.sections.GameDataSection
import com.nendo.argosy.ui.screens.settings.sections.HomeScreenSection
import com.nendo.argosy.ui.screens.settings.sections.MainSettingsSection
import com.nendo.argosy.ui.screens.settings.sections.SoundsSection
import com.nendo.argosy.ui.screens.settings.sections.SteamSection
import com.nendo.argosy.ui.screens.settings.sections.StorageSection
import com.nendo.argosy.ui.screens.settings.sections.SyncFiltersSection
import com.nendo.argosy.ui.screens.settings.sections.SyncSettingsSection
import com.nendo.argosy.ui.screens.settings.sections.formatFileSize
import com.nendo.argosy.ui.theme.Motion

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val imageCacheProgress by viewModel.imageCacheProgress.collectAsState()
    val context = LocalContext.current

    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            val filePath = getFilePathFromUri(context, it)
            if (filePath != null) {
                viewModel.setStoragePath(filePath)
            }
        }
    }

    val backgroundPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let {
            try {
                context.contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: SecurityException) {
                // Ignore if permission can't be persisted
            }
            viewModel.setCustomBackgroundPath(it.toString())
        }
    }

    val logFolderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            context.contentResolver.takePersistableUriPermission(
                it,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            val filePath = getFilePathFromUri(context, it)
            if (filePath != null) {
                viewModel.setFileLoggingPath(filePath)
            }
        }
    }

    var pendingPlatformId by remember { mutableStateOf<String?>(null) }
    val platformFolderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        val platformId = pendingPlatformId
        if (uri != null && platformId != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            val filePath = getFilePathFromUri(context, uri)
            if (filePath != null) {
                viewModel.setPlatformPath(platformId, filePath)
            }
        }
        pendingPlatformId = null
    }

    var pendingSavePathEmulatorId by remember { mutableStateOf<String?>(null) }
    val savePathFolderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        val emulatorId = pendingSavePathEmulatorId
        if (uri != null && emulatorId != null) {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
            val filePath = getFilePathFromUri(context, uri)
            if (filePath != null) {
                viewModel.setEmulatorSavePath(emulatorId, filePath)
            }
        }
        pendingSavePathEmulatorId = null
    }

    val inputDispatcher = LocalInputDispatcher.current
    val inputHandler = remember(onBack) {
        viewModel.createInputHandler(onBack = onBack)
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, inputHandler) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                inputDispatcher.subscribeView(inputHandler, forRoute = Screen.ROUTE_SETTINGS)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        inputDispatcher.subscribeView(inputHandler, forRoute = Screen.ROUTE_SETTINGS)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(uiState.launchFolderPicker) {
        if (uiState.launchFolderPicker) {
            folderPickerLauncher.launch(null)
            viewModel.clearFolderPickerFlag()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.openLogFolderPickerEvent.collect {
            logFolderPickerLauncher.launch(null)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.openUrlEvent.collect { url ->
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.downloadUpdateEvent.collect {
            viewModel.downloadAndInstallUpdate(context)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.requestStoragePermissionEvent.collect {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${context.packageName}")
                }
                context.startActivity(intent)
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.openBackgroundPickerEvent.collect {
            backgroundPickerLauncher.launch(arrayOf("image/*"))
        }
    }

    LaunchedEffect(Unit) {
        viewModel.launchPlatformFolderPicker.collect { platformId ->
            pendingPlatformId = platformId
            platformFolderPickerLauncher.launch(null)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.launchSavePathPicker.collect {
            uiState.emulators.savePathModalInfo?.emulatorId?.let { emulatorId ->
                pendingSavePathEmulatorId = emulatorId
                savePathFolderPickerLauncher.launch(null)
            }
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkStoragePermission()
                if (viewModel.uiState.value.currentSection == SettingsSection.EMULATORS) {
                    viewModel.refreshEmulators()
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val soundPickerBlur by animateDpAsState(
        targetValue = if (uiState.sounds.showSoundPicker) Motion.blurRadiusModal else 0.dp,
        animationSpec = Motion.focusSpringDp,
        label = "soundPickerBlur"
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .blur(soundPickerBlur)
                .background(MaterialTheme.colorScheme.background)
        ) {
            SettingsHeader(
                title = when (uiState.currentSection) {
                    SettingsSection.MAIN -> "SETTINGS"
                    SettingsSection.SERVER -> "GAME DATA"
                    SettingsSection.SYNC_SETTINGS -> "SYNC SETTINGS"
                    SettingsSection.SYNC_FILTERS -> "METADATA FILTERS"
                    SettingsSection.STEAM_SETTINGS -> "STEAM (EXPERIMENTAL)"
                    SettingsSection.STORAGE -> "STORAGE"
                    SettingsSection.DISPLAY -> "DISPLAY"
                    SettingsSection.BOX_ART -> "BOX ART"
                    SettingsSection.HOME_SCREEN -> "HOME SCREEN"
                    SettingsSection.CONTROLS -> "CONTROLS"
                    SettingsSection.SOUNDS -> "SOUNDS"
                    SettingsSection.EMULATORS -> "EMULATORS"
                    SettingsSection.ABOUT -> "ABOUT"
                }
            )

            Box(modifier = Modifier.weight(1f)) {
                when (uiState.currentSection) {
                    SettingsSection.MAIN -> MainSettingsSection(uiState, viewModel)
                    SettingsSection.SERVER -> GameDataSection(uiState, viewModel)
                    SettingsSection.SYNC_SETTINGS -> SyncSettingsSection(uiState, viewModel, imageCacheProgress)
                    SettingsSection.SYNC_FILTERS -> SyncFiltersSection(uiState, viewModel)
                    SettingsSection.STEAM_SETTINGS -> SteamSection(uiState, viewModel)
                    SettingsSection.STORAGE -> StorageSection(uiState, viewModel)
                    SettingsSection.DISPLAY -> DisplaySection(uiState, viewModel)
                    SettingsSection.BOX_ART -> BoxArtSection(uiState, viewModel)
                    SettingsSection.HOME_SCREEN -> HomeScreenSection(uiState, viewModel)
                    SettingsSection.CONTROLS -> ControlsSection(uiState, viewModel)
                    SettingsSection.SOUNDS -> SoundsSection(uiState, viewModel)
                    SettingsSection.EMULATORS -> EmulatorsSection(
                        uiState = uiState,
                        viewModel = viewModel,
                        onLaunchSavePathPicker = {
                            uiState.emulators.savePathModalInfo?.emulatorId?.let { emulatorId ->
                                pendingSavePathEmulatorId = emulatorId
                                savePathFolderPickerLauncher.launch(null)
                            }
                        }
                    )
                    SettingsSection.ABOUT -> AboutSection(uiState, viewModel)
                }
            }

            SettingsFooter(uiState)
        }

        AnimatedVisibility(
            visible = uiState.sounds.showSoundPicker && uiState.sounds.soundPickerType != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            uiState.sounds.soundPickerType?.let { soundType ->
                SoundPickerPopup(
                    soundType = soundType,
                    presets = uiState.sounds.presets,
                    focusIndex = uiState.sounds.soundPickerFocusIndex,
                    currentPreset = uiState.sounds.getCurrentPresetForType(soundType),
                    onConfirm = { viewModel.confirmSoundPickerSelection() },
                    onDismiss = { viewModel.dismissSoundPicker() }
                )
            }
        }

        AnimatedVisibility(
            visible = uiState.storage.platformSettingsModalId != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            uiState.storage.platformSettingsModalId?.let { platformId ->
                val config = uiState.storage.platformConfigs.find { it.platformId == platformId }
                if (config != null) {
                    PlatformSettingsModal(
                        config = config,
                        focusIndex = uiState.storage.platformSettingsFocusIndex,
                        onDismiss = { viewModel.closePlatformSettingsModal() },
                        onToggleSync = { viewModel.togglePlatformSync(platformId, !config.syncEnabled) },
                        onChangePath = { viewModel.openPlatformFolderPicker(platformId) },
                        onResetPath = { viewModel.resetPlatformToGlobal(platformId) },
                        onPurge = { viewModel.requestPurgePlatform(platformId) }
                    )
                }
            }
        }
    }

    if (uiState.showMigrationDialog) {
        val sizeText = formatFileSize(uiState.storage.downloadedGamesSize)
        AlertDialog(
            onDismissRequest = { viewModel.cancelMigration() },
            title = { Text("Migrate Downloads?") },
            text = {
                Text("Move ${uiState.storage.downloadedGamesCount} games ($sizeText) to the new location?")
            },
            confirmButton = {
                Button(onClick = { viewModel.confirmMigration() }) {
                    Text("Migrate")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { viewModel.cancelMigration() }) {
                        Text("Cancel")
                    }
                    TextButton(onClick = { viewModel.skipMigration() }) {
                        Text("Skip")
                    }
                }
            }
        )
    }

    uiState.storage.showMigratePlatformConfirm?.let { info ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelPlatformMigration() },
            title = { Text("Migrate ${info.platformName} ROMs?") },
            text = {
                Text("Move downloaded games to the new location? Files will be copied and then removed from the old location.")
            },
            confirmButton = {
                Button(onClick = { viewModel.confirmPlatformMigration() }) {
                    Text("Migrate")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { viewModel.cancelPlatformMigration() }) {
                        Text("Cancel")
                    }
                    TextButton(onClick = { viewModel.skipPlatformMigration() }) {
                        Text("Skip")
                    }
                }
            }
        )
    }

    uiState.storage.showPurgePlatformConfirm?.let { platformId ->
        val config = uiState.storage.platformConfigs.find { it.platformId == platformId }
        AlertDialog(
            onDismissRequest = { viewModel.cancelPurgePlatform() },
            title = { Text("Purge ${config?.platformName ?: "Platform"}?") },
            text = {
                Text("This will delete all ${config?.gameCount ?: 0} games and their local ROM files. This cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmPurgePlatform() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Purge")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelPurgePlatform() }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun SettingsHeader(title: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Suppress("UNUSED_PARAMETER")
private fun getFilePathFromUri(context: Context, uri: Uri): String? {
    val rawPath = uri.path ?: return null
    val path = Uri.decode(rawPath)

    // Tree URIs have format: /tree/primary:path/to/folder
    // or /tree/primary:path/to/folder/document/primary:path/to/folder
    val treePath = path.substringAfter("/tree/", "")
        .substringBefore("/document/") // Handle document URIs
    if (treePath.isEmpty()) return null

    return when {
        treePath.startsWith("primary:") -> {
            val relativePath = treePath.removePrefix("primary:")
            if (relativePath.isEmpty()) {
                Environment.getExternalStorageDirectory().absolutePath
            } else {
                "${Environment.getExternalStorageDirectory().absolutePath}/$relativePath"
            }
        }
        treePath.contains(":") -> {
            // External SD card: storage-id:path
            val parts = treePath.split(":", limit = 2)
            if (parts.size == 2) {
                val storageId = parts[0]
                val subPath = parts[1]
                if (subPath.isEmpty()) {
                    "/storage/$storageId"
                } else {
                    "/storage/$storageId/$subPath"
                }
            } else null
        }
        else -> null
    }
}

@Composable
private fun SettingsFooter(uiState: SettingsUiState) {
    if (uiState.emulators.showSavePathModal || uiState.emulators.showEmulatorPicker) {
        return
    }

    val hints = buildList {
        add(InputButton.DPAD to "Navigate")
        if (uiState.currentSection == SettingsSection.BOX_ART) {
            add(InputButton.LB_RB to "Preview Size")
        }
        add(InputButton.SOUTH to "Select")
        if (uiState.currentSection == SettingsSection.EMULATORS) {
            add(InputButton.WEST to "Saves")
        }
        add(InputButton.EAST to "Back")
    }

    FooterBar(hints = hints)
}

