package com.nendo.argosy.ui.common.savechannel

import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.repository.SaveCacheManager
import com.nendo.argosy.domain.model.UnifiedSaveEntry
import com.nendo.argosy.domain.usecase.save.GetUnifiedSavesUseCase
import com.nendo.argosy.domain.usecase.save.RestoreCachedSaveUseCase
import com.nendo.argosy.ui.input.SoundFeedbackManager
import com.nendo.argosy.ui.input.SoundType
import com.nendo.argosy.ui.notification.NotificationManager
import com.nendo.argosy.ui.notification.showError
import com.nendo.argosy.ui.notification.showSuccess
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

class SaveChannelDelegate @Inject constructor(
    private val getUnifiedSavesUseCase: GetUnifiedSavesUseCase,
    private val restoreCachedSaveUseCase: RestoreCachedSaveUseCase,
    private val saveCacheManager: SaveCacheManager,
    private val gameDao: GameDao,
    private val notificationManager: NotificationManager,
    private val soundManager: SoundFeedbackManager
) {
    private val _state = MutableStateFlow(SaveChannelState())
    val state: StateFlow<SaveChannelState> = _state.asStateFlow()

    private var currentGameId: Long = 0

    fun show(scope: CoroutineScope, gameId: Long, activeChannel: String?) {
        currentGameId = gameId
        _state.update {
            it.copy(
                isVisible = true,
                isLoading = true,
                focusIndex = 0,
                activeChannel = activeChannel
            )
        }
        soundManager.play(SoundType.OPEN_MODAL)

        scope.launch {
            val activeSaveTimestamp = gameDao.getActiveSaveTimestamp(gameId)
            val entries = getUnifiedSavesUseCase(gameId)
            val slots = entries.filter { it.isLocked }.sortedBy { it.channelName?.lowercase() }
            val timeline = buildTimeline(entries, activeChannel, activeSaveTimestamp)

            val initialTab = determineInitialTab(slots.isNotEmpty(), activeChannel)

            _state.update {
                it.copy(
                    slotsEntries = slots,
                    timelineEntries = timeline,
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
            sorted.firstOrNull { it.channelName == null && it.isLatest }
                ?: sorted.firstOrNull { it.channelName == null }
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
            SaveChannelState(activeChannel = it.activeChannel)
        }
        soundManager.play(SoundType.CLOSE_MODAL)
    }

    fun switchTab(tab: SaveTab) {
        val state = _state.value
        if (tab == SaveTab.SLOTS && !state.hasSaveSlots) return
        if (tab == state.selectedTab) return

        val newEntries = if (tab == SaveTab.SLOTS) state.slotsEntries else state.timelineEntries
        var newFocusIndex = 0

        if (tab == SaveTab.TIMELINE && state.activeChannel == null &&
            newEntries.isNotEmpty() && newEntries.first().isLatest
        ) {
            newFocusIndex = if (newEntries.size > 1) 1 else 0
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
            val entries = state.currentTabEntries
            if (entries.isEmpty()) return@update state

            var newIndex = state.focusIndex + delta

            val maxIndex = (entries.size - 1).coerceAtLeast(0)
            newIndex = newIndex.coerceIn(0, maxIndex)

            if (newIndex != state.focusIndex) {
                soundManager.play(SoundType.NAVIGATE)
            }
            state.copy(focusIndex = newIndex)
        }
    }

    fun setFocusIndex(index: Int) {
        _state.update { state ->
            val entries = state.currentTabEntries
            if (entries.isEmpty()) return@update state

            val maxIndex = (entries.size - 1).coerceAtLeast(0)
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
        }
    }

    fun confirmSelection(
        scope: CoroutineScope,
        emulatorId: String,
        onChannelChanged: (String?) -> Unit,
        onRestored: () -> Unit = {}
    ) {
        val state = _state.value
        val entry = state.focusedEntry ?: return

        if (state.selectedTab == SaveTab.SLOTS) {
            scope.launch {
                val channelName = entry.channelName ?: return@launch
                gameDao.updateActiveSaveChannel(currentGameId, channelName)
                gameDao.updateActiveSaveTimestamp(currentGameId, null)
                _state.update { it.copy(activeChannel = channelName, activeSaveTimestamp = null) }
                onChannelChanged(channelName)

                when (val result = restoreCachedSaveUseCase(entry, currentGameId, emulatorId, false)) {
                    is RestoreCachedSaveUseCase.Result.Restored,
                    is RestoreCachedSaveUseCase.Result.RestoredAndSynced -> {
                        notificationManager.showSuccess("Using save slot: $channelName")
                        _state.update { it.copy(isVisible = false) }
                        onRestored()
                    }
                    is RestoreCachedSaveUseCase.Result.Error -> {
                        notificationManager.showError(result.message)
                    }
                }
            }
        } else {
            _state.update {
                it.copy(
                    showRestoreConfirmation = true,
                    restoreSelectedEntry = entry
                )
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
        onChannelChanged: (String?) -> Unit,
        onRestored: () -> Unit = {}
    ) {
        val entry = _state.value.restoreSelectedEntry ?: return
        val targetChannel = entry.channelName
        val targetTimestamp = entry.timestamp.toEpochMilli()

        scope.launch {
            gameDao.updateActiveSaveTimestamp(currentGameId, targetTimestamp)
            _state.update {
                it.copy(
                    showRestoreConfirmation = false,
                    isVisible = false,
                    activeChannel = targetChannel,
                    activeSaveTimestamp = targetTimestamp
                )
            }
            onChannelChanged(targetChannel)

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
        val cacheId = entry.localCacheId ?: return
        val newName = state.renameText.trim()

        if (newName.isBlank()) {
            notificationManager.showError("Channel name cannot be empty")
            return
        }

        scope.launch {
            saveCacheManager.copyToChannel(cacheId, newName)
            refreshEntries()
            _state.update {
                it.copy(
                    showRenameDialog = false,
                    renameEntry = null,
                    renameText = ""
                )
            }
            notificationManager.showSuccess("Created save slot '$newName'")
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

    fun confirmDeleteChannel(scope: CoroutineScope, onChannelChanged: (String?) -> Unit) {
        val state = _state.value
        val entry = state.deleteSelectedEntry ?: return
        val channelName = entry.channelName ?: return

        scope.launch {
            entry.localCacheId?.let { saveCacheManager.deleteSave(it) }

            if (state.activeChannel == channelName) {
                gameDao.updateActiveSaveChannel(currentGameId, null)
                _state.update { it.copy(activeChannel = null) }
                onChannelChanged(null)
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
    fun secondaryAction(scope: CoroutineScope, onChannelChanged: (String?) -> Unit) {
        val state = _state.value
        if (state.showRestoreConfirmation || state.showRenameDialog || state.showDeleteConfirmation || state.showResetConfirmation) return

        when (state.selectedTab) {
            SaveTab.SLOTS -> showDeleteConfirmation()
            SaveTab.TIMELINE -> showCreateChannelDialog()
        }
    }

    fun tertiaryAction() {
        val state = _state.value
        if (state.showRestoreConfirmation || state.showRenameDialog || state.showDeleteConfirmation || state.showResetConfirmation) return

        if (state.selectedTab == SaveTab.SLOTS) {
            showRenameChannelDialog()
        }
    }

    fun showResetConfirmation() {
        _state.update { it.copy(showResetConfirmation = true) }
    }

    fun dismissResetConfirmation() {
        _state.update { it.copy(showResetConfirmation = false) }
    }

    fun confirmReset(scope: CoroutineScope, onChannelChanged: (String?) -> Unit) {
        scope.launch {
            gameDao.updateActiveSaveChannel(currentGameId, null)
            gameDao.updateActiveSaveTimestamp(currentGameId, null)
            _state.update {
                it.copy(
                    activeChannel = null,
                    activeSaveTimestamp = null,
                    showResetConfirmation = false,
                    isVisible = false
                )
            }
            onChannelChanged(null)
            notificationManager.showSuccess("Reset to latest save")
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
}
