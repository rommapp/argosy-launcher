package com.nendo.argosy.ui.screens.settings.sections

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.nendo.argosy.ui.components.FocusedScroll
import com.nendo.argosy.ui.components.NavigationPreference
import com.nendo.argosy.ui.screens.settings.ConnectionStatus
import com.nendo.argosy.ui.screens.settings.SettingsSection
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.theme.Dimens
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private sealed class MainSettingsItem(
    val key: String,
    val icon: ImageVector,
    val title: String
) {
    data object DeviceSettings : MainSettingsItem("device", Icons.Default.PhoneAndroid, "Device Settings")
    data object GameData : MainSettingsItem("gameData", Icons.Default.Dns, "Game Data")
    data object RetroAchievements : MainSettingsItem("retroAchievements", Icons.Default.EmojiEvents, "RetroAchievements")
    data object Storage : MainSettingsItem("storage", Icons.Default.Storage, "Storage")
    data object Interface : MainSettingsItem("interface", Icons.Default.Palette, "Interface")
    data object Controls : MainSettingsItem("controls", Icons.Default.TouchApp, "Controls")
    data object Emulators : MainSettingsItem("emulators", Icons.Default.Gamepad, "Emulators")
    data object Bios : MainSettingsItem("bios", Icons.Default.Memory, "BIOS Files")
    data object Permissions : MainSettingsItem("permissions", Icons.Default.Security, "Permissions")
    data object About : MainSettingsItem("about", Icons.Default.Info, "About")

    companion object {
        val ALL: List<MainSettingsItem> = listOf(
            DeviceSettings, GameData, RetroAchievements, Storage, Interface, Controls,
            Emulators, Bios, Permissions, About
        )
    }
}

private val mainSettingsLayout = SettingsLayout<MainSettingsItem, Unit>(
    allItems = MainSettingsItem.ALL,
    isFocusable = { true },
    visibleWhen = { _, _ -> true }
)

internal fun mainSettingsMaxFocusIndex(): Int = mainSettingsLayout.maxFocusIndex(Unit)

@Composable
fun MainSettingsSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val listState = rememberLazyListState()

    val visibleItems = remember { mainSettingsLayout.visibleItems(Unit) }

    fun isFocused(item: MainSettingsItem): Boolean =
        uiState.focusedIndex == mainSettingsLayout.focusIndexOf(item, Unit)

    fun getSubtitle(item: MainSettingsItem): String = when (item) {
        MainSettingsItem.DeviceSettings -> "System settings"
        MainSettingsItem.GameData -> when (uiState.server.connectionStatus) {
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
        MainSettingsItem.RetroAchievements -> if (uiState.retroAchievements.isLoggedIn) {
            "Logged in as ${uiState.retroAchievements.username}"
        } else {
            "Not logged in"
        }
        MainSettingsItem.Storage -> if (uiState.storage.downloadedGamesCount > 0) {
            "${uiState.storage.downloadedGamesCount} downloaded"
        } else {
            "No downloads"
        }
        MainSettingsItem.Interface -> "Theme, colors, sounds"
        MainSettingsItem.Controls -> "Button layout, haptic feedback"
        MainSettingsItem.Emulators -> "${uiState.emulators.installedEmulators.size} installed"
        MainSettingsItem.Bios -> uiState.bios.summaryText
        MainSettingsItem.Permissions -> if (uiState.permissions.allGranted) {
            "All granted"
        } else {
            "${uiState.permissions.grantedCount}/${uiState.permissions.totalCount} granted"
        }
        MainSettingsItem.About -> "Version ${uiState.appVersion}"
    }

    fun handleClick(item: MainSettingsItem) {
        when (item) {
            MainSettingsItem.DeviceSettings -> context.startActivity(Intent(Settings.ACTION_SETTINGS))
            MainSettingsItem.GameData -> viewModel.navigateToSection(SettingsSection.SERVER)
            MainSettingsItem.RetroAchievements -> viewModel.navigateToSection(SettingsSection.RETRO_ACHIEVEMENTS)
            MainSettingsItem.Storage -> viewModel.navigateToSection(SettingsSection.STORAGE)
            MainSettingsItem.Interface -> viewModel.navigateToSection(SettingsSection.INTERFACE)
            MainSettingsItem.Controls -> viewModel.navigateToSection(SettingsSection.CONTROLS)
            MainSettingsItem.Emulators -> viewModel.navigateToSection(SettingsSection.EMULATORS)
            MainSettingsItem.Bios -> viewModel.navigateToSection(SettingsSection.BIOS)
            MainSettingsItem.Permissions -> viewModel.navigateToSection(SettingsSection.PERMISSIONS)
            MainSettingsItem.About -> viewModel.navigateToSection(SettingsSection.ABOUT)
        }
    }

    FocusedScroll(
        listState = listState,
        focusedIndex = uiState.focusedIndex
    )

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        items(visibleItems, key = { it.key }) { item ->
            NavigationPreference(
                icon = item.icon,
                title = item.title,
                subtitle = getSubtitle(item),
                isFocused = isFocused(item),
                onClick = { handleClick(item) }
            )
        }
    }
}
