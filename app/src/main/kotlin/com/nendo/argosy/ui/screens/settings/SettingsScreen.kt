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
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
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
import com.nendo.argosy.ui.theme.Dimens
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.nendo.argosy.ui.components.FooterBar
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.ui.filebrowser.FileBrowserMode
import com.nendo.argosy.ui.filebrowser.FileBrowserScreen
import com.nendo.argosy.ui.filebrowser.FileFilter
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.navigation.Screen
import com.nendo.argosy.ui.screens.settings.components.PlatformSettingsModal
import com.nendo.argosy.ui.screens.settings.components.SoundPickerPopup
import com.nendo.argosy.ui.screens.settings.delegates.BuiltinNavigationTarget
import com.nendo.argosy.ui.screens.settings.sections.AboutSection
import com.nendo.argosy.ui.screens.settings.sections.BiosSection
import com.nendo.argosy.ui.screens.settings.sections.BoxArtSection
import com.nendo.argosy.ui.screens.settings.sections.ControlsSection
import com.nendo.argosy.ui.screens.settings.sections.EmulatorsSection
import com.nendo.argosy.ui.screens.settings.sections.BuiltinVideoSection
import com.nendo.argosy.ui.screens.settings.sections.BuiltinControlsSection
import com.nendo.argosy.ui.screens.settings.sections.CoreManagementSection
import com.nendo.argosy.ui.screens.settings.sections.GameDataSection
import com.nendo.argosy.ui.screens.settings.sections.HomeScreenSection
import com.nendo.argosy.ui.screens.settings.sections.InterfaceSection
import com.nendo.argosy.ui.screens.settings.sections.MainSettingsSection
import com.nendo.argosy.ui.screens.settings.sections.PermissionsSection
import com.nendo.argosy.ui.screens.settings.sections.RASettingsSection
import com.nendo.argosy.ui.screens.settings.sections.ShaderStackSection
import com.nendo.argosy.ui.screens.settings.sections.SteamSection
import com.nendo.argosy.ui.screens.settings.sections.StorageSection
import com.nendo.argosy.ui.screens.settings.sections.SyncSettingsSection
import com.nendo.argosy.ui.screens.settings.sections.formatFileSize
import com.nendo.argosy.ui.icons.InputIcons
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.util.clickableNoFocus

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    initialSection: String? = null,
    initialAction: String? = null,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val imageCacheProgress by viewModel.imageCacheProgress.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(initialSection, initialAction) {
        if (initialSection != null) {
            val section = SettingsSection.entries.find { it.name == initialSection }
            if (section != null) {
                viewModel.navigateToSection(section)
                kotlinx.coroutines.delay(300)
                when (initialAction) {
                    "rommConfig" -> viewModel.startRommConfig()
                    "syncLibrary" -> viewModel.setFocusIndex(2)
                }
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

    val audioFilePickerLauncher = rememberLauncherForActivityResult(
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
            viewModel.setAmbientAudioUri(it.toString())
        }
    }

    var showFileBrowser by remember { mutableStateOf(false) }
    var fileBrowserCallback by remember { mutableStateOf<((String) -> Unit)?>(null) }

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

    LaunchedEffect(uiState.currentSection) {
        inputDispatcher.blockInputFor(Motion.transitionDebounceMs)
    }

    LaunchedEffect(uiState.launchFolderPicker) {
        if (uiState.launchFolderPicker) {
            fileBrowserCallback = { path -> viewModel.setStoragePath(path) }
            showFileBrowser = true
            viewModel.clearFolderPickerFlag()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.openLogFolderPickerEvent.collect {
            fileBrowserCallback = { path -> viewModel.setFileLoggingPath(path) }
            showFileBrowser = true
        }
    }

    LaunchedEffect(Unit) {
        viewModel.openUrlEvent.collect { url ->
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
            context.startActivity(intent)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.openDeviceSettingsEvent.collect {
            context.startActivity(Intent(Settings.ACTION_SETTINGS))
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
        viewModel.requestScreenCapturePermissionEvent.collect {
            (context as? com.nendo.argosy.MainActivity)?.requestScreenCapturePermission()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.openBackgroundPickerEvent.collect {
            backgroundPickerLauncher.launch(arrayOf("image/*"))
        }
    }

    LaunchedEffect(Unit) {
        viewModel.openAudioFilePickerEvent.collect {
            audioFilePickerLauncher.launch(arrayOf("audio/*"))
        }
    }

    var showAudioFileBrowser by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.openAudioFileBrowserEvent.collect {
            showAudioFileBrowser = true
        }
    }

    LaunchedEffect(Unit) {
        viewModel.launchPlatformFolderPicker.collect { platformId ->
            fileBrowserCallback = { path -> viewModel.setPlatformPath(platformId, path) }
            showFileBrowser = true
        }
    }

    LaunchedEffect(Unit) {
        viewModel.launchSavePathPicker.collect {
            uiState.emulators.savePathModalInfo?.emulatorId?.let { emulatorId ->
                fileBrowserCallback = { path -> viewModel.setEmulatorSavePath(emulatorId, path) }
                showFileBrowser = true
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.launchPlatformSavePathPicker.collect { platformId ->
            fileBrowserCallback = { path -> viewModel.setPlatformSavePath(platformId, path) }
            showFileBrowser = true
        }
    }

    LaunchedEffect(Unit) {
        viewModel.resetPlatformSavePathEvent.collect { platformId ->
            viewModel.resetPlatformSavePath(platformId)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.launchPlatformStatePathPicker.collect { platformId ->
            fileBrowserCallback = { path -> viewModel.setPlatformStatePath(platformId, path) }
            showFileBrowser = true
        }
    }

    LaunchedEffect(Unit) {
        viewModel.builtinNavigationEvent.collect { target ->
            when (target) {
                BuiltinNavigationTarget.VIDEO_SETTINGS -> viewModel.navigateToSection(SettingsSection.BUILTIN_VIDEO)
                BuiltinNavigationTarget.CONTROLS_SETTINGS -> viewModel.navigateToSection(SettingsSection.BUILTIN_CONTROLS)
                BuiltinNavigationTarget.CORE_MANAGEMENT -> {
                    viewModel.loadCoreManagementState()
                    viewModel.navigateToSection(SettingsSection.CORE_MANAGEMENT)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.resetPlatformStatePathEvent.collect { platformId ->
            viewModel.resetPlatformStatePath(platformId)
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.checkStoragePermission()
                viewModel.refreshPermissions()
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
            if (uiState.currentSection != SettingsSection.SHADER_STACK) {
                SettingsHeader(
                    title = when (uiState.currentSection) {
                        SettingsSection.MAIN -> "SETTINGS"
                        SettingsSection.SERVER -> "GAME DATA"
                        SettingsSection.SYNC_SETTINGS -> "SYNC SETTINGS"
                        SettingsSection.STEAM_SETTINGS -> "STEAM (EXPERIMENTAL)"
                        SettingsSection.RETRO_ACHIEVEMENTS -> "RETROACHIEVEMENTS"
                        SettingsSection.STORAGE -> "STORAGE"
                        SettingsSection.INTERFACE -> "INTERFACE"
                        SettingsSection.BOX_ART -> "BOX ART"
                        SettingsSection.HOME_SCREEN -> "HOME SCREEN"
                        SettingsSection.CONTROLS -> "CONTROLS"
                        SettingsSection.EMULATORS -> "EMULATORS"
                        SettingsSection.BUILTIN_VIDEO -> "BUILT-IN VIDEO"
                        SettingsSection.BUILTIN_CONTROLS -> "BUILT-IN CONTROLS"
                        SettingsSection.CORE_MANAGEMENT -> "MANAGE CORES"
                        SettingsSection.BIOS -> "BIOS FILES"
                        SettingsSection.SHADER_STACK -> "SHADER CHAIN"
                        SettingsSection.PERMISSIONS -> "PERMISSIONS"
                        SettingsSection.ABOUT -> "ABOUT"
                    },
                    rightContent = if ((uiState.currentSection == SettingsSection.BUILTIN_VIDEO ||
                        uiState.currentSection == SettingsSection.BUILTIN_CONTROLS) &&
                        uiState.builtinVideo.availablePlatforms.isNotEmpty()) {
                        {
                            val platformName = if (uiState.builtinVideo.isGlobalContext) {
                                "Global"
                            } else {
                                uiState.builtinVideo.currentPlatformContext?.platformName ?: "Global"
                            }
                            PlatformContextIndicator(
                                platformName = platformName,
                                onPrevious = { viewModel.cyclePlatformContext(-1) },
                                onNext = { viewModel.cyclePlatformContext(1) }
                            )
                        }
                    } else null
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                when (uiState.currentSection) {
                    SettingsSection.MAIN -> MainSettingsSection(uiState, viewModel)
                    SettingsSection.SERVER -> GameDataSection(uiState, viewModel)
                    SettingsSection.SYNC_SETTINGS -> SyncSettingsSection(uiState, viewModel, imageCacheProgress)
                    SettingsSection.STEAM_SETTINGS -> SteamSection(uiState, viewModel)
                    SettingsSection.RETRO_ACHIEVEMENTS -> RASettingsSection(uiState, viewModel)
                    SettingsSection.STORAGE -> StorageSection(uiState, viewModel)
                    SettingsSection.INTERFACE -> InterfaceSection(uiState, viewModel)
                    SettingsSection.BOX_ART -> BoxArtSection(uiState, viewModel)
                    SettingsSection.HOME_SCREEN -> HomeScreenSection(uiState, viewModel)
                    SettingsSection.CONTROLS -> ControlsSection(uiState, viewModel)
                    SettingsSection.EMULATORS -> EmulatorsSection(
                        uiState = uiState,
                        viewModel = viewModel,
                        onLaunchSavePathPicker = {
                            uiState.emulators.savePathModalInfo?.emulatorId?.let { emulatorId ->
                                fileBrowserCallback = { path -> viewModel.setEmulatorSavePath(emulatorId, path) }
                                showFileBrowser = true
                            }
                        }
                    )
                    SettingsSection.BUILTIN_VIDEO -> BuiltinVideoSection(uiState, viewModel)
                    SettingsSection.BUILTIN_CONTROLS -> BuiltinControlsSection(uiState, viewModel)
                    SettingsSection.CORE_MANAGEMENT -> CoreManagementSection(uiState, viewModel)
                    SettingsSection.BIOS -> BiosSection(uiState, viewModel)
                    SettingsSection.SHADER_STACK -> ShaderStackSection(uiState, viewModel)
                    SettingsSection.PERMISSIONS -> PermissionsSection(uiState, viewModel)
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
                        buttonFocusIndex = uiState.storage.platformSettingsButtonIndex,
                        onDismiss = { viewModel.closePlatformSettingsModal() },
                        onToggleSync = { viewModel.togglePlatformSync(platformId, !config.syncEnabled) },
                        onChangeRomPath = { viewModel.openPlatformFolderPicker(platformId) },
                        onResetRomPath = { viewModel.resetPlatformToGlobal(platformId) },
                        onChangeSavePath = { viewModel.openPlatformSavePathPicker(platformId) },
                        onResetSavePath = { viewModel.resetPlatformSavePath(platformId) },
                        onChangeStatePath = { },
                        onResetStatePath = { },
                        onResync = { viewModel.syncPlatform(platformId, config.platformName) },
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
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)) {
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
                Row(horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)) {
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

    if (uiState.storage.showPurgeAllConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.cancelPurgeAll() },
            title = { Text("Reset Library?") },
            text = {
                Text("This will clear all metadata, platforms, and cached images. Downloaded ROM files will be preserved. You will need to re-sync your library.")
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.confirmPurgeAll() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelPurgeAll() }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showFileBrowser) {
        FileBrowserScreen(
            mode = FileBrowserMode.FOLDER_SELECTION,
            onPathSelected = { path ->
                showFileBrowser = false
                fileBrowserCallback?.invoke(path)
                fileBrowserCallback = null
            },
            onDismiss = {
                showFileBrowser = false
                fileBrowserCallback = null
            }
        )
    }

    if (showAudioFileBrowser) {
        FileBrowserScreen(
            mode = FileBrowserMode.FILE_SELECTION,
            fileFilter = FileFilter.AUDIO,
            onPathSelected = { path ->
                showAudioFileBrowser = false
                viewModel.setAmbientAudioFilePath(path)
            },
            onDismiss = {
                showAudioFileBrowser = false
            }
        )
    }

    var showImageCacheBrowser by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.openImageCachePickerEvent.collect {
            showImageCacheBrowser = true
        }
    }

    if (showImageCacheBrowser) {
        FileBrowserScreen(
            mode = FileBrowserMode.FOLDER_SELECTION,
            onPathSelected = { path ->
                showImageCacheBrowser = false
                viewModel.setImageCachePath(path)
            },
            onDismiss = {
                showImageCacheBrowser = false
            }
        )
    }

    var showBiosFolderBrowser by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.launchBiosFolderPicker.collect {
            showBiosFolderBrowser = true
        }
    }

    if (showBiosFolderBrowser) {
        FileBrowserScreen(
            mode = FileBrowserMode.FOLDER_SELECTION,
            onPathSelected = { path ->
                showBiosFolderBrowser = false
                viewModel.onBiosFolderSelected(path)
            },
            onDismiss = {
                showBiosFolderBrowser = false
            }
        )
    }
}

@Composable
private fun SettingsHeader(
    title: String,
    rightContent: @Composable (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = Dimens.spacingLg, vertical = Dimens.spacingMd),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        rightContent?.invoke()
    }
}

@Composable
private fun PlatformContextIndicator(
    platformName: String,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingMd)
    ) {
        Row(
            modifier = Modifier
                .clickableNoFocus(onClick = onPrevious)
                .padding(Dimens.spacingXs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = InputIcons.BumperLeft,
                contentDescription = "Previous context",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Dimens.iconSm)
            )
        }

        Text(
            text = platformName,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary
        )

        Row(
            modifier = Modifier
                .clickableNoFocus(onClick = onNext)
                .padding(Dimens.spacingXs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                painter = InputIcons.BumperRight,
                contentDescription = "Next context",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Dimens.iconSm)
            )
        }
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
        if (uiState.currentSection != SettingsSection.BOX_ART &&
            uiState.currentSection != SettingsSection.SHADER_STACK) {
            add(InputButton.DPAD to "Navigate")
        }
        if (uiState.currentSection == SettingsSection.SHADER_STACK &&
            uiState.shaderStack.entries.isNotEmpty() &&
            uiState.shaderStack.selectedShaderParams.isNotEmpty()
        ) {
            add(InputButton.DPAD_VERTICAL to "Navigate")
        }
        if (uiState.currentSection == SettingsSection.BOX_ART) {
            add(InputButton.LB_RB to "Preview Shape")
            add(InputButton.LT_RT to "Preview Game")
        }
        if (uiState.currentSection == SettingsSection.SHADER_STACK) {
            if (uiState.shaderStack.entries.isNotEmpty()) {
                add(InputButton.LB_RB to "Shader")
                add(InputButton.LT_RT to "Reorder")
                if (uiState.shaderStack.selectedShaderParams.isNotEmpty()) {
                    add(InputButton.DPAD_HORIZONTAL to "Adjust")
                }
            }
            add(InputButton.X to "Add")
            if (uiState.shaderStack.entries.isNotEmpty()) {
                add(InputButton.Y to "Remove")
            }
        }
        if ((uiState.currentSection == SettingsSection.BUILTIN_VIDEO ||
            uiState.currentSection == SettingsSection.BUILTIN_CONTROLS) &&
            uiState.builtinVideo.availablePlatforms.isNotEmpty()) {
            add(InputButton.LB_RB to "Platform")
        }
        add(InputButton.A to "Select")
        if (uiState.currentSection == SettingsSection.EMULATORS) {
            add(InputButton.X to "Saves")
        }
        if (uiState.currentSection == SettingsSection.BUILTIN_VIDEO && !uiState.builtinVideo.isGlobalContext) {
            val platformContext = uiState.builtinVideo.currentPlatformContext
            val platformSettings = platformContext?.let { uiState.platformLibretro.platformSettings[it.platformId] }
            val currentSetting = com.nendo.argosy.ui.screens.settings.sections.builtinVideoItemAtFocusIndex(
                uiState.focusedIndex, uiState.builtinVideo
            )
            val accessor = com.nendo.argosy.ui.screens.settings.libretro.PlatformLibretroSettingsAccessor(
                platformSettings = platformSettings,
                globalState = uiState.builtinVideo,
                onUpdate = { _, _ -> }
            )
            if (currentSetting != null && accessor.hasOverride(currentSetting)) {
                add(InputButton.X to "Reset")
            }
        }
        if (uiState.currentSection == SettingsSection.BUILTIN_CONTROLS && !uiState.builtinVideo.isGlobalContext) {
            val item = com.nendo.argosy.ui.screens.settings.sections.builtinControlsItemAtFocusIndex(
                uiState.focusedIndex, uiState.builtinControls
            )
            val platformContext = uiState.builtinVideo.currentPlatformContext
            val ps = platformContext?.let { uiState.platformLibretro.platformSettings[it.platformId] }
            val hasOverride = when (item) {
                com.nendo.argosy.ui.screens.settings.sections.BuiltinControlsItem.Rumble -> ps?.rumbleEnabled != null
                com.nendo.argosy.ui.screens.settings.sections.BuiltinControlsItem.AnalogAsDpad -> ps?.analogAsDpad != null
                com.nendo.argosy.ui.screens.settings.sections.BuiltinControlsItem.DpadAsAnalog -> ps?.dpadAsAnalog != null
                else -> false
            }
            if (hasOverride) {
                add(InputButton.X to "Reset")
            }
        }
        add(InputButton.B to "Back")
    }

    FooterBar(hints = hints)
}

