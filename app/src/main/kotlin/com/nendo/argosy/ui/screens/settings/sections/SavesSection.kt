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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import com.nendo.argosy.R
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

    class Header(key: String, section: String, val titleRes: Int) : SavesItem(key, section)

    data object SaveSync : SavesItem("saveSync", "policy")
    data object SecureSaves : SavesItem("secureSaves", "policy", visibleWhen = { it.saveSyncEnabled })
    data object SaveCacheLimit : SavesItem("saveCacheLimit", "policy")
    data object ManageSaveSync : SavesItem("manageSaveSync", "manage")
    data object SaveCaches : SavesItem("saveCaches", "manage")

    companion object {
        val ALL: List<SavesItem>
            get() = listOf(
                Header("policyHeader", "policy", R.string.settings_saves_section_sync),
                SaveSync, SecureSaves, SaveCacheLimit,
                Header("manageHeader", "manage", R.string.settings_saves_section_manage),
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
        sectionTitleRes = {
            when (it) {
                "policy" -> R.string.settings_saves_section_sync
                "manage" -> R.string.settings_saves_section_manage
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

    val context = LocalContext.current
    val layout = remember(layoutState) { createSavesLayout(layoutState) }
    val visibleItems = remember(layout) { layout.visibleItems(layoutState) }
    val sections = remember(layout, context) { layout.buildSections(layoutState, context) }

    fun isFocused(item: SavesItem): Boolean =
        uiState.focusedIndex == layout.focusIndexOf(item, layoutState)

    fun openFrom(item: SavesItem, enter: () -> Unit) {
        viewModel.setFocusIndex(layout.focusIndexOf(item, layoutState))
        enter()
    }

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
                SectionHeader(stringResource(item.titleRes))
            }

            SavesItem.SaveSync -> if (isLocked) {
                InfoPreference(
                    title = stringResource(R.string.settings_saves_save_sync_title),
                    value = stringResource(R.string.settings_saves_save_sync_locked_value),
                    subtitle = stringResource(R.string.settings_saves_save_sync_locked_subtitle),
                    isFocused = false
                )
            } else {
                SwitchPreference(
                    title = stringResource(R.string.settings_saves_save_sync_title),
                    subtitle = stringResource(R.string.settings_saves_save_sync_subtitle),
                    isEnabled = syncSettings.saveSyncEnabled,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.toggleSaveSync() }
                )
            }

            SavesItem.SecureSaves -> if (isLocked) {
                InfoPreference(
                    title = stringResource(R.string.settings_saves_secure_saves_title),
                    value = stringResource(R.string.settings_saves_secure_saves_locked_value),
                    subtitle = stringResource(R.string.settings_saves_secure_saves_locked_subtitle),
                    isFocused = false
                )
            } else {
                SwitchPreference(
                    title = stringResource(R.string.settings_saves_secure_saves_title),
                    subtitle = if (syncSettings.secureSaves) {
                        stringResource(R.string.settings_saves_secure_saves_subtitle_on)
                    } else {
                        stringResource(R.string.settings_saves_secure_saves_subtitle_off)
                    },
                    isEnabled = syncSettings.secureSaves,
                    isFocused = isFocused(item),
                    onToggle = { viewModel.toggleSecureSaves() }
                )
            }

            SavesItem.SaveCacheLimit -> {
                val limits = SyncSettingsDelegate.SAVE_CACHE_LIMIT_VALUES
                CyclePreference(
                    title = stringResource(R.string.settings_saves_cache_limit_title),
                    value = pluralStringResource(
                        R.plurals.settings_saves_cache_limit_value,
                        syncSettings.saveCacheLimit,
                        syncSettings.saveCacheLimit
                    ),
                    isFocused = isFocused(item),
                    onClick = { viewModel.cycleSaveCacheLimit(1) },
                    onPrev = { viewModel.cycleSaveCacheLimit(-1) },
                    options = remember(context) {
                        limits.map {
                            context.resources.getQuantityString(
                                R.plurals.settings_saves_cache_limit_value,
                                it,
                                it
                            )
                        }
                    },
                    onSelect = { viewModel.setSaveCacheLimit(limits[it]) },
                    pickerRequestToken = if (uiState.enumPickerKey == item.key) uiState.enumPickerToken else 0
                )
            }

            SavesItem.ManageSaveSync -> {
                val pending = syncSettings.pendingUploadsCount
                NavigationPreference(
                    icon = Icons.Default.CloudSync,
                    title = stringResource(R.string.settings_saves_manage_sync_title),
                    subtitle = if (pending > 0) {
                        pluralStringResource(R.plurals.settings_saves_manage_sync_pending, pending, pending)
                    } else {
                        stringResource(R.string.settings_saves_manage_sync_subtitle)
                    },
                    isFocused = isFocused(item),
                    onClick = { viewModel.navigateToSaveSyncScreen() }
                )
            }

            SavesItem.SaveCaches -> NavigationPreference(
                icon = Icons.Default.Cached,
                title = stringResource(R.string.settings_saves_caches_title),
                subtitle = stringResource(R.string.settings_saves_caches_subtitle),
                isFocused = isFocused(item),
                onClick = { openFrom(item) { viewModel.navigateToStorageCaches() } }
            )
        }
    }
}
