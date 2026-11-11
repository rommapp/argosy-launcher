package com.nendo.argosy.ui.screens.collections

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.qualifiers.ApplicationContext
import com.nendo.argosy.data.repository.CollectionRepository
import com.nendo.argosy.data.repository.DownloadFileStatusRepository
import com.nendo.argosy.data.repository.PlatformRepository
import com.nendo.argosy.ui.common.GameListDetails
import com.nendo.argosy.ui.common.listDetails
import com.nendo.argosy.ui.common.resolveDownloaded
import com.nendo.argosy.data.local.entity.CollectionEntity
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.getDisplayName
import com.nendo.argosy.data.remote.romm.RomMRepository
import com.nendo.argosy.domain.usecase.collection.IsPinnedUseCase
import com.nendo.argosy.domain.usecase.collection.PinCollectionUseCase
import com.nendo.argosy.domain.usecase.collection.RefreshAllCollectionsUseCase
import com.nendo.argosy.domain.usecase.collection.UnpinCollectionUseCase
import com.nendo.argosy.domain.usecase.download.DownloadGameUseCase
import com.nendo.argosy.R
import com.nendo.argosy.core.notification.NotificationDuration
import com.nendo.argosy.core.notification.NotificationManager
import com.nendo.argosy.core.notification.NotificationText
import com.nendo.argosy.core.notification.NotificationType
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.screens.collections.dialogs.CollectionOption
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CollectionGameUi(
    val id: Long,
    val title: String,
    val platformId: Long,
    val platformSlug: String,
    val platformDisplayName: String,
    val details: GameListDetails,
    val isFavorite: Boolean,
    val isDownloaded: Boolean,
    val rommId: Long?
)

suspend fun GameEntity.toCollectionGameUi(
    platformDisplayName: String,
    downloadStatus: DownloadFileStatusRepository
) = CollectionGameUi(
    id = id,
    title = title,
    platformId = platformId,
    platformSlug = platformSlug,
    platformDisplayName = platformDisplayName,
    details = listDetails,
    isFavorite = isFavorite,
    isDownloaded = resolveDownloaded(downloadStatus),
    rommId = rommId
)

data class CollectionDetailUiState(
    val collection: CollectionEntity? = null,
    val games: List<CollectionGameUi> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val focusedIndex: Int = 0,
    val showOptionsModal: Boolean = false,
    val optionsModalFocusIndex: Int = 0,
    val showEditDialog: Boolean = false,
    val showDeleteDialog: Boolean = false,
    val showRemoveGameDialog: Boolean = false,
    val gameToRemove: CollectionGameUi? = null,
    val isPinned: Boolean = false,
    val sectionLabels: List<String> = emptyList(),
    val currentSectionLabel: String = "",
    val isSearchActive: Boolean = false,
    val searchQuery: String = "",
    val isSearchKeyboardOpen: Boolean = false,
    val isSearchStopFocused: Boolean = false,
    val overlayLetter: String? = null,
    val optionsRunning: com.nendo.argosy.ui.common.RunningGameUi? = null,
    val optionsMoveTargets: List<com.nendo.argosy.ui.screens.running.RunningMoveTarget> = emptyList()
) {
    val focusedGame: CollectionGameUi?
        get() = if (isSearchStopFocused) null else games.getOrNull(focusedIndex)

    val downloadableGamesCount: Int
        get() = games.count { !it.isDownloaded && it.rommId != null }

    val optionRows: List<com.nendo.argosy.ui.screens.collections.dialogs.CollectionOptionRow>
        get() = com.nendo.argosy.ui.screens.collections.dialogs.collectionOptionRows(
            showDownloadAll = true,
            downloadableCount = downloadableGamesCount,
            showRemoveGame = focusedGame != null,
            moveTargets = optionsMoveTargets.takeIf { optionsRunning != null }.orEmpty(),
            showCloseGame = optionsRunning != null
        )

    val showSectionSidebar: Boolean
        get() = !isSearchActive && sectionLabels.size > 1
}

@HiltViewModel
class CollectionDetailViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val collectionRepository: CollectionRepository,
    private val platformRepository: PlatformRepository,
    private val romMRepository: RomMRepository,
    private val isPinnedUseCase: IsPinnedUseCase,
    private val pinCollectionUseCase: PinCollectionUseCase,
    private val unpinCollectionUseCase: UnpinCollectionUseCase,
    private val refreshAllCollectionsUseCase: RefreshAllCollectionsUseCase,
    private val downloadGameUseCase: DownloadGameUseCase,
    private val notificationManager: NotificationManager,
    private val positions: VirtualBrowsePositions,
    private val downloadFileStatusRepository: DownloadFileStatusRepository,
    showcaseSource: com.nendo.argosy.ui.common.PresentationShowcaseSource,
    val runningGameMenu: com.nendo.argosy.ui.screens.running.RunningGameMenu
) : ViewModel() {

    private val presentation = com.nendo.argosy.ui.common.PresentationFocus(
        "collections.detail",
        this,
        showcaseSource,
        viewModelScope
    )

    private val collectionId: Long = checkNotNull(savedStateHandle["collectionId"])
    private val stickyKey = "col:$collectionId"

    private data class ModalState(
        val focusedIndex: Int = 0,
        val showOptionsModal: Boolean = false,
        val optionsModalFocusIndex: Int = 0,
        val showEditDialog: Boolean = false,
        val showDeleteDialog: Boolean = false,
        val showRemoveGameDialog: Boolean = false,
        val gameToRemove: CollectionGameUi? = null,
        val isPinned: Boolean = false,
        val isRefreshing: Boolean = false,
        val searchActive: Boolean = false,
        val searchQuery: String = "",
        val searchKeyboardOpen: Boolean = false,
        val searchStopFocused: Boolean = false,
        val optionsRunning: com.nendo.argosy.ui.common.RunningGameUi? = null,
        val optionsMoveTargets: List<com.nendo.argosy.ui.screens.running.RunningMoveTarget> = emptyList()
    )

    private val _modalState = MutableStateFlow(ModalState(focusedIndex = positions.get(stickyKey)))
    private val _refreshTrigger = MutableStateFlow(0)
    private val _overlayLetter = MutableStateFlow<String?>(null)
    private var preSearchIndex = 0
    private var preSearchStopFocused = false
    private var lastRefreshTime = 0L
    private var overlayJob: Job? = null

    companion object {
        private const val REFRESH_DEBOUNCE_MS = 30_000L
    }

    init {
        loadPinStatus()
    }

    private fun loadPinStatus() {
        viewModelScope.launch {
            val isPinned = isPinnedUseCase.isRegularPinned(collectionId)
            _modalState.update { it.copy(isPinned = isPinned) }
        }
    }

    private class VisibleGames(
        val collection: CollectionEntity?,
        val games: List<CollectionGameUi>,
        val sectionLabelByGame: List<String>
    ) {
        val sectionLabels: List<String> = sectionLabelByGame.distinct()
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val visibleGames = _refreshTrigger.flatMapLatest {
        combine(
            collectionRepository.observeCollectionById(collectionId),
            collectionRepository.observeGamesInCollection(collectionId),
            platformRepository.observeAllPlatforms(),
            _modalState.map { it.searchQuery }.distinctUntilChanged()
        ) { collection, games, platforms, searchQuery ->
            val platformMap = platforms.associate { it.id to it.getDisplayName() }
            val sorted = games
                .map { game ->
                    game.toCollectionGameUi(
                        platformDisplayName = platformMap[game.platformId]
                            ?: context.getString(R.string.collections_detail_platform_unknown),
                        downloadStatus = downloadFileStatusRepository
                    )
                }
                .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
            val visible = if (searchQuery.isBlank()) {
                sorted
            } else {
                val q = com.nendo.argosy.util.SearchNormalizer.normalize(searchQuery)
                sorted.filter { com.nendo.argosy.util.SearchNormalizer.normalize(it.title).contains(q) }
            }
            VisibleGames(collection, visible, visible.map { sectionLabelFor(it.title) })
        }
    }.flowOn(Dispatchers.Default)

    private val core = combine(visibleGames, _modalState) { listing, modalState ->
        val visible = listing.games
        val clamped = modalState.focusedIndex.coerceIn(0, (visible.size - 1).coerceAtLeast(0))
        CollectionDetailUiState(
            collection = listing.collection,
            games = visible,
            isLoading = false,
            isRefreshing = modalState.isRefreshing,
            focusedIndex = clamped,
            showOptionsModal = modalState.showOptionsModal,
            optionsModalFocusIndex = modalState.optionsModalFocusIndex,
            showEditDialog = modalState.showEditDialog,
            showDeleteDialog = modalState.showDeleteDialog,
            showRemoveGameDialog = modalState.showRemoveGameDialog,
            gameToRemove = modalState.gameToRemove,
            isPinned = modalState.isPinned,
            sectionLabels = if (modalState.searchActive) emptyList() else listing.sectionLabels,
            currentSectionLabel = listing.sectionLabelByGame.getOrNull(clamped) ?: "",
            isSearchActive = modalState.searchActive,
            searchQuery = modalState.searchQuery,
            isSearchKeyboardOpen = modalState.searchKeyboardOpen,
            isSearchStopFocused = modalState.searchStopFocused,
            optionsRunning = modalState.optionsRunning,
            optionsMoveTargets = modalState.optionsMoveTargets
        )
    }

    val uiState: StateFlow<CollectionDetailUiState> = combine(core, _overlayLetter) { state, overlay ->
        state.copy(overlayLetter = overlay)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CollectionDetailUiState()
    )

    init {
        viewModelScope.launch {
            uiState
                .map { state ->
                    state.focusedGame?.let { com.nendo.argosy.ui.common.PresentationTarget.Game(it.id) }
                        ?: state.collection?.let { collection ->
                            com.nendo.argosy.ui.common.PresentationTarget.GameSet(
                                "collection:$collectionId",
                                collection.name
                            ) { collectionRepository.getGameIdsInCollection(collectionId) }
                        }
                        ?: com.nendo.argosy.ui.common.PresentationTarget.Nothing
                }
                .distinctUntilChanged()
                .collect { presentation.show(it) }
        }
    }

    fun republishCompanionDetail() = presentation.startDescribing()

    fun clearCompanionDetail() = presentation.stopDescribing()

    private fun sectionLabelFor(name: String): String {
        val first = name.trim().firstOrNull()?.uppercaseChar() ?: '#'
        return if (first in 'A'..'Z') first.toString() else "#"
    }

    private fun hasDialogOpen(state: CollectionDetailUiState): Boolean =
        state.showEditDialog || state.showDeleteDialog || state.showRemoveGameDialog

    private fun setFocus(index: Int) {
        _modalState.update { it.copy(focusedIndex = index, searchStopFocused = false) }
        if (!_modalState.value.searchActive) positions.set(stickyKey, index)
    }

    fun moveUp() {
        val state = uiState.value
        when {
            state.isSearchStopFocused -> Unit
            state.focusedIndex > 0 -> setFocus(state.focusedIndex - 1)
            else -> _modalState.update { it.copy(searchStopFocused = true) }
        }
    }

    fun moveDown() {
        val state = uiState.value
        when {
            state.isSearchStopFocused -> if (state.games.isNotEmpty()) setFocus(0)
            state.focusedIndex < state.games.size - 1 -> setFocus(state.focusedIndex + 1)
        }
    }

    fun handleGameLongPress(index: Int) {
        val state = uiState.value
        if (hasDialogOpen(state) || state.showOptionsModal) return
        if (index !in state.games.indices) return
        setFocus(index)
        showOptionsModal()
    }

    fun jumpToSection(label: String) {
        val state = uiState.value
        val target = state.games.indexOfFirst { sectionLabelFor(it.title) == label }
        if (target < 0) return
        setFocus(target)
        showLetterOverlay(label)
    }

    fun jumpToNextSection() {
        val labels = uiState.value.sectionLabels
        if (labels.isEmpty()) return
        val current = labels.indexOf(uiState.value.currentSectionLabel)
        val next = if (current < 0 || current >= labels.lastIndex) 0 else current + 1
        jumpToSection(labels[next])
    }

    fun jumpToPreviousSection() {
        val labels = uiState.value.sectionLabels
        if (labels.isEmpty()) return
        val current = labels.indexOf(uiState.value.currentSectionLabel)
        val prev = if (current <= 0) labels.lastIndex else current - 1
        jumpToSection(labels[prev])
    }

    private fun showLetterOverlay(label: String) {
        overlayJob?.cancel()
        _overlayLetter.value = label
        overlayJob = viewModelScope.launch {
            delay(600)
            _overlayLetter.value = null
        }
    }

    fun openSearch() {
        if (_modalState.value.searchActive) {
            _modalState.update { it.copy(searchKeyboardOpen = true, searchStopFocused = true) }
            return
        }
        preSearchIndex = uiState.value.focusedIndex
        preSearchStopFocused = uiState.value.isSearchStopFocused
        _modalState.update {
            it.copy(
                searchActive = true,
                searchQuery = "",
                searchKeyboardOpen = true,
                searchStopFocused = true,
                focusedIndex = 0
            )
        }
    }

    fun closeSearchKeyboard() {
        _modalState.update { it.copy(searchKeyboardOpen = false) }
    }

    fun closeSearch() {
        if (!_modalState.value.searchActive) return
        _modalState.update {
            it.copy(
                searchActive = false,
                searchQuery = "",
                searchKeyboardOpen = false,
                searchStopFocused = preSearchStopFocused,
                focusedIndex = preSearchIndex
            )
        }
    }

    fun setSearchQuery(query: String) {
        _modalState.update { it.copy(searchQuery = query, focusedIndex = 0) }
    }

    private var optionsOpenJob: Job? = null

    fun showOptionsModal() {
        if (optionsOpenJob?.isActive == true) return
        val gameId = uiState.value.games.getOrNull(_modalState.value.focusedIndex)?.id
        optionsOpenJob = viewModelScope.launch {
            val running = gameId?.let { runningGameMenu.markFor(it) }
            val moveTargets = runningGameMenu.moveTargets(running)
            _modalState.update {
                it.copy(
                    showOptionsModal = true,
                    optionsModalFocusIndex = 0,
                    optionsRunning = running,
                    optionsMoveTargets = moveTargets
                )
            }
        }
    }

    fun hideOptionsModal() {
        optionsOpenJob?.cancel()
        _modalState.update { it.copy(showOptionsModal = false) }
    }

    fun moveOptionsFocus(delta: Int) {
        val maxIndex = (uiState.value.optionRows.size - 1).coerceAtLeast(0)
        val currentIndex = _modalState.value.optionsModalFocusIndex
        val newIndex = (currentIndex + delta).coerceIn(0, maxIndex)
        _modalState.update { it.copy(optionsModalFocusIndex = newIndex) }
    }

    fun selectOption(option: CollectionOption) {
        val state = uiState.value
        hideOptionsModal()
        when (option) {
            CollectionOption.DOWNLOAD_ALL -> downloadAllGames()
            CollectionOption.RENAME -> showEditDialog()
            CollectionOption.DELETE -> showDeleteDialog()
            CollectionOption.REMOVE_GAME -> showRemoveGameDialog()
            CollectionOption.CLOSE_GAME -> runningGameMenu.requestClose(state.optionsRunning, state.focusedGame?.title)
        }
    }

    fun moveRunningGame(target: com.nendo.argosy.ui.screens.running.RunningMoveTarget) {
        val state = uiState.value
        hideOptionsModal()
        val running = state.optionsRunning ?: return
        val title = state.focusedGame?.title ?: return
        runningGameMenu.moveTo(viewModelScope, running, target, title)
    }

    fun confirmOptionSelection() {
        val state = uiState.value
        when (val row = state.optionRows.getOrNull(_modalState.value.optionsModalFocusIndex)) {
            is com.nendo.argosy.ui.screens.collections.dialogs.CollectionOptionRow.Action -> selectOption(row.option)
            is com.nendo.argosy.ui.screens.collections.dialogs.CollectionOptionRow.MoveTo -> moveRunningGame(row.target)
            null -> Unit
        }
    }

    fun downloadAllGames() {
        val state = uiState.value
        val downloadable = state.games.filter { !it.isDownloaded && it.rommId != null }
        if (downloadable.isEmpty()) return

        viewModelScope.launch {
            var queued = 0
            for (game in downloadable) {
                downloadGameUseCase(game.id)
                queued++
            }
            notificationManager.show(
                title = NotificationText.Res(R.string.notif_collection_downloads_queued_title),
                subtitle = NotificationText.Plural(
                    R.plurals.notif_collection_downloads_queued_subtitle,
                    queued,
                    listOf(queued)
                ),
                type = NotificationType.INFO,
                duration = NotificationDuration.MEDIUM
            )
        }
    }

    fun showEditDialog() {
        _modalState.update { it.copy(showEditDialog = true) }
    }

    fun hideEditDialog() {
        _modalState.update { it.copy(showEditDialog = false) }
    }

    fun showDeleteDialog() {
        _modalState.update { it.copy(showDeleteDialog = true) }
    }

    fun hideDeleteDialog() {
        _modalState.update { it.copy(showDeleteDialog = false) }
    }

    fun showRemoveGameDialog() {
        val game = uiState.value.focusedGame ?: return
        _modalState.update {
            it.copy(
                gameToRemove = game,
                showRemoveGameDialog = true
            )
        }
    }

    fun hideRemoveGameDialog() {
        _modalState.update {
            it.copy(
                showRemoveGameDialog = false,
                gameToRemove = null
            )
        }
    }

    fun confirmRemoveGame() {
        val game = _modalState.value.gameToRemove ?: return
        viewModelScope.launch {
            romMRepository.removeGameFromCollectionWithSync(game.id, collectionId)
            hideRemoveGameDialog()
            _refreshTrigger.value++
        }
    }

    fun updateCollectionName(name: String) {
        viewModelScope.launch {
            romMRepository.updateCollectionWithSync(collectionId, name, null)
            hideEditDialog()
        }
    }

    fun deleteCollection(onDeleted: () -> Unit) {
        viewModelScope.launch {
            romMRepository.deleteCollectionWithSync(collectionId)
            hideDeleteDialog()
            onDeleted()
        }
    }

    fun removeGameFromCollection(gameId: Long) {
        viewModelScope.launch {
            romMRepository.removeGameFromCollectionWithSync(gameId, collectionId)
        }
    }

    fun togglePin() {
        viewModelScope.launch {
            val isPinned = _modalState.value.isPinned
            if (isPinned) {
                unpinCollectionUseCase.unpinRegular(collectionId)
            } else {
                pinCollectionUseCase.pinRegular(collectionId)
            }
            _modalState.update { it.copy(isPinned = !isPinned) }
        }
    }

    fun refresh() {
        val now = System.currentTimeMillis()
        if (now - lastRefreshTime < REFRESH_DEBOUNCE_MS) return
        if (_modalState.value.isRefreshing) return

        lastRefreshTime = now
        viewModelScope.launch {
            _modalState.update { it.copy(isRefreshing = true) }
            refreshAllCollectionsUseCase()
            _modalState.update { it.copy(isRefreshing = false) }
            _refreshTrigger.value++
        }
    }

    fun createInputHandler(
        onBack: () -> Unit,
        onGameClick: (Long) -> Unit
    ): InputHandler = object : InputHandler {
        override fun onUp(): InputResult {
            val state = uiState.value
            if (hasDialogOpen(state)) return InputResult.UNHANDLED
            when {
                state.showOptionsModal -> {
                    moveOptionsFocus(-1)
                    return InputResult.HANDLED
                }
                else -> {
                    moveUp()
                    return InputResult.HANDLED
                }
            }
        }

        override fun onDown(): InputResult {
            val state = uiState.value
            if (hasDialogOpen(state)) return InputResult.UNHANDLED
            when {
                state.showOptionsModal -> {
                    moveOptionsFocus(1)
                    return InputResult.HANDLED
                }
                else -> {
                    moveDown()
                    return InputResult.HANDLED
                }
            }
        }

        override fun onConfirm(): InputResult {
            val state = uiState.value
            if (hasDialogOpen(state)) return InputResult.UNHANDLED
            when {
                state.showOptionsModal -> {
                    confirmOptionSelection()
                    return InputResult.HANDLED
                }
                state.isSearchStopFocused -> {
                    openSearch()
                    return InputResult.HANDLED
                }
                else -> {
                    state.focusedGame?.let { onGameClick(it.id) }
                    return InputResult.HANDLED
                }
            }
        }

        override fun onBack(): InputResult {
            val state = uiState.value
            when {
                state.showEditDialog -> {
                    hideEditDialog()
                    return InputResult.HANDLED
                }
                state.showDeleteDialog -> {
                    hideDeleteDialog()
                    return InputResult.HANDLED
                }
                state.showRemoveGameDialog -> {
                    hideRemoveGameDialog()
                    return InputResult.HANDLED
                }
                state.showOptionsModal -> {
                    hideOptionsModal()
                    return InputResult.HANDLED
                }
                state.isSearchActive -> {
                    closeSearch()
                    return InputResult.HANDLED
                }
                else -> {
                    onBack()
                    return InputResult.HANDLED
                }
            }
        }

        override fun onContextMenu(): InputResult {
            val state = uiState.value
            if (hasDialogOpen(state) || state.showOptionsModal) return InputResult.HANDLED
            openSearch()
            return InputResult.HANDLED
        }

        override fun onMenu(): InputResult {
            val state = uiState.value
            if (hasDialogOpen(state) || state.showOptionsModal) return InputResult.HANDLED
            refresh()
            return InputResult.HANDLED
        }

        override fun onPrevTrigger(): InputResult {
            val state = uiState.value
            if (hasDialogOpen(state) || state.showOptionsModal) return InputResult.HANDLED
            if (state.sectionLabels.isEmpty()) return InputResult.UNHANDLED
            jumpToPreviousSection()
            return InputResult.HANDLED
        }

        override fun onNextTrigger(): InputResult {
            val state = uiState.value
            if (hasDialogOpen(state) || state.showOptionsModal) return InputResult.HANDLED
            if (state.sectionLabels.isEmpty()) return InputResult.UNHANDLED
            jumpToNextSection()
            return InputResult.HANDLED
        }

        override fun onSelect(): InputResult {
            val state = uiState.value
            if (hasDialogOpen(state)) return InputResult.UNHANDLED
            if (state.showOptionsModal) return InputResult.HANDLED
            if (com.nendo.argosy.ui.dualscreen.selectSwapsRoles()) return InputResult.UNHANDLED
            showOptionsModal()
            return InputResult.HANDLED
        }

        override fun onLongConfirm(): InputResult {
            val state = uiState.value
            if (hasDialogOpen(state)) return InputResult.UNHANDLED
            if (state.showOptionsModal) return InputResult.HANDLED
            showOptionsModal()
            return InputResult.HANDLED
        }

        override fun onSecondaryAction(): InputResult {
            val state = uiState.value
            if (hasDialogOpen(state) || state.showOptionsModal) return InputResult.HANDLED
            togglePin()
            return InputResult.HANDLED
        }

        private fun heldByOptions(): InputResult =
            if (uiState.value.showOptionsModal) InputResult.HANDLED else InputResult.UNHANDLED

        override fun onLeft(): InputResult = heldByOptions()

        override fun onRight(): InputResult = heldByOptions()

        override fun onPrevSection(): InputResult = heldByOptions()

        override fun onNextSection(): InputResult = heldByOptions()

        override fun onLongSelect(): InputResult = heldByOptions()

        override fun onLeftStickClick(): InputResult = heldByOptions()

        override fun onRightStickClick(): InputResult = heldByOptions()

        override fun onRightStickUp(): InputResult = heldByOptions()

        override fun onRightStickDown(): InputResult = heldByOptions()

        override fun onRightStickLeft(): InputResult = heldByOptions()

        override fun onRightStickRight(): InputResult = heldByOptions()
    }
}
