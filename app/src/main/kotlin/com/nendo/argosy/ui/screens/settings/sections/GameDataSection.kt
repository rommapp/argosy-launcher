package com.nendo.argosy.ui.screens.settings.sections

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.GetApp
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.components.CyclePreference
import com.nendo.argosy.ui.components.ListSection
import com.nendo.argosy.ui.components.NavigationPreference
import com.nendo.argosy.ui.components.SectionFocusedScroll
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.screens.settings.ConnectionStatus
import com.nendo.argosy.ui.screens.settings.InstalledSteamLauncher
import com.nendo.argosy.ui.screens.settings.NotInstalledSteamLauncher
import com.nendo.argosy.ui.screens.settings.SettingsSection
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.components.RomMConfigForm
import com.nendo.argosy.ui.screens.settings.components.SectionHeader
import com.nendo.argosy.ui.screens.settings.components.SteamLauncherPreference
import com.nendo.argosy.ui.theme.Dimens
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
    hasStoragePermission: Boolean
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

    val launcherCount = installedLaunchers.size
    val steamSubtitle = if (launcherCount > 0) {
        "$launcherCount app${if (launcherCount > 1) "s" else ""} detected"
    } else "No launchers installed"
    add(GameDataItem.Header("steamHeader", "steam", "STEAM ($steamSubtitle)"))

    for (launcher in installedLaunchers) {
        add(GameDataItem.InstalledLauncher(launcher))
    }
    if (installedLaunchers.isNotEmpty()) {
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
        hasStoragePermission = state.steam.hasStoragePermission
    )
}

// --- Focus helpers ---

internal fun focusableItems(items: List<GameDataItem>): List<GameDataItem> =
    items.filter { it.isFocusable }

internal fun gameDataItemAtFocusIndex(index: Int, items: List<GameDataItem>): GameDataItem? =
    focusableItems(items).getOrNull(index)

internal fun gameDataMaxFocusIndex(items: List<GameDataItem>): Int =
    (focusableItems(items).size - 1).coerceAtLeast(0)

internal fun gameDataFocusIndexOf(item: GameDataItem, items: List<GameDataItem>): Int =
    focusableItems(items).indexOf(item)

private fun gameDataFocusToListIndex(focusIndex: Int, items: List<GameDataItem>): Int {
    val focusable = focusableItems(items)
    val item = focusable.getOrNull(focusIndex) ?: return focusIndex
    return items.indexOf(item)
}

internal fun gameDataBuildSections(items: List<GameDataItem>): List<ListSection> {
    val focusable = focusableItems(items)
    val sectionNames = items.map { it.section }.distinct()

    return sectionNames.mapNotNull { sectionName ->
        val sectionItems = items.filter { it.section == sectionName }
        val sectionFocusable = focusable.filter { it.section == sectionName }
        if (sectionItems.isEmpty() || sectionFocusable.isEmpty()) return@mapNotNull null

        ListSection(
            listStartIndex = items.indexOf(sectionItems.first()),
            listEndIndex = items.indexOf(sectionItems.last()),
            focusStartIndex = focusable.indexOf(sectionFocusable.first()),
            focusEndIndex = focusable.indexOf(sectionFocusable.last())
        )
    }
}

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
    val listState = rememberLazyListState()
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

    val allItems = remember(
        isConnected, saveSyncEnabled,
        uiState.steam.installedLaunchers, uiState.steam.notInstalledLaunchers,
        uiState.steam.hasStoragePermission
    ) {
        buildGameDataItems(
            isConnected = isConnected,
            saveSyncEnabled = saveSyncEnabled,
            installedLaunchers = uiState.steam.installedLaunchers,
            notInstalledLaunchers = uiState.steam.notInstalledLaunchers,
            hasStoragePermission = uiState.steam.hasStoragePermission
        )
    }

    val sections = remember(allItems) { gameDataBuildSections(allItems) }

    fun isFocused(item: GameDataItem): Boolean {
        if (hasDialogOpen) return false
        return uiState.focusedIndex == gameDataFocusIndexOf(item, allItems)
    }

    SectionFocusedScroll(
        listState = listState,
        focusedIndex = uiState.focusedIndex,
        focusToListIndex = { gameDataFocusToListIndex(it, allItems) },
        sections = sections
    )

    val isDownloading = uiState.steam.downloadingLauncherId != null

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        items(allItems, key = { it.key }) { item ->
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

                GameDataItem.SaveCacheLimit -> CyclePreference(
                    title = "Local Save Cache",
                    value = "${uiState.syncSettings.saveCacheLimit} saves per game",
                    isFocused = isFocused(item),
                    onClick = { viewModel.cycleSaveCacheLimit() }
                )

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
                    val subtitle = when {
                        uiState.syncSettings.isClearingPathCache -> "Clearing..."
                        pathCount > 0 -> "$pathCount cached paths"
                        else -> "No cached paths"
                    }
                    ActionPreference(
                        title = "Clear Save Path Cache",
                        subtitle = subtitle,
                        isFocused = isFocused(item),
                        isEnabled = !uiState.syncSettings.isClearingPathCache && pathCount > 0,
                        onClick = { viewModel.requestClearPathCache() }
                    )
                }

                GameDataItem.ResetSaveCache -> {
                    val totalCached = uiState.syncSettings.saveCacheCount + uiState.syncSettings.stateCacheCount
                    val subtitle = when {
                        uiState.syncSettings.isResettingSaveCache -> "Resetting..."
                        totalCached > 0 -> "$totalCached cached entries"
                        else -> "No cached entries"
                    }
                    ActionPreference(
                        title = "Reset Save Cache",
                        subtitle = subtitle,
                        isFocused = isFocused(item),
                        isEnabled = !uiState.syncSettings.isResettingSaveCache && totalCached > 0,
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
}

@Composable
private fun AddSteamGameDialog(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val selectedLauncherName = uiState.steam.selectedLauncherPackage?.let { pkg ->
        uiState.steam.installedLaunchers.find { it.packageName == pkg }?.displayName
    }
    AlertDialog(
        onDismissRequest = { viewModel.dismissAddSteamGameDialog() },
        title = { Text("Add Steam Game") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(Dimens.spacingMd)) {
                val description = if (selectedLauncherName != null) {
                    "Enter the Steam App ID to add a game for $selectedLauncherName. You can find this in the game's Steam store URL."
                } else {
                    "Enter the Steam App ID to add a game. You can find this in the game's Steam store URL."
                }
                Text(description, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = uiState.steam.addGameAppId,
                    onValueChange = { viewModel.setAddGameAppId(it) },
                    label = { Text("Steam App ID") },
                    placeholder = { Text("e.g. 730") },
                    singleLine = true,
                    enabled = !uiState.steam.isAddingGame,
                    isError = uiState.steam.addGameError != null,
                    modifier = Modifier.fillMaxWidth()
                )
                if (uiState.steam.addGameError != null) {
                    Text(
                        text = uiState.steam.addGameError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { viewModel.confirmAddSteamGame() },
                enabled = !uiState.steam.isAddingGame && uiState.steam.addGameAppId.isNotBlank()
            ) {
                if (uiState.steam.isAddingGame) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(Dimens.spacingMd),
                        strokeWidth = Dimens.borderMedium
                    )
                } else {
                    Text("Add")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = { viewModel.dismissAddSteamGameDialog() },
                enabled = !uiState.steam.isAddingGame
            ) {
                Text("Cancel")
            }
        }
    )
}
