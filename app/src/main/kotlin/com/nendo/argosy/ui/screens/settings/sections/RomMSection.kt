package com.nendo.argosy.ui.screens.settings.sections

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
import com.nendo.argosy.ui.components.ActionPreference
import com.nendo.argosy.ui.components.NavigationPreference
import com.nendo.argosy.ui.screens.settings.ConnectionStatus
import com.nendo.argosy.ui.screens.settings.SettingsSection
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.components.RomMConfigForm
import com.nendo.argosy.ui.screens.settings.components.SectionHeader
import com.nendo.argosy.ui.screens.settings.components.SectionPaneLayout
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.util.formatClockDateTime

internal sealed class RomMItem(val key: String, val section: String) {
    val isFocusable: Boolean get() = this !is Header

    class Header(key: String, section: String, val titleRes: Int) : RomMItem(key, section)

    data object RomManager : RomMItem("romManager", "server")
    data object RomMSignOut : RomMItem("rommSignOut", "server")
    data object Accounts : RomMItem("rommAccounts", "server")
    data object SyncSettings : RomMItem("syncSettings", "library")
    data object SyncLibrary : RomMItem("syncLibrary", "library")
}

internal fun buildRomMItems(
    isConnected: Boolean,
    isSignedIntoRomM: Boolean
): List<RomMItem> = buildList {
    add(RomMItem.Header("serverHeader", "server", R.string.settings_romm_section_server))
    add(RomMItem.RomManager)
    add(RomMItem.Accounts)
    if (isSignedIntoRomM) {
        add(RomMItem.RomMSignOut)
    }

    if (isConnected) {
        add(RomMItem.Header("libraryHeader", "library", R.string.settings_romm_section_library))
        add(RomMItem.SyncSettings)
        add(RomMItem.SyncLibrary)
    }
}

internal fun buildRomMItemsFromState(state: SettingsUiState): List<RomMItem> {
    val isConnected = state.server.connectionStatus == ConnectionStatus.ONLINE ||
        state.server.connectionStatus == ConnectionStatus.OFFLINE
    return buildRomMItems(
        isConnected = isConnected,
        isSignedIntoRomM = state.server.rommUrl.isNotBlank()
    )
}

internal fun createRomMLayout(items: List<RomMItem>) =
    SettingsLayout<RomMItem, Unit>(
        allItems = items,
        isFocusable = { it.isFocusable },
        visibleWhen = { _, _ -> true },
        sectionOf = { it.section },
        sectionTitleRes = {
            when (it) {
                "server" -> R.string.settings_romm_section_server
                "library" -> R.string.settings_romm_section_library
                else -> null
            }
        }
    )

internal fun rommItemAtFocusIndex(index: Int, items: List<RomMItem>): RomMItem? =
    createRomMLayout(items).itemAtFocusIndex(index, Unit)

internal fun rommSections(items: List<RomMItem>) =
    createRomMLayout(items).buildSections(Unit)

internal fun rommMaxFocusIndex(items: List<RomMItem>): Int =
    createRomMLayout(items).maxFocusIndex(Unit)

internal fun rommFocusIndexOf(item: RomMItem, items: List<RomMItem>): Int =
    createRomMLayout(items).focusIndexOf(item, Unit)

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

    val allItems = remember(isConnected, isSignedIntoRomM) {
        buildRomMItems(isConnected = isConnected, isSignedIntoRomM = isSignedIntoRomM)
    }
    val context = LocalContext.current
    val layout = remember(allItems) { createRomMLayout(allItems) }
    val sections = remember(allItems, context) { layout.buildSections(Unit, context) }

    fun isFocused(item: RomMItem): Boolean =
        uiState.focusedIndex == layout.focusIndexOf(item, Unit)

    fun openFrom(item: RomMItem, enter: () -> Unit) {
        viewModel.setFocusIndex(layout.focusIndexOf(item, Unit))
        enter()
    }

    SectionPaneLayout(
        items = allItems,
        sections = sections,
        focusedIndex = uiState.focusedIndex,
        focusToListIndex = { layout.focusToListIndex(it, Unit) },
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

            RomMItem.RomManager -> NavigationPreference(
                icon = Icons.Default.Dns,
                title = stringResource(R.string.settings_romm_server_title),
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

            RomMItem.RomMSignOut -> ActionPreference(
                icon = Icons.AutoMirrored.Filled.Logout,
                title = if (uiState.server.rommSigningOut) {
                    stringResource(R.string.settings_romm_sign_out_title_busy)
                } else {
                    stringResource(R.string.settings_romm_sign_out_title)
                },
                subtitle = uiState.server.rommUsername.takeIf { it.isNotBlank() }
                    ?.let { stringResource(R.string.settings_romm_sign_out_signed_in_as, it) }
                    ?: stringResource(R.string.settings_romm_sign_out_subtitle),
                isFocused = isFocused(item),
                isEnabled = !uiState.server.rommSigningOut,
                isDangerous = true,
                onClick = { viewModel.requestRommSignOut() }
            )

            RomMItem.Accounts -> NavigationPreference(
                icon = Icons.Default.ManageAccounts,
                title = stringResource(R.string.settings_romm_accounts_title),
                subtitle = accountsSummary(context, uiState),
                isFocused = isFocused(item),
                onClick = { openFrom(item) { viewModel.navigateToSection(SettingsSection.ACCOUNTS) } }
            )

            RomMItem.SyncSettings -> NavigationPreference(
                icon = Icons.Default.Tune,
                title = stringResource(R.string.settings_romm_sync_settings_title),
                subtitle = stringResource(R.string.settings_romm_sync_settings_subtitle),
                isFocused = isFocused(item),
                onClick = { openFrom(item) { viewModel.navigateToSection(SettingsSection.SYNC_SETTINGS) } }
            )

            RomMItem.SyncLibrary -> {
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
                    title = stringResource(R.string.settings_romm_sync_library_title),
                    subtitle = lastSyncText,
                    isFocused = isFocused(item),
                    isEnabled = isOnline,
                    onClick = { viewModel.syncRomm() }
                )
            }
        }
    }
}
