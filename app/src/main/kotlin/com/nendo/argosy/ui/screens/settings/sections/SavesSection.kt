package com.nendo.argosy.ui.screens.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cached
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.nendo.argosy.ui.components.CyclePreference
import com.nendo.argosy.ui.components.InfoPreference
import com.nendo.argosy.ui.components.NavigationPreference
import com.nendo.argosy.ui.components.SwitchPreference
import com.nendo.argosy.ui.screens.settings.ConnectionStatus
import com.nendo.argosy.ui.screens.settings.SettingsUiState
import com.nendo.argosy.ui.screens.settings.SettingsViewModel
import com.nendo.argosy.ui.screens.settings.components.SectionHeader
import com.nendo.argosy.ui.screens.settings.components.SectionPaneLayout
import com.nendo.argosy.ui.screens.settings.delegates.SyncSettingsDelegate
import com.nendo.argosy.ui.screens.settings.menu.DisabledBehavior
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.theme.Dimens

private const val SERVER_REQUIRED_SUBTITLE = "Requires a RomM server"

internal data class SavesLayoutState(
    val isConnected: Boolean,
    val saveSyncEnabled: Boolean
) {
    companion object {
        fun from(state: SettingsUiState) = SavesLayoutState(
            isConnected = state.server.connectionStatus == ConnectionStatus.ONLINE ||
                state.server.connectionStatus == ConnectionStatus.OFFLINE,
            saveSyncEnabled = state.syncSettings.saveSyncEnabled
        )
    }
}

internal sealed class SavesItem(
    val key: String,
    val section: String,
    val visibleWhen: (SavesLayoutState) -> Boolean = { true }
) {
    val isFocusable: Boolean get() = this !is Header

    /**
     * Rows that cannot do anything without a paired RomM server, so they render locked
     * rather than vanishing and leaving an empty screen.
     */
    val isServerGated: Boolean get() = this is SaveSync || this is SecureSaves

    class Header(key: String, section: String, val title: String) : SavesItem(key, section)

    data object SaveSync : SavesItem("saveSync", "policy")
    data object SecureSaves : SavesItem("secureSaves", "policy", visibleWhen = { it.saveSyncEnabled })
    data object SaveCacheLimit : SavesItem("saveCacheLimit", "policy")
    data object ManageSaveSync : SavesItem("manageSaveSync", "manage")
    data object SaveCaches : SavesItem("saveCaches", "manage")

    companion object {
        val ALL: List<SavesItem>
            get() = listOf(
                Header("policyHeader", "policy", "SYNC"),
                SaveSync, SecureSaves, SaveCacheLimit,
                Header("manageHeader", "manage", "MANAGE"),
                ManageSaveSync, SaveCaches
            )
    }
}

internal fun createSavesLayout(state: SavesLayoutState) =
    SettingsLayout<SavesItem, SavesLayoutState>(
        allItems = SavesItem.ALL,
        isFocusable = { it.isFocusable && !(it.isServerGated && !state.isConnected) },
        visibleWhen = { item, s -> item.visibleWhen(s) },
        disabledBehavior = { item ->
            if (item.isServerGated && !state.isConnected) DisabledBehavior.LOCKED
            else DisabledBehavior.HIDDEN
        },
        sectionOf = { it.section },
        sectionTitle = {
            when (it) {
                "policy" -> "SYNC"
                "manage" -> "MANAGE"
                else -> null
            }
        }
    )

internal fun savesItemAtFocusIndex(index: Int, state: SavesLayoutState): SavesItem? =
    createSavesLayout(state).itemAtFocusIndex(index, state)

internal fun savesMaxFocusIndex(state: SavesLayoutState): Int =
    createSavesLayout(state).maxFocusIndex(state)

internal fun savesSections(state: SavesLayoutState) =
    createSavesLayout(state).buildSections(state)

@Composable
fun SavesSection(uiState: SettingsUiState, viewModel: SettingsViewModel) {
    val syncSettings = uiState.syncSettings

    val layoutState = remember(
        uiState.server.connectionStatus,
        syncSettings.saveSyncEnabled
    ) {
        SavesLayoutState.from(uiState)
    }

    val layout = remember(layoutState) { createSavesLayout(layoutState) }
    val visibleItems = remember(layout) { layout.visibleItems(layoutState) }
    val sections = remember(layout) { layout.buildSections(layoutState) }

    fun isFocused(item: SavesItem): Boolean =
        uiState.focusedIndex == layout.focusIndexOf(item, layoutState)

    val isLocked = !layoutState.isConnected

    SectionPaneLayout(
        items = visibleItems,
        sections = sections,
        focusedIndex = uiState.focusedIndex,
        focusToListIndex = { layout.focusToListIndex(it, layoutState) },
        itemKey = { it.key },
        isNavItem = { false },
        isHeader = { it is SavesItem.Header },
        onSectionTap = { viewModel.setFocusIndex(it.focusStartIndex) },
        modifier = Modifier.fillMaxSize().padding(Dimens.spacingMd),
        verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) { item ->
        when (item) {
            is SavesItem.Header -> {
                if (item.key != "policyHeader") {
                    Spacer(modifier = Modifier.height(Dimens.spacingSm))
                }
                SectionHeader(item.title)
            }

            SavesItem.SaveSync -> if (isLocked) {
                InfoPreference(
                    title = "Save Sync",
                    value = "Unavailable",
                    subtitle = SERVER_REQUIRED_SUBTITLE,
                    isFocused = false
                )
            } else {
                SwitchPreference(
                    title = "Save Sync",
                    subtitle = "Sync game saves with server",
                    isEnabled = syncSettings.saveSyncEnabled,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.toggleSaveSync() }
                )
            }

            SavesItem.SecureSaves -> if (isLocked) {
                InfoPreference(
                    title = "Secure Saves",
                    value = "Unavailable",
                    subtitle = SERVER_REQUIRED_SUBTITLE,
                    isFocused = false
                )
            } else {
                SwitchPreference(
                    title = "Secure Saves",
                    subtitle = if (syncSettings.secureSaves) {
                        "Argosy manages and enforces save files"
                    } else {
                        "Argosy attempts to sync saves managed outside the launcher"
                    },
                    isEnabled = syncSettings.secureSaves,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.toggleSecureSaves() }
                )
            }

            SavesItem.SaveCacheLimit -> {
                val limits = SyncSettingsDelegate.SAVE_CACHE_LIMIT_VALUES
                CyclePreference(
                    title = "Local Save Cache",
                    value = "${syncSettings.saveCacheLimit} saves per game",
                    isFocused = isFocused(item),
                    onClick = { viewModel.cycleSaveCacheLimit(1) },
                    onPrev = { viewModel.cycleSaveCacheLimit(-1) },
                    options = remember { limits.map { "$it saves per game" } },
                    onSelect = { viewModel.setSaveCacheLimit(limits[it]) },
                    pickerRequestToken = if (uiState.enumPickerKey == item.key) uiState.enumPickerToken else 0
                )
            }

            SavesItem.ManageSaveSync -> {
                val pending = syncSettings.pendingUploadsCount
                NavigationPreference(
                    icon = Icons.Default.CloudSync,
                    title = "Manage Save Sync",
                    subtitle = if (pending > 0) "$pending pending" else "Review conflicts and sync now",
                    isFocused = isFocused(item),
                    onClick = { viewModel.navigateToSaveSyncScreen() }
                )
            }

            SavesItem.SaveCaches -> NavigationPreference(
                icon = Icons.Default.Cached,
                title = "Save Caches",
                subtitle = "Clear cached saves and detected paths",
                isFocused = isFocused(item),
                onClick = { viewModel.navigateToStorageCachesForSaves() }
            )
        }
    }
}
