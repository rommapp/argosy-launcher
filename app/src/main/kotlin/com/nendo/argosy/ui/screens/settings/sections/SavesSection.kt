package com.nendo.argosy.ui.screens.settings.sections

import androidx.annotation.StringRes
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
import com.nendo.argosy.ui.screens.settings.menu.SettingsLayout
import com.nendo.argosy.ui.screens.settings.search.SearchableSetting
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
    val isFocusable: Boolean get() = this is SearchableSetting

    class Header(key: String, section: String, val titleRes: Int) : SavesItem(key, section)

    class Locked(
        key: String,
        section: String,
        @StringRes val titleRes: Int,
        @StringRes val valueRes: Int,
        @StringRes val subtitleRes: Int
    ) : SavesItem(key, section, { !it.isConnected })

    sealed class Row(
        key: String,
        section: String,
        @StringRes override val labelRes: Int,
        visibleWhen: (SavesLayoutState) -> Boolean = { true }
    ) : SavesItem(key, section, visibleWhen), SearchableSetting

    data object SaveSync : Row(
        "saveSync", "policy", R.string.settings_saves_save_sync_title,
        visibleWhen = { it.isConnected }
    )
    data object SecureSaves : Row(
        "secureSaves", "policy", R.string.settings_saves_secure_saves_title,
        visibleWhen = { it.isConnected && it.saveSyncEnabled }
    )
    data object SaveCacheLimit : Row("saveCacheLimit", "policy", R.string.settings_saves_cache_limit_title)
    data object ManageSaveSync : Row("manageSaveSync", "manage", R.string.settings_saves_manage_sync_title)
    data object SaveCaches : Row("saveCaches", "manage", R.string.settings_saves_caches_title)

    companion object {
        val ALL: List<SavesItem>
            get() = listOf(
                Header("policyHeader", "policy", R.string.settings_saves_section_sync),
                Locked(
                    "saveSyncLocked", "policy",
                    R.string.settings_saves_save_sync_title,
                    R.string.settings_saves_save_sync_locked_value,
                    R.string.settings_saves_save_sync_locked_subtitle
                ),
                SaveSync,
                Locked(
                    "secureSavesLocked", "policy",
                    R.string.settings_saves_secure_saves_title,
                    R.string.settings_saves_secure_saves_locked_value,
                    R.string.settings_saves_secure_saves_locked_subtitle
                ),
                SecureSaves, SaveCacheLimit,
                Header("manageHeader", "manage", R.string.settings_saves_section_manage),
                ManageSaveSync, SaveCaches
            )
    }
}

internal val savesLayout = SettingsLayout<SavesItem, SavesLayoutState>(
    allItems = SavesItem.ALL,
    isFocusable = { it.isFocusable },
    visibleWhen = { item, state -> item.visibleWhen(state) },
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
    savesLayout.itemAtFocusIndex(index, state)

internal fun savesMaxFocusIndex(state: SavesLayoutState): Int =
    savesLayout.maxFocusIndex(state)

internal fun savesSections(state: SavesLayoutState) =
    savesLayout.buildSections(state)

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
    val visibleItems = remember(layoutState) { savesLayout.visibleItems(layoutState) }
    val sections = remember(layoutState, context) { savesLayout.buildSections(layoutState, context) }

    fun isFocused(item: SavesItem): Boolean =
        uiState.focusedIndex == savesLayout.focusIndexOf(item, layoutState)

    fun openFrom(item: SavesItem, enter: () -> Unit) {
        viewModel.setFocusIndex(savesLayout.focusIndexOf(item, layoutState))
        enter()
    }

    SectionPaneLayout(
        items = visibleItems,
        sections = sections,
        focusedIndex = uiState.focusedIndex,
        focusToListIndex = { savesLayout.focusToListIndex(it, layoutState) },
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

            is SavesItem.Locked -> InfoPreference(
                title = stringResource(item.titleRes),
                value = stringResource(item.valueRes),
                subtitle = stringResource(item.subtitleRes),
                isFocused = false
            )

            is SavesItem.SaveSync -> SwitchPreference(
                title = stringResource(item.labelRes),
                subtitle = stringResource(R.string.settings_saves_save_sync_subtitle),
                isEnabled = syncSettings.saveSyncEnabled,
                isFocused = isFocused(item),
                onToggle = { viewModel.toggleSaveSync() }
            )

            is SavesItem.SecureSaves -> SwitchPreference(
                title = stringResource(item.labelRes),
                subtitle = if (syncSettings.secureSaves) {
                    stringResource(R.string.settings_saves_secure_saves_subtitle_on)
                } else {
                    stringResource(R.string.settings_saves_secure_saves_subtitle_off)
                },
                isEnabled = syncSettings.secureSaves,
                isFocused = isFocused(item),
                onToggle = { viewModel.toggleSecureSaves() }
            )

            is SavesItem.SaveCacheLimit -> {
                val limits = SyncSettingsDelegate.SAVE_CACHE_LIMIT_VALUES
                CyclePreference(
                    title = stringResource(item.labelRes),
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

            is SavesItem.ManageSaveSync -> {
                val pending = syncSettings.pendingUploadsCount
                NavigationPreference(
                    icon = Icons.Default.CloudSync,
                    title = stringResource(item.labelRes),
                    subtitle = if (pending > 0) {
                        pluralStringResource(R.plurals.settings_saves_manage_sync_pending, pending, pending)
                    } else {
                        stringResource(R.string.settings_saves_manage_sync_subtitle)
                    },
                    isFocused = isFocused(item),
                    onClick = { viewModel.navigateToSaveSyncScreen() }
                )
            }

            is SavesItem.SaveCaches -> NavigationPreference(
                icon = Icons.Default.Cached,
                title = stringResource(item.labelRes),
                subtitle = stringResource(R.string.settings_saves_caches_subtitle),
                isFocused = isFocused(item),
                onClick = { openFrom(item) { viewModel.navigateToStorageCaches() } }
            )
        }
    }
}
