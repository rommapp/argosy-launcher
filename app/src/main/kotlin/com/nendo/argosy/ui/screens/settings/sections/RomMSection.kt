package com.nendo.argosy.ui.screens.settings.sections

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ManageAccounts
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
import com.nendo.argosy.data.preferences.NotOnDeviceAction
import com.nendo.argosy.ui.common.labelRes
import com.nendo.argosy.ui.common.subtitleRes
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.components.NavigationPreference
import com.nendo.argosy.ui.components.SegmentedPreference
import com.nendo.argosy.ui.screens.settings.ConnectionStatus
import com.nendo.argosy.ui.screens.settings.SettingsSection
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.components.RomMConfigForm
import com.nendo.argosy.ui.screens.settings.components.SectionHeader
import com.nendo.argosy.ui.screens.settings.components.SectionPaneLayout
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.screens.settings.search.SearchableSetting
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.util.formatClockDateTime

internal data class RomMLayoutState(
    val isConnected: Boolean,
    val isSignedIntoRomM: Boolean,
    val streamingAvailable: Boolean
) {
    companion object {
        fun from(state: SettingsUiState) = RomMLayoutState(
            isConnected = state.server.connectionStatus == ConnectionStatus.ONLINE ||
                state.server.connectionStatus == ConnectionStatus.OFFLINE,
            isSignedIntoRomM = state.server.rommUrl.isNotBlank(),
            streamingAvailable = state.server.streamingAvailable
        )
    }
}

internal sealed class RomMItem(
    val key: String,
    val section: String,
    val visibleWhen: (RomMLayoutState) -> Boolean = { true }
) {
    val isFocusable: Boolean get() = this is SearchableSetting

    class Header(key: String, section: String, val titleRes: Int) : RomMItem(key, section)

    sealed class Row(
        key: String,
        section: String,
        @StringRes override val labelRes: Int,
        visibleWhen: (RomMLayoutState) -> Boolean = { true }
    ) : RomMItem(key, section, visibleWhen), SearchableSetting

    data object RomManager : Row("romManager", "server", R.string.settings_romm_server_title)
    data object Accounts : Row("rommAccounts", "server", R.string.settings_romm_accounts_title)
    data object NotOnDevice : Row(
        "rommNotOnDevice", "server", R.string.stream_setting_not_on_device_title,
        visibleWhen = { it.streamingAvailable }
    )
    data object RomMSignOut : Row(
        "rommSignOut", "server", R.string.settings_romm_sign_out_title,
        visibleWhen = { it.isSignedIntoRomM }
    )
    data object SyncSettings : Row(
        "syncSettings", "library", R.string.settings_romm_sync_settings_title,
        visibleWhen = { it.isConnected }
    )
    data object SyncLibrary : Row(
        "syncLibrary", "library", R.string.settings_romm_sync_library_title,
        visibleWhen = { it.isConnected }
    )

    companion object {
        val ALL: List<RomMItem>
            get() = listOf(
                Header("serverHeader", "server", R.string.settings_romm_section_server),
                RomManager, Accounts, NotOnDevice, RomMSignOut,
                Header("libraryHeader", "library", R.string.settings_romm_section_library),
                SyncSettings, SyncLibrary
            )
    }
}

internal val rommLayout = SettingsLayout<RomMItem, RomMLayoutState>(
    allItems = RomMItem.ALL,
    isFocusable = { it.isFocusable },
    visibleWhen = { item, state -> item.visibleWhen(state) },
    sectionOf = { it.section },
    sectionTitleRes = {
        when (it) {
            "server" -> R.string.settings_romm_section_server
            "library" -> R.string.settings_romm_section_library
            else -> null
        }
    }
)

internal fun rommItemAtFocusIndex(index: Int, state: RomMLayoutState): RomMItem? =
    rommLayout.itemAtFocusIndex(index, state)

internal fun rommSections(state: RomMLayoutState) =
    rommLayout.buildSections(state)

internal fun rommMaxFocusIndex(state: RomMLayoutState): Int =
    rommLayout.maxFocusIndex(state)

internal fun rommFocusIndexOf(item: RomMItem, state: RomMLayoutState): Int =
    rommLayout.focusIndexOf(item, state)

internal fun accountsSummary(context: android.content.Context, state: SettingsUiState): String {
    val accounts = state.accounts
    val active = accounts.activeAccount
    return when {
        accounts.accounts.isEmpty() -> context.getString(R.string.settings_romm_accounts_empty)
        accounts.accounts.size == 1 -> active?.username
            ?: context.getString(R.string.settings_romm_accounts_single)
        else -> context.getString(
            R.string.settings_romm_accounts_multiple,
            active?.username ?: context.getString(R.string.settings_romm_accounts_none_active),
            accounts.accounts.size
        )
    }
}

@Composable
fun RomMSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    if (uiState.server.rommConfiguring) {
        RomMConfigForm(uiState, viewModel)
    } else {
        RomMContent(uiState, viewModel)
    }
}

@Composable
private fun RomMContent(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val isConnected = uiState.server.connectionStatus == ConnectionStatus.ONLINE ||
        uiState.server.connectionStatus == ConnectionStatus.OFFLINE
    val isOnline = uiState.server.connectionStatus == ConnectionStatus.ONLINE
    val isSignedIntoRomM = uiState.server.rommUrl.isNotBlank()
    val streamingAvailable = uiState.server.streamingAvailable

    val layoutState = remember(isConnected, isSignedIntoRomM, streamingAvailable) {
        RomMLayoutState(
            isConnected = isConnected,
            isSignedIntoRomM = isSignedIntoRomM,
            streamingAvailable = streamingAvailable
        )
    }
    val context = LocalContext.current
    val visibleItems = remember(layoutState) { rommLayout.visibleItems(layoutState) }
    val sections = remember(layoutState, context) { rommLayout.buildSections(layoutState, context) }

    fun isFocused(item: RomMItem): Boolean =
        uiState.focusedIndex == rommLayout.focusIndexOf(item, layoutState)

    fun openFrom(item: RomMItem, enter: () -> Unit) {
        viewModel.setFocusIndex(rommLayout.focusIndexOf(item, layoutState))
        enter()
    }

    SectionPaneLayout(
        items = visibleItems,
        sections = sections,
        focusedIndex = uiState.focusedIndex,
        focusToListIndex = { rommLayout.focusToListIndex(it, layoutState) },
        itemKey = { it.key },
        isNavItem = { false },
        isHeader = { it is RomMItem.Header },
        onSectionTap = { viewModel.setFocusIndex(it.focusStartIndex) },
        modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) { item ->
        when (item) {
            is RomMItem.Header -> {
                if (item.key != "serverHeader") {
                    Spacer(modifier = Modifier.height(Dimens.spacingSm))
                }
                SectionHeader(stringResource(item.titleRes))
            }

            is RomMItem.RomManager -> NavigationPreference(
                icon = Icons.Default.Dns,
                title = stringResource(item.labelRes),
                subtitle = when (uiState.server.connectionStatus) {
                    ConnectionStatus.CHECKING ->
                        stringResource(R.string.settings_romm_server_checking)
                    ConnectionStatus.ONLINE -> uiState.server.rommUrl.ifBlank {
                        stringResource(R.string.settings_romm_server_connected)
                    }
                    ConnectionStatus.OFFLINE ->
                        stringResource(R.string.settings_romm_server_offline, uiState.server.rommUrl)
                    ConnectionStatus.NOT_CONFIGURED ->
                        stringResource(R.string.settings_romm_server_unconfigured)
                },
                isFocused = isFocused(item),
                onClick = { viewModel.startRommConfig() }
            )

            is RomMItem.RomMSignOut -> ActionPreference(
                icon = Icons.AutoMirrored.Filled.Logout,
                title = if (uiState.server.rommSigningOut) {
                    stringResource(R.string.settings_romm_sign_out_title_busy)
                } else {
                    stringResource(item.labelRes)
                },
                subtitle = uiState.server.rommUsername.takeIf { it.isNotBlank() }
                    ?.let { stringResource(R.string.settings_romm_sign_out_signed_in_as, it) }
                    ?: stringResource(R.string.settings_romm_sign_out_subtitle),
                isFocused = isFocused(item),
                isEnabled = !uiState.server.rommSigningOut,
                isDangerous = true,
                onClick = { viewModel.requestRommSignOut() }
            )

            is RomMItem.Accounts -> NavigationPreference(
                icon = Icons.Default.ManageAccounts,
                title = stringResource(item.labelRes),
                subtitle = accountsSummary(context, uiState),
                isFocused = isFocused(item),
                onClick = { openFrom(item) { viewModel.navigateToSection(SettingsSection.ACCOUNTS) } }
            )

            is RomMItem.NotOnDevice -> {
                val action = uiState.server.notOnDeviceAction
                SegmentedPreference(
                    title = stringResource(item.labelRes),
                    subtitle = stringResource(action.subtitleRes),
                    options = NotOnDeviceAction.entries.map { stringResource(it.labelRes) },
                    selectedIndex = NotOnDeviceAction.entries.indexOf(action),
                    isFocused = isFocused(item),
                    onSelect = { viewModel.setNotOnDeviceAction(NotOnDeviceAction.entries[it]) }
                )
            }

            is RomMItem.SyncSettings -> NavigationPreference(
                icon = Icons.Default.Tune,
                title = stringResource(item.labelRes),
                subtitle = stringResource(R.string.settings_romm_sync_settings_subtitle),
                isFocused = isFocused(item),
                onClick = { openFrom(item) { viewModel.navigateToSection(SettingsSection.SYNC_SETTINGS) } }
            )

            is RomMItem.SyncLibrary -> {
                val enabledCount = uiState.syncSettings.enabledPlatformCount
                val totalCount = uiState.syncSettings.totalPlatforms
                val platformText = if (totalCount > 0) {
                    stringResource(R.string.settings_romm_sync_platforms, enabledCount, totalCount)
                } else {
                    ""
                }
                val lastSyncText = uiState.server.lastRommSync?.let { instant ->
                    val synced = formatClockDateTime(context, instant.toEpochMilli())
                    if (platformText.isNotEmpty()) {
                        stringResource(R.string.settings_romm_sync_platforms_and_last, platformText, synced)
                    } else {
                        stringResource(R.string.settings_romm_sync_last, synced)
                    }
                } ?: platformText.ifEmpty { stringResource(R.string.settings_romm_sync_never) }
                ActionPreference(
                    icon = Icons.AutoMirrored.Filled.LibraryBooks,
                    title = stringResource(item.labelRes),
                    subtitle = lastSyncText,
                    isFocused = isFocused(item),
                    isEnabled = isOnline,
                    onClick = { viewModel.syncRomm() }
                )
            }
        }
    }
}
