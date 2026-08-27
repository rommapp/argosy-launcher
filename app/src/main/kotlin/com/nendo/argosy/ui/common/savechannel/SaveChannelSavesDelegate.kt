package com.nendo.argosy.ui.common.savechannel

import android.content.Context
import com.nendo.argosy.R
import com.nendo.argosy.data.emulator.TitleIdDownloadObserver
import com.nendo.argosy.data.repository.ActiveSaveRepository
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.repository.SaveCacheManager
import com.nendo.argosy.data.repository.SaveSyncApiClient
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.data.repository.StateCacheManager
import com.nendo.argosy.data.sync.SyncCoordinator
import com.nendo.argosy.domain.model.UnifiedSaveEntry
import com.nendo.argosy.domain.usecase.save.GetUnifiedSavesUseCase
import com.nendo.argosy.domain.usecase.save.RestoreCachedSaveUseCase
import com.nendo.argosy.domain.usecase.savechannel.ActivateSaveChannelUseCase
import com.nendo.argosy.domain.usecase.savechannel.CopySaveChannelUseCase
import com.nendo.argosy.domain.usecase.savechannel.CreateSaveChannelUseCase
import com.nendo.argosy.domain.usecase.savechannel.DeleteSaveChannelUseCase
import com.nendo.argosy.domain.usecase.savechannel.RenameSaveChannelUseCase
import com.nendo.argosy.domain.usecase.savechannel.RestoreSaveChannelPointUseCase
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.core.notification.showError
import com.nendo.argosy.core.notification.showSuccess
import com.nendo.argosy.ui.common.toNotificationText
import com.nendo.argosy.ui.screens.gamedetail.components.SaveStatusEvent
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

class SaveChannelSavesDelegate @Inject constructor(
    @ApplicationContext private val context: Context,
    private val holder: SaveChannelStateHolder,
    private val getUnifiedSavesUseCase: GetUnifiedSavesUseCase,
    private val restoreCachedSaveUseCase: RestoreCachedSaveUseCase,
    private val activateSaveChannelUseCase: ActivateSaveChannelUseCase,
    private val restoreSaveChannelPointUseCase: RestoreSaveChannelPointUseCase,
    private val createSaveChannelUseCase: CreateSaveChannelUseCase,
    private val copySaveChannelUseCase: CopySaveChannelUseCase,
    private val renameSaveChannelUseCase: RenameSaveChannelUseCase,
    private val deleteSaveChannelUseCase: DeleteSaveChannelUseCase,
    private val saveCacheManager: SaveCacheManager,
    private val saveSyncRepository: SaveSyncRepository,
    private val gameRepository: GameRepository,
    private val activeSaveRepository: ActiveSaveRepository,
    private val notificationManager: NotificationManager,
    private val titleIdDownloadObserver: TitleIdDownloadObserver,
    private val syncCoordinator: SyncCoordinator
) {
    private val _state get() = holder.state
    private val currentGameId get() = holder.currentGameId

    fun buildSaveSlots(
        entries: List<UnifiedSaveEntry>,
        activeChannel: String?,
        isDeviceAwareMode: Boolean = false,
        registeredChannels: List<String> = emptyList()
    ): List<SaveSlotItem> {
        val channelGroups = entries.groupBy { it.channelName } + registeredChannels
            .filterNot { name -> entries.any { it.channelName.equals(name, ignoreCase = true) } }
            .associateWith { emptyList<UnifiedSaveEntry>() }
        val slotItems = mutableListOf<SaveSlotItem>()
        val legacyNames = mutableListOf<String>()

        val archivalSaves = (channelGroups[null] ?: emptyList()).filter { it.isArchival }

        val namedChannels = channelGroups.filterKeys { it != null }
            .toSortedMap(compareBy { it?.lowercase() })

        val autosaveSaves = namedChannels[SaveSyncApiClient.AUTOSAVE_SLOT_NAME] ?: emptyList()
        val effectiveActiveChannel = activeChannel ?: SaveSyncApiClient.AUTOSAVE_SLOT_NAME
        slotItems.add(
            SaveSlotItem(
                channelName = SaveSyncApiClient.AUTOSAVE_SLOT_NAME,
                displayName = context.getString(R.string.ui_save_channel_slot_autosave),
                isActive = effectiveActiveChannel.equals(SaveSyncApiClient.AUTOSAVE_SLOT_NAME, ignoreCase = true),
                saveCount = autosaveSaves.size,
                latestTimestamp = autosaveSaves.maxByOrNull { it.timestamp }?.timestamp?.toEpochMilli()
            )
        )

        namedChannels.filterKeys {
            !it.equals(SaveSyncApiClient.AUTOSAVE_SLOT_NAME, ignoreCase = true)
        }.forEach { (name, saves) ->
            val isUserCreated = saves.any { it.isUserCreatedSlot } ||
                registeredChannels.any { it.equals(name, ignoreCase = true) }

            if (isDeviceAwareMode && !isUserCreated) {
                legacyNames.add(name!!)
                slotItems.add(
                    SaveSlotItem(
                        channelName = name,
                        displayName = name,
                        isActive = false,
                        saveCount = saves.size,
                        latestTimestamp = saves.maxByOrNull {
                            it.timestamp
                        }?.timestamp?.toEpochMilli(),
                        isMigrationCandidate = true
                    )
                )
            } else {
                slotItems.add(
                    SaveSlotItem(
                        channelName = name,
                        displayName = name!!,
                        isActive = name == activeChannel,
                        saveCount = saves.size,
                        latestTimestamp = saves.maxByOrNull {
                            it.timestamp
                        }?.timestamp?.toEpochMilli()
                    )
                )
            }
        }

        if (activeChannel != null && slotItems.none { it.channelName.equals(activeChannel, ignoreCase = true) }) {
            slotItems.add(
                SaveSlotItem(
                    channelName = activeChannel,
                    displayName = activeChannel,
                    isActive = true,
                    saveCount = 0,
                    latestTimestamp = null
                )
            )
        }

        if (archivalSaves.isNotEmpty()) {
            slotItems.add(
                SaveSlotItem(
                    channelName = null,
                    displayName = context.getString(R.string.ui_save_channel_slot_archived),
                    isActive = false,
                    saveCount = archivalSaves.size,
                    latestTimestamp = archivalSaves.maxByOrNull {
                        it.timestamp
                    }?.timestamp?.toEpochMilli(),
                    isArchivedBucket = true
                )
            )
        }

        slotItems.add(
            SaveSlotItem(
                channelName = null,
                displayName = "+ New Slot",
                isActive = false,
                saveCount = 0,
                latestTimestamp = null,
                isCreateAction = true
            )
        )

        _state.update { it.copy(legacyChannels = legacyNames) }

        return slotItems
    }

    fun updateHistoryForFocusedSlot() {
        val state = _state.value
        val slot = state.saveSlots.getOrNull(state.selectedSlotIndex)
        if (slot == null || slot.isCreateAction) {
            _state.update { it.copy(saveHistory = emptyList()) }
            return
        }
        val channelName = slot.channelName
        val activeChannel = state.activeChannel
        val activeSaveTimestamp = state.activeSaveTimestamp
        val activeSaveCacheId = state.activeSaveCacheId
        val isActiveChannel = !slot.isArchivedBucket && channelName == activeChannel

        val filtered = if (slot.isArchivedBucket) {
            holder.rawEntries.filter { it.isArchival }
        } else {
            holder.rawEntries.filter { it.channelName == channelName && !it.isArchival }
        }.sortedByDescending { it.timestamp }

        val history = filtered.mapIndexed { i, entry ->
            val isApplied = isActiveChannel && when {
                activeSaveCacheId != null -> entry.localCacheId == activeSaveCacheId
                activeSaveTimestamp != null -> entry.timestamp.toEpochMilli() == activeSaveTimestamp
                else -> i == 0
            }
            SaveHistoryItem(
                cacheId = entry.localCacheId ?: -1,
                serverSaveId = entry.serverSaveId,
                timestamp = entry.timestamp.toEpochMilli(),
                size = entry.size,
                channelName = entry.channelName,
                isLocal = entry.source != UnifiedSaveEntry.Source.SERVER,
                isSynced = entry.source == UnifiedSaveEntry.Source.BOTH ||
                    entry.source == UnifiedSaveEntry.Source.SERVER,
                isActiveRestorePoint = isApplied,
                isLatest = i == 0,
                isHardcore = entry.isHardcore,
                isRollback = entry.isRollback,
                isArchival = entry.isArchival
            )
        }

        _state.update {
            it.copy(
                saveHistory = history,
                selectedHistoryIndex = 0
            )
        }
    }

    fun focusSlotsColumn() {
        _state.update { it.copy(saveFocusColumn = SaveFocusColumn.SLOTS) }
    }

    fun focusHistoryColumn() {
        val state = _state.value
        if (state.saveHistory.isEmpty()) return
        _state.update {
            it.copy(
                saveFocusColumn = SaveFocusColumn.HISTORY,
                selectedHistoryIndex = if (it.selectedHistoryIndex < 0) 0
                    else it.selectedHistoryIndex
            )
        }
    }

    fun moveSlotSelection(delta: Int) {
        _state.update { state ->
            val max = (state.saveSlots.size - 1).coerceAtLeast(0)
            val newIndex = (state.selectedSlotIndex + delta).coerceIn(0, max)
            if (newIndex != state.selectedSlotIndex) {
            }
            state.copy(selectedSlotIndex = newIndex)
        }
        updateHistoryForFocusedSlot()
    }

    fun moveHistorySelection(delta: Int) {
        _state.update { state ->
            val max = (state.saveHistory.size - 1).coerceAtLeast(0)
            val newIndex = (state.selectedHistoryIndex + delta).coerceIn(0, max)
            if (newIndex != state.selectedHistoryIndex) {
            }
            state.copy(selectedHistoryIndex = newIndex)
        }
    }

    fun setSlotIndex(index: Int) {
        _state.update { state ->
            val max = (state.saveSlots.size - 1).coerceAtLeast(0)
            state.copy(
                selectedSlotIndex = index.coerceIn(0, max),
                saveFocusColumn = SaveFocusColumn.SLOTS
            )
        }
        updateHistoryForFocusedSlot()
    }

    fun setHistoryIndex(index: Int) {
        _state.update { state ->
            val max = (state.saveHistory.size - 1).coerceAtLeast(0)
            state.copy(
                selectedHistoryIndex = index.coerceIn(0, max),
                saveFocusColumn = SaveFocusColumn.HISTORY
            )
        }
    }

    fun confirmSlotOrHistory(
        scope: CoroutineScope,
        emulatorId: String,
        onSaveStatusChanged: (SaveStatusEvent) -> Unit,
        onRestored: () -> Unit
    ) {
        val state = _state.value
        when (state.saveFocusColumn) {
            SaveFocusColumn.SLOTS -> {
                val slot = state.focusedSlot ?: return
                if (slot.isCreateAction) {
                    holder.pendingSaveStatusChanged = onSaveStatusChanged
                    _state.update {
                        it.copy(
                            showRenameDialog = true,
                            renameEntry = null,
                            renameText = "",
                            renameMode = RenameMode.NEW_SLOT
                        )
                    }
                    return
                }
                if (slot.isMigrationCandidate) {
                    _state.update {
                        it.copy(
                            showMigrateConfirmation = true,
                            migrateChannelName = slot.channelName
                        )
                    }
                    return
                }
                if (slot.isArchivedBucket) {
                    focusHistoryColumn()
                    return
                }
                activateSlot(scope, slot, emulatorId, onSaveStatusChanged, onRestored)
            }
            SaveFocusColumn.HISTORY -> {
                val historyItem = state.focusedHistoryItem ?: return
                val entry = findEntryForHistoryItem(historyItem) ?: return
                _state.update {
                    it.copy(
                        showRestoreConfirmation = true,
                        restoreSelectedEntry = entry
                    )
                }
            }
        }
    }

    private fun activateSlot(
        scope: CoroutineScope,
        slot: SaveSlotItem,
        emulatorId: String,
        onSaveStatusChanged: (SaveStatusEvent) -> Unit,
        onRestored: () -> Unit
    ) {
        val state = _state.value
        val channelName = slot.channelName
        val emulatorPackage = state.emulatorPackage

        scope.launch {
            activateSaveChannelUseCase(currentGameId, channelName, state.currentCoreId)
            _state.update {
                it.copy(activeChannel = channelName, activeSaveTimestamp = null, activeSaveCacheId = null)
            }
            onSaveStatusChanged(
                SaveStatusEvent(channelName = channelName, timestamp = null)
            )

            titleIdDownloadObserver.extractTitleIdForGame(currentGameId)

            val candidates = holder.rawEntries.filter { it.channelName == channelName }
            val entry = candidates.maxByOrNull { it.timestamp }
            com.nendo.argosy.util.SaveDebugLogger.logChannelLatestPick(
                gameId = currentGameId,
                channel = channelName,
                pickedCacheId = entry?.localCacheId,
                candidateCount = candidates.size,
                candidateIds = candidates.mapNotNull { it.localCacheId }
            )

            if (entry != null) {
                val entryTimestamp = entry.timestamp.toEpochMilli()
                when (val result = restoreCachedSaveUseCase(
                    entry, currentGameId, emulatorId, false
                )) {
                    is RestoreCachedSaveUseCase.Result.Restored,
                    is RestoreCachedSaveUseCase.Result.RestoredAndSynced -> {
                        activeSaveRepository.setActiveSaveApplied(currentGameId, true)
                        saveSyncRepository.markUserSelectedRestorePoint(currentGameId, emulatorId, channelName)
                        onSaveStatusChanged(
                            SaveStatusEvent(channelName = channelName, timestamp = entryTimestamp)
                        )
                        val label = channelName
                            ?: context.getString(R.string.ui_save_channel_notice_activate_default)
                        notificationManager.showSuccess(
                            NotificationText.Res(R.string.ui_save_channel_notice_using_slot, listOf(label))
                        )
                        _state.update {
                            it.copy(
                                activeSaveTimestamp = entryTimestamp,
                                activeSaveCacheId = entry.localCacheId
                            )
                        }
                        refreshEntries()
                        onRestored()
                    }
                    is RestoreCachedSaveUseCase.Result.Error -> {
                        notificationManager.showError(result.reason.toNotificationText())
                        _state.update { it.copy(isVisible = false) }
                    }
                }
            } else {
                val cleared = restoreCachedSaveUseCase.clearActiveSave(
                    currentGameId, emulatorId
                )
                if (!cleared) {
                    notificationManager.showError(
                        NotificationText.Res(R.string.ui_save_channel_notice_clear_failed)
                    )
                    _state.update { it.copy(isVisible = false) }
                    return@launch
                }
                val label = channelName
                    ?: context.getString(R.string.ui_save_channel_notice_switch_default)
                notificationManager.showSuccess(
                    NotificationText.Res(R.string.ui_save_channel_notice_switched_to, listOf(label))
                )
                _state.update { it.copy(isVisible = false) }
                onRestored()
            }
        }
    }

    private fun findEntryForHistoryItem(item: SaveHistoryItem): UnifiedSaveEntry? {
        return holder.rawEntries.firstOrNull {
            it.channelName == item.channelName &&
                it.timestamp.toEpochMilli() == item.timestamp &&
                it.isArchival == item.isArchival
        }
    }

    fun dismissRestoreConfirmation() {
        _state.update {
            it.copy(
                showRestoreConfirmation = false,
                restoreSelectedEntry = null
            )
        }
    }

    fun restoreSave(
        scope: CoroutineScope,
        emulatorId: String,
        syncToServer: Boolean,
        onSaveStatusChanged: (SaveStatusEvent) -> Unit,
        onRestored: () -> Unit = {}
    ) {
        val state = _state.value
        val entry = state.restoreSelectedEntry
        if (entry == null) {
            _state.update {
                it.copy(isVisible = false, showRestoreConfirmation = false)
            }
            return
        }
        val targetChannel = entry.channelName
        val targetTimestamp = entry.timestamp.toEpochMilli()
        val emulatorPackage = state.emulatorPackage
        val isRestoringLatest = entry.isLatest

        scope.launch {
            com.nendo.argosy.util.SaveDebugLogger.logRestoreEntryPicked(
                gameId = currentGameId,
                channel = targetChannel,
                localCacheId = entry.localCacheId,
                serverSaveId = entry.serverSaveId,
                entryTimestamp = entry.timestamp,
                source = entry.source.name,
                isLatest = entry.isLatest
            )
            restoreSaveChannelPointUseCase(
                gameId = currentGameId,
                channelName = targetChannel,
                isLatest = isRestoringLatest,
                coreId = state.currentCoreId
            )

            _state.update {
                it.copy(
                    showRestoreConfirmation = false,
                    activeChannel = targetChannel,
                    activeSaveTimestamp = targetTimestamp,
                    activeSaveCacheId = entry.localCacheId
                )
            }
            onSaveStatusChanged(
                SaveStatusEvent(channelName = targetChannel, timestamp = targetTimestamp)
            )

            titleIdDownloadObserver.extractTitleIdForGame(currentGameId)

            when (val result = restoreCachedSaveUseCase(
                entry, currentGameId, emulatorId, syncToServer
            )) {
                is RestoreCachedSaveUseCase.Result.Restored -> {
                    activeSaveRepository.setActiveSaveApplied(currentGameId, true)
                    saveSyncRepository.markUserSelectedRestorePoint(currentGameId, emulatorId, targetChannel)
                    val msg = if (targetChannel != null) {
                        NotificationText.Res(
                            R.string.ui_save_channel_notice_restored_to_slot,
                            listOf(targetChannel)
                        )
                    } else {
                        NotificationText.Res(R.string.ui_save_channel_notice_restored)
                    }
                    notificationManager.showSuccess(msg)
                    refreshEntries()
                    onRestored()
                }
                is RestoreCachedSaveUseCase.Result.RestoredAndSynced -> {
                    activeSaveRepository.setActiveSaveApplied(currentGameId, true)
                    saveSyncRepository.markUserSelectedRestorePoint(currentGameId, emulatorId, targetChannel)
                    val msg = if (targetChannel != null) {
                        NotificationText.Res(
                            R.string.ui_save_channel_notice_restored_to_slot_synced,
                            listOf(targetChannel)
                        )
                    } else {
                        NotificationText.Res(R.string.ui_save_channel_notice_restored_synced)
                    }
                    notificationManager.showSuccess(msg)
                    refreshEntries()
                    onRestored()
                }
                is RestoreCachedSaveUseCase.Result.Error -> {
                    notificationManager.showError(result.reason.toNotificationText())
                }
            }
        }
    }

    fun showCreateChannelFromHistory() {
        val state = _state.value
        if (state.selectedTab != SaveTab.SAVES) return
        if (state.saveFocusColumn != SaveFocusColumn.HISTORY) return
        val historyItem = state.focusedHistoryItem ?: return
        val entry = findEntryForHistoryItem(historyItem) ?: return
        _state.update {
            it.copy(
                showRenameDialog = true,
                renameEntry = entry,
                renameText = "",
                renameMode = RenameMode.SAVE_AS
            )
        }
    }

    fun showRenameSlotDialog() {
        val state = _state.value
        if (state.selectedTab != SaveTab.SAVES) return
        if (state.saveFocusColumn != SaveFocusColumn.SLOTS) return
        val slot = state.focusedSlot ?: return
        if (slot.isCreateAction || slot.channelName == null) return

        val entry = holder.rawEntries.firstOrNull {
            it.channelName == slot.channelName && it.isLocked
        } ?: return

        _state.update {
            it.copy(
                showRenameDialog = true,
                renameEntry = entry,
                renameText = slot.channelName,
                renameMode = RenameMode.RENAME
            )
        }
    }

    fun dismissRenameDialog() {
        holder.pendingSaveStatusChanged = null
        _state.update {
            it.copy(
                showRenameDialog = false,
                renameEntry = null,
                renameText = ""
            )
        }
    }

    fun updateRenameText(text: String) {
        _state.update { it.copy(renameText = text) }
    }

    private fun isReservedSlotName(name: String): Boolean =
        SaveSyncApiClient.equalsNormalized(name, SaveSyncApiClient.AUTOSAVE_SLOT_NAME) ||
            SaveSyncApiClient.equalsNormalized(name, SaveSyncApiClient.DEFAULT_SAVE_NAME)

    fun confirmRename(scope: CoroutineScope) {
        val state = _state.value
        val entry = state.renameEntry
        val newName = state.renameText.trim()

        if (newName.isBlank()) {
            notificationManager.showError(
                NotificationText.Res(R.string.ui_save_channel_notice_slot_name_empty)
            )
            return
        }

        if (isReservedSlotName(newName)) {
            notificationManager.showError(
                NotificationText.Res(R.string.ui_save_channel_notice_slot_name_reserved, listOf(newName))
            )
            return
        }

        when (state.renameMode) {
            RenameMode.NEW_SLOT -> {
                scope.launch {
                    if (saveCacheManager.channelExists(currentGameId, newName)) {
                        notificationManager.showError(
                            NotificationText.Res(
                                R.string.ui_save_channel_notice_new_slot_exists,
                                listOf(newName)
                            )
                        )
                        return@launch
                    }
                    confirmCreateNewSlot(scope, newName)
                }
            }
            RenameMode.RENAME -> {
                if (entry == null) return
                confirmRenameChannel(scope, entry, newName)
            }
            RenameMode.SAVE_AS -> {
                if (entry == null) return
                if (newName == entry.channelName) {
                    notificationManager.showError(
                        NotificationText.Res(R.string.ui_save_channel_notice_save_as_same_name)
                    )
                    return
                }
                scope.launch {
                    if (saveCacheManager.channelExists(currentGameId, newName)) {
                        notificationManager.showError(
                            NotificationText.Res(
                                R.string.ui_save_channel_notice_save_as_slot_exists,
                                listOf(newName)
                            )
                        )
                        return@launch
                    }
                    confirmCreateChannel(scope, entry, newName)
                }
            }
        }
    }

    private fun confirmCreateNewSlot(scope: CoroutineScope, name: String) {
        scope.launch {
            createSaveChannelUseCase(currentGameId, name, _state.value.currentCoreId)

            _state.update {
                it.copy(
                    showRenameDialog = false,
                    renameEntry = null,
                    renameText = "",
                    activeChannel = name,
                    activeSaveTimestamp = null,
                    activeSaveCacheId = null
                )
            }
            refreshEntries()
            holder.pendingSaveStatusChanged?.invoke(
                SaveStatusEvent(channelName = name, timestamp = null)
            )
            holder.pendingSaveStatusChanged = null
            notificationManager.showSuccess(
                NotificationText.Res(R.string.ui_save_channel_notice_new_slot_created, listOf(name))
            )
        }
    }

    private fun confirmCreateChannel(
        scope: CoroutineScope,
        entry: UnifiedSaveEntry,
        newName: String
    ) {
        val state = _state.value
        scope.launch {
            val success = copySaveChannelUseCase(
                gameId = currentGameId,
                sourceChannel = entry.channelName,
                targetChannel = newName,
                localCacheId = entry.localCacheId,
                serverSaveId = entry.serverSaveId,
                emulatorId = state.emulatorId
            )

            if (success) {
                refreshEntries()
                _state.update {
                    it.copy(
                        showRenameDialog = false,
                        renameEntry = null,
                        renameText = ""
                    )
                }
                notificationManager.showSuccess(
                    NotificationText.Res(R.string.ui_save_channel_notice_save_as_created, listOf(newName))
                )
                scope.launch { syncCoordinator.processQueue() }
            } else {
                notificationManager.showError(
                    NotificationText.Res(R.string.ui_save_channel_notice_save_as_failed)
                )
            }
        }
    }

    private fun confirmRenameChannel(
        scope: CoroutineScope,
        entry: UnifiedSaveEntry,
        newName: String
    ) {
        val state = _state.value
        val oldName = entry.channelName ?: return

        scope.launch {
            renameSaveChannelUseCase(currentGameId, oldName, newName)

            if (state.activeChannel == oldName) {
                _state.update { it.copy(activeChannel = newName) }
            }

            refreshEntries()
            _state.update {
                it.copy(
                    showRenameDialog = false,
                    renameEntry = null,
                    renameText = ""
                )
            }
            notificationManager.showSuccess(
                NotificationText.Res(R.string.ui_save_channel_notice_renamed, listOf(newName))
            )
        }
    }

    fun showDeleteConfirmation() {
        val state = _state.value
        if (state.selectedTab != SaveTab.SAVES) return
        if (state.saveFocusColumn != SaveFocusColumn.SLOTS) return
        val slot = state.focusedSlot ?: return
        if (slot.isCreateAction || slot.channelName == null) return

        val entry = holder.rawEntries.firstOrNull {
            it.channelName == slot.channelName && it.isLocked
        } ?: return

        _state.update {
            it.copy(
                showDeleteConfirmation = true,
                deleteSelectedEntry = entry
            )
        }
    }

    fun dismissDeleteConfirmation() {
        _state.update {
            it.copy(
                showDeleteConfirmation = false,
                deleteSelectedEntry = null
            )
        }
    }

    /**
     * Delete every copy of a save channel -- local cache entries AND their server saves. Deleting
     * only the local cache lets a server-only (or synced) save survive and re-sync back on refresh.
     */
    fun confirmDeleteChannel(
        scope: CoroutineScope,
        onSaveStatusChanged: (SaveStatusEvent) -> Unit
    ) {
        val state = _state.value
        val entry = state.deleteSelectedEntry ?: return
        val channelName = entry.channelName ?: return

        scope.launch {
            deleteSaveChannelUseCase(currentGameId, channelName)

            if (state.activeChannel == channelName) {
                _state.update {
                    it.copy(activeChannel = null, activeSaveTimestamp = null, activeSaveCacheId = null)
                }
                onSaveStatusChanged(
                    SaveStatusEvent(channelName = null, timestamp = null)
                )
            }

            refreshEntries()
            _state.update {
                it.copy(
                    showDeleteConfirmation = false,
                    deleteSelectedEntry = null,
                    selectedSlotIndex = it.selectedSlotIndex.coerceAtMost(
                        (it.saveSlots.size - 1).coerceAtLeast(0)
                    )
                )
            }
            notificationManager.showSuccess(
                NotificationText.Res(R.string.ui_save_channel_notice_slot_deleted, listOf(channelName))
            )
        }
    }

    fun dismissMigrateConfirmation() {
        _state.update {
            it.copy(
                showMigrateConfirmation = false,
                migrateChannelName = null
            )
        }
    }

    fun confirmMigrateChannel(
        scope: CoroutineScope,
        emulatorId: String,
        onSaveStatusChanged: (SaveStatusEvent) -> Unit,
        onRestored: () -> Unit = {}
    ) {
        val state = _state.value
        val channelName = state.migrateChannelName ?: return

        scope.launch {
            val entries = holder.rawEntries.filter { it.channelName == channelName }
            var migrated = false

            for (entry in entries) {
                if (entry.localCacheId != null) {
                    saveCacheManager.renameSave(entry.localCacheId, channelName)
                    migrated = true
                } else if (entry.serverSaveId != null) {
                    val success = saveSyncRepository.downloadSaveAsChannel(
                        currentGameId,
                        entry.serverSaveId,
                        channelName,
                        state.emulatorId,
                        skipDeviceId = true
                    )
                    if (success) migrated = true
                }
            }

            if (migrated) {
                refreshEntries()

                _state.update {
                    it.copy(
                        showMigrateConfirmation = false,
                        migrateChannelName = null
                    )
                }

                val migratedSlot = _state.value.saveSlots.firstOrNull {
                    it.channelName == channelName && !it.isMigrationCandidate
                }
                if (migratedSlot != null) {
                    activateSlot(
                        scope, migratedSlot, emulatorId,
                        onSaveStatusChanged, onRestored
                    )
                } else {
                    notificationManager.showSuccess(
                        NotificationText.Res(R.string.ui_save_channel_notice_migrated, listOf(channelName))
                    )
                }
            } else {
                notificationManager.showError(
                    NotificationText.Res(R.string.ui_save_channel_notice_migrate_failed)
                )
                _state.update {
                    it.copy(
                        showMigrateConfirmation = false,
                        migrateChannelName = null
                    )
                }
            }
        }
    }

    fun showDeleteLegacyConfirmation() {
        val state = _state.value
        if (state.saveFocusColumn != SaveFocusColumn.SLOTS) return
        val slot = state.focusedSlot ?: return
        if (!slot.isMigrationCandidate) return

        _state.update {
            it.copy(
                showDeleteLegacyConfirmation = true,
                deleteLegacyChannelName = slot.channelName
            )
        }
    }

    fun dismissDeleteLegacyConfirmation() {
        _state.update {
            it.copy(
                showDeleteLegacyConfirmation = false,
                deleteLegacyChannelName = null
            )
        }
    }

    fun confirmDeleteLegacyChannel(scope: CoroutineScope) {
        val state = _state.value
        val channelName = state.deleteLegacyChannelName ?: return

        scope.launch {
            deleteSaveChannelUseCase(currentGameId, channelName)

            refreshEntries()
            _state.update {
                it.copy(
                    showDeleteLegacyConfirmation = false,
                    deleteLegacyChannelName = null,
                    selectedSlotIndex = it.selectedSlotIndex.coerceAtMost(
                        (it.saveSlots.size - 1).coerceAtLeast(0)
                    )
                )
            }
            notificationManager.showSuccess(
                NotificationText.Res(R.string.ui_save_channel_notice_legacy_deleted, listOf(channelName))
            )
        }
    }

    fun syncServerSaves(scope: CoroutineScope) {
        val state = _state.value
        if (state.isSyncing) return

        scope.launch {
            _state.update { it.copy(isSyncing = true) }

            val entries = getUnifiedSavesUseCase(currentGameId, expandHistory = true)
            holder.rawEntries = entries

            val serverEntries = entries.filter {
                it.source == UnifiedSaveEntry.Source.SERVER &&
                    it.serverSaveId != null
            }
            for (entry in serverEntries) {
                saveSyncRepository.downloadAndCacheSave(
                    serverSaveId = entry.serverSaveId!!,
                    gameId = currentGameId,
                    channelName = entry.channelName
                )
            }

            val updated = getUnifiedSavesUseCase(currentGameId, expandHistory = true)
            holder.rawEntries = updated
            val saveSlots = buildSaveSlots(
                updated, state.activeChannel, state.isDeviceAwareMode
            )

            _state.update {
                it.copy(
                    saveSlots = saveSlots,
                    isSyncing = false
                )
            }
            updateHistoryForFocusedSlot()
            notificationManager.showSuccess(
                NotificationText.Res(R.string.ui_save_channel_notice_saves_synced)
            )
        }
    }

    suspend fun refreshEntries() {
        val state = _state.value
        val entries = getUnifiedSavesUseCase(currentGameId, expandHistory = true)
        holder.rawEntries = entries
        val saveSlots = buildSaveSlots(
            entries,
            state.activeChannel,
            state.isDeviceAwareMode,
            activeSaveRepository.registeredChannels(currentGameId)
        )

        _state.update {
            it.copy(saveSlots = saveSlots)
        }
        updateHistoryForFocusedSlot()
    }

    suspend fun loadInitialEntries(): List<UnifiedSaveEntry> {
        val entries = getUnifiedSavesUseCase(currentGameId, expandHistory = true)
        holder.rawEntries = entries
        return entries
    }

    suspend fun loadLocalEntries(): List<UnifiedSaveEntry> {
        val entries = getUnifiedSavesUseCase.localOnly(currentGameId)
        holder.rawEntries = entries
        return entries
    }
}
