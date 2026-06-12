package com.nendo.argosy.ui.common.savechannel

import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.ui.input.SoundFeedbackManager
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.ui.screens.gamedetail.components.SaveStatusEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

class SaveChannelDelegate @Inject constructor(
    private val holder: SaveChannelStateHolder,
    val savesDelegate: SaveChannelSavesDelegate,
    val statesDelegate: SaveChannelStatesDelegate,
    private val saveSyncRepository: SaveSyncRepository,
    private val gameRepository: GameRepository,
    private val soundManager: SoundFeedbackManager
) {
    val state: StateFlow<SaveChannelState> = holder.state.asStateFlow()

    private val _state get() = holder.state

    fun show(
        scope: CoroutineScope,
        gameId: Long,
        activeChannel: String?,
        savePath: String? = null,
        emulatorId: String? = null,
        emulatorPackage: String? = null,
        currentCoreId: String? = null,
        currentCoreVersion: String? = null
    ) {
        holder.currentGameId = gameId
        val deviceId = saveSyncRepository.getDeviceId()
        val isDeviceAware = deviceId != null

        _state.update {
            it.copy(
                isVisible = true,
                isLoading = true,
                selectedSlotIndex = 0,
                selectedHistoryIndex = 0,
                saveFocusColumn = SaveFocusColumn.SLOTS,
                focusIndex = 0,
                activeChannel = activeChannel,
                savePath = savePath,
                emulatorId = emulatorId,
                emulatorPackage = emulatorPackage,
                currentCoreId = currentCoreId,
                currentCoreVersion = currentCoreVersion,
                isDeviceAwareMode = isDeviceAware
            )
        }
        soundManager.play(SoundType.OPEN_MODAL)

        scope.launch {
            val activeSaveTimestamp = gameRepository.getActiveSaveTimestamp(gameId)
            val localEntries = savesDelegate.loadLocalEntries()
            val localSlots = savesDelegate.buildSaveSlots(localEntries, activeChannel, isDeviceAware)

            val states = statesDelegate.loadInitialStates(
                emulatorId = emulatorId,
                channelName = activeChannel,
                currentCoreId = currentCoreId,
                currentCoreVersion = currentCoreVersion
            )
            val stateConfigExists = statesDelegate.supportsStatesFor(emulatorId)

            _state.update {
                it.copy(
                    saveSlots = localSlots,
                    statesEntries = states,
                    supportsStates = stateConfigExists,
                    selectedTab = SaveTab.SAVES,
                    selectedSlotIndex = 0,
                    selectedHistoryIndex = 0,
                    saveFocusColumn = SaveFocusColumn.SLOTS,
                    focusIndex = 0,
                    activeSaveTimestamp = activeSaveTimestamp,
                    isLoading = false,
                    isLoadingServer = true
                )
            }
            savesDelegate.updateHistoryForFocusedSlot()

            val fullEntries = savesDelegate.loadInitialEntries()
            val fullSlots = savesDelegate.buildSaveSlots(fullEntries, activeChannel, isDeviceAware)
            val focusedKey = _state.value.saveSlots.getOrNull(_state.value.selectedSlotIndex)?.slotKey
            val restoredIndex = fullSlots.indexOfFirst { it.slotKey == focusedKey }
                .takeIf { it >= 0 }
                ?: _state.value.selectedSlotIndex.coerceIn(0, (fullSlots.size - 1).coerceAtLeast(0))

            _state.update {
                it.copy(
                    saveSlots = fullSlots,
                    selectedSlotIndex = restoredIndex,
                    isLoadingServer = false
                )
            }
            savesDelegate.updateHistoryForFocusedSlot()
        }
    }

    fun dismiss() {
        holder.rawEntries = emptyList()
        _state.update {
            SaveChannelState(activeChannel = it.activeChannel, savePath = it.savePath)
        }
        soundManager.play(SoundType.CLOSE_MODAL)
    }

    fun switchTab(tab: SaveTab) {
        val state = _state.value
        if (tab == SaveTab.STATES && !state.supportsStates) return
        if (tab == state.selectedTab) return

        _state.update {
            it.copy(
                selectedTab = tab,
                saveFocusColumn = SaveFocusColumn.SLOTS,
                focusIndex = 0
            )
        }
        soundManager.play(SoundType.NAVIGATE)
    }

    fun focusSlotsColumn() = savesDelegate.focusSlotsColumn()

    fun focusHistoryColumn() = savesDelegate.focusHistoryColumn()

    fun setSlotIndex(index: Int) = savesDelegate.setSlotIndex(index)

    fun setHistoryIndex(index: Int) = savesDelegate.setHistoryIndex(index)

    fun setFocusIndex(index: Int) = statesDelegate.setFocusIndex(index)

    fun moveFocus(delta: Int) {
        val state = _state.value
        when (state.selectedTab) {
            SaveTab.SAVES -> {
                when (state.saveFocusColumn) {
                    SaveFocusColumn.SLOTS -> savesDelegate.moveSlotSelection(delta)
                    SaveFocusColumn.HISTORY -> savesDelegate.moveHistorySelection(delta)
                }
            }
            SaveTab.STATES -> statesDelegate.moveStateFocus(delta)
        }
    }

    fun handleLongPress(index: Int) {
        val state = _state.value
        when (state.selectedTab) {
            SaveTab.SAVES -> {
                when (state.saveFocusColumn) {
                    SaveFocusColumn.SLOTS -> {
                        savesDelegate.setSlotIndex(index)
                        savesDelegate.showRenameSlotDialog()
                    }
                    SaveFocusColumn.HISTORY -> {
                        savesDelegate.setHistoryIndex(index)
                        savesDelegate.showCreateChannelFromHistory()
                    }
                }
            }
            SaveTab.STATES -> {}
        }
    }

    fun confirmSelection(
        scope: CoroutineScope,
        emulatorId: String,
        onSaveStatusChanged: (SaveStatusEvent) -> Unit,
        onRestored: () -> Unit = {}
    ) {
        val state = _state.value
        when (state.selectedTab) {
            SaveTab.SAVES -> savesDelegate.confirmSlotOrHistory(
                scope, emulatorId, onSaveStatusChanged, onRestored
            )
            SaveTab.STATES -> statesDelegate.confirmFocusedState(scope)
        }
    }

    fun dismissRestoreConfirmation() = savesDelegate.dismissRestoreConfirmation()

    fun restoreSave(
        scope: CoroutineScope,
        emulatorId: String,
        syncToServer: Boolean,
        onSaveStatusChanged: (SaveStatusEvent) -> Unit,
        onRestored: () -> Unit = {}
    ) = savesDelegate.restoreSave(scope, emulatorId, syncToServer, onSaveStatusChanged, onRestored)

    fun dismissRenameDialog() = savesDelegate.dismissRenameDialog()

    fun updateRenameText(text: String) = savesDelegate.updateRenameText(text)

    fun confirmRename(scope: CoroutineScope) = savesDelegate.confirmRename(scope)

    fun dismissDeleteConfirmation() = savesDelegate.dismissDeleteConfirmation()

    fun confirmDeleteChannel(
        scope: CoroutineScope,
        onSaveStatusChanged: (SaveStatusEvent) -> Unit
    ) = savesDelegate.confirmDeleteChannel(scope, onSaveStatusChanged)

    fun dismissMigrateConfirmation() = savesDelegate.dismissMigrateConfirmation()

    fun confirmMigrateChannel(
        scope: CoroutineScope,
        emulatorId: String,
        onSaveStatusChanged: (SaveStatusEvent) -> Unit,
        onRestored: () -> Unit = {}
    ) = savesDelegate.confirmMigrateChannel(scope, emulatorId, onSaveStatusChanged, onRestored)

    fun dismissDeleteLegacyConfirmation() = savesDelegate.dismissDeleteLegacyConfirmation()

    fun confirmDeleteLegacyChannel(scope: CoroutineScope) =
        savesDelegate.confirmDeleteLegacyChannel(scope)

    fun syncServerSaves(scope: CoroutineScope) = savesDelegate.syncServerSaves(scope)

    fun dismissVersionMismatch() = statesDelegate.dismissVersionMismatch()

    fun confirmVersionMismatch(scope: CoroutineScope) =
        statesDelegate.confirmVersionMismatch(scope)

    fun dismissStateDeleteConfirmation() = statesDelegate.dismissStateDeleteConfirmation()

    fun confirmDeleteState(scope: CoroutineScope) = statesDelegate.confirmDeleteState(scope)

    fun dismissScreenshotPreview() = statesDelegate.dismissScreenshotPreview()

    fun dismissStateReplaceAutoConfirmation() =
        statesDelegate.dismissStateReplaceAutoConfirmation()

    fun confirmReplaceAutoWithSlot(scope: CoroutineScope) =
        statesDelegate.confirmReplaceAutoWithSlot(scope)

    @Suppress("UNUSED_PARAMETER")
    fun secondaryAction(
        scope: CoroutineScope,
        onSaveStatusChanged: (SaveStatusEvent) -> Unit
    ) {
        val state = _state.value
        if (state.showRestoreConfirmation || state.showRenameDialog ||
            state.showDeleteConfirmation || state.showMigrateConfirmation ||
            state.showDeleteLegacyConfirmation ||
            state.showStateDeleteConfirmation ||
            state.showStateReplaceAutoConfirmation) return

        when (state.selectedTab) {
            SaveTab.SAVES -> {
                when (state.saveFocusColumn) {
                    SaveFocusColumn.SLOTS -> {
                        val slot = state.focusedSlot
                        if (slot?.isMigrationCandidate == true) {
                            savesDelegate.showDeleteLegacyConfirmation()
                        } else {
                            savesDelegate.showDeleteConfirmation()
                        }
                    }
                    SaveFocusColumn.HISTORY -> savesDelegate.showCreateChannelFromHistory()
                }
            }
            SaveTab.STATES -> statesDelegate.showStateDeleteConfirmation()
        }
    }

    fun tertiaryAction() {
        val state = _state.value
        if (state.showRestoreConfirmation || state.showRenameDialog ||
            state.showDeleteConfirmation || state.showMigrateConfirmation ||
            state.showDeleteLegacyConfirmation ||
            state.showStateReplaceAutoConfirmation) return

        when (state.selectedTab) {
            SaveTab.SAVES -> {
                when (state.saveFocusColumn) {
                    SaveFocusColumn.SLOTS -> savesDelegate.showRenameSlotDialog()
                    SaveFocusColumn.HISTORY -> {}
                }
            }
            SaveTab.STATES -> {
                val entry = state.focusedStateEntry
                if (entry?.screenshotPath != null) {
                    statesDelegate.showScreenshotPreview()
                }
            }
        }
    }
}
