package com.nendo.argosy.ui.common.savechannel

import com.nendo.argosy.domain.model.UnifiedSaveEntry
import com.nendo.argosy.domain.model.UnifiedStateEntry

enum class SaveTab { SAVES, STATES }

data class SaveChannelState(
    val isVisible: Boolean = false,
    val isLoading: Boolean = false,
    val isSyncing: Boolean = false,
    val selectedTab: SaveTab = SaveTab.SAVES,
    val saveSlots: List<SaveSlotItem> = emptyList(),
    val saveHistory: List<SaveHistoryItem> = emptyList(),
    val saveFocusColumn: SaveFocusColumn = SaveFocusColumn.SLOTS,
    val selectedSlotIndex: Int = 0,
    val selectedHistoryIndex: Int = 0,
    val statesEntries: List<UnifiedStateEntry> = emptyList(),
    val focusIndex: Int = 0,
    val activeChannel: String? = null,
    val activeSaveTimestamp: Long? = null,
    val savePath: String? = null,
    val emulatorId: String? = null,
    val emulatorPackage: String? = null,
    val currentCoreId: String? = null,
    val currentCoreVersion: String? = null,
    val supportsStates: Boolean = false,
    val showRestoreConfirmation: Boolean = false,
    val restoreSelectedEntry: UnifiedSaveEntry? = null,
    val showRenameDialog: Boolean = false,
    val renameEntry: UnifiedSaveEntry? = null,
    val renameText: String = "",
    val showDeleteConfirmation: Boolean = false,
    val deleteSelectedEntry: UnifiedSaveEntry? = null,
    val showVersionMismatchDialog: Boolean = false,
    val versionMismatchState: UnifiedStateEntry? = null,
    val showStateDeleteConfirmation: Boolean = false,
    val stateDeleteTarget: UnifiedStateEntry? = null,
    val showStateReplaceAutoConfirmation: Boolean = false,
    val stateReplaceAutoTarget: UnifiedStateEntry? = null,
    val isDeviceAwareMode: Boolean = false,
    val legacyChannels: List<String> = emptyList(),
    val showMigrateConfirmation: Boolean = false,
    val migrateChannelName: String? = null,
    val showDeleteLegacyConfirmation: Boolean = false,
    val deleteLegacyChannelName: String? = null
) {
    val hasSaveSlots: Boolean get() = saveSlots.any { !it.isCreateAction }
    val hasStates: Boolean get() = supportsStates && statesEntries.isNotEmpty()

    val currentTabSize: Int
        get() = when (selectedTab) {
            SaveTab.SAVES -> when (saveFocusColumn) {
                SaveFocusColumn.SLOTS -> saveSlots.size
                SaveFocusColumn.HISTORY -> saveHistory.size
            }
            SaveTab.STATES -> statesEntries.size
        }

    val focusedSlot: SaveSlotItem?
        get() = saveSlots.getOrNull(selectedSlotIndex)

    val focusedHistoryItem: SaveHistoryItem?
        get() = saveHistory.getOrNull(selectedHistoryIndex)

    val focusedStateEntry: UnifiedStateEntry?
        get() = if (selectedTab == SaveTab.STATES) {
            statesEntries.getOrNull(focusIndex)
        } else null

    val canDeleteSlot: Boolean
        get() = selectedTab == SaveTab.SAVES &&
            saveFocusColumn == SaveFocusColumn.SLOTS &&
            focusedSlot?.let { !it.isCreateAction && it.channelName != null } == true

    val canRenameSlot: Boolean
        get() = selectedTab == SaveTab.SAVES &&
            saveFocusColumn == SaveFocusColumn.SLOTS &&
            focusedSlot?.let { !it.isCreateAction && it.channelName != null } == true

    val canLockAsSlot: Boolean
        get() = selectedTab == SaveTab.SAVES &&
            saveFocusColumn == SaveFocusColumn.HISTORY &&
            focusedHistoryItem != null

    val canDeleteState: Boolean
        get() = selectedTab == SaveTab.STATES &&
            focusedStateEntry?.localCacheId != null

    val canReplaceAutoWithSlot: Boolean
        get() = selectedTab == SaveTab.STATES &&
            focusedStateEntry?.localCacheId != null &&
            focusedStateEntry?.slotNumber?.let { it >= 0 } == true
}
