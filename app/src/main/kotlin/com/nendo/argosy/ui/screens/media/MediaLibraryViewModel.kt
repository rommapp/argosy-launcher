package com.nendo.argosy.ui.screens.media

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.data.media.MediaAvailabilityVerifier
import com.nendo.argosy.data.remote.jellyfin.JellyfinResult
import com.nendo.argosy.data.repository.MediaRepository
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.screens.common.GradientExtractionDelegate
import com.nendo.argosy.ui.input.InputResult
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class MediaLibraryViewModel @Inject constructor(
    private val mediaRepository: MediaRepository,
    private val availabilityVerifier: MediaAvailabilityVerifier,
    private val gradientExtractionDelegate: GradientExtractionDelegate
) : ViewModel() {

    private val _uiState = MutableStateFlow(MediaLibraryUiState())
    val uiState: StateFlow<MediaLibraryUiState> = _uiState.asStateFlow()

    private val selectedLibraryId = MutableStateFlow<String?>(null)

    init {
        observeSignInState()
        observeLibraries()
        observeItems()
        observeGradients()
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

    /**
     * Folds newly sampled poster colours into tiles already on screen, rather than rebuilding the
     * grid for each one. A screenful of posters lands one colour pair at a time as each image
     * decodes, and re-running the item query that often would cost a database read per poster.
     */
    private fun observeGradients() {
        viewModelScope.launch {
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
            _uiState.update { it.copy(isRefreshing = true, refreshLabel = "Refreshing", errorMessage = null) }
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
                    subtitle = item.episodeLabel ?: item.year?.toString(),
                    resumeTicks = item.resumeTicks
                )
            )
        }
        return true
    }

    fun dismissResumePrompt() {
        _uiState.update { it.copy(resumePrompt = null) }
    }

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

        override fun onPrevSection(): InputResult {
            cycleLibrary(-1)
            return InputResult.HANDLED
        }

        override fun onNextSection(): InputResult {
            cycleLibrary(1)
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

        override fun onContextMenu(): InputResult {
            refresh()
            return InputResult.HANDLED
        }
    }
}
