package com.nendo.argosy.ui.screens.collections

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.domain.usecase.collection.CategoryType
import com.nendo.argosy.domain.usecase.collection.GetGamesByCategoryUseCase
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
import com.nendo.argosy.ui.common.RunningGameUi
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.screens.collections.dialogs.CollectionOption
import com.nendo.argosy.ui.screens.collections.dialogs.CollectionOptionRow
import com.nendo.argosy.ui.screens.collections.dialogs.collectionOptionRows
import com.nendo.argosy.ui.screens.common.DownloadIndicatorSource
import com.nendo.argosy.ui.screens.home.GameDownloadIndicator
import com.nendo.argosy.ui.screens.library.FilterMemoryScope
import com.nendo.argosy.ui.screens.library.FocusMove
import com.nendo.argosy.ui.screens.library.LibraryGameUi
import com.nendo.argosy.ui.screens.running.RunningGameMenu
import com.nendo.argosy.ui.screens.running.RunningMoveTarget
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.URLDecoder
import javax.inject.Inject

data class DownloadAllProgress(
    val isActive: Boolean = false,
    val currentIndex: Int = 0,
    val totalCount: Int = 0,
    val isOnCooldown: Boolean = false
)

data class VirtualCategoryUiState(
    val type: String = "",
    val categoryName: String = "",
    val listing: GameSetListing = GameSetListing(),
    val layout: GameSetLayout = GameSetLayout(),
    val filter: GameSetFilterUi = GameSetFilterUi(),
    val isRefreshing: Boolean = false,
    val focusedIndex: Int = 0,
    val scrollTrigger: Int = 0,
    val isPinned: Boolean = false,
    val downloadAllProgress: DownloadAllProgress = DownloadAllProgress(),
    val overlayLetter: String = "",
    val showLetterOverlay: Boolean = false,
    val showOptionsModal: Boolean = false,
    val optionsModalFocusIndex: Int = 0,
    val optionsRunning: RunningGameUi? = null,
    val optionsMoveTargets: List<RunningMoveTarget> = emptyList()
) {
    val games: List<LibraryGameUi>
        get() = listing.listing.games

    val focusedGame: LibraryGameUi?
        get() = games.getOrNull(focusedIndex)

    val sectionLabels: List<String>
        get() = listing.listing.sectionLabels

    val downloadableGamesCount: Int
        get() = games.count { !it.isDownloaded && it.id in listing.rommGameIds }

    val canDownloadAll: Boolean
        get() = downloadableGamesCount > 0 &&
                !downloadAllProgress.isActive &&
                !downloadAllProgress.isOnCooldown

    val optionRows: List<CollectionOptionRow>
        get() = optionRowsFor(optionsRunning, optionsMoveTargets)

    fun optionRowsFor(
        running: RunningGameUi?,
        moveTargets: List<RunningMoveTarget> = emptyList()
    ): List<CollectionOptionRow> = collectionOptionRows(
        showDownloadAll = canDownloadAll,
        downloadableCount = downloadableGamesCount,
        editable = false,
        moveTargets = moveTargets.takeIf { running != null }.orEmpty(),
        showCloseGame = running != null
    )
}

private data class CategoryOptionsState(
    val visible: Boolean = false,
    val focusIndex: Int = 0,
    val running: RunningGameUi? = null,
    val moveTargets: List<RunningMoveTarget> = emptyList()
)

private data class CategoryFocus(
    val index: Int = 0,
    val scrollTrigger: Int = 0
)

private data class CategoryFlags(
    val isPinned: Boolean,
    val isRefreshing: Boolean,
    val downloadProgress: DownloadAllProgress,
    val overlayLetter: String,
    val overlayVisible: Boolean
)

@HiltViewModel
class VirtualCategoryViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    getGamesByCategoryUseCase: GetGamesByCategoryUseCase,
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
    val runningGameMenu: RunningGameMenu
) : ViewModel() {

    private val presentation = com.nendo.argosy.ui.common.PresentationFocus(
        "collections.category",
        this,
        showcaseSource,
        viewModelScope
    )

    private val type: String = checkNotNull(savedStateHandle["type"])
    private val category: String = URLDecoder.decode(checkNotNull(savedStateHandle["category"]), "UTF-8")
    private val stickyKey = "cat:$type:$category"

    val browser = browserDependencies.create(viewModelScope, FilterMemoryScope.category(type, category))

    val downloadIndicators: StateFlow<Map<Long, GameDownloadIndicator>> = downloadIndicatorSource.indicators

    private val _focus = MutableStateFlow(CategoryFocus(index = positions.get(stickyKey)))
    private val _isPinned = MutableStateFlow(false)
    private val _isRefreshing = MutableStateFlow(false)
    private val _downloadAllProgress = MutableStateFlow(DownloadAllProgress())
    private val _options = MutableStateFlow(CategoryOptionsState())
    private var optionsOpenJob: Job? = null
    private val _overlayLetter = MutableStateFlow("")
    private val _overlayVisible = MutableStateFlow(false)
    private var lastRefreshTime = 0L
    private var overlayJob: Job? = null

    companion object {
        private const val REFRESH_DEBOUNCE_MS = 30_000L
        private const val DOWNLOAD_ALL_COOLDOWN_MS = 180_000L
    }

    private val categoryType = when (type) {
        "genres" -> CategoryType.GENRE
        "modes" -> CategoryType.GAME_MODE
        "series" -> CategoryType.SERIES
        else -> CategoryType.GENRE
    }

    init {
        loadPinStatus()
    }

    private fun loadPinStatus() {
        viewModelScope.launch {
            _isPinned.value = isPinnedUseCase.isVirtualPinned(categoryType, category)
        }
    }

    private val flags = combine(
        _isPinned,
        _isRefreshing,
        _downloadAllProgress,
        _overlayLetter,
        _overlayVisible
    ) { isPinned, isRefreshing, progress, letter, visible ->
        CategoryFlags(isPinned, isRefreshing, progress, letter, visible)
    }

    private val core = combine(
        browser.listing(getGamesByCategoryUseCase(categoryType, category)),
        combine(browser.filter, browser.layout) { filter, layout -> filter to layout },
        _focus,
        flags,
        _options
    ) { listing, (filter, layout), focus, flags, options ->
        val clamped = focus.index.coerceIn(0, (listing.listing.games.size - 1).coerceAtLeast(0))
        VirtualCategoryUiState(
            type = type,
            categoryName = category,
            listing = listing,
            layout = layout,
            filter = filter,
            isRefreshing = flags.isRefreshing,
            focusedIndex = clamped,
            scrollTrigger = focus.scrollTrigger,
            isPinned = flags.isPinned,
            downloadAllProgress = flags.downloadProgress,
            overlayLetter = flags.overlayLetter,
            showLetterOverlay = flags.overlayVisible,
            showOptionsModal = options.visible,
            optionsModalFocusIndex = options.focusIndex,
            optionsRunning = options.running,
            optionsMoveTargets = options.moveTargets
        )
    }

    val uiState: StateFlow<VirtualCategoryUiState> = core.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = VirtualCategoryUiState(type = type, categoryName = category)
    )

    init {
        viewModelScope.launch {
            uiState
                .map { state ->
                    state.focusedGame?.let { com.nendo.argosy.ui.common.PresentationTarget.Game(it.id) }
                        ?: com.nendo.argosy.ui.common.PresentationTarget.Nothing
                }
                .distinctUntilChanged()
                .collect { presentation.show(it) }
        }
    }

    fun republishCompanionDetail() = presentation.startDescribing()

    fun clearCompanionDetail() = presentation.stopDescribing()

    private fun setFocus(index: Int, jump: Boolean = false) {
        _focus.update { CategoryFocus(index, if (jump) it.scrollTrigger + 1 else it.scrollTrigger) }
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
        if (state.downloadAllProgress.isActive || state.showOptionsModal) return
        val game = state.games.getOrNull(index) ?: return
        browser.resetStickyColumn()
        setFocus(index)
        openOptionsFor(state, game)
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

    fun togglePin() {
        viewModelScope.launch {
            val isPinned = _isPinned.value
            if (isPinned) {
                unpinCollectionUseCase.unpinVirtual(categoryType, category)
            } else {
                pinCollectionUseCase.pinVirtual(categoryType, category)
            }
            _isPinned.value = !isPinned
        }
    }

    fun refresh() {
        val now = System.currentTimeMillis()
        if (now - lastRefreshTime < REFRESH_DEBOUNCE_MS) return
        if (_isRefreshing.value) return

        lastRefreshTime = now
        viewModelScope.launch {
            _isRefreshing.value = true
            refreshAllCollectionsUseCase()
            _isRefreshing.value = false
        }
    }

    private val _downloadAllConfirmVisible = MutableStateFlow(false)
    val downloadAllConfirmVisible: StateFlow<Boolean> = _downloadAllConfirmVisible

    fun requestDownloadAll() {
        if (uiState.value.canDownloadAll) _downloadAllConfirmVisible.value = true
    }

    fun dismissDownloadAllConfirm() {
        _downloadAllConfirmVisible.value = false
    }

    fun confirmDownloadAll() {
        _downloadAllConfirmVisible.value = false
        showDownloadAllModal()
    }

    private fun showDownloadAllModal() {
        val state = uiState.value
        if (!state.canDownloadAll) return

        val downloadable = state.games.filter { !it.isDownloaded && it.id in state.listing.rommGameIds }
        if (downloadable.isEmpty()) return

        _downloadAllProgress.value = DownloadAllProgress(
            isActive = true,
            currentIndex = 0,
            totalCount = downloadable.size,
            isOnCooldown = false
        )

        viewModelScope.launch {
            var queued = 0
            for ((index, game) in downloadable.withIndex()) {
                _downloadAllProgress.update { it.copy(currentIndex = index + 1) }
                downloadGameUseCase(game.id)
                queued++
                delay(50)
            }

            _downloadAllProgress.update { it.copy(isActive = false, isOnCooldown = true) }

            notificationManager.show(
                title = NotificationText.Res(R.string.notif_virtualcategory_downloads_queued_title),
                subtitle = NotificationText.Plural(
                    R.plurals.notif_virtualcategory_downloads_queued_subtitle,
                    queued,
                    listOf(queued)
                ),
                type = NotificationType.INFO,
                duration = NotificationDuration.MEDIUM
            )

            delay(DOWNLOAD_ALL_COOLDOWN_MS)
            _downloadAllProgress.value = DownloadAllProgress()
        }
    }

    /**
     * Opens the options modal for the focused game. False, and nothing opens, when the modal has
     * no rows for that game.
     */
    fun showOptionsModal(): Boolean {
        val state = uiState.value
        return openOptionsFor(state, state.focusedGame)
    }

    private fun openOptionsFor(state: VirtualCategoryUiState, game: LibraryGameUi?): Boolean {
        if (optionsOpenJob?.isActive == true) return true
        val running = game?.let { runningGameMenu.markFor(it.id) }
        if (state.optionRowsFor(running).isEmpty()) return false
        optionsOpenJob = viewModelScope.launch {
            val moveTargets = runningGameMenu.moveTargets(running)
            _options.update {
                it.copy(
                    visible = true,
                    focusIndex = 0,
                    running = running,
                    moveTargets = moveTargets
                )
            }
        }
        return true
    }

    fun hideOptionsModal() {
        optionsOpenJob?.cancel()
        _options.update { it.copy(visible = false) }
    }

    fun moveOptionsFocus(delta: Int) {
        val maxIndex = (uiState.value.optionRows.size - 1).coerceAtLeast(0)
        _options.update { it.copy(focusIndex = (it.focusIndex + delta).coerceIn(0, maxIndex)) }
    }

    fun selectOption(option: CollectionOption) {
        val state = uiState.value
        hideOptionsModal()
        when (option) {
            CollectionOption.DOWNLOAD_ALL -> requestDownloadAll()
            CollectionOption.CLOSE_GAME -> runningGameMenu.requestClose(state.optionsRunning, state.focusedGame?.title)
            CollectionOption.RENAME, CollectionOption.DELETE, CollectionOption.REMOVE_GAME -> Unit
        }
    }

    fun moveRunningGame(target: RunningMoveTarget) {
        val state = uiState.value
        hideOptionsModal()
        val running = state.optionsRunning ?: return
        val title = state.focusedGame?.title ?: return
        runningGameMenu.moveTo(viewModelScope, running, target, title)
    }

    fun confirmOptionSelection() {
        val state = uiState.value
        when (val row = state.optionRows.getOrNull(state.optionsModalFocusIndex)) {
            is CollectionOptionRow.Action -> selectOption(row.option)
            is CollectionOptionRow.MoveTo -> moveRunningGame(row.target)
            null -> Unit
        }
    }

    fun createInputHandler(
        onBack: () -> Unit,
        onGameClick: (Long) -> Unit
    ): InputHandler = browser.routeInput(object : InputHandler {
        private fun isBlocked(state: VirtualCategoryUiState): Boolean =
            state.downloadAllProgress.isActive || state.showOptionsModal

        private fun heldWhileBlocked(): InputResult =
            if (isBlocked(uiState.value)) InputResult.HANDLED else InputResult.UNHANDLED

        private fun move(direction: FocusMove): InputResult {
            val state = uiState.value
            if (state.downloadAllProgress.isActive) return InputResult.HANDLED
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
            if (state.downloadAllProgress.isActive) return InputResult.HANDLED
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
                state.downloadAllProgress.isActive -> Unit
                state.showOptionsModal -> hideOptionsModal()
                else -> onBack()
            }
            return InputResult.HANDLED
        }

        override fun onPrevSection(): InputResult = heldWhileBlocked()

        override fun onNextSection(): InputResult = heldWhileBlocked()

        override fun onLeftStickClick(): InputResult = heldWhileBlocked()

        override fun onRightStickClick(): InputResult = heldWhileBlocked()

        override fun onRightStickUp(): InputResult = heldWhileBlocked()

        override fun onRightStickDown(): InputResult = heldWhileBlocked()

        override fun onRightStickLeft(): InputResult = heldWhileBlocked()

        override fun onRightStickRight(): InputResult = heldWhileBlocked()

        override fun onSecondaryAction(): InputResult {
            if (isBlocked(uiState.value)) return InputResult.HANDLED
            togglePin()
            return InputResult.HANDLED
        }

        override fun onContextMenu(): InputResult {
            if (isBlocked(uiState.value)) return InputResult.HANDLED
            browser.toggleFilterMenu()
            return InputResult.HANDLED
        }

        override fun onMenu(): InputResult {
            if (isBlocked(uiState.value)) return InputResult.HANDLED
            refresh()
            return InputResult.HANDLED
        }

        override fun onPrevTrigger(): InputResult = sectionJump(-1)

        override fun onNextTrigger(): InputResult = sectionJump(1)

        private fun sectionJump(direction: Int): InputResult {
            val state = uiState.value
            if (isBlocked(state)) return InputResult.HANDLED
            if (state.sectionLabels.isEmpty()) return InputResult.UNHANDLED
            jumpToAdjacentSection(direction)
            return InputResult.HANDLED
        }

        override fun onSelect(): InputResult {
            if (isBlocked(uiState.value)) return InputResult.HANDLED
            if (com.nendo.argosy.ui.dualscreen.selectSwapsRoles()) return InputResult.UNHANDLED
            return onLongConfirm()
        }

        override fun onLongSelect(): InputResult =
            if (isBlocked(uiState.value)) InputResult.HANDLED else InputResult.UNHANDLED

        override fun onLongConfirm(): InputResult {
            if (isBlocked(uiState.value)) return InputResult.HANDLED
            return if (showOptionsModal()) InputResult.HANDLED else InputResult.UNHANDLED
        }
    })
}
