package com.nendo.argosy.ui.common.savechannel

import com.nendo.argosy.domain.model.UnifiedSaveEntry
import com.nendo.argosy.domain.model.UnifiedStateEntry
import com.nendo.argosy.ui.components.TextEntryFocus
import com.nendo.argosy.ui.common.savechannel.snapshot.SnapshotViewState

enum class SaveTab { SAVES, STATES }

enum class RenameMode { SAVE_AS, RENAME, NEW_SLOT }

data class SaveChannelState(
    val isVisible: Boolean = false,
    val isLoading: Boolean = false,
    val isLoadingServer: Boolean = false,
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
    val activeSaveCacheId: Long? = null,
    val activeSaveServerId: Long? = null,
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
    val renameMode: RenameMode = RenameMode.SAVE_AS,
    val renameFocus: TextEntryFocus = TextEntryFocus(),
    val showDeleteConfirmation: Boolean = false,
    val deleteTarget: SaveDeleteTarget? = null,
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
    val deleteLegacyChannelName: String? = null,
    val showScreenshotPreview: Boolean = false,
    val screenshotPreviewEntry: UnifiedStateEntry? = null,
    val showSlotPicker: Boolean = false,
    val slotPickerEntry: UnifiedSaveEntry? = null,
    val slotPickerItems: List<SlotPickerItem> = emptyList(),
    val slotPickerIndex: Int = 0,
    val snapshot: SnapshotViewState? = null
) {
    val isSnapshotSavesView: Boolean
        get() = isVisible && snapshot != null && selectedTab == SaveTab.SAVES

    val hasSaveSlots: Boolean get() = saveSlots.any { !it.isCreateAction }
    val hasStates: Boolean get() = supportsStates
    val showsStatesTab: Boolean get() = supportsStates && snapshot == null

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

    val canDeleteArchived: Boolean
        get() = selectedTab == SaveTab.SAVES &&
            saveFocusColumn == SaveFocusColumn.SLOTS &&
            focusedSlot?.isArchivedBucket == true

    val canDeleteVersion: Boolean
        get() = selectedTab == SaveTab.SAVES &&
            saveFocusColumn == SaveFocusColumn.HISTORY &&
            focusedHistoryItem?.isDeletable == true

    val canRenameSlot: Boolean
        get() = selectedTab == SaveTab.SAVES &&
            saveFocusColumn == SaveFocusColumn.SLOTS &&
            focusedSlot?.let { !it.isCreateAction && it.channelName != null } == true

    val canLockAsSlot: Boolean
        get() = selectedTab == SaveTab.SAVES &&
            saveFocusColumn == SaveFocusColumn.HISTORY &&
            focusedHistoryItem != null

    val focusedSlotPickerItem: SlotPickerItem?
        get() = slotPickerItems.getOrNull(slotPickerIndex)

    fun isSlotFocused(index: Int): Boolean =
        saveFocusColumn == SaveFocusColumn.SLOTS && selectedSlotIndex == index

    fun isHistoryFocused(index: Int): Boolean =
        saveFocusColumn == SaveFocusColumn.HISTORY && selectedHistoryIndex == index

    fun isStateFocused(index: Int): Boolean =
        selectedTab == SaveTab.STATES && focusIndex == index

    fun isSlotPickerItemFocused(index: Int): Boolean =
        showSlotPicker && slotPickerIndex == index

    val canDeleteState: Boolean
        get() = selectedTab == SaveTab.STATES &&
            focusedStateEntry?.localCacheId != null

    val canReplaceAutoWithSlot: Boolean
        get() = selectedTab == SaveTab.STATES &&
            focusedStateEntry?.localCacheId != null &&
            focusedStateEntry?.slotNumber?.let { it >= 0 } == true
}
