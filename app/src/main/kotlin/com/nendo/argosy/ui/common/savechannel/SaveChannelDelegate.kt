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
        _state.update {
            it.copy(
                isVisible = true,
                isLoading = true,
                focusIndex = 0,
                activeChannel = activeChannel,
                savePath = savePath,
                emulatorId = emulatorId,
                emulatorPackage = emulatorPackage,
                currentCoreId = currentCoreId,
                currentCoreVersion = currentCoreVersion
            )
        }
        soundManager.play(SoundType.OPEN_MODAL)

        scope.launch {
            val activeSaveTimestamp = gameDao.getActiveSaveTimestamp(gameId)
            val entries = getUnifiedSavesUseCase(gameId)
            val slots = entries.filter { it.isLocked }.sortedBy { it.channelName?.lowercase() }
            val timeline = buildTimeline(entries, activeChannel, activeSaveTimestamp)

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

            val initialTab = determineInitialTab(slots.isNotEmpty(), activeChannel)

            _state.update {
                it.copy(
                    slotsEntries = slots,
                    timelineEntries = timeline,
                    statesEntries = states,
                    supportsStates = isRetroArch,
                    selectedTab = initialTab,
                    focusIndex = 0,
                    activeSaveTimestamp = activeSaveTimestamp,
                    isLoading = false
                )
            }
        }
    }

    private fun buildTimeline(
        entries: List<UnifiedSaveEntry>,
        activeChannel: String?,
        activeSaveTimestamp: Long?
    ): List<UnifiedSaveEntry> {
        if (entries.isEmpty()) return emptyList()

        val sorted = entries.sortedByDescending { it.timestamp }

        val activeEntry = if (activeSaveTimestamp != null) {
            sorted.firstOrNull { it.timestamp.toEpochMilli() == activeSaveTimestamp }
        } else if (activeChannel != null) {
            sorted.firstOrNull { it.channelName == activeChannel }
        } else {
            sorted.firstOrNull { it.channelName == null }
        }

        return sorted.mapIndexed { index, entry ->
            entry.copy(
                isLatest = index == 0,
                isActive = entry === activeEntry
            )
        }
    }

    private fun determineInitialTab(hasSlots: Boolean, activeChannel: String?): SaveTab {
        if (!hasSlots) return SaveTab.TIMELINE
        if (activeChannel == null) return SaveTab.TIMELINE
        return SaveTab.SLOTS
    }

    fun dismiss() {
        _state.update {
            SaveChannelState(activeChannel = it.activeChannel, savePath = it.savePath)
        }
        soundManager.play(SoundType.CLOSE_MODAL)
    }

    fun switchTab(tab: SaveTab) {
        val state = _state.value
        if (tab == SaveTab.SLOTS && !state.hasSaveSlots) return
        if (tab == SaveTab.STATES && !state.hasStates) return
        if (tab == state.selectedTab) return

        var newFocusIndex = 0

        if (tab == SaveTab.TIMELINE) {
            val newEntries = state.timelineEntries
            if (state.activeChannel == null && newEntries.isNotEmpty() && newEntries.first().isLatest) {
                newFocusIndex = if (newEntries.size > 1) 1 else 0
            }
        }

        _state.update {
            it.copy(
                selectedTab = tab,
                focusIndex = newFocusIndex
            )
        }
        soundManager.play(SoundType.NAVIGATE)
    }

    fun moveFocus(delta: Int) {
        _state.update { state ->
            val size = state.currentTabSize
            if (size == 0) return@update state

            var newIndex = state.focusIndex + delta

            val maxIndex = (size - 1).coerceAtLeast(0)
            newIndex = newIndex.coerceIn(0, maxIndex)

            if (newIndex != state.focusIndex) {
                soundManager.play(SoundType.NAVIGATE)
            }
            state.copy(focusIndex = newIndex)
        }
    }

    fun setFocusIndex(index: Int) {
        _state.update { state ->
            val size = state.currentTabSize
            if (size == 0) return@update state

            val maxIndex = (size - 1).coerceAtLeast(0)
            val newIndex = index.coerceIn(0, maxIndex)

            state.copy(focusIndex = newIndex)
        }
    }

    fun handleLongPress(index: Int) {
        setFocusIndex(index)
        val state = _state.value
        when (state.selectedTab) {
            SaveTab.TIMELINE -> showCreateChannelDialog()
            SaveTab.SLOTS -> showRenameChannelDialog()
            SaveTab.STATES -> { /* No long-press action for states */ }
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
            SaveTab.SLOTS -> {
                val entry = state.focusedEntry
                if (entry == null) {
                    _state.update { it.copy(isVisible = false) }
                    return
                }
                val emulatorPackage = state.emulatorPackage
                scope.launch {
                    val channelName = entry.channelName
                    if (channelName == null) {
                        notificationManager.showError("Invalid save slot")
                        _state.update { it.copy(isVisible = false) }
                        return@launch
                    }
                    gameDao.updateActiveSaveChannel(currentGameId, channelName)
                    gameDao.updateActiveSaveTimestamp(currentGameId, null)
                    _state.update { it.copy(activeChannel = channelName, activeSaveTimestamp = null) }
                    onSaveStatusChanged(SaveStatusEvent(channelName = channelName, timestamp = null))

                    titleIdDownloadObserver.extractTitleIdForGame(currentGameId)

                    if (emulatorPackage != null && state.supportsStates) {
                        val stateResult = restoreCachedStatesUseCase(
                            gameId = currentGameId,
                            channelName = channelName,
                            emulatorPackage = emulatorPackage,
                            coreId = state.currentCoreId
                        )
                        android.util.Log.d("SaveChannelDelegate", "State restore result: $stateResult")
                    }

                    when (val result = restoreCachedSaveUseCase(entry, currentGameId, emulatorId, false)) {
                        is RestoreCachedSaveUseCase.Result.Restored,
                        is RestoreCachedSaveUseCase.Result.RestoredAndSynced -> {
                            notificationManager.showSuccess("Using save slot: $channelName")
                            _state.update { it.copy(isVisible = false) }
                            onRestored()
                        }
                        is RestoreCachedSaveUseCase.Result.Error -> {
                            notificationManager.showError(result.message)
                            _state.update { it.copy(isVisible = false) }
                        }
                    }
                }
            }
            SaveTab.TIMELINE -> {
                val entry = state.focusedEntry
                if (entry == null) {
                    _state.update { it.copy(isVisible = false) }
                    return
                }
                _state.update {
                    it.copy(
                        showRestoreConfirmation = true,
                        restoreSelectedEntry = entry
                    )
                }
            }
            SaveTab.STATES -> {
                val stateEntry = state.focusedStateEntry
                if (stateEntry == null || stateEntry.localCacheId == null) {
                    _state.update { it.copy(isVisible = false) }
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
            _state.update { it.copy(isVisible = false, showRestoreConfirmation = false) }
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
                    val stateResult = restoreCachedStatesUseCase(
                        gameId = currentGameId,
                        channelName = targetChannel,
                        emulatorPackage = emulatorPackage,
                        coreId = state.currentCoreId,
                        skipAutoState = false
                    )
                    android.util.Log.d("SaveChannelDelegate", "Timeline state restore (latest): $stateResult")
                } else {
                    val stateResult = restoreCachedStatesUseCase(
                        gameId = currentGameId,
                        channelName = targetChannel,
                        emulatorPackage = emulatorPackage,
                        coreId = state.currentCoreId,
                        skipAutoState = true
                    )
                    android.util.Log.d("SaveChannelDelegate", "Timeline state restore (non-latest, skipped auto): $stateResult")

                    if (game?.localPath != null) {
                        val deleted = stateCacheManager.deleteAutoStateFromDisk(
                            emulatorId = emulatorId,
                            romPath = game.localPath,
                            platformSlug = game.platformSlug,
                            emulatorPackage = emulatorPackage,
                            coreId = state.currentCoreId
                        )
                        android.util.Log.d("SaveChannelDelegate", "Deleted auto-state from disk: $deleted")
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
            onSaveStatusChanged(SaveStatusEvent(channelName = targetChannel, timestamp = newTimestamp))

            titleIdDownloadObserver.extractTitleIdForGame(currentGameId)

            when (val result = restoreCachedSaveUseCase(entry, currentGameId, emulatorId, syncToServer)) {
                is RestoreCachedSaveUseCase.Result.Restored -> {
                    val msg = if (targetChannel != null) "Restored to $targetChannel" else "Save restored"
                    notificationManager.showSuccess(msg)
                    onRestored()
                }
                is RestoreCachedSaveUseCase.Result.RestoredAndSynced -> {
                    val msg = if (targetChannel != null) "Restored to $targetChannel and synced" else "Save restored and synced"
                    notificationManager.showSuccess(msg)
                    onRestored()
                }
                is RestoreCachedSaveUseCase.Result.Error -> {
                    notificationManager.showError(result.message)
                }
            }
        }
    }

    fun showCreateChannelDialog() {
        val state = _state.value
        if (state.selectedTab != SaveTab.TIMELINE) return
        val entry = state.focusedEntry ?: return
        if (!entry.canBecomeChannel) return
        _state.update {
            it.copy(
                showRenameDialog = true,
                renameEntry = entry,
                renameText = ""
            )
        }
    }

    fun showRenameChannelDialog() {
        val state = _state.value
        if (state.selectedTab != SaveTab.SLOTS) return
        val entry = state.focusedEntry ?: return
        if (!entry.isChannel) return

        _state.update {
            it.copy(
                showRenameDialog = true,
                renameEntry = entry,
                renameText = entry.channelName ?: ""
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

    fun confirmCreateChannel(scope: CoroutineScope) {
        val state = _state.value
        val entry = state.renameEntry ?: return
        val newName = state.renameText.trim()

        if (newName.isBlank()) {
            notificationManager.showError("Channel name cannot be empty")
            return
        }

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

    fun confirmRenameChannel(scope: CoroutineScope) {
        val state = _state.value
        val entry = state.renameEntry ?: return
        val cacheId = entry.localCacheId ?: return
        val newName = state.renameText.trim()

        if (newName.isBlank()) {
            notificationManager.showError("Channel name cannot be empty")
            return
        }

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
        if (state.selectedTab != SaveTab.SLOTS) return
        val entry = state.focusedEntry ?: return
        if (!entry.isChannel) return

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

    fun confirmDeleteChannel(scope: CoroutineScope, onSaveStatusChanged: (SaveStatusEvent) -> Unit) {
        val state = _state.value
        val entry = state.deleteSelectedEntry ?: return
        val channelName = entry.channelName ?: return

        scope.launch {
            entry.localCacheId?.let { saveCacheManager.deleteSave(it) }

            if (state.activeChannel == channelName) {
                gameDao.updateActiveSaveChannel(currentGameId, null)
                gameDao.updateActiveSaveTimestamp(currentGameId, null)
                _state.update { it.copy(activeChannel = null, activeSaveTimestamp = null) }
                onSaveStatusChanged(SaveStatusEvent(channelName = null, timestamp = null))
            }

            refreshEntries()
            _state.update {
                val newSlots = it.slotsEntries
                it.copy(
                    showDeleteConfirmation = false,
                    deleteSelectedEntry = null,
                    focusIndex = it.focusIndex.coerceAtMost((newSlots.size - 1).coerceAtLeast(0)),
                    selectedTab = if (newSlots.isEmpty()) SaveTab.TIMELINE else it.selectedTab
                )
            }
            notificationManager.showSuccess("Deleted save slot '$channelName'")
        }
    }

    @Suppress("UNUSED_PARAMETER")
    fun secondaryAction(scope: CoroutineScope, onSaveStatusChanged: (SaveStatusEvent) -> Unit) {
        val state = _state.value
        if (state.showRestoreConfirmation || state.showRenameDialog || state.showDeleteConfirmation ||
            state.showStateDeleteConfirmation || state.showStateReplaceAutoConfirmation) return

        when (state.selectedTab) {
            SaveTab.SLOTS -> showDeleteConfirmation()
            SaveTab.TIMELINE -> showCreateChannelDialog()
            SaveTab.STATES -> showStateDeleteConfirmation()
        }
    }

    fun tertiaryAction() {
        val state = _state.value
        if (state.showRestoreConfirmation || state.showRenameDialog || state.showDeleteConfirmation ||
            state.showStateDeleteConfirmation || state.showStateReplaceAutoConfirmation) return

        when (state.selectedTab) {
            SaveTab.SLOTS -> showRenameChannelDialog()
            SaveTab.STATES -> showStateReplaceAutoConfirmation()
            else -> {}
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
                    val slotLabel = if (stateEntry.slotNumber == -1) "auto state" else "state slot ${stateEntry.slotNumber}"
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
                    notificationManager.showError("No state configuration for this emulator")
                }
            }
        }
    }

    private suspend fun refreshEntries() {
        val state = _state.value
        val entries = getUnifiedSavesUseCase(currentGameId)
        val slots = entries.filter { it.isLocked }.sortedBy { it.channelName?.lowercase() }
        val timeline = buildTimeline(entries, state.activeChannel, state.activeSaveTimestamp)

        _state.update {
            it.copy(
                slotsEntries = slots,
                timelineEntries = timeline
            )
        }
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
                    focusIndex = it.focusIndex.coerceAtMost((it.statesEntries.size - 2).coerceAtLeast(0))
                )
            }
            val slotLabel = if (entry.slotNumber == -1) "auto state" else "state slot ${entry.slotNumber}"
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
                val autoScreenshot = java.io.File(coreDir, "$autoFileName.png")
                screenshotFile.copyTo(autoScreenshot, overwrite = true)
            }

            val channelDirName = sourceCache.channelName ?: "default"
            val coreDirName = sourceCache.coreId ?: "unknown"
            val autoCachePath = "${sourceCache.platformSlug}/${currentGameId}/$channelDirName/$coreDirName/$autoFileName"
            val autoScreenshotPath = if (screenshotFile != null) "$autoCachePath.png" else null

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
            notificationManager.showSuccess("Replaced auto state with slot ${sourceEntry.slotNumber}")
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
