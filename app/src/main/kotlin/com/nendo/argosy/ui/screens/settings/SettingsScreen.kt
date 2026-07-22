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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import com.nendo.argosy.ui.theme.backdrop.BackdropRole
import com.nendo.argosy.ui.theme.backdrop.surfaceBackdrop
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.nendo.argosy.ui.components.FooterHints
import com.nendo.argosy.ui.components.FooterSpacer
import com.nendo.argosy.ui.components.InputButton
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.filebrowser.FileBrowserMode
import com.nendo.argosy.ui.filebrowser.FileBrowserScreen
import com.nendo.argosy.ui.filebrowser.FileFilter
import com.nendo.argosy.ui.input.LocalInputDispatcher
import com.nendo.argosy.ui.navigation.Screen
import com.nendo.argosy.ui.primitives.ArgosyConfirmModal
import com.nendo.argosy.ui.primitives.ArgosyConfirmModalHost
import com.nendo.argosy.util.formatBytes
import com.nendo.argosy.ui.screens.musicbrowser.MusicBrowserMode
import com.nendo.argosy.ui.screens.musicbrowser.MusicBrowserScreen
import com.nendo.argosy.data.storage.StorageCategory
import com.nendo.argosy.ui.screens.settings.components.HardResetModal
import com.nendo.argosy.ui.screens.settings.components.PlatformSettingsModal
import com.nendo.argosy.ui.screens.settings.components.ReleaseChangelogModal
import com.nendo.argosy.ui.screens.settings.components.SoundPickerPopup
import com.nendo.argosy.ui.screens.settings.delegates.BuiltinNavigationTarget
import com.nendo.argosy.ui.screens.settings.sections.AboutSection
import com.nendo.argosy.ui.screens.settings.sections.BiosSection
import com.nendo.argosy.ui.screens.settings.sections.DistributeResultModal
import com.nendo.argosy.ui.screens.settings.sections.DriversSection
import com.nendo.argosy.ui.screens.settings.sections.AmbientLedSection
import com.nendo.argosy.ui.screens.settings.sections.BoxArtSection
import com.nendo.argosy.ui.screens.settings.sections.ControlsSection
import com.nendo.argosy.ui.screens.settings.sections.BuiltinEmulatorSection
import com.nendo.argosy.ui.screens.settings.sections.EmulatorsSection
import com.nendo.argosy.ui.screens.settings.sections.PlatformDetailSection
import com.nendo.argosy.ui.screens.settings.sections.PlatformDetailItem
import com.nendo.argosy.ui.screens.settings.sections.platformDetailItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.FrameSection
import com.nendo.argosy.ui.screens.settings.sections.BuiltinVideoSection
import com.nendo.argosy.ui.screens.settings.sections.BuiltinControlsSection
import com.nendo.argosy.ui.screens.settings.sections.CoreManagementSection
import com.nendo.argosy.ui.screens.settings.sections.CoreOptionItem
import com.nendo.argosy.ui.screens.settings.sections.CoreOptionsSection
import com.nendo.argosy.ui.screens.settings.sections.coreOptionsItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.GameDataSection
import com.nendo.argosy.ui.screens.settings.sections.HomeScreenSection
import com.nendo.argosy.ui.screens.settings.sections.InterfaceSection
import com.nendo.argosy.ui.screens.settings.sections.MainSettingsSection
import com.nendo.argosy.ui.screens.settings.sections.PermissionsSection
import com.nendo.argosy.ui.screens.settings.sections.RASettingsSection
import com.nendo.argosy.ui.screens.settings.sections.ShaderStackSection
import com.nendo.argosy.ui.screens.settings.sections.SocialSection
import com.nendo.argosy.ui.screens.settings.sections.SteamSection
import com.nendo.argosy.ui.screens.settings.sections.StorageCachesSection
import com.nendo.argosy.ui.screens.settings.sections.StorageGamesSection
import com.nendo.argosy.ui.screens.settings.sections.StoragePlatformGamesSection
import com.nendo.argosy.ui.screens.settings.sections.StoragePlatformGamesItem
import com.nendo.argosy.ui.screens.settings.sections.createStoragePlatformGamesLayoutInfo
import com.nendo.argosy.ui.screens.settings.sections.storagePlatformGamesItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.StorageSection
import com.nendo.argosy.ui.screens.settings.sections.SyncSettingsSection
import com.nendo.argosy.data.preferences.FontSlot
import com.nendo.argosy.ui.screens.settings.sections.ThemeBackdropSection
import com.nendo.argosy.ui.screens.settings.sections.ThemeFontsSection
import com.nendo.argosy.ui.screens.settings.sections.ThemeMusicSection
import com.nendo.argosy.ui.screens.settings.sections.ThemeSection
import com.nendo.argosy.ui.screens.settings.sections.ThemeSoundsItem
import com.nendo.argosy.ui.screens.settings.sections.ThemeSoundsLayoutState
import com.nendo.argosy.ui.screens.settings.sections.ThemeSoundsSection
import com.nendo.argosy.ui.screens.settings.sections.themeSoundsItemAtFocusIndex
import com.nendo.argosy.ui.screens.settings.sections.formatFileSize
import com.nendo.argosy.ui.screens.settings.libretro.libretroSettingsMaxFocusIndex
import com.nendo.argosy.ui.icons.InputIcons
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.util.clickableNoFocus

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    initialSection: String? = null,
    initialAction: String? = null,
    initialPlatformId: Long? = null,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val imageCacheProgress by viewModel.imageCacheProgress.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(initialSection, initialAction, initialPlatformId) {
        if (initialSection != null) {
            val section = SettingsSection.entries.find { it.name.equals(initialSection, ignoreCase = true) }
            if (section == SettingsSection.PLATFORM_DETAIL && initialPlatformId != null) {
                viewModel.openPlatformDetailById(initialPlatformId)
            } else if (section != null) {
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

    var pendingFontSlot by remember { mutableStateOf<FontSlot?>(null) }
    val fontPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        val slot = pendingFontSlot
        pendingFontSlot = null
        if (uri != null && slot != null) {
            viewModel.importFont(slot, uri)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.openFontPickerEvent.collect { slot ->
            pendingFontSlot = slot
            fontPickerLauncher.launch(FONT_PICKER_MIME_TYPES)
        }
    }

    var showFileBrowser by remember { mutableStateOf(false) }
    var fileBrowserTitle by remember { mutableStateOf<String?>(null) }
    var fileBrowserCallback by remember { mutableStateOf<((String) -> Unit)?>(null) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onNotificationPermissionResult(granted)
    }

    val mediaPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.onMediaPermissionResult(granted)
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

    LaunchedEffect(uiState.currentSection) {
        showFileBrowser = false
        fileBrowserCallback = null
        inputDispatcher.blockInputFor(Motion.transitionDebounceMs)
    }

    LaunchedEffect(uiState.launchFolderPicker) {
        if (uiState.launchFolderPicker) {
            fileBrowserCallback = when {
                viewModel.hasPendingBiosCopy -> { path: String -> viewModel.onBiosCopyFolderSelected(path) }
                else -> { path: String -> viewModel.setStoragePath(path) }
            }
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

    LaunchedEffect(onBack) {
        viewModel.hardResetCompletedEvent.collect { onBack() }
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
        viewModel.requestNotificationPermissionEvent.collect {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            } else {
                viewModel.onNotificationPermissionResult(true)
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.requestMediaPermissionEvent.collect {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                mediaPermissionLauncher.launch(android.Manifest.permission.READ_MEDIA_IMAGES)
            } else {
                viewModel.onMediaPermissionResult(true)
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

    var showBgmPlaylistManager by remember { mutableStateOf(false) }
    var showBgmAddMusicBrowser by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.openBgmPlaylistManagerEvent.collect {
            showBgmPlaylistManager = true
        }
    }

    LaunchedEffect(Unit) {
        viewModel.openBgmAddMusicBrowserEvent.collect {
            showBgmAddMusicBrowser = true
        }
    }

    var showMusicBrowserBgm by remember { mutableStateOf(false) }
    var showMusicLocationBrowser by remember { mutableStateOf(false) }
    var musicBrowserSfxTarget by remember { mutableStateOf<SoundType?>(null) }

    LaunchedEffect(Unit) {
        viewModel.openMusicBrowserBgmEvent.collect {
            showMusicBrowserBgm = true
        }
    }

    LaunchedEffect(Unit) {
        viewModel.openMusicLocationPickerEvent.collect {
            showMusicLocationBrowser = true
        }
    }

    LaunchedEffect(Unit) {
        viewModel.openMusicBrowserSfxEvent.collect { soundType ->
            musicBrowserSfxTarget = soundType
        }
    }

    var customSoundTargetType by remember { mutableStateOf<SoundType?>(null) }

    LaunchedEffect(Unit) {
        viewModel.openCustomSoundPickerEvent.collect { soundType ->
            customSoundTargetType = soundType
        }
    }

    fun platformName(platformId: Long): String =
        uiState.emulators.platforms.find { it.platform.id == platformId }?.platform?.name ?: "Platform"

    LaunchedEffect(Unit) {
        viewModel.launchPlatformFolderPicker.collect { platformId ->
            fileBrowserTitle = "${platformName(platformId)} ROM Path"
            fileBrowserCallback = { path -> viewModel.setPlatformPath(platformId, path) }
            showFileBrowser = true
        }
    }

    LaunchedEffect(Unit) {
        viewModel.openGameNativeSyncDirPickerEvent.collect {
            fileBrowserTitle = "GameNative Sync Folder"
            fileBrowserCallback = { path -> viewModel.setGameNativeSyncDir(path) }
            showFileBrowser = true
        }
    }

    LaunchedEffect(Unit) {
        viewModel.launchSavePathPicker.collect {
            uiState.emulators.savePathModalInfo?.emulatorId?.let { emulatorId ->
                fileBrowserTitle = "Save Path"
                fileBrowserCallback = { path -> viewModel.setEmulatorSavePath(emulatorId, path) }
                showFileBrowser = true
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.launchPlatformSavePathPicker.collect { platformId ->
            fileBrowserTitle = "${platformName(platformId)} Save Path"
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
            fileBrowserTitle = "${platformName(platformId)} State Path"
            fileBrowserCallback = { path -> viewModel.setPlatformStatePath(platformId, path) }
            showFileBrowser = true
        }
    }

    LaunchedEffect(Unit) {
        viewModel.launchBuiltinSavePathPicker.collect {
            fileBrowserTitle = "Built-in Save Path"
            fileBrowserCallback = { path -> viewModel.setBuiltinSavePath(path) }
            showFileBrowser = true
        }
    }

    LaunchedEffect(Unit) {
        viewModel.launchBuiltinStatePathPicker.collect {
            fileBrowserTitle = "Built-in State Path"
            fileBrowserCallback = { path -> viewModel.setBuiltinStatePath(path) }
            showFileBrowser = true
        }
    }

    LaunchedEffect(Unit) {
        viewModel.launchPlatformBuiltinSavePathPicker.collect { platformId ->
            fileBrowserTitle = "${platformName(platformId)} Built-in Save Path"
            fileBrowserCallback = { path -> viewModel.setPlatformBuiltinSavePath(platformId, path) }
            showFileBrowser = true
        }
    }

    LaunchedEffect(Unit) {
        viewModel.launchPlatformBuiltinStatePathPicker.collect { platformId ->
            fileBrowserTitle = "${platformName(platformId)} Built-in State Path"
            fileBrowserCallback = { path -> viewModel.setPlatformBuiltinStatePath(platformId, path) }
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
                BuiltinNavigationTarget.CORE_OPTIONS -> {
                    viewModel.loadCoreOptionsState()
                    viewModel.navigateToSection(SettingsSection.CORE_OPTIONS)
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
                if (viewModel.uiState.value.currentSection == SettingsSection.PLATFORMS) {
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
                .surfaceBackdrop(BackdropRole.CONTENT)
        ) {
            if (uiState.currentSection != SettingsSection.SHADER_STACK &&
                uiState.currentSection != SettingsSection.FRAME_PICKER) {
                SettingsHeader(
                    title = when (uiState.currentSection) {
                        SettingsSection.MAIN -> "SETTINGS"
                        SettingsSection.SERVER -> "GAME DATA"
                        SettingsSection.SYNC_SETTINGS -> "SYNC SETTINGS"
                        SettingsSection.STEAM_SETTINGS -> "STEAM (EXPERIMENTAL)"
                        SettingsSection.RETRO_ACHIEVEMENTS -> "RETROACHIEVEMENTS"
                        SettingsSection.STORAGE -> "STORAGE"
                        SettingsSection.STORAGE_GAMES -> "GAMES STORAGE"
                        SettingsSection.STORAGE_PLATFORM_GAMES ->
                            uiState.storagePlatformGames.platformName.uppercase().ifBlank { "PLATFORM GAMES" }
                        SettingsSection.STORAGE_CACHES -> "CACHES & SYSTEM"
                        SettingsSection.THEME -> "THEME"
                        SettingsSection.THEME_SOUNDS -> "SOUNDS"
                        SettingsSection.THEME_MUSIC -> "MUSIC"
                        SettingsSection.THEME_FONTS -> "FONTS"
                        SettingsSection.THEME_BACKDROP -> "SURFACE BACKDROP"
                        SettingsSection.INTERFACE -> "INTERFACE"
                        SettingsSection.BOX_ART -> "BOX ART"
                        SettingsSection.HOME_SCREEN -> "HOME SCREEN"
                        SettingsSection.AMBIENT_LED -> "LED CONTROL"
                        SettingsSection.CONTROLS -> "CONTROLS"
                        SettingsSection.PLATFORMS -> "PLATFORMS"
                        SettingsSection.BUILTIN_EMULATOR -> "BUILT-IN EMULATOR"
                        SettingsSection.PLATFORM_DETAIL -> {
                            val config = uiState.emulators.platforms.getOrNull(uiState.platformDetail.platformIndex)
                            config?.platform?.name?.uppercase() ?: "PLATFORM"
                        }
                        SettingsSection.BUILTIN_VIDEO -> "BUILT-IN A/V & PERFORMANCE"
                        SettingsSection.BUILTIN_CONTROLS -> "BUILT-IN CONTROLS"
                        SettingsSection.CORE_MANAGEMENT -> "MANAGE CORES"
                        SettingsSection.CORE_OPTIONS -> "CORE OPTIONS"
                        SettingsSection.BIOS -> "BIOS FILES"
                        SettingsSection.SHADER_STACK -> "SHADER CHAIN"
                        SettingsSection.FRAME_PICKER -> "SELECT FRAME"
                        SettingsSection.PERMISSIONS -> "PERMISSIONS"
                        SettingsSection.DRIVERS -> "GPU DRIVERS"
                        SettingsSection.ABOUT -> "ABOUT"
                        SettingsSection.SOCIAL -> "SOCIAL"
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
                    } else if (uiState.currentSection == SettingsSection.CORE_OPTIONS &&
                        uiState.coreOptions.availablePlatforms.isNotEmpty()) {
                        {
                            val platformName = uiState.coreOptions.currentPlatformContext?.platformName ?: "---"
                            PlatformContextIndicator(
                                platformName = platformName,
                                onPrevious = { viewModel.cycleCoreOptionsPlatformContext(-1) },
                                onNext = { viewModel.cycleCoreOptionsPlatformContext(1) }
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
                    SettingsSection.STORAGE_GAMES -> StorageGamesSection(uiState, viewModel)
                    SettingsSection.STORAGE_PLATFORM_GAMES -> StoragePlatformGamesSection(uiState, viewModel)
                    SettingsSection.STORAGE_CACHES -> StorageCachesSection(uiState, viewModel)
                    SettingsSection.THEME -> ThemeSection(uiState, viewModel)
                    SettingsSection.THEME_SOUNDS -> ThemeSoundsSection(uiState, viewModel)
                    SettingsSection.THEME_MUSIC -> ThemeMusicSection(uiState, viewModel)
                    SettingsSection.THEME_FONTS -> ThemeFontsSection(uiState, viewModel)
                    SettingsSection.THEME_BACKDROP -> ThemeBackdropSection(uiState, viewModel)
                    SettingsSection.INTERFACE -> InterfaceSection(uiState, viewModel)
                    SettingsSection.BOX_ART -> BoxArtSection(uiState, viewModel)
                    SettingsSection.HOME_SCREEN -> HomeScreenSection(uiState, viewModel)
                    SettingsSection.AMBIENT_LED -> AmbientLedSection(uiState, viewModel)
                    SettingsSection.CONTROLS -> ControlsSection(uiState, viewModel)
                    SettingsSection.BUILTIN_EMULATOR -> BuiltinEmulatorSection(uiState, viewModel)
                    SettingsSection.PLATFORMS -> EmulatorsSection(
                        uiState = uiState,
                        viewModel = viewModel,
                        onLaunchSavePathPicker = {
                            uiState.emulators.savePathModalInfo?.emulatorId?.let { emulatorId ->
                                fileBrowserCallback = { path -> viewModel.setEmulatorSavePath(emulatorId, path) }
                                showFileBrowser = true
                            }
                        }
                    )
                    SettingsSection.PLATFORM_DETAIL -> PlatformDetailSection(
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
                    SettingsSection.CORE_OPTIONS -> CoreOptionsSection(uiState, viewModel)
                    SettingsSection.BIOS -> BiosSection(uiState, viewModel)
                    SettingsSection.SHADER_STACK -> ShaderStackSection(viewModel.shaderChainManager)
                    SettingsSection.FRAME_PICKER -> FrameSection(uiState, viewModel)
                    SettingsSection.PERMISSIONS -> PermissionsSection(uiState, viewModel)
                    SettingsSection.DRIVERS -> DriversSection(uiState, viewModel)
                    SettingsSection.ABOUT -> AboutSection(uiState, viewModel)
                    SettingsSection.SOCIAL -> SocialSection(uiState, viewModel)
                }
            }

            SettingsFooter(uiState, viewModel.shaderChainManager.shaderStack)
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
                    onConfirm = { index -> viewModel.confirmSoundPickerSelectionAt(index) },
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

        ReleaseChangelogModal(
            state = uiState.changelog,
            onLoadMore = { viewModel.loadChangelogPage() },
            onDismiss = { viewModel.closeChangelog() }
        )

        uiState.systemizeResult?.let { result ->
            com.nendo.argosy.ui.screens.settings.dialogs.SystemizeResultDialog(
                result = result,
                onDismiss = { viewModel.dismissSystemizeDialog() }
            )
        }
        if (uiState.bios.showDistributeResultModal) {
            DistributeResultModal(
                results = uiState.bios.distributeResults,
                onDismiss = { viewModel.dismissDistributeResultModal() }
            )
        }
    }

    val builtinMigration = uiState.pendingBuiltinPathMigration
    val builtinMigrationTypeLabel = when (builtinMigration?.pathType) {
        BuiltinPathType.SAVE -> "save"
        BuiltinPathType.STATE -> "state"
        null -> ""
    }
    ArgosyConfirmModalHost(
        visible = uiState.showBuiltinPathMigrationDialog && builtinMigration != null,
        title = "Migrate $builtinMigrationTypeLabel files?",
        message = "The destination already contains ${builtinMigration?.existingFileCount ?: 0} $builtinMigrationTypeLabel files. Move existing files from the old location? This will overwrite any conflicts.",
        confirmLabel = "Migrate",
        onConfirm = { viewModel.confirmBuiltinPathMigration() },
        onDismiss = { viewModel.cancelBuiltinPathMigration() },
        neutralLabel = "Skip",
        onNeutral = { viewModel.skipBuiltinPathMigration() }
    )

    val musicRelocation = uiState.ambientAudio.pendingMusicRelocation
    ArgosyConfirmModalHost(
        visible = musicRelocation != null,
        title = "Move music files?",
        message = "The current music folder contains ${musicRelocation?.fileCount ?: 0} files. Move them to the new location? This will overwrite any conflicts.",
        confirmLabel = "Move",
        onConfirm = { viewModel.confirmMusicRelocation() },
        onDismiss = { viewModel.cancelMusicRelocation() },
        neutralLabel = "Skip",
        onNeutral = { viewModel.skipMusicRelocation() }
    )

    ArgosyConfirmModalHost(
        visible = uiState.showMigrationDialog,
        title = "Migrate Downloads?",
        message = "Move ${uiState.storage.downloadedGamesCount} games (${formatFileSize(uiState.storage.downloadedGamesSize)}) to the new location?",
        confirmLabel = "Migrate",
        onConfirm = { viewModel.confirmMigration() },
        onDismiss = { viewModel.cancelMigration() },
        neutralLabel = "Skip",
        onNeutral = { viewModel.skipMigration() }
    )

    val platformMigrationInfo = uiState.storage.showMigratePlatformConfirm
    ArgosyConfirmModalHost(
        visible = platformMigrationInfo != null,
        title = "Migrate ${platformMigrationInfo?.platformName ?: "Platform"} ROMs?",
        message = "Move downloaded games to the new location? Files will be copied and then removed from the old location.",
        confirmLabel = "Migrate",
        onConfirm = { viewModel.confirmPlatformMigration() },
        onDismiss = { viewModel.cancelPlatformMigration() },
        neutralLabel = "Skip",
        onNeutral = { viewModel.skipPlatformMigration() }
    )

    val purgePlatformConfig = uiState.storage.showPurgePlatformConfirm?.let { platformId ->
        uiState.storage.platformConfigs.find { it.platformId == platformId }
    }
    ArgosyConfirmModalHost(
        visible = uiState.storage.showPurgePlatformConfirm != null,
        title = "Purge ${purgePlatformConfig?.platformName ?: "Platform"}?",
        message = "This will delete all ${purgePlatformConfig?.gameCount ?: 0} games and their local ROM files. This cannot be undone.",
        confirmLabel = "Purge",
        destructive = true,
        onConfirm = { viewModel.confirmPurgePlatform() },
        onDismiss = { viewModel.cancelPurgePlatform() }
    )

    if (uiState.storage.purgeAllPendingUploads > 0) {
        ArgosyConfirmModalHost(
            visible = uiState.storage.showPurgeAllConfirm,
            title = "Sync Saves First",
            message = "Sync saves first - ${uiState.storage.purgeAllPendingUploads} pending. Resetting the library now would permanently discard saves that have not been uploaded.",
            confirmLabel = "Sync Now",
            onConfirm = {
                viewModel.cancelPurgeAll()
                viewModel.requestSyncSaves()
            },
            onDismiss = { viewModel.cancelPurgeAll() }
        )
    } else {
        ArgosyConfirmModalHost(
            visible = uiState.storage.showPurgeAllConfirm,
            title = "Reset Library?",
            message = "Clears the database and image cache. Downloaded files stay on disk. Permanently lost: play time, local collections, download history, save sync state, and per-game settings. You will need to re-sync your library.",
            confirmLabel = "Reset",
            destructive = true,
            onConfirm = { viewModel.confirmPurgeAll() },
            onDismiss = { viewModel.cancelPurgeAll() }
        )
    }

    if (uiState.storage.showHardResetModal) {
        val snapshot = uiState.attribution.snapshot
        val gamesBytes = snapshot?.categories?.get(StorageCategory.GAMES)?.bytes
            ?: uiState.storage.downloadedGamesSize
        val gamesCount = snapshot?.gamesPerPlatform?.takeIf { it.isNotEmpty() }
            ?.sumOf { it.downloadedCount }
            ?: uiState.storage.downloadedGamesCount
        HardResetModal(
            downloadedGamesCount = gamesCount,
            downloadedGamesBytes = gamesBytes,
            pendingUploads = uiState.storage.hardResetPendingUploads,
            isResetting = uiState.storage.isHardResetting,
            canSyncNow = uiState.server.connectionStatus == ConnectionStatus.ONLINE &&
                !uiState.syncSettings.isSyncing,
            onSyncNow = { viewModel.requestSyncSaves() },
            onHoldStart = { viewModel.hardResetHoldStarted() },
            onConfirm = { viewModel.confirmHardReset() },
            onDismiss = { viewModel.cancelHardReset() }
        )
    }

    val platformGameDelete = uiState.storagePlatformGames.deleteConfirm
    if (platformGameDelete != null) {
        val saveWarning = if (platformGameDelete.unsyncedSaves > 0) {
            val noun = if (platformGameDelete.unsyncedSaves == 1) "save" else "saves"
            " ${platformGameDelete.unsyncedSaves} unsynced $noun will be lost."
        } else ""
        if (platformGameDelete.hasSoundtrack) {
            ArgosyConfirmModalHost(
                visible = true,
                title = "Delete ${platformGameDelete.title}?",
                message = "Removes all downloaded files for this game.$saveWarning Choose whether to also delete its soundtrack.",
                cancelLabel = "Cancel",
                neutralLabel = "Delete Game",
                onNeutral = { viewModel.confirmStoragePlatformGameDelete(platformGameDelete.gameId, withSoundtrack = false) },
                confirmLabel = "Delete + Soundtrack",
                destructive = true,
                onConfirm = { viewModel.confirmStoragePlatformGameDelete(platformGameDelete.gameId, withSoundtrack = true) },
                onDismiss = { viewModel.dismissStoragePlatformGameDelete() }
            )
        } else {
            ArgosyConfirmModalHost(
                visible = true,
                title = "Delete ${platformGameDelete.title}?",
                message = "Removes all downloaded files for this game.$saveWarning This cannot be undone.",
                confirmLabel = "Delete Game",
                destructive = true,
                onConfirm = { viewModel.confirmStoragePlatformGameDelete(platformGameDelete.gameId, withSoundtrack = false) },
                onDismiss = { viewModel.dismissStoragePlatformGameDelete() }
            )
        }
    }

    val platformCategoryDelete = uiState.storagePlatformGames.categoryDeleteConfirm
    if (platformCategoryDelete != null) {
        val fileNoun = if (platformCategoryDelete.fileCount == 1) "file" else "files"
        val bucketLabel = com.nendo.argosy.ui.screens.settings.sections
            .bucketDisplayLabel(platformCategoryDelete.bucket)
        ArgosyConfirmModalHost(
            visible = true,
            title = "Remove $bucketLabel?",
            message = "Removes ${platformCategoryDelete.fileCount} $fileNoun " +
                "(${formatBytes(platformCategoryDelete.totalBytes)}). Re-downloads from your library.",
            confirmLabel = "Delete",
            destructive = true,
            onConfirm = {
                viewModel.confirmStoragePlatformCategoryDelete(
                    platformCategoryDelete.gameId,
                    platformCategoryDelete.bucket
                )
            },
            onDismiss = { viewModel.dismissStoragePlatformCategoryDelete() }
        )
    }

    ArgosyConfirmModalHost(
        visible = uiState.syncSettings.showResetSaveCacheConfirm,
        title = "Reset Save Cache?",
        message = "This will delete all locally cached save snapshots and pending sync operations. Your actual save files and server saves are not affected.",
        confirmLabel = "Reset",
        destructive = true,
        onConfirm = { viewModel.confirmResetSaveCache() },
        onDismiss = { viewModel.cancelResetSaveCache() }
    )

    ArgosyConfirmModalHost(
        visible = uiState.syncSettings.showClearPathCacheConfirm,
        title = "Clear Save Path Cache?",
        message = "This will clear all detected save file paths. Paths will be re-detected on next sync. Use this if saves are syncing to the wrong location.",
        confirmLabel = "Clear",
        destructive = true,
        onConfirm = { viewModel.confirmClearPathCache() },
        onDismiss = { viewModel.cancelClearPathCache() }
    )

    ArgosyConfirmModalHost(
        visible = uiState.syncSettings.showClearStateCacheConfirm,
        title = "Clear State Cache?",
        message = "This will delete all locally cached save state snapshots and their pending sync operations. Save files and server copies are not affected.",
        confirmLabel = "Clear",
        destructive = true,
        onConfirm = { viewModel.confirmClearStateCache() },
        onDismiss = { viewModel.cancelClearStateCache() }
    )

    val pendingCachesClear = uiState.storageCaches.pendingClear
    ArgosyConfirmModalHost(
        visible = pendingCachesClear != null,
        title = cachesClearConfirmTitle(pendingCachesClear),
        message = cachesClearConfirmMessage(pendingCachesClear),
        confirmLabel = "Clear",
        destructive = true,
        onConfirm = { viewModel.confirmCachesClear() },
        onDismiss = { viewModel.cancelCachesClear() }
    )

    ArgosyConfirmModal(
        visible = uiState.syncSettings.showForceSyncConfirm,
        title = "Sync Saves?",
        message = "This will scan all downloaded games for save changes and sync them with the server. Local saves newer than the last sync will be uploaded, and newer server saves will be downloaded.",
        confirmLabel = "Sync",
        onConfirm = { viewModel.confirmSyncSaves() },
        onDismiss = { viewModel.cancelSyncSaves() },
        focusedIndex = uiState.syncSettings.syncConfirmButtonIndex
    )

    if (showFileBrowser) {
        FileBrowserScreen(
            mode = FileBrowserMode.FOLDER_SELECTION,
            title = fileBrowserTitle,
            onPathSelected = { path ->
                showFileBrowser = false
                fileBrowserTitle = null
                fileBrowserCallback?.invoke(path)
                fileBrowserCallback = null
            },
            onDismiss = {
                showFileBrowser = false
                fileBrowserTitle = null
                fileBrowserCallback = null
            }
        )
    }

    if (showBgmPlaylistManager) {
        BgmPlaylistManagerScreen(
            onAddMusic = { showBgmAddMusicBrowser = true },
            onDismiss = { showBgmPlaylistManager = false }
        )
    }

    if (showBgmAddMusicBrowser) {
        FileBrowserScreen(
            mode = FileBrowserMode.FILE_OR_FOLDER_SELECTION,
            fileFilter = FileFilter.AUDIO,
            title = "Add Music",
            onPathSelected = { path ->
                showBgmAddMusicBrowser = false
                viewModel.addBgmPlaylistEntry(path)
            },
            onDismiss = {
                showBgmAddMusicBrowser = false
            }
        )
    }

    if (showMusicLocationBrowser) {
        FileBrowserScreen(
            mode = FileBrowserMode.FOLDER_SELECTION,
            title = "Music Location",
            onPathSelected = { path ->
                showMusicLocationBrowser = false
                viewModel.onMusicLocationSelected(path)
            },
            onDismiss = {
                showMusicLocationBrowser = false
            }
        )
    }

    if (showMusicBrowserBgm) {
        MusicBrowserScreen(
            mode = MusicBrowserMode.BGM,
            onDismiss = { showMusicBrowserBgm = false }
        )
    }

    musicBrowserSfxTarget?.let { soundType ->
        MusicBrowserScreen(
            mode = MusicBrowserMode.SFX,
            sfxTargetLabel = soundType.name
                .replace("_", " ")
                .lowercase()
                .split(" ")
                .joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } },
            onSfxSelected = { path ->
                musicBrowserSfxTarget = null
                viewModel.setCustomSoundFile(soundType, path, fromRomm = true)
            },
            onDismiss = { musicBrowserSfxTarget = null }
        )
    }

    customSoundTargetType?.let { soundType ->
        FileBrowserScreen(
            mode = FileBrowserMode.FILE_SELECTION,
            fileFilter = FileFilter.AUDIO,
            title = "Custom Sound",
            onPathSelected = { path ->
                customSoundTargetType = null
                viewModel.setCustomSoundFile(soundType, path)
            },
            onDismiss = {
                customSoundTargetType = null
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

    var showGpuDriverFileBrowser by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.launchGpuDriverFilePicker.collect {
            showGpuDriverFileBrowser = true
        }
    }

    if (showGpuDriverFileBrowser) {
        FileBrowserScreen(
            mode = FileBrowserMode.FILE_SELECTION,
            fileFilter = FileFilter(extensions = setOf("zip")),
            onPathSelected = { path ->
                showGpuDriverFileBrowser = false
                viewModel.installGpuDriverFromFile(path)
            },
            onDismiss = {
                showGpuDriverFileBrowser = false
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
private fun SettingsFooter(uiState: SettingsUiState, shaderStack: ShaderStackState) {
    if (uiState.emulators.showSavePathModal || uiState.emulators.showEmulatorPicker ||
        uiState.emulators.updateModal != null || uiState.emulators.showLaunchArgsModal ||
        uiState.emulators.showAppPickerModal || uiState.emulators.showMemcardPicker) {
        return
    }
    if (shaderStack.showShaderPicker) {
        return
    }

    val hints = buildList {
        if (uiState.currentSection != SettingsSection.BOX_ART &&
            uiState.currentSection != SettingsSection.SHADER_STACK) {
            add(InputButton.DPAD to "Navigate")
        }
        if (uiState.currentSection == SettingsSection.SHADER_STACK &&
            shaderStack.entries.isNotEmpty() &&
            shaderStack.selectedShaderParams.isNotEmpty()
        ) {
            add(InputButton.DPAD_VERTICAL to "Navigate")
        }
        if (uiState.currentSection == SettingsSection.BOX_ART) {
            add(InputButton.LB_RB to "Preview Shape")
            add(InputButton.LT_RT to "Preview Game")
        }
        if (uiState.currentSection == SettingsSection.SHADER_STACK) {
            if (shaderStack.entries.isNotEmpty()) {
                add(InputButton.LB_RB to "Shader")
                add(InputButton.LT_RT to "Reorder")
                if (shaderStack.selectedShaderParams.isNotEmpty()) {
                    add(InputButton.DPAD_HORIZONTAL to "Adjust")
                    add(InputButton.A to "Reset")
                }
                add(InputButton.Y to "Remove")
            }
            add(InputButton.X to "Add")
        }
        if ((uiState.currentSection == SettingsSection.BUILTIN_VIDEO ||
            uiState.currentSection == SettingsSection.BUILTIN_CONTROLS) &&
            uiState.builtinVideo.availablePlatforms.isNotEmpty()) {
            add(InputButton.LB_RB to "Platform")
        }
        if (uiState.currentSection == SettingsSection.BUILTIN_VIDEO &&
            uiState.builtinVideo.isGlobalContext &&
            uiState.builtinVideo.savePath.isNotEmpty()) {
            val videoState = uiState.builtinVideo
            val settingsMax = libretroSettingsMaxFocusIndex(
                platformSlug = null,
                canEnableBFI = videoState.canEnableBlackFrameInsertion
            )
            val onSavePath = uiState.focusedIndex == settingsMax + 1
            val onStatePath = uiState.focusedIndex == settingsMax + 2
            if ((onSavePath && videoState.isCustomSavePath) || (onStatePath && videoState.isCustomStatePath)) {
                add(InputButton.Y to "Reset to Default")
            }
        }
        if (uiState.currentSection == SettingsSection.THEME_SOUNDS && uiState.sounds.enabled) {
            val soundsLayout = ThemeSoundsLayoutState.from(uiState)
            val focusedSoundItem = themeSoundsItemAtFocusIndex(uiState.focusedIndex, soundsLayout)
            if (focusedSoundItem is ThemeSoundsItem.SoundTypeItem) {
                add(InputButton.X to "Sample")
                if (uiState.sounds.soundConfigs.containsKey(focusedSoundItem.soundType)) {
                    add(InputButton.Y to "Reset to Default")
                }
            }
        }
        if (uiState.currentSection == SettingsSection.CORE_OPTIONS &&
            uiState.coreOptions.availablePlatforms.isNotEmpty()) {
            add(InputButton.LB_RB to "Platform")
            val focusedCoreItem = coreOptionsItemAtFocusIndex(
                uiState.focusedIndex, uiState.coreOptions
            )
            if (focusedCoreItem is CoreOptionItem.Option && focusedCoreItem.isOverridden) {
                add(InputButton.Y to "Reset to Default")
            }
        }
        if (uiState.currentSection == SettingsSection.STEAM_SETTINGS) {
            val steamItem = com.nendo.argosy.ui.screens.settings.sections.steamItemAtFocusIndex(
                uiState.focusedIndex, uiState.steam
            )
            if (steamItem == com.nendo.argosy.ui.screens.settings.sections.SteamItem.SyncLibrary) {
                add(InputButton.X to "Force Sync")
            }
        }
        if (uiState.currentSection == SettingsSection.STORAGE) {
            add(InputButton.X to "Refresh")
        }
        if (uiState.currentSection == SettingsSection.STORAGE_GAMES) {
            add(InputButton.X to "Sort")
        }
        if (uiState.currentSection == SettingsSection.STORAGE_PLATFORM_GAMES) {
            val pgInfo = createStoragePlatformGamesLayoutInfo(uiState)
            val focusedPg = storagePlatformGamesItemAtFocusIndex(uiState.focusedIndex, pgInfo)
            val focusedGame = (focusedPg as? StoragePlatformGamesItem.GameCard)?.let { card ->
                uiState.storagePlatformGames.games.firstOrNull { it.gameId == card.gameId }
            }
            if (focusedGame != null) {
                if (focusedGame.buckets.size > 1) {
                    add(InputButton.DPAD_HORIZONTAL to "Category")
                }
                add(InputButton.Y to "Delete")
            }
        }
        if (uiState.currentSection == SettingsSection.PLATFORM_DETAIL) {
            val config = uiState.emulators.platforms.getOrNull(uiState.platformDetail.platformIndex)
            val detail = uiState.platformDetail
            val syncEnabled = config?.let { c ->
                uiState.storage.platformConfigs.find { it.platformId == c.platform.id }?.syncEnabled
            } ?: true
            val focusedItem = config?.let {
                platformDetailItemAtFocusIndex(uiState.focusedIndex, it, detail, syncEnabled)
            }
            if (focusedItem is PlatformDetailItem.Core ||
                focusedItem is PlatformDetailItem.Extension ||
                focusedItem is PlatformDetailItem.DisplayTarget ||
                focusedItem is PlatformDetailItem.Emulator) {
                add(InputButton.DPAD_HORIZONTAL to "Adjust")
            }
            if (config != null) {
                val emulatorId = config.effectiveEmulatorId
                if (emulatorId != null && emulatorId in uiState.emulators.emulatorUpdateVersions) {
                    add(InputButton.X to "Update Emulator")
                }
            }
            val storageConfig = uiState.storage.platformConfigs.find { it.platformId == config?.platform?.id }
            val canReset = when (focusedItem) {
                is PlatformDetailItem.RomPath -> storageConfig?.customRomPath != null
                is PlatformDetailItem.SavePath -> !config!!.effectiveEmulatorIsRetroArch && storageConfig?.isUserSavePathOverride == true
                is PlatformDetailItem.StatePath -> !config!!.effectiveEmulatorIsRetroArch && storageConfig?.isUserStatePathOverride == true
                else -> false
            }
            if (canReset) {
                add(InputButton.Y to "Reset")
            }
            val aLabel = when (focusedItem) {
                is PlatformDetailItem.SyncToggle, is PlatformDetailItem.LegacyMode -> "Toggle"
                is PlatformDetailItem.BuiltinVideo, is PlatformDetailItem.BuiltinControls, is PlatformDetailItem.BuiltinCoreOptions -> "Open"
                else -> "Select"
            }
            add(InputButton.A to aLabel)
        } else if (uiState.currentSection != SettingsSection.SHADER_STACK) {
            add(InputButton.A to "Select")
        }
        if (uiState.currentSection == SettingsSection.PLATFORMS) {
            val emuLayoutInfo = com.nendo.argosy.ui.screens.settings.sections.createEmulatorsLayoutInfo(
                platforms = uiState.emulators.platforms
            )
            val focusedItem = com.nendo.argosy.ui.screens.settings.sections.emulatorsItemAtFocusIndex(
                uiState.focusedIndex, emuLayoutInfo
            )
            if (focusedItem is com.nendo.argosy.ui.screens.settings.sections.EmulatorsItem.PlatformItem &&
                focusedItem.config.showDisplayTargetOption
            ) {
                add(InputButton.LB_RB to "Display")
            }
            if (focusedItem is com.nendo.argosy.ui.screens.settings.sections.EmulatorsItem.PlatformItem) {
                if (!focusedItem.config.platform.syncEnabled) {
                    add(InputButton.Y to "Enable")
                } else {
                    val emulatorId = focusedItem.config.effectiveEmulatorId
                    if (emulatorId != null && emulatorId in uiState.emulators.emulatorUpdateVersions) {
                        add(InputButton.X to "Update")
                    }
                }
            }
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
                add(InputButton.Y to "Reset")
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
                add(InputButton.Y to "Reset")
            }
        }
        add(InputButton.B to "Back")
    }

    FooterHints(hints = hints)
    FooterSpacer()
}

private fun cachesClearConfirmTitle(target: CachesClearTarget?): String = when (target) {
    CachesClearTarget.IMAGE_CACHE -> "Clear Image Cache?"
    CachesClearTarget.ROM_EXTRACTION -> "Clear Extracted ROMs?"
    CachesClearTarget.SFX_CACHE -> "Clear Sound Effects Cache?"
    CachesClearTarget.EMULATOR_APKS -> "Clear Emulator Installers?"
    CachesClearTarget.MISC_DOWNLOADS -> "Clear Misc Downloads?"
    CachesClearTarget.SHADERS_CATALOG -> "Clear Shader Catalog?"
    CachesClearTarget.FRAMES -> "Clear Frame Overlays?"
    CachesClearTarget.STEAM_DOWNLOADS -> "Clear Steam Download Data?"
    null -> ""
}

private fun cachesClearConfirmMessage(target: CachesClearTarget?): String = when (target) {
    CachesClearTarget.IMAGE_CACHE ->
        "This will delete all cached covers, backgrounds, and screenshots. Images re-download from the server as you browse."
    CachesClearTarget.ROM_EXTRACTION ->
        "This will delete extracted working copies of compressed games. They re-extract the next time each game launches."
    CachesClearTarget.SFX_CACHE ->
        "This will delete transcoded custom sound files. They rebuild automatically the next time sounds load."
    CachesClearTarget.MISC_DOWNLOADS ->
        "This will delete friend presence covers and downloaded GPU driver packages. Installed drivers are not affected."
    CachesClearTarget.EMULATOR_APKS ->
        "This will delete downloaded emulator installer APKs. Installed emulators are not affected."
    CachesClearTarget.SHADERS_CATALOG ->
        "This will delete downloaded catalog shaders. Custom shaders are kept, and catalog shaders re-download on demand."
    CachesClearTarget.FRAMES ->
        "This will delete downloaded frame overlays. They re-download on demand when selected."
    CachesClearTarget.STEAM_DOWNLOADS ->
        "This will delete staged Steam downloads and clear the download queue. Installed games are not affected, but partial downloads restart from zero."
    null -> ""
}

