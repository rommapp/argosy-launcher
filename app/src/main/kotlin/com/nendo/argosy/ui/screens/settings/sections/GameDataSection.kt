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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Info
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.components.CyclePreference
import com.nendo.argosy.ui.components.InfoPreference
import com.nendo.argosy.ui.components.NavigationPreference
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.screens.settings.ConnectionStatus
import com.nendo.argosy.ui.screens.settings.SettingsSection
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.components.RomMConfigForm
import com.nendo.argosy.ui.screens.settings.components.SectionHeader
import com.nendo.argosy.ui.screens.settings.components.SteamLauncherPreference
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.Motion
import java.time.ZoneId
import java.time.format.DateTimeFormatter

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
    val launcherCount = uiState.steam.installedLaunchers.size

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refreshSteamSettings()
            viewModel.refreshUsageStatsPermission()
        }
    }

    val maxIndex = calculateMaxIndex(isConnected, saveSyncEnabled, launcherCount)
    val steamBaseIndex = calculateSteamBaseIndex(isConnected, saveSyncEnabled)
    val steamHeaderScrollIndex = calculateSteamHeaderScrollIndex(isConnected, saveSyncEnabled)

    LaunchedEffect(uiState.focusedIndex) {
        if (uiState.focusedIndex in 0..maxIndex) {
            val scrollIndex = calculateScrollIndex(
                focusedIndex = uiState.focusedIndex,
                isConnected = isConnected,
                saveSyncEnabled = saveSyncEnabled
            )
            val viewportHeight = listState.layoutInfo.viewportSize.height
            val itemHeight = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.size ?: 0
            val centerOffset = if (itemHeight > 0) (viewportHeight - itemHeight) / 2 else 0
            val paddingBuffer = (itemHeight * Motion.scrollPaddingPercent).toInt()

            val isInSteamSection = uiState.focusedIndex >= steamBaseIndex
            if (isInSteamSection) {
                val steamItemOffset = uiState.focusedIndex - steamBaseIndex
                val targetScrollIndex = steamHeaderScrollIndex + steamItemOffset + 1
                val maxScrollIndex = steamHeaderScrollIndex
                val currentFirstVisible = listState.firstVisibleItemIndex

                if (currentFirstVisible < maxScrollIndex) {
                    listState.animateScrollToItem(maxScrollIndex, 0)
                } else if (targetScrollIndex > currentFirstVisible + 3) {
                    val scrollTarget = (targetScrollIndex - 3).coerceAtLeast(maxScrollIndex)
                    listState.animateScrollToItem(scrollTarget, 0)
                }
            } else {
                listState.animateScrollToItem(scrollIndex, -centerOffset + paddingBuffer)
            }
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        // === SERVER ===
        item { SectionHeader("SERVER") }
        item {
            NavigationPreference(
                icon = Icons.Default.Dns,
                title = "Rom Manager",
                subtitle = when (uiState.server.connectionStatus) {
                    ConnectionStatus.CHECKING -> "Checking connection..."
                    ConnectionStatus.ONLINE -> uiState.server.rommUrl ?: "Connected"
                    ConnectionStatus.OFFLINE -> "${uiState.server.rommUrl} (offline)"
                    ConnectionStatus.NOT_CONFIGURED -> "Not configured"
                },
                isFocused = uiState.focusedIndex == 0,
                onClick = { viewModel.startRommConfig() }
            )
        }

        // === LIBRARY === (only if connected)
        if (isConnected) {
            item {
                Spacer(modifier = Modifier.height(Dimens.spacingSm))
                SectionHeader("LIBRARY")
            }
            item {
                NavigationPreference(
                    icon = Icons.Default.Tune,
                    title = "Sync Settings",
                    subtitle = "Filters and media options",
                    isFocused = uiState.focusedIndex == 1,
                    onClick = { viewModel.navigateToSection(SettingsSection.SYNC_SETTINGS) }
                )
            }
            item {
                val lastSyncText = uiState.server.lastRommSync?.let { instant ->
                    val formatter = DateTimeFormatter
                        .ofPattern("MMM d, h:mm a")
                        .withZone(ZoneId.systemDefault())
                    "Last: ${formatter.format(instant)}"
                } ?: "Never synced"
                ActionPreference(
                    icon = Icons.AutoMirrored.Filled.LibraryBooks,
                    title = "Sync Library",
                    subtitle = lastSyncText,
                    isFocused = uiState.focusedIndex == 2,
                    isEnabled = isOnline,
                    onClick = { viewModel.syncRomm() }
                )
            }

            // === TRACKING ===
            item {
                Spacer(modifier = Modifier.height(Dimens.spacingSm))
                SectionHeader("TRACKING")
            }
            item {
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
                    isFocused = uiState.focusedIndex == 3,
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
        }

        // === SAVE GAMES === (only if connected + save sync enabled)
        if (isConnected && saveSyncEnabled) {
            item {
                Spacer(modifier = Modifier.height(Dimens.spacingSm))
                SectionHeader("SAVE GAMES")
            }
            item {
                CyclePreference(
                    title = "Local Save Cache",
                    value = "${uiState.syncSettings.saveCacheLimit} saves per game",
                    isFocused = uiState.focusedIndex == 4,
                    onClick = { viewModel.cycleSaveCacheLimit() }
                )
            }
            item {
                val pendingText = if (uiState.syncSettings.pendingUploadsCount > 0) {
                    "${uiState.syncSettings.pendingUploadsCount} pending"
                } else {
                    "Up to date"
                }
                ActionPreference(
                    icon = Icons.Default.Sync,
                    title = "Sync Saves",
                    subtitle = pendingText,
                    isFocused = uiState.focusedIndex == 5,
                    isEnabled = isOnline,
                    onClick = { viewModel.runSaveSyncNow() }
                )
            }
        }

        // === ANDROID ===
        val androidBaseIndex = calculateAndroidBaseIndex(isConnected, saveSyncEnabled)
        item {
            Spacer(modifier = Modifier.height(Dimens.spacingSm))
            SectionHeader("ANDROID")
        }
        item {
            val subtitle = when {
                uiState.android.isScanning -> "Scanning... ${uiState.android.scanProgressPercent}%"
                uiState.android.lastScanGamesAdded != null -> "${uiState.android.lastScanGamesAdded} games found"
                else -> "Detect installed games"
            }
            ActionPreference(
                icon = Icons.Default.PhoneAndroid,
                title = "Scan for Android Games",
                subtitle = subtitle,
                isFocused = uiState.focusedIndex == androidBaseIndex,
                isEnabled = !uiState.android.isScanning,
                onClick = { viewModel.scanForAndroidGames() }
            )
        }

        // === STEAM ===
        item {
            Spacer(modifier = Modifier.height(Dimens.spacingSm))
            val steamSubtitle = if (launcherCount > 0) {
                "$launcherCount app${if (launcherCount > 1) "s" else ""} detected"
            } else {
                "No launchers installed"
            }
            SectionHeader("STEAM ($steamSubtitle)")
        }

        val steamBaseIndex = calculateSteamBaseIndex(isConnected, saveSyncEnabled)

        if (launcherCount == 0) {
            item {
                InfoPreference(
                    title = "No Steam Launchers",
                    value = "Install GameHub Lite or GameNative",
                    isFocused = uiState.focusedIndex == steamBaseIndex,
                    icon = Icons.Default.Info
                )
            }
        } else {
            itemsIndexed(uiState.steam.installedLaunchers) { index, launcher ->
                val isSyncingThis = uiState.steam.isSyncing && uiState.steam.syncingLauncher == launcher.packageName
                val itemIndex = steamBaseIndex + index
                val isFocused = uiState.focusedIndex == itemIndex
                val isEnabled = uiState.steam.hasStoragePermission && !uiState.steam.isSyncing

                SteamLauncherPreference(
                    displayName = launcher.displayName,
                    subtitle = if (launcher.scanMayIncludeUninstalled) "Scan may include titles no longer installed" else null,
                    supportsScanning = launcher.supportsScanning,
                    isSyncing = isSyncingThis,
                    isFocused = isFocused,
                    isEnabled = isEnabled,
                    actionIndex = uiState.steam.launcherActionIndex,
                    onScan = { viewModel.scanSteamLauncher(launcher.packageName) },
                    onAdd = { viewModel.showAddSteamGameDialog(launcher.packageName) }
                )
            }

            item {
                val isRefreshing = uiState.steam.isSyncing && uiState.steam.syncingLauncher == "refresh"
                val refreshIndex = steamBaseIndex + launcherCount
                ActionPreference(
                    icon = Icons.Default.Sync,
                    title = "Refresh Metadata",
                    subtitle = if (isRefreshing) "Refreshing..." else "Update screenshots and backgrounds",
                    isFocused = uiState.focusedIndex == refreshIndex,
                    isEnabled = !uiState.steam.isSyncing,
                    onClick = { viewModel.refreshSteamMetadata() }
                )
            }
        }

        if (!uiState.steam.hasStoragePermission && launcherCount > 0) {
            item {
                Spacer(modifier = Modifier.height(Dimens.spacingSm))
                ActionPreference(
                    icon = Icons.Default.Cloud,
                    title = "Grant Storage Permission",
                    subtitle = "Required for Steam integration",
                    isFocused = false,
                    onClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                                data = Uri.parse("package:${context.packageName}")
                            }
                            context.startActivity(intent)
                        }
                    }
                )
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
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
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

private fun calculateMaxIndex(isConnected: Boolean, saveSyncEnabled: Boolean, launcherCount: Int): Int {
    val steamBaseIndex = calculateSteamBaseIndex(isConnected, saveSyncEnabled)
    return if (launcherCount > 0) {
        steamBaseIndex + launcherCount // launchers + refresh metadata
    } else {
        steamBaseIndex // just the "no launchers" info
    }
}

private fun calculateAndroidBaseIndex(isConnected: Boolean, saveSyncEnabled: Boolean): Int {
    return when {
        isConnected && saveSyncEnabled -> 6  // Rom Manager(0), Sync Settings(1), Sync Library(2), Accurate Play Time(3), Save Cache(4), Sync Saves(5), Android at 6
        isConnected -> 4                      // Rom Manager(0), Sync Settings(1), Sync Library(2), Accurate Play Time(3), Android at 4
        else -> 1                             // Rom Manager(0), Android at 1
    }
}

private fun calculateSteamBaseIndex(isConnected: Boolean, saveSyncEnabled: Boolean): Int {
    return calculateAndroidBaseIndex(isConnected, saveSyncEnabled) + 1 // Steam comes after Android
}

private fun calculateSteamHeaderScrollIndex(isConnected: Boolean, saveSyncEnabled: Boolean): Int {
    return when {
        isConnected && saveSyncEnabled -> 12  // SERVER(0), RomM(1), LIBRARY(2), SyncSet(3), SyncLib(4), TRACKING(5), PlayTime(6), SAVES(7), Cache(8), SyncSaves(9), ANDROID(10), ScanAndroid(11), STEAM(12)
        isConnected -> 9                       // SERVER(0), RomM(1), LIBRARY(2), SyncSet(3), SyncLib(4), TRACKING(5), PlayTime(6), ANDROID(7), ScanAndroid(8), STEAM(9)
        else -> 4                              // SERVER(0), RomM(1), ANDROID(2), ScanAndroid(3), STEAM(4)
    }
}

private fun calculateScrollIndex(
    focusedIndex: Int,
    isConnected: Boolean,
    saveSyncEnabled: Boolean
): Int {
    val androidBaseIndex = calculateAndroidBaseIndex(isConnected, saveSyncEnabled)
    val steamBaseIndex = calculateSteamBaseIndex(isConnected, saveSyncEnabled)

    return when {
        !isConnected -> {
            when {
                focusedIndex == 0 -> 1                      // Rom Manager (after SERVER header)
                focusedIndex == androidBaseIndex -> 3       // Scan Android (after ANDROID header)
                else -> focusedIndex + 4                    // Steam items (after STEAM header)
            }
        }
        !saveSyncEnabled -> {
            when {
                focusedIndex == 0 -> 1                      // Rom Manager (after SERVER header)
                focusedIndex in 1..2 -> focusedIndex + 2    // Sync Settings, Sync Library (after LIBRARY header)
                focusedIndex == 3 -> focusedIndex + 3       // Accurate Play Time (after TRACKING header)
                focusedIndex == androidBaseIndex -> focusedIndex + 4  // Scan Android (after ANDROID header)
                else -> focusedIndex + 5                    // Steam items (after STEAM header)
            }
        }
        else -> {
            when {
                focusedIndex == 0 -> 1                      // Rom Manager
                focusedIndex in 1..2 -> focusedIndex + 2    // Sync Settings, Sync Library
                focusedIndex == 3 -> focusedIndex + 3       // Accurate Play Time (after TRACKING header)
                focusedIndex in 4..5 -> focusedIndex + 4    // Save Cache, Sync Saves (after SAVES header)
                focusedIndex == androidBaseIndex -> focusedIndex + 5  // Scan Android (after ANDROID header)
                else -> focusedIndex + 6                    // Steam items (after STEAM header)
            }
        }
    }
}
