package com.nendo.argosy.ui.common.savechannel

import com.nendo.argosy.data.emulator.TitleIdDownloadObserver
import com.nendo.argosy.data.repository.GameRepository
import com.nendo.argosy.data.repository.SaveCacheManager
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.data.repository.StateCacheManager
import com.nendo.argosy.data.sync.SyncCoordinator
import com.nendo.argosy.domain.model.UnifiedSaveEntry
import com.nendo.argosy.domain.usecase.save.GetUnifiedSavesUseCase
import com.nendo.argosy.domain.usecase.save.RestoreCachedSaveUseCase
import com.nendo.argosy.domain.usecase.state.RestoreCachedStatesUseCase
import com.nendo.argosy.ui.input.SoundFeedbackManager
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.core.notification.showError
import com.nendo.argosy.core.notification.showSuccess
import com.nendo.argosy.ui.screens.gamedetail.components.SaveStatusEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

class SaveChannelSavesDelegate @Inject constructor(
    private val holder: SaveChannelStateHolder,
    private val getUnifiedSavesUseCase: GetUnifiedSavesUseCase,
    private val restoreCachedSaveUseCase: RestoreCachedSaveUseCase,
    private val restoreCachedStatesUseCase: RestoreCachedStatesUseCase,
    private val saveCacheManager: SaveCacheManager,
    private val saveSyncRepository: SaveSyncRepository,
    private val stateCacheManager: StateCacheManager,
    private val gameRepository: GameRepository,
    private val notificationManager: NotificationManager,
    private val soundManager: SoundFeedbackManager,
    private val titleIdDownloadObserver: TitleIdDownloadObserver,
    private val syncCoordinator: SyncCoordinator
) {
    private val _state get() = holder.state
    private val currentGameId get() = holder.currentGameId

    fun buildSaveSlots(
        entries: List<UnifiedSaveEntry>,
        activeChannel: String?,
        isDeviceAwareMode: Boolean = false
    ): List<SaveSlotItem> {
        val channelGroups = entries.groupBy { it.channelName }
        val slotItems = mutableListOf<SaveSlotItem>()
        val legacyNames = mutableListOf<String>()

        val autoSaves = channelGroups[null] ?: emptyList()
        slotItems.add(
            SaveSlotItem(
                channelName = null,
                displayName = "Auto Save",
                isActive = activeChannel == null,
                saveCount = autoSaves.size,
                latestTimestamp = autoSaves.maxByOrNull {
                    it.timestamp
                }?.timestamp?.toEpochMilli()
            )
        )

        val namedChannels = channelGroups.filterKeys { it != null }
            .toSortedMap(compareBy { it?.lowercase() })

        namedChannels.forEach { (name, saves) ->
            val isUserCreated = saves.any { it.isUserCreatedSlot }

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

        if (activeChannel != null && slotItems.none { it.channelName == activeChannel }) {
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
        val isActiveChannel = channelName == activeChannel

        val filtered = holder.rawEntries
            .filter { it.channelName == channelName }
            .sortedByDescending { it.timestamp }

        val history = filtered.mapIndexed { i, entry ->
            val isApplied = isActiveChannel && if (activeSaveTimestamp != null) {
                entry.timestamp.toEpochMilli() == activeSaveTimestamp
            } else {
                i == 0
            }
            SaveHistoryItem(
                cacheId = entry.localCacheId ?: -1,
                timestamp = entry.timestamp.toEpochMilli(),
                size = entry.size,
                channelName = entry.channelName,
                isLocal = entry.source != UnifiedSaveEntry.Source.SERVER,
                isSynced = entry.source == UnifiedSaveEntry.Source.BOTH ||
                    entry.source == UnifiedSaveEntry.Source.SERVER,
                isActiveRestorePoint = isApplied,
                isLatest = i == 0,
                isHardcore = entry.isHardcore,
                isRollback = entry.isRollback
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
        soundManager.play(SoundType.NAVIGATE)
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
        soundManager.play(SoundType.NAVIGATE)
    }

    fun moveSlotSelection(delta: Int) {
        _state.update { state ->
            val max = (state.saveSlots.size - 1).coerceAtLeast(0)
            val newIndex = (state.selectedSlotIndex + delta).coerceIn(0, max)
            if (newIndex != state.selectedSlotIndex) {
                soundManager.play(SoundType.NAVIGATE)
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
                soundManager.play(SoundType.NAVIGATE)
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
            gameRepository.updateActiveSaveChannel(currentGameId, channelName)
            gameRepository.updateActiveSaveTimestamp(currentGameId, null)
            _state.update {
                it.copy(activeChannel = channelName, activeSaveTimestamp = null)
            }
            onSaveStatusChanged(
                SaveStatusEvent(channelName = channelName, timestamp = null)
            )

            titleIdDownloadObserver.extractTitleIdForGame(currentGameId)

            if (emulatorPackage != null && state.supportsStates) {
                restoreCachedStatesUseCase(
                    gameId = currentGameId,
                    channelName = channelName,
                    emulatorPackage = emulatorPackage,
                    coreId = state.currentCoreId
                )
            }

            val entry = holder.rawEntries
                .filter { it.channelName == channelName }
                .maxByOrNull { it.timestamp }

            if (entry != null) {
                val entryTimestamp = entry.timestamp.toEpochMilli()
                when (val result = restoreCachedSaveUseCase(
                    entry, currentGameId, emulatorId, false
                )) {
                    is RestoreCachedSaveUseCase.Result.Restored,
                    is RestoreCachedSaveUseCase.Result.RestoredAndSynced -> {
                        gameRepository.updateActiveSaveApplied(currentGameId, true)
                        gameRepository.updateActiveSaveTimestamp(currentGameId, entryTimestamp)
                        onSaveStatusChanged(
                            SaveStatusEvent(channelName = channelName, timestamp = entryTimestamp)
                        )
                        val label = channelName ?: "Auto Save"
                        notificationManager.showSuccess("Using save slot: $label")
                        _state.update { it.copy(isVisible = false) }
                        onRestored()
                    }
                    is RestoreCachedSaveUseCase.Result.Error -> {
                        notificationManager.showError(result.message)
                        _state.update { it.copy(isVisible = false) }
                    }
                }
            } else {
                val cleared = restoreCachedSaveUseCase.clearActiveSave(
                    currentGameId, emulatorId
                )
                if (!cleared) {
                    notificationManager.showError("Failed to clear existing save")
                    _state.update { it.copy(isVisible = false) }
                    return@launch
                }
                val label = channelName ?: "Auto Save"
                notificationManager.showSuccess("Switched to: $label")
                _state.update { it.copy(isVisible = false) }
                onRestored()
            }
        }
    }

    private fun findEntryForHistoryItem(item: SaveHistoryItem): UnifiedSaveEntry? {
        return holder.rawEntries.firstOrNull {
            it.channelName == item.channelName &&
                it.timestamp.toEpochMilli() == item.timestamp
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
            val game = gameRepository.getById(currentGameId)

            if (emulatorPackage != null && state.supportsStates) {
                if (isRestoringLatest) {
                    restoreCachedStatesUseCase(
                        gameId = currentGameId,
                        channelName = targetChannel,
                        emulatorPackage = emulatorPackage,
                        coreId = state.currentCoreId,
                        skipAutoState = false
                    )
                } else {
                    restoreCachedStatesUseCase(
                        gameId = currentGameId,
                        channelName = targetChannel,
                        emulatorPackage = emulatorPackage,
                        coreId = state.currentCoreId,
                        skipAutoState = true
                    )
                    if (game?.localPath != null) {
                        stateCacheManager.deleteAutoStateFromDisk(
                            emulatorId = emulatorId,
                            romPath = game.localPath,
                            platformSlug = game.platformSlug,
                            emulatorPackage = emulatorPackage,
                            coreId = state.currentCoreId
                        )
                    }
                }
            }

            val newTimestamp = if (isRestoringLatest) null else targetTimestamp
            gameRepository.updateActiveSaveTimestamp(currentGameId, newTimestamp)
            _state.update {
                it.copy(
                    showRestoreConfirmation = false,
                    isVisible = false,
                    activeChannel = targetChannel,
                    activeSaveTimestamp = newTimestamp
                )
            }
            onSaveStatusChanged(
                SaveStatusEvent(channelName = targetChannel, timestamp = newTimestamp)
            )

            titleIdDownloadObserver.extractTitleIdForGame(currentGameId)

            when (val result = restoreCachedSaveUseCase(
                entry, currentGameId, emulatorId, syncToServer
            )) {
                is RestoreCachedSaveUseCase.Result.Restored -> {
                    gameRepository.updateActiveSaveApplied(currentGameId, true)
                    val msg = if (targetChannel != null) {
                        "Restored to $targetChannel"
                    } else "Save restored"
                    notificationManager.showSuccess(msg)
                    onRestored()
                }
                is RestoreCachedSaveUseCase.Result.RestoredAndSynced -> {
                    gameRepository.updateActiveSaveApplied(currentGameId, true)
                    val msg = if (targetChannel != null) {
                        "Restored to $targetChannel and synced"
                    } else "Save restored and synced"
                    notificationManager.showSuccess(msg)
                    onRestored()
                }
                is RestoreCachedSaveUseCase.Result.Error -> {
                    notificationManager.showError(result.message)
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

    fun confirmRename(scope: CoroutineScope) {
        val state = _state.value
        val entry = state.renameEntry
        val newName = state.renameText.trim()

        if (newName.isBlank()) {
            notificationManager.showError("Slot name cannot be empty")
            return
        }

        when (state.renameMode) {
            RenameMode.NEW_SLOT -> {
                scope.launch {
                    if (saveCacheManager.channelExists(currentGameId, newName)) {
                        notificationManager.showError("Slot '$newName' already exists")
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
                    notificationManager.showError("New slot name must differ from the source")
                    return
                }
                scope.launch {
                    if (saveCacheManager.channelExists(currentGameId, newName)) {
                        notificationManager.showError("Slot '$newName' already exists")
                        return@launch
                    }
                    confirmCreateChannel(scope, entry, newName)
                }
            }
        }
    }

    private fun confirmCreateNewSlot(scope: CoroutineScope, name: String) {
        scope.launch {
            val game = gameRepository.getById(currentGameId) ?: return@launch
            val emulatorId = _state.value.emulatorId

            gameRepository.updateActiveSaveChannel(currentGameId, name)
            gameRepository.updateActiveSaveTimestamp(currentGameId, null)

            if (emulatorId != null) {
                restoreCachedSaveUseCase.clearActiveSave(currentGameId, emulatorId)
            }

            _state.update {
                it.copy(
                    showRenameDialog = false,
                    renameEntry = null,
                    renameText = "",
                    activeChannel = name,
                    activeSaveTimestamp = null
                )
            }
            refreshEntries()
            holder.pendingSaveStatusChanged?.invoke(
                SaveStatusEvent(channelName = name, timestamp = null)
            )
            holder.pendingSaveStatusChanged = null
            notificationManager.showSuccess("Created save slot '$name'")
        }
    }

    private fun confirmCreateChannel(
        scope: CoroutineScope,
        entry: UnifiedSaveEntry,
        newName: String
    ) {
        val state = _state.value
        scope.launch {
            val success = if (entry.localCacheId != null) {
                saveCacheManager.copyToChannel(entry.localCacheId, newName) != null
            } else if (entry.serverSaveId != null) {
                saveSyncRepository.downloadSaveAsChannel(
                    currentGameId,
                    entry.serverSaveId,
                    newName,
                    state.emulatorId
                )
            } else {
                false
            }

            if (success) {
                stateCacheManager.duplicateStatesForChannel(
                    gameId = currentGameId,
                    sourceChannel = entry.channelName,
                    targetChannel = newName
                )

                refreshEntries()
                _state.update {
                    it.copy(
                        showRenameDialog = false,
                        renameEntry = null,
                        renameText = ""
                    )
                }
                notificationManager.showSuccess("Created save slot '$newName'")
                scope.launch { syncCoordinator.processQueue() }
            } else {
                notificationManager.showError("Failed to create save slot")
            }
        }
    }

    private fun confirmRenameChannel(
        scope: CoroutineScope,
        entry: UnifiedSaveEntry,
        newName: String
    ) {
        val state = _state.value
        val cacheId = entry.localCacheId ?: return

        scope.launch {
            saveCacheManager.renameSave(cacheId, newName)

            if (state.activeChannel == entry.channelName) {
                gameRepository.updateActiveSaveChannel(currentGameId, newName)
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
            notificationManager.showSuccess("Renamed to '$newName'")
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

    fun confirmDeleteChannel(
        scope: CoroutineScope,
        onSaveStatusChanged: (SaveStatusEvent) -> Unit
    ) {
        val state = _state.value
        val entry = state.deleteSelectedEntry ?: return
        val channelName = entry.channelName ?: return

        scope.launch {
            entry.localCacheId?.let { saveCacheManager.deleteSave(it) }

            if (state.activeChannel == channelName) {
                gameRepository.updateActiveSaveChannel(currentGameId, null)
                gameRepository.updateActiveSaveTimestamp(currentGameId, null)
                _state.update {
                    it.copy(activeChannel = null, activeSaveTimestamp = null)
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
            notificationManager.showSuccess("Deleted save slot '$channelName'")
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
                        "Migrated '$channelName'"
                    )
                }
            } else {
                notificationManager.showError("Failed to migrate save")
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
            val entries = holder.rawEntries.filter { it.channelName == channelName }

            val serverIds = entries.mapNotNull { it.serverSaveId }
            if (serverIds.isNotEmpty()) {
                saveSyncRepository.deleteServerSaves(serverIds)
            }
            for (entry in entries) {
                entry.localCacheId?.let { saveCacheManager.deleteSave(it) }
            }

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
            notificationManager.showSuccess("Deleted '$channelName'")
        }
    }

    fun syncServerSaves(scope: CoroutineScope) {
        val state = _state.value
        if (state.isSyncing) return

        scope.launch {
            _state.update { it.copy(isSyncing = true) }

            val entries = getUnifiedSavesUseCase(currentGameId)
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

            val updated = getUnifiedSavesUseCase(currentGameId)
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
            notificationManager.showSuccess("Saves synced from server")
        }
    }

    suspend fun refreshEntries() {
        val state = _state.value
        val entries = getUnifiedSavesUseCase(currentGameId)
        holder.rawEntries = entries
        val saveSlots = buildSaveSlots(
            entries, state.activeChannel, state.isDeviceAwareMode
        )

        _state.update {
            it.copy(saveSlots = saveSlots)
        }
        updateHistoryForFocusedSlot()
    }

    suspend fun loadInitialEntries(): List<UnifiedSaveEntry> {
        val entries = getUnifiedSavesUseCase(currentGameId)
        holder.rawEntries = entries
        return entries
    }
}
