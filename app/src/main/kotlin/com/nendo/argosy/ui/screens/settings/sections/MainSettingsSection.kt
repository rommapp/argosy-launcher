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
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ManageAccounts
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
import com.nendo.argosy.ui.screens.settings.SocialAuthStatus
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import com.nendo.argosy.ui.screens.settings.components.SectionHeader
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.theme.Dimens
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal sealed class MainSettingsItem(
    val key: String,
    val icon: ImageVector,
    val title: String,
    val section: String
) {
    val isFocusable: Boolean get() = this !is Header

    class Header(key: String, section: String, title: String) :
        MainSettingsItem(key, Icons.Default.Info, title, section)

    data object Theme : MainSettingsItem("theme", Icons.Default.Palette, "Theme", "launcher")
    data object Interface : MainSettingsItem("interface", Icons.Default.Dashboard, "Interface", "launcher")
    data object Controls : MainSettingsItem("controls", Icons.Default.TouchApp, "Navigation", "launcher")

    data object BuiltinEmulator :
        MainSettingsItem("builtin_emulator", Icons.Default.Build, "Built-in Emulator", "gameplay")
    data object RetroAchievements : MainSettingsItem(
        "retroAchievements",
        Icons.Default.EmojiEvents,
        "RetroAchievements",
        "gameplay"
    )
    data object Bios : MainSettingsItem("bios", Icons.Default.Memory, "BIOS Files", "gameplay")
    data object Drivers :
        MainSettingsItem("drivers", Icons.Default.DeveloperBoard, "GPU Drivers", "gameplay")

    data object Platforms : MainSettingsItem("platforms", Icons.Default.Gamepad, "Platforms", "library")
    data object Storage : MainSettingsItem("storage", Icons.Default.Storage, "Storage", "library")

    data object GameData : MainSettingsItem("gameData", Icons.Default.Dns, "Game Data", "connections")
    data object Accounts :
        MainSettingsItem("accounts", Icons.Default.ManageAccounts, "Accounts", "connections")
    data object Steam : MainSettingsItem("steam", Icons.Default.CloudQueue, "Steam", "connections")
    data object Social : MainSettingsItem("social", Icons.Default.Group, "Social", "connections")

    data object Permissions :
        MainSettingsItem("permissions", Icons.Default.Security, "Permissions", "system")
    data object DeviceSettings :
        MainSettingsItem("device", Icons.Default.PhoneAndroid, "Android Settings", "system")
    data object About : MainSettingsItem("about", Icons.Default.Info, "About", "system")

    companion object {
        val ALL: List<MainSettingsItem> = listOf(
            Header("launcherHeader", "launcher", "LAUNCHER"),
            Theme, Interface, Controls,
            Header("gameplayHeader", "gameplay", "GAMEPLAY"),
            BuiltinEmulator, RetroAchievements, Bios, Drivers,
            Header("libraryHeader", "library", "LIBRARY"),
            Platforms, Storage,
            Header("connectionsHeader", "connections", "CONNECTIONS"),
            GameData, Accounts, Steam, Social,
            Header("systemHeader", "system", "SYSTEM"),
            Permissions, DeviceSettings, About
        )
    }
}

private val mainSettingsLayout = SettingsLayout<MainSettingsItem, Unit>(
    allItems = MainSettingsItem.ALL,
    isFocusable = { it.isFocusable },
    visibleWhen = { _, _ -> true },
    sectionOf = { it.section }
)

internal fun mainSettingsMaxFocusIndex(): Int =
    mainSettingsLayout.maxFocusIndex(Unit)

internal fun mainSettingsItemAtFocusIndex(index: Int): MainSettingsItem? =
    mainSettingsLayout.itemAtFocusIndex(index, Unit)

@Composable
fun MainSettingsSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val context = LocalContext.current
    val listState = rememberLazyListState()

    val visibleItems = remember { mainSettingsLayout.visibleItems(Unit) }

    fun isFocused(item: MainSettingsItem): Boolean =
        uiState.focusedIndex == mainSettingsLayout.focusIndexOf(item, Unit)

    fun getSubtitle(item: MainSettingsItem): String = when (item) {
        is MainSettingsItem.Header -> ""
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
        MainSettingsItem.Accounts -> {
            val accounts = uiState.accounts
            val active = accounts.activeAccount
            when {
                accounts.accounts.isEmpty() -> "No account paired"
                accounts.accounts.size == 1 -> active?.username ?: "1 account"
                else -> "${active?.username ?: "No active account"} of ${accounts.accounts.size}"
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
        MainSettingsItem.Theme -> "Colors, backdrop, fonts"
        MainSettingsItem.Interface -> "Layout, dimmer, displays"
        MainSettingsItem.Controls -> "Button layout, haptic feedback"
        MainSettingsItem.Platforms -> "${uiState.emulators.platforms.size} platforms"
        MainSettingsItem.BuiltinEmulator -> if (uiState.emulators.builtinLibretroEnabled) "Enabled" else "Disabled"
        MainSettingsItem.Bios -> uiState.bios.summaryText
        MainSettingsItem.Drivers -> uiState.drivers.summary
        MainSettingsItem.Steam -> if (uiState.steam.username != null) {
            uiState.steam.username
        } else {
            "Not signed in"
        }
        MainSettingsItem.Social -> when (uiState.social.authStatus) {
            SocialAuthStatus.CONNECTED -> "Linked as ${uiState.social.displayName ?: uiState.social.username}"
            SocialAuthStatus.CONNECTING -> "Connecting..."
            else -> "Not linked"
        }
        MainSettingsItem.Permissions -> if (uiState.permissions.allGranted) {
            "All granted"
        } else {
            "${uiState.permissions.grantedCount}/${uiState.permissions.totalCount} granted"
        }
        MainSettingsItem.About -> "Version ${uiState.appVersion}"
    }

    fun handleClick(item: MainSettingsItem) {
        when (item) {
            is MainSettingsItem.Header -> Unit
            MainSettingsItem.DeviceSettings -> context.startActivity(Intent(Settings.ACTION_SETTINGS))
            MainSettingsItem.GameData -> viewModel.navigateToSection(SettingsSection.SERVER)
            MainSettingsItem.Accounts -> viewModel.navigateToSection(SettingsSection.ACCOUNTS)
            MainSettingsItem.RetroAchievements -> viewModel.navigateToSection(SettingsSection.RETRO_ACHIEVEMENTS)
            MainSettingsItem.Storage -> viewModel.navigateToSection(SettingsSection.STORAGE)
            MainSettingsItem.Theme -> viewModel.navigateToSection(SettingsSection.THEME)
            MainSettingsItem.Interface -> viewModel.navigateToSection(SettingsSection.INTERFACE)
            MainSettingsItem.Controls -> viewModel.navigateToSection(SettingsSection.CONTROLS)
            MainSettingsItem.Platforms -> viewModel.navigateToSection(SettingsSection.PLATFORMS)
            MainSettingsItem.BuiltinEmulator -> viewModel.navigateToSection(SettingsSection.BUILTIN_EMULATOR)
            MainSettingsItem.Bios -> viewModel.navigateToSection(SettingsSection.BIOS)
            MainSettingsItem.Drivers -> viewModel.navigateToSection(SettingsSection.DRIVERS)
            MainSettingsItem.Steam -> viewModel.navigateToSection(SettingsSection.STEAM_SETTINGS)
            MainSettingsItem.Social -> viewModel.navigateToSection(SettingsSection.SOCIAL)
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
            if (item is MainSettingsItem.Header) {
                if (item.key != "launcherHeader") {
                    Spacer(modifier = Modifier.height(Dimens.spacingSm))
                }
                SectionHeader(item.title)
            } else {
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
}
