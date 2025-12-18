package com.nendo.argosy.ui.common.savechannel

import com.nendo.argosy.domain.model.UnifiedSaveEntry

enum class SaveTab { SLOTS, TIMELINE }

data class SaveChannelState(
    val isVisible: Boolean = false,
    val isLoading: Boolean = false,
    val selectedTab: SaveTab = SaveTab.TIMELINE,
    val slotsEntries: List<UnifiedSaveEntry> = emptyList(),
    val timelineEntries: List<UnifiedSaveEntry> = emptyList(),
    val focusIndex: Int = 0,
    val activeChannel: String? = null,
    val activeSaveTimestamp: Long? = null,
    val savePath: String? = null,
    val showRestoreConfirmation: Boolean = false,
    val restoreSelectedEntry: UnifiedSaveEntry? = null,
    val showRenameDialog: Boolean = false,
    val renameEntry: UnifiedSaveEntry? = null,
    val renameText: String = "",
    val showDeleteConfirmation: Boolean = false,
    val deleteSelectedEntry: UnifiedSaveEntry? = null,
    val showResetConfirmation: Boolean = false
) {
    val hasSaveSlots: Boolean get() = slotsEntries.isNotEmpty()

    val currentTabEntries: List<UnifiedSaveEntry>
        get() = when (selectedTab) {
            SaveTab.SLOTS -> slotsEntries
            SaveTab.TIMELINE -> timelineEntries
        }

    val focusedEntry: UnifiedSaveEntry?
        get() = currentTabEntries.getOrNull(focusIndex)

    val canCreateChannel: Boolean
        get() = selectedTab == SaveTab.TIMELINE && focusedEntry?.canBecomeChannel == true

    val canDeleteChannel: Boolean
        get() = selectedTab == SaveTab.SLOTS && focusedEntry?.isChannel == true

    val canRenameChannel: Boolean
        get() = selectedTab == SaveTab.SLOTS && focusedEntry?.isChannel == true
}
