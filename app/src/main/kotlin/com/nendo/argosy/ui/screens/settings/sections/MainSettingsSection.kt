package com.nendo.argosy.ui.screens.settings.sections

import android.content.Intent
import android.provider.Settings
import androidx.annotation.StringRes
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
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.ui.common.resolve
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
import com.nendo.argosy.util.formatClockDateTime

internal sealed class MainSettingsItem(
    val key: String,
    val icon: ImageVector,
    @StringRes val titleRes: Int,
    val section: String
) {
    val isFocusable: Boolean get() = this !is Header

    class Header(key: String, section: String, @StringRes titleRes: Int) :
        MainSettingsItem(key, Icons.Default.Info, titleRes, section)

    data object Theme :
        MainSettingsItem("theme", Icons.Default.Palette, R.string.settings_main_theme_title, "launcher")
    data object Interface : MainSettingsItem(
        "interface",
        Icons.Default.Dashboard,
        R.string.settings_main_interface_title,
        "launcher"
    )
    data object Navigation : MainSettingsItem(
        "navigation",
        Icons.Default.TouchApp,
        R.string.settings_main_navigation_title,
        "launcher"
    )
    data object Audio :
        MainSettingsItem("audio", Icons.Default.GraphicEq, R.string.settings_main_audio_title, "launcher")
    data object Displays : MainSettingsItem(
        "displays",
        Icons.Default.Devices,
        R.string.settings_main_displays_title,
        "launcher"
    )

    data object BuiltinEmulator : MainSettingsItem(
        "builtin_emulator",
        Icons.Default.Build,
        R.string.settings_main_builtin_emulator_title,
        "gameplay"
    )
    data object Saves :
        MainSettingsItem("saves", Icons.Default.Save, R.string.settings_main_saves_title, "gameplay")
    data object RetroAchievements : MainSettingsItem(
        "retroAchievements",
        Icons.Default.EmojiEvents,
        R.string.settings_main_retroachievements_title,
        "gameplay"
    )
    data object Bios :
        MainSettingsItem("bios", Icons.Default.Memory, R.string.settings_main_bios_title, "gameplay")
    data object Drivers : MainSettingsItem(
        "drivers",
        Icons.Default.DeveloperBoard,
        R.string.settings_main_drivers_title,
        "gameplay"
    )

    data object Platforms : MainSettingsItem(
        "platforms",
        Icons.Default.Gamepad,
        R.string.settings_main_platforms_title,
        "library"
    )
    data object Storage :
        MainSettingsItem("storage", Icons.Default.Storage, R.string.settings_main_storage_title, "library")

    data object RomM :
        MainSettingsItem("romm", Icons.Default.Dns, R.string.settings_main_romm_title, "connections")
    data object Steam : MainSettingsItem(
        "steam",
        Icons.Default.CloudQueue,
        R.string.settings_main_steam_title,
        "connections"
    )
    data object Jellyfin : MainSettingsItem(
        "jellyfin",
        Icons.Default.Movie,
        R.string.settings_main_jellyfin_title,
        "connections"
    )
    data object Social :
        MainSettingsItem("social", Icons.Default.Group, R.string.settings_main_social_title, "connections")

    data object Permissions : MainSettingsItem(
        "permissions",
        Icons.Default.Security,
        R.string.settings_main_permissions_title,
        "system"
    )
    data object DeviceSettings : MainSettingsItem(
        "device",
        Icons.Default.PhoneAndroid,
        R.string.settings_main_device_title,
        "system"
    )
    data object About :
        MainSettingsItem("about", Icons.Default.Info, R.string.settings_main_about_title, "system")

    companion object {
        val ALL: List<MainSettingsItem>
            get() = listOf(
                Header("launcherHeader", "launcher", R.string.settings_main_section_launcher),
                Theme, Interface, Navigation, Audio, Displays,
                Header("gameplayHeader", "gameplay", R.string.settings_main_section_gameplay),
                BuiltinEmulator, Saves, RetroAchievements, Bios, Drivers,
                Header("libraryHeader", "library", R.string.settings_main_section_library),
                Platforms, Storage,
                Header("connectionsHeader", "connections", R.string.settings_main_section_connections),
                RomM, Steam, Jellyfin, Social,
                Header("systemHeader", "system", R.string.settings_main_section_system),
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
        MainSettingsItem.DeviceSettings -> context.getString(R.string.settings_main_device_subtitle)
        MainSettingsItem.RomM -> when (uiState.server.connectionStatus) {
            ConnectionStatus.NOT_CONFIGURED ->
                context.getString(R.string.settings_main_romm_subtitle_unconfigured)
            ConnectionStatus.CHECKING -> context.getString(R.string.settings_main_romm_subtitle_checking)
            ConnectionStatus.OFFLINE -> context.getString(R.string.settings_main_romm_subtitle_offline)
            ConnectionStatus.ONLINE -> {
                uiState.server.lastRommSync?.let { instant ->
                    context.getString(
                        R.string.settings_main_romm_subtitle_last_sync,
                        formatClockDateTime(context, instant.toEpochMilli())
                    )
                } ?: context.getString(R.string.settings_main_romm_subtitle_never_synced)
            }
        }
        MainSettingsItem.Saves -> if (uiState.syncSettings.saveSyncEnabled) {
            context.getString(R.string.settings_main_saves_subtitle_on)
        } else {
            context.getString(R.string.settings_main_saves_subtitle_off)
        }
        MainSettingsItem.RetroAchievements -> if (uiState.retroAchievements.isLoggedIn) {
            context.getString(
                R.string.settings_main_retroachievements_subtitle_logged_in,
                uiState.retroAchievements.username
            )
        } else {
            context.getString(R.string.settings_main_retroachievements_subtitle_logged_out)
        }
        MainSettingsItem.Storage -> if (uiState.storage.downloadedGamesCount > 0) {
            context.resources.getQuantityString(
                R.plurals.settings_main_storage_subtitle_downloaded,
                uiState.storage.downloadedGamesCount,
                uiState.storage.downloadedGamesCount
            )
        } else {
            context.getString(R.string.settings_main_storage_subtitle_empty)
        }
        MainSettingsItem.Theme -> context.getString(R.string.settings_main_theme_subtitle)
        MainSettingsItem.Interface -> context.getString(R.string.settings_main_interface_subtitle)
        MainSettingsItem.Navigation -> context.getString(R.string.settings_main_navigation_subtitle)
        MainSettingsItem.Audio -> context.getString(R.string.settings_main_audio_subtitle)
        MainSettingsItem.Displays -> context.getString(R.string.settings_main_displays_subtitle)
        MainSettingsItem.Platforms -> context.resources.getQuantityString(
            R.plurals.settings_main_platforms_subtitle,
            uiState.emulators.platforms.size,
            uiState.emulators.platforms.size
        )
        MainSettingsItem.BuiltinEmulator -> if (uiState.emulators.builtinLibretroEnabled) {
            context.getString(R.string.settings_main_builtin_emulator_subtitle_on)
        } else {
            context.getString(R.string.settings_main_builtin_emulator_subtitle_off)
        }
        MainSettingsItem.Bios -> uiState.bios.summaryText.resolve(context)
        MainSettingsItem.Drivers -> uiState.drivers.summary.resolve(context)
        MainSettingsItem.Steam -> if (uiState.steam.username != null) {
            uiState.steam.username
        } else {
            context.getString(R.string.settings_main_steam_subtitle_signed_out)
        }
        MainSettingsItem.Jellyfin -> when {
            !uiState.jellyfin.hasServer ->
                context.getString(R.string.settings_main_jellyfin_subtitle_unconfigured)
            uiState.jellyfin.isSignedIn -> uiState.jellyfin.userName.takeIf { it.isNotBlank() }
                ?.let { context.getString(R.string.settings_main_jellyfin_subtitle_signed_in_as, it) }
                ?: context.getString(R.string.settings_main_jellyfin_subtitle_signed_in)
            else -> context.getString(R.string.settings_main_jellyfin_subtitle_signed_out)
        }
        MainSettingsItem.Social -> when (uiState.social.authStatus) {
            SocialAuthStatus.CONNECTED -> context.getString(
                R.string.settings_main_social_subtitle_linked,
                uiState.social.displayName ?: uiState.social.username
            )
            SocialAuthStatus.CONNECTING -> context.getString(R.string.settings_main_social_subtitle_connecting)
            else -> context.getString(R.string.settings_main_social_subtitle_unlinked)
        }
        MainSettingsItem.Permissions -> if (uiState.permissions.allGranted) {
            context.getString(R.string.settings_main_permissions_subtitle_all)
        } else {
            context.getString(
                R.string.settings_main_permissions_subtitle_partial,
                uiState.permissions.grantedCount,
                uiState.permissions.totalCount
            )
        }
        MainSettingsItem.About -> context.getString(R.string.settings_main_about_subtitle, uiState.appVersion)
    }

    fun handleClick(item: MainSettingsItem) {
        if (item !is MainSettingsItem.Header) {
            viewModel.setFocusIndex(mainSettingsLayout.focusIndexOf(item, Unit))
        }
        when (item) {
            is MainSettingsItem.Header -> Unit
            MainSettingsItem.DeviceSettings -> context.startActivity(Intent(Settings.ACTION_SETTINGS))
            MainSettingsItem.RomM -> viewModel.navigateToSection(SettingsSection.ROMM)
            MainSettingsItem.Saves -> viewModel.navigateToSection(SettingsSection.SAVES)
            MainSettingsItem.RetroAchievements -> viewModel.navigateToSection(SettingsSection.RETRO_ACHIEVEMENTS)
            MainSettingsItem.Storage -> viewModel.navigateToSection(SettingsSection.STORAGE)
            MainSettingsItem.Theme -> viewModel.navigateToSection(SettingsSection.THEME)
            MainSettingsItem.Interface -> viewModel.navigateToSection(SettingsSection.INTERFACE)
            MainSettingsItem.Navigation -> viewModel.navigateToSection(SettingsSection.NAVIGATION)
            MainSettingsItem.Audio -> viewModel.navigateToSection(SettingsSection.AUDIO)
            MainSettingsItem.Displays -> viewModel.navigateToSection(SettingsSection.DISPLAYS)
            MainSettingsItem.Platforms -> viewModel.navigateToSection(SettingsSection.PLATFORMS)
            MainSettingsItem.BuiltinEmulator -> viewModel.navigateToSection(SettingsSection.BUILTIN_EMULATOR)
            MainSettingsItem.Bios -> viewModel.navigateToSection(SettingsSection.BIOS)
            MainSettingsItem.Drivers -> viewModel.navigateToSection(SettingsSection.DRIVERS)
            MainSettingsItem.Steam -> viewModel.navigateToSection(SettingsSection.STEAM_SETTINGS)
            MainSettingsItem.Jellyfin -> viewModel.navigateToSection(SettingsSection.JELLYFIN)
            MainSettingsItem.Social -> viewModel.navigateToSection(SettingsSection.SOCIAL)
            MainSettingsItem.Permissions -> viewModel.navigateToSection(SettingsSection.PERMISSIONS)
            MainSettingsItem.About -> viewModel.navigateToSection(SettingsSection.ABOUT)
        }
    }

    fun pickerToken(item: MainSettingsItem): Int =
        if (uiState.enumPickerKey == item.key) uiState.enumPickerToken else 0

    FocusedScroll(
        listState = listState,
        focusedIndex = mainSettingsLayout.focusToListIndex(uiState.focusedIndex, Unit)
    )

    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxSize()
            .padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        items(visibleItems, key = { it.key }) { item ->
            when (item) {
                is MainSettingsItem.Header -> {
                    if (item.key != "launcherHeader") {
                        Spacer(modifier = Modifier.height(Dimens.spacingSm))
                    }
                    SectionHeader(stringResource(item.titleRes))
                }
                else -> NavigationPreference(
                    icon = item.icon,
                    title = stringResource(item.titleRes),
                    subtitle = getSubtitle(item),
                    isFocused = isFocused(item),
                    onClick = { handleClick(item) }
                )
            }
        }
    }
}
