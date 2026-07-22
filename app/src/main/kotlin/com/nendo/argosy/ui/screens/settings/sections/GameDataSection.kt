package com.nendo.argosy.ui.screens.settings.sections

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import com.nendo.argosy.ui.screens.settings.components.SectionPaneLayout
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.GetApp
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.components.CyclePreference
import com.nendo.argosy.ui.components.Modal
import com.nendo.argosy.ui.components.NavigationPreference
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.ModalInputEffect
import com.nendo.argosy.ui.primitives.ModalActionButton
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.screens.settings.ConnectionStatus
import com.nendo.argosy.ui.screens.settings.InstalledSteamLauncher
import com.nendo.argosy.ui.screens.settings.NotInstalledSteamLauncher
import com.nendo.argosy.ui.screens.settings.SettingsSection
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.components.RomMConfigForm
import com.nendo.argosy.ui.screens.settings.components.SectionHeader
import com.nendo.argosy.ui.screens.settings.delegates.SyncSettingsDelegate
import com.nendo.argosy.ui.screens.settings.components.SteamLauncherPreference
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// --- Item definitions ---

internal sealed class GameDataItem(val key: String, val section: String) {
    val isFocusable: Boolean get() = this !is Header && this !is StorageNote

    class Header(key: String, section: String, val title: String) : GameDataItem(key, section)

    data object RomManager : GameDataItem("romManager", "server")
    data object SyncSettings : GameDataItem("syncSettings", "library")
    data object SyncLibrary : GameDataItem("syncLibrary", "library")
    data object AccuratePlayTime : GameDataItem("accuratePlayTime", "tracking")
    data object SaveSync : GameDataItem("saveSync", "saves")
    data object SaveCacheLimit : GameDataItem("saveCacheLimit", "saves")
    data object SyncSaves : GameDataItem("syncSaves", "saves")
    data object ClearPathCache : GameDataItem("clearPathCache", "saves")
    data object ResetSaveCache : GameDataItem("resetSaveCache", "saves")
    data object ScanAndroid : GameDataItem("scanAndroid", "android")
    data class InstalledLauncher(val data: InstalledSteamLauncher) :
        GameDataItem("steam_${data.packageName}", "steam")
    data object RefreshMetadata : GameDataItem("refreshMetadata", "steam")
    data class NotInstalledLauncher(val data: NotInstalledSteamLauncher) :
        GameDataItem("steam_install_${data.emulatorId}", "steam")
    data object StorageNote : GameDataItem("storageNote", "steam")
}

internal fun buildGameDataItems(
    isConnected: Boolean,
    saveSyncEnabled: Boolean,
    installedLaunchers: List<InstalledSteamLauncher>,
    notInstalledLaunchers: List<NotInstalledSteamLauncher>,
    hasStoragePermission: Boolean,
    isSteamLoggedIn: Boolean = false
): List<GameDataItem> = buildList {
    add(GameDataItem.Header("serverHeader", "server", "SERVER"))
    add(GameDataItem.RomManager)

    if (isConnected) {
        add(GameDataItem.Header("libraryHeader", "library", "LIBRARY"))
        add(GameDataItem.SyncSettings)
        add(GameDataItem.SyncLibrary)

        add(GameDataItem.Header("trackingHeader", "tracking", "TRACKING"))
        add(GameDataItem.AccuratePlayTime)

        add(GameDataItem.Header("savesHeader", "saves", "SAVES"))
        add(GameDataItem.SaveSync)
        if (saveSyncEnabled) {
            add(GameDataItem.SaveCacheLimit)
            add(GameDataItem.SyncSaves)
        }
        add(GameDataItem.ClearPathCache)
        add(GameDataItem.ResetSaveCache)
    }

    add(GameDataItem.Header("androidHeader", "android", "ANDROID"))
    add(GameDataItem.ScanAndroid)

    val visibleLaunchers = if (isSteamLoggedIn) {
        installedLaunchers.filter { it.packageName != "app.gamenative" }
    } else {
        installedLaunchers
    }
    val launcherCount = visibleLaunchers.size
    val steamSubtitle = if (launcherCount > 0) {
        "$launcherCount app${if (launcherCount > 1) "s" else ""} detected"
    } else "No launchers installed"
    add(GameDataItem.Header("steamHeader", "steam", "STEAM ($steamSubtitle)"))

    for (launcher in visibleLaunchers) {
        add(GameDataItem.InstalledLauncher(launcher))
    }
    if (visibleLaunchers.isNotEmpty()) {
        add(GameDataItem.RefreshMetadata)
    }
    for (launcher in notInstalledLaunchers) {
        add(GameDataItem.NotInstalledLauncher(launcher))
    }

    if (!hasStoragePermission && installedLaunchers.isNotEmpty()) {
        add(GameDataItem.StorageNote)
    }
}

internal fun buildGameDataItemsFromState(state: SettingsUiState): List<GameDataItem> {
    val isConnected = state.server.connectionStatus == ConnectionStatus.ONLINE ||
        state.server.connectionStatus == ConnectionStatus.OFFLINE
    return buildGameDataItems(
        isConnected = isConnected,
        saveSyncEnabled = state.syncSettings.saveSyncEnabled,
        installedLaunchers = state.steam.installedLaunchers,
        notInstalledLaunchers = state.steam.notInstalledLaunchers,
        hasStoragePermission = state.steam.hasStoragePermission,
        isSteamLoggedIn = state.steam.connectionState == com.nendo.argosy.data.steam.SteamConnectionState.LOGGED_IN
    )
}

// --- Layout + focus helpers ---

internal fun createGameDataLayout(items: List<GameDataItem>) =
    SettingsLayout<GameDataItem, Unit>(
        allItems = items,
        isFocusable = { it.isFocusable },
        visibleWhen = { _, _ -> true },
        sectionOf = { it.section },
        sectionTitle = {
            when (it) {
                "server" -> "SERVER"
                "library" -> "LIBRARY"
                "tracking" -> "TRACKING"
                "saves" -> "SAVES"
                "android" -> "ANDROID"
                "steam" -> "STEAM"
                else -> null
            }
        }
    )

internal data class GameDataLayoutInfo(
    val layout: SettingsLayout<GameDataItem, Unit>,
    val items: List<GameDataItem>
)

internal fun createGameDataLayoutInfo(items: List<GameDataItem>): GameDataLayoutInfo =
    GameDataLayoutInfo(createGameDataLayout(items), items)

internal fun focusableItems(items: List<GameDataItem>): List<GameDataItem> =
    createGameDataLayout(items).focusableItems(Unit)

internal fun gameDataItemAtFocusIndex(index: Int, items: List<GameDataItem>): GameDataItem? =
    createGameDataLayout(items).itemAtFocusIndex(index, Unit)

internal fun gameDataSections(items: List<GameDataItem>) =
    createGameDataLayout(items).buildSections(Unit)

internal fun gameDataMaxFocusIndex(items: List<GameDataItem>): Int =
    createGameDataLayout(items).maxFocusIndex(Unit)

internal fun gameDataFocusIndexOf(item: GameDataItem, items: List<GameDataItem>): Int =
    createGameDataLayout(items).focusIndexOf(item, Unit)

// --- Composables ---

@Composable
fun GameDataSection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel
) {
    if (uiState.server.rommConfiguring) {
        RomMConfigForm(uiState, viewModel)
    } else {
        GameDataContent(uiState, viewModel)
    }

    if (uiState.steam.showAddGameDialog) {
        AddSteamGameDialog(uiState, viewModel)
    }

    if (uiState.steam.variantPickerInfo != null) {
        com.nendo.argosy.ui.screens.settings.components.VariantPickerModal(
            info = uiState.steam.variantPickerInfo,
            focusIndex = uiState.steam.variantPickerFocusIndex,
            onItemTap = { index -> viewModel.handleSteamVariantItemTap(index) },
            onConfirm = { viewModel.confirmSteamVariantSelection() },
            onDismiss = { viewModel.dismissSteamVariantPicker() }
        )
    }
}

@Composable
private fun GameDataContent(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val isConnected = uiState.server.connectionStatus == ConnectionStatus.ONLINE ||
        uiState.server.connectionStatus == ConnectionStatus.OFFLINE
    val isOnline = uiState.server.connectionStatus == ConnectionStatus.ONLINE
    val saveSyncEnabled = uiState.syncSettings.saveSyncEnabled

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refreshSteamSettings()
            viewModel.refreshUsageStatsPermission()
        }
    }

    val hasDialogOpen = uiState.steam.showAddGameDialog || uiState.steam.variantPickerInfo != null

    val isSteamLoggedIn = uiState.steam.connectionState == com.nendo.argosy.data.steam.SteamConnectionState.LOGGED_IN
    val allItems = remember(
        isConnected, saveSyncEnabled,
        uiState.steam.installedLaunchers, uiState.steam.notInstalledLaunchers,
        uiState.steam.hasStoragePermission, isSteamLoggedIn
    ) {
        buildGameDataItems(
            isConnected = isConnected,
            saveSyncEnabled = saveSyncEnabled,
            installedLaunchers = uiState.steam.installedLaunchers,
            notInstalledLaunchers = uiState.steam.notInstalledLaunchers,
            hasStoragePermission = uiState.steam.hasStoragePermission,
            isSteamLoggedIn = isSteamLoggedIn
        )
    }

    val layout = remember(allItems) { createGameDataLayout(allItems) }
    val sections = remember(allItems) { layout.buildSections(Unit) }

    fun isFocused(item: GameDataItem): Boolean {
        if (hasDialogOpen) return false
        return uiState.focusedIndex == layout.focusIndexOf(item, Unit)
    }

    val isDownloading = uiState.steam.downloadingLauncherId != null

    SectionPaneLayout(
        items = allItems,
        sections = sections,
        focusedIndex = uiState.focusedIndex,
        focusToListIndex = { layout.focusToListIndex(it, Unit) },
        itemKey = { it.key },
        isNavItem = { false },
        isHeader = { it is GameDataItem.Header },
        onSectionTap = { viewModel.setFocusIndex(it.focusStartIndex) },
        modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) { item ->
            when (item) {
                is GameDataItem.Header -> {
                    if (item.key != "serverHeader") {
                        Spacer(modifier = Modifier.height(Dimens.spacingSm))
                    }
                    SectionHeader(item.title)
                }

                GameDataItem.RomManager -> NavigationPreference(
                    icon = Icons.Default.Dns,
                    title = "Rom Manager",
                    subtitle = when (uiState.server.connectionStatus) {
                        ConnectionStatus.CHECKING -> "Checking connection..."
                        ConnectionStatus.ONLINE -> uiState.server.rommUrl ?: "Connected"
                        ConnectionStatus.OFFLINE -> "${uiState.server.rommUrl} (offline)"
                        ConnectionStatus.NOT_CONFIGURED -> "Not configured"
                    },
                    isFocused = isFocused(item),
                    onClick = { viewModel.startRommConfig() }
                )

                GameDataItem.SyncSettings -> NavigationPreference(
                    icon = Icons.Default.Tune,
                    title = "Sync Settings",
                    subtitle = "Filters and media options",
                    isFocused = isFocused(item),
                    onClick = { viewModel.navigateToSection(SettingsSection.SYNC_SETTINGS) }
                )

                GameDataItem.SyncLibrary -> {
                    val enabledCount = uiState.syncSettings.enabledPlatformCount
                    val totalCount = uiState.syncSettings.totalPlatforms
                    val platformText = if (totalCount > 0) "$enabledCount/$totalCount platforms" else ""
                    val lastSyncText = uiState.server.lastRommSync?.let { instant ->
                        val formatter = DateTimeFormatter
                            .ofPattern("MMM d, h:mm a")
                            .withZone(ZoneId.systemDefault())
                        if (platformText.isNotEmpty()) "$platformText - ${formatter.format(instant)}"
                        else "Last: ${formatter.format(instant)}"
                    } ?: if (platformText.isNotEmpty()) platformText else "Never synced"
                    ActionPreference(
                        icon = Icons.AutoMirrored.Filled.LibraryBooks,
                        title = "Sync Library",
                        subtitle = lastSyncText,
                        isFocused = isFocused(item),
                        isEnabled = isOnline,
                        onClick = { viewModel.syncRomm() }
                    )
                }

                GameDataItem.AccuratePlayTime -> {
                    val hasPermission = uiState.controls.hasUsageStatsPermission
                    val isEnabled = uiState.controls.accuratePlayTimeEnabled
                    val subtitle = when {
                        isEnabled && hasPermission -> "Tracking active screen time"
                        isEnabled && !hasPermission -> "Permission required - tap to grant"
                        else -> "Track only when screen is on"
                    }
                    SwitchPreference(
                        title = "Accurate Play Time",
                        subtitle = subtitle,
                        isEnabled = isEnabled,
                        isFocused = isFocused(item),
                        onToggle = { enabled ->
                            if (enabled && !hasPermission) {
                                viewModel.openUsageStatsSettings()
                            } else {
                                viewModel.setAccuratePlayTimeEnabled(enabled)
                            }
                        },
                        onLabelClick = if (isEnabled && !hasPermission) {
                            { viewModel.openUsageStatsSettings() }
                        } else null
                    )
                }

                GameDataItem.SaveSync -> SwitchPreference(
                    title = "Save Sync",
                    subtitle = "Sync game saves with server",
                    isEnabled = saveSyncEnabled,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.toggleSaveSync() }
                )

                GameDataItem.SaveCacheLimit -> {
                    val limits = SyncSettingsDelegate.SAVE_CACHE_LIMIT_VALUES
                    CyclePreference(
                        title = "Local Save Cache",
                        value = "${uiState.syncSettings.saveCacheLimit} saves per game",
                        isFocused = isFocused(item),
                        onClick = { viewModel.cycleSaveCacheLimit(1) },
                        onPrev = { viewModel.cycleSaveCacheLimit(-1) },
                        options = remember { limits.map { "$it saves per game" } },
                        onSelect = { viewModel.setSaveCacheLimit(limits[it]) },
                        pickerRequestToken = if (uiState.enumPickerKey == item.key) uiState.enumPickerToken else 0
                    )
                }

                GameDataItem.SyncSaves -> {
                    val pendingText = if (uiState.syncSettings.pendingUploadsCount > 0) {
                        "${uiState.syncSettings.pendingUploadsCount} pending"
                    } else "Up to date"
                    ActionPreference(
                        icon = Icons.Default.Sync,
                        title = "Sync Saves",
                        subtitle = pendingText,
                        isFocused = isFocused(item),
                        isEnabled = isOnline,
                        onClick = { viewModel.requestSyncSaves() }
                    )
                }

                GameDataItem.ClearPathCache -> {
                    val pathCount = uiState.syncSettings.pathCacheCount
                    val pendingUploads = uiState.syncSettings.pendingUploadsCount
                    val subtitle = when {
                        uiState.syncSettings.isClearingPathCache -> "Clearing..."
                        pendingUploads > 0 -> "$pendingUploads saves waiting to upload"
                        pathCount > 0 -> "$pathCount cached paths"
                        else -> "No cached paths"
                    }
                    ActionPreference(
                        title = "Clear Save Path Cache",
                        subtitle = subtitle,
                        isFocused = isFocused(item),
                        isEnabled = !uiState.syncSettings.isClearingPathCache && pathCount > 0 && pendingUploads == 0,
                        onClick = { viewModel.requestClearPathCache() }
                    )
                }

                GameDataItem.ResetSaveCache -> {
                    val totalCached = uiState.syncSettings.saveCacheCount + uiState.syncSettings.stateCacheCount
                    val pendingUploads = uiState.syncSettings.pendingUploadsCount
                    val subtitle = when {
                        uiState.syncSettings.isResettingSaveCache -> "Resetting..."
                        pendingUploads > 0 -> "$pendingUploads saves waiting to upload"
                        totalCached > 0 -> "$totalCached cached entries"
                        else -> "No cached entries"
                    }
                    ActionPreference(
                        title = "Reset Save Cache",
                        subtitle = subtitle,
                        isFocused = isFocused(item),
                        isEnabled = !uiState.syncSettings.isResettingSaveCache && totalCached > 0 && pendingUploads == 0,
                        isDangerous = true,
                        onClick = { viewModel.requestResetSaveCache() }
                    )
                }

                GameDataItem.ScanAndroid -> {
                    val subtitle = when {
                        uiState.android.isScanning -> "Scanning... ${uiState.android.scanProgressPercent}%"
                        uiState.android.lastScanGamesAdded != null -> "${uiState.android.lastScanGamesAdded} games found"
                        else -> "Detect installed games"
                    }
                    ActionPreference(
                        icon = Icons.Default.PhoneAndroid,
                        title = "Scan for Android Games",
                        subtitle = subtitle,
                        isFocused = isFocused(item),
                        isEnabled = !uiState.android.isScanning,
                        onClick = { viewModel.scanForAndroidGames() }
                    )
                }

                is GameDataItem.InstalledLauncher -> {
                    val launcher = item.data
                    val isSyncingThis = uiState.steam.isSyncing &&
                        uiState.steam.syncingLauncher == launcher.packageName
                    SteamLauncherPreference(
                        displayName = launcher.displayName,
                        subtitle = if (launcher.scanMayIncludeUninstalled) {
                            "Scan may include titles no longer installed"
                        } else null,
                        supportsScanning = launcher.supportsScanning,
                        isSyncing = isSyncingThis,
                        isFocused = isFocused(item),
                        isEnabled = uiState.steam.hasStoragePermission && !uiState.steam.isSyncing,
                        actionIndex = uiState.steam.launcherActionIndex,
                        onScan = { viewModel.scanSteamLauncher(launcher.packageName) },
                        onAdd = { viewModel.showAddSteamGameDialog(launcher.packageName) }
                    )
                }

                GameDataItem.RefreshMetadata -> {
                    val isRefreshing = uiState.steam.isSyncing &&
                        uiState.steam.syncingLauncher == "refresh"
                    ActionPreference(
                        icon = Icons.Default.Sync,
                        title = "Refresh Metadata",
                        subtitle = if (isRefreshing) "Refreshing..."
                            else "Update screenshots and backgrounds",
                        isFocused = isFocused(item),
                        isEnabled = !uiState.steam.isSyncing,
                        onClick = { viewModel.refreshSteamMetadata() }
                    )
                }

                is GameDataItem.NotInstalledLauncher -> {
                    val launcher = item.data
                    val isThisDownloading = uiState.steam.downloadingLauncherId == launcher.emulatorId
                    val subtitle = when {
                        isThisDownloading && uiState.steam.downloadProgress != null -> {
                            val pct = (uiState.steam.downloadProgress * 100).toInt()
                            "Downloading... $pct%"
                        }
                        isThisDownloading -> "Waiting for install..."
                        launcher.hasDirectDownload -> "Download APK"
                        else -> "Open Play Store"
                    }
                    ActionPreference(
                        icon = if (launcher.hasDirectDownload) Icons.Default.GetApp
                            else Icons.AutoMirrored.Filled.OpenInNew,
                        title = launcher.displayName,
                        subtitle = subtitle,
                        isFocused = isFocused(item),
                        isEnabled = !isDownloading,
                        onClick = { viewModel.installSteamLauncher(launcher.emulatorId) }
                    )
                }

                is GameDataItem.StorageNote -> {
                    Spacer(modifier = Modifier.height(Dimens.spacingSm))
                    ActionPreference(
                        icon = Icons.Default.Cloud,
                        title = "Grant Storage Permission",
                        subtitle = "Required for Steam integration",
                        isFocused = false,
                        onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                val intent = Intent(
                                    Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION
                                ).apply { data = Uri.parse("package:${context.packageName}") }
                                context.startActivity(intent)
                            }
                        }
                    )
                }
            }
    }
}

private const val ADD_GAME_ROW_FIELD = 0
private const val ADD_GAME_ROW_BUTTONS = 1

@Composable
internal fun AddSteamGameDialog(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val theme = LocalArgosyTheme.current
    val selectedLauncherName = uiState.steam.selectedLauncherPackage?.let { pkg ->
        uiState.steam.installedLaunchers.find { it.packageName == pkg }?.displayName
    }
    val isAddingGame = uiState.steam.isAddingGame
    val canAdd = !isAddingGame && uiState.steam.addGameAppId.isNotBlank()

    var focusRow by remember { mutableIntStateOf(ADD_GAME_ROW_FIELD) }
    var buttonIndex by remember { mutableIntStateOf(1) }
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val currentIsAdding by rememberUpdatedState(isAddingGame)
    val currentCanAdd by rememberUpdatedState(canAdd)

    LaunchedEffect(focusRow) {
        if (focusRow == ADD_GAME_ROW_FIELD) focusRequester.requestFocus() else focusManager.clearFocus()
    }

    val inputHandler = remember {
        object : InputHandler {
            override fun onUp(): InputResult {
                if (!currentIsAdding) focusRow = ADD_GAME_ROW_FIELD
                return InputResult.HANDLED
            }

            override fun onDown(): InputResult {
                if (!currentIsAdding) focusRow = ADD_GAME_ROW_BUTTONS
                return InputResult.HANDLED
            }

            override fun onLeft(): InputResult {
                if (!currentIsAdding && focusRow == ADD_GAME_ROW_BUTTONS) buttonIndex = 0
                return InputResult.HANDLED
            }

            override fun onRight(): InputResult {
                if (!currentIsAdding && focusRow == ADD_GAME_ROW_BUTTONS) buttonIndex = 1
                return InputResult.HANDLED
            }

            override fun onConfirm(): InputResult {
                when {
                    currentIsAdding -> {}
                    focusRow == ADD_GAME_ROW_FIELD -> focusRow = ADD_GAME_ROW_BUTTONS
                    buttonIndex == 0 -> viewModel.dismissAddSteamGameDialog()
                    currentCanAdd -> viewModel.confirmAddSteamGame()
                }
                return InputResult.HANDLED
            }

            override fun onBack(): InputResult {
                if (!currentIsAdding) viewModel.dismissAddSteamGameDialog()
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
        title = "Add Steam Game",
        onDismiss = { if (!isAddingGame) viewModel.dismissAddSteamGameDialog() }
    ) {
        val description = if (selectedLauncherName != null) {
            "Enter the Steam App ID to add a game for $selectedLauncherName. You can find this in the game's Steam store URL."
        } else {
            "Enter the Steam App ID to add a game. You can find this in the game's Steam store URL."
        }
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(Dimens.spacingMd))
        OutlinedTextField(
            value = uiState.steam.addGameAppId,
            onValueChange = { viewModel.setAddGameAppId(it) },
            label = { Text("Steam App ID") },
            placeholder = { Text("e.g. 730") },
            singleLine = true,
            enabled = !isAddingGame,
            isError = uiState.steam.addGameError != null,
            shape = fieldShape,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { if (canAdd) viewModel.confirmAddSteamGame() }),
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(focusRequester)
                .then(
                    if (focusRow == ADD_GAME_ROW_FIELD) {
                        Modifier.background(theme.focusAccent.copy(alpha = 0.15f), fieldShape)
                    } else Modifier
                )
        )
        if (uiState.steam.addGameError != null) {
            Spacer(modifier = Modifier.height(Dimens.spacingSm))
            Text(
                text = uiState.steam.addGameError,
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
                focused = focusRow == ADD_GAME_ROW_BUTTONS && buttonIndex == 0,
                onClick = { viewModel.dismissAddSteamGameDialog() },
                enabled = !isAddingGame
            )
            if (isAddingGame) {
                CircularProgressIndicator(
                    modifier = Modifier.size(Dimens.iconMd),
                    strokeWidth = Dimens.borderMedium
                )
            } else {
                ModalActionButton(
                    label = "Add",
                    tint = theme.focusAccent,
                    restLabelColor = theme.textPrimary,
                    focused = focusRow == ADD_GAME_ROW_BUTTONS && buttonIndex == 1,
                    onClick = { viewModel.confirmAddSteamGame() },
                    enabled = canAdd
                )
            }
        }
    }
}
