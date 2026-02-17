package com.nendo.argosy.ui.dualscreen.gamedetail

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.local.dao.EmulatorConfigDao
import com.nendo.argosy.data.local.entity.CollectionEntity
import com.nendo.argosy.data.local.dao.GameDao
import com.nendo.argosy.data.repository.CollectionRepository
import com.nendo.argosy.data.repository.PlatformRepository
import com.nendo.argosy.data.local.entity.CollectionGameEntity
import com.nendo.argosy.data.local.entity.EmulatorConfigEntity
import com.nendo.argosy.data.local.entity.getDisplayName
import com.nendo.argosy.data.emulator.EmulatorRegistry
import com.nendo.argosy.data.emulator.InstalledEmulator
import com.nendo.argosy.data.model.GameSource
import com.nendo.argosy.domain.model.CompletionStatus
import com.nendo.argosy.ui.common.savechannel.SaveFocusColumn
import com.nendo.argosy.ui.common.savechannel.SaveHistoryItem
import com.nendo.argosy.ui.common.savechannel.SaveSlotItem
import com.nendo.argosy.util.DisplayAffinityHelper
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

const val MEDIA_GRID_COLUMNS = 3

class DualGameDetailViewModel(
    private val gameDao: GameDao,
    private val platformRepository: PlatformRepository,
    private val collectionRepository: CollectionRepository,
    private val emulatorConfigDao: EmulatorConfigDao,
    private val displayAffinityHelper: DisplayAffinityHelper,
    private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(DualGameDetailUiState())
    val uiState: StateFlow<DualGameDetailUiState> = _uiState.asStateFlow()

    private val _selectedScreenshotIndex = MutableStateFlow(-1)
    val selectedScreenshotIndex: StateFlow<Int> =
        _selectedScreenshotIndex.asStateFlow()

    private val _selectedOptionIndex = MutableStateFlow(0)
    val selectedOptionIndex: StateFlow<Int> =
        _selectedOptionIndex.asStateFlow()

    private val _saveSlots = MutableStateFlow<List<SaveSlotItem>>(emptyList())
    val saveSlots: StateFlow<List<SaveSlotItem>> = _saveSlots.asStateFlow()

    private var _rawEntries: List<SaveEntryData> = emptyList()

    private val _savesLoading = MutableStateFlow(true)
    val savesLoading: StateFlow<Boolean> = _savesLoading.asStateFlow()

    private val _savesApplying = MutableStateFlow(false)
    val savesApplying: StateFlow<Boolean> = _savesApplying.asStateFlow()

    private val _saveHistory = MutableStateFlow<List<SaveHistoryItem>>(emptyList())
    val saveHistory: StateFlow<List<SaveHistoryItem>> = _saveHistory.asStateFlow()

    private val _selectedSlotIndex = MutableStateFlow(0)
    val selectedSlotIndex: StateFlow<Int> = _selectedSlotIndex.asStateFlow()

    private val _selectedHistoryIndex = MutableStateFlow(0)
    val selectedHistoryIndex: StateFlow<Int> = _selectedHistoryIndex.asStateFlow()

    private val _activeModal =
        MutableStateFlow(ActiveModal.NONE)
    val activeModal: StateFlow<ActiveModal> = _activeModal.asStateFlow()

    private val _ratingPickerValue = MutableStateFlow(0)
    val ratingPickerValue: StateFlow<Int> =
        _ratingPickerValue.asStateFlow()

    private val _statusPickerValue = MutableStateFlow<String?>(null)
    val statusPickerValue: StateFlow<String?> =
        _statusPickerValue.asStateFlow()

    private val _visibleOptions =
        MutableStateFlow<List<GameDetailOption>>(emptyList())
    val visibleOptions: StateFlow<List<GameDetailOption>> =
        _visibleOptions.asStateFlow()

    private val _emulatorPickerList =
        MutableStateFlow<List<InstalledEmulator>>(emptyList())

    private val _collectionItems =
        MutableStateFlow<List<DualCollectionItem>>(emptyList())
    val collectionItems: StateFlow<List<DualCollectionItem>> =
        _collectionItems.asStateFlow()

    private var ratingDebounceJob: Job? = null
    private var difficultyDebounceJob: Job? = null
    private var statusDebounceJob: Job? = null

    val focusedSlotChannelName: String?
        get() {
            val slot = _saveSlots.value.getOrNull(_selectedSlotIndex.value)
            return if (slot?.isCreateAction == true) null else slot?.channelName
        }

    fun adjustRatingInline(delta: Int) {
        val current = _uiState.value.rating ?: 0
        val next = (current + delta).coerceIn(0, 10)
        _uiState.update { it.copy(rating = next.takeIf { v -> v > 0 }) }
        ratingDebounceJob?.cancel()
        ratingDebounceJob = viewModelScope.launch {
            delay(250)
            val gameId = _uiState.value.gameId
            if (gameId >= 0) gameDao.updateUserRating(gameId, next)
        }
    }

    fun adjustDifficultyInline(delta: Int) {
        val current = _uiState.value.userDifficulty
        val next = (current + delta).coerceIn(0, 10)
        _uiState.update { it.copy(userDifficulty = next) }
        difficultyDebounceJob?.cancel()
        difficultyDebounceJob = viewModelScope.launch {
            delay(250)
            val gameId = _uiState.value.gameId
            if (gameId >= 0) gameDao.updateUserDifficulty(gameId, next)
        }
    }

    fun cycleStatusInline(delta: Int) {
        val current = _uiState.value.status
        val next = if (delta > 0) CompletionStatus.cycleNext(current)
            else CompletionStatus.cyclePrev(current)
        _uiState.update { it.copy(status = next) }
        statusDebounceJob?.cancel()
        statusDebounceJob = viewModelScope.launch {
            delay(250)
            val gameId = _uiState.value.gameId
            if (gameId >= 0) gameDao.updateStatus(gameId, next)
        }
    }

    fun openRatingPicker() {
        _ratingPickerValue.value = _uiState.value.rating ?: 0
        _activeModal.value = ActiveModal.RATING
    }

    fun openDifficultyPicker() {
        _ratingPickerValue.value = _uiState.value.userDifficulty
        _activeModal.value = ActiveModal.DIFFICULTY
    }

    fun openStatusPicker() {
        _statusPickerValue.value =
            _uiState.value.status
                ?: CompletionStatus.entries.first().apiValue
        _activeModal.value = ActiveModal.STATUS
    }

    fun adjustPickerValue(delta: Int) {
        _ratingPickerValue.update { (it + delta).coerceIn(0, 10) }
    }

    fun setPickerValue(value: Int) {
        _ratingPickerValue.value = value.coerceIn(0, 10)
    }

    fun moveStatusSelection(delta: Int) {
        val entries = CompletionStatus.entries
        val current = CompletionStatus.fromApiValue(
            _statusPickerValue.value
        ) ?: entries.first()
        val next = entries[
            (current.ordinal + delta).mod(entries.size)
        ]
        _statusPickerValue.value = next.apiValue
    }

    fun setStatusSelection(apiValue: String) {
        _statusPickerValue.value = apiValue
    }

    fun confirmPicker() {
        val gameId = _uiState.value.gameId
        if (gameId < 0) return
        when (_activeModal.value) {
            ActiveModal.RATING -> {
                val value = _ratingPickerValue.value
                _uiState.update { it.copy(rating = value.takeIf { v -> v > 0 }) }
                viewModelScope.launch {
                    gameDao.updateUserRating(gameId, value)
                }
            }
            ActiveModal.DIFFICULTY -> {
                val value = _ratingPickerValue.value
                _uiState.update { it.copy(userDifficulty = value) }
                viewModelScope.launch {
                    gameDao.updateUserDifficulty(gameId, value)
                }
            }
            ActiveModal.STATUS -> {
                val value = _statusPickerValue.value
                _uiState.update { it.copy(status = value) }
                viewModelScope.launch {
                    gameDao.updateStatus(gameId, value)
                }
            }
            ActiveModal.EMULATOR, ActiveModal.COLLECTION,
            ActiveModal.SAVE_NAME -> return
            ActiveModal.NONE -> return
        }
        _activeModal.value = ActiveModal.NONE
    }

    fun dismissPicker() {
        _activeModal.value = ActiveModal.NONE
    }

    fun loadGame(gameId: Long) {
        viewModelScope.launch {
            val game = gameDao.getById(gameId) ?: return@launch
            val platform = platformRepository.getById(game.platformId)
            val platformName = platform?.getDisplayName() ?: game.platformSlug

            val remoteUrls = game.screenshotPaths
                ?.split(",")?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?: emptyList()
            val cachedPaths = game.cachedScreenshotPaths
                ?.split(",")?.map { it.trim() }
                ?.filter { it.isNotBlank() }
                ?: emptyList()
            val screenshots = remoteUrls.mapIndexed { index, url ->
                cachedPaths.getOrNull(index)
                    ?.takeIf { it.startsWith("/") }
                    ?: url
            }

            val isPlayable = game.localPath != null ||
                game.source == GameSource.STEAM ||
                game.source == GameSource.ANDROID_APP

            val emulatorConfig =
                emulatorConfigDao.getByGameId(game.id)
                    ?: emulatorConfigDao.getDefaultForPlatform(game.platformId)

            val activeChannel = game.activeSaveChannel
            val activeSaveTimestamp = game.activeSaveTimestamp

            val newState = DualGameDetailUiState(
                gameId = game.id,
                title = game.title,
                coverPath = game.coverPath,
                backgroundPath = game.backgroundPath,
                platformName = platformName,
                developer = game.developer,
                releaseYear = game.releaseYear,
                description = game.description,
                playTimeMinutes = game.playTimeMinutes,
                lastPlayedAt = game.lastPlayed?.toEpochMilli() ?: 0,
                status = game.status,
                rating = game.userRating.takeIf { r -> r > 0 },
                isPlayable = isPlayable,
                userDifficulty = game.userDifficulty,
                screenshots = screenshots,
                currentTab = DualGameDetailTab.OPTIONS,
                isFavorite = game.isFavorite,
                isLoading = false,
                achievementCount = game.achievementCount,
                earnedAchievementCount = game.earnedAchievementCount,
                isRommGame = game.rommId != null,
                isSteamGame = game.source == GameSource.STEAM,
                isAndroidApp = game.source == GameSource.ANDROID_APP,
                isDownloaded = game.localPath != null,
                platformSlug = game.platformSlug,
                platformId = game.platformId,
                emulatorName = emulatorConfig?.displayName,
                activeChannel = activeChannel,
                activeSaveTimestamp = activeSaveTimestamp
            )
            _uiState.value = newState
            _visibleOptions.value = newState.visibleOptions()

            _selectedScreenshotIndex.value =
                if (screenshots.isNotEmpty()) 0 else -1
            _selectedOptionIndex.value = 0
            _savesLoading.value = true
        }
    }

    fun loadUnifiedSaves(
        entries: List<SaveEntryData>,
        activeChannel: String?,
        activeSaveTimestamp: Long?
    ) {
        _rawEntries = entries

        val channelGroups = entries.groupBy { it.channelName }
        val slotItems = mutableListOf<SaveSlotItem>()

        val autoSaves = channelGroups[null] ?: emptyList()
        slotItems.add(
            SaveSlotItem(
                channelName = null,
                displayName = "Auto Save",
                isActive = activeChannel == null,
                saveCount = autoSaves.size,
                latestTimestamp = autoSaves.maxByOrNull { it.timestamp }
                    ?.timestamp
            )
        )

        channelGroups.filterKeys { it != null }
            .forEach { (name, saves) ->
                slotItems.add(
                    SaveSlotItem(
                        channelName = name,
                        displayName = name!!,
                        isActive = name == activeChannel,
                        saveCount = saves.size,
                        latestTimestamp = saves.maxByOrNull { it.timestamp }
                            ?.timestamp
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

        val preservedSlotName = _saveSlots.value
            .getOrNull(_selectedSlotIndex.value)?.channelName
        _saveSlots.value = slotItems
        val restoredIndex = if (preservedSlotName != null) {
            slotItems.indexOfFirst {
                !it.isCreateAction && it.channelName == preservedSlotName
            }.takeIf { it >= 0 }
        } else null
        _selectedSlotIndex.value = restoredIndex ?: 0
        _selectedHistoryIndex.value = 0

        _uiState.update {
            it.copy(
                activeChannel = activeChannel,
                activeSaveTimestamp = activeSaveTimestamp
            )
        }
        updateHistoryForFocusedSlot(activeSaveTimestamp)
        _savesLoading.value = false
        _savesApplying.value = false
    }

    private fun updateHistoryForFocusedSlot(
        activeSaveTimestamp: Long? = _uiState.value.activeSaveTimestamp
    ) {
        val slot = _saveSlots.value.getOrNull(_selectedSlotIndex.value)
        if (slot == null || slot.isCreateAction) {
            _saveHistory.value = emptyList()
            return
        }
        val channelName = slot.channelName
        val activeChannel = _uiState.value.activeChannel
        val isActiveChannel = channelName == activeChannel
        val filtered = _rawEntries
            .filter { it.channelName == channelName }
            .sortedByDescending { it.timestamp }
        _saveHistory.value = filtered.mapIndexed { i, entry ->
            val isApplied = isActiveChannel && if (activeSaveTimestamp != null) {
                entry.timestamp == activeSaveTimestamp
            } else {
                i == 0
            }
            SaveHistoryItem(
                cacheId = entry.localCacheId ?: -1,
                timestamp = entry.timestamp,
                size = entry.size,
                channelName = entry.channelName,
                isLocal = entry.source != "SERVER",
                isSynced = entry.source == "BOTH" || entry.source == "SERVER",
                isActiveRestorePoint = isApplied,
                isLatest = i == 0,
                isHardcore = entry.isHardcore,
                isRollback = entry.isRollback
            )
        }
        _selectedHistoryIndex.value = 0
    }

    fun reloadSaves() {
        _savesLoading.value = true
    }

    fun setActiveChannel(channelName: String?) {
        _savesApplying.value = true
        _uiState.update {
            it.copy(activeChannel = channelName, activeSaveTimestamp = null)
        }
        _saveSlots.update { slots ->
            slots.map { slot ->
                if (slot.isCreateAction) slot
                else slot.copy(isActive = slot.channelName == channelName)
            }
        }
    }

    fun setActiveRestorePoint(channelName: String?, timestamp: Long) {
        _savesApplying.value = true
        _uiState.update {
            it.copy(
                activeChannel = channelName,
                activeSaveTimestamp = timestamp
            )
        }
        _saveSlots.update { slots ->
            slots.map { slot ->
                if (slot.isCreateAction) slot
                else slot.copy(isActive = slot.channelName == channelName)
            }
        }
        updateHistoryForFocusedSlot(timestamp)
    }

    fun focusSlotsColumn() {
        _uiState.update { it.copy(saveFocusColumn = SaveFocusColumn.SLOTS) }
    }

    fun focusHistoryColumn() {
        if (_saveHistory.value.isEmpty()) return
        _uiState.update { it.copy(saveFocusColumn = SaveFocusColumn.HISTORY) }
        if (_selectedHistoryIndex.value < 0) _selectedHistoryIndex.value = 0
    }

    fun moveSlotSelection(delta: Int) {
        val max = (_saveSlots.value.size - 1).coerceAtLeast(0)
        _selectedSlotIndex.update { (it + delta).coerceIn(0, max) }
        updateHistoryForFocusedSlot()
    }

    fun moveHistorySelection(delta: Int) {
        val max = (_saveHistory.value.size - 1).coerceAtLeast(0)
        _selectedHistoryIndex.update { (it + delta).coerceIn(0, max) }
    }

    fun setTab(tab: DualGameDetailTab) {
        _uiState.update { it.copy(currentTab = tab) }
        resetSelectionForTab(tab)
    }

    fun nextTab() {
        val entries = DualGameDetailTab.entries
        val current = _uiState.value.currentTab
        val next = entries[(current.ordinal + 1) % entries.size]
        setTab(next)
    }

    fun previousTab() {
        val entries = DualGameDetailTab.entries
        val current = _uiState.value.currentTab
        val prev = entries[
            if (current.ordinal == 0) entries.size - 1
            else current.ordinal - 1
        ]
        setTab(prev)
    }

    fun moveSelectionUp() {
        when (_uiState.value.currentTab) {
            DualGameDetailTab.SAVES -> {
                if (_uiState.value.saveFocusColumn == SaveFocusColumn.SLOTS) {
                    moveSlotSelection(-1)
                } else {
                    moveHistorySelection(-1)
                }
            }
            DualGameDetailTab.OPTIONS -> {
                _selectedOptionIndex.update { idx ->
                    (idx - 1).coerceAtLeast(0)
                }
            }
            DualGameDetailTab.MEDIA -> {
                val screenshots = _uiState.value.screenshots
                if (screenshots.isEmpty()) return
                _selectedScreenshotIndex.update { idx ->
                    (idx - MEDIA_GRID_COLUMNS).coerceAtLeast(0)
                }
            }
        }
    }

    fun moveSelectionDown() {
        when (_uiState.value.currentTab) {
            DualGameDetailTab.SAVES -> {
                if (_uiState.value.saveFocusColumn == SaveFocusColumn.SLOTS) {
                    moveSlotSelection(1)
                } else {
                    moveHistorySelection(1)
                }
            }
            DualGameDetailTab.OPTIONS -> {
                _selectedOptionIndex.update { idx ->
                    (idx + 1).coerceAtMost(
                        (_visibleOptions.value.size - 1).coerceAtLeast(0)
                    )
                }
            }
            DualGameDetailTab.MEDIA -> {
                val screenshots = _uiState.value.screenshots
                if (screenshots.isEmpty()) return
                _selectedScreenshotIndex.update { idx ->
                    (idx + MEDIA_GRID_COLUMNS)
                        .coerceAtMost(screenshots.size - 1)
                }
            }
        }
    }

    fun moveSelectionLeft() {
        if (_uiState.value.currentTab != DualGameDetailTab.MEDIA) return
        val screenshots = _uiState.value.screenshots
        if (screenshots.isEmpty()) return
        _selectedScreenshotIndex.update { idx ->
            (idx - 1).coerceAtLeast(0)
        }
    }

    fun moveSelectionRight() {
        if (_uiState.value.currentTab != DualGameDetailTab.MEDIA) return
        val screenshots = _uiState.value.screenshots
        if (screenshots.isEmpty()) return
        _selectedScreenshotIndex.update { idx ->
            (idx + 1).coerceAtMost(screenshots.size - 1)
        }
    }

    fun toggleFavorite() {
        val state = _uiState.value
        if (state.gameId < 0) return
        val newFavorite = !state.isFavorite
        _uiState.update { it.copy(isFavorite = newFavorite) }
        viewModelScope.launch {
            gameDao.updateFavorite(state.gameId, newFavorite)
        }
    }

    fun getPlayIntent(gameId: Long): Pair<Intent, android.os.Bundle?> {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("argosy://play/$gameId")
            setPackage(context.packageName)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
        }
        val options =
            displayAffinityHelper.getActivityOptions(forEmulator = true)
        return intent to options
    }

    fun getGameDetailIntent(
        gameId: Long
    ): Pair<Intent, android.os.Bundle?> {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            data = Uri.parse("argosy://game/$gameId")
            setPackage(context.packageName)
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP
            )
        }
        val options =
            displayAffinityHelper.getActivityOptions(forEmulator = true)
        return intent to options
    }

    fun getSelectedScreenshotPath(): String? {
        val idx = _selectedScreenshotIndex.value
        val screenshots = _uiState.value.screenshots
        return screenshots.getOrNull(idx)
    }

    fun setScreenshotIndex(index: Int) {
        _selectedScreenshotIndex.value = index
    }

    private fun resetSelectionForTab(tab: DualGameDetailTab) {
        when (tab) {
            DualGameDetailTab.SAVES -> {
                _selectedSlotIndex.value =
                    if (_saveSlots.value.isNotEmpty()) 0 else 0
                _selectedHistoryIndex.value = 0
                _uiState.update {
                    it.copy(saveFocusColumn = SaveFocusColumn.SLOTS)
                }
                updateHistoryForFocusedSlot()
            }
            DualGameDetailTab.MEDIA -> {
                _selectedScreenshotIndex.value =
                    if (_uiState.value.screenshots.isNotEmpty()) 0 else -1
            }
            DualGameDetailTab.OPTIONS -> {
                _selectedOptionIndex.value = 0
            }
        }
    }

    fun openEmulatorPicker(emulators: List<InstalledEmulator>) {
        _emulatorPickerList.value = emulators
        _activeModal.value = ActiveModal.EMULATOR
    }

    fun confirmEmulatorByIndex(index: Int) {
        val emulators = _emulatorPickerList.value
        val selected = if (index == 0) null else emulators.getOrNull(index - 1)
        val state = _uiState.value
        viewModelScope.launch {
            emulatorConfigDao.deleteGameOverride(state.gameId)
            if (selected != null) {
                emulatorConfigDao.insert(
                    EmulatorConfigEntity(
                        platformId = state.platformId,
                        gameId = state.gameId,
                        packageName = selected.def.packageName,
                        displayName = selected.def.displayName,
                        coreName = EmulatorRegistry.getDefaultCore(
                            state.platformSlug
                        )?.id,
                        isDefault = false
                    )
                )
            }
            _uiState.update {
                it.copy(emulatorName = selected?.def?.displayName)
            }
        }
        _activeModal.value = ActiveModal.NONE
    }

    fun openCollectionModal() {
        viewModelScope.launch {
            val gameId = _uiState.value.gameId
            val allCollections = collectionRepository.getAllCollections()
                .filter { it.name.isNotBlank() && it.isUserCreated }
            val memberIds = collectionRepository.getCollectionIdsForGame(gameId)
            _collectionItems.value = allCollections.map {
                DualCollectionItem(
                    it.id, it.name, memberIds.contains(it.id)
                )
            }
            _activeModal.value = ActiveModal.COLLECTION
        }
    }

    fun toggleCollection(collectionId: Long) {
        val gameId = _uiState.value.gameId
        val item = _collectionItems.value.find { it.id == collectionId }
            ?: return
        viewModelScope.launch {
            if (item.isInCollection) {
                collectionRepository.removeGameFromCollection(collectionId, gameId)
            } else {
                collectionRepository.addGameToCollection(
                    CollectionGameEntity(collectionId, gameId)
                )
            }
            _collectionItems.update { list ->
                list.map {
                    if (it.id == collectionId)
                        it.copy(isInCollection = !it.isInCollection)
                    else it
                }
            }
        }
    }

    fun createAndAddToCollection(name: String) {
        val gameId = _uiState.value.gameId
        if (gameId < 0) return
        viewModelScope.launch {
            val collectionId = collectionRepository.insertCollection(
                CollectionEntity(name = name)
            )
            collectionRepository.addGameToCollection(
                CollectionGameEntity(
                    collectionId = collectionId,
                    gameId = gameId
                )
            )
            val allCollections = collectionRepository.getAllCollections()
                .filter { it.name.isNotBlank() && it.isUserCreated }
            val memberIds = collectionRepository.getCollectionIdsForGame(gameId)
            _collectionItems.value = allCollections.map {
                DualCollectionItem(
                    it.id, it.name, memberIds.contains(it.id)
                )
            }
        }
    }

    fun dismissCollectionModal() {
        _activeModal.value = ActiveModal.NONE
    }
}
