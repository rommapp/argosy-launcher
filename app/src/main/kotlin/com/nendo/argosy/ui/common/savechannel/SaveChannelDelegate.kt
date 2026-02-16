package com.nendo.argosy.ui.common.savechannel

import com.nendo.argosy.data.emulator.TitleIdDownloadObserver
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.repository.SaveCacheManager
import com.nendo.argosy.data.repository.SaveSyncRepository
import com.nendo.argosy.data.repository.StateCacheManager
import com.nendo.argosy.domain.model.UnifiedSaveEntry
import com.nendo.argosy.domain.model.UnifiedStateEntry
import com.nendo.argosy.domain.usecase.save.GetUnifiedSavesUseCase
import com.nendo.argosy.domain.usecase.save.RestoreCachedSaveUseCase
import com.nendo.argosy.domain.usecase.state.GetUnifiedStatesUseCase
import com.nendo.argosy.domain.usecase.state.RestoreCachedStatesUseCase
import com.nendo.argosy.domain.usecase.state.RestoreStateResult
import com.nendo.argosy.domain.usecase.state.RestoreStateUseCase
import com.nendo.argosy.ui.input.SoundFeedbackManager
import com.nendo.argosy.ui.input.SoundType
import com.nendo.argosy.ui.notification.NotificationManager
import com.nendo.argosy.ui.notification.showError
import com.nendo.argosy.ui.notification.showSuccess
import com.nendo.argosy.ui.screens.gamedetail.components.SaveStatusEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

class SaveChannelDelegate @Inject constructor(
    private val getUnifiedSavesUseCase: GetUnifiedSavesUseCase,
    private val getUnifiedStatesUseCase: GetUnifiedStatesUseCase,
    private val restoreCachedSaveUseCase: RestoreCachedSaveUseCase,
    private val restoreStateUseCase: RestoreStateUseCase,
    private val restoreCachedStatesUseCase: RestoreCachedStatesUseCase,
    private val saveCacheManager: SaveCacheManager,
    private val saveSyncRepository: SaveSyncRepository,
    private val stateCacheManager: StateCacheManager,
    private val gameDao: GameDao,
    private val notificationManager: NotificationManager,
    private val soundManager: SoundFeedbackManager,
    private val titleIdDownloadObserver: TitleIdDownloadObserver
) {
    private val _state = MutableStateFlow(SaveChannelState())
    val state: StateFlow<SaveChannelState> = _state.asStateFlow()

    private var currentGameId: Long = 0
    private var _rawEntries: List<UnifiedSaveEntry> = emptyList()

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
        currentGameId = gameId
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
            val activeSaveTimestamp = gameDao.getActiveSaveTimestamp(gameId)
            val entries = getUnifiedSavesUseCase(gameId)
            _rawEntries = entries

            val saveSlots = buildSaveSlots(entries, activeChannel, isDeviceAware)

            val isRetroArch = emulatorId?.startsWith("retroarch") == true
            val states = if (isRetroArch) {
                getUnifiedStatesUseCase(
                    gameId = gameId,
                    emulatorId = emulatorId,
                    channelName = activeChannel,
                    currentCoreId = currentCoreId,
                    currentCoreVersion = currentCoreVersion
                )
            } else {
                emptyList()
            }

            _state.update {
                it.copy(
                    saveSlots = saveSlots,
                    statesEntries = states,
                    supportsStates = isRetroArch,
                    selectedTab = SaveTab.SAVES,
                    selectedSlotIndex = 0,
                    selectedHistoryIndex = 0,
                    saveFocusColumn = SaveFocusColumn.SLOTS,
                    focusIndex = 0,
                    activeSaveTimestamp = activeSaveTimestamp,
                    isLoading = false
                )
            }
            updateHistoryForFocusedSlot()
        }
    }

    private fun buildSaveSlots(
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

    private fun updateHistoryForFocusedSlot() {
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

        val filtered = _rawEntries
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

    fun dismiss() {
        _rawEntries = emptyList()
        _state.update {
            SaveChannelState(activeChannel = it.activeChannel, savePath = it.savePath)
        }
        soundManager.play(SoundType.CLOSE_MODAL)
    }

    fun switchTab(tab: SaveTab) {
        val state = _state.value
        if (tab == SaveTab.STATES && !state.hasStates) return
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

    fun moveFocus(delta: Int) {
        val state = _state.value
        when (state.selectedTab) {
            SaveTab.SAVES -> {
                when (state.saveFocusColumn) {
                    SaveFocusColumn.SLOTS -> moveSlotSelection(delta)
                    SaveFocusColumn.HISTORY -> moveHistorySelection(delta)
                }
            }
            SaveTab.STATES -> {
                _state.update { s ->
                    val size = s.statesEntries.size
                    if (size == 0) return@update s
                    val maxIndex = (size - 1).coerceAtLeast(0)
                    val newIndex = (s.focusIndex + delta).coerceIn(0, maxIndex)
                    if (newIndex != s.focusIndex) {
                        soundManager.play(SoundType.NAVIGATE)
                    }
                    s.copy(focusIndex = newIndex)
                }
            }
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

    fun setFocusIndex(index: Int) {
        _state.update { state ->
            val size = state.statesEntries.size
            if (size == 0) return@update state
            val maxIndex = (size - 1).coerceAtLeast(0)
            state.copy(focusIndex = index.coerceIn(0, maxIndex))
        }
    }

    fun handleLongPress(index: Int) {
        val state = _state.value
        when (state.selectedTab) {
            SaveTab.SAVES -> {
                when (state.saveFocusColumn) {
                    SaveFocusColumn.SLOTS -> {
                        setSlotIndex(index)
                        showRenameSlotDialog()
                    }
                    SaveFocusColumn.HISTORY -> {
                        setHistoryIndex(index)
                        showCreateChannelFromHistory()
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
            SaveTab.SAVES -> {
                when (state.saveFocusColumn) {
                    SaveFocusColumn.SLOTS -> {
                        val slot = state.focusedSlot ?: return
                        if (slot.isCreateAction) {
                            _state.update {
                                it.copy(
                                    showRenameDialog = true,
                                    renameEntry = null,
                                    renameText = ""
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
            SaveTab.STATES -> {
                val stateEntry = state.focusedStateEntry
                if (stateEntry == null || stateEntry.localCacheId == null) {
                    return
                }
                if (stateEntry.versionStatus == UnifiedStateEntry.VersionStatus.MISMATCH) {
                    _state.update {
                        it.copy(
                            showVersionMismatchDialog = true,
                            versionMismatchState = stateEntry
                        )
                    }
                } else {
                    restoreState(scope, stateEntry, forceRestore = false)
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
            gameDao.updateActiveSaveChannel(currentGameId, channelName)
            gameDao.updateActiveSaveTimestamp(currentGameId, null)
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

            val entry = _rawEntries
                .filter { it.channelName == channelName }
                .maxByOrNull { it.timestamp }

            if (entry != null) {
                when (val result = restoreCachedSaveUseCase(
                    entry, currentGameId, emulatorId, false
                )) {
                    is RestoreCachedSaveUseCase.Result.Restored,
                    is RestoreCachedSaveUseCase.Result.RestoredAndSynced -> {
                        gameDao.updateActiveSaveApplied(currentGameId, true)
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
        return _rawEntries.firstOrNull {
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
            val game = gameDao.getById(currentGameId)

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
            gameDao.updateActiveSaveTimestamp(currentGameId, newTimestamp)
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
                    gameDao.updateActiveSaveApplied(currentGameId, true)
                    val msg = if (targetChannel != null) {
                        "Restored to $targetChannel"
                    } else "Save restored"
                    notificationManager.showSuccess(msg)
                    onRestored()
                }
                is RestoreCachedSaveUseCase.Result.RestoredAndSynced -> {
                    gameDao.updateActiveSaveApplied(currentGameId, true)
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
        if (!entry.canBecomeChannel) return
        _state.update {
            it.copy(
                showRenameDialog = true,
                renameEntry = entry,
                renameText = ""
            )
        }
    }

    fun showRenameSlotDialog() {
        val state = _state.value
        if (state.selectedTab != SaveTab.SAVES) return
        if (state.saveFocusColumn != SaveFocusColumn.SLOTS) return
        val slot = state.focusedSlot ?: return
        if (slot.isCreateAction || slot.channelName == null) return

        val entry = _rawEntries.firstOrNull {
            it.channelName == slot.channelName && it.isLocked
        } ?: return

        _state.update {
            it.copy(
                showRenameDialog = true,
                renameEntry = entry,
                renameText = slot.channelName
            )
        }
    }

    fun dismissRenameDialog() {
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

        if (entry == null) {
            confirmCreateNewSlot(scope, newName)
            return
        }

        if (entry.isChannel) {
            confirmRenameChannel(scope, entry, newName)
        } else {
            confirmCreateChannel(scope, entry, newName)
        }
    }

    private fun confirmCreateNewSlot(scope: CoroutineScope, name: String) {
        scope.launch {
            val autoSave = _rawEntries
                .filter { it.channelName == null }
                .maxByOrNull { it.timestamp }

            val success = if (autoSave?.localCacheId != null) {
                saveCacheManager.copyToChannel(autoSave.localCacheId, name) != null
            } else {
                false
            }

            if (success) {
                refreshEntries()
                _state.update {
                    it.copy(
                        showRenameDialog = false,
                        renameEntry = null,
                        renameText = ""
                    )
                }
                notificationManager.showSuccess("Created save slot '$name'")
            } else {
                notificationManager.showError("Failed to create save slot")
            }
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
                refreshEntries()
                _state.update {
                    it.copy(
                        showRenameDialog = false,
                        renameEntry = null,
                        renameText = ""
                    )
                }
                notificationManager.showSuccess("Created save slot '$newName'")
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
                gameDao.updateActiveSaveChannel(currentGameId, newName)
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

        val entry = _rawEntries.firstOrNull {
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
                gameDao.updateActiveSaveChannel(currentGameId, null)
                gameDao.updateActiveSaveTimestamp(currentGameId, null)
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
            val entries = _rawEntries.filter { it.channelName == channelName }
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
            val entries = _rawEntries.filter { it.channelName == channelName }

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
                            showDeleteLegacyConfirmation()
                        } else {
                            showDeleteConfirmation()
                        }
                    }
                    SaveFocusColumn.HISTORY -> showCreateChannelFromHistory()
                }
            }
            SaveTab.STATES -> showStateDeleteConfirmation()
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
                    SaveFocusColumn.SLOTS -> showRenameSlotDialog()
                    SaveFocusColumn.HISTORY -> {}
                }
            }
            SaveTab.STATES -> showStateReplaceAutoConfirmation()
        }
    }

    fun dismissVersionMismatch() {
        _state.update {
            it.copy(
                showVersionMismatchDialog = false,
                versionMismatchState = null
            )
        }
    }

    fun confirmVersionMismatch(scope: CoroutineScope) {
        val stateEntry = _state.value.versionMismatchState ?: return
        _state.update {
            it.copy(
                showVersionMismatchDialog = false,
                versionMismatchState = null
            )
        }
        restoreState(scope, stateEntry, forceRestore = true)
    }

    private fun restoreState(
        scope: CoroutineScope,
        stateEntry: UnifiedStateEntry,
        forceRestore: Boolean
    ) {
        val state = _state.value
        val cacheId = stateEntry.localCacheId ?: return
        val emulatorId = state.emulatorId ?: return

        scope.launch {
            val game = gameDao.getById(currentGameId)
            val romPath = game?.localPath
            if (romPath == null) {
                notificationManager.showError("Game has no local path")
                return@launch
            }

            val result = restoreStateUseCase(
                cacheId = cacheId,
                emulatorId = emulatorId,
                platformId = game.platformSlug,
                romPath = romPath,
                currentCoreId = state.currentCoreId,
                currentCoreVersion = state.currentCoreVersion,
                forceRestore = forceRestore
            )

            when (result) {
                is RestoreStateResult.Success -> {
                    val slotLabel = if (stateEntry.slotNumber == -1) {
                        "auto state"
                    } else "state slot ${stateEntry.slotNumber}"
                    notificationManager.showSuccess("Restored $slotLabel")
                    _state.update { it.copy(isVisible = false) }
                }
                is RestoreStateResult.VersionMismatch -> {
                    _state.update {
                        it.copy(
                            showVersionMismatchDialog = true,
                            versionMismatchState = stateEntry
                        )
                    }
                }
                is RestoreStateResult.Error -> {
                    notificationManager.showError(result.message)
                }
                is RestoreStateResult.NotFound -> {
                    notificationManager.showError("State not found in cache")
                }
                is RestoreStateResult.NoConfig -> {
                    notificationManager.showError(
                        "No state configuration for this emulator"
                    )
                }
            }
        }
    }

    fun syncServerSaves(scope: CoroutineScope) {
        val state = _state.value
        if (state.isSyncing) return

        scope.launch {
            _state.update { it.copy(isSyncing = true) }

            val entries = getUnifiedSavesUseCase(currentGameId)
            _rawEntries = entries

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
            _rawEntries = updated
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

    private suspend fun refreshEntries() {
        val state = _state.value
        val entries = getUnifiedSavesUseCase(currentGameId)
        _rawEntries = entries
        val saveSlots = buildSaveSlots(
            entries, state.activeChannel, state.isDeviceAwareMode
        )

        _state.update {
            it.copy(saveSlots = saveSlots)
        }
        updateHistoryForFocusedSlot()
    }

    fun showStateDeleteConfirmation() {
        val state = _state.value
        if (state.selectedTab != SaveTab.STATES) return
        val entry = state.focusedStateEntry ?: return
        if (entry.localCacheId == null) return

        _state.update {
            it.copy(
                showStateDeleteConfirmation = true,
                stateDeleteTarget = entry
            )
        }
    }

    fun dismissStateDeleteConfirmation() {
        _state.update {
            it.copy(
                showStateDeleteConfirmation = false,
                stateDeleteTarget = null
            )
        }
    }

    fun confirmDeleteState(scope: CoroutineScope) {
        val state = _state.value
        val entry = state.stateDeleteTarget ?: return
        val cacheId = entry.localCacheId ?: return

        scope.launch {
            stateCacheManager.deleteState(cacheId)
            refreshStates()
            _state.update {
                it.copy(
                    showStateDeleteConfirmation = false,
                    stateDeleteTarget = null,
                    focusIndex = it.focusIndex.coerceAtMost(
                        (it.statesEntries.size - 2).coerceAtLeast(0)
                    )
                )
            }
            val slotLabel = if (entry.slotNumber == -1) {
                "auto state"
            } else "state slot ${entry.slotNumber}"
            notificationManager.showSuccess("Deleted $slotLabel")
        }
    }

    fun showStateReplaceAutoConfirmation() {
        val state = _state.value
        if (state.selectedTab != SaveTab.STATES) return
        val entry = state.focusedStateEntry ?: return
        if (entry.localCacheId == null || entry.slotNumber < 0) return

        _state.update {
            it.copy(
                showStateReplaceAutoConfirmation = true,
                stateReplaceAutoTarget = entry
            )
        }
    }

    fun dismissStateReplaceAutoConfirmation() {
        _state.update {
            it.copy(
                showStateReplaceAutoConfirmation = false,
                stateReplaceAutoTarget = null
            )
        }
    }

    fun confirmReplaceAutoWithSlot(scope: CoroutineScope) {
        val state = _state.value
        val sourceEntry = state.stateReplaceAutoTarget ?: return
        val sourceCacheId = sourceEntry.localCacheId ?: return

        scope.launch {
            val sourceCache = stateCacheManager.getStateById(sourceCacheId)
            if (sourceCache == null) {
                notificationManager.showError("Source state not found")
                return@launch
            }

            val sourceFile = stateCacheManager.getCacheFile(sourceCache)
            if (sourceFile == null) {
                notificationManager.showError("Source state file not found")
                return@launch
            }

            val autoState = state.statesEntries.find { it.slotNumber == -1 }
            if (autoState?.localCacheId != null) {
                stateCacheManager.deleteState(autoState.localCacheId)
            }

            val autoFileName = sourceFile.name.replace(
                Regex("\\.state\\d+$"),
                ".state.auto"
            ).let { name ->
                if (!name.endsWith(".state.auto")) {
                    name.replace(".state", ".state.auto")
                } else name
            }

            val coreDir = stateCacheManager.getCoreDir(
                currentGameId,
                sourceCache.platformSlug,
                sourceCache.channelName,
                sourceCache.coreId
            )
            val autoFile = java.io.File(coreDir, autoFileName)
            sourceFile.copyTo(autoFile, overwrite = true)

            val screenshotFile = stateCacheManager.getScreenshotFile(sourceCache)
            if (screenshotFile != null) {
                val autoScreenshot = java.io.File(
                    coreDir, "$autoFileName.png"
                )
                screenshotFile.copyTo(autoScreenshot, overwrite = true)
            }

            val channelDirName = sourceCache.channelName ?: "default"
            val coreDirName = sourceCache.coreId ?: "unknown"
            val autoCachePath = "${sourceCache.platformSlug}/" +
                "${currentGameId}/$channelDirName/$coreDirName/$autoFileName"
            val autoScreenshotPath = if (screenshotFile != null) {
                "$autoCachePath.png"
            } else null

            val autoEntity = sourceCache.copy(
                id = 0,
                slotNumber = -1,
                cachePath = autoCachePath,
                screenshotPath = autoScreenshotPath,
                cachedAt = java.time.Instant.now()
            )
            stateCacheManager.cacheState(
                gameId = autoEntity.gameId,
                platformSlug = autoEntity.platformSlug,
                emulatorId = autoEntity.emulatorId,
                slotNumber = -1,
                statePath = autoFile.absolutePath,
                coreId = autoEntity.coreId,
                coreVersion = autoEntity.coreVersion,
                channelName = autoEntity.channelName,
                isLocked = autoEntity.isLocked
            )

            refreshStates()
            _state.update {
                it.copy(
                    showStateReplaceAutoConfirmation = false,
                    stateReplaceAutoTarget = null
                )
            }
            notificationManager.showSuccess(
                "Replaced auto state with slot ${sourceEntry.slotNumber}"
            )
        }
    }

    private suspend fun refreshStates() {
        val state = _state.value
        val states = getUnifiedStatesUseCase(
            gameId = currentGameId,
            emulatorId = state.emulatorId,
            channelName = state.activeChannel,
            currentCoreId = state.currentCoreId,
            currentCoreVersion = state.currentCoreVersion
        )
        _state.update { it.copy(statesEntries = states) }
    }
}
