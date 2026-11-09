package com.nendo.argosy.ui.screens.media

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.DualScreenManagerHolder
import com.nendo.argosy.R
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.data.media.MediaAvailabilityVerifier
import com.nendo.argosy.data.remote.jellyfin.JellyfinResult
import com.nendo.argosy.data.repository.MediaRepository
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.screens.common.GradientExtractionDelegate
import com.nendo.argosy.ui.input.InputResult
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import com.nendo.argosy.data.local.entity.MediaItemEntity
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import com.nendo.argosy.ui.screens.media.delegates.MediaDownloadPromptOutcome
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MediaLibraryViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val mediaRepository: MediaRepository,
    private val availabilityVerifier: MediaAvailabilityVerifier,
    private val downloadDelegate:
        com.nendo.argosy.ui.screens.media.delegates.MediaDownloadDelegate,
    private val gradientExtractionDelegate: GradientExtractionDelegate,
    private val quickNavigation: com.nendo.argosy.data.preferences.QuickNavigationSource
) : ViewModel() {

    val quickNavigationEnabled: kotlinx.coroutines.flow.StateFlow<Boolean> get() = quickNavigation.enabled

    private val companionOwner = com.nendo.argosy.ui.dualscreen.SlotOwner.of("media.library", this)
    private var isDescribing = false

    private val _uiState = MutableStateFlow(MediaLibraryUiState())
    val uiState: StateFlow<MediaLibraryUiState> = _uiState.asStateFlow()

    private val selectedLibraryId = MutableStateFlow<String?>(null)

    init {
        observeSignInState()
        observeLibraries()
        observeItems()
        observeGradients()
        observeFocusForCompanion()
        availabilityVerifier.verifyOnOpen()
    }

    private fun observeSignInState() {
        viewModelScope.launch {
            mediaRepository.isSignedIn.collect { signedIn ->
                _uiState.update { it.copy(isSignedIn = signedIn, isLoading = signedIn && it.libraries.isEmpty()) }
            }
        }
    }

    private fun observeLibraries() {
        viewModelScope.launch {
            mediaRepository.observeLibraries().collect { entities ->
                val libraries = entities.map { it.toMediaLibraryUi() }
                _uiState.update { state ->
                    val index = state.selectedLibraryIndex.coerceIn(0, (libraries.size - 1).coerceAtLeast(0))
                    state.copy(
                        libraries = libraries,
                        selectedLibraryIndex = index,
                        isLoading = state.isLoading && libraries.isEmpty()
                    )
                }
                selectedLibraryId.value = libraries.getOrNull(_uiState.value.selectedLibraryIndex)?.libraryId
                if (libraries.isEmpty() && _uiState.value.isSignedIn) refresh()
            }
        }
    }

    /**
     * The grid, rebuilt whenever its contents or their availability changes. Availability arrives as
     * an already-computed map, so a shelf of several hundred tiles costs map reads rather than a
     * filesystem call per tile.
     */
    private fun observeItems() {
        viewModelScope.launch {
            selectedLibraryId
                .filterNotNull()
                .distinctUntilChanged()
                .flatMapLatest { libraryId -> mediaRepository.observeLibraryItems(libraryId) }
                .distinctUntilChanged(::sameApartFromGradients)
                .combine(availabilityVerifier.availability) { entities, verified -> entities to verified }
                .map { (entities, verified) ->
                    gradientExtractionDelegate.loadPersistedMediaGradients(
                        viewModelScope,
                        entities.map { it.itemId }
                    )
                    val userData = mediaRepository.getUserDataFor(entities.map { it.itemId })
                    val gradients = gradientExtractionDelegate.mediaGradients.value
                    entities.map {
                        it.toMediaItemUi(mediaRepository, userData[it.itemId], verified, gradients)
                    }
                }
                .flowOn(Dispatchers.Default)
                .collect { items ->
                    _uiState.update { state ->
                        state.copy(
                            items = items,
                            focusedIndex = state.focusedIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0)),
                            isLoading = false
                        )
                    }
                }
        }
    }

    private fun sameApartFromGradients(old: List<MediaItemEntity>, new: List<MediaItemEntity>): Boolean =
        old.size == new.size && old.indices.all { i ->
            old[i] == new[i] || old[i].copy(gradientColors = null) == new[i].copy(gradientColors = null)
        }

    /**
     * Folds newly sampled poster colours into tiles already on screen, rather than rebuilding the
     * grid for each one. A screenful of posters lands one colour pair at a time as each image
     * decodes, and re-running the item query that often would cost a database read per poster.
     */
    private fun observeGradients() {
        viewModelScope.launch(Dispatchers.Default) {
            gradientExtractionDelegate.mediaGradients.collect { gradients ->
                if (gradients.isEmpty()) return@collect
                _uiState.update { state ->
                    state.copy(
                        items = state.items.map { item ->
                            gradients[item.itemId]
                                ?.takeIf { it != item.gradientColors }
                                ?.let { item.copy(gradientColors = it) }
                                ?: item
                        }
                    )
                }
            }
        }
    }

    /**
     * Samples an item's poster the first time it finishes decoding. A poster is fetched rather than
     * stored, so the drawn bitmap is the only place its colours can be read from.
     */
    fun onPosterLoaded(itemId: String, bitmap: android.graphics.Bitmap) {
        gradientExtractionDelegate.extractForMedia(viewModelScope, itemId, bitmap)
    }

    fun setColumnsCount(columns: Int) {
        if (columns <= 0) return
        _uiState.update { if (it.columnsCount == columns) it else it.copy(columnsCount = columns) }
    }

    fun selectLibrary(index: Int) {
        val state = _uiState.value
        if (state.libraries.isEmpty()) return
        val target = index.mod(state.libraries.size)
        if (target == state.selectedLibraryIndex) return
        _uiState.update { it.copy(selectedLibraryIndex = target, focusedIndex = 0, items = emptyList(), isLoading = true) }
        selectedLibraryId.value = state.libraries[target].libraryId
    }

    /**
     * Opens the named library. The libraries arrive asynchronously, so an id that is not among them
     * yet is left alone rather than treated as absent: the caller re-offers it once the list lands.
     */
    fun selectLibraryById(libraryId: String) {
        val index = _uiState.value.libraries.indexOfFirst { it.libraryId == libraryId }
        if (index < 0) return
        selectLibrary(index)
    }

    fun cycleLibrary(direction: Int) {
        val state = _uiState.value
        if (state.libraries.size < 2) return
        selectLibrary(state.selectedLibraryIndex + direction)
    }

    fun setFocusedIndex(index: Int) {
        val size = _uiState.value.items.size
        if (size == 0) return
        _uiState.update { it.copy(focusedIndex = index.coerceIn(0, size - 1)) }
    }

    /**
     * Tells the showcase screen what this one has focused.
     *
     * Driven off state rather than called from each navigation method, because the other screen is
     * describing a cursor rather than a destination: every path that moves the cursor has to reach
     * it, and there are more of those than any one of them knows about.
     */
    private fun observeFocusForCompanion() {
        viewModelScope.launch {
            _uiState
                .map { it.items.getOrNull(it.focusedIndex) }
                .distinctUntilChanged()
                .collect { publishCompanionDetail(it) }
        }
    }

    /**
     * Re-states the focused item to the showcase, for a return from somewhere that replaced it.
     *
     * The observer above only speaks when the focus changes, and coming back from a detail screen
     * changes nothing: without this the showcase keeps whatever the detail screen left there until
     * the viewer happens to move.
     */
    fun republishCompanionDetail() {
        isDescribing = true
        val state = _uiState.value
        publishCompanionDetail(state.items.getOrNull(state.focusedIndex))
    }

    private fun publishCompanionDetail(item: MediaItemUi?) {
        if (!isDescribing) return
        DualScreenManagerHolder.instance?.setCompanionDetail(
            companionOwner,
            item?.toCompanionDetail(context)
        )
    }

    /**
     * Stops describing this screen. Called when it goes away, so the showcase does not keep
     * standing over a cursor that no longer exists.
     */
    fun clearCompanionDetail() {
        isDescribing = false
        DualScreenManagerHolder.instance?.setCompanionDetail(companionOwner, null)
    }

    /**
     * Horizontal movement wraps across the whole grid; vertical movement clamps at the ends, so the
     * last row cannot fall off into an empty cell.
     */
    fun moveFocusHorizontal(direction: Int): Boolean {
        val size = _uiState.value.items.size
        if (size == 0) return false
        _uiState.update { it.copy(focusedIndex = (it.focusedIndex + direction).mod(size)) }
        return true
    }

    fun moveFocusVertical(direction: Int): Boolean {
        val state = _uiState.value
        if (state.items.isEmpty()) return false
        val target = state.focusedIndex + direction * state.columnsCount
        if (target < 0 || target > state.items.lastIndex) return false
        _uiState.update { it.copy(focusedIndex = target) }
        return true
    }

    fun refresh() {
        if (_uiState.value.isRefreshing) return
        availabilityVerifier.verifyOnOpen()
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isRefreshing = true,
                    refreshLabel = R.string.media_library_viewmodel_refresh_label,
                    errorMessage = null
                )
            }
            when (val result = mediaRepository.refreshLibraries()) {
                is JellyfinResult.Success -> _uiState.update {
                    it.copy(isRefreshing = false, refreshLabel = null, isLoading = false)
                }
                is JellyfinResult.Error -> _uiState.update {
                    it.copy(isRefreshing = false, refreshLabel = null, isLoading = false, errorMessage = result.message)
                }
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(errorMessage = null) }
    }

    /**
     * Raises the resume prompt for one tile. A series is not itself playable and an item with
     * nothing to resume has no choice to offer, so neither raises the prompt and the caller falls
     * back to its own action.
     */
    fun openResumePrompt(index: Int): Boolean {
        val item = _uiState.value.items.getOrNull(index) ?: return false
        if (!item.isPlayable || !item.hasResumePosition) return false
        _uiState.update {
            it.copy(
                focusedIndex = index,
                resumePrompt = MediaResumePrompt(
                    itemId = item.itemId,
                    title = item.title,
                    subtitle = item.episodeLabel(context) ?: item.year?.toString(),
                    resumeTicks = item.resumeTicks
                )
            )
        }
        return true
    }

    fun dismissResumePrompt() {
        _uiState.update { it.copy(resumePrompt = null) }
    }

    fun openMenu() {
        val item = _uiState.value.focusedItem ?: return
        _uiState.update {
            it.copy(
                menu = MediaMenuState(
                    targetItemId = item.itemId,
                    title = item.title,
                    subtitle = item.seriesName ?: item.year?.toString(),
                    actions = buildMediaMenu(
                        MediaMenuContext(
                            canRefreshEpisodes = false,
                            hasDownloads = item.isDownloaded,
                            hasLibrary = false
                        )
                    ),
                    targetPlayed = item.played,
                    targetIsFavorite = item.isFavorite
                )
            )
        }
    }

    fun dismissMenu() = _uiState.update { it.copy(menu = null) }

    fun moveMenuFocus(delta: Int) {
        val menu = _uiState.value.menu ?: return
        if (menu.actions.isEmpty()) return
        _uiState.update {
            it.copy(menu = menu.copy(focusedIndex = (menu.focusedIndex + delta).mod(menu.actions.size)))
        }
    }

    fun focusMenuOption(index: Int) {
        val menu = _uiState.value.menu ?: return
        _uiState.update { it.copy(menu = menu.copy(focusedIndex = index)) }
    }

    fun confirmMenuOption() {
        val menu = _uiState.value.menu ?: return
        if (menu.isBusy) return
        when (menu.focusedAction) {
            MediaMenuAction.ToggleWatched -> {
                val played = !menu.targetPlayed
                viewModelScope.launch { mediaRepository.setPlayed(menu.targetItemId, played) }
                dismissMenu()
            }
            MediaMenuAction.ToggleFavorite -> {
                val favorite = !menu.targetIsFavorite
                viewModelScope.launch { mediaRepository.setFavorite(menu.targetItemId, favorite) }
                dismissMenu()
            }
            MediaMenuAction.Download -> {
                dismissMenu()
                openDownloadPrompt()
            }
            MediaMenuAction.RemoveDownloads -> {
                dismissMenu()
                openRemovalPrompt()
            }
            MediaMenuAction.RefreshSeries,
            MediaMenuAction.GoToLibrary,
            null -> dismissMenu()
        }
    }

    private fun openRemovalPrompt() {
        val item = _uiState.value.focusedItem ?: return
        viewModelScope.launch {
            val prompt = downloadDelegate.openRemovalPrompt(item)
            _uiState.update { it.copy(downloadPrompt = prompt) }
        }
    }

    fun openDownloadPrompt() {
        val item = _uiState.value.focusedItem ?: return
        viewModelScope.launch {
            when (val outcome = downloadDelegate.openPrompt(item)) {
                is MediaDownloadPromptOutcome.Ready ->
                    _uiState.update { it.copy(downloadPrompt = outcome.prompt) }
                is MediaDownloadPromptOutcome.Refused ->
                    _uiState.update { it.copy(errorMessage = outcome.reason) }
            }
        }
    }

    fun moveDownloadFocus(delta: Int) {
        val prompt = _uiState.value.downloadPrompt ?: return
        _uiState.update { it.copy(downloadPrompt = downloadDelegate.moveFocus(prompt, delta)) }
    }

    fun focusDownloadOption(index: Int) {
        val prompt = _uiState.value.downloadPrompt ?: return
        _uiState.update { it.copy(downloadPrompt = downloadDelegate.focus(prompt, index)) }
    }

    fun moveDownloadSideways(towardsEnd: Boolean) {
        val prompt = _uiState.value.downloadPrompt ?: return
        if (prompt.step != MediaDownloadStep.EPISODES) return
        _uiState.update {
            it.copy(downloadPrompt = downloadDelegate.moveSideways(prompt, towardsEnd))
        }
    }

    fun confirmDownloadOption() {
        val state = _uiState.value
        val prompt = state.downloadPrompt ?: return
        val item = state.focusedItem ?: return
        if (prompt.step == MediaDownloadStep.EPISODES) {
            when {
                prompt.episodes.isCancelFocused -> dismissDownloadPrompt()
                prompt.episodes.isConfirmFocused -> commitEpisodeSelection()
                else -> _uiState.update {
                    it.copy(downloadPrompt = downloadDelegate.toggleEpisode(prompt))
                }
            }
            return
        }
        viewModelScope.launch {
            val next = downloadDelegate.advance(prompt, item)
            _uiState.update { it.copy(downloadPrompt = next) }
        }
    }

    fun commitEpisodeSelection() {
        val prompt = _uiState.value.downloadPrompt ?: return
        if (prompt.step != MediaDownloadStep.EPISODES) return
        if (!prompt.episodes.hasSelection) return
        viewModelScope.launch {
            val next = downloadDelegate.confirmEpisodeSelection(prompt)
            _uiState.update { it.copy(downloadPrompt = next) }
        }
    }

    fun dismissDownloadPrompt() = _uiState.update { it.copy(downloadPrompt = null) }

    fun createInputHandler(
        onBack: () -> Unit,
        onItemSelect: (String) -> Unit,
        onPlay: (String) -> Unit = {}
    ): InputHandler = object : InputHandler {
        override fun onUp(): InputResult =
            if (moveFocusVertical(-1)) InputResult.HANDLED else InputResult.handled(SoundType.BOUNDARY)

        override fun onDown(): InputResult =
            if (moveFocusVertical(1)) InputResult.HANDLED else InputResult.handled(SoundType.BOUNDARY)

        override fun onLeft(): InputResult =
            if (moveFocusHorizontal(-1)) InputResult.HANDLED else InputResult.handled(SoundType.BOUNDARY)

        override fun onRight(): InputResult =
            if (moveFocusHorizontal(1)) InputResult.HANDLED else InputResult.handled(SoundType.BOUNDARY)

        override fun onConfirm(): InputResult {
            val item = _uiState.value.focusedItem ?: return InputResult.handled(SoundType.BOUNDARY)
            onItemSelect(item.itemId)
            return InputResult.HANDLED
        }

        override fun onBack(): InputResult {
            onBack()
            return InputResult.HANDLED
        }

        override fun onPrevTrigger(): InputResult = libraryButton(-1, onBumper = false)

        override fun onNextTrigger(): InputResult = libraryButton(1, onBumper = false)

        override fun onPrevSection(): InputResult = libraryButton(-1, onBumper = true)

        override fun onNextSection(): InputResult = libraryButton(1, onBumper = true)

        private fun libraryButton(direction: Int, onBumper: Boolean): InputResult {
            if (onBumper == quickNavigation.enabled.value) return InputResult.UNHANDLED
            cycleLibrary(direction)
            return InputResult.HANDLED
        }

        override fun onSecondaryAction(): InputResult {
            val state = _uiState.value
            val item = state.focusedItem ?: return InputResult.handled(SoundType.BOUNDARY)
            if (!item.isPlayable) return InputResult.handled(SoundType.BOUNDARY)
            if (openResumePrompt(state.focusedIndex)) return InputResult.HANDLED
            onPlay(item.itemId)
            return InputResult.HANDLED
        }

        override fun onLongConfirm(): InputResult {
            openMenu()
            return InputResult.HANDLED
        }

        override fun onContextMenu(): InputResult {
            refresh()
            return InputResult.HANDLED
        }
    }
}
