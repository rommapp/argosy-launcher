package com.nendo.argosy.ui.screens.collections

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.qualifiers.ApplicationContext
import com.nendo.argosy.data.repository.PlatformRepository
import com.nendo.argosy.data.local.entity.GameEntity
import com.nendo.argosy.data.local.entity.getDisplayName
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
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.Dispatchers
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
    val games: List<CollectionGameUi> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val focusedIndex: Int = 0,
    val isPinned: Boolean = false,
    val downloadAllProgress: DownloadAllProgress = DownloadAllProgress(),
    val sectionLabels: List<String> = emptyList(),
    val currentSectionLabel: String = "",
    val isSearchActive: Boolean = false,
    val searchQuery: String = "",
    val isSearchKeyboardOpen: Boolean = false,
    val isSearchStopFocused: Boolean = false,
    val overlayLetter: String? = null,
    val showOptionsModal: Boolean = false,
    val optionsModalFocusIndex: Int = 0,
    val optionsRunning: RunningGameUi? = null,
    val optionsMoveTargets: List<RunningMoveTarget> = emptyList()
) {
    val focusedGame: CollectionGameUi?
        get() = if (isSearchStopFocused) null else games.getOrNull(focusedIndex)

    val downloadableGamesCount: Int
        get() = games.count { !it.isDownloaded && it.rommId != null }

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

    val showSectionSidebar: Boolean
        get() = !isSearchActive && sectionLabels.size > 1
}

private data class CategorySearchState(
    val active: Boolean = false,
    val query: String = "",
    val keyboardOpen: Boolean = false,
    val stopFocused: Boolean = false
)

private data class CategoryOptionsState(
    val visible: Boolean = false,
    val focusIndex: Int = 0,
    val running: RunningGameUi? = null,
    val moveTargets: List<RunningMoveTarget> = emptyList()
)

@HiltViewModel
class VirtualCategoryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    savedStateHandle: SavedStateHandle,
    private val getGamesByCategoryUseCase: GetGamesByCategoryUseCase,
    private val platformRepository: PlatformRepository,
    private val isPinnedUseCase: IsPinnedUseCase,
    private val pinCollectionUseCase: PinCollectionUseCase,
    private val unpinCollectionUseCase: UnpinCollectionUseCase,
    private val refreshAllCollectionsUseCase: RefreshAllCollectionsUseCase,
    private val downloadGameUseCase: DownloadGameUseCase,
    private val notificationManager: NotificationManager,
    private val positions: VirtualBrowsePositions,
    private val downloadFileStatusRepository: com.nendo.argosy.data.repository.DownloadFileStatusRepository,
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
    private val _focusedIndex = MutableStateFlow(positions.get(stickyKey))
    private val _isPinned = MutableStateFlow(false)
    private val _isRefreshing = MutableStateFlow(false)
    private val _downloadAllProgress = MutableStateFlow(DownloadAllProgress())
    private val _search = MutableStateFlow(CategorySearchState())
    private val _options = MutableStateFlow(CategoryOptionsState())
    private var optionsOpenJob: Job? = null
    private val _overlayLetter = MutableStateFlow<String?>(null)
    private var preSearchIndex = 0
    private var preSearchStopFocused = false
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
            val isPinned = isPinnedUseCase.isVirtualPinned(categoryType, category)
            _isPinned.value = isPinned
        }
    }

    private class VisibleGames(val games: List<CollectionGameUi>, val sectionLabelByGame: List<String>) {
        val sectionLabels: List<String> = sectionLabelByGame.distinct()
    }

    private val visibleGames = combine(
        getGamesByCategoryUseCase(categoryType, category),
        platformRepository.observeAllPlatforms(),
        _search.map { it.query }.distinctUntilChanged()
    ) { games, platforms, query ->
        val platformMap = platforms.associate { it.id to it.getDisplayName() }
        val sorted = games
            .map { game ->
                game.toCollectionGameUi(
                    platformDisplayName = platformMap[game.platformId]
                        ?: context.getString(R.string.collections_category_platform_unknown),
                    downloadStatus = downloadFileStatusRepository
                )
            }
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
        val visible = if (query.isBlank()) {
            sorted
        } else {
            val q = com.nendo.argosy.util.SearchNormalizer.normalize(query)
            sorted.filter { com.nendo.argosy.util.SearchNormalizer.normalize(it.title).contains(q) }
        }
        VisibleGames(visible, visible.map { sectionLabelFor(it.title) })
    }.flowOn(Dispatchers.Default)

    private val core = combine(
        visibleGames,
        _focusedIndex,
        combine(_isPinned, _isRefreshing, _downloadAllProgress) { a, b, c -> Triple(a, b, c) },
        _search,
        _options
    ) { listing, focusedIndex, (isPinned, isRefreshing, downloadProgress), search, options ->
        val visible = listing.games
        val clamped = focusedIndex.coerceIn(0, (visible.size - 1).coerceAtLeast(0))
        VirtualCategoryUiState(
            type = type,
            categoryName = category,
            games = visible,
            isLoading = false,
            isRefreshing = isRefreshing,
            focusedIndex = clamped,
            isPinned = isPinned,
            downloadAllProgress = downloadProgress,
            sectionLabels = if (search.active) emptyList() else listing.sectionLabels,
            currentSectionLabel = listing.sectionLabelByGame.getOrNull(clamped) ?: "",
            isSearchActive = search.active,
            searchQuery = search.query,
            isSearchKeyboardOpen = search.keyboardOpen,
            isSearchStopFocused = search.stopFocused,
            showOptionsModal = options.visible,
            optionsModalFocusIndex = options.focusIndex,
            optionsRunning = options.running,
            optionsMoveTargets = options.moveTargets
        )
    }

    val uiState: StateFlow<VirtualCategoryUiState> = combine(core, _overlayLetter) { state, overlay ->
        state.copy(overlayLetter = overlay)
    }.stateIn(
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

    private fun sectionLabelFor(name: String): String {
        val first = name.trim().firstOrNull()?.uppercaseChar() ?: '#'
        return if (first in 'A'..'Z') first.toString() else "#"
    }

    private fun setFocus(index: Int) {
        _search.update { it.copy(stopFocused = false) }
        _focusedIndex.value = index
        if (!_search.value.active) positions.set(stickyKey, index)
    }

    fun moveUp() {
        val state = uiState.value
        when {
            state.isSearchStopFocused -> Unit
            state.focusedIndex > 0 -> setFocus(state.focusedIndex - 1)
            else -> _search.update { it.copy(stopFocused = true) }
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
        if (state.downloadAllProgress.isActive || state.showOptionsModal) return
        val game = state.games.getOrNull(index) ?: return
        setFocus(index)
        openOptionsFor(state, game)
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
        if (_search.value.active) {
            _search.update { it.copy(keyboardOpen = true, stopFocused = true) }
            return
        }
        preSearchIndex = uiState.value.focusedIndex
        preSearchStopFocused = uiState.value.isSearchStopFocused
        _focusedIndex.value = 0
        _search.value = CategorySearchState(active = true, query = "", keyboardOpen = true, stopFocused = true)
    }

    fun closeSearchKeyboard() {
        _search.update { it.copy(keyboardOpen = false) }
    }

    fun closeSearch() {
        if (!_search.value.active) return
        _search.value = CategorySearchState(active = false, query = "", stopFocused = preSearchStopFocused)
        _focusedIndex.value = preSearchIndex
    }

    fun setSearchQuery(query: String) {
        _search.update { it.copy(query = query) }
        _focusedIndex.value = 0
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

        val downloadable = state.games.filter { !it.isDownloaded && it.rommId != null }
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
                _downloadAllProgress.update {
                    it.copy(
                        currentIndex = index + 1
                    )
                }
                downloadGameUseCase(game.id)
                queued++
                delay(50)
            }

            _downloadAllProgress.update {
                it.copy(
                    isActive = false,
                    isOnCooldown = true
                )
            }

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

    fun dismissDownloadAllModal() {
        if (!_downloadAllProgress.value.isActive) {
            _downloadAllProgress.update { it.copy(isActive = false) }
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

    private fun openOptionsFor(state: VirtualCategoryUiState, game: CollectionGameUi?): Boolean {
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
    ): InputHandler = object : InputHandler {
        private fun isBlocked(state: VirtualCategoryUiState): Boolean =
            state.downloadAllProgress.isActive || state.showOptionsModal

        private fun heldWhileBlocked(): InputResult =
            if (isBlocked(uiState.value)) InputResult.HANDLED else InputResult.UNHANDLED

        override fun onUp(): InputResult {
            val state = uiState.value
            if (state.downloadAllProgress.isActive) return InputResult.HANDLED
            if (state.showOptionsModal) moveOptionsFocus(-1) else moveUp()
            return InputResult.HANDLED
        }

        override fun onDown(): InputResult {
            val state = uiState.value
            if (state.downloadAllProgress.isActive) return InputResult.HANDLED
            if (state.showOptionsModal) moveOptionsFocus(1) else moveDown()
            return InputResult.HANDLED
        }

        override fun onConfirm(): InputResult {
            val state = uiState.value
            if (state.downloadAllProgress.isActive) return InputResult.HANDLED
            if (state.showOptionsModal) {
                confirmOptionSelection()
                return InputResult.HANDLED
            }
            if (state.isSearchStopFocused) {
                openSearch()
                return InputResult.HANDLED
            }
            state.focusedGame?.let { onGameClick(it.id) }
            return InputResult.HANDLED
        }

        override fun onBack(): InputResult {
            val state = uiState.value
            if (state.downloadAllProgress.isActive) {
                return InputResult.HANDLED
            }
            if (state.showOptionsModal) {
                hideOptionsModal()
                return InputResult.HANDLED
            }
            if (state.isSearchActive) {
                closeSearch()
                return InputResult.HANDLED
            }
            onBack()
            return InputResult.HANDLED
        }

        override fun onLeft(): InputResult = heldWhileBlocked()

        override fun onRight(): InputResult = heldWhileBlocked()

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
            openSearch()
            return InputResult.HANDLED
        }

        override fun onMenu(): InputResult {
            if (isBlocked(uiState.value)) return InputResult.HANDLED
            refresh()
            return InputResult.HANDLED
        }

        override fun onPrevTrigger(): InputResult {
            val state = uiState.value
            if (isBlocked(state)) return InputResult.HANDLED
            if (state.sectionLabels.isEmpty()) return InputResult.UNHANDLED
            jumpToPreviousSection()
            return InputResult.HANDLED
        }

        override fun onNextTrigger(): InputResult {
            val state = uiState.value
            if (isBlocked(state)) return InputResult.HANDLED
            if (state.sectionLabels.isEmpty()) return InputResult.UNHANDLED
            jumpToNextSection()
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
    }
}
