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
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
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
    val overlayLetter: String? = null
) {
    val focusedGame: CollectionGameUi?
        get() = games.getOrNull(focusedIndex)

    val downloadableGamesCount: Int
        get() = games.count { !it.isDownloaded && it.rommId != null }

    val canDownloadAll: Boolean
        get() = downloadableGamesCount > 0 &&
                !downloadAllProgress.isActive &&
                !downloadAllProgress.isOnCooldown

    val showSectionSidebar: Boolean
        get() = !isSearchActive && sectionLabels.size > 1
}

private data class CategorySearchState(val active: Boolean = false, val query: String = "")

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
    showcaseSource: com.nendo.argosy.ui.common.PresentationShowcaseSource
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
    private val _overlayLetter = MutableStateFlow<String?>(null)
    private var preSearchIndex = 0
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
        _search
    ) { listing, focusedIndex, (isPinned, isRefreshing, downloadProgress), search ->
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
            searchQuery = search.query
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
        _focusedIndex.value = index
        if (!_search.value.active) positions.set(stickyKey, index)
    }

    fun moveUp() {
        val current = uiState.value.focusedIndex
        if (current > 0) setFocus(current - 1)
    }

    fun moveDown() {
        val current = uiState.value.focusedIndex
        if (current < uiState.value.games.size - 1) setFocus(current + 1)
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
        if (_search.value.active) return
        preSearchIndex = uiState.value.focusedIndex
        _focusedIndex.value = 0
        _search.value = CategorySearchState(active = true, query = "")
    }

    fun closeSearch() {
        if (!_search.value.active) return
        _search.value = CategorySearchState(active = false, query = "")
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

    fun createInputHandler(
        onBack: () -> Unit,
        onGameClick: (Long) -> Unit
    ): InputHandler = object : InputHandler {
        override fun onUp(): InputResult {
            if (uiState.value.downloadAllProgress.isActive) return InputResult.HANDLED
            moveUp()
            return InputResult.HANDLED
        }

        override fun onDown(): InputResult {
            if (uiState.value.downloadAllProgress.isActive) return InputResult.HANDLED
            moveDown()
            return InputResult.HANDLED
        }

        override fun onConfirm(): InputResult {
            if (uiState.value.downloadAllProgress.isActive) return InputResult.HANDLED
            uiState.value.focusedGame?.let { onGameClick(it.id) }
            return InputResult.HANDLED
        }

        override fun onBack(): InputResult {
            if (uiState.value.downloadAllProgress.isActive) {
                return InputResult.HANDLED
            }
            if (uiState.value.isSearchActive) {
                closeSearch()
                return InputResult.HANDLED
            }
            onBack()
            return InputResult.HANDLED
        }

        override fun onSecondaryAction(): InputResult {
            if (uiState.value.downloadAllProgress.isActive) return InputResult.HANDLED
            togglePin()
            return InputResult.HANDLED
        }

        override fun onContextMenu(): InputResult {
            if (uiState.value.downloadAllProgress.isActive) return InputResult.HANDLED
            openSearch()
            return InputResult.HANDLED
        }

        override fun onMenu(): InputResult {
            if (uiState.value.downloadAllProgress.isActive) return InputResult.HANDLED
            refresh()
            return InputResult.HANDLED
        }

        override fun onPrevTrigger(): InputResult {
            if (uiState.value.downloadAllProgress.isActive) return InputResult.HANDLED
            if (uiState.value.sectionLabels.isEmpty()) return InputResult.UNHANDLED
            jumpToPreviousSection()
            return InputResult.HANDLED
        }

        override fun onNextTrigger(): InputResult {
            if (uiState.value.downloadAllProgress.isActive) return InputResult.HANDLED
            if (uiState.value.sectionLabels.isEmpty()) return InputResult.UNHANDLED
            jumpToNextSection()
            return InputResult.HANDLED
        }

        override fun onSelect(): InputResult {
            if (uiState.value.downloadAllProgress.isActive) return InputResult.HANDLED
            if (com.nendo.argosy.ui.dualscreen.selectSwapsRoles()) return InputResult.UNHANDLED
            return onLongConfirm()
        }

        override fun onLongSelect(): InputResult =
            if (uiState.value.downloadAllProgress.isActive) InputResult.HANDLED else InputResult.UNHANDLED

        override fun onLongConfirm(): InputResult {
            if (uiState.value.downloadAllProgress.isActive) return InputResult.HANDLED
            if (uiState.value.canDownloadAll) {
                requestDownloadAll()
                return InputResult.HANDLED
            }
            return InputResult.UNHANDLED
        }
    }
}
