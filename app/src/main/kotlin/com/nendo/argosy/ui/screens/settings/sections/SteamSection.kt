package com.nendo.argosy.ui.screens.settings.sections

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.components.InfoPreference
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.theme.Dimens

@Composable
fun SteamSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val lifecycleOwner = LocalLifecycleOwner.current

    LaunchedEffect(lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            viewModel.refreshSteamSettings()
        }
    }

    val maxIndex = 2 + uiState.steam.installedLaunchers.size


    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        item {
            val (icon, subtitle, statusColor) = if (uiState.steam.hasStoragePermission) {
                Triple(
                    Icons.Default.CheckCircle,
                    "Storage access granted",
                    MaterialTheme.colorScheme.primary
                )
            } else {
                Triple(
                    Icons.Default.Warning,
                    "Grant storage access",
                    MaterialTheme.colorScheme.error
                )
            }

            ActionPreference(
                icon = icon,
                title = "Storage Permission",
                subtitle = subtitle,
                isFocused = uiState.focusedIndex == 0,
                onClick = {
                    if (!uiState.steam.hasStoragePermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                            data = Uri.parse("package:${context.packageName}")
                        }
                        context.startActivity(intent)
                    }
                },
                iconTint = statusColor
            )
        }

        if (uiState.steam.installedLaunchers.isEmpty()) {
            item {
                InfoPreference(
                    title = "No Steam Launchers",
                    value = "Install GameHub Lite or GameNative",
                    isFocused = uiState.focusedIndex == 1,
                    icon = Icons.Default.Info
                )
            }
        } else {
            itemsIndexed(uiState.steam.installedLaunchers) { index, launcher ->
                val isSyncingThis = uiState.steam.isSyncing && uiState.steam.syncingLauncher == launcher.packageName
                val isFocused = uiState.focusedIndex == index + 1
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
        }

        item {
            val isRefreshing = uiState.steam.isSyncing && uiState.steam.syncingLauncher == "refresh"
            val refreshIndex = 1 + uiState.steam.installedLaunchers.size
            ActionPreference(
                icon = Icons.Default.Sync,
                title = "Refresh Metadata",
                subtitle = if (isRefreshing) "Refreshing..." else "Update screenshots and backgrounds",
                isFocused = uiState.focusedIndex == refreshIndex,
                isEnabled = !uiState.steam.isSyncing,
                onClick = { viewModel.refreshSteamMetadata() }
            )
        }

        item {
            Spacer(modifier = Modifier.height(Dimens.spacingMd))
            Text(
                text = "Steam integration requires GameHub Lite or GameNative to be installed. " +
                    "Select a launcher to scan for games or add manually.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(horizontal = Dimens.spacingSm)
            )
        }
    }

    if (uiState.steam.showAddGameDialog) {
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
}

@Composable
private fun SteamLauncherPreference(
    displayName: String,
    subtitle: String?,
    supportsScanning: Boolean,
    isSyncing: Boolean,
    isFocused: Boolean,
    isEnabled: Boolean,
    actionIndex: Int,
    onScan: () -> Unit,
    onAdd: () -> Unit
) {
    val backgroundColor = if (isFocused) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (isFocused) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val secondaryColor = if (isFocused) {
        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.55f)
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Dimens.settingsItemMinHeight)
            .clip(RoundedCornerShape(Dimens.radiusLg))
            .background(backgroundColor, RoundedCornerShape(Dimens.radiusLg))
            .padding(Dimens.spacingMd),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isSyncing) Icons.Default.Sync else Icons.Default.Cloud,
            contentDescription = null,
            tint = if (isEnabled) contentColor else contentColor.copy(alpha = 0.5f),
            modifier = Modifier.size(Dimens.iconMd)
        )
        Spacer(modifier = Modifier.width(Dimens.spacingMd))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayName,
                style = MaterialTheme.typography.titleMedium,
                color = if (isEnabled) contentColor else contentColor.copy(alpha = 0.5f)
            )
            val subtitleText = when {
                isSyncing -> "Scanning..."
                subtitle != null -> subtitle
                else -> null
            }
            if (subtitleText != null) {
                Text(
                    text = subtitleText,
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryColor
                )
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (supportsScanning) {
                val scanSelected = isFocused && actionIndex == 0
                val scanBgColor = when {
                    scanSelected -> MaterialTheme.colorScheme.primary
                    isFocused -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)
                    else -> MaterialTheme.colorScheme.surfaceVariant
                }
                val scanTextColor = when {
                    scanSelected -> MaterialTheme.colorScheme.onPrimary
                    !isEnabled -> contentColor.copy(alpha = 0.5f)
                    else -> contentColor
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(Dimens.radiusSm))
                        .background(scanBgColor)
                        .clickable(enabled = isEnabled) { onScan() }
                        .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingXs)
                ) {
                    Text(
                        text = "Scan",
                        style = MaterialTheme.typography.labelMedium,
                        color = scanTextColor
                    )
                }
            }

            val addSelected = isFocused && if (supportsScanning) actionIndex == 1 else true
            val addBgColor = when {
                addSelected -> MaterialTheme.colorScheme.primary
                isFocused -> MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.12f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }
            val addTextColor = when {
                addSelected -> MaterialTheme.colorScheme.onPrimary
                !isEnabled -> contentColor.copy(alpha = 0.5f)
                else -> contentColor
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(Dimens.radiusSm))
                    .background(addBgColor)
                    .clickable(enabled = isEnabled) { onAdd() }
                    .padding(horizontal = Dimens.spacingMd, vertical = Dimens.spacingXs)
            ) {
                Text(
                    text = "Add",
                    style = MaterialTheme.typography.labelMedium,
                    color = addTextColor
                )
            }
        }
    }
}
