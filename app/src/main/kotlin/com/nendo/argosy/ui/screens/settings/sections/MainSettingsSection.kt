package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.nendo.argosy.ui.common.scrollToItemIfNeeded
import com.nendo.argosy.ui.components.NavigationPreference
import com.nendo.argosy.ui.screens.settings.ConnectionStatus
import com.nendo.argosy.ui.screens.settings.SettingsSection
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.theme.Dimens
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Composable
fun MainSettingsSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val listState = rememberLazyListState()

    // 1:1 mapping - focus index equals LazyColumn item index
    LaunchedEffect(uiState.focusedIndex) {
        if (uiState.focusedIndex in 0..8) {
            listState.scrollToItemIfNeeded(uiState.focusedIndex)
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        item {
            val gameDataSubtitle = when (uiState.server.connectionStatus) {
                ConnectionStatus.NOT_CONFIGURED -> "Server not configured"
                ConnectionStatus.CHECKING -> "Checking connection..."
                ConnectionStatus.OFFLINE -> "Server offline"
                ConnectionStatus.ONLINE -> {
                    uiState.server.lastRommSync?.let { instant ->
                        val formatter = DateTimeFormatter
                            .ofPattern("MMM d, h:mm a")
                            .withZone(ZoneId.systemDefault())
                        "Last sync: ${formatter.format(instant)}"
                    } ?: "Never synced"
                }
            }
            NavigationPreference(
                icon = Icons.Default.Dns,
                title = "Game Data",
                subtitle = gameDataSubtitle,
                isFocused = uiState.focusedIndex == 0,
                onClick = { viewModel.navigateToSection(SettingsSection.SERVER) }
            )
        }
        item {
            val downloadedText = if (uiState.storage.downloadedGamesCount > 0) {
                "${uiState.storage.downloadedGamesCount} downloaded"
            } else {
                "No downloads"
            }
            NavigationPreference(
                icon = Icons.Default.Storage,
                title = "Storage",
                subtitle = downloadedText,
                isFocused = uiState.focusedIndex == 1,
                onClick = { viewModel.navigateToSection(SettingsSection.STORAGE) }
            )
        }
        item {
            NavigationPreference(
                icon = Icons.Default.Palette,
                title = "Display",
                subtitle = "Theme, colors, animations",
                isFocused = uiState.focusedIndex == 2,
                onClick = { viewModel.navigateToSection(SettingsSection.DISPLAY) }
            )
        }
        item {
            NavigationPreference(
                icon = Icons.Default.TouchApp,
                title = "Controls",
                subtitle = "Button layout, haptic feedback",
                isFocused = uiState.focusedIndex == 3,
                onClick = { viewModel.navigateToSection(SettingsSection.CONTROLS) }
            )
        }
        item {
            NavigationPreference(
                icon = Icons.AutoMirrored.Filled.VolumeUp,
                title = "Sounds",
                subtitle = if (uiState.sounds.enabled) "Enabled" else "Disabled",
                isFocused = uiState.focusedIndex == 4,
                onClick = { viewModel.navigateToSection(SettingsSection.SOUNDS) }
            )
        }
        item {
            NavigationPreference(
                icon = Icons.Default.Gamepad,
                title = "Emulators",
                subtitle = "${uiState.emulators.installedEmulators.size} installed",
                isFocused = uiState.focusedIndex == 5,
                onClick = { viewModel.navigateToSection(SettingsSection.EMULATORS) }
            )
        }
        item {
            val biosStatus = uiState.bios.summaryText
            NavigationPreference(
                icon = Icons.Default.Memory,
                title = "BIOS Files",
                subtitle = biosStatus,
                isFocused = uiState.focusedIndex == 6,
                onClick = { viewModel.navigateToSection(SettingsSection.BIOS) }
            )
        }
        item {
            val permissionStatus = if (uiState.permissions.allGranted) {
                "All granted"
            } else {
                "${uiState.permissions.grantedCount}/${uiState.permissions.totalCount} granted"
            }
            NavigationPreference(
                icon = Icons.Default.Security,
                title = "Permissions",
                subtitle = permissionStatus,
                isFocused = uiState.focusedIndex == 7,
                onClick = { viewModel.navigateToSection(SettingsSection.PERMISSIONS) }
            )
        }
        item {
            NavigationPreference(
                icon = Icons.Default.Info,
                title = "About",
                subtitle = "Version ${uiState.appVersion}",
                isFocused = uiState.focusedIndex == 8,
                onClick = { viewModel.navigateToSection(SettingsSection.ABOUT) }
            )
        }
    }
}
