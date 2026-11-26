package com.nendo.argosy.ui.screens.collections

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.data.repository.CollectionRepository
import com.nendo.argosy.data.local.entity.CollectionEntity
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
import com.nendo.argosy.ui.screens.common.DownloadIndicatorSource
import com.nendo.argosy.ui.screens.home.GameDownloadIndicator
import com.nendo.argosy.ui.screens.library.FilterMemoryScope
import com.nendo.argosy.ui.screens.library.FocusMove
import com.nendo.argosy.ui.screens.library.LibraryGameUi
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CollectionDetailUiState(
    val collection: CollectionEntity? = null,
    val listing: GameSetListing = GameSetListing(),
    val layout: GameSetLayout = GameSetLayout(),
    val filter: GameSetFilterUi = GameSetFilterUi(),
    val isRefreshing: Boolean = false,
    val focusedIndex: Int = 0,
    val scrollTrigger: Int = 0,
    val showOptionsModal: Boolean = false,
    val optionsModalFocusIndex: Int = 0,
    val showEditDialog: Boolean = false,
    val showDeleteDialog: Boolean = false,
    val showRemoveGameDialog: Boolean = false,
    val gameToRemove: LibraryGameUi? = null,
    val isPinned: Boolean = false,
    val overlayLetter: String = "",
    val showLetterOverlay: Boolean = false,
    val optionsRunning: com.nendo.argosy.ui.common.RunningGameUi? = null,
    val optionsMoveTargets: List<com.nendo.argosy.ui.screens.running.RunningMoveTarget> = emptyList()
) {
    val games: List<LibraryGameUi>
        get() = listing.listing.games

    val focusedGame: LibraryGameUi?
        get() = games.getOrNull(focusedIndex)

    val sectionLabels: List<String>
        get() = listing.listing.sectionLabels

    val downloadableGamesCount: Int
        get() = games.count { !it.isDownloaded && it.id in listing.rommGameIds }

    val optionRows: List<com.nendo.argosy.ui.screens.collections.dialogs.CollectionOptionRow>
        get() = com.nendo.argosy.ui.screens.collections.dialogs.collectionOptionRows(
            showDownloadAll = true,
            downloadableCount = downloadableGamesCount,
            showRemoveGame = focusedGame != null,
            moveTargets = optionsMoveTargets.takeIf { optionsRunning != null }.orEmpty(),
            showCloseGame = optionsRunning != null
        )
}

@HiltViewModel
class CollectionDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val collectionRepository: CollectionRepository,
    private val romMRepository: RomMRepository,
    private val isPinnedUseCase: IsPinnedUseCase,
    private val pinCollectionUseCase: PinCollectionUseCase,
    private val unpinCollectionUseCase: UnpinCollectionUseCase,
    private val refreshAllCollectionsUseCase: RefreshAllCollectionsUseCase,
    private val downloadGameUseCase: DownloadGameUseCase,
    private val notificationManager: NotificationManager,
    private val positions: VirtualBrowsePositions,
    browserDependencies: GameSetBrowser.Dependencies,
    downloadIndicatorSource: DownloadIndicatorSource,
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

    val browser = browserDependencies.create(viewModelScope, FilterMemoryScope.collection(collectionId))

    val downloadIndicators: StateFlow<Map<Long, GameDownloadIndicator>> = downloadIndicatorSource.indicators

    private data class ModalState(
        val focusedIndex: Int = 0,
        val scrollTrigger: Int = 0,
        val showOptionsModal: Boolean = false,
        val optionsModalFocusIndex: Int = 0,
        val showEditDialog: Boolean = false,
        val showDeleteDialog: Boolean = false,
        val showRemoveGameDialog: Boolean = false,
        val gameToRemove: LibraryGameUi? = null,
        val isPinned: Boolean = false,
        val isRefreshing: Boolean = false,
        val optionsRunning: com.nendo.argosy.ui.common.RunningGameUi? = null,
        val optionsMoveTargets: List<com.nendo.argosy.ui.screens.running.RunningMoveTarget> = emptyList()
    )

    private val _modalState = MutableStateFlow(ModalState(focusedIndex = positions.get(stickyKey)))
    private val _refreshTrigger = MutableStateFlow(0)
    private val _overlayLetter = MutableStateFlow("")
    private val _overlayVisible = MutableStateFlow(false)
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

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val visibleGames = _refreshTrigger.flatMapLatest {
        combine(
            collectionRepository.observeCollectionById(collectionId),
            browser.listing(collectionRepository.observeGamesInCollection(collectionId))
        ) { collection, listing -> collection to listing }
    }

    private val core = combine(
        visibleGames,
        _modalState,
        browser.filter,
        browser.layout
    ) { (collection, listing), modalState, filter, layout ->
        val clamped = modalState.focusedIndex.coerceIn(0, (listing.listing.games.size - 1).coerceAtLeast(0))
        CollectionDetailUiState(
            collection = collection,
            listing = listing,
            layout = layout,
            filter = filter,
            isRefreshing = modalState.isRefreshing,
            focusedIndex = clamped,
            scrollTrigger = modalState.scrollTrigger,
            showOptionsModal = modalState.showOptionsModal,
            optionsModalFocusIndex = modalState.optionsModalFocusIndex,
            showEditDialog = modalState.showEditDialog,
            showDeleteDialog = modalState.showDeleteDialog,
            showRemoveGameDialog = modalState.showRemoveGameDialog,
            gameToRemove = modalState.gameToRemove,
            isPinned = modalState.isPinned,
            optionsRunning = modalState.optionsRunning,
            optionsMoveTargets = modalState.optionsMoveTargets
        )
    }

    val uiState: StateFlow<CollectionDetailUiState> = combine(
        core,
        _overlayLetter,
        _overlayVisible
    ) { state, letter, visible ->
        state.copy(overlayLetter = letter, showLetterOverlay = visible)
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

    private fun hasDialogOpen(state: CollectionDetailUiState): Boolean =
        state.showEditDialog || state.showDeleteDialog || state.showRemoveGameDialog

    private fun setFocus(index: Int, jump: Boolean = false) {
        _modalState.update {
            it.copy(focusedIndex = index, scrollTrigger = if (jump) it.scrollTrigger + 1 else it.scrollTrigger)
        }
        positions.set(stickyKey, index)
    }

    fun moveFocus(direction: FocusMove): Boolean {
        val state = uiState.value
        val target = browser.moveFocus(
            direction,
            state.focusedIndex,
            state.listing.listing,
            state.layout.columnsCount
        ) ?: return false
        setFocus(target)
        return true
    }

    fun handleItemTap(index: Int, onGameClick: (Long) -> Unit) {
        val state = uiState.value
        val game = state.games.getOrNull(index) ?: return
        if (index == state.focusedIndex) {
            onGameClick(game.id)
            return
        }
        browser.resetStickyColumn()
        setFocus(index)
        browser.playNavigateSound()
    }

    fun handleGameLongPress(index: Int) {
        val state = uiState.value
        if (hasDialogOpen(state) || state.showOptionsModal) return
        if (index !in state.games.indices) return
        browser.resetStickyColumn()
        setFocus(index)
        showOptionsModal()
    }

    fun jumpToSection(label: String) {
        val target = uiState.value.listing.listing.firstGameIndexOfSection(label) ?: return
        browser.resetStickyColumn()
        setFocus(target, jump = true)
        showLetterOverlay(label)
    }

    private fun jumpToAdjacentSection(direction: Int) {
        val state = uiState.value
        val labels = state.sectionLabels
        if (labels.isEmpty()) return
        val current = labels.indexOf(state.listing.listing.sectionLabelForGameIndex(state.focusedIndex))
        val target = if (current < 0) 0 else (current + direction).mod(labels.size)
        jumpToSection(labels[target])
    }

    private fun showLetterOverlay(label: String) {
        overlayJob?.cancel()
        _overlayLetter.value = label
        _overlayVisible.value = true
        overlayJob = viewModelScope.launch {
            delay(600)
            _overlayVisible.value = false
        }
    }

    private var optionsOpenJob: Job? = null

    fun showOptionsModal() {
        if (optionsOpenJob?.isActive == true) return
        val gameId = uiState.value.focusedGame?.id
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
        _modalState.update { it.copy(optionsModalFocusIndex = (it.optionsModalFocusIndex + delta).coerceIn(0, maxIndex)) }
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
        val downloadable = state.games.filter { !it.isDownloaded && it.id in state.listing.rommGameIds }
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
        _modalState.update { it.copy(gameToRemove = game, showRemoveGameDialog = true) }
    }

    fun hideRemoveGameDialog() {
        _modalState.update { it.copy(showRemoveGameDialog = false, gameToRemove = null) }
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
    ): InputHandler = browser.routeInput(object : InputHandler {
        private fun move(direction: FocusMove): InputResult {
            val state = uiState.value
            if (hasDialogOpen(state)) return InputResult.UNHANDLED
            if (state.showOptionsModal) {
                when (direction) {
                    FocusMove.UP -> moveOptionsFocus(-1)
                    FocusMove.DOWN -> moveOptionsFocus(1)
                    FocusMove.LEFT, FocusMove.RIGHT -> Unit
                }
                return InputResult.HANDLED
            }
            return if (moveFocus(direction)) InputResult.HANDLED else InputResult.UNHANDLED
        }

        override fun onUp(): InputResult = move(FocusMove.UP)

        override fun onDown(): InputResult = move(FocusMove.DOWN)

        override fun onLeft(): InputResult = move(FocusMove.LEFT)

        override fun onRight(): InputResult = move(FocusMove.RIGHT)

        override fun onConfirm(): InputResult {
            val state = uiState.value
            if (hasDialogOpen(state)) return InputResult.UNHANDLED
            if (state.showOptionsModal) {
                confirmOptionSelection()
                return InputResult.HANDLED
            }
            state.focusedGame?.let { onGameClick(it.id) }
            return InputResult.HANDLED
        }

        override fun onBack(): InputResult {
            val state = uiState.value
            when {
                state.showEditDialog -> hideEditDialog()
                state.showDeleteDialog -> hideDeleteDialog()
                state.showRemoveGameDialog -> hideRemoveGameDialog()
                state.showOptionsModal -> hideOptionsModal()
                else -> onBack()
            }
            return InputResult.HANDLED
        }

        override fun onContextMenu(): InputResult {
            val state = uiState.value
            if (hasDialogOpen(state) || state.showOptionsModal) return InputResult.HANDLED
            browser.toggleFilterMenu()
            return InputResult.HANDLED
        }

        override fun onMenu(): InputResult {
            val state = uiState.value
            if (hasDialogOpen(state) || state.showOptionsModal) return InputResult.HANDLED
            refresh()
            return InputResult.HANDLED
        }

        override fun onPrevTrigger(): InputResult = sectionJump(-1)

        override fun onNextTrigger(): InputResult = sectionJump(1)

        private fun sectionJump(direction: Int): InputResult {
            val state = uiState.value
            if (hasDialogOpen(state) || state.showOptionsModal) return InputResult.HANDLED
            if (state.sectionLabels.isEmpty()) return InputResult.UNHANDLED
            jumpToAdjacentSection(direction)
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

        override fun onPrevSection(): InputResult = heldByOptions()

        override fun onNextSection(): InputResult = heldByOptions()

        override fun onLongSelect(): InputResult = heldByOptions()

        override fun onLeftStickClick(): InputResult = heldByOptions()

        override fun onRightStickClick(): InputResult = heldByOptions()

        override fun onRightStickUp(): InputResult = heldByOptions()

        override fun onRightStickDown(): InputResult = heldByOptions()

        override fun onRightStickLeft(): InputResult = heldByOptions()

        override fun onRightStickRight(): InputResult = heldByOptions()
    })
}
