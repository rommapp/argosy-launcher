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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.nendo.argosy.R
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
import com.nendo.argosy.data.sync.UnflushedQueuePolicy
import com.nendo.argosy.ui.screens.settings.components.HardResetModal
import com.nendo.argosy.ui.screens.settings.components.PlatformSettingsModal
import com.nendo.argosy.ui.screens.settings.components.ReleaseChangelogModal
import com.nendo.argosy.ui.screens.settings.components.SoundPickerPopup
import com.nendo.argosy.ui.screens.settings.delegates.BuiltinNavigationTarget
import com.nendo.argosy.ui.screens.settings.sections.AboutSection
import com.nendo.argosy.ui.screens.settings.sections.AccountsSection
import com.nendo.argosy.ui.screens.settings.sections.BiosSection
import com.nendo.argosy.ui.screens.settings.sections.BiosDownloadFailureModal
import com.nendo.argosy.ui.screens.settings.sections.DistributeResultModal
import com.nendo.argosy.ui.screens.settings.sections.RomMItem
import com.nendo.argosy.ui.screens.settings.sections.buildRomMItemsFromState
import com.nendo.argosy.ui.screens.settings.sections.rommFocusIndexOf
import com.nendo.argosy.ui.screens.settings.sections.DriversSection
import com.nendo.argosy.ui.screens.settings.sections.AmbientLedSection
import com.nendo.argosy.ui.screens.settings.sections.AudioSection
import com.nendo.argosy.ui.screens.settings.sections.BoxArtSection
import com.nendo.argosy.ui.screens.settings.sections.DisplaysSection
import com.nendo.argosy.ui.screens.settings.sections.NavigationSection
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
import com.nendo.argosy.ui.screens.settings.sections.RomMSection
import com.nendo.argosy.ui.screens.settings.sections.SavesSection
import com.nendo.argosy.ui.screens.settings.sections.ControllerGripSection
import com.nendo.argosy.ui.screens.settings.sections.HomeScreenSection
import com.nendo.argosy.ui.screens.settings.sections.InterfaceSection
import com.nendo.argosy.ui.screens.settings.sections.JellyfinSection
import com.nendo.argosy.ui.screens.settings.sections.MainSettingsSection
import com.nendo.argosy.ui.screens.settings.sections.PermissionsSection
import com.nendo.argosy.ui.screens.settings.sections.RASettingsSection
import com.nendo.argosy.ui.screens.settings.sections.ShaderStackSection
import com.nendo.argosy.ui.screens.settings.sections.SocialSection
import com.nendo.argosy.ui.screens.settings.sections.SteamSection
import com.nendo.argosy.ui.screens.settings.sections.StorageCachesSection
import com.nendo.argosy.ui.screens.settings.sections.StorageGamesSection
import com.nendo.argosy.ui.screens.settings.sections.StorageMediaSection
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
import com.nendo.argosy.ui.screens.settings.libretro.libretroSettingsMaxFocusIndex
import com.nendo.argosy.ui.icons.InputIcons
import com.nendo.argosy.ui.theme.Motion
import com.nendo.argosy.ui.util.clickableNoFocus
import com.nendo.argosy.ui.screens.settings.sections.LibrarySection

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    initialSection: String? = null,
    initialAction: String? = null,
    initialPlatformId: Long? = null,
    onNavigateToAvatarEditor: () -> Unit = {},
    onNavigate: (String) -> Unit = {},
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
                viewModel.startAtSection(section)
                kotlinx.coroutines.delay(300)
                when (initialAction) {
                    "rommConfig" -> viewModel.startRommConfig()
                    "syncLibrary" -> {
                        val items = buildRomMItemsFromState(viewModel.uiState.value)
                        viewModel.setFocusIndex(rommFocusIndexOf(RomMItem.SyncLibrary, items))
                    }
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
    var showSettingsBackupBrowser by remember { mutableStateOf(false) }
    var showCertBrowser by remember { mutableStateOf(false) }

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

    val blePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        viewModel.onBlePermissionResult(results.values.all { it })
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
        viewModel.openSettingsBackupPickerEvent.collect {
            showSettingsBackupBrowser = true
        }
    }

    LaunchedEffect(Unit) {
        viewModel.openCertificatePickerEvent.collect {
            showCertBrowser = true
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
        viewModel.navigationEvents.collect { event ->
            onNavigate(event.route)
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
        viewModel.requestBlePermissionEvent.collect {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                blePermissionLauncher.launch(
                    arrayOf(
                        android.Manifest.permission.BLUETOOTH_SCAN,
                        android.Manifest.permission.BLUETOOTH_ADVERTISE,
                        android.Manifest.permission.BLUETOOTH_CONNECT
                    )
                )
            } else {
                viewModel.onBlePermissionResult(true)
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
    var showMediaLocationBrowser by remember { mutableStateOf(false) }
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
        viewModel.openMediaLocationPickerEvent.collect {
            showMediaLocationBrowser = true
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

    val platformFallbackName = stringResource(R.string.settings_shell_platform_fallback)
    fun platformName(platformId: Long): String =
        uiState.emulators.platforms.find { it.platform.id == platformId }?.platform?.name ?: platformFallbackName

    val platformRomPathTitleTemplate = stringResource(R.string.settings_shell_filebrowser_platform_rom_path_title)
    LaunchedEffect(Unit) {
        viewModel.launchPlatformFolderPicker.collect { platformId ->
            fileBrowserTitle = platformRomPathTitleTemplate.format(platformName(platformId))
            fileBrowserCallback = { path -> viewModel.setPlatformPath(platformId, path) }
            showFileBrowser = true
        }
    }

    val gameNativeSyncFolderTitleTemplate = stringResource(R.string.settings_shell_filebrowser_gamenative_sync_folder_title)
    LaunchedEffect(Unit) {
        viewModel.openGameNativeSyncDirPickerEvent.collect { folder ->
            fileBrowserTitle = gameNativeSyncFolderTitleTemplate.format(folder.displayName)
            fileBrowserCallback = { path -> viewModel.setGameNativeSyncDir(folder, path) }
            showFileBrowser = true
        }
    }

    val savePathTitle = stringResource(R.string.settings_shell_filebrowser_save_path_title)
    LaunchedEffect(Unit) {
        viewModel.launchSavePathPicker.collect {
            uiState.emulators.savePathModalInfo?.emulatorId?.let { emulatorId ->
                fileBrowserTitle = savePathTitle
                fileBrowserCallback = { path -> viewModel.setEmulatorSavePath(emulatorId, path) }
                showFileBrowser = true
            }
        }
    }

    val platformSavePathTitleTemplate = stringResource(R.string.settings_shell_filebrowser_platform_save_path_title)
    LaunchedEffect(Unit) {
        viewModel.launchPlatformSavePathPicker.collect { platformId ->
            fileBrowserTitle = platformSavePathTitleTemplate.format(platformName(platformId))
            fileBrowserCallback = { path -> viewModel.setPlatformSavePath(platformId, path) }
            showFileBrowser = true
        }
    }

    LaunchedEffect(Unit) {
        viewModel.resetPlatformSavePathEvent.collect { platformId ->
            viewModel.resetPlatformSavePath(platformId)
        }
    }

    val platformStatePathTitleTemplate = stringResource(R.string.settings_shell_filebrowser_platform_state_path_title)
    LaunchedEffect(Unit) {
        viewModel.launchPlatformStatePathPicker.collect { platformId ->
            fileBrowserTitle = platformStatePathTitleTemplate.format(platformName(platformId))
            fileBrowserCallback = { path -> viewModel.setPlatformStatePath(platformId, path) }
            showFileBrowser = true
        }
    }

    val builtinSavePathTitle = stringResource(R.string.settings_shell_filebrowser_builtin_save_path_title)
    LaunchedEffect(Unit) {
        viewModel.launchBuiltinSavePathPicker.collect {
            fileBrowserTitle = builtinSavePathTitle
            fileBrowserCallback = { path -> viewModel.setBuiltinSavePath(path) }
            showFileBrowser = true
        }
    }

    val builtinStatePathTitle = stringResource(R.string.settings_shell_filebrowser_builtin_state_path_title)
    LaunchedEffect(Unit) {
        viewModel.launchBuiltinStatePathPicker.collect {
            fileBrowserTitle = builtinStatePathTitle
            fileBrowserCallback = { path -> viewModel.setBuiltinStatePath(path) }
            showFileBrowser = true
        }
    }

    val platformBuiltinSavePathTitleTemplate =
        stringResource(R.string.settings_shell_filebrowser_platform_builtin_save_path_title)
    LaunchedEffect(Unit) {
        viewModel.launchPlatformBuiltinSavePathPicker.collect { platformId ->
            fileBrowserTitle = platformBuiltinSavePathTitleTemplate.format(platformName(platformId))
            fileBrowserCallback = { path -> viewModel.setPlatformBuiltinSavePath(platformId, path) }
            showFileBrowser = true
        }
    }

    val platformBuiltinStatePathTitleTemplate =
        stringResource(R.string.settings_shell_filebrowser_platform_builtin_state_path_title)
    LaunchedEffect(Unit) {
        viewModel.launchPlatformBuiltinStatePathPicker.collect { platformId ->
            fileBrowserTitle = platformBuiltinStatePathTitleTemplate.format(platformName(platformId))
            fileBrowserCallback = { path -> viewModel.setPlatformBuiltinStatePath(platformId, path) }
            showFileBrowser = true
        }
    }

    LaunchedEffect(Unit) {
        viewModel.avatarEditorEvent.collect { onNavigateToAvatarEditor() }
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
                        SettingsSection.MAIN -> stringResource(R.string.settings_shell_header_main)
                        SettingsSection.ACCOUNTS -> stringResource(R.string.settings_shell_header_accounts)
                        SettingsSection.ROMM -> stringResource(R.string.settings_shell_header_romm)
                        SettingsSection.SAVES -> stringResource(R.string.settings_shell_header_saves)
                        SettingsSection.SYNC_SETTINGS -> stringResource(R.string.settings_shell_header_sync_settings)
                        SettingsSection.STEAM_SETTINGS -> stringResource(R.string.settings_shell_header_steam)
                        SettingsSection.JELLYFIN -> stringResource(R.string.settings_shell_header_jellyfin)
                        SettingsSection.RETRO_ACHIEVEMENTS -> stringResource(R.string.settings_shell_header_retroachievements)
                        SettingsSection.STORAGE -> stringResource(R.string.settings_shell_header_storage)
                        SettingsSection.STORAGE_GAMES -> stringResource(R.string.settings_shell_header_storage_games)
                        SettingsSection.STORAGE_MEDIA -> stringResource(R.string.settings_shell_header_storage_media)
                        SettingsSection.STORAGE_PLATFORM_GAMES ->
                            uiState.storagePlatformGames.platformName.uppercase().ifBlank {
                                stringResource(R.string.settings_shell_header_storage_platform_games_fallback)
                            }
                        SettingsSection.STORAGE_CACHES -> stringResource(R.string.settings_shell_header_storage_caches)
                        SettingsSection.THEME -> stringResource(R.string.settings_shell_header_theme)
                        SettingsSection.AUDIO -> stringResource(R.string.settings_shell_header_audio)
                        SettingsSection.THEME_SOUNDS -> stringResource(R.string.settings_shell_header_theme_sounds)
                        SettingsSection.THEME_MUSIC -> stringResource(R.string.settings_shell_header_theme_music)
                        SettingsSection.THEME_FONTS -> stringResource(R.string.settings_shell_header_theme_fonts)
                        SettingsSection.THEME_BACKDROP -> stringResource(R.string.settings_shell_header_theme_backdrop)
                        SettingsSection.INTERFACE -> stringResource(R.string.settings_shell_header_interface)
                        SettingsSection.BOX_ART -> stringResource(R.string.settings_shell_header_box_art)
                        SettingsSection.CONTROLLER_GRIP -> stringResource(R.string.settings_shell_header_controller_grip)
                        SettingsSection.HOME_SCREEN -> stringResource(R.string.settings_shell_header_home_screen)
                        SettingsSection.LIBRARY_VIEW -> stringResource(R.string.settings_shell_header_library_view)
                        SettingsSection.DISPLAYS -> stringResource(R.string.settings_shell_header_displays)
                        SettingsSection.AMBIENT_LED -> stringResource(R.string.settings_shell_header_ambient_led)
                        SettingsSection.NAVIGATION -> stringResource(R.string.settings_shell_header_navigation)
                        SettingsSection.PLATFORMS -> stringResource(R.string.settings_shell_header_platforms)
                        SettingsSection.BUILTIN_EMULATOR -> stringResource(R.string.settings_shell_header_builtin_emulator)
                        SettingsSection.PLATFORM_DETAIL -> {
                            val config = uiState.emulators.platforms.getOrNull(uiState.platformDetail.platformIndex)
                            config?.platform?.name?.uppercase()
                                ?: stringResource(R.string.settings_shell_header_platform_detail_fallback)
                        }
                        SettingsSection.BUILTIN_VIDEO -> stringResource(R.string.settings_shell_header_builtin_video)
                        SettingsSection.BUILTIN_CONTROLS -> stringResource(R.string.settings_shell_header_builtin_controls)
                        SettingsSection.CORE_MANAGEMENT -> stringResource(R.string.settings_shell_header_core_management)
                        SettingsSection.CORE_OPTIONS -> stringResource(R.string.settings_shell_header_core_options)
                        SettingsSection.BIOS -> stringResource(R.string.settings_shell_header_bios)
                        SettingsSection.SHADER_STACK -> stringResource(R.string.settings_shell_header_shader_stack)
                        SettingsSection.FRAME_PICKER -> stringResource(R.string.settings_shell_header_frame_picker)
                        SettingsSection.PERMISSIONS -> stringResource(R.string.settings_shell_header_permissions)
                        SettingsSection.DRIVERS -> stringResource(R.string.settings_shell_header_drivers)
                        SettingsSection.ABOUT -> stringResource(R.string.settings_shell_header_about)
                        SettingsSection.SOCIAL -> stringResource(R.string.settings_shell_header_social)
                    },
                    rightContent = if ((uiState.currentSection == SettingsSection.BUILTIN_VIDEO ||
                        uiState.currentSection == SettingsSection.BUILTIN_CONTROLS) &&
                        uiState.builtinVideo.availablePlatforms.isNotEmpty()) {
                        {
                            val globalContextLabel = stringResource(R.string.settings_shell_header_context_global)
                            val platformName = if (uiState.builtinVideo.isGlobalContext) {
                                globalContextLabel
                            } else {
                                uiState.builtinVideo.currentPlatformContext?.platformName ?: globalContextLabel
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
                    SettingsSection.ACCOUNTS -> AccountsSection(uiState, viewModel)
                    SettingsSection.ROMM -> RomMSection(uiState, viewModel)
                    SettingsSection.SAVES -> SavesSection(uiState, viewModel)
                    SettingsSection.SYNC_SETTINGS -> SyncSettingsSection(uiState, viewModel, imageCacheProgress)
                    SettingsSection.STEAM_SETTINGS -> SteamSection(uiState, viewModel)
                    SettingsSection.JELLYFIN -> JellyfinSection(uiState, viewModel)
                    SettingsSection.RETRO_ACHIEVEMENTS -> RASettingsSection(uiState, viewModel)
                    SettingsSection.STORAGE -> StorageSection(uiState, viewModel)
                    SettingsSection.STORAGE_GAMES -> StorageGamesSection(uiState, viewModel)
                    SettingsSection.STORAGE_MEDIA -> StorageMediaSection(uiState, viewModel)
                    SettingsSection.STORAGE_PLATFORM_GAMES -> StoragePlatformGamesSection(uiState, viewModel)
                    SettingsSection.STORAGE_CACHES -> StorageCachesSection(uiState, viewModel)
                    SettingsSection.THEME -> ThemeSection(uiState, viewModel)
                    SettingsSection.AUDIO -> AudioSection(uiState, viewModel)
                    SettingsSection.THEME_SOUNDS -> ThemeSoundsSection(uiState, viewModel)
                    SettingsSection.THEME_MUSIC -> ThemeMusicSection(uiState, viewModel)
                    SettingsSection.THEME_FONTS -> ThemeFontsSection(uiState, viewModel)
                    SettingsSection.THEME_BACKDROP -> ThemeBackdropSection(uiState, viewModel)
                    SettingsSection.INTERFACE -> InterfaceSection(uiState, viewModel)
                    SettingsSection.BOX_ART -> BoxArtSection(uiState, viewModel)
                    SettingsSection.CONTROLLER_GRIP -> ControllerGripSection(uiState, viewModel)
                    SettingsSection.HOME_SCREEN -> HomeScreenSection(uiState, viewModel)
                    SettingsSection.LIBRARY_VIEW -> LibrarySection(uiState, viewModel)
                    SettingsSection.DISPLAYS -> DisplaysSection(uiState, viewModel)
                    SettingsSection.AMBIENT_LED -> AmbientLedSection(uiState, viewModel)
                    SettingsSection.NAVIGATION -> NavigationSection(uiState, viewModel)
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

            SettingsFooter(
                uiState = uiState,
                shaderStack = viewModel.shaderChainManager.shaderStack,
                onHintClick = { button ->
                    when (button) {
                        InputButton.A -> { inputHandler.onConfirm() }
                        InputButton.X -> { inputHandler.onContextMenu() }
                        InputButton.Y -> { inputHandler.onSecondaryAction() }
                        InputButton.LB_RB -> { inputHandler.onNextSection() }
                        InputButton.LT_RT -> { inputHandler.onNextTrigger() }
                        InputButton.B -> { inputHandler.onBack() }
                        else -> Unit
                    }
                }
            )
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
        if (uiState.bios.showDownloadFailureModal) {
            BiosDownloadFailureModal(
                failures = uiState.bios.downloadFailures,
                onDismiss = { viewModel.dismissDownloadFailureModal() }
            )
        }
    }

    val builtinMigration = uiState.pendingBuiltinPathMigration
    val builtinMigrationTitle = when (builtinMigration?.pathType) {
        BuiltinPathType.SAVE -> stringResource(R.string.settings_shell_modal_builtin_migration_save_title)
        BuiltinPathType.STATE -> stringResource(R.string.settings_shell_modal_builtin_migration_state_title)
        null -> ""
    }
    val builtinMigrationMessage = when (builtinMigration?.pathType) {
        BuiltinPathType.SAVE -> stringResource(
            R.string.settings_shell_modal_builtin_migration_save_message,
            builtinMigration.existingFileCount
        )
        BuiltinPathType.STATE -> stringResource(
            R.string.settings_shell_modal_builtin_migration_state_message,
            builtinMigration.existingFileCount
        )
        null -> ""
    }
    ArgosyConfirmModalHost(
        visible = uiState.showBuiltinPathMigrationDialog && builtinMigration != null,
        title = builtinMigrationTitle,
        message = builtinMigrationMessage,
        confirmLabel = stringResource(R.string.settings_shell_modal_builtin_migration_confirm),
        onConfirm = { viewModel.confirmBuiltinPathMigration() },
        onDismiss = { viewModel.cancelBuiltinPathMigration() },
        neutralLabel = stringResource(R.string.settings_shell_modal_builtin_migration_skip),
        onNeutral = { viewModel.skipBuiltinPathMigration() }
    )

    val musicRelocation = uiState.ambientAudio.pendingMusicRelocation
    ArgosyConfirmModalHost(
        visible = musicRelocation != null,
        title = stringResource(R.string.settings_shell_modal_music_relocation_title),
        message = stringResource(
            R.string.settings_shell_modal_music_relocation_message, musicRelocation?.fileCount ?: 0
        ),
        confirmLabel = stringResource(R.string.settings_shell_modal_music_relocation_confirm),
        onConfirm = { viewModel.confirmMusicRelocation() },
        onDismiss = { viewModel.cancelMusicRelocation() },
        neutralLabel = stringResource(R.string.settings_shell_modal_music_relocation_skip),
        onNeutral = { viewModel.skipMusicRelocation() }
    )

    val mediaRelocation = uiState.jellyfin.pendingMediaRelocation
    ArgosyConfirmModalHost(
        visible = mediaRelocation != null,
        title = stringResource(R.string.settings_shell_modal_media_relocation_title),
        message = stringResource(
            R.string.settings_shell_modal_media_relocation_message, mediaRelocation?.fileCount ?: 0
        ),
        confirmLabel = stringResource(R.string.settings_shell_modal_media_relocation_confirm),
        onConfirm = { viewModel.confirmMediaRelocation() },
        onDismiss = { viewModel.cancelMediaRelocation() },
        neutralLabel = stringResource(R.string.settings_shell_modal_media_relocation_leave),
        onNeutral = { viewModel.skipMediaRelocation() }
    )

    ArgosyConfirmModalHost(
        visible = uiState.jellyfin.showSignOutConfirm,
        title = stringResource(R.string.settings_shell_modal_jellyfin_signout_title),
        message = stringResource(R.string.settings_shell_modal_jellyfin_signout_message),
        confirmLabel = stringResource(R.string.settings_shell_modal_jellyfin_signout_confirm),
        destructive = true,
        onConfirm = { viewModel.confirmJellyfinSignOut() },
        onDismiss = { viewModel.cancelJellyfinSignOut() }
    )

    ArgosyConfirmModalHost(
        visible = uiState.showImportSettingsConfirm,
        title = stringResource(R.string.settings_shell_modal_import_settings_title),
        message = stringResource(R.string.settings_shell_modal_import_settings_message),
        confirmLabel = stringResource(R.string.settings_shell_modal_import_settings_confirm),
        onConfirm = { viewModel.confirmImportSettings() },
        onDismiss = { viewModel.cancelImportSettings() }
    )

    ArgosyConfirmModalHost(
        visible = uiState.showMigrationDialog,
        title = stringResource(R.string.settings_shell_modal_migrate_downloads_title),
        message = stringResource(
            R.string.settings_shell_modal_migrate_downloads_message,
            uiState.storage.downloadedGamesCount,
            formatBytes(uiState.storage.downloadedGamesSize)
        ),
        confirmLabel = stringResource(R.string.settings_shell_modal_migrate_downloads_confirm),
        onConfirm = { viewModel.confirmMigration() },
        onDismiss = { viewModel.cancelMigration() },
        neutralLabel = stringResource(R.string.settings_shell_modal_migrate_downloads_skip),
        onNeutral = { viewModel.skipMigration() }
    )

    val platformMigrationInfo = uiState.storage.showMigratePlatformConfirm
    ArgosyConfirmModalHost(
        visible = platformMigrationInfo != null,
        title = stringResource(
            R.string.settings_shell_modal_migrate_platform_title,
            platformMigrationInfo?.platformName
                ?: stringResource(R.string.settings_shell_modal_migrate_platform_fallback)
        ),
        message = stringResource(R.string.settings_shell_modal_migrate_platform_message),
        confirmLabel = stringResource(R.string.settings_shell_modal_migrate_platform_confirm),
        onConfirm = { viewModel.confirmPlatformMigration() },
        onDismiss = { viewModel.cancelPlatformMigration() },
        neutralLabel = stringResource(R.string.settings_shell_modal_migrate_platform_skip),
        onNeutral = { viewModel.skipPlatformMigration() }
    )

    val purgePlatformConfig = uiState.storage.showPurgePlatformConfirm?.let { platformId ->
        uiState.storage.platformConfigs.find { it.platformId == platformId }
    }
    ArgosyConfirmModalHost(
        visible = uiState.storage.showPurgePlatformConfirm != null,
        title = stringResource(
            R.string.settings_shell_modal_purge_platform_title,
            purgePlatformConfig?.platformName
                ?: stringResource(R.string.settings_shell_modal_purge_platform_fallback)
        ),
        message = stringResource(
            R.string.settings_shell_modal_purge_platform_message, purgePlatformConfig?.gameCount ?: 0
        ),
        confirmLabel = stringResource(R.string.settings_shell_modal_purge_platform_confirm),
        destructive = true,
        onConfirm = { viewModel.confirmPurgePlatform() },
        onDismiss = { viewModel.cancelPurgePlatform() }
    )

    if (uiState.storage.purgeAllPendingUploads > 0) {
        ArgosyConfirmModalHost(
            visible = uiState.storage.showPurgeAllConfirm,
            title = stringResource(R.string.settings_shell_modal_sync_first_title),
            message = stringResource(
                R.string.settings_shell_modal_sync_first_message, uiState.storage.purgeAllPendingUploads
            ),
            confirmLabel = stringResource(R.string.settings_shell_modal_sync_first_confirm),
            onConfirm = {
                viewModel.cancelPurgeAll()
                viewModel.requestSyncSaves()
            },
            onDismiss = { viewModel.cancelPurgeAll() }
        )
    } else {
        ArgosyConfirmModalHost(
            visible = uiState.storage.showPurgeAllConfirm,
            title = stringResource(R.string.settings_shell_modal_reset_library_title),
            message = stringResource(R.string.settings_shell_modal_reset_library_message),
            confirmLabel = stringResource(R.string.settings_shell_modal_reset_library_confirm),
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
            " " + pluralStringResource(
                R.plurals.settings_shell_unsynced_saves_warning,
                platformGameDelete.unsyncedSaves,
                platformGameDelete.unsyncedSaves
            )
        } else {
            ""
        }
        if (platformGameDelete.hasSoundtrack) {
            ArgosyConfirmModalHost(
                visible = true,
                title = stringResource(
                    R.string.settings_shell_modal_delete_game_soundtrack_title, platformGameDelete.title
                ),
                message = stringResource(
                    R.string.settings_shell_modal_delete_game_soundtrack_message, saveWarning
                ),
                cancelLabel = stringResource(R.string.settings_shell_modal_delete_game_cancel),
                neutralLabel = stringResource(R.string.settings_shell_modal_delete_game_only),
                onNeutral = { viewModel.confirmStoragePlatformGameDelete(platformGameDelete.gameId, withSoundtrack = false) },
                confirmLabel = stringResource(R.string.settings_shell_modal_delete_game_with_soundtrack),
                destructive = true,
                onConfirm = { viewModel.confirmStoragePlatformGameDelete(platformGameDelete.gameId, withSoundtrack = true) },
                onDismiss = { viewModel.dismissStoragePlatformGameDelete() }
            )
        } else {
            ArgosyConfirmModalHost(
                visible = true,
                title = stringResource(
                    R.string.settings_shell_modal_delete_game_plain_title, platformGameDelete.title
                ),
                message = stringResource(
                    R.string.settings_shell_modal_delete_game_plain_message, saveWarning
                ),
                confirmLabel = stringResource(R.string.settings_shell_modal_delete_game_plain_confirm),
                destructive = true,
                onConfirm = { viewModel.confirmStoragePlatformGameDelete(platformGameDelete.gameId, withSoundtrack = false) },
                onDismiss = { viewModel.dismissStoragePlatformGameDelete() }
            )
        }
    }

    val platformCategoryDelete = uiState.storagePlatformGames.categoryDeleteConfirm
    if (platformCategoryDelete != null) {
        val bucketLabel = stringResource(
            com.nendo.argosy.ui.screens.settings.sections
                .bucketDisplayLabelRes(platformCategoryDelete.bucket)
        )
        ArgosyConfirmModalHost(
            visible = true,
            title = stringResource(R.string.settings_storage_category_delete_title, bucketLabel),
            message = pluralStringResource(
                R.plurals.settings_storage_category_delete_message,
                platformCategoryDelete.fileCount,
                platformCategoryDelete.fileCount,
                formatBytes(platformCategoryDelete.totalBytes)
            ),
            confirmLabel = stringResource(R.string.settings_storage_category_delete_confirm),
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

    AccountModals(uiState, viewModel)

    ArgosyConfirmModalHost(
        visible = uiState.server.showRommSignOutConfirm,
        title = stringResource(R.string.settings_shell_modal_romm_signout_title),
        message = if (uiState.server.rommSignOutPendingUploads > 0) {
            pluralStringResource(
                R.plurals.settings_shell_romm_signout_pending_message,
                uiState.server.rommSignOutPendingUploads,
                uiState.server.rommSignOutPendingUploads
            )
        } else {
            stringResource(R.string.settings_shell_romm_signout_message)
        },
        confirmLabel = stringResource(R.string.settings_shell_modal_romm_signout_confirm),
        destructive = true,
        onConfirm = { viewModel.confirmRommSignOut() },
        onDismiss = { viewModel.cancelRommSignOut() }
    )

    ArgosyConfirmModalHost(
        visible = uiState.server.rommSignOutBlockedBy != null,
        title = stringResource(R.string.settings_shell_romm_signout_blocked_title),
        message = stringResource(
            R.string.settings_shell_romm_signout_blocked_message,
            uiState.server.rommSignOutBlockedBy.orEmpty()
        ),
        cancelLabel = stringResource(R.string.settings_shell_romm_signout_blocked_cancel),
        confirmLabel = stringResource(R.string.settings_shell_romm_signout_blocked_confirm),
        destructive = true,
        onConfirm = { viewModel.forceRommSignOut() },
        onDismiss = { viewModel.dismissRommSignOutBlocked() }
    )

    ArgosyConfirmModalHost(
        visible = uiState.syncSettings.showResetSaveCacheConfirm,
        title = stringResource(R.string.settings_shell_modal_reset_save_cache_title),
        message = stringResource(R.string.settings_shell_modal_reset_save_cache_message),
        confirmLabel = stringResource(R.string.settings_shell_modal_reset_save_cache_confirm),
        destructive = true,
        onConfirm = { viewModel.confirmResetSaveCache() },
        onDismiss = { viewModel.cancelResetSaveCache() }
    )

    ArgosyConfirmModalHost(
        visible = uiState.syncSettings.showClearPathCacheConfirm,
        title = stringResource(R.string.settings_shell_modal_clear_save_path_cache_title),
        message = stringResource(R.string.settings_shell_modal_clear_save_path_cache_message),
        confirmLabel = stringResource(R.string.settings_shell_modal_clear_save_path_cache_confirm),
        destructive = true,
        onConfirm = { viewModel.confirmClearPathCache() },
        onDismiss = { viewModel.cancelClearPathCache() }
    )

    ArgosyConfirmModalHost(
        visible = uiState.syncSettings.showSecureSavesConfirm,
        title = stringResource(R.string.settings_shell_modal_secure_saves_off_title),
        message = stringResource(R.string.settings_shell_modal_secure_saves_off_message),
        confirmLabel = stringResource(R.string.settings_shell_modal_secure_saves_off_confirm),
        destructive = true,
        onConfirm = { viewModel.confirmDisableSecureSaves() },
        onDismiss = { viewModel.cancelDisableSecureSaves() }
    )

    ArgosyConfirmModalHost(
        visible = uiState.syncSettings.showClearStateCacheConfirm,
        title = stringResource(R.string.settings_shell_modal_clear_state_cache_title),
        message = stringResource(R.string.settings_shell_modal_clear_state_cache_message),
        confirmLabel = stringResource(R.string.settings_shell_modal_clear_state_cache_confirm),
        destructive = true,
        onConfirm = { viewModel.confirmClearStateCache() },
        onDismiss = { viewModel.cancelClearStateCache() }
    )

    val pendingCachesClear = uiState.storageCaches.pendingClear
    ArgosyConfirmModalHost(
        visible = pendingCachesClear != null,
        title = cachesClearConfirmTitle(pendingCachesClear),
        message = cachesClearConfirmMessage(pendingCachesClear),
        confirmLabel = stringResource(R.string.settings_shell_modal_caches_clear_confirm),
        destructive = true,
        onConfirm = { viewModel.confirmCachesClear() },
        onDismiss = { viewModel.cancelCachesClear() }
    )

    val pendingDeleteCoreId = uiState.coreOptions.pendingDeleteCoreId
    ArgosyConfirmModalHost(
        visible = pendingDeleteCoreId != null,
        title = stringResource(R.string.settings_shell_modal_delete_core_title),
        message = stringResource(R.string.settings_shell_modal_delete_core_message),
        confirmLabel = stringResource(R.string.settings_shell_modal_delete_core_confirm),
        destructive = true,
        onConfirm = { viewModel.confirmDeleteCore() },
        onDismiss = { viewModel.cancelDeleteCore() }
    )

    ArgosyConfirmModal(
        visible = uiState.syncSettings.showForceSyncConfirm,
        title = stringResource(R.string.settings_shell_modal_sync_saves_title),
        message = stringResource(R.string.settings_shell_modal_sync_saves_message),
        confirmLabel = stringResource(R.string.settings_shell_modal_sync_saves_confirm),
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

    if (showCertBrowser) {
        FileBrowserScreen(
            mode = FileBrowserMode.FILE_SELECTION,
            title = stringResource(R.string.settings_shell_filebrowser_certificate_title),
            fileFilter = com.nendo.argosy.ui.filebrowser.FileFilter.CERTIFICATE,
            onPathSelected = { path ->
                showCertBrowser = false
                viewModel.importCertificate(path)
            },
            onDismiss = { showCertBrowser = false }
        )
    }

    if (showSettingsBackupBrowser) {
        FileBrowserScreen(
            mode = FileBrowserMode.FILE_SELECTION,
            title = stringResource(R.string.settings_shell_filebrowser_settings_backup_title),
            onPathSelected = { path ->
                showSettingsBackupBrowser = false
                viewModel.importSettingsFrom(path)
            },
            onDismiss = { showSettingsBackupBrowser = false }
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
            title = stringResource(R.string.settings_shell_filebrowser_add_music_title),
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
            title = stringResource(R.string.settings_shell_filebrowser_music_location_title),
            onPathSelected = { path ->
                showMusicLocationBrowser = false
                viewModel.onMusicLocationSelected(path)
            },
            onDismiss = {
                showMusicLocationBrowser = false
            }
        )
    }

    if (showMediaLocationBrowser) {
        FileBrowserScreen(
            mode = FileBrowserMode.FOLDER_SELECTION,
            title = stringResource(R.string.settings_shell_filebrowser_media_location_title),
            onPathSelected = { path ->
                showMediaLocationBrowser = false
                viewModel.onMediaLocationSelected(path)
            },
            onDismiss = {
                showMediaLocationBrowser = false
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
            title = stringResource(R.string.settings_shell_filebrowser_custom_sound_title),
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
                contentDescription = stringResource(R.string.settings_shell_context_previous_desc),
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
                contentDescription = stringResource(R.string.settings_shell_context_next_desc),
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

/**
 * Every accounts prompt goes through [ArgosyConfirmModalHost] so the modal takes the input stack
 * as it opens; a rendered-only modal would leave the gamepad driving the list behind it.
 */
@Composable
private fun AccountModals(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val accounts = uiState.accounts

    val accountFallbackName = stringResource(R.string.settings_shell_accounts_fallback_name)
    val exitAccount = accounts.exitPromptAccount
    ArgosyConfirmModalHost(
        visible = accounts.exitPromptAccountId != null,
        title = if (accounts.exitPromptIsForAdd) {
            stringResource(R.string.settings_shell_accounts_add_title)
        } else {
            stringResource(R.string.settings_shell_accounts_switch_title, exitAccount?.username ?: accountFallbackName)
        },
        message = stringResource(R.string.settings_shell_accounts_exit_message),
        confirmLabel = if (accounts.exitPromptIsForAdd) {
            stringResource(R.string.settings_shell_accounts_add_confirm)
        } else {
            stringResource(R.string.settings_shell_accounts_switch_confirm)
        },
        onConfirm = { viewModel.confirmAccountExitPrompt() },
        onDismiss = { viewModel.cancelAccountExitPrompt() }
    )

    val removalAccount = accounts.removalAccount
    val removalFallbackName = stringResource(R.string.settings_shell_accounts_remove_fallback_name)
    val removalPendingSuffixTemplate = stringResource(R.string.settings_shell_accounts_removal_pending_suffix)
    val removalMessage = buildString {
        append(stringResource(R.string.settings_shell_accounts_removal_message_base))
        accounts.removalPendingSummary?.let { append(removalPendingSuffixTemplate.format(it)) }
        if (accounts.removalIsLastAccount) {
            append(stringResource(R.string.settings_shell_accounts_removal_last_account_suffix))
        }
        append(stringResource(R.string.settings_shell_accounts_removal_revoke_suffix))
    }
    ArgosyConfirmModalHost(
        visible = accounts.removalAccountId != null && !accounts.isRemoving,
        title = stringResource(R.string.settings_shell_accounts_remove_title, removalAccount?.username ?: removalFallbackName),
        message = removalMessage,
        cancelLabel = stringResource(R.string.settings_shell_accounts_cancel),
        neutralLabel = if (accounts.removalHasPendingWork) {
            stringResource(R.string.settings_shell_accounts_keep_queued_work)
        } else {
            null
        },
        onNeutral = { viewModel.confirmAccountRemoval(UnflushedQueuePolicy.REFUSE) },
        confirmLabel = if (accounts.removalHasPendingWork) {
            stringResource(R.string.settings_shell_accounts_discard_and_remove)
        } else {
            stringResource(R.string.settings_shell_accounts_remove_confirm)
        },
        destructive = true,
        onConfirm = { viewModel.confirmAccountRemoval(UnflushedQueuePolicy.DISCARD) },
        onDismiss = { viewModel.cancelAccountRemoval() }
    )

    ArgosyConfirmModalHost(
        visible = accounts.switchFailure != null,
        title = stringResource(R.string.settings_shell_accounts_switch_failed_title),
        message = stringResource(
            R.string.settings_shell_accounts_switch_failed_message, accounts.switchFailure.orEmpty()
        ),
        confirmLabel = stringResource(R.string.settings_shell_accounts_retry),
        onConfirm = { viewModel.retryInterruptedAccountSwitch() },
        onDismiss = { viewModel.dismissAccountNotice() },
        cancelLabel = stringResource(R.string.settings_shell_accounts_not_now)
    )

    ArgosyConfirmModalHost(
        visible = accounts.switchBlocker != null,
        title = stringResource(R.string.settings_shell_accounts_cannot_switch_title),
        message = accounts.switchBlocker.orEmpty(),
        confirmLabel = stringResource(R.string.settings_shell_accounts_ok),
        onConfirm = { viewModel.dismissAccountNotice() },
        onDismiss = { viewModel.dismissAccountNotice() },
        cancelLabel = stringResource(R.string.settings_shell_accounts_close)
    )

    ArgosyConfirmModalHost(
        visible = accounts.notice != null && accounts.switchBlocker == null && accounts.switchFailure == null,
        title = stringResource(R.string.settings_shell_accounts_notice_title),
        message = accounts.notice.orEmpty(),
        confirmLabel = stringResource(R.string.settings_shell_accounts_ok2),
        onConfirm = { viewModel.dismissAccountNotice() },
        onDismiss = { viewModel.dismissAccountNotice() },
        cancelLabel = stringResource(R.string.settings_shell_accounts_close2)
    )
}

@Composable
private fun SettingsFooter(
    uiState: SettingsUiState,
    shaderStack: ShaderStackState,
    onHintClick: ((InputButton) -> Unit)? = null
) {
    if (uiState.emulators.showSavePathModal || uiState.emulators.showEmulatorPicker ||
        uiState.emulators.updateModal != null || uiState.emulators.showLaunchArgsModal ||
        uiState.emulators.showAppPickerModal || uiState.emulators.showMemcardPicker) {
        return
    }
    if (shaderStack.showShaderPicker) {
        return
    }
    if (uiState.currentSection == SettingsSection.ACCOUNTS && uiState.accounts.switchInProgress) {
        return
    }

    val navigateHint = stringResource(R.string.settings_shell_footer_navigate)
    val navigateVerticalHint = stringResource(R.string.settings_shell_footer_navigate_vertical)
    val previewShapeHint = stringResource(R.string.settings_shell_footer_preview_shape)
    val previewGameHint = stringResource(R.string.settings_shell_footer_preview_game)
    val shaderHint = stringResource(R.string.settings_shell_footer_shader)
    val reorderHint = stringResource(R.string.settings_shell_footer_reorder)
    val adjustShaderStackHint = stringResource(R.string.settings_shell_footer_adjust_shaderstack)
    val resetShaderStackHint = stringResource(R.string.settings_shell_footer_reset_shaderstack)
    val removeShaderStackHint = stringResource(R.string.settings_shell_footer_remove_shaderstack)
    val addShaderStackHint = stringResource(R.string.settings_shell_footer_add_shaderstack)
    val platformBuiltinHint = stringResource(R.string.settings_shell_footer_platform_builtin)
    val resetToDefaultBuiltinVideoHint = stringResource(R.string.settings_shell_footer_reset_to_default_builtinvideo)
    val sampleHint = stringResource(R.string.settings_shell_footer_sample)
    val resetToDefaultThemeSoundsHint = stringResource(R.string.settings_shell_footer_reset_to_default_themesounds)
    val platformCoreOptionsHint = stringResource(R.string.settings_shell_footer_platform_coreoptions)
    val resetToDefaultCoreOptionsHint = stringResource(R.string.settings_shell_footer_reset_to_default_coreoptions)
    val forceSyncHint = stringResource(R.string.settings_shell_footer_force_sync)
    val foldersScanHint = stringResource(R.string.settings_shell_footer_folders_scan)
    val switchRemoveHint = stringResource(R.string.settings_shell_footer_switch_remove)
    val refreshHint = stringResource(R.string.settings_shell_footer_refresh)
    val sortHint = stringResource(R.string.settings_shell_footer_sort)
    val categoryHint = stringResource(R.string.settings_shell_footer_category)
    val deleteHint = stringResource(R.string.settings_shell_footer_delete)
    val adjustPlatformDetailHint = stringResource(R.string.settings_shell_footer_adjust_platformdetail)
    val updateEmulatorHint = stringResource(R.string.settings_shell_footer_update_emulator)
    val resetPlatformDetailHint = stringResource(R.string.settings_shell_footer_reset_platformdetail)
    val toggleLabelHint = stringResource(R.string.settings_shell_footer_toggle_label)
    val openLabelHint = stringResource(R.string.settings_shell_footer_open_label)
    val selectLabelHint = stringResource(R.string.settings_shell_footer_select_label)
    val selectDefaultHint = stringResource(R.string.settings_shell_footer_select_default)
    val displayHint = stringResource(R.string.settings_shell_footer_display)
    val enableHint = stringResource(R.string.settings_shell_footer_enable)
    val updateHint = stringResource(R.string.settings_shell_footer_update)
    val resetBuiltinVideoOverrideHint = stringResource(R.string.settings_shell_footer_reset_builtinvideo_override)
    val resetBuiltinControlsOverrideHint = stringResource(R.string.settings_shell_footer_reset_builtincontrols_override)
    val backHint = stringResource(R.string.settings_shell_footer_back)

    val hints = buildList {
        if (uiState.currentSection != SettingsSection.BOX_ART &&
            uiState.currentSection != SettingsSection.SHADER_STACK) {
            add(InputButton.DPAD to navigateHint)
        }
        if (uiState.currentSection == SettingsSection.SHADER_STACK &&
            shaderStack.entries.isNotEmpty() &&
            shaderStack.selectedShaderParams.isNotEmpty()
        ) {
            add(InputButton.DPAD_VERTICAL to navigateVerticalHint)
        }
        if (uiState.currentSection == SettingsSection.BOX_ART) {
            add(InputButton.LB_RB to previewShapeHint)
            add(InputButton.LT_RT to previewGameHint)
        }
        if (uiState.currentSection == SettingsSection.SHADER_STACK) {
            if (shaderStack.entries.isNotEmpty()) {
                add(InputButton.LB_RB to shaderHint)
                add(InputButton.LT_RT to reorderHint)
                if (shaderStack.selectedShaderParams.isNotEmpty()) {
                    add(InputButton.DPAD_HORIZONTAL to adjustShaderStackHint)
                    add(InputButton.A to resetShaderStackHint)
                }
                add(InputButton.Y to removeShaderStackHint)
            }
            add(InputButton.X to addShaderStackHint)
        }
        if ((uiState.currentSection == SettingsSection.BUILTIN_VIDEO ||
            uiState.currentSection == SettingsSection.BUILTIN_CONTROLS) &&
            uiState.builtinVideo.availablePlatforms.isNotEmpty()) {
            add(InputButton.LB_RB to platformBuiltinHint)
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
                add(InputButton.Y to resetToDefaultBuiltinVideoHint)
            }
        }
        if (uiState.currentSection == SettingsSection.THEME_SOUNDS && uiState.sounds.enabled) {
            val soundsLayout = ThemeSoundsLayoutState.from(uiState)
            val focusedSoundItem = themeSoundsItemAtFocusIndex(uiState.focusedIndex, soundsLayout)
            if (focusedSoundItem is ThemeSoundsItem.SoundTypeItem) {
                add(InputButton.X to sampleHint)
                if (uiState.sounds.soundConfigs.containsKey(focusedSoundItem.soundType)) {
                    add(InputButton.Y to resetToDefaultThemeSoundsHint)
                }
            }
        }
        if (uiState.currentSection == SettingsSection.CORE_OPTIONS &&
            uiState.coreOptions.availablePlatforms.isNotEmpty()) {
            add(InputButton.LB_RB to platformCoreOptionsHint)
            val focusedCoreItem = coreOptionsItemAtFocusIndex(
                uiState.focusedIndex, uiState.coreOptions
            )
            if (focusedCoreItem is CoreOptionItem.Option && focusedCoreItem.isOverridden) {
                add(InputButton.Y to resetToDefaultCoreOptionsHint)
            }
        }
        if (uiState.currentSection == SettingsSection.STEAM_SETTINGS) {
            val steamItem = com.nendo.argosy.ui.screens.settings.sections.steamItemAtFocusIndex(
                uiState.focusedIndex, uiState.steam
            )
            if (steamItem == com.nendo.argosy.ui.screens.settings.sections.SteamItem.SyncLibrary) {
                add(InputButton.X to forceSyncHint)
            }
            if (steamItem == com.nendo.argosy.ui.screens.settings.sections.SteamItem.GameNativeLibrary &&
                uiState.steam.gameNativeSyncDirs.isNotEmpty()
            ) {
                add(InputButton.DPAD_HORIZONTAL to foldersScanHint)
            }
        }
        if (uiState.currentSection == SettingsSection.ACCOUNTS && !uiState.accounts.pairing.active) {
            val focusedAccount = com.nendo.argosy.ui.screens.settings.sections
                .accountsItemAtFocusIndex(uiState.focusedIndex, uiState.accounts)
            if (focusedAccount is com.nendo.argosy.ui.screens.settings.sections.AccountsItem.Account &&
                uiState.accounts.actionsFor(focusedAccount.account).size > 1
            ) {
                add(InputButton.DPAD_HORIZONTAL to switchRemoveHint)
            }
        }
        if (uiState.currentSection == SettingsSection.STORAGE) {
            add(InputButton.X to refreshHint)
        }
        if (uiState.currentSection == SettingsSection.STORAGE_GAMES) {
            add(InputButton.X to sortHint)
        }
        if (uiState.currentSection == SettingsSection.STORAGE_PLATFORM_GAMES) {
            val pgInfo = createStoragePlatformGamesLayoutInfo(uiState)
            val focusedPg = storagePlatformGamesItemAtFocusIndex(uiState.focusedIndex, pgInfo)
            val focusedGame = (focusedPg as? StoragePlatformGamesItem.GameCard)?.let { card ->
                uiState.storagePlatformGames.games.firstOrNull { it.gameId == card.gameId }
            }
            if (focusedGame != null) {
                if (focusedGame.buckets.size > 1) {
                    add(InputButton.DPAD_HORIZONTAL to categoryHint)
                }
                add(InputButton.Y to deleteHint)
            }
        }
        if (uiState.currentSection == SettingsSection.PLATFORM_DETAIL) {
            val config = uiState.emulators.platforms.getOrNull(uiState.platformDetail.platformIndex)
            val detail = uiState.platformDetail
            val storageConfig = config?.let { c ->
                uiState.storage.platformConfigs.find { it.platformId == c.platform.id }
            }
            val syncEnabled = storageConfig?.syncEnabled ?: true
            val focusedItem = config?.let {
                platformDetailItemAtFocusIndex(
                    uiState.focusedIndex, it, detail, syncEnabled,
                    storageConfig?.folderMemcardCount ?: -1
                )
            }
            if (focusedItem is PlatformDetailItem.Core ||
                focusedItem is PlatformDetailItem.Extension ||
                focusedItem is PlatformDetailItem.DisplayTarget ||
                focusedItem is PlatformDetailItem.Emulator) {
                add(InputButton.DPAD_HORIZONTAL to adjustPlatformDetailHint)
            }
            if (config != null) {
                val emulatorId = config.effectiveEmulatorId
                if (emulatorId != null && emulatorId in uiState.emulators.emulatorUpdateVersions) {
                    add(InputButton.X to updateEmulatorHint)
                }
            }
            val canReset = when (focusedItem) {
                is PlatformDetailItem.RomPath -> storageConfig?.customRomPath != null
                is PlatformDetailItem.SavePath -> storageConfig?.isUserSavePathOverride == true
                is PlatformDetailItem.StatePath -> storageConfig?.isUserStatePathOverride == true
                else -> false
            }
            if (canReset) {
                add(InputButton.Y to resetPlatformDetailHint)
            }
            val aLabel = when (focusedItem) {
                is PlatformDetailItem.SyncToggle, is PlatformDetailItem.LegacyMode -> toggleLabelHint
                is PlatformDetailItem.BuiltinVideo, is PlatformDetailItem.BuiltinControls, is PlatformDetailItem.BuiltinCoreOptions -> openLabelHint
                else -> selectLabelHint
            }
            add(InputButton.A to aLabel)
        } else if (uiState.currentSection != SettingsSection.SHADER_STACK) {
            add(InputButton.A to selectDefaultHint)
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
                add(InputButton.LB_RB to displayHint)
            }
            if (focusedItem is com.nendo.argosy.ui.screens.settings.sections.EmulatorsItem.PlatformItem) {
                if (!focusedItem.config.platform.syncEnabled) {
                    add(InputButton.Y to enableHint)
                } else {
                    val emulatorId = focusedItem.config.effectiveEmulatorId
                    if (emulatorId != null && emulatorId in uiState.emulators.emulatorUpdateVersions) {
                        add(InputButton.X to updateHint)
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
                add(InputButton.Y to resetBuiltinVideoOverrideHint)
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
                add(InputButton.Y to resetBuiltinControlsOverrideHint)
            }
        }
        add(InputButton.B to backHint)
    }

    FooterHints(hints = hints, onHintClick = onHintClick)
    FooterSpacer()
}

@Composable
private fun cachesClearConfirmTitle(target: CachesClearTarget?): String = when (target) {
    CachesClearTarget.IMAGE_CACHE -> stringResource(R.string.settings_shell_caches_image_title)
    CachesClearTarget.ROM_EXTRACTION -> stringResource(R.string.settings_shell_caches_rom_extraction_title)
    CachesClearTarget.ROM_STAGING -> stringResource(R.string.settings_shell_caches_rom_staging_title)
    CachesClearTarget.SFX_CACHE -> stringResource(R.string.settings_shell_caches_sfx_title)
    CachesClearTarget.EMULATOR_APKS -> stringResource(R.string.settings_shell_caches_emulator_apks_title)
    CachesClearTarget.MISC_DOWNLOADS -> stringResource(R.string.settings_shell_caches_misc_downloads_title)
    CachesClearTarget.SHADERS_CATALOG -> stringResource(R.string.settings_shell_caches_shaders_catalog_title)
    CachesClearTarget.FRAMES -> stringResource(R.string.settings_shell_caches_frames_title)
    CachesClearTarget.STEAM_DOWNLOADS -> stringResource(R.string.settings_shell_caches_steam_downloads_title)
    null -> ""
}

@Composable
private fun cachesClearConfirmMessage(target: CachesClearTarget?): String = when (target) {
    CachesClearTarget.IMAGE_CACHE -> stringResource(R.string.settings_shell_caches_image_message)
    CachesClearTarget.ROM_EXTRACTION -> stringResource(R.string.settings_shell_caches_rom_extraction_message)
    CachesClearTarget.ROM_STAGING -> stringResource(R.string.settings_shell_caches_rom_staging_message)
    CachesClearTarget.SFX_CACHE -> stringResource(R.string.settings_shell_caches_sfx_message)
    CachesClearTarget.MISC_DOWNLOADS -> stringResource(R.string.settings_shell_caches_misc_downloads_message)
    CachesClearTarget.EMULATOR_APKS -> stringResource(R.string.settings_shell_caches_emulator_apks_message)
    CachesClearTarget.SHADERS_CATALOG -> stringResource(R.string.settings_shell_caches_shaders_catalog_message)
    CachesClearTarget.FRAMES -> stringResource(R.string.settings_shell_caches_frames_message)
    CachesClearTarget.STEAM_DOWNLOADS -> stringResource(R.string.settings_shell_caches_steam_downloads_message)
    null -> ""
}

