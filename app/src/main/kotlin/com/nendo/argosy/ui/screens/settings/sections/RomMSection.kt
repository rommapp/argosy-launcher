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
import java.time.ZoneId
import java.time.format.DateTimeFormatter

internal sealed class RomMItem(val key: String, val section: String) {
    val isFocusable: Boolean get() = this !is Header

    class Header(key: String, section: String, val title: String) : RomMItem(key, section)

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
    add(RomMItem.Header("serverHeader", "server", "SERVER"))
    add(RomMItem.RomManager)
    add(RomMItem.Accounts)
    if (isSignedIntoRomM) {
        add(RomMItem.RomMSignOut)
    }

    if (isConnected) {
        add(RomMItem.Header("libraryHeader", "library", "LIBRARY"))
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
        sectionTitle = {
            when (it) {
                "server" -> "SERVER"
                "library" -> "LIBRARY"
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

internal fun accountsSummary(state: SettingsUiState): String {
    val accounts = state.accounts
    val active = accounts.activeAccount
    return when {
        accounts.accounts.isEmpty() -> "No account paired"
        accounts.accounts.size == 1 -> active?.username ?: "1 account"
        else -> "${active?.username ?: "No active account"} of ${accounts.accounts.size}"
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
    val layout = remember(allItems) { createRomMLayout(allItems) }
    val sections = remember(allItems) { layout.buildSections(Unit) }

    fun isFocused(item: RomMItem): Boolean =
        uiState.focusedIndex == layout.focusIndexOf(item, Unit)

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
                SectionHeader(item.title)
            }

            RomMItem.RomManager -> NavigationPreference(
                icon = Icons.Default.Dns,
                title = "Rom Manager",
                subtitle = when (uiState.server.connectionStatus) {
                    ConnectionStatus.CHECKING -> "Checking connection..."
                    ConnectionStatus.ONLINE -> uiState.server.rommUrl.ifBlank { "Connected" }
                    ConnectionStatus.OFFLINE -> "${uiState.server.rommUrl} (offline)"
                    ConnectionStatus.NOT_CONFIGURED -> "Not configured"
                },
                isFocused = isFocused(item),
                onClick = { viewModel.startRommConfig() }
            )

            RomMItem.RomMSignOut -> ActionPreference(
                icon = Icons.AutoMirrored.Filled.Logout,
                title = if (uiState.server.rommSigningOut) "Signing out..." else "Sign Out",
                subtitle = uiState.server.rommUsername.takeIf { it.isNotBlank() }
                    ?.let { "Signed in as $it" }
                    ?: "Forget this RomM account",
                isFocused = isFocused(item),
                isEnabled = !uiState.server.rommSigningOut,
                isDangerous = true,
                onClick = { viewModel.requestRommSignOut() }
            )

            RomMItem.Accounts -> NavigationPreference(
                icon = Icons.Default.ManageAccounts,
                title = "Accounts",
                subtitle = accountsSummary(uiState),
                isFocused = isFocused(item),
                onClick = { viewModel.navigateToSection(SettingsSection.ACCOUNTS) }
            )

            RomMItem.SyncSettings -> NavigationPreference(
                icon = Icons.Default.Tune,
                title = "Sync Settings",
                subtitle = "Filters and media options",
                isFocused = isFocused(item),
                onClick = { viewModel.navigateToSection(SettingsSection.SYNC_SETTINGS) }
            )

            RomMItem.SyncLibrary -> {
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
        }
    }
}
